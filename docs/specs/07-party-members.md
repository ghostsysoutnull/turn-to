# Spec: Party Members

## Overview

A **party member** is a named entity with configurable stats that travels with the player and persists across sections. Party members are defined in the adventure configuration. They are not the player character, but they participate in combat, can be referenced in scripts and conditions, and appear in the status bar.

Examples: a ship, a spaceship, a companion character, a regiment, a creature the player controls.

---

## Party Member Lifecycle

A party member moves through the following states over the course of an adventure:

```
WAITING ──► ACTIVE ──► REMOVED
               ▲           │
               └───────────┘
             (re-join allowed)
```

| State     | Meaning |
|-----------|---------|
| `WAITING` | Defined in the adventure config but not yet part of the active party. Not shown in the status bar. Stats are initialised but not tracked in the UI. |
| `ACTIVE`  | Currently travelling with the player. Shown in the status bar (if visible). Eligible to participate in combat. Conditions that test party state apply. |
| `REMOVED` | No longer with the player. Hidden from the status bar. Combat events that list this member ignore it. Conditions that test party state reflect removal. |

### Pre-definition requirement

Every party member that may ever appear in an adventure **must** be declared in the adventure's top-level `partyMembers` list. The engine does not support creating a wholly new party member at runtime that was not pre-defined in the config. If a member should only appear later in the story, declare it in the config with `initialState: WAITING`.

---

## Party Member Definition

| Field        | Type                  | Description |
|--------------|-----------------------|-------------|
| id           | string                | Unique identifier within the adventure. Referenced in scripts and combat events. |
| displayName  | string                | Name shown to the player in the UI. |
| lifeStat     | string                | The stat name whose depletion to 0 marks the member as defeated. |
| onDefeat     | DefeatConsequence     | What happens when the life stat reaches 0. |
| initialState | MemberState           | `WAITING` or `ACTIVE`. Default: `ACTIVE`. |
| stats        | map of StatDefinition | Named stats, each with an initial value and optional max. |

> **Note on visibility:** Earlier versions of this spec described a `visibility` field. Visibility is now derived from state: an `ACTIVE` member is visible in the status bar; a `WAITING` or `REMOVED` member is not. There is no separate visibility toggle. If the adventure previously used `HIDDEN` to model a member not yet joined, declare that member with `initialState: WAITING` instead.

---

## Stat Definitions

Each stat has an initial value and a maximum. Both support **fixed integers** and **dice formulas**. Stats are rolled and fixed at adventure load time, not when the member joins.

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

## Dynamic Joining

A member defined with `initialState: WAITING` starts the adventure in the `WAITING` state and joins the active party when a script calls `ctx.addPartyMember(id)`.

```lua
-- Section onEnter: player boards their ship
ctx.addPartyMember('ship')
ctx.showMessage('You board The Banshee. She is ready to sail.')
```

- If the id is not recognised, the call is silently ignored.
- If the member is already `ACTIVE`, the call is silently ignored.
- If the member is `REMOVED`, the call re-activates them (see Re-joining below).

---

## Dynamic Removal Outside Combat

A party member can be removed from the active party at any time via script, independent of combat and defeat. This covers narrative events such as a companion choosing to leave, a ship being sold, or a vessel lost to a storm.

```lua
-- Section onEnter: the companion decides to part ways
ctx.removePartyMember('companion')
ctx.showMessage('Theron looks at you gravely. "My road ends here," he says.')
```

- The member moves to the `REMOVED` state.
- The member is hidden from the status bar immediately.
- The member's stats are preserved in memory for the remainder of the adventure run.
- If the id is not recognised or the member is already `REMOVED`, the call is silently ignored.

This is distinct from defeat: `removePartyMember` does not trigger `onDefeat` and does not check the life stat.

---

## Re-joining

A member that has been removed — whether by `ctx.removePartyMember`, by a `REMOVE` defeat consequence, or by a `NAVIGATE` defeat consequence — may re-join the party by a later `ctx.addPartyMember(id)` call.

When a removed member re-joins:

- Their state changes from `REMOVED` to `ACTIVE`.
- Their stats are restored to the values they held at the moment of removal. Stats are **not** re-rolled.
- If the author intends different stats on re-joining, they must adjust the stats with `member.modifyStat` calls immediately after `ctx.addPartyMember`.

