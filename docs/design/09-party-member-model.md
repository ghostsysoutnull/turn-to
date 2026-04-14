# Design: Party Member Model

## Domain Objects

### PartyMember

```java
public class PartyMember {
    private final String id;
    private final String displayName;
    private final String lifeStat;
    private final DefeatConsequence onDefeat;
    private final Map<String, PartyMemberStat> stats;
    private boolean visible;

    public String id();
    public String displayName();
    public boolean isVisible();
    public void setVisible(boolean visible);

    public int getStat(String name);
    public int getMaxStat(String name);
    public void modifyStat(String name, int delta);   // clamps to [0, max]
    public boolean hasStat(String name);

    public boolean isDefeated();   // lifeStat current == 0
}
```

`PartyMember` is mutable — stats change during play. All definition fields are final.

---

### PartyMemberStat

```java
public record PartyMemberStat(String name, int current, int max) {
    public PartyMemberStat modify(int delta) {
        int next = Math.clamp(current + delta, 0, max);
        return new PartyMemberStat(name, next, max);
    }
    public boolean isDepleted() { return current == 0; }
    public String display() {
        return current == max ? String.valueOf(current) : current + "/" + max;
    }
}
```

Immutable value object. `PartyMember` holds a map and replaces entries on change.

---

### DefeatConsequence

```java
public sealed interface DefeatConsequence
    permits GameOverConsequence, RemoveConsequence, NavigateConsequence {}

public record GameOverConsequence(String message) implements DefeatConsequence {}
public record RemoveConsequence(String message) implements DefeatConsequence {}
public record NavigateConsequence(int section, String message) implements DefeatConsequence {}
```

---

## StatDefinition — Fixed vs Dice

`StatDefinition` describes how a stat's initial and max values are determined at adventure start (party member creation). It is a loader/creation concern only — once resolved, the live `PartyMemberStat` holds plain integers.

```java
public sealed interface StatDefinition
    permits FixedStatDefinition, DiceStatDefinition {}

public record FixedStatDefinition(int initial, int max) implements StatDefinition {
    /** Convenience: initial == max */
    public FixedStatDefinition(int value) { this(value, value); }

    @Override public int resolveInitial(Dice dice) { return initial; }
    @Override public int resolveMax(Dice dice)     { return max; }
}

public record DiceStatDefinition(DiceFormula formula, OptionalInt fixedMax) implements StatDefinition {
    @Override public int resolveInitial(Dice dice) { return formula.roll(dice); }
    @Override public int resolveMax(Dice dice) {
        return fixedMax.isPresent() ? fixedMax.getAsInt() : resolveInitial(dice);
    }
}
```

`StatDefinition` is used only during `PartyMember` creation. Both `initial` and `max` are resolved once at adventure start, producing concrete integers.

---

## DiceFormula

Parses and evaluates dice expressions. Lives in the `mechanics` package — it is also used for player character creation.

```java
public record DiceFormula(int diceCount, int diceSides, int modifier) {

    private static final Pattern PATTERN =
        Pattern.compile("(\\d+)d(\\d+)([+-]\\d+)?");

    public static DiceFormula parse(String expression) {
        Matcher m = PATTERN.matcher(expression.trim());
        if (!m.matches())
            throw new IllegalArgumentException("Invalid dice formula: " + expression);
        int count    = Integer.parseInt(m.group(1));
        int sides    = Integer.parseInt(m.group(2));
        int modifier = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
        return new DiceFormula(count, sides, modifier);
    }

    public int roll(Dice dice) {
        int total = modifier;
        for (int i = 0; i < diceCount; i++) total += dice.roll(diceSides);
        return total;
    }

    /** True if this expression is effectively a fixed value (0 dice). */
    public boolean isFixed() { return diceCount == 0; }
}
```

---

## Visibility

`PartyMember.visible` is a mutable flag. Default comes from the adventure definition:

```java
public enum Visibility { ALWAYS, HIDDEN }
```

The loader sets `visible = (definition.visibility() == Visibility.ALWAYS)` during party member creation. Scripts toggle it via `ctx.getPartyMember(id).setVisible(true/false)`.

Defeated members are automatically hidden by the engine after processing the defeat consequence.

---

