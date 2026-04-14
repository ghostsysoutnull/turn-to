# Agent Workflow

## Pipeline

```
User
 │
 ▼
Spec Agent          reads: docs/specs/*, user requirements
 │                  writes: docs/specs/
 ▼
Design Agent        reads: docs/specs/*, docs/design/*
 │                  writes: docs/design/
 ▼
Test Agent          reads: docs/specs/*, docs/design/*, src/main/java/*
 │                  writes: src/test/java/
 ▼
Code Agent          reads: docs/specs/*, docs/design/*, src/test/java/*
 │                  writes: src/main/java/
 ▼
User review
```

Each stage is a deliberate checkpoint. The user reviews the output of each agent before proceeding to the next.

---

## How to Invoke an Agent

Each agent is invoked by spawning a subagent with:
1. The agent's role definition from `workflow/agents/<agent>.md`
2. A clear task description
3. Any relevant context from previous stages

Example invocation prompt:

```
You are the Spec Agent for this project. Your role, responsibilities, and
boundaries are defined in workflow/agents/spec-agent.md — read it first.

Task: [describe what needs to be specced]

Relevant context: [any decisions already made, links to prior discussion]
```

---

## TDD Sequencing

Tests are written **before** production code.

1. **Design agent** produces or updates the test strategy table in the relevant design doc.
2. **Test agent** reads the test strategy table and writes failing tests. No production code yet.
3. **Code agent** reads the failing tests and implements to make them pass.

The test agent must not write production code. The code agent must not write tests. If the code agent cannot make a test pass without changing the test, it raises a blocker to the design agent — it does not modify the test.

---

## Feedback and Blockers

Agents do not fix issues outside their scope. When an agent finds a problem that belongs to a prior stage, it:

1. Completes everything it legitimately can.
2. Returns a structured summary that includes the blocker.

### Blocker routing

| Found by | Blocker type | Routes to |
|----------|-------------|-----------|
| Design agent | Spec gap or ambiguity | Spec agent |
| Test agent | Design gap or missing test strategy | Design agent |
| Test agent | Spec ambiguity | Spec agent |
| Code agent | Design gap (interface unclear) | Design agent |
| Code agent | Spec ambiguity | Spec agent |

The user receives the blocker report and decides whether to invoke the appropriate prior-stage agent or resolve it directly.

---

## Return Value Format

Every agent ends its run with a structured summary in this format:

```
## Done
[Brief description of what was completed]

## Files Changed
- path/to/file — what changed and why

## Blockers
### → Spec Agent
- [issue requiring spec clarification or update]

### → Design Agent
- [issue requiring design clarification or update]

### → User
- [decision or ambiguity requiring human input]
```

If there are no blockers in a category, omit that section.

---

## Consistency Checks

### Spec agent (runs on every invocation)
- No duplicate definitions across spec files
- Cross-references point to documents that exist
- Naming is consistent across all spec files
- No implementation detail has crept into specs

### Design agent (runs on every invocation)
- All concepts introduced in specs have a corresponding design element
- No method bodies or pseudocode in design docs
- Interface signatures align with the architecture doc
- Test strategy tables are present for every component

### Test agent (runs on every invocation)
- Every row in the relevant design doc test strategy table has a corresponding test
- Test doubles used match those defined in `design/06-testability.md`

### Code agent (runs on every invocation)
- All failing tests pass after implementation
- No test files are modified
- Package structure matches `design/01-architecture.md`
- No direct use of `System.in` / `System.out` outside terminal adapter classes

---

## Scope Boundaries (summary)

| Agent | May write | Must not write |
|-------|-----------|----------------|
| Spec | `docs/specs/` | design docs, source, tests |
| Design | `docs/design/` | specs, source, tests |
| Test | `src/test/java/` | specs, design docs, production source |
| Code | `src/main/java/` | specs, design docs, test source |
