# Design: Domain Model

## Player

```java
public class Player {
    private final Map<AttributeType, Attribute> attributes;
    private final Inventory inventory;
    private int gold;
    private int provisions;

    public int getSkill();
    public int getStamina();
    public int getMaxStamina();
    public int getLuck();
    public int getStat(AttributeType type);
    public void modifyAttribute(AttributeType type, int delta);
    public int getGold();
    public void modifyGold(int delta);
    public int getProvisions();
    public void modifyProvisions(int delta);
    public Inventory getInventory();
    public boolean isAlive();
}
```

`Player` is mutable. It is **not** a record. `getStat(AttributeType)` is a generic accessor used by `ConditionEvaluator`; it delegates to the same attribute map as the named getters. `modifyGold` clamps to `[0, Integer.MAX_VALUE]`. `modifyProvisions` clamps to `[0, Integer.MAX_VALUE]`.

---

## Attribute

```java
public record Attribute(AttributeType type, int current, int max) {
    public Attribute modify(int delta);
}
```

Immutable value object. `Player` holds a map and replaces entries on change. Modification clamps to `[0, max]`.

---

## AttributeType

```java
public enum AttributeType { SKILL, STAMINA, LUCK }
```

---

## Adventure

```java
public class Adventure {
    public String id();
    public String title();
    public String description();
    public int startSection();
    public int initialProvisions();
    public Section getSection(int number);   // throws IllegalArgumentException if not found
    public boolean hasItem(String name);
    public Item getItem(String name);        // throws IllegalArgumentException if not found
    public List<PartyMemberDefinition> partyMemberDefinitions();
    public List<String> combatSystems();
    public Optional<Grid> getGrid(String id);
    public List<Grid> grids();
}
```

`getSection` and `getItem` throw `IllegalArgumentException` on a miss. Adventures are validated at load time, so a miss is a programming error, not a runtime condition.

---

## Section

```java
public class Section {
    public int number();
    public String narrative();
    public List<SectionEvent> events();
    public List<Choice> choices();
    public SectionType type();
    public ScriptBlock scripts();
}
```

---

## ChoiceTarget

A choice navigates to exactly one of two target types.

```java
public sealed interface ChoiceTarget
    permits SectionTarget, GridTarget {}

public record SectionTarget(int sectionNumber) implements ChoiceTarget {}
public record GridTarget(String gridId, String cellId) implements ChoiceTarget {}
```

---

## Choice

```java
public record Choice(String text, ChoiceTarget target, Optional<Condition> condition, Optional<String> id) {}
```

`id` is used by `ctx.hideChoice(id)` in `onChoices` scripts. It is unique within a section or cell if present.

---

## ScriptBlock

```java
public record ScriptBlock(Map<String, String> hooks) {
    public ScriptBlock {
        hooks = Map.copyOf(hooks); // defensive copy — immutable after construction
    }
    public Optional<String> get(String hookName);
    public static ScriptBlock empty();
}
```

`Section`, `Adventure`, `Item`, and `CombatEvent` each carry a `ScriptBlock`. The compact constructor copies the map so external mutation after construction is impossible.

---

## SectionEvent Hierarchy

Uses a **sealed interface** so the engine's `switch` is exhaustive at compile time. Adding a new event type without handling it is a compile error.

```java
public sealed interface SectionEvent
    permits CombatEvent, StatChangeEvent, ItemEvent,
            LuckTestEvent, SkillTestEvent, NavigateEvent, GoldChangeEvent {}

public record CombatEvent(
    String system,
    List<String> participantIds,
    List<Creature> opponents,
    boolean simultaneous,
    Map<String, Object> params,
    ScriptBlock scripts
) implements SectionEvent {}

public record StatChangeEvent(AttributeType attribute, int delta) implements SectionEvent {}
public enum ItemAction { GAIN, LOSS }
public record ItemEvent(String itemName, ItemAction action, int quantity) implements SectionEvent {}
public record LuckTestEvent(int successSection, int failSection) implements SectionEvent {}
public record SkillTestEvent(int successSection, int failSection) implements SectionEvent {}
public record NavigateEvent(int targetSection) implements SectionEvent {}
public record GoldChangeEvent(int delta) implements SectionEvent {}
```

---

## Creature

```java
public record Creature(String name, int skill, int stamina) {
    public Creature wound(int damage);
    public boolean isAlive();
}
```

Immutable: each combat round produces a new `Creature` value.

---

## Combat Records

