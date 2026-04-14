# Design: Testability Strategy

## Principle

Every layer of the system must be testable in isolation without a real terminal, real filesystem, or random dice. This is achieved by injecting all external dependencies through interfaces.

---

## Test Doubles

### `FixedDice`

Returns a predetermined value for every roll. Useful for deterministic combat and stat tests.

```java
public class FixedDice implements Dice {
    private final int value;
    public FixedDice(int value) { this.value = value; }

    @Override
    public int roll(int sides) { return value; }
}
```

### `SequenceDice`

Returns values from a predefined sequence, cycling or throwing when exhausted.

```java
public class SequenceDice implements Dice {
    public SequenceDice(int... values) { ... }

    @Override
    public int roll(int sides) { return nextValue(); }
}
```

Use this to simulate specific combat scenarios (e.g. player misses first round, hits second).

---

### `ScriptedInput`

Supplies a pre-written sequence of choices and yes/no answers.

```java
public class ScriptedInput implements GameInput {
    public ScriptedInput(int... choices) { ... }

    @Override
    public int readChoice(List<Choice> available) { return nextChoice(); }

    @Override
    public boolean readYesNo(String prompt) { return nextBoolean(); }

    @Override
    public void waitForEnter() { /* no-op */ }
}
```

Throws `AssertionError` if the script is exhausted unexpectedly — a test failure signal.

---

### `RecordingOutput`

Captures all output calls for assertion.

```java
public class RecordingOutput implements GameOutput {
    private final List<String> messages = new ArrayList<>();
    private final List<CombatRound> rounds = new ArrayList<>();

    @Override public void showMessage(String m) { messages.add(m); }
    @Override public void showCombatRound(CombatRound r) { rounds.add(r); }
    // ... etc

    public List<String> getMessages() { return Collections.unmodifiableList(messages); }
    public List<CombatRound> getCombatRounds() { return Collections.unmodifiableList(rounds); }
    public boolean wasCleared() { ... }
}
```

---

### `InMemoryAdventureLoader`

Accepts an `Adventure` object directly, bypassing file I/O.

```java
public class InMemoryAdventureLoader implements AdventureLoader {
    private final Map<String, Adventure> adventures;

    public InMemoryAdventureLoader(Adventure... adventures) { ... }

    @Override
    public Adventure load(String id) { return adventures.get(id); }
}
```

---

## Test Layers

| What to test | Test doubles used | What to assert |
|---|---|---|
| `CombatEngine` | `FixedDice` / `SequenceDice`, `ScriptedInput`, `RecordingOutput` | `CombatResult` fields, rounds displayed |
| `LuckTest` | `FixedDice` | Return value, LUCK decremented |
| `EventProcessor` | `FixedDice`, `RecordingOutput` | Player state changes, navigation changes, output messages |
| `Game` (full loop) | All test doubles + `InMemoryAdventureLoader` | `GameState` after run, messages shown |
| `JsonAdventureLoader` | Real filesystem (test resources) | `Adventure` structure, validation errors |
| `Player` | None | Attribute clamping, inventory mutation |
| `TerminalOutput` | Captured `PrintStream` | Exact or partial string output |
| `TerminalInput` | `ByteArrayInputStream` with scripted bytes | Correct choice parsing, re-prompt on invalid input |

---

## Example: Full Game Integration Test

```java
@Test
void playerDiesWhenStaminaReachesZero() {
    var section1 = new Section(1, "You enter a room.", List.of(
        new CombatEvent(List.of(new Creature("Giant", 12, 100)), false)
    ), List.of());  // no choices, but combat death ends game

    var adventure = new Adventure("test", "Test", "", 1, 0,
        Map.of(1, section1));

    var dice = new FixedDice(1);  // player always rolls lowest AS, always loses
    var input = new ScriptedInput();  // no luck tests
    var output = new RecordingOutput();
    var loader = new InMemoryAdventureLoader(adventure);

    var game = new Game(input, output, loader, dice);
    game.run("test");

    assertThat(output.wasGameOverShown()).isTrue();
    assertThat(output.wasVictoryShown()).isFalse();
}
```
