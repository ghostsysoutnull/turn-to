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

**Wound damage values (from spec):**
- Normal wound: 2 STAMINA
- Player wounds creature, Lucky: 4 STAMINA to creature (instead of 2)
- Player wounds creature, Unlucky: 1 STAMINA to creature (instead of 2)
- Creature wounds player, Lucky: 1 STAMINA to player (instead of 2)
- Creature wounds player, Unlucky: 3 STAMINA to player (instead of 2)

Luck is optional — `GameInput.askTestLuck()` is called at each wound opportunity. If the player declines, normal damage applies.

| Layer | Scenario | Setup | What to assert |
|-------|----------|-------|----------------|
| unit | Player wins every round | `FixedDice` giving player high AS, creature low AS | `CombatResult.playerWon() == true`, correct round count |
| unit | Creature wins every round | `FixedDice` giving creature high AS | `CombatResult.playerWon() == false` |
| unit | Draw rounds | `FixedDice` giving equal AS for N rounds then player wins | `playerStaminaLost == 0` for draw rounds, round count correct |
| unit | Luck test: player wounds creature, lucky | `SequenceDice` + `ScriptedInput` confirming luck, lucky roll | Creature takes 4 STAMINA instead of 2 |
| unit | Luck test: player wounds creature, unlucky | `SequenceDice` + `ScriptedInput` confirming luck, unlucky roll | Creature takes 1 STAMINA instead of 2 |
| unit | Luck test: creature wounds player, lucky | `SequenceDice` + `ScriptedInput` confirming luck, lucky roll | Player takes 1 STAMINA instead of 2 |
| unit | Luck test: creature wounds player, unlucky | `SequenceDice` + `ScriptedInput` confirming luck, unlucky roll | Player takes 3 STAMINA instead of 2 |
| unit | Simultaneous multi-combat | Two creatures, `simultaneous = true` | Both creatures attack each round; `roundsFought` reflects combined combat |
