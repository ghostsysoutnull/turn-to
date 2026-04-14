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

## Agent Workflow

This project uses a four-agent pipeline. See `workflow/WORKFLOW.md` for the full process.

| Agent | Role | Writes to |
|-------|------|-----------|
| Spec | Writes and validates specs | `docs/specs/` |
| Design | Writes technical design | `docs/design/` |
| Test | Writes failing tests from design strategy tables | `src/test/java/` |
| Code | Implements to make tests pass | `src/main/java/` |

Each agent stays strictly within its own output boundary. Violations of scope are not permitted.
