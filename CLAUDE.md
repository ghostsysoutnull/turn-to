# TAS Neo — Project Context

This file is auto-loaded by every agent and session. It defines the project, conventions, and rules that all agents must follow regardless of their specific role.

---

## What This Project Is

**TAS Neo** is a text-based terminal adventure game engine inspired by the Fighting Fantasy gamebook series. It is written in Java and is designed to be data-driven: adventures are authored as JSON files, not code. The engine is specification-driven — specs are written before design, design before code, tests before implementation.

---

## Tech Stack

| Concern | Choice |
|---------|--------|
| Language | Java (latest LTS) |
| Build | Maven |
| Testing | JUnit 5 + AssertJ |
| Scripting | Lua via LuaJ |
| Adventure format | JSON |

---

## Repository Layout

```
docs/specs/              Functional specifications — behaviour and rules, no implementation
docs/design/             Technical design — interfaces, signatures, data formats, rationale
docs/LESSONS.md          Accumulated operational gotchas — read before every session
workflow/                Agent role definitions and pipeline documentation
src/main/java/           Production source code
src/test/java/           Test source code (test doubles live here too)
src/test/resources/      Fixture files for loader tests
adventures/              Adventure JSON files
BACKLOG.md               Prioritised work items — bugs, features, content gaps
REFACTORING-BACKLOG.md   Structural improvements deferred for dedicated effort
```

---

## Architectural Principles

These are non-negotiable. Every agent must respect them.

1. **Testability first** — every component is injectable and testable in isolation. No static state. No direct use of `System.in` / `System.out` outside terminal adapter classes.
2. **Dependency inversion** — the engine depends on interfaces, never on concrete I/O or infrastructure.
3. **Rich domain model** — rules and invariants live in the domain layer.
4. **Lifecycle-driven scripting** — the engine owns a fixed set of hook points; scripts are passengers.
5. **Specs before design, design before code, tests before implementation.**

---

## Testability Covenant

**Every feature — UI, gameplay, scripting, grid navigation, combat, loading — must be fully exercisable by an agent running the test suite, with no human present and no terminal interaction.**

These rules are absolute. Violations are bugs, not style preferences.

### Code rules

- `TerminalInput` and `TerminalOutput` **never** appear in test code. Tests always use `ScriptedInput` and `RecordingOutput`.
- No test may use `System.in`, `System.out`, or `System.err` directly.
- No test may read from or write to the filesystem except: `JsonAdventureLoaderTest` (fixture files under `src/test/resources/`), `AdventureValidationTest`, `ChapterValidationTest`, and acceptance tests in `src/test/java/com/tas/neo/acceptance/` — all of which load production adventures from `adventures/`.
- No test may have a non-deterministic outcome. All dice rolls use `FixedDice` or `SequenceDice`.
- No test may `Thread.sleep`, use wall-clock time, or depend on execution order across test classes.

### Output rules

- Tests must produce **no output on success**. A passing test suite outputs only the final summary line.
- Failure messages must be self-diagnosing: an agent reading the failure message must be able to identify the broken invariant without running the code or reading a stack trace.
- Do not use `System.out.println` or any logging framework that writes to stdout in test code.
- AssertJ failure messages satisfy this requirement when assertions are written with `.as("description")` on non-obvious checks.

### Design rules

- Every component that interacts with the outside world (terminal, filesystem, random, time) must be behind an interface with a test double defined in `docs/design/06-testability.md`.
- `GameState` must expose enough read methods that any game state assertion can be made without accessing private fields.
- New features that introduce agent-unverifiable behaviour (e.g. direct console writes, blocking reads) are rejected until a test double and test strategy exist for them.

---

## Documentation Rules

### Spec documents (`docs/specs/`)
- Describe **what** the system does and **what rules** it follows.
- No implementation language, no class names, no method signatures.
- Written in prose and tables.

### Design documents (`docs/design/`)
- Describe **component responsibilities**, **interface and record declarations** (fields + method signatures only), **data format examples**, and **design rationale**.
- **No method bodies. No pseudocode. No private implementation details.**
- Test strategy tables belong here.

### Code (`src/`)
- Implementation detail lives here, not in docs.
- JavaDoc explains intent where the code alone is not sufficient.

---

## Package Root

`com.tas.neo`

---

## Naming Conventions

- Classes: `PascalCase`
- Methods and fields: `camelCase`
- Constants and enums: `UPPER_SNAKE_CASE`
- Test classes: `[SubjectClass]Test`
- Test double classes: `[Role][Interface]` (e.g. `FixedDice`, `RecordingOutput`, `ScriptedInput`)

---

## Git Conventions

- Commit messages: imperative mood, present tense, describe the *why* not the *what*
- Always include `Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>` when AI-assisted
- Do not commit generated files, IDE files, or build artefacts

---

## Adventure Analysis Tools

Pre-computed report files in `adventures/` answer most questions about an adventure without opening the raw JSON. **Do not write ad-hoc scripts to query adventure JSON when a report file answers the question.**

