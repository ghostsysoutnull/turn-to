# Design: Architecture

## Guiding Principles

1. **Testability first** — every component is testable in isolation. No static state, no direct System.in/out usage outside of terminal adapter classes.
2. **Dependency inversion** — the game engine depends on interfaces, never on concrete I/O or infrastructure.
3. **Rich domain model** — game rules live in the domain layer, not in the engine or UI.
4. **Data-driven adventures** — adventures are loaded from external files, not hardcoded.

---

## Layer Overview

```
┌──────────────────────────────────────────────┐
│              Entrypoint (Main)               │
└──────────────────┬───────────────────────────┘
                   │ wires up
┌──────────────────▼───────────────────────────┐
│              Engine Layer                    │
│   Game, GameState, GameRunner                │
└───────┬──────────────┬───────────────────────┘
        │              │
┌───────▼──────┐  ┌────▼──────────────────────┐
│  Domain Layer│  │       I/O Layer            │
│  Player      │  │  GameInput (interface)     │
│  Adventure   │  │  GameOutput (interface)    │
│  Section     │  │  TerminalInput             │
│  Events      │  │  TerminalOutput            │
│  Combat      │  └───────────────────────────┘
└───────┬──────┘
        │
┌───────▼──────────────────────────────────────┐
│           Mechanics Layer                    │
│   Dice (interface), RandomDice               │
│   CombatEngine, LuckTest, SkillTest          │
└──────────────────────────────────────────────┘
        │
┌───────▼──────────────────────────────────────┐
│           Loader Layer                       │
│   AdventureLoader (interface)                │
│   JsonAdventureLoader                        │
└──────────────────────────────────────────────┘
```

---

## Package Structure

```
com.tas.neo
├── Main.java                          # Entrypoint only; wires dependencies
├── domain
│   ├── player
│   │   ├── Player.java
│   │   ├── Attribute.java             # record
│   │   └── AttributeType.java         # enum: SKILL, STAMINA, LUCK
│   ├── adventure
│   │   ├── Adventure.java
│   │   ├── Section.java
│   │   ├── SectionType.java           # enum: NORMAL, VICTORY, INSTANT_DEATH
│   │   ├── Choice.java                # record
│   │   └── event
│   │       ├── SectionEvent.java      # sealed interface
│   │       ├── CombatEvent.java
│   │       ├── StatChangeEvent.java
│   │       ├── ItemEvent.java
│   │       ├── LuckTestEvent.java
│   │       ├── SkillTestEvent.java
│   │       ├── NavigateEvent.java
│   │       └── GoldChangeEvent.java
│   └── combat
│       ├── Creature.java              # record
│       ├── CombatRound.java           # record
│       └── CombatResult.java          # record
├── mechanics
│   ├── Dice.java                      # interface
│   ├── RandomDice.java
│   ├── CombatEngine.java
│   ├── LuckTest.java
│   └── SkillTest.java
├── io
│   ├── GameInput.java                 # interface
│   ├── GameOutput.java                # interface
│   ├── TerminalInput.java
│   └── TerminalOutput.java
├── loader
│   ├── AdventureLoader.java           # interface
│   └── JsonAdventureLoader.java
└── engine
    ├── Game.java
    ├── GameState.java
    └── EventProcessor.java
```

---

## Dependency Rules

| Layer | May depend on | Must NOT depend on |
|-------|--------------|-------------------|
| domain | nothing | all other layers |
| mechanics | domain | engine, io, loader |
| io | domain | engine, mechanics, loader |
| loader | domain | engine, mechanics, io |
| engine | domain, mechanics, io, loader | nothing restricted |
| Main | all layers | — |

---

## Key Interfaces

### `Dice`
```java
public interface Dice {
    int roll(int sides);
    default int roll2d6() { return roll(6) + roll(6); }
}
```

### `GameInput`
```java
public interface GameInput {
    int readChoice(List<Choice> choices);
    boolean readYesNo(String prompt);
    void waitForEnter();
}
```

### `GameOutput`
```java
public interface GameOutput {
    void showStatus(Player player);
    void showNarrative(String text);
    void showChoices(List<Choice> choices);
    void showMessage(String message);
    void showCombatRound(CombatRound round);
    void showGameOver(String message);
    void showVictory(String message);
    void clear();
}
```

### `AdventureLoader`
```java
public interface AdventureLoader {
    Adventure load(String adventureId) throws AdventureLoadException;
}
```

---

## Wiring (Main)

`Main` is the only class that touches concrete implementations:

```java
Dice dice = new RandomDice();
GameInput input = new TerminalInput(System.in);
GameOutput output = new TerminalOutput(System.out);
AdventureLoader loader = new JsonAdventureLoader(Path.of("adventures"));
Game game = new Game(input, output, loader, dice);
game.run("the-warlock-of-firetop-mountain");
```

Tests substitute `FixedDice`, `ScriptedInput`, and `RecordingOutput` in place of the terminal implementations.
