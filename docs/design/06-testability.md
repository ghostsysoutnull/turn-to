# Design: Testability Strategy

## Principle

Every layer of the system must be testable in isolation without a real terminal, real filesystem, or random dice. This is achieved by injecting all external dependencies through interfaces and providing a set of test doubles in `src/test`.

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

### `RecordingOutput`

Captures all output calls for assertion. Does not print anything.

```java
public class RecordingOutput implements GameOutput {
    public List<String> getMessages();
    public List<CombatRound> getCombatRounds();
    public boolean wasGameOverShown();
    public boolean wasVictoryShown();
    public boolean wasCleared();
}
```

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
| `Game` (full loop) | All test doubles + `InMemoryAdventureLoader` | `GameState` after run, output recorded |
| `JsonAdventureLoader` | Real filesystem (test resources) | `Adventure` structure, validation errors |
| `TerminalOutput` | Captured `PrintStream` | String output content |
| `TerminalInput` | `ByteArrayInputStream` | Choice parsing, re-prompt on invalid input |
| `PartyMember` defeat | `InMemoryAdventureLoader`, `FixedDice` | Correct consequence applied |
| `CombatSystem` | `FixedDice`, `NoOpScriptEngine` | `CombatOutcome.type()` correct |
