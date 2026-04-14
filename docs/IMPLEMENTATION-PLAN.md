# Implementation Plan

Tracks progress from spec through to working code. Each component follows the four-stage pipeline: spec → design → tests → code.

**Status legend**

| Symbol | Meaning |
|--------|---------|
| ✅ | Done |
| 🔄 | In progress |
| ⬜ | Not started |

---

## Specs

| # | Document | Status |
|---|----------|--------|
| 01 | Game mechanics | ✅ |
| 02 | Combat | ✅ |
| 03 | Adventure structure | ✅ |
| 04 | Terminal UI | ✅ |
| 05 | Scripting | ✅ |
| 06 | Items | ✅ |
| 07 | Party members | ✅ |
| 08 | Adventure authoring structure | ✅ |
| 09 | Location networks | ✅ |

---

## Design

| # | Document | Status |
|---|----------|--------|
| 01 | Architecture | ✅ |
| 02 | Domain model | ✅ |
| 03 | Combat engine | ✅ |
| 04 | Game engine | ✅ |
| 05 | Adventure loader | ✅ |
| 06 | Testability strategy | ✅ |
| 07 | Scripting engine | ✅ |
| 08 | Item model | ✅ |
| 09 | Party member model | ✅ |
| 10 | Combat systems | ✅ |
| 11 | Location networks | ✅ |

---

## Implementation

Components are grouped by layer (bottom-up). Each layer depends on the layers above it in the table. Implement in order.

### Legend

| Column | Meaning |
|--------|---------|
| Tests | Failing tests written |
| Code | Tests passing, production code complete |

---

### Layer 1 — Domain

| Component | Key classes | Tests | Code |
|-----------|-------------|-------|------|
| Player + Attribute | `Player`, `Attribute`, `AttributeType` | ⬜ | ⬜ |
| Inventory | `Inventory`, `ItemStack` | ⬜ | ⬜ |
| Adventure + Section | `Adventure`, `Section`, `SectionType`, `ScriptBlock` | ⬜ | ⬜ |
| Choice + Condition | `Choice`, `Condition` hierarchy, `ConditionEvaluator` | ⬜ | ⬜ |
| Events | `SectionEvent` sealed hierarchy | ⬜ | ⬜ |
| Item | `Item`, `ItemCategory` | ⬜ | ⬜ |
| Party member | `PartyMember`, `PartyMemberStat`, `MemberState`, `DefeatConsequence` | ⬜ | ⬜ |
| Combat records | `Creature`, `CombatRound`, `CombatResult`, `CombatOutcome` | ⬜ | ⬜ |
| Location network | `Grid`, `Cell`, `Passage`, `Direction` | ⬜ | ⬜ |

---

### Layer 2 — Mechanics

Depends on: Domain

| Component | Key classes | Tests | Code |
|-----------|-------------|-------|------|
| Dice | `Dice`, `RandomDice`, `DiceFormula` | ⬜ | ⬜ |
| Stat definition | `StatDefinition` (sealed) | ⬜ | ⬜ |
| Luck test | `LuckTest` | ⬜ | ⬜ |
| Skill test | `SkillTest` | ⬜ | ⬜ |
| Combat engine | `CombatEngine` | ⬜ | ⬜ |

---

### Layer 3 — Scripting

Depends on: Domain

| Component | Key classes | Tests | Code |
|-----------|-------------|-------|------|
| Script engine | `ScriptEngine`, `LuaScriptEngine` | ⬜ | ⬜ |
| Script context | `ScriptContext`, `DefaultScriptContext`, `PartyMemberProxy` | ⬜ | ⬜ |
| Adventure script state | `AdventureScriptState` | ⬜ | ⬜ |

---

### Layer 4 — I/O

Depends on: Domain

| Component | Key classes | Tests | Code |
|-----------|-------------|-------|------|
| Game input | `GameInput`, `TerminalInput` | ⬜ | ⬜ |
| Game output | `GameOutput`, `TerminalOutput` | ⬜ | ⬜ |

---

### Layer 5 — Combat systems

Depends on: Domain, Mechanics

| Component | Key classes | Tests | Code |
|-----------|-------------|-------|------|
| Combat system registry | `CombatSystem`, `CombatSystemRegistry`, `DefaultCombatSystemRegistry` | ⬜ | ⬜ |
| Personal combat system | `PersonalCombatSystem` | ⬜ | ⬜ |

---

### Layer 6 — Loader

Depends on: Domain

| Component | Key classes | Tests | Code |
|-----------|-------------|-------|------|
| Adventure loader | `AdventureLoader`, `JsonAdventureLoader` | ⬜ | ⬜ |

---

### Layer 7 — Engine

Depends on: all layers

| Component | Key classes | Tests | Code |
|-----------|-------------|-------|------|
| Game state | `GameState` | ⬜ | ⬜ |
| Hook dispatcher | `HookDispatcher` | ⬜ | ⬜ |
| Game loop | `Game` | ⬜ | ⬜ |

---

### Layer 8 — Test doubles

Written alongside tests. Not production code.

| Double | Implements | Written |
|--------|-----------|---------|
| `FixedDice` | `Dice` | ⬜ |
| `SequenceDice` | `Dice` | ⬜ |
| `ScriptedInput` | `GameInput` | ⬜ |
| `RecordingOutput` | `GameOutput` | ⬜ |
| `RecordingScriptContext` | `ScriptContext` | ⬜ |
| `NoOpScriptEngine` | `ScriptEngine` | ⬜ |
| `InMemoryAdventureLoader` | `AdventureLoader` | ⬜ |

---

## Design gaps

All gaps resolved. ✅

---

## Order of play

1. **Resolve design gaps** (Design Agent)
2. **Write all test doubles** (Test Agent — Layer 8 first, needed by all other tests)
3. **Layer 1 tests + code** (Domain — no dependencies)
4. **Layer 2 tests + code** (Mechanics)
5. **Layers 3 + 4 in parallel** (Scripting and I/O — both depend only on Domain)
6. **Layer 5 tests + code** (Combat systems — depends on Domain + Mechanics)
7. **Layer 6 tests + code** (Loader — depends on Domain)
8. **Layer 7 tests + code** (Engine — depends on everything)
