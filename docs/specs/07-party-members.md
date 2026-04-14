# Spec: Party Members

## Overview

A **party member** is a named entity with configurable stats that travels with the player and persists across sections. Party members are defined in the adventure configuration. They are not the player character, but they participate in combat, can be referenced in scripts and conditions, and appear in the status bar.

Examples: a ship, a spaceship, a companion character, a regiment, a creature the player controls.

---

## Party Member Definition

| Field       | Type                | Description |
|-------------|---------------------|-------------|
| id          | string              | Unique identifier within the adventure. Referenced in scripts and combat events. |
| displayName | string              | Name shown to the player in the UI. |
| lifeStat    | string              | The stat name whose depletion to 0 marks the member as defeated. |
| onDefeat    | DefeatConsequence   | What happens when the life stat reaches 0. |
| visibility  | Visibility          | `ALWAYS` (visible from adventure start) or `HIDDEN` (shown via script). Default: `ALWAYS`. |
| stats       | map of StatDefinition | Named stats, each with an initial value and optional max. |

---

## Stat Definitions

Each stat has an initial value and a maximum. Both support **fixed integers** and **dice formulas**.

### Fixed value
```json
"CREW": { "initial": 20, "max": 20 }
```

### Dice formula (initial)
```json
"SKILL": { "initial": "1d6+4" }
```
When `max` is omitted and `initial` is a dice formula, max equals the rolled initial value — the same behaviour as player character creation.

### Dice formula (initial) with fixed max
```json
"STAMINA": { "initial": "2d6+6", "max": 24 }
```
The companion starts with a random STAMINA but can be restored up to a fixed ceiling.

### Dice formula syntax

| Pattern | Meaning |
|---------|---------|
| `"NdS"` | Roll N dice of S sides |
| `"NdS+M"` | Roll N dice of S sides, add modifier M |
| `"NdS-M"` | Roll N dice of S sides, subtract modifier M |

Examples: `"1d6"`, `"2d6+12"`, `"1d6+6"`, `"3d6-2"`.

---

## Defeat Consequences

| Type      | Behaviour |
|-----------|-----------|
| `GAME_OVER` | The adventure ends immediately with a custom death message. |
| `REMOVE`    | The party member is silently removed from the party. A message is shown. Adventure continues. |
| `NAVIGATE`  | The adventure navigates to a specified section. Useful for branching outcomes (e.g. ship destroyed → section 200). |

```json
{ "type": "GAME_OVER", "message": "Your ship sinks. The sea claims you." }
{ "type": "REMOVE",    "message": "Theron falls. You press on alone." }
{ "type": "NAVIGATE",  "section": 200, "message": "Your ship is destroyed." }
```

---

## Visibility

| Value    | Behaviour |
|----------|-----------|
| `ALWAYS` | Party member appears in the status bar from the first section. Default. |
| `HIDDEN` | Party member is defined but not displayed. The adventure script calls `ctx.getPartyMember(id).setVisible(true)` when appropriate (e.g. when the player boards their ship). |

Visibility can be toggled at any time via script. Defeated members are automatically hidden.

---

## Status Bar Display

Visible party members appear in the status bar alongside player stats:

```
SKILL:10  STAMINA:18/20  LUCK:8  Gold:5
The Banshee — CREW:17/20  HULL:9/10
Theron      — SKILL:8  STAMINA:14/18
```

Each party member occupies its own line, showing all its stats as `current/max`. If max equals current (full health), the `/max` part may be omitted for readability.

---

## Combat Participation

Party members are declared as participants in a `CombatEvent`:

```json
{
  "type": "COMBAT",
  "system": "naval",
  "participants": ["ship"]
}
```

The combat system receives the listed party members and interacts with their stats directly. A party member not listed in a combat event is unaffected by that encounter.

---

## Party Member Conditions

Conditions can test a party member's stat, enabling choices or events to be gated on party state:

| Condition Type           | Description |
|--------------------------|-------------|
| `PARTY_STAT_AT_LEAST`    | Party member's named stat >= threshold |
| `PARTY_STAT_AT_MOST`     | Party member's named stat <= threshold |
| `PARTY_MEMBER_PRESENT`   | Party member has not been defeated/removed |
| `PARTY_MEMBER_DEFEATED`  | Party member has been defeated |

---

## JSON Example

```json
{
  "partyMembers": [
    {
      "id": "ship",
      "displayName": "The Banshee",
      "lifeStat": "CREW",
      "visibility": "HIDDEN",
      "onDefeat": { "type": "GAME_OVER", "message": "Your ship sinks beneath the waves." },
      "stats": {
        "CREW": { "initial": 20, "max": 20 },
        "HULL": { "initial": 10, "max": 10 }
      }
    },
    {
      "id": "companion",
      "displayName": "Theron",
      "lifeStat": "STAMINA",
      "visibility": "HIDDEN",
      "onDefeat": { "type": "REMOVE", "message": "Theron falls. You press on alone." },
      "stats": {
        "SKILL":   { "initial": "1d6+4" },
        "STAMINA": { "initial": "2d6+6" }
      }
    }
  ]
}
```
