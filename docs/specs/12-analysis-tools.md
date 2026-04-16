# Spec 12 — Adventure Analysis Tools

## Purpose

The adventure analysis tools generate pre-computed report files from an adventure JSON source. Each tool answers a specific class of questions about an adventure's structure, content, or agent-authoring contracts. The reports are written to the `adventures/` directory and are the authoritative reference for any question they cover — agents and developers must read the relevant report rather than querying the raw JSON directly.

All tools are regenerated automatically after any change to an adventure JSON file. No report file is ever edited by hand.

---

## Tool Overview

| Tool | Output file | Primary question answered |
|------|-------------|--------------------------|
| `AdventureReportGenerator` | `<id>-report.txt` | Is the adventure structurally sound? |
| `AdventureStateReport` | `<id>-state.txt` | Where is each state variable set, read, checked, or removed? |
| `AdventureItemReport` | `<id>-items.txt` | Where is each item gained, lost, required, or forbidden? |
| `AdventureGateDigest` | `<id>-gates.txt` | What are the gate contracts for each chapter? |
| `AdventureSectionDigest` | `<id>-digest-<chapterId>.txt` | What happens in each section of a chapter? |

---

## AdventureReportGenerator

Produces a whole-adventure structural summary. Answers reachability, section type distribution, item reference completeness, chapter coverage, and runner simulation results.

### Report format

```
== Adventure Report: <id> ==
Generated: <date>  |  Title: <title>  |  Sections: N  |  Items: N

REACHABILITY
  All N sections reachable from start (§S) ✓
  — or —
  § unreachable: §A §B §C

SECTION TYPES
  NORMAL: N  VICTORY: N  INSTANT_DEATH: N

ITEMS  (N defined)
  All items referenced in sections ✓
  — or —
  Unreferenced items: ItemName ItemName

CHAPTERS  (N)
  ch1  [§1–§45]   entry:§1   exits:[§46 §47]  coverage:45/45 ✓  exits-reachable:✓
  ch2  [§46–§90]  entry:§46  exits:[§91]       coverage:45/45 ✓  exits-reachable:✓

ISSUES
  none ✓
  — or —
  ✗ N sections unreachable from start
  ✗ Chapter chN: N sections not reachable from entry
  ⚠ Unreferenced items: ItemName
```

The `ISSUES` section lists structural defects with `✗` (errors) or `⚠` (warnings). An adventure is structurally sound when `ISSUES` contains only `none ✓`.

---

## AdventureStateReport

Produces an index of every state variable in the adventure, showing every section and hook where each variable is set, read (via `state.get` or `state.has`), checked (via a `STATE_EQUALS` or `STATE_NOT_EQUALS` condition on a choice), or removed.

**Use this tool when:** an agent needs to trace a state variable across chapters — e.g. to verify that a gate-out contract's assumed state is always set before it is checked, or to find all sections that depend on a specific flag.

### Report format

```
== State Variable Report: <id> ==
Generated: <date>  |  Variables: N

  variableName                    set:§N(hook)  read:§N(hook)  check:§N(condition)  remove:§N(hook)
```

Each row is one state variable. Columns:

| Column | Meaning |
|--------|---------|
| `set` | Sections (and script hooks) where `state.set("key", ...)` is called |
| `read` | Sections where `state.get("key")` or `state.has("key")` is called |
| `check` | Sections where a choice condition tests this variable (`STATE_EQUALS` or `STATE_NOT_EQUALS`) |
| `remove` | Sections where `state.remove("key")` is called |

A `—` in any column means the operation does not occur anywhere in the adventure.

---

## AdventureItemReport

Produces a full item lifecycle index showing every section where each adventure-defined item is gained, lost, required (via `HAS_ITEM` condition), or forbidden (via `LACKS_ITEM` condition).

**Use this tool when:** an agent needs to trace an item's full journey through the adventure — e.g. to verify that a gate-out guaranteed item is always gained before the chapter exit, or to find all the places an item can be lost.

### Report format

```
== Item Lifecycle Report: <id> ==
Generated: <date>  |  Items: N

  ItemName                      GAIN:§A,§B  LOSS:§C  REQUIRED:§D  FORBIDDEN:§E
```

Each row is one item from the adventure's item manifest. Columns:

| Column | Meaning |
|--------|---------|
| `GAIN` | Sections containing an `ItemEvent` with action `GAIN` for this item |
| `LOSS` | Sections containing an `ItemEvent` with action `LOSS` for this item |
| `REQUIRED` | Sections where a choice has a `HAS_ITEM` condition on this item |
| `FORBIDDEN` | Sections where a choice has a `LACKS_ITEM` condition on this item |

A `—` in any column means the operation does not occur. Items with no entries in any column are flagged as `⚠ unreferenced`.

---

## AdventureGateDigest

Produces a compact view of all chapter gate contracts in a single file. Each chapter shows its entry gate (what state is assumed when the player enters) and its exit gates (what state is guaranteed, possible, or forbidden when the player leaves).

