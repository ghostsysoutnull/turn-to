# Spec 11 — Adventure Runner

## Purpose

The Adventure Runner executes an adventure automatically, without human input, by simulating player decisions according to a configurable strategy. It runs the adventure N times and produces a compact structured report that identifies coverage gaps, ending distributions, item flow patterns, and potential accessibility issues.

The runner is an analysis tool, not a gameplay feature. It operates entirely outside the game engine's I/O layer and is intended for use by agents and developers to stress-test adventure content.

---

## Simulation Model

Each run maintains a **simulated game state** for the duration of one playthrough:

| State element | Description |
|---------------|-------------|
| Current section | The section number the player is currently at |
| Inventory | The set of items the player currently carries |
| Gold | The player's current gold amount |
| SKILL | The player's current SKILL stat |
| STAMINA | The player's current STAMINA stat |
| State variables | A key-value map of all Lua state variables set during play |
| Sections visited | Ordered list of section numbers visited this run |
| Chapter boundary snapshots | Inventory and state at each chapter exit section |

### Initialisation

At the start of each run, game state is initialised as follows:

- Current section: the adventure's declared start section
- Inventory: empty
- Gold: 0
- SKILL: drawn from the adventure's initial stat range (or a fixed value in deterministic mode)
- STAMINA: drawn from the adventure's initial stat range (or a fixed value in deterministic mode)
- State variables: the adventure's `onLoad` script is executed to set initial values
- The adventure's starting `events` (on the start section) are applied before the first choice

---

## Section Processing

When the runner enters a section, it processes it in this order:

1. Record the section in the visited list.
2. Apply the section's `events` in order (see Event Processing below).
3. Execute the section's `onEnter` script, if present (see Script Execution below).
4. If the section type is `VICTORY` or `INSTANT_DEATH`, the run ends. Record the ending.
5. Otherwise, collect the available choices (see Choice Filtering below).
6. If no choices are available and the section is `NORMAL`, the run ends as **STUCK** — a dead end. Record this as an issue.
7. Otherwise, select a choice according to the active strategy (see Choice Selection Strategies below).
8. Navigate to the target section and repeat.

### Cycle detection

If the runner visits the same section more than a configurable maximum number of times in a single run (default: 5), the run ends as **CYCLE** — a likely infinite loop. Record this as an issue.

---

## Event Processing

Events are applied to simulated game state in the order they appear on the section:

| Event type | Effect on simulated state |
|------------|--------------------------|
| `ITEM_GAIN` | Add item to inventory |
| `ITEM_LOSS` | Remove item from inventory (no-op if not carried) |
| `GOLD_CHANGE` | Add delta to gold total (floor at 0) |
| `STAT_CHANGE` | Apply delta to the named stat |
| `SKILL_TEST` | Resolve using dice strategy; navigate to success or failure section |
| `LUCK_TEST` | Resolve using dice strategy; navigate to success or failure section |
| `COMBAT` | Resolve using combat simulation; navigate to success or failure section |
| `NAVIGATE` | Navigate immediately to the target section |

When a SKILL_TEST, LUCK_TEST, COMBAT, or NAVIGATE event fires during section processing, navigation is immediate — no choices are presented for that section.

---

## Script Execution

`onEnter` scripts are executed against the simulated state. The runner supports the following script operations:

| Operation | Behaviour |
|-----------|-----------|
| `state.set(key, value)` | Set a state variable |
| `state.get(key)` | Read a state variable (returns the current simulated value) |
| `state.has(key)` | Check whether a state variable is set |
| `state.remove(key)` | Remove a state variable |
| `ctx.hasItem(name)` | Check the simulated inventory |
| `ctx.navigateTo(n)` | Navigate to section n immediately, bypassing choice selection |
| `ctx.showMessage(text)` | No-op in simulation — message text is discarded |
| `ctx.addChoice(text, target)` | Register a dynamic choice for this section |

`onChoices` scripts (dynamic choice injection) are executed before choice filtering. Any choices injected via `ctx.addChoice` are added to the available choice list before condition filtering.

Scripts that call unsupported operations are treated as no-ops for that operation. The runner does not fail on unsupported script calls; it logs them as warnings in the run report.

---

## Choice Filtering

Before a choice is presented to the selection strategy, it is filtered against the simulated game state:

| Condition type | Filter rule |
|----------------|------------|
| `HAS_ITEM` | Choice is available if the item is in the simulated inventory |
| `LACKS_ITEM` | Choice is available if the item is not in the simulated inventory |
| `HAS_GOLD` | Choice is available if gold >= the required amount |
| `HAS_FLAG` | Choice is available if the state variable is set to true |
| `LACKS_FLAG` | Choice is available if the state variable is not set to true |
| `SKILL_CONDITION` | Choice is available if SKILL >= the required value |
| No condition | Choice is always available |

A choice whose target section has already been visited more than the cycle detection threshold is excluded from selection in that run, unless it is the only available choice.

---

## Choice Selection Strategies

The strategy governs which available choice the runner selects at each section.

### `RANDOM`

