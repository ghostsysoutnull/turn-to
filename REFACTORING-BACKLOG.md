# Refactoring Backlog

Items here are structural improvements that were identified during unrelated work and
deliberately deferred. Each entry records what triggered the observation so the context
is not lost.

**These are not bugs and not features.** A refactoring is a structural change that leaves
all observable behaviour identical. The test suite is the enforcement mechanism: if all
existing tests pass unchanged after the refactoring, and no test was added or modified to
make them pass, it was a refactoring. If any test had to change, behaviour changed — and
it is no longer a refactoring. See `docs/design/13-oo-design-guidelines.md § Definition`
for the full definition and its implications.

No refactoring here should be started without:
1. A dedicated effort separate from any feature or bug work
2. The user's explicit awareness and approval
3. Full test coverage of every path to be moved, verified green before any structural change
4. The TDD sequence from `docs/design/13-oo-design-guidelines.md`

---

## Entry format

```
## YYYY-MM-DD — <Smell type> — <Class or area>
Noticed during: <what task triggered the observation>
Smell: <specific signal>
Threshold met: <which rule from 13-oo-design-guidelines.md>
Where: <file or class>
Scope: narrow | medium | broad
Fix: <pattern to apply and any relevant design doc already written>
```

---

## Entries

## 2026-04-16 — Smell 3: Constructor with ≥ 8 parameters — Adventure
Noticed during: B4-1 (adding `playerStats` as the 12th constructor argument)
Smell: `Adventure` has 12 positional constructor parameters. Callers pass naked `Map.of()` for unused fields — intent is invisible at every call site. ~20 test fixtures are affected.
Threshold met: Smell 3 — ≥ 8 constructor parameters with more than 2 call sites repeating the same infrastructure arguments.
Where: `src/main/java/com/tas/neo/domain/adventure/Adventure.java`
Scope: broad — touches domain (`Adventure.java`), loader (`JsonAdventureLoader`), and ~20 test fixtures across multiple test classes
Fix: static inner `Builder` per the design already written in `docs/design/02-domain-model.md § Builders`. Builder default for `playerStats` is empty map; default for `scripts`, `items`, `grids`, `partyMembers`, `combatSystems` is empty / `ScriptBlock.empty()`. Jackson continues to use the all-args constructor via `@JsonCreator`.
