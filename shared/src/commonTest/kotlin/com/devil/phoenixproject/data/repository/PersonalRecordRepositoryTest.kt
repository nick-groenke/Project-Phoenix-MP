package com.devil.phoenixproject.data.repository

import com.devil.phoenixproject.domain.model.PRType
import com.devil.phoenixproject.domain.model.PersonalRecord
import com.devil.phoenixproject.testutil.FakePersonalRecordRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for PersonalRecordRepository mode-specific PR tracking.
 *
 * Issue #111: PRs should be tracked separately per workout mode so that
 * users can see their best performance in each mode independently.
 */
class PersonalRecordRepositoryTest {

    private lateinit var repository: FakePersonalRecordRepository

    private val exerciseId = "bench-press"
    private val exerciseName = "Bench Press"

    @BeforeTest
    fun setup() {
        repository = FakePersonalRecordRepository()
        repository.reset()
    }

    // ========== Test 1: PRs tracked separately per mode ==========

    @Test
    fun `PRs tracked separately per mode - different weights`() = runTest {
        // Insert OLD_SCHOOL PR at 35kg
        val oldSchoolResult = repository.updatePRsIfBetter(
            exerciseId = exerciseId,
            weightPerCableKg = 35f,
            reps = 10,
            workoutMode = "OldSchool",
            timestamp = 1000L
        )
        assertTrue(oldSchoolResult.isSuccess)

        // Insert ECHO PR at 50kg
        val echoResult = repository.updatePRsIfBetter(
            exerciseId = exerciseId,
            weightPerCableKg = 50f,
            reps = 8,
            workoutMode = "Echo",
            timestamp = 2000L
        )
        assertTrue(echoResult.isSuccess)

        // Query by OldSchool mode - should get OldSchool PR
        val oldSchoolPR = repository.getBestWeightPR(exerciseId, "OldSchool")
        assertNotNull(oldSchoolPR)
        assertEquals(35f, oldSchoolPR.weightPerCableKg)
        assertEquals("OldSchool", oldSchoolPR.workoutMode)

        // Query by Echo mode - should get Echo PR
        val echoPR = repository.getBestWeightPR(exerciseId, "Echo")
        assertNotNull(echoPR)
        assertEquals(50f, echoPR.weightPerCableKg)
        assertEquals("Echo", echoPR.workoutMode)

        // They should be different values
        assertNotEquals(oldSchoolPR.weightPerCableKg, echoPR.weightPerCableKg)
    }

    @Test
    fun `PRs tracked separately per mode - same exercise different modes`() = runTest {
        // Insert PRs for multiple modes
        repository.updatePRsIfBetter(exerciseId, 30f, 12, "OldSchool", 1000L)
        repository.updatePRsIfBetter(exerciseId, 45f, 6, "Pump", 2000L)
        repository.updatePRsIfBetter(exerciseId, 40f, 8, "TUT", 3000L)
        repository.updatePRsIfBetter(exerciseId, 55f, 5, "Echo", 4000L)

        // Each mode should have its own distinct PR
        val oldSchoolPR = repository.getBestWeightPR(exerciseId, "OldSchool")
        val pumpPR = repository.getBestWeightPR(exerciseId, "Pump")
        val tutPR = repository.getBestWeightPR(exerciseId, "TUT")
        val echoPR = repository.getBestWeightPR(exerciseId, "Echo")

        assertEquals(30f, oldSchoolPR?.weightPerCableKg)
        assertEquals(45f, pumpPR?.weightPerCableKg)
        assertEquals(40f, tutPR?.weightPerCableKg)
        assertEquals(55f, echoPR?.weightPerCableKg)
    }

    @Test
    fun `querying non-existent mode returns null`() = runTest {
        // Insert PR for OldSchool only
        repository.updatePRsIfBetter(exerciseId, 40f, 10, "OldSchool", 1000L)

        // Query for Echo mode should return null
        val echoPR = repository.getBestWeightPR(exerciseId, "Echo")
        assertNull(echoPR)
    }

    // ========== Test 2: getAllPRsForExercise returns all modes ==========

    @Test
    fun `getAllPRsForExercise returns PRs for all modes`() = runTest {
        // Insert PRs for multiple modes
        repository.updatePRsIfBetter(exerciseId, 35f, 10, "OldSchool", 1000L)
        repository.updatePRsIfBetter(exerciseId, 50f, 8, "Echo", 2000L)
        repository.updatePRsIfBetter(exerciseId, 42f, 6, "Pump", 3000L)

        // Get all PRs for exercise
        val allPRs = repository.getAllPRsForExercise(exerciseId)

        // Should have 6 PRs (2 per mode: MAX_WEIGHT and MAX_VOLUME)
        assertEquals(6, allPRs.size)

        // Verify all modes are represented
        val modes = allPRs.map { it.workoutMode }.toSet()
        assertTrue(modes.contains("OldSchool"))
        assertTrue(modes.contains("Echo"))
        assertTrue(modes.contains("Pump"))
    }

