# Design: Session Logging

## Responsibilities

The session logging system records every adventure run as a text log (human-readable) and a structured JSON log (agent-readable). It is implemented as an injectable `GameLogger` interface so test runs capture logs in memory rather than writing to disk. The engine calls `GameLogger` at every navigation, event, and error point.

---

## GameLogger

```java
public interface GameLogger {
    void logNavigation(NavigationEntry entry);
    void logEvent(OutputEvent event);
    void logError(GameError error);
    void close();
}
```

`close()` flushes and finalises the log. Called by the engine when the session ends (victory, game over, or unrecoverable fault). After `close()`, further calls are no-ops.

`GameLogger` lives in `com.tas.neo.io` — it is an output boundary, parallel to `GameOutput`.

---

## NavigationEntry

```java
public record NavigationEntry(String from, String to, String via) {}
```

`from` and `to` use the location string format: `"section:N"` for sections, `"grid:<id>:<x>,<y>,<z>"` for grid cells. `via` is the choice text the player selected.

---

## PlayerSnapshot

A lightweight capture of player state at a point in time. Used in error entries.

```java
public record PlayerSnapshot(
    String location,
    int stamina,
    int skill,
    int luck,
    int gold,
    List<String> inventory
) {
    public static PlayerSnapshot of(GameState state);
}
```

`of(GameState)` is a factory that reads the current state. `inventory` contains item names only (no quantities) — sufficient for error diagnosis.

---

## GameError

```java
public record GameError(
    String source,
    String type,
    String message,
    PlayerSnapshot snapshot
) {}
```

`source` identifies where the error occurred, e.g. `"section-42-onEnter"` or `"grid-dungeon-cell(1,2,0)-onChoices"`. `type` is one of `"ScriptError"` or `"NavigationError"`.

---

## SessionLog

The complete in-memory record of a run.

```java
public record SessionLog<E>(
    String adventureId,
    List<NavigationEntry> path,
    List<E> events,
    List<GameError> errors,
    String result,
    int stepsCount
) {}
```

`result` is `"VICTORY"`, `"GAME_OVER"`, or `"ABANDONED"`.

`SessionLog` is generic on the event type to avoid a dependency from `domain.log` onto `io.OutputEvent`. `FileGameLogger` and `RecordingGameLogger` use `SessionLog<OutputEvent>`; tests that have no interest in events use the raw type or `SessionLog<Object>`.

---

## Implementations

### `NoOpGameLogger`

Discards all calls. Used in tests that have no interest in the log.

```java
public class NoOpGameLogger implements GameLogger {
    public void logNavigation(NavigationEntry entry);
    public void logEvent(OutputEvent event);
    public void logError(GameError error);
    public void close();
}
```

### `RecordingGameLogger`

Accumulates all calls in memory. Used in tests that assert on log content.

```java
public class RecordingGameLogger implements GameLogger {
    public SessionLog sessionLog();
    public List<GameError> errors();
    public boolean hasErrors();
}
```

Lives in `src/test/java`.

### `FileGameLogger`

Writes a text log and a JSON log to the `sessions/` directory. Builds the `SessionLog` in memory and writes both formats on `close()`.

```java
public class FileGameLogger implements GameLogger {
    public FileGameLogger(String adventureId, Path sessionsDir);
}
```

---

## Text Log Format

Written by `FileGameLogger.close()`. One entry per navigation step:

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

[Section 400] VICTORY — You have defeated the Warlock!

─────────────────────────────────────
Result:      VICTORY
Steps:       23
Errors:      0
```

---

## JSON Log Format

```json
{
  "adventureId": "the-warlock-of-firetop-mountain",
  "result": "VICTORY",
  "stepsCount": 23,
  "path": [
    { "from": "section:1", "to": "section:2", "via": "Entered the mountain" },
    { "from": "section:2", "to": "section:3", "via": "Search the body" },
    { "from": "grid:dungeon-level-1:0,0,0", "to": "grid:dungeon-level-1:1,0,0", "via": "Go east" }
  ],
  "events": [
    { "type": "NarrativeShown", "text": "You stand before the entrance..." },
    { "type": "CombatRoundShown", "roundNumber": 1, "playerWon": true }
  ],
  "errors": []
}
```

Error entry example:

```json
{
  "source": "section-42-onEnter",
  "type": "ScriptError",
  "message": "attempt to index a nil value (global 'doorState')",
  "snapshot": {
    "location": "section:42",
    "stamina": 8, "skill": 9, "luck": 5, "gold": 3,
    "inventory": ["Iron Key", "Torch"]
  }
}
```

---

## SeededDice

Provides reproducible random behaviour using a seeded `java.util.Random`. Lives in `com.tas.neo.mechanics` alongside `RandomDice`.

```java
public class SeededDice implements Dice {
    public SeededDice(long seed);
    public int roll(int sides);
}
```

The same seed produces the same sequence of rolls on every run. Used by `ScenarioRunner` in random mode to make failing random runs reproducible: record the seed, re-run with the same seed to reproduce the failure.

---

## Package Structure Additions

```
com.tas.neo
├── io
│   ├── GameLogger.java          # interface
│   ├── FileGameLogger.java
│   └── NoOpGameLogger.java
├── domain
│   └── log
│       ├── NavigationEntry.java # record
│       ├── PlayerSnapshot.java  # record
│       ├── GameError.java       # record
│       └── SessionLog.java      # record
└── mechanics
    └── SeededDice.java
```

`RecordingGameLogger` lives in `src/test/java/com/tas/neo/io/`.

---

## Engine Integration

`Game` receives a `GameLogger` at construction. `HookDispatcher` receives it too — script errors are caught in `HookDispatcher` and passed to `logError` before execution continues. `GameState` provides `PlayerSnapshot.of(state)` as a convenience factory.

```java
public class Game {
    public Game(GameInput input, GameOutput output, AdventureLoader loader,
                Dice dice, ScriptEngine scriptEngine,
                CombatSystemRegistry combatRegistry, GameLogger logger);
    public void run(String adventureId);
}
```

---

## Test Strategy

| What | Approach |
|------|----------|
| `NavigationEntry` format | Assert `from`/`to` strings for section and grid locations |
| `PlayerSnapshot.of` | Known `GameState` → assert all fields captured correctly |
| `RecordingGameLogger` accumulates | Run `ScenarioRunner` with it → assert `sessionLog().path()` matches choices made |
| `RecordingGameLogger` captures errors | ScenarioRunner with a section that has a broken script → assert `hasErrors() == true`, error `source` and `type` correct |
| `FileGameLogger` writes both files | `ScenarioRunner` with `FileGameLogger` pointing to a temp dir → assert both files exist and JSON parses correctly |
| `SeededDice` reproducibility | Two runs with same seed → assert identical navigation paths |
| Random run terminates | `ScenarioRunner.random()` with `SeededDice` → assert run reaches terminal state |
| No log files in test runs | `ScenarioRunner` with `NoOpGameLogger` (default) → assert no files written to `sessions/` |
