# Design: Party Member Model

## PartyMember

```java
public class PartyMember {
    public String id();
    public String displayName();
    public boolean isVisible();
    public void setVisible(boolean visible);
    public int getStat(String name);
    public int getMaxStat(String name);
    public void modifyStat(String name, int delta);
    public boolean hasStat(String name);
    public boolean isDefeated();
}
```

`PartyMember` is mutable — stats change during play. All definition fields are final. `isDefeated()` returns true when the designated life stat reaches 0. Modification clamps to `[0, max]`.

---

## PartyMemberStat

```java
public record PartyMemberStat(String name, int current, int max) {
    public PartyMemberStat modify(int delta);
    public boolean isDepleted();
    public String display();
}
```

Immutable value object. `PartyMember` holds a map and replaces entries on change. `display()` returns `"current/max"` or just `"current"` when current equals max.

---

## DefeatConsequence

```java
public sealed interface DefeatConsequence
    permits GameOverConsequence, RemoveConsequence, NavigateConsequence {}

public record GameOverConsequence(String message) implements DefeatConsequence {}
public record RemoveConsequence(String message) implements DefeatConsequence {}
public record NavigateConsequence(int section, String message) implements DefeatConsequence {}
```

Defeat is handled by `HookDispatcher` immediately after any stat modification that causes `isDefeated()` to become true.

---

## StatDefinition

Describes how a stat's initial and max values are resolved at adventure start. A loader-only concern — once resolved, live `PartyMemberStat` holds plain integers.

```java
public sealed interface StatDefinition
    permits FixedStatDefinition, DiceStatDefinition {}

public record FixedStatDefinition(int initial, int max) implements StatDefinition {
    public int resolveInitial(Dice dice);
    public int resolveMax(Dice dice);
}

public record DiceStatDefinition(DiceFormula formula, OptionalInt fixedMax) implements StatDefinition {
    public int resolveInitial(Dice dice);
    public int resolveMax(Dice dice);
}
```

When `fixedMax` is absent on a `DiceStatDefinition`, max equals the rolled initial value — the same behaviour as player character creation.

---

## DiceFormula

Parses and evaluates dice expressions. Lives in `mechanics` — shared with player character creation.

```java
public record DiceFormula(int diceCount, int diceSides, int modifier) {
    public static DiceFormula parse(String expression);
    public int roll(Dice dice);
}
```

Supported syntax: `NdS`, `NdS+M`, `NdS-M` (e.g. `"2d6+12"`, `"1d6+6"`, `"1d6"`).

---

## Visibility

```java
public enum Visibility { ALWAYS, HIDDEN }
```

The loader sets `visible = (definition.visibility() == Visibility.ALWAYS)` during creation. Scripts toggle it via `ctx.getPartyMember(id).setVisible(...)`. Defeated members are automatically hidden by `HookDispatcher` after applying the defeat consequence.

---

## PartyMemberDefinition

Loader-only object carrying the unresolved configuration. Converted to a live `PartyMember` at adventure start.

```java
public class PartyMemberDefinition {
    public String id();
    public String displayName();
    public String lifeStat();
    public DefeatConsequence onDefeat();
    public Visibility visibility();
    public Map<String, StatDefinition> stats();
}
```

---

## Party Member Conditions

```java
public record PartyStatCondition(
    String memberId, String statName, ComparisonType comparison, int threshold
) implements Condition {}

public record PartyMemberPresentCondition(String memberId, boolean present) implements Condition {}
```

`ConditionEvaluator` resolves these against `GameState`, which holds the live party member map.

---

## Test Strategy

| What | Approach |
|------|----------|
| `DiceFormula.parse` | Valid and invalid expressions |
| `DiceFormula.roll` | `FixedDice` → assert deterministic total |
| `DiceStatDefinition` resolution | `FixedDice` with and without `fixedMax` |
| `PartyMemberStat.modify` | Assert clamping at 0 and max |
| `PartyMember.isDefeated` | Set life stat to 0 → assert true |
| Defeat → `GAME_OVER` | Stat reduced to 0 → assert `state.isGameOver()` |
| Defeat → `REMOVE` | Stat reduced to 0 → assert member absent from `GameState` |
| Defeat → `NAVIGATE` | Stat reduced to 0 → assert `state.currentSection()` changed |
| Visibility toggle | `setVisible(false)` then `true` → assert `visiblePartyMembers()` |
| Proxy no-op on unknown id | `ctx.getPartyMember('unknown')` → assert no exception |
