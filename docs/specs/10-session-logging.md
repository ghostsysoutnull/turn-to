# Spec: Session Logging

## Overview

Every adventure run produces two log artefacts: a **text session log** for human reading and a **structured session log** for programmatic assessment. Both are written when the session ends, whether by victory, game over, or error. The engine continues running when errors occur — errors are recorded and the session proceeds.

---

## Session Text Log

A human-readable narrative of the session. Records, in order:

- The adventure title and start time
- Each location visited (section or grid cell) with its narrative text
- The choice the player made at each location
- Combat outcomes (won/lost, rounds fought)
- Items gained or lost
- Stat changes
- Any errors that occurred, with context
- The final result (victory, game over, or abandoned) and total step count

The text log is the record an author or tester reads to understand what happened during a run.

### Format

```
=== THE WARLOCK OF FIRETOP MOUNTAIN ===
Started: 2026-04-14 10:32:15

[Section 1] You stand before the entrance to Firetop Mountain...
  → Entered the mountain  (→ Section 2)

[Section 2] Inside the cave...
  COMBAT: Goblin (SKILL 5, STAMINA 6) — PLAYER VICTORY (3 rounds, -4 STAMINA)
  → Search the body  (→ Section 3)

[Grid: dungeon-level-1 (0,0,0)] Cold air rushes past as you step inside.
  → Go east  (→ (1,0,0))

[Grid: dungeon-level-1 (1,0,0)] A guard post, long since abandoned.
  → Go north  (→ Section 55)

[Section 400] VICTORY — You have defeated the Warlock!

─────────────────────────────────────
Result:      VICTORY
Steps:       23
Errors:      0
```

---

## Structured Session Log

A machine-readable record of the same run. Intended for programmatic assessment by agents and tooling. Contains all information in the text log in a queryable format.

### Top-level fields

| Field | Type | Description |
|-------|------|-------------|
| `adventureId` | string | The adventure that was played |
| `result` | string | `"VICTORY"`, `"GAME_OVER"`, or `"ABANDONED"` |
| `stepsCount` | int | Total number of locations visited |
| `path` | list | Ordered list of navigation entries |
| `events` | list | Ordered list of all game events that occurred |
| `errors` | list | Structured error entries (empty if no errors) |

### Navigation entry

| Field | Description |
|-------|-------------|
| `from` | Location the player left (e.g. `"section:42"`, `"grid:dungeon:1,2,0"`) |
| `to` | Location the player arrived at |
| `via` | The choice text that triggered the navigation |

### Error entry

An error entry is written whenever a recoverable fault occurs during the session. The session continues after an error is recorded.

| Field | Description |
|-------|-------------|
| `source` | Where the error occurred (e.g. `"section-42-onEnter"`, `"grid-dungeon-cell(1,2,0)-onChoices"`) |
| `type` | Category: `"ScriptError"`, `"NavigationError"` |
| `message` | The fault message |
| `snapshot` | Player state at the time of the error |

### Player snapshot (within an error entry)

| Field | Description |
|-------|-------------|
| `location` | Current location string |
| `stamina`, `skill`, `luck`, `gold` | Current values |
| `inventory` | List of item names currently carried |

---

## Log File Naming and Location

Session logs are written to a `sessions/` directory at the project root.

| File | Naming |
|------|--------|
| Text log | `sessions/<adventureId>-<timestamp>.txt` |
| Structured log | `sessions/<adventureId>-<timestamp>.json` |

Both files are written together at the end of the session. If the `sessions/` directory does not exist, it is created.

---

## Test Runs

When an adventure is run in test context (via the test scenario runner), session logs are captured in memory and not written to disk unless the test explicitly requests it. A test that fails due to an in-game error can inspect the structured log to diagnose the fault without re-running the adventure.

---

## See Also

- **Spec: Adventure Structure** — sections and events that generate log entries
- **Spec: Scripting** — script errors that generate error log entries
- **Spec: Location Networks** — grid locations recorded in navigation entries