```java
public record CombatRound(
    int roundNumber,
    int playerAttackStrength,
    int creatureAttackStrength,
    int playerRoll,
    int creatureRoll,
    CombatRoundOutcome outcome
) {}

public record CombatResult(boolean playerWon, int roundsFought, int playerStaminaLost) {}

public record CombatOutcome(CombatOutcomeType type, Optional<Integer> navigateTo) {}
```

`CombatRoundOutcome` enum: `PLAYER_WOUNDS`, `CREATURE_WOUNDS`, `DRAW`.
`CombatOutcomeType` enum: `VICTORY`, `DEFEAT`, `FLED`, `DELEGATED`.

---

## Condition Hierarchy

```java
public sealed interface Condition
    permits HasItemCondition, LacksItemCondition, StatCondition, GoldCondition,
            PartyStatCondition, PartyMemberActiveCondition,
            PartyMemberWaitingCondition, PartyMemberRemovedCondition,
            StateEqualsCondition, StateNotEqualsCondition {}

public record HasItemCondition(String itemName) implements Condition {}
public record LacksItemCondition(String itemName) implements Condition {}
public record StatCondition(AttributeType attribute, ComparisonType comparison, int threshold) implements Condition {}
public record GoldCondition(int minimum) implements Condition {}
public record PartyStatCondition(String memberId, String statName, ComparisonType comparison, int threshold) implements Condition {}
public record PartyMemberActiveCondition(String memberId) implements Condition {}
public record PartyMemberWaitingCondition(String memberId) implements Condition {}
public record PartyMemberRemovedCondition(String memberId) implements Condition {}
public record StateEqualsCondition(String key, Object value) implements Condition {}
public record StateNotEqualsCondition(String key, Object value) implements Condition {}
```

`ComparisonType` enum: `AT_LEAST`, `AT_MOST`.

`ConditionEvaluator` is a service class that evaluates conditions against `Player` and `GameState`. Evaluation logic does not live in the records themselves.

```java
public class ConditionEvaluator {
    public boolean evaluate(Condition condition, Player player, GameState state,
                            AdventureScriptState scriptState);
}
```

`AdventureScriptState` is required because `StateEqualsCondition` and `StateNotEqualsCondition` compare against script flags — those live in `AdventureScriptState`, not `GameState`. Passing it as a parameter keeps `GameState` free of scripting concerns.

Dispatch uses a Java 21 `switch` expression over the sealed `Condition` hierarchy — never `instanceof` chains. Every branch is exhaustive at compile time; adding a new `Condition` subtype without handling it is a compile error.

```java
return switch (condition) {
    case HasItemCondition c          -> player.getInventory().has(c.itemName());
    case LacksItemCondition c        -> !player.getInventory().has(c.itemName());
    case StatCondition c             -> c.comparison() == ComparisonType.AT_LEAST
                                         ? player.getStat(c.attribute()) >= c.threshold()
                                         : player.getStat(c.attribute()) <= c.threshold();
    case GoldCondition c             -> player.getGold() >= c.minimum();
    case PartyStatCondition c        -> { /* evaluate via state.getPartyMember */ }
    case PartyMemberActiveCondition c  -> state.getPartyMember(c.memberId()).isActive();
    case PartyMemberWaitingCondition c -> state.getPartyMember(c.memberId()).state() == MemberState.WAITING;
    case PartyMemberRemovedCondition c -> state.getPartyMember(c.memberId()).state() == MemberState.REMOVED;
    case StateEqualsCondition c      -> Objects.equals(scriptState.get(c.key()), c.value());
    case StateNotEqualsCondition c   -> !Objects.equals(scriptState.get(c.key()), c.value());
};
```

---

## Builders

`Adventure`, `Section`, `Cell`, and `Grid` have enough constructor arguments that positional construction is error-prone. Each exposes a static inner `Builder` with a fluent API. Jackson uses `@JsonCreator` on the all-args constructor directly; `Builder` is for production wiring and test fixture construction.

```java
Adventure adventure = Adventure.builder()
    .id("warlock")
    .title("The Warlock of Firetop Mountain")
    .startSection(1)
    .section(section1)
    .section(section2)
    .build();

Section section = Section.builder()
    .number(1)
    .narrative("You stand before the entrance.")
    .type(SectionType.NORMAL)
    .choice(Choice.builder().text("Enter").target(new SectionTarget(2)).build())
    .build();
```

`Builder.build()` throws `IllegalStateException` for any required field left unset. Optional fields (`scripts`, `events`, `grids`, `items`, `partyMembers`) default to empty collections / `ScriptBlock.empty()` when not set. Every `Builder` is a static inner class of its target type.
