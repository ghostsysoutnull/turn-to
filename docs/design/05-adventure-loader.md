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

Sections of type VICTORY or INSTANT_DEATH with choices produce a warning — the choices are ignored.

---

## Test Strategy

| What | Approach |
|------|----------|
| Valid adventure loads | Fixture JSON → assert `Adventure` fields correct |
| Each validation rule | Corresponding malformed fixture → assert `AdventureLoadException` |
| Party member stat resolution | Fixture with dice-formula stat, `FixedDice` → assert expected value |
| Engine tests | `InMemoryAdventureLoader` — supplies `Adventure` objects directly, no filesystem |
