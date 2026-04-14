# Design: Adventure Loader

## Responsibilities

`AdventureLoader` reads an adventure from an external source and produces a validated `Adventure` domain object. The default implementation reads JSON files from disk.

---

## Interface

```java
public interface AdventureLoader {
    Adventure load(String adventureId) throws AdventureLoadException;
}
```

`AdventureLoadException` is a checked exception wrapping parse errors, missing files, or validation failures.

---

## File Location Convention

Adventures are stored as JSON files under an `adventures/` directory:

```
adventures/
└── the-warlock-of-firetop-mountain.json
```

`JsonAdventureLoader` is constructed with the root path:

```java
new JsonAdventureLoader(Path.of("adventures"));
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
  "sections": [
    {
      "number": 1,
      "type": "NORMAL",
      "narrative": "You stand before the entrance to Firetop Mountain...",
      "events": [
        {
          "type": "STAT_CHANGE",
          "attribute": "STAMINA",
          "delta": -1
        }
      ],
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
          "creatures": [
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

## Validation Rules (enforced at load time)

| Rule | Error if violated |
|------|------------------|
| `startSection` exists in `sections` | `AdventureLoadException` |
| All `targetSection` values in choices reference an existing section | `AdventureLoadException` |
| All `successSection` / `failSection` in test events reference existing sections | `AdventureLoadException` |
| Sections of type NORMAL with no choices and no NAVIGATE event | `AdventureLoadException` |
| Sections of type VICTORY or INSTANT_DEATH have no choices | Warning only (choices are ignored) |
| Creature SKILL and STAMINA are > 0 | `AdventureLoadException` |

---

## Test Strategy

- Unit test `JsonAdventureLoader` against small fixture JSON files stored under `src/test/resources/adventures/`.
- Test valid adventures load correctly.
- Test each validation rule with a corresponding malformed fixture that triggers `AdventureLoadException`.
- `InMemoryAdventureLoader` can be used in engine tests to supply adventures programmatically without touching the filesystem.
