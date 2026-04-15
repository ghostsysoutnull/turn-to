# Design: OO Design Guidelines

This document is a decision framework for the Code Agent and reviewers. It answers three questions:

1. **Is this code ready to refactor?** — criteria before touching structure
2. **What smell maps to what pattern?** — specific to this codebase, with thresholds
3. **How do I refactor safely?** — TDD sequence for structural changes

It is not a patterns catalog. Generic OO theory is out of scope. Every example here is drawn from this codebase.

---

## Rule Zero: Default to Not Refactoring

Refactoring has a cost: it generates test churn, risks introducing bugs, and consumes agent context. The default answer to "should I refactor this?" is **no**.

Apply a refactoring only when:
- A specific smell listed below is present **and** its threshold is met
- The refactoring has a clear, failing test that proves it was needed
- The change is fully covered by existing or new tests before any structural move begins

If there is no failing test that motivates the refactoring, it is speculative. Do not proceed.

---

## Patterns Already in Use

These patterns are established in this codebase. Do not re-derive them from scratch; follow the existing conventions.

| Pattern | Where used | Notes |
|---------|-----------|-------|
| Sealed interface + pattern-matching switch | `SectionEvent`, `Condition`, `ChoiceTarget` | Java 21 `sealed`/`permits`. New subtypes require adding a `permits` clause and a new switch arm. |
| Strategy | `CombatSystem`, `Dice`, `ScriptEngine`, `AdventureLoader` | New pluggable behaviour = new interface + test double, not a new switch arm. |
| Null Object | `NoOpGameLogger`, `NoOpScriptEngine` | Test doubles that do nothing. Every interface that has side effects (I/O, logging) must have one. |
| Parse → validate → build | `JsonAdventureLoader` | Raw JSON → DTO → validation → domain object. Never build domain objects directly from raw JSON. |
| Hook dispatcher | `HookDispatcher` | The engine owns hook points; scripts and event handlers are passengers. Do not add new hook points without a spec change. |

---

## Smell → Pattern Map

### Smell 1: Switch on a type string or enum that will grow

**Signal**: a `switch` dispatches on a `String` or `enum` type field, and a new value requires adding a case arm *and* adding business logic in multiple places.

**Current examples**:
- `parseSectionEventDto()` in `JsonAdventureLoader` — 8 cases today
- `parseCondition()` in `JsonAdventureLoader` — 10 cases today
- `processEvent()` in `HookDispatcher` — 7 cases today

**Threshold**: the smell does not justify refactoring until there are **≥ 12 cases** *or* adding a new case requires touching **3+ methods** that each switch on the same type.

**Pattern to apply**: for the loader switches — a `Map<String, EventParser>` registry where each parser is a single-method interface. For `processEvent` — already a sealed-interface switch, which is the correct form; adding a method to the `SectionEvent` sealed hierarchy is the right extension point, not a registry.

**Do not apply**: a Chain-of-Responsibility, a Visitor, or an abstract factory unless the threshold above is met. At current scale these add indirection with no concrete benefit.

---

### Smell 2: `instanceof` checks on a sealed hierarchy outside a switch

**Signal**: code does `if (event instanceof LuckTestEvent e) { ... } else if (event instanceof SkillTestEvent e) { ... }` outside a `switch` expression.

**Current example**: none today — all dispatch uses `switch`. This is a preemptive guideline.

**Threshold**: any occurrence. A scattered `instanceof` chain on a sealed type is a sign the behaviour belongs on the type itself.

**Pattern to apply**: add a method to the sealed interface and implement it in each permitted record. Example — `Condition` currently has no `evaluate()` method. If callers accumulate `instanceof` chains to evaluate conditions, the fix is:

```java
public sealed interface Condition permits ... {
    boolean evaluate(Player player, Inventory inventory, AdventureScriptState state);
}
```

Each permitted record implements `evaluate()`. The caller becomes a single method call. The TDD trigger is a test for a new condition type that makes adding `evaluate()` the simplest path.

---

### Smell 3: Constructor with ≥ 6 parameters, most of which are always the same

**Signal**: a constructor or method takes many parameters and callers always pass the same values for most of them. Callers have to remember which argument is which.

**Current example**: `HookDispatcher` constructor — 8 parameters. Already at the boundary.

**Threshold**: 8+ constructor parameters with more than 2 callsites that each repeat the same infrastructure arguments.

