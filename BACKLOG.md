# Backlog

Audited: 2026-04-16. Three tiers by impact. Carries forward deferred items from GAPS.md plus newly identified gaps.

---

## Tier 1 — Wrong or misleading output

### B1-1: Runner reports STUCK for grid-entry adventures

The runner terminates a run as STUCK when it encounters a `GridTarget` choice (spec 11 acknowledges
grid navigation is not simulated). For `the-vaults-of-stonebridge`, this produces 100% STUCK runs
and "No VICTORY reached in 100 runs" in ISSUES — which reads as a broken adventure when the
adventure is structurally sound.

- **Where:** `src/main/java/com/tas/neo/analysis/RunSimulator.java` lines ~169–175 — the
  `GridTarget` branch in the choice navigation switch
- **What's needed:**
  - Track grid-entry terminations separately from genuine STUCK (no exits) terminations
  - Add `GRID_ENTRY` outcome to `RunOutcome` enum, or count grid-entry runs as a distinct stat
  - Report section: show grid-entry run count as a neutral note, not an error
  - Remove "No VICTORY reached" error when all non-victory outcomes are grid-entry terminations
- **Status:** [x] done — 2026-04-16

### B1-2: Party member conditions always evaluate to false in simulation

`ConditionEvaluator` returns `false` for all four party condition types
(`PARTY_MEMBER_ACTIVE`, `PARTY_MEMBER_WAITING`, `PARTY_MEMBER_REMOVED`, `PARTY_STAT`). Any
choice gated on party state is permanently invisible to the runner — it never appears in
`everAvailable`, so the GATING section will flag it as "condition never met" even if the
condition is perfectly reachable in real play.

- **Where:** `src/main/java/com/tas/neo/analysis/ConditionEvaluator.java` lines 38–41
- **What's needed:**
  - `SimulatedGameState` needs party member state tracking (currently absent)
  - `RunSimulator.processEvents()` needs to apply party-member lifecycle events (add/remove member
    scripted in `onEnter`) to simulated state
  - `ConditionEvaluator` can then evaluate party conditions against simulated state
- **Note:** No current adventure uses party-gated choices, so this does not produce false positives
  today. Becomes urgent as soon as any adventure gates choices on party state.
- **Status:** [x] done — 2026-04-16

---

## Tier 2 — Incomplete features

### B2-1: Session log text format — item and stat events (deferred from T2-1)

The text log records narrative, victory, and game-over text but omits item gains/losses and stat
changes. An agent reading a session log cannot tell what the player picked up or how their stats
changed at each section.

- **Where:**
  - `src/main/java/com/tas/neo/engine/HookDispatcher.java` — `processEvent()` handles `ItemEvent`
    and `StatChangeEvent` but does not call `logger.logEvent()`
  - `src/main/java/com/tas/neo/io/OutputEvent.java` — needs `ItemGained`, `ItemLost`,
    `StatChanged` variants
  - `src/main/java/com/tas/neo/io/FileGameLogger.java` — `formatTextLog()` needs to render them
- **What's needed (text log lines):**
  - `  ITEM_GAIN: Guard's Pass`
  - `  ITEM_LOSS: Torch`
  - `  STAT_CHANGE: STAMINA -4 (now 14)`
- **Spec:** `docs/specs/10-session-logging.md`
- **Status:** [ ] open

### B2-2: Session log text format — combat round summary (deferred from T2-1)

The text log has no record of combat outcomes. An agent reading a session log cannot tell whether
the player won a fight, how many rounds it took, or how much STAMINA they lost.

- **Where:**
  - `src/main/java/com/tas/neo/mechanics/CombatEngine.java` — resolves combat but returns only
    outcome, not a structured result with rounds and damage
  - `src/main/java/com/tas/neo/engine/HookDispatcher.java` — dispatches combat events but receives
    no structured result to log
- **What's needed:**
  - New `CombatResult` record: `opponent name`, `outcome (VICTORY/DEFEAT)`, `rounds`, `staminaLost`
  - `CombatEngine` and `CombatSystem` interface return `CombatResult` instead of bare boolean/int
  - `HookDispatcher` calls `logger.logEvent(new OutputEvent.CombatResolved(...))` after each fight
  - Text log renders: `  COMBAT: Gate Guard (SKILL 7, STAMINA 8) — PLAYER VICTORY (4 rounds, -6 STAMINA)`
- **Note:** More invasive than B2-1 — touches the combat system interface. Should be its own session.
- **Spec:** `docs/specs/10-session-logging.md`
- **Status:** [ ] open

### B2-3: Spec coverage — analysis tools have no spec documents

`AdventureStateReport`, `AdventureItemReport`, `AdventureGateDigest`, and `AdventureSectionDigest`
are production tools in `src/main/java/com/tas/neo/analysis/` with no corresponding spec documents.
The adventure runner has spec 11 and design 14; the companion tools have nothing.

This violates the project's spec-first principle and means the tools' contracts are readable only
from the source code.

- **Where:** `docs/specs/` — no spec 12 or 13 (numbering has room)
- **What's needed:** One spec document covering all four tools: purpose, report format, what each
  section means, when to use which tool. Can mirror the format of spec 11.
- **Status:** [ ] open

---

## Tier 3 — Polish and content

### B3-1: Spec terminology mismatch — HAS_FLAG / LACKS_FLAG

Spec 11 (adventure runner) uses condition names `HAS_FLAG` and `LACKS_FLAG` to describe boolean
state variable checks. The implementation uses `STATE_EQUALS` and `STATE_NOT_EQUALS` (which are
more general: they compare state to any value, not just `true`). The loader's JSON condition type
strings match the implementation (`STATE_EQUALS`), not the spec.

- **Where:**
  - `docs/specs/11-adventure-runner.md` — Choice Filtering table
  - `src/main/java/com/tas/neo/loader/JsonAdventureLoader.java` — `parseCondition()`
- **Options:**
  - Update spec 11 to use `STATE_EQUALS` / `STATE_NOT_EQUALS` terminology (preferred — spec catches up to the richer implementation)
  - Or add `HAS_FLAG` / `LACKS_FLAG` as aliases in the loader (backwards-compat sugar)
- **Status:** [ ] open

### B3-2: the-warlock-of-firetop-mountain is a skeleton

The adventure file has 6 sections (1–5 plus §400 as VICTORY), no chapters, and no grid. It exists
in the adventures directory and is referenced in test infrastructure, but is not a playable
adventure.

- **Where:** `adventures/the-warlock-of-firetop-mountain.json`
- **Options:** Author it fully using the pipeline, or explicitly mark it as a
  reference/demo skeleton in the file so it is not confused with a complete adventure.
- **Status:** [ ] open

### B3-3: the-vaults-of-stonebridge runner incompatibility note

The vaults adventure routes through a grid at §15, making the runner report 100% STUCK. The
adventure is structurally sound (consistency check passes, all sections have exits). The run report
is misleading as-is, but this is a symptom of B1-1 rather than an adventure defect.

- **Dependency:** B1-1 must be resolved first; then regenerate the vaults run report.
- **Status:** [x] unblocked — B1-1 done; vaults now reports `ISSUES: none ✓`
