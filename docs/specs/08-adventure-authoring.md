# Spec: Adventure Authoring Structure

## Overview

Large adventures are authored in discrete **chapters** — bounded subgraphs of the adventure with defined entry and exit points. Chapters are an authoring convention only. The engine sees a flat list of sections and has no knowledge of chapter structure. No load-time or runtime validation of chapter boundaries is performed.

This spec defines: what a chapter is, what a gate is, how chapter briefs anchor an AI authoring agent, and the adventure manifest that maintains named entity consistency across the whole adventure.

---

## Chapters

A **chapter** is a contiguous, bounded portion of the adventure graph that can be authored independently.

### Rules

- A chapter has exactly one **entry section** — the section a player arrives at when entering the chapter.
- A chapter has one or more **exit sections** — sections from which the player leaves the chapter by moving to a gate entry section in another chapter.
- Sections within a chapter may reference each other freely.
- A section must not reference a section in a different chapter by number, **except** for gate entry sections of other chapters. Those numbers are known from the gate contract and may appear as choice targets or navigation targets in exit sections.
- The engine does not enforce these rules. They are authoring discipline.

### Section Count Guidance

| Adventure scale | Chapters | Sections per chapter | Total sections |
|-----------------|----------|----------------------|----------------|
| Short           | 3–5      | 30–60                | ~100–200       |
| Standard        | 6–10     | 30–60                | ~300–500       |
| Long            | 10–15    | 30–60                | ~400–800       |

The target range is **30–60 sections per chapter**. A full standard adventure of roughly 400 sections is 6–10 chapters.

---

## Gates

A **gate** is the authoring contract at a chapter boundary. It is not enforced by the engine. It exists so that:

1. An AI authoring agent writing one chapter knows exactly what to assume about player state on arrival and what to guarantee on departure.
2. Adventure authors can reason about cross-chapter continuity without coupling chapter authorship.

### Gate Structure

Each gate is defined from the perspective of the **receiving chapter** (the chapter the player is entering).

| Field            | Description |
|------------------|-------------|
| `entrySection`   | The section number that is the entry point of the receiving chapter. Choices and navigation events in exit sections of the sending chapter point here. |
| `in`             | The **in-contract**: what player state the receiving chapter's author may assume on arrival. |
| `out`            | The **out-contract**: what player state the sending chapter's author must guarantee on departure. |
| `normalize`      | Optional normalization rules: adjustments the entry section may apply before the chapter's story proceeds (e.g. removing a temporary quest item, capping a stat). |
| `branches`       | Optional branch conditions: different entry sections depending on player state (see Branch Conditions). |

### In-Contract

The in-contract documents the player state that is guaranteed to be true when a player arrives at the gate. The authoring agent for the receiving chapter may rely on these facts.

In-contract fields are informational prose and structured conditions. Example:

```
in:
  required:
    - Player carries "Iron Key"
  possible:
    - Player may carry "Silver Coin"
    - Player STAMINA may be any value > 0
  assumed_state:
    - state variable "vaultEntered" has been set to true
```

### Out-Contract

The out-contract documents what the sending chapter's author must ensure before the player exits. It documents which items a player **may** carry when leaving and any state that must be set.

Out-contract fields are informational prose. Example:

```
out:
  guaranteed:
    - Player carries "Iron Key" if they solved the vault puzzle
  possible:
    - Player may carry "Silver Coin" (found in section 85)
  forbidden:
    - Player must not carry "Temple Pass" (consumed at section 92)
```

Cross-chapter items flow through the player's inventory. There is no special engine mechanism. The gate-out contract documents what may be carried; the gate-in contract documents what may be assumed.

### Normalization Rules

Some gates require the receiving chapter to put the player into a clean state before the story proceeds. Normalization rules are prose notes in the gate definition that instruct the author of the entry section on what to adjust:

```
normalize:
  - Remove "Temple Pass" from inventory (it has been used)
  - Set state.dungeonLevel = 2
```

These notes guide the authoring of the entry section's `onEnter` script or events. They are not executed automatically by the engine.

### Branch Conditions