## Party Member Script Proxy

Scripts access party members through a proxy object returned by `ctx.getPartyMember(id)`. The proxy wraps a `PartyMember` and exposes a Lua-friendly API:

```java
public class PartyMemberProxy {
    private final PartyMember member;

    public void modifyStat(String name, int delta) { member.modifyStat(name, delta); }
    public int getStat(String name)                { return member.getStat(name); }
    public int getMaxStat(String name)             { return member.getMaxStat(name); }
    public boolean isDefeated()                    { return member.isDefeated(); }
    public boolean isVisible()                     { return member.isVisible(); }
    public void setVisible(boolean visible)        { member.setVisible(visible); }
}
```

`DefaultScriptContext.getPartyMember(id)` looks up the member from `GameState`, wraps it, and returns the proxy. If the id is unknown or the member has been removed, it returns a null-safe proxy that logs a warning and no-ops all calls.

---

## Defeat Handling

Defeat is checked by `HookDispatcher` after any operation that modifies a party member's stat:

```java
private void checkDefeat(PartyMember member, GameState state) {
    if (!member.isDefeated()) return;
    member.setVisible(false);

    switch (member.onDefeat()) {
        case GameOverConsequence c  -> { output.showGameOver(c.message()); state.setGameOver(); }
        case RemoveConsequence c    -> { output.showMessage(c.message()); state.removePartyMember(member.id()); }
        case NavigateConsequence c  -> { output.showMessage(c.message()); state.navigateTo(c.section()); }
    }
}
```

This is called from `DefaultScriptContext.modifyPartyMemberStat` and from `CombatSystem` implementations via `HookDispatcher`.

---

## Adventure and GameState Updates

`Adventure` gains:

```java
private final List<PartyMemberDefinition> partyMemberDefinitions;
public List<PartyMemberDefinition> partyMemberDefinitions();
```

`GameState` gains:

```java
private final Map<String, PartyMember> partyMembers;  // keyed by id

public PartyMember getPartyMember(String id);
public List<PartyMember> visiblePartyMembers();
public void removePartyMember(String id);
```

Party members are instantiated from definitions at adventure start (after player creation), with stats resolved via `StatDefinition.resolve(dice)`.

---

## Party Member Conditions

```java
public sealed interface Condition
    permits HasItemCondition, LacksItemCondition, StatCondition, GoldCondition,
            PartyStatCondition, PartyMemberPresentCondition {}

public record PartyStatCondition(
    String memberId, String statName, ComparisonType comparison, int threshold
) implements Condition {}

public record PartyMemberPresentCondition(String memberId, boolean present) implements Condition {}
```

`ConditionEvaluator` gains corresponding evaluation methods against `GameState`.

---

## Package Additions

```
com.tas.neo
├── domain
│   └── party
│       ├── PartyMember.java
│       ├── PartyMemberStat.java          # record
│       ├── PartyMemberDefinition.java    # loader-only; holds StatDefinitions
│       ├── DefeatConsequence.java        # sealed interface
│       └── Visibility.java               # enum: ALWAYS, HIDDEN
├── mechanics
│   ├── DiceFormula.java
│   └── StatDefinition.java              # sealed interface
└── scripting
    └── PartyMemberProxy.java
```

---

## Test Strategy

| What | Approach |
|------|----------|
| `DiceFormula.parse` | Unit test valid and invalid expressions |
| `DiceFormula.roll` | `FixedDice` → assert deterministic total |
| `DiceStatDefinition` resolve | Assert initial and max with `FixedDice` and explicit `fixedMax` |
| `PartyMemberStat.modify` | Assert clamping at 0 and max |
| `PartyMember.isDefeated` | Set life stat to 0 → assert true |
| Defeat → `GAME_OVER` | Script reduces life stat to 0 → assert `state.isGameOver()` |
| Defeat → `REMOVE` | Script reduces life stat to 0 → assert member absent from `GameState` |
| Defeat → `NAVIGATE` | Script reduces life stat to 0 → assert `state.currentSection()` changed |
| Visibility toggle | `setVisible(false)` then `setVisible(true)` → assert `visiblePartyMembers()` |
| `getPartyMember` unknown id | Assert proxy no-ops and logs warning |