> **Design note:** Whether re-joining is appropriate is an adventure authoring decision. The engine permits it; the adventure author controls it through scripts.

---

## Defeat Consequences

Defeat is triggered when a party member's life stat reaches 0 during combat (or via `member.modifyStat` if that brings the life stat to 0 outside combat).

| Type      | Behaviour |
|-----------|-----------|
| `GAME_OVER` | The adventure ends immediately with a custom death message. |
| `REMOVE`    | The party member moves to `REMOVED` state. A message is shown. Adventure continues. |
| `NAVIGATE`  | The adventure navigates to a specified section. The party member moves to `REMOVED` state. A message is shown. |

```json
{ "type": "GAME_OVER", "message": "Your ship sinks. The sea claims you." }
{ "type": "REMOVE",    "message": "Theron falls. You press on alone." }
{ "type": "NAVIGATE",  "section": 200, "message": "Your ship is destroyed." }
```

For `NAVIGATE`, the member is moved to `REMOVED` before navigation occurs.

---

## Status Bar Display

Active party members appear in the status bar alongside player stats:

```
SKILL:10  STAMINA:18/20  LUCK:8  Gold:5
The Banshee — CREW:17/20  HULL:9/10
Theron      — SKILL:8  STAMINA:14/18
```

Each active party member occupies its own line, showing all its stats as `current/max`. If max equals current (full health), the `/max` part may be omitted for readability.

`WAITING` and `REMOVED` members do not appear in the status bar.

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

If a listed party member is in `WAITING` or `REMOVED` state when the combat event fires, that member is excluded from the encounter as if they were not listed. The encounter proceeds with the remaining participants.

---

## Party Member Conditions

Conditions can test a party member's stat or state, enabling choices or events to be gated on party composition:

| Condition Type           | Description |
|--------------------------|-------------|
| `PARTY_STAT_AT_LEAST`    | Party member's named stat >= threshold. Member must be `ACTIVE`. |
| `PARTY_STAT_AT_MOST`     | Party member's named stat <= threshold. Member must be `ACTIVE`. |
| `PARTY_MEMBER_ACTIVE`    | Party member is currently in `ACTIVE` state. |
| `PARTY_MEMBER_WAITING`   | Party member is in `WAITING` state (not yet joined). |
| `PARTY_MEMBER_REMOVED`   | Party member is in `REMOVED` state (departed or defeated). |

> **Rename note:** The conditions previously named `PARTY_MEMBER_PRESENT` and `PARTY_MEMBER_DEFEATED` are replaced by `PARTY_MEMBER_ACTIVE` and `PARTY_MEMBER_REMOVED` respectively to align with the lifecycle model.

---

## JSON Example

```json
{
  "partyMembers": [
    {
      "id": "ship",
      "displayName": "The Banshee",
      "lifeStat": "CREW",
      "initialState": "WAITING",
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
      "initialState": "WAITING",
      "onDefeat": { "type": "REMOVE", "message": "Theron falls. You press on alone." },
      "stats": {
        "SKILL":   { "initial": "1d6+4" },
        "STAMINA": { "initial": "2d6+6" }
      }
    }
  ]
}
```

### Script examples

```lua
-- Section 10 onEnter: player boards ship
ctx.addPartyMember('ship')
ctx.showMessage('You board The Banshee. She is ready to sail.')

-- Section 50 onEnter: companion joins the party
ctx.addPartyMember('companion')
ctx.showMessage('Theron falls in step beside you.')

-- Section 80 onEnter: companion chooses to leave
ctx.removePartyMember('companion')
ctx.showMessage('"My road ends here," Theron says. He turns and walks away.')

-- Section 120 onEnter: ship sold in port
ctx.removePartyMember('ship')
ctx.showMessage('You sell The Banshee to a merchant. The gold clinks in your purse.')

-- Section 150 onEnter: Theron returns (re-joining a previously removed member)
ctx.addPartyMember('companion')
ctx.showMessage('Against all odds, Theron stands before you once more.')
```

---

## See Also

- **Spec: Scripting** — full `ctx` API including `ctx.addPartyMember` and `ctx.removePartyMember`
- **Spec: Adventure Structure** — adventure-level `partyMembers` list
- **Spec: Combat** — combat participant resolution
