# Adventure Authoring Pipeline

This document describes the end-to-end process for creating a new adventure, from a user-supplied design brief to a complete, playable adventure JSON file.

---

## Overview

```
User
 │  (design brief)
 ▼
Adventure Architect Agent   — one run, whole adventure
 │  produces: adventure JSON skeleton, adventure manifest,
 │            chapter briefs, gate contracts, section allocations,
 │            grid spatial briefs, grid dimensions and entry/exit mapping
 ▼
User review of scaffold
 │  (approve or revise chapter plan, gate contracts, grid briefs, manifest)
 ▼
Load check gate             — JsonAdventureLoader.load() against the skeleton
 │  fails fast on format mismatches before any content is written
 ▼
Adventure Author Agents     — one run per chapter, parallel where possible
Grid Agents                 — one run per grid, parallel where possible
 │  Author: produces chapter sections JSON, manifest additions
 │  Grid:   produces grid cells JSON, manifest additions
 ▼
Manifest merge              — collect manifest additions from all agents
 ▼
(optional) Consistency Check Agent
 │  reads: all sections, all grids, manifest
 │  reports: continuity issues, dangling references, unreachable cells
 ▼
Final assembly              — merge sections and grids into adventure JSON
 ▼
Playable adventure
```

---

## Phase 1: Adventure Design Brief

The user provides a design brief before invoking the Architect agent. The brief does not need to be exhaustive — the Architect fills in gaps and documents its decisions.

### Minimum required

| Field | Description |
|-------|-------------|
| Title | The adventure's name |
| Concept | One paragraph: premise, setting, core conflict |
| Scale | `short` (~100–200 sections), `standard` (~300–500), or `long` (~400–800) |

### Optional but useful

| Field | Description |
|-------|-------------|
| Tone | Genre, mood, pacing (e.g. "dark dungeon crawl", "comic pirate caper") |
| Narrative beats | Key story moments that must occur, in rough order |
| Key characters | Named NPCs the user wants to exist |
| Key items | Items the user wants in the adventure |
| Starting conditions | Player stats, starting items, initial state variables |

---

## Phase 2: Adventure Architect Agent

**Invocation**: one run per adventure.

**Input**: the design brief.

**Output**:
- `adventures/<id>.json` — adventure JSON skeleton with `chapters` array (briefs + gate contracts), `items` list, empty `sections` array.
- `adventures/<id>-manifest.json` — adventure manifest with all planned items, characters, and locations.
- Structured summary: chapter plan table, cross-chapter item list, authoring dependency order.

**User review gate**: the user reviews the scaffold before any chapter authoring begins. Changes to gate contracts or section allocations after authoring has started are expensive — a gate change may require rewriting sections in the affected chapters.

**Load check gate**: before any chapter authoring begins, run `JsonAdventureLoader` against the skeleton JSON and verify it loads without error:

```
mvn exec:java -Dexec.mainClass=com.tas.neo.loader.JsonAdventureLoader \
  -Dexec.args="adventures/<id>.json" 2>&1 | grep -i "error\|fail\|exception"
```

Or from a test:
```java
new JsonAdventureLoader(Path.of("adventures")).load("<id>");
```

If the skeleton fails to load, fix it before proceeding. Item definitions, chapter structure, and script stubs must all parse cleanly. This catches format mismatches between the Architect's output and the loader's expectations before any content is written.

---

## Phase 3: Adventure Author Agents and Grid Agents

**Invocation**: one run per chapter (Author Agent) or per grid (Grid Agent). Units with no incoming manifest dependencies can run in parallel. Author Agents and Grid Agents may run concurrently with each other.

### Dependency order

A chapter or grid may be authored in parallel with another unit if and only if:
- Its gate/arrival contract does not reference any item, character, or state introduced by a unit that has not yet been authored.
- Its manifest dependencies (items it references) are already declared in the manifest.

The Architect's structured summary includes a "Ready for Authoring" list identifying which units can start immediately and which must wait.

### Manifest update protocol

After each chapter author completes:

