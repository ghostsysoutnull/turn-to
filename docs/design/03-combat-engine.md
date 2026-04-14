# Design: Combat Engine

## Responsibilities

`CombatEngine` orchestrates a full combat encounter. It:

- Runs combat rounds until one side reaches 0 STAMINA
- Produces a `CombatRound` record for each round (displayed by `GameOutput`)
- Handles the optional Luck test mid-combat via `GameInput`
- Returns a `CombatResult` when combat ends

`CombatEngine` does **not** modify `Player` directly — it returns the result and the engine layer applies it.

---

## Constructor Dependencies

```java
public class CombatEngine {
    public CombatEngine(Dice dice, GameInput input, GameOutput output) { ... }
}
```

- `Dice` — injectable for deterministic testing
- `GameInput` — to ask player if they want to Test Luck
- `GameOutput` — to display each round

---

## Core Method

```java
public CombatResult fight(Player player, List<Creature> creatures, boolean simultaneous);
```

For `simultaneous = false` (default): fight each creature in sequence.
For `simultaneous = true`: all creatures attack the player each round.

---

## Single-Creature Combat Loop (pseudocode)

```
round = 1
while creature.isAlive() and player.isAlive():
    playerAS = player.skill + dice.roll2d6()
    creatureAS = creature.skill + dice.roll2d6()

    if playerAS > creatureAS:
        damage = 2
        if player wants to test luck:
            damage = luckTest.test(player) ? 4 : 1
        creature = creature.wound(damage)
        outcome = PLAYER_WOUNDS

    else if creatureAS > playerAS:
        damage = 2
        if player wants to test luck:
            damage = luckTest.test(player) ? 1 : 3
        player.modifyAttribute(STAMINA, -damage)
        outcome = CREATURE_WOUNDS

    else:
        outcome = DRAW

    output.showCombatRound(new CombatRound(round, playerAS, creatureAS, ...))
    round++

return new CombatResult(player.isAlive(), round - 1, staminaLost)
```

---

## LuckTest

`LuckTest` is a standalone class used by both `CombatEngine` and `EventProcessor`:

```java
public class LuckTest {
    public LuckTest(Dice dice) { ... }

    public boolean test(Player player) {
        int roll = dice.roll2d6();
        boolean lucky = roll <= player.getLuck();
        player.modifyAttribute(LUCK, -1);  // always decreases
        return lucky;
    }
}
```

---

## SkillTest

```java
public class SkillTest {
    public SkillTest(Dice dice) { ... }

    public boolean test(Player player) {
        int roll = dice.roll2d6();
        return roll <= player.getSkill();
    }
}
```

Note: SKILL is not decremented by a Skill Test — unlike LUCK.

---

## Test Strategy

```java
// Deterministic combat test
Dice fixed = sides -> 3;  // always rolls 3 on any die
CombatEngine engine = new CombatEngine(fixed, new ScriptedInput(), new RecordingOutput());

// Player SKILL 10 + 6 (3+3) = 16 AS
// Creature SKILL 8 + 6 = 14 AS  → player always wins each round
CombatResult result = engine.fight(player, List.of(goblin), false);
assertThat(result.playerWon()).isTrue();
assertThat(result.roundsFought()).isEqualTo(5);  // goblin has 10 STAMINA / 2 per round
```