**Use this tool when:** an agent needs to verify chain integrity across chapters — e.g. that a chapter's gate-in assumptions are satisfied by the previous chapter's gate-out guarantees, or to understand what items/state any chapter can expect when it begins.

### Report format

```
== Gate Contract Digest: <id> ==
Generated: <date>  |  Chapters: N

CH1  [§1–§45]
  IN   (adventure start — no entry contract)

  OUT  §46 (Path of Fire)
       guaranteed:  Iron Key
       possible:    Torch
       forbidden:   Guard's Pass
       state:       suspicion=false

CH2  [§46–§90]
  IN   §46
       required:    Iron Key
       possible:    Torch
       state:       suspicion=false

  OUT  §91
       ...
```

Fields under `IN` (entry gate):

| Field | Meaning |
|-------|---------|
| `required` | Items the player must carry to have reached this entry section |
| `possible` | Items the player may carry at this point |
| `state` | State variable values assumed to hold at entry |

Fields under `OUT` (exit gate):

| Field | Meaning |
|-------|---------|
| `guaranteed` | Items the player is guaranteed to carry when exiting this section |
| `possible` | Items the player may carry |
| `forbidden` | Items the player cannot carry |
| `state` | State variable values known to hold at the exit |

A chapter with no exit gates is the final chapter. A chapter's first entry gate labelled "adventure start" has no predecessor contract.

---

## AdventureSectionDigest

Produces a compact per-section digest for one chapter. Each section is rendered in 3–5 lines showing its type, navigation targets, a narrative excerpt, events, gated choices, and script hook names. A 45-section chapter is compressed from ~800 lines of raw JSON to ~180 lines while preserving all mechanically relevant information.

**Use this tool when:** an agent needs to review the content of a specific chapter — e.g. the Chapter Reviewer Agent performing a narrative pass, or an author verifying that events and conditions are correctly placed.

### Invocation

```
mvn exec:java -Dexec.mainClass=com.tas.neo.analysis.AdventureSectionDigest \
  -Dexec.args="adventures/<id>.json <chapterId>"
```

### Report format

```
== Section Digest: <id> / ch2 ==
Generated: <date>  |  Range: §46–§90  |  Entry: §46

[§46] NORMAL          →§47,§52
  You descend the crumbling staircase into the vaults below.
  events: +Iron Key  STAMINA-2
  conds:  [HAS:Torch]→§48:"Light the way"
  hooks:  onEnter

[§47] NORMAL          →§50
  The corridor splits ahead.

[§90] INSTANT_DEATH
  The ceiling collapses on top of you.
```

Each section block contains:

| Line | Content |
|------|---------|
| Header | `[§N] TYPE` followed by `→§A,§B` navigation targets (choices only, not events) |
| Narrative | First sentence of the section's narrative text, truncated at 140 characters |
| `events` | Each event in terse notation (see below); omitted if section has no events |
| `conds` | Gated choices: `[condition]→§target:"choice text"`; omitted if none |
| `hooks` | Script hook names present; omitted if none |

### Event notation

| Notation | Meaning |
|----------|---------|
| `+ItemName` | Item gained |
| `-ItemName` | Item lost |
| `STAMINA-3` | Stat decreased |
| `SKILL+1` | Stat increased |
| `GOLD+5` | Gold change |
| `→§N` | Forced navigation (NavigateEvent) |
| `LUCK→§N/§M` | Luck test: success → §N, fail → §M |
| `SKILL_TEST→§N/§M` | Skill test: success → §N, fail → §M |
| `COMBAT→§N/§M` | Combat: victory → §N, defeat → §M |

### Condition notation

| Notation | Meaning |
|----------|---------|
| `HAS:ItemName` | Player must carry the item |
| `LACKS:ItemName` | Player must not carry the item |
| `SKILL>=N` | Player's SKILL must be at least N |
| `GOLD>=N` | Player's gold must be at least N |
| `STATE:key=value` | State variable must equal value |
| `STATE:key≠value` | State variable must not equal value |

---

## When to use which tool

| Question | Tool |
|----------|------|
| Is the adventure structurally sound? Are all sections reachable? Do all chapters have full coverage? | `<id>-report.txt` |
| How often does the runner reach each chapter or ending? | `<id>-report.txt` |
| Where is state variable X set, read, checked, or removed? | `<id>-state.txt` |
| Is a gate-out's assumed state always set before the exit? | `<id>-state.txt` |
| Where is item X gained, lost, required, or forbidden? | `<id>-items.txt` |
| Is a gate-out guaranteed item always gainable before the exit? | `<id>-items.txt` |
| What does the previous chapter guarantee or forbid? | `<id>-gates.txt` |
| What can the next chapter assume at its entry? | `<id>-gates.txt` |
| What does each section in chapter N do? | `<id>-digest-<chapterId>.txt` |
| Is the narrative of a section consistent with its mechanics? | `<id>-digest-<chapterId>.txt` |
