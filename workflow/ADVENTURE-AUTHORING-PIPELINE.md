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
Adventure Reviewer Agent    — one run, whole scaffold
 │  checks: loader parses skeleton, section ranges contiguous,
 │           gate contract symmetry, brief sufficiency, dep order,
 │           scale sanity
 │  APPROVED → proceed    NEEDS FIXES → back to Architect Agent
 ▼
User review of verdict
 │  (optionally override advisory flags; approve or request revision)
 ▼
┌─────────────────────────────────────────────────────────────────┐

│  For each chapter (in dependency order):                        │
│                                                                 │
│  Adventure Author Agent    — one run per chapter                │
│   │  produces: chapter sections JSON, manifest additions        │
│   ▼                                                             │
│  Chapter Reviewer Agent    — one run per chapter                │
│   │  runs: mvn test -Dtest=ChapterValidationTest                │
│   │  checks: range, references, gate contracts, reachability    │
│   │  APPROVED → proceed    NEEDS FIXES → back to Author Agent   │
│   ▼                                                             │
│  Manifest merge            — add any new manifest entries       │
└─────────────────────────────────────────────────────────────────┘
 │
 ▼
Grid Agents                 — one run per grid, parallel where possible
 │  produces: grid cells JSON, manifest additions
 ▼
(optional) Consistency Check Agent
 │  reads: all sections, all grids, manifest
 │  reports: cross-chapter continuity issues, narrative drift
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

**Adventure Reviewer gate**: after the Architect produces its output, the Adventure Reviewer Agent runs before any chapter authoring begins. It checks:

- Loader parses the skeleton without error (`mvn test -Dtest=AdventureValidationTest`)
- Section ranges are contiguous, non-overlapping, and cover the total budget
- Gate contracts are symmetric (every item in `out` appears in the corresponding `in`)
- Chapter briefs are sufficient to anchor an author
- Dependency order is correct
- Scale is appropriate for the stated adventure length

The Reviewer returns **APPROVED** or **NEEDS FIXES**. Fixes go back to the Architect Agent. The pipeline does not proceed to authoring until the Reviewer approves.

**User review**: the user reviews the Reviewer's verdict before authoring begins. The user may override advisory flags (e.g. accept a thin brief) but must not override structural or gate contract failures — those are load-time or reference integrity errors that will cause authoring to fail or produce a broken adventure.

---

## Phase 3: Adventure Author Agents and Grid Agents

**Invocation**: one Author Agent run per chapter, in dependency order. Grid Agents run per grid. Units with no incoming manifest dependencies can run in parallel with each other. Author Agents and Grid Agents may run concurrently.

### Per-chapter review loop

Each chapter goes through a tight author → review loop before the next chapter begins:

1. **Author Agent** writes the chapter sections JSON and structured summary.
2. **Regenerate the adventure report**:
   ```
   mvn exec:java -Dexec.mainClass=com.tas.neo.analysis.AdventureReportGenerator \
     -Dexec.args="adventures/<id>.json"
   ```
   This writes `adventures/<id>-report.txt` — a compact summary the Chapter Reviewer Agent
   reads instead of the full JSON.
3. **Chapter Reviewer Agent** runs immediately after:
   - Reads `adventures/<id>-report.txt` first for structural facts
   - Executes `mvn test -Dtest=AdventureValidationTest,ChapterValidationTest`
   - Reviews gate contracts, reference integrity, reachability, and narrative alignment.
   - Returns **APPROVED** or **NEEDS FIXES** with section-level detail.
4. If **NEEDS FIXES**: route the review report back to the Author Agent. Repeat from step 1.
5. If **APPROVED**: update the manifest (step below), then proceed to the next chapter.

This loop catches problems before downstream chapters are authored against a broken gate. A gate contract error found in chapter 2 review costs one rewrite; the same error found after all four chapters are written costs four.

### Dependency order

A chapter may be authored once all chapters it depends on have been **APPROVED**:
- Its gate-in contract does not assume state introduced by a chapter that is not yet approved.
- Its manifest dependencies are already declared in the manifest.

The Architect's structured summary includes a "Ready for Authoring" list identifying which chapters can start immediately and which must wait.

### Manifest update protocol

After each chapter is **APPROVED** by the Chapter Reviewer:

1. Collect the `## Manifest Updates` section from the author's structured summary.
2. Add any new items, characters, or locations to `adventures/<id>-manifest.json`.
3. Update the manifest before starting any chapter that depends on those entities.

If the Architect pre-declared all entities, the manifest update step is a verification only — no new entries should appear. If a chapter author introduces an unplanned entity, it must be added to the manifest and any dependent chapters must be checked for conflicts before they proceed.

### Section number allocation

Each chapter is allocated a non-overlapping section number range by the Architect. Chapter authors must not use section numbers outside their range. The Architect's `sectionRange` field in the chapter JSON is the authoritative source.

---

## Phase 4: Cross-Adventure Consistency Check (optional)

Per-chapter structural and gate checks are handled by the Chapter Reviewer Agent during Phase 3. This optional phase addresses issues that only become visible when all chapters exist together.

A consistency checker agent reads all authored sections and the final manifest. It focuses on:

| Check | What it looks for |
|-------|------------------|
| Cross-chapter narrative consistency | Characters or locations that appear inconsistently across chapter boundaries |
| Tone and pacing | Narrative drift — chapters that feel tonally disconnected from each other |
| State variable naming | Variables set in early chapters and read in later chapters use consistent names |
| Unused manifest entries | Items, characters, or locations declared in the manifest but never referenced in any section |

Structural checks (dangling references, orphaned sections, gate fulfilment) are already enforced by `AdventureValidationTest` and `ChapterValidationTest` and do not need repeating here. Run `mvn test` to confirm all automated checks pass before invoking this agent.

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
| Adventure Reviewer | `workflow/agents/adventure-reviewer-agent.md` |
| Adventure Author | `workflow/agents/adventure-author-agent.md` |
| Chapter Reviewer | `workflow/agents/chapter-reviewer-agent.md` |
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

## How to Invoke the Adventure Reviewer Agent

```
You are the Adventure Reviewer Agent for TAS Neo. Your role, responsibilities,
and boundaries are defined in workflow/agents/adventure-reviewer-agent.md — read it first.

Task: Review the scaffold for adventure <adventure-id> produced by the Architect Agent.

- Adventure file: adventures/<adventure-id>.json
- Manifest: adventures/<adventure-id>-manifest.json
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

## How to Invoke a Chapter Reviewer Agent

```
You are the Chapter Reviewer Agent for TAS Neo. Your role, responsibilities,
and boundaries are defined in workflow/agents/chapter-reviewer-agent.md — read it first.

Task: Review chapter <id> of adventure <adventure-id>, which was just authored.

- Adventure file: adventures/<adventure-id>.json
- Manifest: adventures/<adventure-id>-manifest.json
- Chapter id under review: <id>
- Section range: <from>–<to>
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