    @Test
    fun `getAllPRsForExercise returns both PR types per mode`() = runTest {
        // Insert PR (will create both MAX_WEIGHT and MAX_VOLUME)
        repository.updatePRsIfBetter(exerciseId, 40f, 10, "OldSchool", 1000L)

        val allPRs = repository.getAllPRsForExercise(exerciseId)

        // Should have both PR types
        val prTypes = allPRs.map { it.prType }.toSet()
        assertTrue(prTypes.contains(PRType.MAX_WEIGHT))
        assertTrue(prTypes.contains(PRType.MAX_VOLUME))
    }

    @Test
    fun `getAllPRsForExercise returns empty for unknown exercise`() = runTest {
        val allPRs = repository.getAllPRsForExercise("unknown-exercise")
        assertTrue(allPRs.isEmpty())
    }

    @Test
    fun `getPRsForExercise flow returns all modes`() = runTest {
        // Insert PRs for multiple modes
        repository.updatePRsIfBetter(exerciseId, 35f, 10, "OldSchool", 1000L)
        repository.updatePRsIfBetter(exerciseId, 50f, 8, "Echo", 2000L)

        // Get PRs via Flow
        val allPRs = repository.getPRsForExercise(exerciseId).first()

        // Should contain PRs from both modes
        val modes = allPRs.map { it.workoutMode }.toSet()
        assertTrue(modes.contains("OldSchool"))
        assertTrue(modes.contains("Echo"))
    }

    // ========== Test 3: Mode-specific upsert updates only that mode ==========

    @Test
    fun `mode-specific upsert updates only that mode`() = runTest {
        // Insert PR for OldSchool at 35kg
        repository.updatePRsIfBetter(exerciseId, 35f, 10, "OldSchool", 1000L)

        // Insert PR for Echo at 50kg
        repository.updatePRsIfBetter(exerciseId, 50f, 8, "Echo", 2000L)

        // Update OldSchool to 40kg (improvement)
        repository.updatePRsIfBetter(exerciseId, 40f, 10, "OldSchool", 3000L)

        // Verify Echo is still 50kg (unchanged)
        val echoPR = repository.getBestWeightPR(exerciseId, "Echo")
        assertEquals(50f, echoPR?.weightPerCableKg)

        // Verify OldSchool is now 40kg (updated)
        val oldSchoolPR = repository.getBestWeightPR(exerciseId, "OldSchool")
        assertEquals(40f, oldSchoolPR?.weightPerCableKg)
    }

    @Test
    fun `update one mode does not affect other modes`() = runTest {
        // Setup initial PRs for all modes
        repository.updatePRsIfBetter(exerciseId, 30f, 10, "OldSchool", 1000L)
        repository.updatePRsIfBetter(exerciseId, 35f, 10, "Pump", 1000L)
        repository.updatePRsIfBetter(exerciseId, 40f, 10, "TUT", 1000L)
        repository.updatePRsIfBetter(exerciseId, 45f, 10, "Echo", 1000L)

        // Update only Pump mode to higher weight
        repository.updatePRsIfBetter(exerciseId, 50f, 10, "Pump", 2000L)

        // Verify only Pump changed
        assertEquals(30f, repository.getBestWeightPR(exerciseId, "OldSchool")?.weightPerCableKg)
        assertEquals(50f, repository.getBestWeightPR(exerciseId, "Pump")?.weightPerCableKg) // Updated
        assertEquals(40f, repository.getBestWeightPR(exerciseId, "TUT")?.weightPerCableKg)
        assertEquals(45f, repository.getBestWeightPR(exerciseId, "Echo")?.weightPerCableKg)
    }

    @Test
    fun `lower weight does not update existing PR for mode`() = runTest {
        // Insert PR for OldSchool at 40kg
        repository.updatePRsIfBetter(exerciseId, 40f, 10, "OldSchool", 1000L)

        // Try to "update" with lower weight
        repository.updatePRsIfBetter(exerciseId, 35f, 10, "OldSchool", 2000L)

        // Verify OldSchool is still 40kg (not downgraded)
        val oldSchoolPR = repository.getBestWeightPR(exerciseId, "OldSchool")
        assertEquals(40f, oldSchoolPR?.weightPerCableKg)
    }

    // ========== Additional edge case tests ==========

    @Test
    fun `getLatestPR returns most recent PR for mode`() = runTest {
        // Insert multiple PRs for same mode at different times
        repository.updatePRsIfBetter(exerciseId, 35f, 10, "OldSchool", 1000L)
        repository.updatePRsIfBetter(exerciseId, 40f, 10, "OldSchool", 2000L)

        val latestPR = repository.getLatestPR(exerciseId, "OldSchool")
        assertNotNull(latestPR)
        assertEquals(2000L, latestPR.timestamp)
    }

    @Test
    fun `getWeightPR and getVolumePR return correct PR types for mode`() = runTest {
        // Insert PR that creates both types
        repository.updatePRsIfBetter(exerciseId, 40f, 8, "OldSchool", 1000L)

        val weightPR = repository.getWeightPR(exerciseId, "OldSchool")
        val volumePR = repository.getVolumePR(exerciseId, "OldSchool")

        assertNotNull(weightPR)
        assertNotNull(volumePR)
        assertEquals(PRType.MAX_WEIGHT, weightPR.prType)
        assertEquals(PRType.MAX_VOLUME, volumePR.prType)
    }

