# Design: Combat Engine

## Responsibilities

`CombatEngine` orchestrates a personal combat encounter. It:

- Runs combat rounds until one side reaches 0 STAMINA
- Produces a `CombatRound` record per round, passed to `GameOutput`
- Offers the player an optional Luck test after each wound via `GameInput`
- Returns a `CombatResult` when combat ends

`CombatEngine` does **not** modify `Player` directly — it returns the result and the caller applies the changes.

---

## Class Declaration

```java
public class CombatEngine {
    public CombatEngine(Dice dice, GameInput input, GameOutput output);
    public CombatResult fight(Player player, List<Creature> creatures, boolean simultaneous);
}
```

- `simultaneous = false`: fight each creature in sequence (default).
- `simultaneous = true`: all creatures attack the player each round.

---

## LuckTest

```java
public class LuckTest {
    public LuckTest(Dice dice);
    public boolean test(Player player);
}
```

Rolls 2d6 and compares against the player's current LUCK. LUCK is always decremented by 1 regardless of outcome — this is a core rule, not an implementation choice.

---

## SkillTest

```java
public class SkillTest {
    public SkillTest(Dice dice);
    public boolean test(Player player);
}
```

Rolls 2d6 and compares against the player's current SKILL. Unlike LUCK, SKILL is not decremented by a Skill Test.

---

## Test Strategy

| Scenario | Setup | What to assert |
|----------|-------|----------------|
| Player wins every round | `FixedDice` giving player high AS, creature low AS | `CombatResult.playerWon() == true`, correct round count |
| Creature wins every round | `FixedDice` giving creature high AS | `CombatResult.playerWon() == false` |
| Draw rounds | `FixedDice` giving equal AS | No STAMINA lost, round count increments |
| Luck test improves wound | `SequenceDice` + `ScriptedInput` confirming luck | Extra damage dealt to creature |
| Luck test worsens wound | `SequenceDice` + `ScriptedInput` confirming luck | Reduced damage to creature |
| Simultaneous multi-combat | Two creatures, `simultaneous = true` | Both creatures attack each round |
