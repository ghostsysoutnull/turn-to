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
    public void modifyAttribute(AttributeType type, int delta);
    public Inventory getInventory();
    public boolean isAlive();
}
```

`Player` is mutable. It is **not** a record.

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
    public Section getSection(int number);
    public boolean hasItem(String name);
    public Item getItem(String name);
    public List<PartyMemberDefinition> partyMemberDefinitions();
    public List<String> combatSystems();
}
```

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

## Choice

```java
public record Choice(String text, int targetSection, Optional<Condition> condition) {}
```

---

## ScriptBlock

```java
public record ScriptBlock(Map<String, String> hooks) {
    public Optional<String> get(String hookName);
    public static ScriptBlock empty();
}
```

`Section`, `Adventure`, `Item`, and `CombatEvent` each carry a `ScriptBlock`.

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
            PartyMemberWaitingCondition, PartyMemberRemovedCondition {}

public record HasItemCondition(String itemName) implements Condition {}
public record LacksItemCondition(String itemName) implements Condition {}
public record StatCondition(AttributeType attribute, ComparisonType comparison, int threshold) implements Condition {}
public record GoldCondition(int minimum) implements Condition {}
public record PartyStatCondition(String memberId, String statName, ComparisonType comparison, int threshold) implements Condition {}
public record PartyMemberActiveCondition(String memberId) implements Condition {}
public record PartyMemberWaitingCondition(String memberId) implements Condition {}
public record PartyMemberRemovedCondition(String memberId) implements Condition {}
```

`ComparisonType` enum: `AT_LEAST`, `AT_MOST`.

`ConditionEvaluator` is a service class that evaluates conditions against `Player` and `GameState`. Evaluation logic does not live in the records themselves.
