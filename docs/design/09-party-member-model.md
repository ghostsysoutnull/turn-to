# Design: Party Member Model

## MemberState

```java
public enum MemberState { WAITING, ACTIVE, REMOVED }
```

A party member moves through states in the direction `WAITING → ACTIVE → REMOVED`. A `REMOVED` member may be re-activated back to `ACTIVE` via `ctx.addPartyMember(id)`. Stats are **not** re-rolled on re-activation.

---

## PartyMember

```java
public class PartyMember {
    public String id();
    public String displayName();
    public MemberState state();
    public boolean isActive();
    public int getStat(String name);
    public int getMaxStat(String name);
    public void modifyStat(String name, int delta);
    public boolean hasStat(String name);
    public boolean isDefeated();
    public void setState(MemberState state);
}
```

`PartyMember` is mutable — stats and state change during play. All definition fields are final. `isDefeated()` returns true when the designated life stat reaches 0. Stat modification clamps to `[0, max]`. `isActive()` is a convenience that returns `state() == MemberState.ACTIVE`.

**Stat mutation pattern.** `PartyMemberStat` is an immutable record. `modifyStat(name, delta)` replaces the map entry: `stats.put(name, stats.get(name).modify(delta))`. `modify(delta)` on the record returns a new `PartyMemberStat` with `current` clamped to `[0, max]`. Direct map mutation with `put` is the only permitted write path — no other code replaces stat entries.

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

Defeat is handled by `HookDispatcher` immediately after any stat modification that causes `isDefeated()` to become true. Both `RemoveConsequence` and `NavigateConsequence` move the member to `REMOVED` state before any navigation occurs.

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

Parses and evaluates dice expressions. Lives in `domain` — shared with player character creation.

```java
public record DiceFormula(int diceCount, int diceSides, int modifier) {
    public static DiceFormula parse(String expression);
    public int roll(Dice dice);
}
```

Supported syntax: `NdS`, `NdS+M`, `NdS-M` (e.g. `"2d6+12"`, `"1d6+6"`, `"1d6"`).

---

## PartyMemberDefinition

Loader-only object carrying the unresolved configuration. Converted to a live `PartyMember` at adventure start.

```java
public class PartyMemberDefinition {
    public String id();
    public String displayName();
    public String lifeStat();
    public DefeatConsequence onDefeat();
    public MemberState initialState();
    public Map<String, StatDefinition> stats();
}
```

`initialState()` is `ACTIVE` by default. Members declared with `WAITING` are not shown in the status bar until `ctx.addPartyMember(id)` is called.

---

## Party Member Conditions

```java
public record PartyStatCondition(
    String memberId, String statName, ComparisonType comparison, int threshold
) implements Condition {}

public record PartyMemberActiveCondition(String memberId) implements Condition {}
public record PartyMemberWaitingCondition(String memberId) implements Condition {}
public record PartyMemberRemovedCondition(String memberId) implements Condition {}
```

These replace the former `PartyMemberPresentCondition`. Each condition tests an exact lifecycle state. `PartyStatCondition` requires the member to be `ACTIVE`; if the member is not `ACTIVE`, the condition evaluates to false.

`ConditionEvaluator` resolves these against `GameState`, which holds the live party member map.

---

## Test Strategy

| Layer | What | Approach |
|-------|------|----------|
| unit | `DiceFormula.parse` | Valid and invalid expressions |
| unit | `DiceFormula.roll` | `FixedDice` → assert deterministic total |
| unit | `DiceStatDefinition` resolution | `FixedDice` with and without `fixedMax` |
| unit | `PartyMemberStat.modify` | Assert clamping at 0 and max |
| unit | `PartyMember.isDefeated` | Set life stat to 0 → assert true |
| unit | `PartyMember.isActive` | Member in each state → assert correct boolean |
| engine-integration | Defeat → `GAME_OVER` | Stat reduced to 0 → assert `state.isGameOver()` |
| engine-integration | Defeat → `REMOVE` | Stat reduced to 0 → assert member state is `REMOVED` in `GameState` |
| engine-integration | Defeat → `NAVIGATE` | Stat reduced to 0 → assert member state is `REMOVED` and `state.currentSection()` changed |
| engine-integration | `ctx.addPartyMember` on `WAITING` member | Member moves to `ACTIVE` → assert `isActive()` true |
| engine-integration | `ctx.addPartyMember` on `ACTIVE` member | No-op → assert state unchanged |
| engine-integration | `ctx.addPartyMember` on `REMOVED` member | Member re-activates → assert `isActive()` true, stats preserved |
| engine-integration | `ctx.addPartyMember` unknown id | No-op → assert no exception |
| engine-integration | `ctx.removePartyMember` on `ACTIVE` member | Member moves to `REMOVED` → assert not in `activePartyMembers()` |
| engine-integration | `ctx.removePartyMember` on `WAITING` member | No-op → assert state unchanged |
| engine-integration | `ctx.removePartyMember` on `REMOVED` member | No-op → assert state unchanged |
| engine-integration | `ctx.removePartyMember` does not trigger `onDefeat` | Remove `ACTIVE` member → assert game not over, no defeat message |
| unit | `PartyMemberActiveCondition` | `ACTIVE` member → true; `WAITING`/`REMOVED` → false |
| unit | `PartyMemberWaitingCondition` | `WAITING` member → true; others → false |
| unit | `PartyMemberRemovedCondition` | `REMOVED` member → true; others → false |
| unit | `PartyStatCondition` on non-ACTIVE member | Member not `ACTIVE` → condition evaluates false |
| unit | Proxy no-op on unknown id | `ctx.getPartyMember('unknown')` → assert no exception |
| unit | Stats rolled at load time, not on join | Member with dice-formula stat: stats fixed before `addPartyMember` call |
