# Spec: Location Networks

## Overview

A **location network** (or **grid**) is a spatial structure of traversable cells arranged on a three-dimensional coordinate system. Grids model mazes, dungeons, multi-floor buildings, and any region the player can explore by moving in cardinal directions. They exist alongside sections in the adventure — a section sends the player into a grid, and a grid passage sends the player back out to a section.

Grids are first-class adventure entities. They are not embedded in the `sections` array and their cells are not numbered as sections. A cell is identified by its position within its grid.

---

## Grid

A grid is declared at the adventure level in a `grids` array.

| Field    | Type         | Description |
|----------|--------------|-------------|
| `id`     | string       | Unique identifier within the adventure |
| `width`  | int          | Number of columns (x-axis). Valid x values: 0 to width−1 |
| `height` | int          | Number of rows (y-axis). Valid y values: 0 to height−1 |
| `floors` | int          | Number of floors (z-axis). Valid z values: 0 to floors−1. Default: 1 |
| `cells`  | list of Cell | The cells that exist in this grid. Positions not occupied by a cell are impassable walls. |

---

## Cell

A cell is a single navigable location within a grid.

| Field      | Type               | Description |
|------------|--------------------|-------------|
| `x`        | int                | Column position |
| `y`        | int                | Row position |
| `z`        | int                | Floor position. Default: 0 |
| `id`       | string (optional)  | Named identifier. Required for cells used as grid entry points. Must be unique within the grid. |
| `narrative`| string             | Text displayed to the player on arrival |
| `events`   | list of Event      | Structured events that fire on entry, before passages are shown. Same event types as sections. |
| `scripts`  | ScriptBlock        | Lifecycle hooks. Same hooks as sections: `onEnter`, `onDisplay`, `onChoices`, `onExit`. |
| `passages` | map                | Directional exits from this cell. Keys are direction names; values are Passage objects. |
| `choices`  | list of Choice     | Optional non-navigation choices (e.g. "Search the room", "Examine the altar"). Presented alongside passage-derived choices. |

Cells do not have a `type` field. Every cell is a navigable location. Dead ends are cells with no outward passages.

### Cell Lifecycle Hooks

Cell hooks fire in exactly the same way as section hooks.

| Hook        | Fires when |
|-------------|-----------|
| `onEnter`   | Cell is entered, before narrative is displayed |
| `onDisplay` | Narrative is about to be shown |
| `onChoices` | Passage choices and explicit choices are about to be presented |
| `onExit`    | Player has selected a passage or choice, before navigation executes |

---

## Passages

A passage is a directional exit from a cell. The player navigates the grid by selecting passages, which are presented as choices alongside any explicit choices on the cell.

| Field       | Type                 | Description |
|-------------|----------------------|-------------|
| `label`     | string (optional)    | Choice text shown to the player. Default labels listed below. |
| `condition` | Condition (optional) | Same condition types as section choices. Passage is hidden if not met. |
| `toSection` | int (optional)       | Exit the grid and navigate to this section number. Mutually exclusive with directional targeting. |

A passage without `toSection` targets the adjacent cell implied by its direction and the current cell's coordinates. A passage with `toSection` exits the grid entirely.

### Directions and Coordinate Deltas

| Direction | x | y | z | Default label |
|-----------|---|---|---|---------------|
| `north`   | 0 | −1 | 0 | "Go north" |
| `south`   | 0 | +1 | 0 | "Go south" |
| `east`    | +1 | 0 | 0 | "Go east" |
| `west`    | −1 | 0 | 0 | "Go west" |
| `up`      | 0 | 0 | +1 | "Go up" |
| `down`    | 0 | 0 | −1 | "Go down" |

Each cell may declare at most one passage per direction.

### Passage Visibility

Passage visibility follows the same two-stage evaluation as section choices:

1. Declarative conditions are evaluated. Passages whose conditions are not met are hidden.
2. The `onChoices` script fires on the remaining visible list. `ctx.addChoice` and `ctx.hideChoice` operate on the combined list of passage-derived choices and explicit choices.

---

## Entering a Grid

A section choice targets a grid by setting `toGrid` (grid id) and `toCell` (cell id) instead of `targetSection`. The referenced cell must exist and must have a matching `id`.

```json
{
  "text": "Enter the dungeon",
  "toGrid": "dungeon-level-1",
  "toCell": "entrance"
}
```

A choice must have exactly one navigation target: either `targetSection`, or `toGrid` + `toCell`. Both forms support `condition` and `id` in the same way.

---

## Exiting a Grid

A passage with `toSection` exits the grid and navigates to the specified section. The `toSection` value must reference an existing section in the adventure.

```json
"passages": {
  "north": { "toSection": 55, "label": "Leave the dungeon" }
}
```

`ctx.navigateTo(sectionNumber)` in a cell script also exits the grid and navigates to the specified section.

---

## Validation Rules

All rules are enforced at adventure load time.

| Rule |
|------|
| All cell positions within declared grid dimensions |
| No two cells at the same `(x, y, z)` within a grid |
| No two cells with the same `id` within a grid |
| Passages without `toSection` target a coordinate that contains a cell |
| `toSection` values in passages reference an existing section |
| `toGrid` + `toCell` in section choices resolve to an existing grid and cell |
| A choice must not declare both `targetSection` and `toGrid`/`toCell` |

---

## JSON Example

```json
{
  "id": "dungeon-level-1",
  "width": 3,
  "height": 3,
  "floors": 2,
  "cells": [
    {
      "id": "entrance",
      "x": 0, "y": 0, "z": 0,
      "narrative": "Cold air rushes past as you step inside. Corridors lead east and south.",
      "events": [],
      "scripts": {},
      "passages": {
        "north": { "toSection": 55, "label": "Leave the dungeon" },
        "east":  {},
        "south": { "condition": { "type": "HAS_ITEM", "itemName": "Torch" }, "label": "Venture into the dark" }
      }
    },
    {
      "x": 1, "y": 0, "z": 0,
      "narrative": "A guard post, long since abandoned. A staircase rises to the north.",
      "events": [],
      "passages": {
        "west": {},
        "up":   { "label": "Climb the staircase" }
      },
      "choices": [
        { "text": "Search the desk", "targetSection": 201 }
      ]
    },
    {
      "x": 1, "y": 0, "z": 1,
      "narrative": "The upper level. Arrow slits overlook the corridor below.",
      "passages": {
        "down":  { "label": "Descend the staircase" },
        "east":  {
          "condition": { "type": "STATE_EQUALS", "key": "gateOpen", "value": true },
          "label": "Pass through the open gate"
        }
      }
    }
  ]
}
```

---

## Adventure-level JSON structure

```json
{
  "id": "the-warlock-of-firetop-mountain",
  "sections": [ ... ],
  "grids": [
    { "id": "dungeon-level-1", "width": 3, "height": 3, "floors": 2, "cells": [ ... ] }
  ],
  "items": [ ... ]
}
```

---

## See Also

- **Spec: Adventure Structure** — sections, choices, events, and conditions
- **Spec: Scripting** — lifecycle hooks and the `ctx` API available in cell scripts
- **Spec: Items** — items referenced in passage conditions and cell events
