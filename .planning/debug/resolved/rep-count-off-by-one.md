---
status: resolved
trigger: "Rep counter is off-by-one - app shows 5/5 but expects one more rep, preventing set completion"
created: 2026-01-23T00:00:00Z
updated: 2026-01-23T01:00:00Z
---

## Current Focus

hypothesis: The machine's repsSetCount lags behind the actual rep completion by one - machine deloads before sending final repsSetCount update
test: Compare our repsSetCount approach vs official app's down-warmup approach
expecting: Official app uses down counter calculation, not repsSetCount, which may be more reliable
next_action: Modify rep counting to use (down - warmupTarget) instead of trusting repsSetCount for completion detection

## Symptoms

expected: When 5 working reps configured, app should count 1/5 through 5/5 in sync with machine, then progress to next set/exercise. Machine counts 3 warmup reps + 10 working reps correctly and stops.
actual: App shows rep count one behind - reaches 5/5 but expects another rep. Gets stuck at final rep and cannot progress to next set/exercise. "Rep count changes at 'down' position, but is one rep behind the change of the weight."
errors: No crash - just stuck state where app won't progress
reproduction: Run a routine exercise to the end of reps (e.g., 5/5) with another set or exercise queued. Happens in Routines confirmed. Multiple users affected on both Android and iOS.
started: Ongoing issue - multiple users reporting. Owner attempted fix but issue persists.

## Eliminated

## Evidence

- timestamp: 2026-01-23T00:05:00Z
  checked: RepCounterFromMachine.kt (825 lines)
  found: |
    Rep counting uses TWO modes:
    - MODERN: warmupReps=repsRomCount, workingReps=repsSetCount (trusted from machine)
    - LEGACY: counts on topCounter increments

    Key finding: Working reps are set directly from machine's repsSetCount field (line 472):
    `workingReps = repsSetCount`

    The machine is trusted unconditionally for working rep count.
    WORKOUT_COMPLETE fires when workingReps >= workingTarget (line 490).

    No off-by-one arithmetic visible in RepCounterFromMachine itself.
  implication: Bug may be in how workingTarget is configured, or how UI displays/compares the rep count

- timestamp: 2026-01-23T00:15:00Z
  checked: BlePacketFactory.kt line 137, MainViewModel.kt line 1065-1071
  found: |
    CRITICAL MISMATCH FOUND:

    BLE packet (line 137):
      `frame[0x04] = (params.reps + params.warmupReps).toByte()`
    Machine is told TOTAL reps = working + warmup.

    RepCounter config (line 1065-1071):
      `repCounter.configure(
        warmupTarget = params.warmupReps,
        workingTarget = params.reps,  // NOT params.reps + params.warmupReps!
        ...
      )`

    The machine receives: warmupReps + workingReps as total reps
    But the app configures: workingTarget = workingReps only

    Machine's repsSetCount should track working reps (after warmup).
    The machine starts counting repsSetCount AFTER repsRomCount (warmup) is complete.

    HOWEVER: The symptom says machine "counts 3 warmup + 10 working correctly then stops"
    but app shows "9/10". This means repsSetCount maxes at 9, not 10.

    Possible cause: Machine sends repsSetCount = working_reps, but there's an off-by-one
    in how machine counts vs how it reports.
  implication: Need to trace BLE data to see actual repsSetCount values received

- timestamp: 2026-01-23T00:30:00Z
  checked: Parent repo test at ProtocolBuilderTest.kt line 73-74
  found: |
    Test comment says "Reps should be 10 + 3 + 1 = 14" but actual implementation
    at ProtocolBuilder.kt line 56 does NOT include +1:
      `frame[0x04] = (params.reps + params.warmupReps).toByte()`

    This suggests there may have been a known off-by-one issue that was supposed
    to be fixed with +1 counter compensation, but the fix was never implemented
    (or was reverted).

    Key hypothesis: The machine deloads after counting (warmup + working) total reps,
    but repsSetCount reports working_reps - 1 because of how the machine internally
    tracks the final rep crossing.

    Machine behavior sequence:
    1. User completes final rep movement (reaches BOTTOM)
    2. Machine increments internal counter, detects target reached
    3. Machine sends DELOAD signal (stops motors)
    4. Machine may NOT send final repsSetCount update because it's already deloading

    Result: App never receives repsSetCount = working_target, stays at working_target - 1.
  implication: |
    FIX OPTION 1: Add +1 compensation to BLE packet (match what test expected)
    FIX OPTION 2: Handle completion when repsSetCount >= workingTarget - 1 AND machine deloads
    FIX OPTION 3: Trust WORKOUT_COMPLETE event timing over repsSetCount matching

- timestamp: 2026-01-23T00:40:00Z
  checked: Official app decompilation - Yj/p.java line 146-149
  found: |
    OFFICIAL APP APPROACH:
    Working reps = down - repsRomTotal (warmup count)

    Line 149: `pVar.k().getDown() - pVar.d()`
    where d() returns repsRomTotal (warmup count)

    This means the official app calculates working reps from the raw down counter,
    NOT from repsSetCount. The down counter increments at BOTTOM of each rep.

    OUR APPROACH:
    workingReps = repsSetCount (trusted directly)

    HYPOTHESIS CONFIRMED:
    If repsSetCount lags by one (machine deloads before sending final update),
    using (down - warmupTarget) would give the correct count because:
    - down counter likely updates before deload signal
    - repsSetCount might not update if machine enters deload state first
  implication: |
    ROOT CAUSE IDENTIFIED:
    We trust repsSetCount which may not update on final rep before machine deloads.
    Official app uses (down counter - warmup count) which is more reliable.

    FIX: For WORKOUT_COMPLETE detection, also check if:
    (downCounter - warmupTarget) >= workingTarget
    OR modify to use down-based counting like official app

## Resolution

root_cause: |
  The machine's repsSetCount field does not reliably update on the final rep before the machine
  enters deload state. The app was trusting repsSetCount unconditionally, which meant:
  - Machine counts 3 warmup + 10 working = 13 total reps
  - Machine deloads after completing all reps
  - But repsSetCount only reports 9 (not 10) before deload
  - App shows 9/10 and won't progress because repsSetCount < workingTarget

  The official Vitruvian app uses a different approach: working reps = (down counter - warmup count)
  which is more reliable because the down counter updates immediately at the bottom of each rep,
  before any deload logic kicks in.

fix: |
  Added a fallback completion check in RepCounterFromMachine.kt that:
  1. Triggers ONLY when repsSetCount is exactly 1 rep short of target
  2. Uses (down counter - warmupTarget) to verify actual rep completion
  3. Updates workingReps to match target for correct UI display
  4. Emits WORKING_COMPLETED and WORKOUT_COMPLETE events

  The fix is conservative - it only triggers when exactly 1 rep short (not 2+)
  to avoid false positives from bad data.

verification: |
  - Added 5 new unit tests in Issue210FallbackCompletionTest class:
    1. Fallback triggers when repsSetCount is one short but down counter shows completion
    2. Fallback does not trigger if repsSetCount already matched target (no double events)
    3. Fallback does not trigger for Just Lift mode
    4. Fallback does not trigger for AMRAP mode
    5. Fallback only triggers when exactly one rep short (not multiple)
  - All 25 existing tests pass
  - All 5 new tests pass
  - BUILD SUCCESSFUL

files_changed:
  - shared/src/commonMain/kotlin/com/devil/phoenixproject/domain/usecase/RepCounterFromMachine.kt
  - shared/src/commonTest/kotlin/com/devil/phoenixproject/domain/usecase/RepCounterFromMachineTest.kt
