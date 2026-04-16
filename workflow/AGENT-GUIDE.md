# Agent Guide

Practical guidance for invoking the TAS Neo agents. This document does not repeat what the pipeline or agent role files already say — read those first. This document answers: which agent for what, how to brief them well, and what to do when things go wrong.

---

## Quick Reference

| I want to… | Start here |
|------------|-----------|
| Add a new engine feature | Spec Agent |
| Change existing engine behaviour | Spec Agent (update the spec first) |
| Design a component after a spec is written | Design Agent |
| Write tests after a design is written | Test Agent |
| Implement code after tests are written | Code Agent |
| Create a new adventure from scratch | Adventure Architect Agent |
| Write sections for a specific adventure chapter | Adventure Author Agent |
| Write cells for a specific adventure grid | Grid Agent |

For the development pipeline see `workflow/WORKFLOW.md`.
For the adventure authoring pipeline see `workflow/ADVENTURE-AUTHORING-PIPELINE.md`.

---

## Scenarios

### 1. Adding a new engine feature

Run the full pipeline in order. Each stage is a checkpoint — review the output before proceeding.

```
Spec Agent → Design Agent → Test Agent → Code Agent
```

**What to hand between stages:**

| Handoff | What the next agent needs |
|---------|--------------------------|
| Spec → Design | The spec doc(s) written or updated. Tell the Design Agent which specs changed. |
| Design → Test | The design doc(s) written or updated, especially the test strategy tables. Tell the Test Agent which tables to cover. |
| Test → Code | The failing test files. Tell the Code Agent which tests to make pass. |

---

### 2. Changing existing engine behaviour

A behaviour change touches the spec first. Determine the blast radius before invoking any agent.

**Decision tree:**

```
Does the change affect what the system does (rules, behaviour)?
  Yes → Spec Agent updates the spec first.
        ↓
  Does the spec change affect component interfaces or data formats?
    Yes → Design Agent updates the design doc.
          ↓
    Does the interface change break existing tests?
      Yes → Test Agent updates tests to match the new interface.
            ↓
            Code Agent implements.
      No  → Code Agent implements directly against existing tests.
    No  → Code Agent implements directly.
  No  → (pure refactor, no spec change)
        Code Agent only, no upstream agents needed.
```

When in doubt, start with the Spec Agent and let the blocker routing in each stage surface what else needs updating.

---

### 3. Fixing a bug

First determine whether the bug is a spec ambiguity or an implementation error.

- **Implementation error** (code does not match the spec): Code Agent only. Point it at the failing test or the mismatched behaviour.
- **Spec gap** (behaviour is undefined for this case): Spec Agent first to define the correct behaviour, then ripple down as needed.
- **Design gap** (interface makes correct implementation impossible): Design Agent first, then Test Agent (if tests need updating), then Code Agent.

---

### 4. Creating a new adventure

```
User writes design brief → Adventure Architect Agent → User reviews scaffold
  → Adventure Author Agents (one per chapter, parallel where safe)
  → Manifest merge after each chapter
  → (optional) Consistency check
  → Final assembly
```

The Architect produces everything chapter authors depend on. Do not invoke chapter authors until the scaffold has been reviewed. A gate contract change after authoring has started requires rewriting the affected chapters.

See `workflow/ADVENTURE-AUTHORING-PIPELINE.md` for the full process.

---

### 5. Adding a spec to an adventure that is already partially authored

If chapters are already written and you need to change a gate contract:

1. Identify which chapters are affected (the sending chapter, the receiving chapter).
2. Re-invoke those chapter authors with the updated gate contract.
3. Update the manifest if new items or characters were introduced.

Do not patch authored sections manually — re-run the affected chapter author with the full updated brief and gate contracts.

---

## Invocation Quality

The quality of the agent's output is directly proportional to the quality of the brief. Templates exist in the pipeline docs — this section shows what separates a good brief from a poor one.

### Poor brief

```
You are the Spec Agent. Add a spec for saving and loading game state.
```

Problems:
- No context about what decisions have already been made
- No boundaries (does "saving" mean mid-section saves? Between sections only? Cloud sync?)
- No indication of what related specs exist that need to stay consistent

### Good brief

