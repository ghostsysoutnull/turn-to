# Design: Adventure Runner

## Spec reference

`docs/specs/11-adventure-runner.md`

---

## Component overview

| Component | Package | Responsibility |
|-----------|---------|----------------|
| `SimulatedGameState` | `analysis` | Mutable game state for one run: inventory, stats, gold, script state, visit log, chapter snapshots |
| `SimulatedScriptContext` | `analysis` | Implements `ScriptContext`; bridges `LuaScriptEngine` calls to `SimulatedGameState` |
| `ConditionEvaluator` | `analysis` | Evaluates a `Condition` against `SimulatedGameState`; returns true/false |
| `ChoiceSelector` | `analysis` | Strategy interface: given available choices and current state, returns the chosen index |
| `RandomChoiceSelector` | `analysis` | Implements `ChoiceSelector`; selects uniformly at random |
| `ItemSeekingChoiceSelector` | `analysis` | Implements `ChoiceSelector`; doubles weight of choices with a satisfied `HAS_ITEM` condition |
| `HighSuspicionChoiceSelector` | `analysis` | Implements `ChoiceSelector`; prefers choices whose target section's `onEnter` script increments `suspicion` |
| `LowSuspicionChoiceSelector` | `analysis` | Implements `ChoiceSelector`; inverse of `HighSuspicionChoiceSelector` |
| `RunConfiguration` | `analysis` | Immutable value object holding batch parameters |
| `RunResult` | `analysis` | Immutable record of one completed run: outcome, ending section, path, snapshots, warnings |
| `RunBatchResult` | `analysis` | Aggregation of N `RunResult` objects; provides distribution, coverage, and flow queries |
| `RunSimulator` | `analysis` | Executes one run: processes sections, events, and scripts; delegates choice to `ChoiceSelector` |
| `AdventureRunner` | `analysis` | Batch orchestration and CLI entry point; writes `adventures/<id>-run-report.txt` |
| `RunReportGenerator` | `analysis` | Formats `RunBatchResult` into the compact structured text report |

---

## Key design decision: use `LuaScriptEngine` directly

`SimulatedScriptContext` implements the existing `ScriptContext` interface. `RunSimulator` passes it to `LuaScriptEngine.execute()` for every `onEnter` and `onChoices` script. This means real Lua is executed — there is no reimplemented Lua parser in the runner. The simulation cannot diverge from engine behaviour on scripted logic.

`SimulatedScriptContext` captures `navigateTo(int)` calls and exposes the target for `RunSimulator` to consume after script execution. `showMessage` is a no-op. `addChoice` appends to the dynamic choice list. All other calls delegate to `SimulatedGameState`.

---

## `SimulatedGameState`

```java
package com.tas.neo.analysis;

public class SimulatedGameState {

    public void addItem(String name);
    public void removeItem(String name);
    public boolean hasItem(String name);
    public Set<String> inventory();

    public int gold();
    public void modifyGold(int delta);        // floors at 0

    public int stat(String attribute);
    public void modifyStat(String attribute, int delta);

    public AdventureScriptState scriptState();

    public List<Integer> sectionsVisited();
    public int visitCount(int section);
    public void recordVisit(int section);

    public void recordChapterSnapshot(String chapterId, int atSection);
    public List<ChapterSnapshot> chapterSnapshots();
}
```

`SimulatedGameState` is created fresh for each run. It does not share state across runs.

---

## `ChapterSnapshot`

```java
package com.tas.neo.analysis;

public record ChapterSnapshot(
    String chapterId,
    int atSection,
    Set<String> inventory,
    int gold,
    Map<String, Object> stateVariables
) {}
```

Captured when `RunSimulator` enters a section that is declared as a chapter entry section. Uses immutable copies of inventory and state at that moment.

---

## `SimulatedScriptContext`

```java
package com.tas.neo.analysis;

public class SimulatedScriptContext implements ScriptContext {

    public SimulatedScriptContext(SimulatedGameState state);

    // After execute() returns, the runner queries these:
    public OptionalInt navigationTarget();   // set if navigateTo() was called
    public List<Choice> dynamicChoices();    // set if addChoice() was called
    public List<String> warnings();          // unsupported operations logged here

    // ScriptContext implementation delegates all stat/item/gold/state calls to SimulatedGameState.
    // navigateTo() records the target in navigationTarget().
    // showMessage() is a no-op.
    // addChoice() appends a Choice to dynamicChoices().
    // hideChoice() is a no-op (runner does not support dynamic choice hiding).
    // getPartyMember(), addPartyMember(), removePartyMember() log a warning and return a no-op proxy.
}
```

---

## `ConditionEvaluator`

```java
package com.tas.neo.analysis;

public class ConditionEvaluator {

    /** Returns true if the condition is satisfied by the current simulated state. */
    public static boolean evaluate(Condition condition, SimulatedGameState state);
}
```

Uses pattern matching over the `Condition` sealed interface:

