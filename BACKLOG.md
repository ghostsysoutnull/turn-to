# Backlog

Audited: 2026-04-16 (second pass). Previous nine items all closed. New items identified
from run-report analysis and acceptance test work.

---

## Tier 1 — Wrong or misleading output

### B4-1: Runner uses default SKILL/STAMINA when adventure doesn't configure fixed values

When `config.fixedSkill()` / `config.fixedStamina()` are absent (the normal case), the
runner leaves SKILL and STAMINA at 0 in `SimulatedGameState`. When combat fires, lines
235–236 of `RunSimulator` detect the zero, emit a warning, and silently substitute 10/10.
Every combat outcome in every run report is wrong as a result.

The run report for the iron road already shows the symptom:
```
⚠ SKILL uninitialised — combat used default value 10
⚠ STAMINA uninitialised — combat used default value 10
```

- **Where:** `src/main/java/com/tas/neo/analysis/RunSimulator.java` lines 89–94 — the
  `run()` method initialises state but only applies stats from `config`, never from the
  adventure's own stat formulas
- **What's needed:**
  - `Adventure` (or the JSON) needs to carry stat range definitions (the same `1d6+6` /
    `2d6+12` formulas that `Game.createPlayer()` uses)
  - `RunSimulator.run()` rolls initial SKILL, STAMINA, LUCK from those formulas using
    the run's seeded dice instance, unless overridden by `config.fixedSkill/Stamina`
  - The SKILL/STAMINA warning lines (235–236) become unreachable and can be removed
- **Dependency:** The adventure JSON format needs a `stats` block if it doesn't already
  carry stat definitions. Check `Adventure` domain model first.

---

## Tier 2 — Incomplete features

### B4-2: Runner CYCLE report omits the termination section

When a run ends as CYCLE, the report records the count but not which section was the
cycle root. To diagnose a cycle an author must cross-reference the section graph manually.

The iron road has 5 CYCLE runs out of 50 — a high enough rate to indicate a real structural
loop — but the report gives no hint of where it is.

- **Where:** `src/main/java/com/tas/neo/analysis/RunSimulator.java` lines 111–113 —
  `RunOutcome.CYCLE` is returned with `currentSection` (the section that tripped the
  threshold), but `AdventureReportGenerator` does not include that section number in the
  ENDINGS table
- **What's needed:**
  - `RunOutcome.CYCLE` entries in the report should include the cycle root section, e.g.
    `CYCLE §N count (pct%)` matching the format of other ENDINGS rows
  - Consider also listing the most frequently cycled sections in an ISSUES note so authors
    can locate and break the loop

### B4-3: Iron road has no gold source — HAS_GOLD conditions permanently inaccessible

The iron road has six `GOLD_CHANGE` events: four are negative (purchases), two are at
VICTORY sections (`§104 +20`, `§151 +200`). There is no section that awards gold before
the player reaches a win condition. Fourteen choices gated on `HAS_GOLD` are permanently
unreachable in any run, and the runner correctly flags all of them in GATING.

This is either an adventure design oversight (gold was planned but the earning mechanism
was never authored) or an intentional stub awaiting a future chapter.

- **Where:** `adventures/the-iron-road.json` — no section with a positive `GOLD_CHANGE`
  before the VICTORY endings; confirmed with `AdventureSectionInspector --has-event GOLD_CHANGE`
- **What's needed (investigate first):**
  - Determine whether gold earning was intended — check chapter gate contracts for any
    `gold` entries in `guarantees` or `possible` fields (`the-iron-road-gates.txt`)
  - If gold earning was intended, identify which chapter and section should award it and
    author the event
  - If gold earning was intentionally deferred, document the design decision in the adventure
    description and mark the `HAS_GOLD` choices with a note so the GATING section doesn't
    flag them as structural errors

---

## Tier 3 — Polish and content

### B4-4: Iron road ch4 coverage is low and needs a dedicated run

Coverage report: 38% of runs reach ch4, and 20 of 35 ch4 sections are unreached in 50
runs. Some of this is explained by 48% of runs dying at §4 in ch1 (standing ground at the
opening encounter). The rest may indicate structural gaps in ch4 or inaccessible branches.

- **Where:** `adventures/the-iron-road-run-report.txt` — unreached ch4 sections listed;
  `adventures/the-iron-road-report.txt` — chapter coverage summary
- **What's needed:**
  - Run the runner with `--strategy ITEM_SEEKING` and `--runs 500` to increase ch4 reach
  - Review unreached ch4 sections in the digest (`the-iron-road-digest-ch4.txt`) for dead
    branches or missing inbound references
  - Use `AdventureSectionInspector --refs §N` on each unreached section to confirm it has
    at least one valid inbound path

### B4-5: Warlock of Firetop Mountain adventure is a skeleton

The adventure has 6 sections (§1–§5 plus §400 VICTORY), no chapters, and no grid. It is
documented as a skeleton but remains in the adventures directory alongside complete
adventures. Its presence in test fixtures makes it harder to distinguish test scaffolding
from playable content.

- **Where:** `adventures/the-warlock-of-firetop-mountain.json`
- **Options:**
  - Author it fully using the four-agent authoring pipeline (spec 8, workflow docs) —
    appropriate if it is the next content target
  - Move it to `src/test/resources/` where it belongs as a test fixture, and remove it
    from the `adventures/` directory — appropriate if content work is not imminent

---

## Closed

All items below were resolved on 2026-04-16.

### B1-1: Runner reports STUCK for grid-entry adventures ✓
Grid-entry runs now terminate as `GRID_ENTRY` outcome rather than `STUCK`. Vaults of
Stonebridge report now shows `ISSUES: none ✓`.

### B1-2: Party member conditions always evaluate to false in simulation ✓
`SimulatedGameState` now tracks party member lifecycle. `ConditionEvaluator` evaluates
`PARTY_MEMBER_ACTIVE/WAITING/REMOVED` against simulated state. `RunSimulator.run()`
initialises party state from `adventure.partyMemberDefinitions()`.

### B2-1: Session log text format — item and stat events ✓
`HookDispatcher.processEvent()` logs `ItemGained`, `ItemLost`, and `StatChanged` events.
`FileGameLogger.formatTextLog()` renders them as `ITEM_GAIN:`, `ITEM_LOSS:`, `STAT_CHANGE:`.

### B2-2: Session log text format — combat round summary ✓
`CombatResult` record added. `PersonalCombatSystem` returns it inside `CombatOutcome`.
`HookDispatcher` logs `CombatResolved` after each fight. Text log renders opponent stats,
outcome, rounds, and STAMINA lost.

### B2-3: Spec coverage — analysis tools have no spec documents ✓
`docs/specs/12-analysis-tools.md` written. Covers all five tools with report formats,
field meanings, event/condition notation, and a decision table.

### B3-1: Spec terminology mismatch — HAS_FLAG / LACKS_FLAG ✓
Spec 11 updated to use `STATE_EQUALS` / `STATE_NOT_EQUALS` throughout.

### B3-2: Warlock adventure not marked as skeleton ✓
Description field updated to explicitly identify the adventure as a skeleton/demo.

### B3-3: Vaults of Stonebridge runner incompatibility ✓
Unblocked by B1-1. Vaults now reports `ISSUES: none ✓`.

### B3-4: onLoad script state binding was broken ✓
`HookDispatcher.runScript()` called the two-arg `execute()` which never bound `scriptState`
to Lua globals. All adventure-level scripts were silently failing. Fixed by adding a
three-arg `execute(script, ctx, scriptState)` default to `ScriptEngine` and updating
`runScript()` to use it. Discovered by `IronRoadAcceptanceTest`.
