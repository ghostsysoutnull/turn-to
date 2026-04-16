# Design: Adventure Loader

## Responsibilities

`AdventureLoader` reads an adventure from an external source and produces a fully validated `Adventure` domain object. The default implementation reads JSON files from disk.

---

## Interface

```java
public interface AdventureLoader {
    Adventure load(String adventureId) throws AdventureLoadException;
}
```

`AdventureLoadException` is a checked exception wrapping parse errors, missing files, or validation failures.

---

## File Convention

Adventures are stored as JSON files under an `adventures/` directory. `JsonAdventureLoader` is constructed with the root path and derives the filename from the adventure id.

```java
public class JsonAdventureLoader implements AdventureLoader {
    public JsonAdventureLoader(Path adventuresRoot);
}
```

The loader does **not** take a `Dice` argument. `StatDefinition` entries (dice formulas) are stored verbatim in `PartyMemberDefinition` and resolved by `Game` at adventure start — not at load time.

```
adventures/
└── the-warlock-of-firetop-mountain.json
```

---

## JSON Format

```json
{
  "id": "the-warlock-of-firetop-mountain",
  "title": "The Warlock of Firetop Mountain",
  "description": "Somewhere inside the monster-infested caverns...",
  "startSection": 1,
  "initialProvisions": 10,
  "combatSystems": [],
  "scripts": {
    "onLoad":  "state.set('doorUnlocked', false)",
    "onStart": "ctx.showMessage('Your adventure begins...')"
  },
  "items": [ ],
  "partyMembers": [ ],
  "grids": [
    {
      "id": "cave-network",
      "width": 3,
      "height": 3,
      "floors": 1,
      "cells": [
        {
          "id": "entrance",
          "x": 0, "y": 0, "z": 0,
          "narrative": "Cold air rushes past as you step inside.",
          "events": [],
          "scripts": {},
          "passages": {
            "east":  {},
            "north": { "toSection": 2, "label": "Leave the cave" }
          }
        }
      ]
    }
  ],
  "sections": [
    {
      "number": 1,
      "type": "NORMAL",
      "narrative": "You stand before the entrance to Firetop Mountain...",
      "events": [
        { "type": "STAT_CHANGE", "attribute": "STAMINA", "delta": -1 }
      ],
      "scripts": {
        "onChoices": "if ctx.hasItem('Map') then ctx.addChoice('Consult the map', 42) end"
      },
      "choices": [
        { "text": "Enter the mountain", "targetSection": 2 },
        { "text": "Explore the cave", "toGrid": "cave-network", "toCell": "entrance" },
        { "text": "Turn back", "targetSection": 400 }
      ]
    },
    {
      "number": 2,
      "type": "NORMAL",
      "narrative": "Inside the cave...",
      "events": [
        {
          "type": "COMBAT",
          "system": "personal",
          "participants": [],
          "opponents": [
            { "name": "Goblin", "skill": 5, "stamina": 6 }
          ],
          "simultaneous": false
        }
      ],
      "choices": [
        {
          "text": "Search the body",
          "targetSection": 3,
          "condition": { "type": "LACKS_ITEM", "itemName": "Goblin Key" }
        }
      ]
    }
  ]
}
```

---

## Defaults Applied at Load Time

| Field | Default when absent from JSON |
|-------|-------------------------------|
| `CombatEvent.system` | `"personal"` |
| `CombatEvent.simultaneous` | `false` |
| `Section.events` | empty list |
| `Section.choices` | empty list |
| `Adventure.grids` | empty list |
| `Adventure.items` | empty list |
| `Adventure.partyMembers` | empty list |

These defaults are applied by `JsonAdventureLoader` during deserialization, before validation runs.

---

## Validation Rules

All rules are enforced at load time. Violations throw `AdventureLoadException`.

| Rule |
|------|
| `startSection` exists in `sections` |
| All `targetSection` values in choices reference an existing section |
| All `successSection` / `failSection` in test events reference an existing section |
| NORMAL sections with no choices must have a NAVIGATE event or `onEnter` script that navigates |
| Creature SKILL and STAMINA are > 0 |
| All item names referenced in events or scripts exist in the adventure's item list |
| All party member ids referenced in `CombatEvent.participantIds` exist in `partyMembers` |
| All `combatSystems` ids beyond `"personal"` are registered in `CombatSystemRegistry` |
| All grid ids are unique within the adventure |
| All cell positions `(x, y, z)` within their grid's declared dimensions |
| No two cells at the same `(x, y, z)` within a grid |
| No two cells with the same `id` within a grid |
| All cell passages without `toSection` target a coordinate that contains a cell |
| All `toSection` values in cell passages reference an existing section |
| All `toGrid` + `toCell` pairs in choices reference an existing grid id and a cell with a matching `id` |
| A choice must not declare both `targetSection` and `toGrid`/`toCell` |

Sections of type VICTORY or INSTANT_DEATH with choices produce a warning — the choices are ignored.

---

## Test Strategy

| Layer | What | Approach |
|-------|------|----------|
| unit | Valid adventure loads | Fixture JSON → assert `Adventure` fields correct |
| unit | Each validation rule | Corresponding malformed fixture → assert `AdventureLoadException` |
| unit | Party member stat resolution | Fixture with dice-formula stat, `FixedDice` → assert expected value |
| unit | `playerStats` field parsed correctly | Fixture JSON with `playerStats` block → assert `adventure.playerStats()` contains SKILL, STAMINA, LUCK with correct formulas |
| unit | Valid grid loads | Fixture JSON with grid → assert grid id, dimensions, cell count, passage structure correct |
| unit | Each grid validation rule | Corresponding malformed fixture → assert `AdventureLoadException` |
| unit | `toGrid`/`toCell` choice resolves | Fixture with section choice targeting a named grid cell → assert resolves without error |
| unit | `toGrid`/`toCell` choice with unknown grid | Malformed fixture → assert `AdventureLoadException` |
| unit | `toGrid`/`toCell` choice with unknown cell id | Malformed fixture → assert `AdventureLoadException` |
| — | Engine tests | `InMemoryAdventureLoader` — supplies `Adventure` objects directly, no filesystem |
