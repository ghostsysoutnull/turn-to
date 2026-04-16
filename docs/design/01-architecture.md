# Design: Architecture

## Guiding Principles

1. **Testability first** — every component is testable in isolation. No static state, no direct System.in/out usage outside terminal adapter classes.
2. **Dependency inversion** — the engine depends on interfaces, never on concrete I/O or infrastructure.
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
│ Section      │  │  GameLogger (interface)       │
│ Item         │  │  TerminalInput                │
│ PartyMember  │  │  TerminalOutput               │
│ Events       │  │  FileGameLogger               │
│ Grid/Cell    │  │  NoOpGameLogger               │
│ Combat       │  └───────────────────────────────┘
│ Events       │
│ Combat       │
│ Dice (iface) │
│ DiceFormula  │
│ StatDefinition│
└───────┬──────┘
        │
┌───────▼──────────────────────────────────────────┐
│              Mechanics Layer                     │
│  RandomDice, SeededDice                          │
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
│              Combat Layer                        │
│  CombatSystem (interface)                        │
│  CombatSystemRegistry (interface)                │
│  PersonalCombatSystem                            │
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
├── Main.java
├── domain
│   ├── Dice.java                          # interface
│   ├── DiceFormula.java                   # record
│   ├── StatDefinition.java                # sealed interface
│   ├── DiceStatDefinition.java            # record
│   ├── FixedStatDefinition.java           # record
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
│   │       ├── CombatEvent.java           # includes system, participants, params
│   │       ├── StatChangeEvent.java
│   │       ├── ItemEvent.java
│   │       ├── LuckTestEvent.java
│   │       ├── SkillTestEvent.java
│   │       ├── NavigateEvent.java
│   │       └── GoldChangeEvent.java
│   ├── item
│   │   ├── Item.java
│   │   ├── ItemCategory.java              # enum: USABLE, EQUIPPABLE, KEY, PASSIVE
│   │   └── ItemScriptHook.java            # enum: ON_PICKUP, ON_DROP, ON_USE, ...
│   ├── party
│   │   ├── PartyMember.java
│   │   ├── PartyMemberStat.java           # record
│   │   ├── PartyMemberDefinition.java     # loader-only
│   │   ├── DefeatConsequence.java         # sealed interface
│   │   └── MemberState.java              # enum: WAITING, ACTIVE, REMOVED
│   ├── combat
│   │   ├── Creature.java                  # record
│   │   ├── CombatRound.java               # record
│   │   ├── CombatResult.java              # record
│   │   └── CombatOutcome.java             # record
│   └── log
│       ├── NavigationEntry.java           # record
│       ├── PlayerSnapshot.java            # record
│       ├── GameError.java                 # record
│       └── SessionLog.java                # record
├── mechanics
│   ├── RandomDice.java
│   ├── SeededDice.java
│   ├── CombatEngine.java
│   ├── LuckTest.java
│   └── SkillTest.java
├── scripting
│   ├── ScriptEngine.java                  # interface
│   ├── LuaScriptEngine.java
│   ├── ScriptContext.java                 # interface
│   ├── DefaultScriptContext.java
│   ├── AdventureScriptState.java
│   └── PartyMemberProxy.java
├── io
│   ├── GameInput.java                     # interface
│   ├── GameOutput.java                    # interface
│   ├── GameLogger.java                    # interface
│   ├── TerminalInput.java
│   ├── TerminalOutput.java
│   ├── FileGameLogger.java
│   └── NoOpGameLogger.java
├── combat
│   ├── CombatSystem.java                  # interface
│   ├── CombatSystemRegistry.java          # interface
│   ├── DefaultCombatSystemRegistry.java
│   └── personal
│       └── PersonalCombatSystem.java
├── loader
│   ├── AdventureLoader.java               # interface
│   └── JsonAdventureLoader.java
└── engine
    ├── Game.java
    ├── GameState.java
    └── HookDispatcher.java
```

---

## Dependency Rules

| Layer     | May depend on                                    | Must NOT depend on              |
|-----------|--------------------------------------------------|---------------------------------|
| domain    | nothing                                          | all other layers                |
| mechanics | domain                                           | engine, io, loader, scripting, combat |
| scripting | domain                                           | engine, io, loader, mechanics   |
| io        | domain                                           | engine, mechanics, loader, scripting |
| combat    | domain, mechanics                                | engine, io, loader, scripting   |
| loader    | domain                                           | engine, mechanics, io, scripting |
| engine    | domain, mechanics, io, loader, scripting, combat | —                               |
| Main      | all layers                                       | —                               |

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
    void showStatus(Player player, List<PartyMember> activeMembers);
    void showNarrative(String text);
    void showChoices(List<Choice> choices);
    void showMessage(String message);
    void showCombatRound(CombatRound round);
    void showInventory(List<ItemStack> stacks, int gold, int provisions);
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
    void modifyStat(String attribute, int delta);
    int getStat(String attribute);
    void modifyGold(int delta);
    int getGold();
    void addItem(String itemName);
    void addItem(String itemName, int quantity);
    void removeItem(String itemName);
    void removeItem(String itemName, int quantity);
    boolean hasItem(String itemName);
    int getItemCount(String itemName);
    PartyMemberProxy getPartyMember(String id);
    void addPartyMember(String id);
    void removePartyMember(String id);
    void navigateTo(int section);
    int currentSection();
    void showMessage(String message);
    void addChoice(String text, int targetSection);
    void hideChoice(String id);
}
```

### `CombatSystem`
```java
public interface CombatSystem {
    String id();
    CombatOutcome run(Player player, List<PartyMember> participants,
                     List<Creature> opponents, Map<String, Object> params,
                     CombatSystemRegistry registry, HookDispatcher hooks,
                     GameInput input, GameOutput output, Dice dice);
}
```

### `CombatSystemRegistry`
```java
public interface CombatSystemRegistry {
    CombatSystem get(String id);
    boolean has(String id);
}
```

---

## Wiring (Main)

`Main` is the only class that touches concrete implementations:

```java
Dice dice                           = new RandomDice();
GameInput input                     = new TerminalInput(System.in, System.out);
GameOutput output                   = new TerminalOutput(System.out);
GameLogger logger                   = new FileGameLogger("the-warlock-of-firetop-mountain", Path.of("sessions"));
ScriptEngine scriptEngine           = new LuaScriptEngine();
AdventureLoader loader              = new JsonAdventureLoader(Path.of("adventures"));
CombatSystemRegistry combatRegistry = new DefaultCombatSystemRegistry(
    new PersonalCombatSystem(new CombatEngine(dice, input, output))
);
Game game = new Game(input, output, loader, dice, scriptEngine, combatRegistry, logger);
game.run("the-warlock-of-firetop-mountain");
```