1. Collect the `## Manifest Updates` section from the author's structured summary.
2. Add any new items, characters, or locations to `adventures/<id>-manifest.json`.
3. Update the manifest before starting any chapter that depends on those entities.

If the Architect pre-declared all entities, the manifest update step is a verification only — no new entries should appear. If a chapter author introduces an unplanned entity, it must be added to the manifest and any dependent chapters must be checked for conflicts before they proceed.

### Section number allocation

Each chapter is allocated a non-overlapping section number range by the Architect. Chapter authors must not use section numbers outside their range. The Architect's `sectionRange` field in the chapter JSON is the authoritative source.

---

## Phase 4: Consistency Check (optional)

A consistency checker agent reads all authored sections and the final manifest. It checks for:

| Check | What it looks for |
|-------|------------------|
| Dangling references | `targetSection` values that do not exist in any chapter's sections |
| Orphaned sections | Sections that are never the target of any choice or navigation event |
| Item continuity | Items referenced in sections that are not in the manifest or `items` list |
| Gate fulfilment | Exit sections that do not satisfy their chapter's out-contract conditions |
| Narrative consistency | Characters or locations that appear inconsistently across chapters |

The consistency checker reports issues but does not fix them. Issues are routed back to the relevant chapter author for correction.

---

## Phase 5: Final Assembly

Merge all chapter `sections` arrays into the adventure JSON's `sections` array. The Architect's JSON skeleton already contains the `chapters` array, `items` list, and metadata. Final assembly is additive — no existing fields are changed.

```json
{
  "id": "...",
  "title": "...",
  "startSection": 1,
  "items": [ ... ],
  "chapters": [ ... ],
  "sections": [
    ...ch1 sections...,
    ...ch2 sections...,
    ...chN sections...
  ]
}
```

Section ordering within the array does not matter — the engine navigates by section number, not array index.

---

## Section Budget Reference

| Scale | Chapters | Sections per chapter | Total sections |
|-------|----------|----------------------|----------------|
| Short | 3–5 | 30–60 | ~100–200 |
| Standard | 6–10 | 30–60 | ~300–500 |
| Long | 10–15 | 30–60 | ~400–800 |

The target range per chapter is **30–60 sections**. An agent authoring more than 60 sections in a single run risks context rot — narrative drift, forgotten constraints, and inconsistent item tracking. If a chapter naturally wants more than 60 sections, split it into two chapters with a gate between them.

---

## Agent Role Definitions

| Agent | Role definition |
|-------|----------------|
| Adventure Architect | `workflow/agents/adventure-architect-agent.md` |
| Adventure Author | `workflow/agents/adventure-author-agent.md` |
| Grid Agent | `workflow/agents/grid-agent.md` |

---

## How to Invoke the Architect Agent

```
You are the Adventure Architect Agent for TAS Neo. Your role, responsibilities,
and boundaries are defined in workflow/agents/adventure-architect-agent.md — read it first.

Task: Scaffold a new adventure from the following design brief.

Design brief:
[paste brief here]
```

## How to Invoke a Chapter Author Agent

```
You are the Adventure Author Agent for TAS Neo. Your role, responsibilities,
and boundaries are defined in workflow/agents/adventure-author-agent.md — read it first.

Task: Write all sections for chapter <id> of adventure <adventure-id>.

- Adventure file: adventures/<adventure-id>.json
- Manifest: adventures/<adventure-id>-manifest.json
- Your chapter id: <id>
- Your section range: <from>–<to>
```

## How to Invoke a Grid Agent

```
You are the Grid Agent for TAS Neo. Your role, responsibilities,
and boundaries are defined in workflow/agents/grid-agent.md — read it first.

Task: Write all cells for grid <grid-id> of adventure <adventure-id>.

- Adventure file: adventures/<adventure-id>.json
- Manifest: adventures/<adventure-id>-manifest.json
- Your grid id: <grid-id>
- Dimensions: <width>×<height>×<floors>
- Spatial brief: [paste spatial brief from Architect output here]
```
