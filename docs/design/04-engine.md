# Design: Game Engine

## Responsibilities

The engine layer ties everything together. It:

- Manages the `GameState` (current section, player)
- Drives the main game loop (section → events → choices → navigate → repeat)
- Delegates event processing to `EventProcessor`
- Delegates combat to `CombatEngine`
- Delegates I/O to `GameInput` / `GameOutput`

---

## GameState

```java
public class GameState {
    private final Player player;
    private Section currentSection;
    private boolean gameOver;
    private boolean victory;

    public void navigateTo(Section section);
    public boolean isTerminal();  // gameOver || victory
}
```

---

## Game

```java
public class Game {
    public Game(GameInput input, GameOutput output,
                AdventureLoader loader, Dice dice) { ... }

    public void run(String adventureId);
}
```

`run()` is the top-level game loop:

```
adventure = loader.load(adventureId)
player = createCharacter(adventure)
state = new GameState(player, adventure.getSection(adventure.startSection()))

while not state.isTerminal():
    section = state.currentSection()
    output.clear()
    output.showStatus(player)
    output.showNarrative(section.narrative())

    eventProcessor.process(section.events(), state)

    if state.isTerminal(): break

    choices = resolveChoices(section.choices(), player)
    choices = injectSystemChoices(choices, player)   // eat, inventory, quit
    output.showChoices(choices)

    chosen = input.readChoice(choices)
    handleChoice(chosen, state)
```

---

## EventProcessor

Processes a list of `SectionEvent` items against the current `GameState`. Uses pattern matching on the sealed interface:

```java
public class EventProcessor {
    public void process(List<SectionEvent> events, GameState state) {
        for (SectionEvent event : events) {
            if (state.isTerminal()) return;
            processOne(event, state);
        }
    }

    private void processOne(SectionEvent event, GameState state) {
        switch (event) {
            case CombatEvent e      -> handleCombat(e, state);
            case StatChangeEvent e  -> handleStatChange(e, state);
            case ItemEvent e        -> handleItem(e, state);
            case LuckTestEvent e    -> handleLuckTest(e, state);
            case SkillTestEvent e   -> handleSkillTest(e, state);
            case NavigateEvent e    -> handleNavigate(e, state);
            case GoldChangeEvent e  -> handleGoldChange(e, state);
        }
    }
}
```

The `switch` is exhaustive by the sealed interface contract — adding a new event type causes a compile error until handled.

---

## System Choices

The engine injects these choices into every section's choice list:

| Condition | Injected choice |
|-----------|----------------|
| provisions > 0 | "Eat a provision (restores 4 STAMINA)" |
| always | "Check inventory" |
| always | "Quit" |

These are handled entirely within the engine and never navigate to a section.

---

## Character Creation

```java
private Player createCharacter(Adventure adventure) {
    int skill   = dice.roll(6) + 6;
    int stamina = dice.roll(6) + dice.roll(6) + 12;
    int luck    = dice.roll(6) + 6;
    return new Player(skill, stamina, luck, adventure.initialProvisions());
}
```

The rolled values become both the **initial** and **maximum** values for each attribute.

---

## Section Type Handling

After events complete and before showing choices:

```java
switch (section.type()) {
    case VICTORY      -> { output.showVictory(...); state.setVictory(); }
    case INSTANT_DEATH -> { output.showGameOver(...); state.setGameOver(); }
    case NORMAL       -> { /* show choices */ }
}
```

Player death (STAMINA == 0) is detected in `EventProcessor` after any event that modifies STAMINA, and sets `state.setGameOver()` immediately.