| Condition type | Rule |
|----------------|------|
| `HasItemCondition` | `state.hasItem(name)` |
| `LacksItemCondition` | `!state.hasItem(name)` |
| `GoldCondition` | `state.gold() >= minimum` |
| `StatCondition` | `state.stat(attribute) >= minimum` |
| `StateEqualsCondition` | `Objects.equals(state.scriptState().get(key), value)` |
| `StateNotEqualsCondition` | `!Objects.equals(state.scriptState().get(key), value)` |
| Party conditions | Always returns `false` (runner does not simulate party members); logged as warning |

---

## `ChoiceSelector`

```java
package com.tas.neo.analysis;

public interface ChoiceSelector {

    /**
     * Select one choice from the available list.
     *
     * @param choices  non-empty list of choices that passed condition filtering
     * @param state    current simulated game state (read-only by contract)
     * @param random   seeded random source for probabilistic selection
     * @return index into choices
     */
    int select(List<Choice> choices, SimulatedGameState state, Random random);
}
```

### `RandomChoiceSelector`

Calls `random.nextInt(choices.size())`. No weighting.

### `ItemSeekingChoiceSelector`

Assigns weight 2 to choices with a satisfied `HasItemCondition`, weight 1 to all others. Selects using weighted random.

### `HighSuspicionChoiceSelector`

At construction, receives the set of section numbers whose `onEnter` script contains `state.set('suspicion'` or increments the `suspicion` variable (pre-scanned from the adventure JSON via `AdventureSectionInspector` pattern matching). Assigns weight 3 to choices whose target is in that set; weight 1 to all others.

### `LowSuspicionChoiceSelector`

Inverse weighting: weight 1 to suspicion-raising choices, weight 3 to all others.

---

## `RunConfiguration`

```java
package com.tas.neo.analysis;

public record RunConfiguration(
    int runs,
    ChoiceSelector choiceSelector,
    Dice dice,
    long seed,
    int maxVisitsPerSection,
    OptionalInt fixedSkill,
    OptionalInt fixedStamina
) {
    public static RunConfiguration defaults();
    public static RunConfiguration withSeed(long seed);
}
```

---

## `RunResult`

```java
package com.tas.neo.analysis;

public record RunResult(
    RunOutcome outcome,
    int endingSection,
    List<Integer> sectionsVisited,
    List<ChapterSnapshot> chapterSnapshots,
    List<String> warnings
) {}

public enum RunOutcome { VICTORY, INSTANT_DEATH, STUCK, CYCLE }
```

---

## `RunBatchResult`

```java
package com.tas.neo.analysis;

public class RunBatchResult {

    public List<RunResult> results();
    public int runCount();

    /** Count of runs with the given outcome. */
    public int outcomeCount(RunOutcome outcome);

    /** Count of runs that ended at the given section. */
    public int endingCount(int section);

    /** All section numbers reached in at least one run. */
    public Set<Integer> coveredSections();

    /** Section numbers never reached in any run. */
    public Set<Integer> uncoveredSections(Adventure adventure);

    /**
     * For each chapter, the fraction of runs carrying the named item
     * when entering that chapter (i.e. at the chapter snapshot).
     */
    public double itemCarryRate(String chapterId, String itemName);

    /**
     * For runs that have a snapshot at the given chapter,
     * returns numeric values of the named state variable as a summary.
     */
    public StateSummary stateDistribution(String chapterId, String variableName);

    /**
     * For runs that have a snapshot at the given chapter,
     * returns a summary of the gold amount at that chapter entry.
     */
    public StateSummary goldDistribution(String chapterId);

    /**
     * Fraction of runs (0.0–1.0) that entered the given chapter at least once.
     * The first chapter is always 1.0.
     */
    public double chapterReachRate(String chapterId);

    /**
     * Distribution of run lengths (section visit count) across all non-STUCK,
     * non-CYCLE runs.
     */
    public RunLengthSummary runLengthSummary();

    /** Choices that were available in at least one run but never selected. */
    public List<NeverSelectedChoice> neverSelectedChoices();

    /** All warnings emitted across all runs, deduplicated. */
    public List<String> warnings();
}

public record RunLengthSummary(double average, int min, int max) {}

public record StateSummary(OptionalDouble average, int min, int max,
                            Map<Object, Integer> valueCounts) {}

public record NeverSelectedChoice(int section, String choiceText) {}
```

---

## `RunSimulator`

```java
package com.tas.neo.analysis;

public class RunSimulator {

    public RunSimulator(Adventure adventure, JsonNode rawJson,
                        ScriptEngine scriptEngine, RunConfiguration config,
                        Random random);

    public RunResult run();
}
```

`RunSimulator` is not reused across runs — construct a new instance per run. Its `run()` method:

1. Initialises a fresh `SimulatedGameState`.
2. Executes the adventure's `onLoad` script to initialise state variables.
3. Processes sections in a loop until a terminal condition is reached.
4. At each section: checks for cycle, applies events (including SKILL_TEST/LUCK_TEST/COMBAT resolution), executes `onEnter` script, executes `onChoices` script, filters choices, delegates to `ChoiceSelector`.
5. Records chapter snapshots when entering chapter entry sections.
6. Returns a `RunResult`.

### SKILL_TEST and LUCK_TEST resolution

