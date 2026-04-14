# Spec: Combat System

## Overview

Combat occurs when the player encounters a hostile creature. It is turn-based and resolved entirely through dice rolls against SKILL scores. Combat continues until either the player or the creature reaches 0 STAMINA.

---

## Combatants

Every creature in combat has:

| Field    | Description |
|----------|-------------|
| NAME     | Display name of the creature |
| SKILL    | Combat ability score |
| STAMINA  | Hit points |

---

## Combat Round

Each round of combat proceeds as follows:

### 1. Calculate Attack Strengths

- **Player Attack Strength** = Player SKILL + 2d6
- **Creature Attack Strength** = Creature SKILL + 2d6

### 2. Resolve the Round

| Result | Outcome |
|--------|---------|
| Player AS > Creature AS | Player wounds creature: creature loses **2 STAMINA** |
| Creature AS > Player AS | Creature wounds player: player loses **2 STAMINA** |
| Equal AS | **Neither** is wounded — the round is a draw |

### 3. Display

The result of every round is shown to the player: both attack strengths, the dice rolled, and the outcome.

---

## Optional Luck in Combat

At the moment a wound is dealt (by either side), the player may choose to **Test Luck**:

- **Wound dealt by player, Lucky**: deal **4 STAMINA** damage instead of 2 (extra damage).
- **Wound dealt by player, Unlucky**: deal **1 STAMINA** damage instead of 2 (weakened blow).
- **Wound received by player, Lucky**: take **1 STAMINA** damage instead of 2 (glancing blow).
- **Wound received by player, Unlucky**: take **3 STAMINA** damage instead of 2 (severe blow).

Testing Luck is optional and always reduces LUCK by 1 regardless of outcome.

---

## Multiple Opponents

Some encounters involve fighting more than one creature.

- By default, multiple creatures are fought **one at a time** in sequence.
- Each creature is a separate combat encounter resolved with the standard rules.
- Some sections may specify simultaneous multi-combat (both creatures attack each round) — this must be explicitly flagged in the section event data.

---

## End of Combat

| Condition | Result |
|-----------|--------|
| Creature STAMINA <= 0 | Player wins; section continues |
| Player STAMINA <= 0 | Player dies; game over |

After winning combat the game continues to the section's next event or choice list.
