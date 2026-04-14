# Spec: Adventure Structure

## Overview

An adventure is a self-contained story composed of numbered **sections**. The player begins at the adventure's designated starting section and navigates by making choices or having outcomes determined by events. Each adventure has exactly one starting section and one or more victory sections.

---

## Adventure

| Field       | Type     | Description |
|-------------|----------|-------------|
| id          | string   | Unique identifier |
| title       | string   | Display title |
| description | string   | Short blurb shown at the start |
| startSection | int     | Section number where the adventure begins |
| initialProvisions | int | Number of provisions the player starts with |

---

## Section

A section is a single numbered node in the adventure graph.

| Field    | Type            | Description |
|----------|-----------------|-------------|
| number   | int             | Unique identifier within the adventure |
| narrative| string          | The text displayed to the player |
| events   | list of Event   | Zero or more events that execute before choices are shown |
| choices  | list of Choice  | Zero or more choices presented to the player |
| type     | SectionType     | Normal, Victory, or Instant Death |

### Section Types

| Type         | Behaviour |
|--------------|-----------|
| NORMAL       | Standard section; player proceeds via choices or events |
| VICTORY      | The adventure ends with a win message |
| INSTANT_DEATH| The adventure ends with a death message regardless of STAMINA |

### Sections with No Choices

A section with an empty choice list and no navigation event **must** be a VICTORY or INSTANT_DEATH section. The game engine enforces this constraint at adventure load time.

---

## Choice

A choice represents a navigable option presented to the player.

| Field      | Type   | Description |
|------------|--------|-------------|
| text       | string | Label shown to the player |
| targetSection | int | Section number to navigate to |
| condition  | Condition (optional) | Prerequisite that must be met for the choice to appear |

---

## Events

Events are actions that fire automatically when a section is entered, before choices are shown. They execute in the order listed.

### Event Types

| Event Type     | Description |
|----------------|-------------|
| COMBAT         | Triggers a combat encounter with one or more creatures |
| STAT_CHANGE    | Modifies a player attribute (e.g. -2 STAMINA, +1 LUCK) |
| ITEM_GAIN      | Adds an item to the player's inventory |
| ITEM_LOSS      | Removes an item from the player's inventory |
| LUCK_TEST      | Forces a Luck Test; outcome routes to different sections |
| SKILL_TEST     | Forces a Skill Test (roll 2d6 under SKILL); routes on outcome |
| NAVIGATE       | Unconditionally sends the player to another section |
| GOLD_CHANGE    | Adds or subtracts Gold |

### Event Outcomes

Events that have branching outcomes (LUCK_TEST, SKILL_TEST, COMBAT) specify a **success target section** and a **failure target section**. If not specified, the section continues to the next event or to the choice list.

---

## Conditions

Conditions guard choices or events, making them visible/active only when met.

| Condition Type   | Description |
|------------------|-------------|
| HAS_ITEM         | Player carries a specific item |
| LACKS_ITEM       | Player does not carry a specific item |
| STAT_AT_LEAST    | A given attribute is >= a threshold |
| STAT_AT_MOST     | A given attribute is <= a threshold |
| HAS_GOLD         | Player has >= a gold amount |

Conditions on choices hide the choice entirely if not met. Conditions on events skip the event entirely if not met.