A gate may have multiple possible entry sections depending on player state at departure. Each branch specifies a condition and the entry section number it routes to. The default branch is used when no condition matches.

Example:

```
branches:
  - condition: "Player carries 'Iron Key'"
    entrySection: 101
  - condition: "state.bribeAccepted == true"
    entrySection: 103
  - default:
    entrySection: 102
```

Branch conditions are prose-and-condition authoring notes, not engine rules. The sending chapter's exit sections implement the routing as choices with conditions or `onChoices` scripts. The branch table in the gate contract tells those authors which target section numbers to use.

---

## Chapter Brief

A **chapter brief** is the prose document that anchors an AI authoring agent before it writes a single section. An agent that begins writing without a complete brief may introduce contradictions, miss narrative obligations, or generate content inconsistent with the overall adventure.

### Required Contents

| Section | What it covers |
|---------|----------------|
| Narrative purpose | What this chapter must accomplish in the story. What questions it answers, what it sets up for later chapters. |
| Setting | The physical or environmental context. Named locations within the chapter. |
| Named characters | Every character who appears, with brief descriptions. Do not introduce characters not listed here. |
| Items in this chapter | Every item that exists in this chapter — gained, lost, or checked. Must be consistent with the adventure manifest. |
| Tone | Mood, pacing, register (tense action, slow horror, comic relief, etc.). |
| Arc contribution | How this chapter fits into the overall adventure arc and what the player should feel or know by the end. |
| Section range | The section numbers allocated to this chapter (e.g. sections 101–160). |
| Gate references | The gate entry section(s) of chapters this chapter may connect to, and the gate entry section of this chapter. |

A chapter brief is not a section-by-section outline. It anchors the agent at the chapter level. The agent decides the internal structure.

---

## Adventure Manifest

The **adventure manifest** is a top-level index of named entities across the whole adventure. It prevents authoring agents working on different chapters from inventing duplicates or contradicting established facts.

### Manifest Contents

| Section | What it tracks |
|---------|----------------|
| Items | Every item defined in the adventure, with the chapter where it is introduced. |
| Characters | Every named non-player character, with their introducing chapter and a one-line description. |
| Locations | Every named location (dungeon, room, city, landmark), with the chapter where it is first visited. |

### Rules

- Before an authoring agent writes a chapter, it reads the manifest to understand what already exists.
- After an authoring agent writes a chapter, it returns any new items, characters, and locations it introduced. These must be added to the manifest before the next chapter is authored.
- An authoring agent must not invent a name or item that conflicts with an existing manifest entry.
- The manifest does not replace the adventure's `items` list — item definitions remain there. The manifest records which chapter introduced each item to prevent duplicate definitions.

### Manifest Format

The manifest is a standalone JSON document alongside the adventure file. It is not embedded in the adventure JSON and is not read by the engine.

```json
{
  "adventureId": "the-warlock-of-firetop-mountain",
  "items": [
    { "name": "Iron Key",     "description": "Heavy iron key with a serpent engraving", "introducedInChapter": "ch1" },
    { "name": "Silver Coin",  "description": "An old coin bearing a forgotten king",    "introducedInChapter": "ch2" }
  ],
  "characters": [
    { "name": "Zagor",        "description": "The Warlock of Firetop Mountain, final antagonist", "introducedInChapter": "ch6" },
    { "name": "Old Merchant", "description": "A travelling trader found near the mountain entrance", "introducedInChapter": "ch1" }
  ],
  "locations": [
    { "name": "Firetop Mountain", "description": "The mountain where Zagor makes his lair", "introducedInChapter": "ch1" },
    { "name": "The Vault",        "description": "A locked chamber deep in the dungeon",   "introducedInChapter": "ch3" }
  ]
}
```

---

## JSON Representation

Chapters are declared in the adventure JSON as an optional top-level `chapters` array. The engine ignores this field entirely. Its purpose is to document authoring structure alongside the sections it describes.

### Adventure-level `chapters` field

