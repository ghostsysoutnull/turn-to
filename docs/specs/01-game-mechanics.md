# Spec: Core Game Mechanics

## Overview

TAS Neo is a text-based terminal adventure game inspired by the Fighting Fantasy gamebook series. The player navigates a branching narrative divided into numbered sections, making choices and resolving challenges through a dice-based stat system.

---

## Attributes

Every player character has three core attributes:

| Attribute | Description | Initial Value |
|-----------|-------------|---------------|
| SKILL     | Combat prowess and general ability | Rolled: 1d6 + 6 |
| STAMINA   | Health and endurance | Rolled: 2d6 + 12 |
| LUCK      | Fortune and chance | Rolled: 1d6 + 6 |

### Rules

- **Initial values** are determined once at character creation by dice rolls.
- **Maximum values** are fixed at creation. Attributes may be restored up to their maximum but never beyond it.
- STAMINA reaching **0** means the player is dead — the game is over.
- SKILL and LUCK may be temporarily reduced by combat or events but never permanently increased beyond initial value.

---

## Dice

The game uses standard polyhedral dice:

- **1d6**: One six-sided die (values 1–6)
- **2d6**: Two six-sided dice summed (values 2–12)

All dice rolls in the game are made transparently — the result is displayed to the player.

---

## Luck Tests

The player may be asked or may choose to **Test their Luck**.

### Procedure

1. Roll **2d6**.
2. If the result is **less than or equal to** the current LUCK score, the test **succeeds** (Lucky).
3. If the result is **greater than** the current LUCK score, the test **fails** (Unlucky).
4. **Regardless of outcome**, the LUCK score decreases by 1.

### Usage

- Some section events require a mandatory Luck Test.
- During combat, the player may optionally Test Luck to deal extra damage or reduce damage received.

---

## Provisions

The player carries **Provisions** (food) which can be eaten to restore STAMINA.

- Each provision restores **4 STAMINA** (up to maximum).
- Provisions are consumed one at a time.
- The player starts with a default number of provisions defined by the adventure.

---

## Gold

Gold is the in-game currency. It has no mechanical effect in the base ruleset but may be required or awarded by specific section events in an adventure.

---

## Win and Lose Conditions

| Condition | Trigger |
|-----------|---------|
| **Victory** | Reaching a section flagged as the adventure's final victory section |
| **Death** | STAMINA reduced to 0 |
| **Instant Death** | Some sections explicitly end the game regardless of STAMINA |