```
You are the Spec Agent for TAS Neo. Read workflow/agents/spec-agent.md first.

Task: Write a spec for save and load game state.

Decisions already made:
- Save is triggered by the player explicitly, not automatically.
- Only one save slot per adventure run.
- Save captures: current section number, player stats, inventory, and adventure state variables.
- Party member state is included in the save.

Out of scope for this spec:
- Cloud saves, multiple slots, save file format details (those belong in design).

Related specs to stay consistent with:
- docs/specs/01-game-mechanics.md (player stats model)
- docs/specs/07-party-members.md (party member state)
- docs/specs/05-scripting.md (state variables)
```

The good brief answers three questions before the agent has to ask:
1. What decisions are already made (don't re-debate these)
2. What is explicitly out of scope (don't expand beyond this)
3. What existing specs must remain consistent

---

## Handling Blockers

Every agent ends its run with a structured summary that may include blockers. A blocker means the agent completed what it legitimately could and flagged something it cannot resolve alone.

### Routing rules

| Blocker source | Routes to | What to do |
|---------------|-----------|-----------|
| Design Agent → Spec Agent | Spec gap or ambiguity | Invoke Spec Agent with the specific question. Do not proceed to Test Agent until resolved. |
| Test Agent → Design Agent | Missing test strategy, ambiguous signature | Invoke Design Agent to fill the gap. Re-run the Test Agent for the affected rows after. |
| Test Agent → Spec Agent | Spec ambiguity making a test meaningless | Invoke Spec Agent. May require Design Agent follow-up. |
| Code Agent → Design Agent | Interface unclear or insufficient | Invoke Design Agent. If tests need updating, Test Agent follows. |
| Code Agent → Spec Agent | Rule undefined for an edge case | Invoke Spec Agent, then ripple down as needed. |
| Any agent → User | Decision required | You make the call, then re-invoke the blocked agent with the decision included in the brief. |

### What not to do

- Do not ask the Code Agent to "work around" a design gap. It will invent a solution that conflicts with what the Design Agent would have produced.
- Do not ask the Test Agent to weaken a test to avoid a blocker. The test is the contract — if it cannot be satisfied, the design is wrong.
- Do not ask the Spec Agent to retroactively justify code that was already written.

---

## Parallelism

### Development pipeline

The four development agents (Spec, Design, Test, Code) are sequential. Each stage's output is the next stage's input. They cannot be parallelized.

The only exception: if two completely independent features are being developed simultaneously (separate specs, separate design docs, separate tests, no shared interfaces), each feature can run its own pipeline in parallel. This is rare and requires the Design Agent to verify that the two feature designs do not conflict.

### Adventure authoring

Adventure Author Agents can be parallelized. See `workflow/ADVENTURE-AUTHORING-PIPELINE.md` for the dependency rules — specifically which chapters can start immediately and which must wait for prior chapters' manifest additions.

The Adventure Architect Agent always runs alone, before any chapter authors.

---

## Common Mistakes

| Mistake | Consequence | Correct approach |
|---------|-------------|-----------------|
| Invoking the Design Agent before the Spec Agent finishes | Design is built on an incomplete spec; likely needs a full redo | Always complete and review the spec before invoking Design |
| Invoking the Code Agent to "just fix a small thing" without a test | No test contract; the fix may break something else and there is no safety net | Write the test first, even for small fixes |
| Asking the Code Agent to write tests | Violates the TDD contract; the agent's own tests have no independent authority | Use the Test Agent |
| Giving a chapter author incomplete gate contracts | Chapter will invent its own cross-chapter assumptions, breaking continuity | Architect produces complete gates before any chapter author starts |
| Updating a spec without re-running downstream agents | Design, tests, and code may now contradict the spec | Assess blast radius and re-run affected stages |
| Making code or test changes directly in conversation without invoking agents | TDD contract broken; tests written after the code have no independent authority; design doc test strategy rows may be added retroactively | Invoke the correct agent explicitly — even for small gaps; the pipeline cost is lower than the cost of undisciplined drift |
| Starting work without reading LESSONS.md and relevant design docs | Repeats mistakes already paid for; violates established patterns silently | Always follow the session start protocol in CLAUDE.md before any work begins |
