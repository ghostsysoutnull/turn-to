# Spec: Adventure Structure

## Overview

An adventure is a self-contained story composed of numbered **sections**. The player begins at the adventure's designated starting section and navigates by making choices or having outcomes determined by events and scripts. Each adventure has exactly one starting section and one or more victory sections.

---

## Adventure

| Field             | Type     | Description |
|-------------------|----------|-------------|
| id                | string   | Unique identifier |
| title             | string   | Display title |
| description       | string   | Short blurb shown at the start |
| startSection      | int      | Section number where the adventure begins |
| initialProvisions | int      | Number of provisions the player starts with |
| items             | list     | All item definitions available in this adventure |
| partyMembers      | list     | Named entities with configurable stats that travel with the player (ships, companions, etc.) |
| combatSystems     | list     | Combat system ids used by this adventure beyond the default `"personal"` system |
| scripts           | ScriptBlock | Lifecycle hook scripts at adventure level |
| grids             | list (optional) | Location networks: spatially organised grids of cells the player can explore. See **Spec: Location Networks** for the full model. |
| chapters          | list (optional) | Authoring structure: chapter definitions with briefs and gate contracts. The engine ignores this field entirely. See **Spec: Adventure Authoring Structure** for the full chapter and gate model. |

### Adventure Lifecycle Hooks

| Hook          | Fires when |
|---------------|-----------|
| `onLoad`      | Adventure file is parsed; player not yet created. Use to initialise adventure-wide state variables. |
| `onStart`     | Player has been created and the first section is about to display. |
| `onVictory`   | Player reached a victory section. |
| `onGameOver`  | Player's STAMINA reached 0. |

---

## Section

A section is a single numbered node in the adventure graph.

| Field     | Type            | Description |
|-----------|-----------------|-------------|
| number    | int             | Unique identifier within the adventure |
| narrative | string          | The text displayed to the player |
| events    | list of Event   | Structured events that execute on entry, before choices are shown |
| choices   | list of Choice  | Zero or more choices presented to the player |
| type      | SectionType     | Normal, Victory, or Instant Death |
| scripts   | ScriptBlock     | Lifecycle hook scripts at section level |

### Section Types

| Type          | Behaviour |
|---------------|-----------|
| NORMAL        | Standard section; player proceeds via choices or events |
| VICTORY       | The adventure ends with a win message |
| INSTANT_DEATH | The adventure ends with a death message regardless of STAMINA |

### Sections with No Choices

A section with an empty choice list and no navigation event or `onEnter` script that navigates **must** be a VICTORY or INSTANT_DEATH section. The game engine enforces this constraint at adventure load time.

### Section Lifecycle Hooks

| Hook        | Fires when |
|-------------|-----------|
| `onEnter`   | Section is entered, before narrative is displayed. |
| `onDisplay` | Narrative is about to be shown; script may alter the displayed text. |
| `onChoices` | Choices are about to be presented; script may inject or suppress choices dynamically. |
| `onExit`    | Player has selected a choice, before navigation to the target section. |

---

## Events

Structured events are simple, data-driven actions that fire on section entry in the order listed. They are the right tool for straightforward, predictable effects. Complex or conditional logic belongs in lifecycle scripts.

### Event Types

| Event Type  | Description |
|-------------|-------------|
| COMBAT      | Triggers a combat encounter with one or more creatures |
| STAT_CHANGE | Modifies a player attribute (e.g. -2 STAMINA, +1 LUCK) |
| ITEM_GAIN   | Adds an item to the player's inventory |
| ITEM_LOSS   | Removes an item from the player's inventory |
| LUCK_TEST   | Forces a Luck Test; outcome routes to different sections |
| SKILL_TEST  | Forces a Skill Test (roll 2d6 under SKILL); routes on outcome |
| NAVIGATE    | Unconditionally sends the player to another section |
| GOLD_CHANGE | Adds or subtracts Gold |

### Event Outcomes

Events with branching outcomes (LUCK_TEST, SKILL_TEST, COMBAT) specify a **success target section** and a **failure target section**. If not specified, the section continues to the next event or to the choice list.

