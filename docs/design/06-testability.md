# Design: Testability Strategy

## Principle

Every layer of the system — including UI, gameplay, scripting, grid navigation, and combat — must be fully exercisable by an agent running the test suite, with no human present and no terminal interaction. Tests produce no output on success. Failure messages are self-diagnosing. See the Testability Covenant in `CLAUDE.md` for the full set of rules.

This is achieved by injecting all external dependencies through interfaces, providing test doubles for every boundary, and offering a `ScenarioRunner` utility for full end-to-end game runs.

---

## Test Doubles

All test doubles live under `src/test/java` alongside the tests that use them.

### `FixedDice`

Returns a predetermined value for every roll. Used for deterministic combat and stat tests where a single constant outcome is sufficient.

```java
public class FixedDice implements Dice {
    public FixedDice(int value);
    public int roll(int sides);
}
```

### `SequenceDice`

Returns values from a predefined sequence in order. Throws `AssertionError` when the sequence is exhausted — signals that the test did not control enough rolls.

```java
public class SequenceDice implements Dice {
    public SequenceDice(int... values);
    public int roll(int sides);
}
```

Use when specific per-round outcomes matter (e.g. player misses round 1, hits round 2).

### `ScriptedInput`

Supplies a pre-written sequence of choices and yes/no answers. Throws `AssertionError` if the script is exhausted unexpectedly.

```java
public class ScriptedInput implements GameInput {
    public ScriptedInput(int... choices);
    public int readChoice(List<Choice> available);
    public boolean readYesNo(String prompt);
    public void waitForEnter();
}
```

### `OutputEvent`

Structured event hierarchy. `RecordingOutput` records one `OutputEvent` per `GameOutput` call. Assertions target typed events rather than raw strings, making them stable across narrative rewording.

```java
public sealed interface OutputEvent
    permits NarrativeShown, MessageShown, CombatRoundShown,
            ChoicesShown, VictoryShown, GameOverShown,
            StatusShown, InventoryShown, ScreenCleared {}

public record NarrativeShown(String text)       implements OutputEvent {}
public record MessageShown(String text)         implements OutputEvent {}
public record CombatRoundShown(CombatRound round) implements OutputEvent {}
public record ChoicesShown(List<Choice> choices)  implements OutputEvent {}
public record VictoryShown(String message)      implements OutputEvent {}
public record GameOverShown(String message)     implements OutputEvent {}
public record StatusShown(Player player, List<PartyMember> activeMembers) implements OutputEvent {}
public record InventoryShown(List<ItemStack> stacks, int gold, int provisions) implements OutputEvent {}
public record ScreenCleared()                   implements OutputEvent {}
```

### `RecordingOutput`

Captures all output calls as structured `OutputEvent` entries. Does not print anything.

```java
public class RecordingOutput implements GameOutput {
    public List<OutputEvent> events();
    public List<String> messages();
    public boolean wasGameOverShown();
    public boolean wasVictoryShown();
    public boolean wasCleared();
}
```

`messages()` is a convenience that extracts all `MessageShown.text()` values in order. `events()` is the authoritative record.

### `RecordingScriptContext`

Captures every call a script makes against the context, for testing Lua scripts in isolation.

```java
public class RecordingScriptContext implements ScriptContext {
    public List<String> getMessages();
    public List<StatChange> getStatChanges();
    public int navigatedTo();
    public boolean itemAdded(String name);
    public boolean itemRemoved(String name);
}
```

### `NoOpScriptEngine`

Executes nothing. Used in engine and combat tests that do not involve scripts, to avoid LuaJ overhead.

```java
public class NoOpScriptEngine implements ScriptEngine {
    public void execute(String script, ScriptContext context);
}
```

### `InMemoryAdventureLoader`

Accepts `Adventure` objects directly. Used in all engine-level tests, bypassing JSON parsing and filesystem I/O.

```java
public class InMemoryAdventureLoader implements AdventureLoader {
    public InMemoryAdventureLoader(Adventure... adventures);
    public Adventure load(String id);
}
```

---

