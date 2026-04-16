# Implementation Gaps

Audited: 2026-04-16. Three tiers by impact. Each item follows the standard pipeline: Design → Test → Code.

---

## Tier 1 — Breaks gameplay

### T1-1: Party member script integration (specs 05, 07)

`DefaultScriptContext.addPartyMember()` and `removePartyMember()` are no-ops. The domain model (states, stats, defeat consequences) is complete; scripts cannot drive it. Any adventure that activates or dismisses a party member via a script is silently broken at runtime.

- **Where:** `src/main/java/com/tas/neo/scripting/DefaultScriptContext.java` lines ~107–112
- **Domain model:** `com.tas.neo.domain.party` — complete; `PartyMember`, `MemberState`, `DefeatConsequence` all exist
- **What's needed:**
  - `addPartyMember(id)` — look up member in `GameState`; if WAITING → ACTIVE; if REMOVED → restore stats, ACTIVE; if already ACTIVE → no-op
  - `removePartyMember(id)` — ACTIVE → REMOVED; must NOT trigger `onDefeat`
  - `getPartyMember(id)` — return real proxy bound to the live `PartyMember` instance, not `PartyMemberProxy.unknown()`
- **Specs:** `docs/specs/05-scripting.md` (ScriptContext API), `docs/specs/07-party-members.md` (lifecycle)
- **Status:** [x] done — 2026-04-16

---

## Tier 2 — Incomplete features

### T2-1: Session log text format (spec 10)

The JSON session log is mostly complete. The human-readable `.txt` log is sparse — it records navigation but omits narrative text, combat outcomes, and item/stat changes.

- **Where:** `src/main/java/com/tas/neo/io/FileGameLogger.java` — `writeTxt()` method
- **What's needed:**
  - ~~Narrative text per section~~ — done: `[Section N] narrative` and `[Grid: id (x,y,z)] narrative`
  - Combat lines: `COMBAT: Gate Guard (SKILL 7, STAMINA 8) — PLAYER VICTORY (4 rounds, -6 STAMINA)` — deferred; needs richer event type or aggregation
  - Item events: `ITEM_GAIN: Guard's Pass` — deferred; needs `logEvent` wiring in HookDispatcher
  - Stat changes: `STAT_CHANGE: STAMINA -4 (now 14)` — deferred; same
- **Spec:** `docs/specs/10-session-logging.md`
- **Status:** [x] partial — 2026-04-16 (narrative done; combat, items, stats deferred)

### T2-2: Adventure Runner report — missing sections (spec 11)

The runner simulates correctly. Three report sections defined in spec 11 are not generated.

- **Where:** `src/main/java/com/tas/neo/analysis/RunReportGenerator.java`
- **What's needed:**
  - `ITEM FLOW AT CHAPTER BOUNDARIES` — carry rates per item at each chapter gate
  - `GOLD AND STATE DISTRIBUTION AT CHAPTER BOUNDARIES` — gold range and state variable values at each gate
  - `GATING` — choices with conditions that were never met across all N runs (dead gates)
- **Data available:** `RunBatchResult`, `ChapterSnapshot` already accumulate inventory and state at boundaries; gating requires tracking condition evaluations per run
- **Spec:** `docs/specs/11-adventure-runner.md`
- **Status:** [x] done — 2026-04-16

---

## Tier 3 — Polish

### T3-1: Grid load-time validation (spec 09)

`JsonAdventureLoader` does not validate grid structure at load time.

- **Missing checks:**
  - Cell (x, y, z) within declared grid dimensions
  - No duplicate cells at the same (x, y, z)
  - No duplicate cell ids within a grid
  - Passage target coordinates exist within the same grid
  - `toSection` in passages references a declared section
- **Where:** `src/main/java/com/tas/neo/loader/JsonAdventureLoader.java` — `validateGridDto()` method (exists, add checks)
- **Spec:** `docs/specs/09-location-networks.md`
- **Status:** [ ] open

### T3-2: Default passage labels (spec 09)

Passages with no explicit label display the direction enum name (e.g. `NORTH`) instead of a human-readable label (`Go north`).

- **Where:** `src/main/java/com/tas/neo/engine/Game.java` line ~203 — `passage.label().orElse(entry.getKey().name())`
- **Fix:** Add `defaultLabel()` to `Direction` enum (e.g. `NORTH → "Go north"`) or format inline
- **Spec:** `docs/specs/09-location-networks.md`
- **Status:** [x] done — 2026-04-16

### T3-3: Grid location format in error snapshots (spec 10)

`PlayerSnapshot.locationString()` returns `"grid:<id>"` when the player is inside a grid. Spec requires `"grid:<id>:<x>,<y>,<z>"`.

- **Where:** `src/main/java/com/tas/neo/domain/log/PlayerSnapshot.java`
- **Fix:** Include current cell coordinates when location is a grid cell
- **Spec:** `docs/specs/10-session-logging.md`
- **Status:** [x] done — 2026-04-16

### T3-4: Adventure Runner report header format (spec 11)

Minor format mismatch vs. spec. Current header omits date and dice mode label.

- **Spec format:** `Generated: <date>  |  Runs: <N>  |  Strategy: <strategy>  |  Dice: <mode>  |  Seed: <seed>`
- **Where:** `src/main/java/com/tas/neo/analysis/RunReportGenerator.java`
- **Status:** [x] done — 2026-04-16
