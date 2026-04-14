# Design: Architecture

## Guiding Principles

1. **Testability first** — every component is testable in isolation. No static state, no direct System.in/out usage outside of terminal adapter classes.
2. **Dependency inversion** — the game engine depends on interfaces, never on concrete I/O or infrastructure.
3. **Rich domain model** — game rules live in the domain layer, not in the engine or UI.
4. **Data-driven adventures** — adventures are loaded from external files, not hardcoded.
5. **Lifecycle-driven scripting** — the engine owns a fixed set of hook points; scripts are passengers, not drivers.

---

## Layer Overview

```
┌──────────────────────────────────────────────────┐
│                Entrypoint (Main)                 │
└────────────────────┬─────────────────────────────┘
                     │ wires up
┌────────────────────▼─────────────────────────────┐
│                 Engine Layer                     │
│   Game, GameState, HookDispatcher                │
└───────┬─────────────────┬────────────────────────┘
        │                 │
┌───────▼──────┐  ┌───────▼──────────────────────┐
│ Domain Layer │  │          I/O Layer            │
│ Player       │  │  GameInput  (interface)       │
│ Adventure    │  │  GameOutput (interface)       │
│ Section      │  │  TerminalInput                │
│ Item         │  │  TerminalOutput               │
│ Events       │  └───────────────────────────────┘
│ Combat       │
└───────┬──────┘
        │
┌───────▼──────────────────────────────────────────┐
│              Mechanics Layer                     │
│  Dice (interface), RandomDice                    │
│  CombatEngine, LuckTest, SkillTest               │
└───────┬──────────────────────────────────────────┘
        │
┌───────▼──────────────────────────────────────────┐
│              Scripting Layer                     │
│  ScriptEngine (interface)                        │
│  LuaScriptEngine                                 │
│  ScriptContext (interface)                       │
│  AdventureScriptState                            │
└───────┬──────────────────────────────────────────┘
        │
┌───────▼──────────────────────────────────────────┐
│               Loader Layer                       │
│  AdventureLoader (interface)                     │
│  JsonAdventureLoader                             │
└──────────────────────────────────────────────────┘
```

---

## Package Structure

```
com.tas.neo
├── Main.java                              # Entrypoint only; wires dependencies
├── domain
│   ├── player
│   │   ├── Player.java
│   │   ├── Attribute.java                 # record
│   │   └── AttributeType.java             # enum: SKILL, STAMINA, LUCK
│   ├── adventure
│   │   ├── Adventure.java
│   │   ├── Section.java
│   │   ├── SectionType.java               # enum: NORMAL, VICTORY, INSTANT_DEATH
│   │   ├── Choice.java                    # record
│   │   ├── ScriptBlock.java               # record: map of hook name → script string
│   │   └── event
│   │       ├── SectionEvent.java          # sealed interface
│   │       ├── CombatEvent.java
│   │       ├── StatChangeEvent.java
│   │       ├── ItemEvent.java
│   │       ├── LuckTestEvent.java
│   │       ├── SkillTestEvent.java
│   │       ├── NavigateEvent.java
│   │       └── GoldChangeEvent.java
│   ├── item
│   │   ├── Item.java
│   │   ├── ItemCategory.java              # enum: USABLE, EQUIPPABLE, KEY, PASSIVE
│   │   └── ItemScriptHook.java            # enum: ON_PICKUP, ON_DROP, ON_USE, ON_EQUIP, ON_UNEQUIP, ON_COMBAT_ROUND
│   └── combat
│       ├── Creature.java                  # record
│       ├── CombatRound.java               # record
│       └── CombatResult.java              # record
├── mechanics
│   ├── Dice.java                          # interface
│   ├── RandomDice.java
│   ├── CombatEngine.java
│   ├── LuckTest.java
│   └── SkillTest.java
├── scripting
│   ├── ScriptEngine.java                  # interface
│   ├── LuaScriptEngine.java
│   ├── ScriptContext.java                 # interface exposed to scripts
│   ├── DefaultScriptContext.java
│   └── AdventureScriptState.java          # mutable k/v store scoped to an adventure run
├── io
│   ├── GameInput.java                     # interface
│   ├── GameOutput.java                    # interface
│   ├── TerminalInput.java
│   └── TerminalOutput.java
├── loader
│   ├── AdventureLoader.java               # interface
│   └── JsonAdventureLoader.java
└── engine
    ├── Game.java
    ├── GameState.java
    └── HookDispatcher.java                # fires lifecycle hooks via ScriptEngine
```

---

## Dependency Rules

| Layer     | May depend on                          | Must NOT depend on       |
|-----------|----------------------------------------|--------------------------|
| domain    | nothing                                | all other layers         |
| mechanics | domain                                 | engine, io, loader, scripting |
| scripting | domain                                 | engine, io, loader, mechanics |
| io        | domain                                 | engine, mechanics, loader, scripting |
| loader    | domain                                 | engine, mechanics, io, scripting |
| engine    | domain, mechanics, io, loader, scripting | nothing restricted     |
| Main      | all layers                             | —                        |

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

### `ScriptEngine`
```java
public interface ScriptEngine {
    void execute(String script, ScriptContext context) throws ScriptException;
}
```

### `ScriptContext`
```java
public interface ScriptContext {
    // Player
    void modifyStat(String attribute, int delta);
    int getStat(String attribute);
    // Inventory
    void addItem(String itemName);
    void removeItem(String itemName);
    boolean hasItem(String itemName);
    // Navigation
    void navigateTo(int section);
    int currentSection();
    // Output
    void showMessage(String message);
    // Choices (onChoices hook only)
    void addChoice(String text, int targetSection);
    void removeChoice(String text);
    // Adventure state
    void setState(String key, Object value);
    Object getState(String key);
    // Gold
    void modifyGold(int delta);
    int getGold();
}
```

---

## Wiring (Main)

`Main` is the only class that touches concrete implementations:

```java
Dice dice                 = new RandomDice();
GameInput input           = new TerminalInput(System.in);
GameOutput output         = new TerminalOutput(System.out);
ScriptEngine scriptEngine = new LuaScriptEngine();
AdventureLoader loader    = new JsonAdventureLoader(Path.of("adventures"));
Game game                 = new Game(input, output, loader, dice, scriptEngine);
game.run("the-warlock-of-firetop-mountain");
```

Tests substitute `FixedDice`, `ScriptedInput`, `RecordingOutput`, `RecordingScriptContext`, and `InMemoryAdventureLoader`.
