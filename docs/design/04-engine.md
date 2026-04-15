# Design: Game Engine

## Responsibilities

The engine layer ties everything together. It:

- Manages `GameState` (current section, player, party members)
- Drives the main game loop: section → events → choices → navigate → repeat
- Delegates event processing to `HookDispatcher`
- Delegates combat to `CombatSystemRegistry`
- Delegates I/O to `GameInput` / `GameOutput`
- Injects system choices (eat, inventory, quit) into every section

---

## GameState

```java
public class GameState {
    public Player player();
    public Section currentSection();
    public void navigateTo(Section section);
    public Optional<Grid> currentGrid();
    public Optional<Cell> currentCell();
    public void navigateToCell(Grid grid, Cell cell);
    public boolean isInGrid();
    public boolean isTerminal();
    public boolean isGameOver();
    public boolean isVictory();
    public void setGameOver();
    public void setVictory();
    public PartyMember getPartyMember(String id);
    public List<PartyMember> activePartyMembers();
    public void setPartyMemberState(String id, MemberState state);
}
```

`isTerminal()` returns true when either `gameOver` or `victory` is set. The game loop checks this after every event and after every navigation.

`navigateTo(Section)` clears any active grid state — the player exits the grid and is now in a section. `navigateToCell` sets the active grid and cell, clearing any section context. `currentSection()` returns `null` when the player is in a grid.

**Scope constraint.** `GameState` holds exactly: current position (section or grid+cell), the `Player`, the party member map, and terminal flags. It does not store `Adventure` data, section lists, item catalogs, or any data derivable from the loaded adventure. Any method that would require storing adventure data on `GameState` is a design violation — route it through `Adventure` directly in `Game` or `HookDispatcher` instead.

---

## Game

```java
public class Game {
    public Game(GameInput input, GameOutput output, AdventureLoader loader,
                Dice dice, ScriptEngine scriptEngine, CombatSystemRegistry combatRegistry,
                GameLogger logger);
    public void run(String adventureId);
    public GameState state();
}
```

`run()` loads the adventure, creates the player and party members, then drives the game loop until `GameState.isTerminal()`.

---

## HookDispatcher

Single point of contact between the engine and the scripting/combat layers. Fires named lifecycle hooks and resolves combat events.

```java
public class HookDispatcher {
    public HookDispatcher(ScriptEngine scriptEngine, GameInput input, GameOutput output,
                          GameState state, AdventureScriptState scriptState,
                          CombatSystemRegistry combatRegistry, Dice dice,
                          GameLogger logger);

    public void fireAdventureHook(AdventureHook hook, Adventure adventure);
    public void fireSectionHook(SectionHook hook, Section section, List<Choice> mutableChoices);
    public void fireCombatHook(CombatHook hook, CombatEvent event, CombatRound round);
    public void fireItemHook(ItemScriptHook hook, Item item);
    public CombatOutcome processCombatEvent(CombatEvent event);
    public void processEvent(SectionEvent event);
}
```

If the relevant `ScriptBlock` has no entry for a hook, the call is a no-op.

`processEvent(SectionEvent)` dispatches over the sealed `SectionEvent` hierarchy using a Java 21 `switch` expression. `Game` calls this single method per event — it never switches over event types itself. Adding a new `SectionEvent` subtype requires only updating `HookDispatcher.processEvent`, not `Game`.

`HookDispatcher` constructs `CombatContext` internally from its own `input`, `output`, `combatRegistry`, and `dice` fields when dispatching a `CombatEvent` — callers never build `CombatContext` directly.

---

## System Choices

The engine injects these choices into every section's choice list at display time. They are never authored in section data.

| Condition | Injected choice |
|-----------|----------------|
| provisions > 0 | "Eat a provision (restores 4 STAMINA)" |
| always | "Check inventory" |
| always | "Quit" |

---

## Character and Party Creation

At adventure start, the engine:

1. Rolls player stats using `DiceFormula` (SKILL: `1d6+6`, STAMINA: `2d6+12`, LUCK: `1d6+6`). Rolled values become both initial and maximum.
2. Creates each declared party member by resolving their `StatDefinition` entries via `Dice`. Each member's initial `MemberState` is set from `PartyMemberDefinition.initialState()` (`ACTIVE` or `WAITING`). Stats are rolled regardless of initial state.
3. Fires `AdventureHook.ON_LOAD`, then `AdventureHook.ON_START`.

---

## Section Type Handling

After all events have processed:

- `VICTORY` → fire `ON_VICTORY` hook, show victory message, set `state.setVictory()`
- `INSTANT_DEATH` → show death message, set `state.setGameOver()`
- `NORMAL` → build and display choice list

Player death (STAMINA == 0) is detected by `HookDispatcher` immediately after any stat-modifying event and sets `state.setGameOver()` without waiting for section type resolution.

---

## Test Strategy

| Scenario | Setup | What to assert |
|----------|-------|----------------|
| Victory section ends game | `InMemoryAdventureLoader` with VICTORY section | `state.isVictory() == true` |
| INSTANT_DEATH ends game | Section of type INSTANT_DEATH | `state.isGameOver() == true` |
| Player death mid-event | STAMINA reduced to 0 by `StatChangeEvent` | `state.isGameOver()`, no choices shown |
| System choices injected | Any normal section | "Check inventory" and "Quit" always present |
| Navigation follows choice | `ScriptedInput` selecting choice 1 | `state.currentSection()` == target |
| Party member created | Adventure with dice-formula stat | Stat within expected range |
| Enter grid via choice | `InMemoryAdventureLoader` with grid, choice using `GridTarget` | `state.isInGrid() == true`, `state.currentCell()` == entry cell |
| Exit grid via passage `toSection` | Player in grid, selects exit passage | `state.isInGrid() == false`, `state.currentSection()` == target |
| Cell events fire on entry | Cell with `StatChangeEvent` | Player stat modified |