## ScenarioRunner

`ScenarioRunner` drives a complete game run with scripted inputs and deterministic dice. It is the primary tool for end-to-end game tests — full playthroughs, grid traversal sequences, multi-section narratives — without any terminal interaction.

```java
public class ScenarioRunner {
    public ScenarioRunner(Adventure adventure, Dice dice, int... choiceSequence);
    public ScenarioResult run();
}

public record ScenarioResult(
    GameState finalState,
    RecordingOutput output,
    boolean victory,
    boolean gameOver
) {
    public int finalSection();        // -1 if player is in a grid at end
    public boolean isInGrid();
    public Optional<String> finalGridId();
}
```

`ScenarioRunner` wires up `Game` internally using `InMemoryAdventureLoader`, `ScriptedInput`, and `RecordingOutput`. The caller provides `Adventure`, `Dice` (use `FixedDice` for determinism), and the choice sequence. `ScriptedInput` throws `AssertionError` if the choice sequence is exhausted before the game ends — this surfaces scenarios where the adventure takes an unexpected path.

---

## `GameState` Inspectability

Every field of `GameState` that a test might need to assert on must be readable via public methods. No test may use reflection to access private state. The following must all be publicly readable:

- Current section number (or -1 if in grid)
- Whether the player is in a grid, and which grid and cell
- Player stats (SKILL, STAMINA, LUCK, gold, provisions)
- Inventory contents (item names and quantities)
- Party member states and stats
- `isGameOver()`, `isVictory()`, `isTerminal()`

If a new feature adds state that a test needs to assert on, the public read API must be extended before the feature is considered complete.

---

## Test Layers

| What to test | Test doubles used | What to assert |
|---|---|---|
| `Player` | None | Attribute clamping, inventory mutations |
| `Inventory` | None | Quantity tracking, `onDrop` boundary |
| `DiceFormula` | `FixedDice` | Correct totals for given rolls |
| `LuckTest` | `FixedDice` | Lucky/unlucky return value, LUCK decremented |
| `SkillTest` | `FixedDice` | Pass/fail return value, SKILL unchanged |
| `CombatEngine` | `FixedDice`, `SequenceDice`, `ScriptedInput`, `RecordingOutput` | `CombatResult` fields, rounds displayed |
| `HookDispatcher` / scripts | `NoOpScriptEngine` or `LuaScriptEngine` + `RecordingScriptContext` | Hooks fired, context calls recorded |
| `Game` (full loop) | `ScenarioRunner` with `FixedDice` + `InMemoryAdventureLoader` | `ScenarioResult` fields, `RecordingOutput.events()` |
| `JsonAdventureLoader` | Real filesystem (test resources) | `Adventure` structure, validation errors |
| `TerminalOutput` | Captured `PrintStream` | String output content |
| `TerminalInput` | `ByteArrayInputStream` | Choice parsing, re-prompt on invalid input |
| `PartyMember` defeat | `ScenarioRunner`, `FixedDice` | Correct consequence in `ScenarioResult.finalState()` |
| `CombatSystem` | `FixedDice`, `NoOpScriptEngine` | `CombatOutcome.type()` correct |
| Grid traversal | `ScenarioRunner`, `FixedDice` | `isInGrid()`, `finalGridId()`, cell position in `finalState` |

---

## Maven Surefire Configuration

Configure `maven-surefire-plugin` so the test suite produces no output on success and only failure details on failure. Agents read `mvn test` stdout to assess results.

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <reportFormat>plain</reportFormat>
    <useFile>false</useFile>
    <trimStackTrace>false</trimStackTrace>
    <redirectTestOutputToFile>false</redirectTestOutputToFile>
  </configuration>
</plugin>
```

With this configuration:
- Passing tests produce no per-test output.
- Failures show the test name, assertion message, and stack trace.
- The final line is the summary: `Tests run: N, Failures: M, Errors: K`.

Test code must not call `System.out.println` or configure any logger that writes to stdout. If a test needs to capture and inspect output, it uses `RecordingOutput` — it does not print to the console.