| Question | Use |
|----------|-----|
| Is the adventure structurally sound? Reachability, section types, item references, chapter coverage. | `adventures/<id>-report.txt` |
| What sections are in chapter N? Narrative, events, choices, gated conditions. | `adventures/<id>-digest-<chapterId>.txt` |
| Where is state variable X set, read, checked, or removed? | `adventures/<id>-state.txt` |
| Where is item X gained, lost, required, or forbidden? | `adventures/<id>-items.txt` |
| What are the gate contracts for each chapter? Guaranteed, possible, forbidden, assumed state. | `adventures/<id>-gates.txt` |
| Full JSON of a specific section (for reading before an Edit-tool change). | `AdventureSectionInspector <id>.json N` |
| What sections link TO section N? (before repurposing or removing it.) | `AdventureSectionInspector <id>.json --refs N` |
| All sections containing event type X (optionally filtered by item name). | `AdventureSectionInspector <id>.json --has-event ITEM_GAIN [--item Name]` |
| All sections with a choice condition of type X. | `AdventureSectionInspector <id>.json --has-condition GoldCondition` |
| NORMAL sections with no successors (dead ends). | `AdventureSectionInspector <id>.json --dead-ends` |
| All sections that set, read, or check a specific state variable. | `AdventureSectionInspector <id>.json --state varName` |
| Raw JSON gate contracts for a chapter (for preparing an Edit-tool change). | `AdventureSectionInspector <id>.json --gate ch3 [in\|out]` |

**Prefer tools over scripts.** Reach for a tool before reaching for Python. Use this hierarchy:

1. **Report files** — whole-adventure structural questions (reachability, items, state, gates)
2. **`AdventureSectionInspector`** — targeted queries (specific sections, inbound refs, event/condition search, state variable focus, gate JSON)
3. **Python** — only when neither above covers the situation (e.g. multi-condition cross-section queries the inspector doesn't support); write it as a named script, not an inline one-liner

Regenerate all reports after any change to an adventure JSON file:

```
mvn exec:java -Dexec.mainClass=com.tas.neo.analysis.AdventureReportGenerator  -Dexec.args="adventures/<id>.json"
mvn exec:java -Dexec.mainClass=com.tas.neo.analysis.AdventureStateReport      -Dexec.args="adventures/<id>.json"
mvn exec:java -Dexec.mainClass=com.tas.neo.analysis.AdventureItemReport        -Dexec.args="adventures/<id>.json"
mvn exec:java -Dexec.mainClass=com.tas.neo.analysis.AdventureGateDigest        -Dexec.args="adventures/<id>.json"
mvn exec:java -Dexec.mainClass=com.tas.neo.analysis.AdventureSectionDigest     -Dexec.args="adventures/<id>.json ch1"
# repeat AdventureSectionDigest for each chapter
```

**Authoritative sources:**

- If a report file disagrees with the adventure JSON, the JSON wins. Fix the JSON (or the report generator), then regenerate.
- Never edit a generated report file by hand. All report files must be reproducible from their source by running the generator above.

---

## Session Start Protocol

Before any code or documentation work begins — in every session, every agent invocation:

1. Read `docs/LESSONS.md` — accumulated operational gotchas. Skipping this repeats mistakes already paid for.
2. Read `BACKLOG.md` — understand what is in progress, what is next, and what is deliberately deferred.
3. Read `REFACTORING-BACKLOG.md` — know which structural debts exist so they are not accidentally fixed inline during unrelated work.
4. Read the relevant `docs/design/` files for the area being changed.
5. Read the relevant `docs/specs/` files if the change touches behaviour.

This applies to all agents and to conversational sessions alike.

### Session opening summary (conversational sessions only)

After completing the reads above, produce a session opening summary covering:

1. **Next steps** — the top open items from `BACKLOG.md` in priority order, one line each.
2. **Coding standards reminder** — a brief recap of the non-negotiable rules active in this project:
   - Pipeline discipline: Spec → Design → Test → Code; no inline code or test changes
   - Refactoring rule: structural smells go to `REFACTORING-BACKLOG.md`, never fixed inline; a refactoring never changes observable behaviour
   - OO rules: objects own their decisions; construction produces valid objects; encapsulate what varies
   - Testability: every change must be fully exercisable by the test suite; `mvn -q test` must pass before and after
   - Adventure JSON changes: regenerate all reports after every edit

This summary keeps both the user and the assistant aligned at the start of every session without needing to ask.

---

## Agent Workflow

This project uses a four-agent pipeline. See `workflow/WORKFLOW.md` for the full process.

| Agent | Role | Writes to |
|-------|------|-----------|
| Spec | Writes and validates specs | `docs/specs/` |
| Design | Writes technical design | `docs/design/` |
| Test | Writes failing tests from design strategy tables | `src/test/java/` |
| Code | Implements to make tests pass | `src/main/java/` |

Each agent stays strictly within its own output boundary. Violations of scope are not permitted.

### Refactoring detected during unrelated work

When a structural smell is identified while working on something else, do not refactor inline. Complete the original task, append an entry to `REFACTORING-BACKLOG.md`, and report it explicitly. Medium and broad refactors require the user's explicit approval in a dedicated effort. See `docs/design/13-oo-design-guidelines.md § Raising a Refactoring Detected During Unrelated Work` for the full protocol.

### Problem-scope discipline

When the user raises a concern about the workflow, process, or system quality, map the full problem space before proposing a fix. A concern about one gap usually implies related gaps elsewhere — in other agents, other layers, other lifecycle phases. Answering only the narrow slice named is not enough.

Before proposing changes, ask: across all agents, all testing layers, all lifecycle phases — where else could this concern manifest? Present that full picture, then propose changes that address the whole thing.

Answering the narrow version and waiting for the user to direct the next step is a failure mode. The user should not need to ask the same question five times from different angles to get complete coverage.

### Pipeline discipline in conversational sessions

The pipeline applies even when working conversationally, not through formal agent invocations:

- Changes to `src/main/java/` → Code Agent
- Changes to `src/test/java/` → Test Agent
- Changes to `docs/design/` → Design Agent
- Changes to `docs/specs/` → Spec Agent

Inline code or test changes made directly in conversation violate the TDD contract: tests written after the fact have no independent authority, and design doc test strategy tables may be bypassed or added retroactively. When in doubt, invoke the agent explicitly rather than doing the work inline.