```json
{
  "id": "the-warlock-of-firetop-mountain",
  "title": "The Warlock of Firetop Mountain",
  "startSection": 1,
  "chapters": [ ... ],
  "items": [ ... ],
  "sections": [ ... ]
}
```

### Chapter entry structure

| Field          | Type           | Description |
|----------------|----------------|-------------|
| `id`           | string         | Unique identifier for this chapter (e.g. `"ch1"`) |
| `title`        | string         | Human-readable chapter title |
| `sectionRange` | object         | `{ "from": N, "to": M }` — inclusive range of section numbers |
| `sectionList`  | array of int   | Explicit list of section numbers, used when sections are not contiguous. Use `sectionRange` or `sectionList`, not both. |
| `brief`        | string         | Prose chapter brief (see Chapter Brief above) |
| `gates`        | object         | `{ "entryGate": GateContract, "exitGates": [GateContract, ...] }`. `entryGate` is null for the first chapter. |

### Gate contract structure

An entry gate (`entryGate`) declares the state the receiving chapter may assume on arrival. An exit gate (`exitGates` entry) declares what the sending chapter must guarantee on departure and where the player goes next. All fields are optional except `entrySection` on exit gates.

```json
{
  "entrySection": 101,
  "in": {
    "required": [ "Player carries 'Iron Key'" ],
    "possible": [ "Player may carry 'Silver Coin'" ],
    "assumedState": [ "state.vaultEntered == true" ]
  },
  "out": {
    "guaranteed": [],
    "possible": [ "Player may carry 'Iron Key'", "Player may carry 'Silver Coin'" ],
    "forbidden": [ "Player must not carry 'Temple Pass'" ]
  },
  "normalize": [
    "Remove 'Temple Pass' from inventory at entry section"
  ],
  "branches": [
    { "condition": "Player carries 'Iron Key'", "entrySection": 101 },
    { "condition": "state.bribeAccepted == true", "entrySection": 103 },
    { "default": true, "entrySection": 102 }
  ]
}
```

Entry gates typically use `in`, `normalize`, and `branches`. Exit gates typically use `entrySection` and `out`. All fields may appear on either gate type where needed.

### Full example

```json
{
  "id": "the-warlock-of-firetop-mountain",
  "title": "The Warlock of Firetop Mountain",
  "startSection": 1,
  "chapters": [
    {
      "id": "ch1",
      "title": "The Mountain Entrance",
      "sectionRange": { "from": 1, "to": 60 },
      "brief": "The player arrives at the foot of Firetop Mountain and enters the dungeon. This chapter establishes the atmosphere of dread, introduces the Old Merchant as an early source of information and items, and sends the player deeper into the mountain. The chapter ends when the player crosses the stone bridge to the mid-dungeon.",
      "gates": {
        "entryGate": null,
        "exitGates": [
          {
            "entrySection": 61,
            "out": {
              "guaranteed": [],
              "possible": [ "Player may carry 'Lantern'", "Player may carry 'Iron Key'" ],
              "forbidden": []
            }
          }
        ]
      }
    },
    {
      "id": "ch2",
      "title": "The Mid-Dungeon",
      "sectionRange": { "from": 61, "to": 130 },
      "brief": "The player navigates the mid-dungeon, a maze of passages and trapped rooms. Key narrative event: discovering the locked vault. The chapter ends at the Warlock's antechamber gate.",
      "gates": {
        "entryGate": {
          "entrySection": 61,
          "in": {
            "required": [],
            "possible": [ "Player may carry 'Lantern'", "Player may carry 'Iron Key'" ],
            "assumedState": []
          }
        },
        "exitGates": [
          {
            "entrySection": 131,
            "out": {
              "guaranteed": [],
              "possible": [ "Player may carry 'Iron Key'", "Player may carry 'Silver Coin'" ],
              "forbidden": []
            }
          }
        ]
      }
    }
  ],
  "items": [ ],
  "sections": [ ]
}
```

---

## See Also

- **Spec: Adventure Structure** — the full adventure and section model that chapters describe
- **Spec: Items** — item definitions referenced in gate contracts and the adventure manifest
- **Spec: Scripting** — lifecycle hooks used in gate normalization entry sections