**Pattern to apply**: Builder or factory method for the common case. The test double constructors in `HookDispatcherProcessEventTest` already create convenience factory methods (`dispatcherWithDice`, `dispatcherWithCombatRegistry`) — that is the right direction. If the production constructor grows further, extract a `HookDispatcherFactory` or a builder that wires the common defaults.

**Do not apply**: a builder for a constructor that is only called once (in `Main`), or for test-only constructors.

---

### Smell 4: Parallel conditionals — same `if/else` shape in multiple methods

**Signal**: two or more methods contain identical conditional structure (same branches, same discriminating field), duplicating the decision logic.

**Current example**: `failSectionFrom()` was extracted from LUCK_TEST and SKILL_TEST precisely because the identical alias-resolution logic appeared twice. The `itemNameFrom()` helper was the prior instance of the same smell.

**Threshold**: the same conditional shape in **2+ places**, where changing the condition (e.g. adding a third alias) would require touching all of them.

**Pattern to apply**: extract a helper method (as already done). Only escalate to a Strategy or a dedicated class if the logic is complex enough to warrant its own test class.

---

### Smell 5: A class knows too much about another class's internals

**Signal**: class A accesses multiple fields or methods of class B in sequence to compute something that belongs in B. Often recognisable as a chain of `.get()` calls, or as a method that could be replaced by a single method on B.

**Current example**: callers that call `state.player().getInventory().has(itemName)` — three hops. If this pattern appears in 3+ places, it's a signal to add `state.playerHasItem(itemName)` to `GameState`, which already has a `Scope constraint` note in its design doc.

**Threshold**: 3+ call sites doing the same multi-hop chain.

**Pattern to apply**: add a focused method to the class that owns the data. This is the Law of Demeter applied as a practical rule, not a theoretical one. Do not move the logic to a utility class.

---

## TDD Sequence for Structural Refactoring

Structural refactors are higher-risk than feature additions because they move code that already works. The sequence below makes the move provably safe.

### Step 1: Confirm coverage before touching structure

Run the test suite. Every code path you intend to move must be covered by at least one test before you move it. If coverage is missing, write tests first against the *existing* structure. Do not move uncovered code.

```
mvn test
```

All green. If not, stop — fix the failing tests first.

### Step 2: Write a test that proves the smell exists

For type-switch smells: write a test for the new case that requires adding a case arm in 3+ places — this test fails. That failure is the evidence the refactoring is justified.

For duplication smells: write a test that would break if one of the duplicate sites was updated but not the other — this test may or may not fail yet, but it documents the coupling.

### Step 3: Make the smallest structural change that makes the new test pass

Do not refactor the entire class. Move the minimum amount of code. The new test should turn green; all existing tests must remain green.

```
mvn test
```

If existing tests fail, revert and diagnose. Do not proceed with a partial refactor.

### Step 4: Refactor the duplication away

Now that the new test passes against the new structure, remove the old duplicate sites. After each removal, run the suite.

```
mvn test   # after each removal
```

### Step 5: Delete dead code

Remove any code that is no longer reachable. Do not leave `// removed` comments or backward-compatible shims. If it compiles and tests pass without it, delete it.

---

## What NOT to Refactor

These are patterns that look like smells but are not, in this codebase:

| What you might want to do | Why not to |
|--------------------------|------------|
| Extract constants for JSON field name strings | Each field name appears once; a constant adds a declaration with no meaningful reuse. The existing `failSectionFrom` / `itemNameFrom` helpers are the right abstraction level. |
| Add a `SectionEventParser` interface per event type | The 8-case switch in the loader is clean and complete. The sealed hierarchy already provides exhaustiveness checking. A parser-per-type registry would be harder to read with no gain at this scale. |
| Add `abstract` base classes to the sealed record hierarchy | Records cannot extend classes. Shared behaviour belongs in helper methods or interfaces with default implementations, not in abstract base classes. |
| Generalise `FixedDice` / `SequenceDice` into a single configurable class | These are test doubles with single, clear purposes. Merging them makes tests harder to read. |
| Replace the DTO layer in the loader with direct domain construction | The parse → validate → build separation exists precisely to keep validation logic isolated. Collapsing it couples parsing errors to domain invariants. |
| Move event evaluation logic to `HookDispatcher` | `HookDispatcher` dispatches; it does not own game rules. Rules that depend only on domain state belong on domain objects. |
