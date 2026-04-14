# Design: Domain Model

## Player

```java
public class Player {
    private final Map<AttributeType, Attribute> attributes;
    private final List<String> inventory;
    private int gold;
    private int provisions;

    // Attribute access
    public int getSkill();
    public int getStamina();
    public int getMaxStamina();
    public int getLuck();

    // Mutations
    public void modifyAttribute(AttributeType type, int delta);  // clamps to [0, max]
    public void addItem(String item);
    public boolean removeItem(String item);
    public boolean hasItem(String item);
    public boolean isAlive();  // stamina > 0
}
```

`Player` is mutable — it accumulates state changes throughout the game. It is **not** a record.

---

## Attribute

```java
public record Attribute(AttributeType type, int current, int max) {
    public Attribute modify(int delta) {
        int next = Math.clamp(current + delta, 0, max);
        return new Attribute(type, next, max);
    }
}
```

Immutable value object. `Player` holds a map of these and replaces them on change.

---

## AttributeType

```java
public enum AttributeType {
    SKILL, STAMINA, LUCK
}
```

---

## Adventure and Section

```java
public class Adventure {
    private final String id;
    private final String title;
    private final String description;
    private final int startSection;
    private final int initialProvisions;
    private final Map<Integer, Section> sections;
    private final List<PartyMemberDefinition> partyMemberDefinitions;
    private final List<String> combatSystems;           // ids beyond "personal"

    public Section getSection(int number);              // throws if not found
    public List<PartyMemberDefinition> partyMemberDefinitions();
    public boolean hasItem(String name);
}
```

```java
public class Section {
    private final int number;
    private final String narrative;
    private final List<SectionEvent> events;
    private final List<Choice> choices;
    private final SectionType type;
}
```

---

## Choice

```java
public record Choice(String text, int targetSection, Optional<Condition> condition) {}
```

---

## SectionEvent Hierarchy

Uses a **sealed interface** so the engine's `switch` is exhaustive at compile time:

```java
public sealed interface SectionEvent
    permits CombatEvent, StatChangeEvent, ItemEvent,
            LuckTestEvent, SkillTestEvent, NavigateEvent, GoldChangeEvent {}
```

```java
public record CombatEvent(
    String system,                  // default "personal"
    List<String> participantIds,    // party member ids
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
    public Creature wound(int damage) {
        return new Creature(name, skill, stamina - damage);
    }
    public boolean isAlive() { return stamina > 0; }
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
    CombatRoundOutcome outcome  // PLAYER_WOUNDS, CREATURE_WOUNDS, DRAW
) {}

public record CombatResult(
    boolean playerWon,
    int roundsFought,
    int playerStaminaLost
) {}
```

---

## Condition

```java
public sealed interface Condition
    permits HasItemCondition, LacksItemCondition, StatCondition, GoldCondition,
            PartyStatCondition, PartyMemberPresentCondition {}

public record HasItemCondition(String itemName) implements Condition {}
public record LacksItemCondition(String itemName) implements Condition {}
public record StatCondition(AttributeType attribute, ComparisonType comparison, int threshold) implements Condition {}
public record GoldCondition(int minimum) implements Condition {}
public record PartyStatCondition(String memberId, String statName, ComparisonType comparison, int threshold) implements Condition {}
public record PartyMemberPresentCondition(String memberId, boolean present) implements Condition {}
```

`ComparisonType` enum: `AT_LEAST`, `AT_MOST`.

A `ConditionEvaluator` service class handles evaluation against `Player` and `GameState`, keeping evaluation logic out of the domain records themselves.
