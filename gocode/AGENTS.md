<!-- OPENSPEC:START -->
# OpenSpec Instructions (gocode/)

This directory contains a standalone Go prototype (TUI + BLE) for controlling a Vitruvian device on macOS.

If you are going to implement new behavior, change device-control semantics, or add new capabilities, follow the repo’s OpenSpec workflow first (see `openspec/AGENTS.md`). Do not implement unapproved proposals that change behavior or safety characteristics.
<!-- OPENSPEC:END -->

# gocode/ Local Instructions

## Safety (non-negotiable)

- Always provide an immediate “stop” action in any interactive app:
  - **Soft stop**: write `0x50 0x00` (releases tension / clears faults while keeping polling alive).
  - **Hard stop/reset** (when ending a session): write `0x0A 0x00 0x00 0x00`.
- Never start motors implicitly. Starting a set MUST require explicit user intent (e.g., “ARM + START” or an interactive confirmation).
- Default behavior should be conservative:
  - Start in read-only/telemetry mode.
  - Require explicit enabling of auto-start behaviors.

## BLE/GATT correctness

- Do not run concurrent GATT operations. Implement a strict single-flight operation queue (serial executor / mutex).
- Use **Write With Response** for TX writes (`6e400002-...`). Do not assume Write Without Response works.
- Poll Monitor via `read()` in a sequential loop with a timeout. Treat repeated timeouts as a degraded link and disconnect/reconnect.
- Prefer robust cancellation and cleanup:
  - All loops MUST stop on context cancellation.
  - Always unsubscribe/close on exit.

## Code hygiene

- Keep the Go code small, readable, and dependency-light. Avoid adding frameworks unless necessary.
- Keep protocol constants and frame builders in one place (e.g., `protocol/`), and do not scatter UUIDs through UI code.
- Log to stderr; never log raw secrets (if any are introduced later).