Both use `dice.roll(6) + dice.roll(6)` and compare against the relevant stat. Navigation goes to the event's `successSection` or `failureSection` accordingly.

### COMBAT resolution

Each combat round: attacker strength = stat + 2d6; defender strength = SKILL + 2d6. Higher wins and deals damage. `STAMINA` is decremented by damage amount. If player STAMINA reaches 0 or below, navigate to `failureSection`. If opponent STAMINA reaches 0 or below, navigate to `successSection`. Opponent initial stats are taken from the `CombatEvent` fields. Round count is not bounded (cycle detection on the section level prevents infinite combat loops in the event of a data error).

---

## `AdventureRunner`

```java
package com.tas.neo.analysis;

public class AdventureRunner {

    public static RunBatchResult run(Adventure adventure, JsonNode rawJson,
                                     RunConfiguration config);

    /** CLI entry point. args[0] = path, optional flags: --runs N --strategy ... --seed N */
    public static void main(String[] args) throws Exception;
}
```

`main()` loads the adventure via `JsonAdventureLoader`, reads raw JSON via Jackson, calls `run()`, passes the result to `RunReportGenerator.generate()`, writes to `adventures/<id>-run-report.txt`, and prints to stdout.

---

## `RunReportGenerator`

```java
package com.tas.neo.analysis;

public class RunReportGenerator {

    public static String generate(Adventure adventure, JsonNode rawJson,
                                   RunBatchResult result, RunConfiguration config);
}
```

Produces the report format defined in the spec. No section narrative text appears in the report. The ITEM FLOW grid is built by iterating chapter boundary transitions in order; items with all-zero entries across all boundaries are omitted. Reads chapter definitions from `rawJson.path("chapters")` (same pattern as other report generators).

---

## Test strategy

| Test class | What it covers |
|------------|---------------|
| `SimulatedGameStateTest` | `addItem`/`removeItem`/`hasItem` correctness; `modifyGold` floors at 0; `modifyStat` clamps to 0; `visitCount` increments; snapshot immutability |
| `SimulatedScriptContextTest` | `navigateTo` captured in `navigationTarget()`; `addItem` delegates to state; `showMessage` is no-op; `addChoice` appended to dynamic choices; unsupported ops logged as warnings |
| `ConditionEvaluatorTest` | One test per `Condition` subtype; true and false cases; `StateEqualsCondition` with int, string, and boolean values |
| `RandomChoiceSelectorTest` | With one choice: always returns index 0; with `FixedDice` sequence: deterministic selection |
| `ItemSeekingChoiceSelectorTest` | Choice with satisfied `HAS_ITEM` selected more often than unconditioned choice over 1000 trials |
| `HighSuspicionChoiceSelectorTest` | Suspicion-raising choice preferred; non-suspicion choice preferred with `LowSuspicionChoiceSelector` |
| `RunSimulatorTest` | VICTORY reached; INSTANT_DEATH reached; STUCK detected; CYCLE detected; ITEM_GAIN event applied to state; GOLD_CHANGE floored at 0; `navigateTo` in script overrides choice; chapter snapshot captured at entry section |
| `RunBatchResultTest` | `coveredSections()` union of all runs; `itemCarryRate()` fraction; `neverSelectedChoices()` correct; `stateDistribution()` avg/min/max; `goldDistribution()` avg/min/max; `chapterReachRate()` fraction; `runLengthSummary()` avg/min/max excluding STUCK/CYCLE |
| `AdventureRunnerIntegrationTest` | Runs 20 iterations against `the-iron-road.json` with `RANDOM` strategy and fixed seed; asserts at least one VICTORY reached; asserts all 170 sections appear in `coveredSections()` union within reasonable run count |

All tests use `SeededDice` or `FixedDice` — no wall-clock randomness. No test reads from the filesystem except `AdventureRunnerIntegrationTest`, which uses `adventures/the-iron-road.json` as its fixture (same pattern as `AdventureValidationTest`).

---

## Rationale

**Why not wrap `ScenarioRunner`?**
`ScenarioRunner` drives the full `Game` engine (terminal input, output events, section rendering). `AdventureRunner` needs chapter boundary snapshots, per-run item flow tracking, and weighted choice strategies — none of which fit cleanly onto `ScenarioRunner`'s interface without significant changes. Building the simulation in the analysis layer keeps the runner self-contained and avoids coupling it to the engine's output model.

**Why use `LuaScriptEngine` rather than parsing scripts?**
Scripts use the full LuaJ runtime. A custom parser would need to handle Lua conditionals, arithmetic, and standard library calls (e.g. `math.max`). Using `LuaScriptEngine` with a `SimulatedScriptContext` is both simpler and correct by construction — the exact same Lua interpreter that runs in production.

**Why pre-scan for suspicion-raising sections in `HighSuspicionChoiceSelector`?**
Inspecting the target section's `onEnter` script at choice time during a run would require executing the script ahead of navigation, which is expensive and potentially side-effecting. Pre-scanning at construction time (regex over `state.set('suspicion'` patterns) is a one-time cost and accurate for all current adventure content.