Select uniformly at random from all available choices. This is the baseline strategy and the default.

### `ITEM_SEEKING`

Weight choices that have a satisfied `HAS_ITEM` condition twice as heavily as unconditioned choices. This biases the runner toward paths that exercise item-gated content, increasing the chance that item-dependent branches are explored.

### `HIGH_SUSPICION`

When a choice leads to a section whose `onEnter` script sets or increments the `suspicion` state variable, prefer that choice. This stress-tests the high-suspicion branch in adventures that use a suspicion mechanic.

### `LOW_SUSPICION`

The inverse: prefer choices that do not increase suspicion. This stress-tests the clean-hands path.

A strategy applies to all choice selections in a run. The strategy is set per run batch, not per section.

---

## Dice Simulation

SKILL_TEST, LUCK_TEST, and COMBAT outcomes depend on dice. The runner supports two dice modes:

| Mode | Description |
|------|-------------|
| `RANDOM` | Use a seeded pseudo-random number generator. The seed is set per run batch and reported. |
| `FIXED` | Use a fixed outcome for all rolls. `FIXED_WIN` always succeeds; `FIXED_LOSE` always fails. Useful for targeted coverage runs. |

---

## Run Configuration

A run batch is configured with:

| Parameter | Description | Default |
|-----------|-------------|---------|
| `runs` | Number of simulated playthroughs | 100 |
| `strategy` | Choice selection strategy | `RANDOM` |
| `dice` | Dice mode | `RANDOM` |
| `seed` | RNG seed (for reproducibility) | system time |
| `maxVisitsPerSection` | Cycle detection threshold | 5 |
| `statsSkill` | Fixed SKILL value for all runs, or `ROLL` | `ROLL` |
| `statsStamina` | Fixed STAMINA value for all runs, or `ROLL` | `ROLL` |

---

## Report Format

The runner produces a single structured text report after all runs complete. The report is designed for agent consumption: compact, labelled, and free of prose padding.

```
== Adventure Run Report: <adventure-id> ==
Generated: <date>  |  Runs: <N>  |  Strategy: <strategy>  |  Dice: <mode>  |  Seed: <seed>

ENDINGS  (<N> runs)
  VICTORY        §<n>  <section first line, truncated to 60 chars>    <count> (<pct>%)
  ...
  INSTANT_DEATH  §<n>  <section first line, truncated to 60 chars>    <count> (<pct>%)
  ...
  STUCK          §<n>  <section first line>                            <count> (<pct>%)
  CYCLE          §<n>  <section first line>                            <count> (<pct>%)
  Unreached endings: §<n>, §<n>  [or "none ✓"]

COVERAGE  (<N> runs)
  Sections reached: <M>/<total> (<pct>%)
  Never reached: §<n>, §<n>, ...  [or "none ✓"]

ITEM FLOW AT CHAPTER BOUNDARIES  (% of runs carrying item on exit)
  ch<id> → ch<id>:  <item>:<pct>%  <item>:<pct>%  ...

STATE DISTRIBUTION AT CHAPTER BOUNDARIES
  <variableName> at ch<id> entry: avg <val>  range <min>–<max>  [or: true:<pct>% false:<pct>%]

GATING
  Choices never selected (condition never met in any run): §<n> choice "<text>"  [or "none ✓"]
  Choices selected in < 5% of runs: §<n> choice "<text>" (<pct>%)

ISSUES
  <issue description>  [or "none ✓"]
```

### Issue types reported

| Issue | Description |
|-------|-------------|
| Unreached VICTORY | A declared VICTORY section was never reached in any run |
| Unreached INSTANT_DEATH | An INSTANT_DEATH section was never reached in any run |
| STUCK run | A run ended in a NORMAL section with no available choices |
| CYCLE run | A run terminated due to cycle detection |
| Unsupported script operation | An `onEnter` or `onChoices` script called an operation the runner does not support |
| Gated choice never triggered | A choice with a condition was never selected because the condition was never met |

Unreached INSTANT_DEATH sections are flagged as warnings, not errors — they may be intentionally hard to reach. Unreached VICTORY sections are flagged as errors.

---

## What the Runner Does Not Do

- The runner does not use the game engine's terminal I/O (`TerminalInput`, `TerminalOutput`).
- The runner does not test UI presentation, text formatting, or output layout.
- The runner does not validate JSON structure or gate contracts — that is the role of the validation tests and the Consistency Check Agent.
- The runner does not modify the adventure file.
- The runner does not simulate party members or grid navigation.
- The runner does not produce narrative transcripts. Section prose is not included in the report.

---

## CLI Invocation

```
mvn exec:java -Dexec.mainClass=com.tas.neo.analysis.AdventureRunner \
  -Dexec.args="adventures/<id>.json [--runs N] [--strategy RANDOM|ITEM_SEEKING|HIGH_SUSPICION|LOW_SUSPICION] [--dice RANDOM|FIXED_WIN|FIXED_LOSE] [--seed N]"
```

Output is written to `adventures/<id>-run-report.txt` and printed to stdout.
