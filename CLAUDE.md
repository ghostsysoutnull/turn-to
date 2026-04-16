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
docs/specs/         Functional specifications — behaviour and rules, no implementation
docs/design/        Technical design — interfaces, signatures, data formats, rationale
workflow/           Agent role definitions and pipeline documentation
src/main/java/      Production source code
src/test/java/      Test source code (test doubles live here too)
src/test/resources/ Fixture files for loader tests
adventures/         Adventure JSON files
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
- No test may read from or write to the filesystem except `JsonAdventureLoaderTest`, which uses fixture files under `src/test/resources/`.
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

## Agent Workflow

This project uses a four-agent pipeline. See `workflow/WORKFLOW.md` for the full process.

| Agent | Role | Writes to |
|-------|------|-----------|
| Spec | Writes and validates specs | `docs/specs/` |
| Design | Writes technical design | `docs/design/` |
| Test | Writes failing tests from design strategy tables | `src/test/java/` |
| Code | Implements to make tests pass | `src/main/java/` |

Each agent stays strictly within its own output boundary. Violations of scope are not permitted.