---

## Choice

| Field         | Type                | Description |
|---------------|---------------------|-------------|
| text          | string              | Label shown to the player |
| targetSection | int                 | Section number to navigate to. Mutually exclusive with `toGrid`/`toCell`. |
| toGrid        | string              | Grid id to enter. Must be paired with `toCell`. Mutually exclusive with `targetSection`. |
| toCell        | string              | Id of the entry cell within the grid. Must be paired with `toGrid`. |
| condition     | Condition (optional)| Prerequisite that must be met for the choice to appear |
| id            | string (optional)   | Stable identifier used by `onChoices` scripts to hide this choice. Must be unique within the section if present. |

A choice must have exactly one navigation target: either `targetSection`, or `toGrid` + `toCell`.

---

## Conditions

Conditions guard choices or events, making them visible/active only when met. For complex conditions, use an `onChoices` script instead.

| Condition Type   | Description |
|------------------|-------------|
| HAS_ITEM         | Player carries a specific item |
| LACKS_ITEM       | Player does not carry a specific item |
| STAT_AT_LEAST    | A given attribute is >= a threshold |
| STAT_AT_MOST     | A given attribute is <= a threshold |
| HAS_GOLD         | Player has >= a gold amount |
| STATE_EQUALS     | A state variable equals a given value (string, number, or boolean) |
| STATE_NOT_EQUALS | A state variable does not equal a given value |

Conditions on choices hide the choice entirely if not met. Conditions on events skip the event entirely if not met.

---

## Choice Visibility: Evaluation Order

When a section has both declarative conditions on choices and an `onChoices` script, evaluation proceeds in two stages:

1. **Declarative conditions** are evaluated on all static choices. Choices whose conditions are not met are removed from the visible list.
2. **`onChoices` script** fires on the already-filtered list. It may inject new choices via `ctx.addChoice` or hide remaining choices via `ctx.hideChoice`.

`ctx.hideChoice(id)` on a choice that a condition already removed is a no-op. The two-stage model means declarative conditions are the fast path for simple cases; scripts handle logic that cannot be expressed as data.

---

## Items

Items are defined once at the adventure level and referenced by name throughout sections and scripts. See **Spec: Items** for the full item model.

Each item definition includes:
- A unique name
- A description shown in the inventory
- A category (USABLE, EQUIPPABLE, KEY, PASSIVE)
- A script block with item lifecycle hooks (`onPickup`, `onDrop`, `onUse`, `onEquip`, `onUnequip`, `onCombatRound`)

---

## Party Members

Adventures can declare zero or more party members — named entities with fully configurable stats that persist across sections. See **Spec: Party Members** for the full definition.

---

## JSON Structure (overview)

```json
{
  "id": "the-warlock-of-firetop-mountain",
  "title": "The Warlock of Firetop Mountain",
  "startSection": 1,
  "initialProvisions": 10,
  "scripts": {
    "onLoad": "state.set('doorUnlocked', false)",
    "onStart": "ctx.showMessage('Your adventure begins...')"
  },
  "items": [ ... ],
  "grids": [ ... ],
  "sections": [
    {
      "number": 1,
      "type": "NORMAL",
      "narrative": "You stand before the entrance...",
      "events": [
        { "type": "STAT_CHANGE", "attribute": "STAMINA", "delta": -1 }
      ],
      "scripts": {
        "onEnter": "if ctx.hasItem('Torch') then ctx.showMessage('Your torch lights the way.') end",
        "onChoices": "if ctx.hasItem('Iron Key') and state.get('bridgeCrossed') then ctx.addChoice('Unlock the gate', 45) end"
      },
      "choices": [
        { "text": "Enter the mountain", "targetSection": 2 },
        { "text": "Explore the caves", "toGrid": "cave-network", "toCell": "entrance" },
        {
          "text": "Pass through the door",
          "targetSection": 45,
          "condition": { "type": "STATE_EQUALS", "key": "doorUnlocked", "value": true }
        },
        {
          "text": "Flee",
          "id": "flee",
          "targetSection": 3
        }
      ]
    }
  ]
}
```