    @Test
    fun `getBestPR returns highest volume across all modes`() = runTest {
        // Insert PRs with different volumes (weight * 2 * reps)
        // OldSchool: 30kg * 2 * 10 = 600kg volume
        repository.updatePRsIfBetter(exerciseId, 30f, 10, "OldSchool", 1000L)
        // Echo: 50kg * 2 * 8 = 800kg volume
        repository.updatePRsIfBetter(exerciseId, 50f, 8, "Echo", 2000L)

        val bestPR = repository.getBestPR(exerciseId)
        assertNotNull(bestPR)
        assertEquals(800f, bestPR.volume)
        assertEquals("Echo", bestPR.workoutMode)
    }

    @Test
    fun `getBestWeightPR without mode returns highest weight across all modes`() = runTest {
        repository.updatePRsIfBetter(exerciseId, 30f, 10, "OldSchool", 1000L)
        repository.updatePRsIfBetter(exerciseId, 55f, 6, "Echo", 2000L)
        repository.updatePRsIfBetter(exerciseId, 45f, 8, "Pump", 3000L)

        val bestWeightPR = repository.getBestWeightPR(exerciseId)
        assertNotNull(bestWeightPR)
        assertEquals(55f, bestWeightPR.weightPerCableKg)
    }

    @Test
    fun `getBestVolumePR without mode returns highest volume across all modes`() = runTest {
        // OldSchool: 30kg * 2 * 15 = 900kg
        repository.updatePRsIfBetter(exerciseId, 30f, 15, "OldSchool", 1000L)
        // Echo: 50kg * 2 * 8 = 800kg
        repository.updatePRsIfBetter(exerciseId, 50f, 8, "Echo", 2000L)

        val bestVolumePR = repository.getBestVolumePR(exerciseId)
        assertNotNull(bestVolumePR)
        assertEquals(900f, bestVolumePR.volume)
    }

    @Test
    fun `updatePRsIfBetter returns list of broken PR types`() = runTest {
        // First PR should break both types
        val result1 = repository.updatePRsIfBetter(exerciseId, 40f, 10, "OldSchool", 1000L)
        assertTrue(result1.isSuccess)
        val broken1 = result1.getOrNull()!!
        assertTrue(broken1.contains(PRType.MAX_WEIGHT))
        assertTrue(broken1.contains(PRType.MAX_VOLUME))

        // Higher weight but same volume = only weight PR
        val result2 = repository.updatePRsIfBetter(exerciseId, 45f, 8, "OldSchool", 2000L)
        val broken2 = result2.getOrNull()!!
        assertTrue(broken2.contains(PRType.MAX_WEIGHT))
        // Volume: 45 * 2 * 8 = 720 < 40 * 2 * 10 = 800, so no volume PR
    }

    @Test
    fun `multiple exercises have independent mode-specific PRs`() = runTest {
        val exercise1 = "bench-press"
        val exercise2 = "squat"

        // Set PRs for both exercises in different modes
        repository.updatePRsIfBetter(exercise1, 40f, 10, "OldSchool", 1000L)
        repository.updatePRsIfBetter(exercise1, 50f, 8, "Echo", 1000L)
        repository.updatePRsIfBetter(exercise2, 60f, 10, "OldSchool", 1000L)
        repository.updatePRsIfBetter(exercise2, 80f, 6, "Echo", 1000L)

        // Verify each exercise has its own mode-specific PRs
        assertEquals(40f, repository.getBestWeightPR(exercise1, "OldSchool")?.weightPerCableKg)
        assertEquals(50f, repository.getBestWeightPR(exercise1, "Echo")?.weightPerCableKg)
        assertEquals(60f, repository.getBestWeightPR(exercise2, "OldSchool")?.weightPerCableKg)
        assertEquals(80f, repository.getBestWeightPR(exercise2, "Echo")?.weightPerCableKg)
    }

    @Test
    fun `addRecord directly respects mode separation`() = runTest {
        // Use addRecord helper method directly
        repository.addRecord(
            PersonalRecord(
                id = 1,
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                weightPerCableKg = 35f,
                reps = 10,
                oneRepMax = 45f,
                timestamp = 1000L,
                workoutMode = "OldSchool",
                prType = PRType.MAX_WEIGHT,
                volume = 700f
            )
        )
        repository.addRecord(
            PersonalRecord(
                id = 2,
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                weightPerCableKg = 55f,
                reps = 8,
                oneRepMax = 65f,
                timestamp = 2000L,
                workoutMode = "Echo",
                prType = PRType.MAX_WEIGHT,
                volume = 880f
            )
        )

        val oldSchoolPR = repository.getBestWeightPR(exerciseId, "OldSchool")
        val echoPR = repository.getBestWeightPR(exerciseId, "Echo")

        assertEquals(35f, oldSchoolPR?.weightPerCableKg)
        assertEquals(55f, echoPR?.weightPerCableKg)
    }
}
