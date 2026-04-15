# TAS Neo — Implementation Runbook

This is the single authoritative document for driving implementation. It replaces
`IMPLEMENTATION-PLAN.md` and `IMPLEMENTATION-PROMPTS.md`.

**How to use:** Say "start", "proceed", "where are we", or "continue". The assistant
reads the Current Position block, executes the next step, updates this block, and
reports. No manual copying or agent selection required.

---

## Current Position

```
Phase : 5 — Loader
Step  : 5.1 — Test Agent
Status: TODO
```

**Next action:** Invoke Test Agent with the prompt in § Phase 5 › Step 5.1.

---

## Progress Overview

| Phase | Description | Steps | Done |
|-------|-------------|-------|------|
| 0 | Scaffolding | 0.1 Code | ✅ |
| 1 | Domain | 1.1 Test · 1.2 Code | ✅ ✅ |
| 2 | Mechanics | 2.1 Test · 2.2 Code | ✅ ✅ |
| 3 | Scripting + I/O (parallel) | 3.1 Test-S · 3.2 Test-IO · 3.3 Code-S · 3.4 Code-IO | ✅ ✅ ✅ ✅ |
| 4 | Combat systems | 4.1 Test · 4.2 Code | ✅ ✅ |
| 5 | Loader | 5.1 Test · 5.2 Code | ⬜ ⬜ |
| 6 | Engine | 6.1 Test · 6.2 Code | ⬜ ⬜ |
| 7 | Integration | 7.1 Code | ⬜ |

---

## Execution Rules

These govern how each step is run and what "done" means.

### TDD and compilation

Tests are written before production code. After a Test Agent step the project will
**not compile** — the classes under test do not yet exist. That is correct and
expected. The Code Agent's job is to write production code that makes the tests
both compile and pass.

### Acceptance criteria

| Step type | Done when |
|-----------|-----------|
| Test Agent | Agent reports completion; test files exist at the correct paths |
| Code Agent | `mvn -q test` exits 0; no test failures or errors |

### Parallel steps (Phase 3)

Steps 3.1 and 3.2 (Test Agents) are dispatched in parallel — two agents run
simultaneously. Steps 3.3 and 3.4 (Code Agents) follow after both test steps are
done; they too can run in parallel. Advance the cursor only when all four steps in
Phase 3 are done.

### Blockers

If a Code Agent step cannot pass tests without resolving a design ambiguity, it
reports a blocker and stops. Do not invent a resolution. Escalate to the user.

### Lessons learned

Every agent reads `docs/LESSONS.md` before starting. When something worth recording is
discovered, append one entry: one heading line, then at most three lines — what, where,
fix. No hedging. If a lesson reveals a design doc is wrong, fix the doc instead of
writing an entry. Do not reorganize the file mid-task.

---

### Testability covenant — applies to every agent

Every agent invoked from this runbook must honour the following rules.

- **No human interaction in tests.** `TerminalInput` and `TerminalOutput` never
  appear in test code. Use `ScriptedInput` and `RecordingOutput`.
- **No console output on success.** Tests must not call `System.out.println` or
  log to stdout. A passing suite produces only the Maven summary line.
- **Failure messages must be self-diagnosing.** Use `.as("description")` on
  non-obvious AssertJ assertions so the broken invariant is readable without
  running the code.
- **No non-determinism.** All dice use `FixedDice` or `SequenceDice`.
- **Full playthroughs use `ScenarioRunner`.** Do not wire up `Game` manually in
  engine or integration tests.
- **Maven Surefire must be configured** in `pom.xml` per
  `docs/design/06-testability.md` before any tests are written (done once in
  Step 1.2, stays in place for all later steps).
- **Always run `mvn -q test`** (not `mvn test`) when verifying a phase. The `-q`
  flag suppresses Maven's own INFO build chatter. On success the only output is
  the final aggregate summary line.

If a Code Agent output introduces terminal-touching code in tests or produces
console noise on success, treat it as a defect — invoke the Code Agent again to
fix it before advancing.

---

## Phase 0 — Scaffolding

No Test Agent step. Creates the Maven project skeleton before any code is written.
All subsequent phases depend on this being in place.

### Step 0.1 — Code Agent: Scaffolding

```
You are the Code Agent for TAS Neo. Read workflow/agents/code-agent.md first.

Task: Create the Maven project skeleton. No production classes, no test classes.

Produce exactly:

1. pom.xml at the project root with:
   - groupId: com.tas.neo, artifactId: tas-neo, version: 1.0-SNAPSHOT
   - Java 21 (maven-compiler-plugin source/target 21)
   - Dependencies:
       org.junit.jupiter:junit-jupiter:5.11.0 (test scope)
       org.assertj:assertj-core:3.26.3 (test scope)
       org.luaj:luaj-jse:3.0.1
       com.fasterxml.jackson.core:jackson-databind:2.17.2
   - maven-surefire-plugin configured exactly as specified in
     docs/design/06-testability.md
   - maven-jar-plugin with mainClass: com.tas.neo.Main

2. Empty package directories (create a .gitkeep in each):
   - src/main/java/com/tas/neo/
   - src/test/java/com/tas/neo/
   - src/test/resources/adventures/
   - adventures/

3. .gitignore at the project root covering: target/, *.class, *.iml,
   .idea/, .vscode/, sessions/

4. sessions/ directory entry in .gitignore (do not create the directory —
   it is created at runtime by FileGameLogger).

Run mvn -q test after creating pom.xml to confirm the build lifecycle
works with zero tests. Expected output: BUILD SUCCESS.

Do not create any Java source files. Do not touch docs/ or workflow/.
```

**Acceptance:** `mvn -q test` exits 0; directory structure exists; `.gitignore` present.

---

## Phase 1 — Domain

Depends on: Phase 0. All other phases depend on this one.

### Step 1.1 — Test Agent: Domain

```
You are the Test Agent for TAS Neo. Read workflow/agents/test-agent.md first.

Task: Write all failing tests for the domain layer.

Design docs to read (all test strategy tables apply):
- docs/design/02-domain-model.md      — Player, Attribute, Adventure, Section, Choice,
                                        ChoiceTarget, Condition hierarchy, Events,
                                        Creature, combat records
- docs/design/08-item-model.md        — Item, ItemCategory, Inventory, ItemStack,
                                        onDrop/onPickup rules
- docs/design/09-party-member-model.md — PartyMember, PartyMemberStat, MemberState,
                                        DefeatConsequence, StatDefinition, DiceFormula,
                                        PartyMemberDefinition, party member conditions
- docs/design/11-location-networks.md — Grid, Cell, Passage, Direction
- docs/design/12-session-logging.md   — NavigationEntry, PlayerSnapshot, GameError,
                                        SessionLog (domain records only — GameLogger
                                        interface is in Phase 3)
- docs/design/06-testability.md       — available test doubles and their signatures

Also write the following test doubles and utilities (needed by tests in this and all
later phases):
- FixedDice
- SequenceDice
- ScriptedInput
- RecordingOutput (with OutputEvent sealed hierarchy — see docs/design/06-testability.md)
- RecordingScriptContext
- NoOpScriptEngine
- InMemoryAdventureLoader
- ScenarioRunner + ScenarioResult (scripted and random static factories — see
  docs/design/06-testability.md)
- RecordingGameLogger (accumulates NavigationEntry, OutputEvent, GameError in memory)

Place test doubles in src/test/java/com/tas/neo/ at the appropriate sub-package.
Place test classes mirroring the class under test
(e.g. PlayerTest → src/test/java/com/tas/neo/domain/player/PlayerTest.java).

The production classes do not exist yet. Tests will not compile until the Code Agent
runs — that is expected. Write tests that correctly assert the behaviour specified in
the design docs.

Out of scope: production code, tests for any layer above domain (mechanics, scripting,
io, combat, loader, engine).
```

**Acceptance:** test files exist at correct paths; agent reports no design gaps.

---

### Step 1.2 — Code Agent: Domain

```
You are the Code Agent for TAS Neo. Read workflow/agents/code-agent.md first.

Task: Implement the domain layer. Make all domain tests compile and pass.

Failing tests to satisfy:
- src/test/java/com/tas/neo/domain/ (all test classes written by the Test Agent)

Design docs to implement from:
- docs/design/01-architecture.md       — package structure, layer dependency rules
- docs/design/02-domain-model.md       — Player, Attribute, AttributeType, Adventure,
                                         Section, SectionType, Choice, ChoiceTarget
                                         (SectionTarget, GridTarget), ScriptBlock,
                                         SectionEvent sealed hierarchy, Condition sealed
                                         hierarchy, Creature, CombatRound, CombatResult,
                                         CombatOutcome, ConditionEvaluator
- docs/design/08-item-model.md         — Item, ItemCategory, ItemScriptHook, Inventory,
                                         ItemStack
- docs/design/09-party-member-model.md — PartyMember, PartyMemberStat, MemberState,
                                         DefeatConsequence sealed hierarchy,
                                         StatDefinition sealed hierarchy,
                                         PartyMemberDefinition
- docs/design/11-location-networks.md  — Direction, Passage, Cell, Grid
- docs/design/12-session-logging.md    — NavigationEntry, PlayerSnapshot, GameError,
                                         SessionLog (records in com.tas.neo.domain.log)

Package root: com.tas.neo. Domain classes under com.tas.neo.domain.

DiceFormula lives in com.tas.neo.mechanics — write it there. The domain layer does not
import it directly. ConditionEvaluator is a service class; it lives in the domain
package alongside the Condition types it evaluates.

Do not implement any class outside the domain and mechanics.DiceFormula.
Do not touch src/test/.
```

**Acceptance:** `mvn -q test` exits 0 with all domain tests passing.

---

## Phase 2 — Mechanics

Depends on: Phase 1 (Domain).

### Step 2.1 — Test Agent: Mechanics

```
You are the Test Agent for TAS Neo. Read workflow/agents/test-agent.md first.

Task: Write all failing tests for the mechanics layer.

Design docs to read:
- docs/design/03-combat-engine.md      — CombatEngine, LuckTest, SkillTest
- docs/design/09-party-member-model.md — DiceFormula, StatDefinition test strategy rows
- docs/design/06-testability.md        — test doubles to use

Available test doubles (written in Phase 1):
- FixedDice, SequenceDice, ScriptedInput, RecordingOutput

Place test classes in src/test/java/com/tas/neo/mechanics/.

Production code from Phase 1 exists. These tests should compile against it.
```

**Acceptance:** test files exist at correct paths; agent reports no design gaps.

---

### Step 2.2 — Code Agent: Mechanics

```
You are the Code Agent for TAS Neo. Read workflow/agents/code-agent.md first.

Task: Implement the mechanics layer. Make all mechanics tests compile and pass.

Failing tests to satisfy:
- src/test/java/com/tas/neo/mechanics/ (all test classes written by the Test Agent)

Design docs to implement from:
- docs/design/03-combat-engine.md      — CombatEngine, LuckTest, SkillTest
- docs/design/09-party-member-model.md — DiceFormula, StatDefinition sealed hierarchy
                                         (FixedStatDefinition, DiceStatDefinition)

Package: com.tas.neo.mechanics

DiceFormula may already exist from Phase 1 — verify it matches the design before
writing anything. Do not rewrite it if it is already correct.

Do not touch src/test/.
```

**Acceptance:** `mvn -q test` exits 0 with all mechanics tests (and all Phase 1 tests) passing.

---

## Phase 3 — Scripting + I/O (parallel)

Depends on: Phase 1 (Domain). Both sub-tracks are independent of each other.

**Execution order:**
1. Dispatch Step 3.1 and Step 3.2 in **parallel**.
2. Wait for both to complete.
3. Dispatch Step 3.3 and Step 3.4 in **parallel**.
4. Wait for both to complete, then advance.

---

### Step 3.1 — Test Agent: Scripting

```
You are the Test Agent for TAS Neo. Read workflow/agents/test-agent.md first.

Task: Write all failing tests for the scripting layer.

Design docs to read:
- docs/design/07-scripting-engine.md — ScriptEngine, LuaScriptEngine, ScriptContext,
                                       DefaultScriptContext, AdventureScriptState,
                                       PartyMemberProxy, HookDispatcher
- docs/design/06-testability.md      — test doubles to use

Available test doubles (written in Phase 1):
- FixedDice, RecordingScriptContext, NoOpScriptEngine, InMemoryAdventureLoader

Key contracts to test:
- LuaScriptEngine executes a script and calls ctx methods — use RecordingScriptContext
- Failed script does not crash — ScriptException is caught, game continues
- Sandboxing: io.open() from a script throws; no file access permitted
- HookDispatcher fires the correct hook for a given ScriptBlock
- navigateTo blocked in onChoices context — UnsupportedOperationException
- ctx.currentSection() returns -1 in a grid cell context
- ctx.hideChoice(id) removes the matching choice; no-op if id not found
- Party member proxy: no-op on unknown id

Place test classes in src/test/java/com/tas/neo/scripting/.
```

**Acceptance:** test files exist at correct paths; agent reports no design gaps.

---

### Step 3.2 — Test Agent: I/O

```
You are the Test Agent for TAS Neo. Read workflow/agents/test-agent.md first.

Task: Write all failing tests for the I/O layer, including session logging
implementations.

Design docs to read:
- docs/design/01-architecture.md     — GameInput, GameOutput, GameLogger interface
                                       signatures
- docs/design/08-item-model.md       — inventory screen rendering (showInventory format)
- docs/design/12-session-logging.md  — GameLogger, FileGameLogger, NoOpGameLogger; text
                                       and JSON log formats; test strategy table
- docs/design/06-testability.md      — TerminalOutput and TerminalInput test rows

Key contracts to test:
- TerminalOutput: showNarrative, showChoices, showMessage write expected strings to
  the PrintStream
- TerminalInput: readChoice returns the player's 1-based selection; re-prompts on
  invalid input
- TerminalInput: readYesNo parses y/n; re-prompts on other input
- Inventory screen: countable items show quantity suffix; non-countable do not
- FileGameLogger: ScenarioRunner with FileGameLogger pointing at a temp dir → both
  .txt and .json files written on close(); JSON parses and contains expected fields
- NoOpGameLogger: all calls succeed silently; no files written
- SeededDice reproducibility: two ScenarioRunner.random() runs with the same seed
  produce identical navigation paths

Use a captured PrintStream for TerminalOutput tests.
Use ByteArrayInputStream for TerminalInput tests.

Place test classes in src/test/java/com/tas/neo/io/.
SeededDice reproducibility test goes in src/test/java/com/tas/neo/mechanics/.
```

**Acceptance:** test files exist at correct paths; agent reports no design gaps.

---

### Step 3.3 — Code Agent: Scripting

```
You are the Code Agent for TAS Neo. Read workflow/agents/code-agent.md first.

Task: Implement the scripting layer. Make all scripting tests compile and pass.

Failing tests to satisfy:
- src/test/java/com/tas/neo/scripting/

Design docs to implement from:
- docs/design/07-scripting-engine.md — ScriptEngine, LuaScriptEngine (uses LuaJ),
                                       ScriptContext, DefaultScriptContext,
                                       AdventureScriptState, PartyMemberProxy,
                                       HookDispatcher

Package: com.tas.neo.scripting

LuaJ is the embedded Lua interpreter. Add it as a Maven dependency if not already
present. Remove io, os, package, require, dofile, load, loadfile from the Lua
environment before executing any script.

DefaultScriptContext must throw UnsupportedOperationException for navigateTo when
operating in onChoices mode, and for addChoice/hideChoice when outside onChoices mode.

Do not touch src/test/.
```

**Acceptance:** `mvn -q test` exits 0 with all scripting tests (and all prior tests) passing.

---

### Step 3.4 — Code Agent: I/O

```
You are the Code Agent for TAS Neo. Read workflow/agents/code-agent.md first.

Task: Implement the I/O layer, including session logging. Make all I/O tests compile
and pass.

Failing tests to satisfy:
- src/test/java/com/tas/neo/io/
- src/test/java/com/tas/neo/mechanics/ (SeededDice reproducibility test)

Design docs to implement from:
- docs/design/01-architecture.md     — GameInput, GameOutput, GameLogger interfaces
- docs/design/08-item-model.md       — inventory screen format
- docs/design/12-session-logging.md  — GameLogger interface, FileGameLogger (text + JSON
                                       via Jackson), NoOpGameLogger; SeededDice (in
                                       com.tas.neo.mechanics)

Package: com.tas.neo.io

TerminalInput and TerminalOutput are the only classes that may use System.in and
System.out — via injected streams, not directly.

FileGameLogger builds the SessionLog in memory and writes both files in close(). It
creates the sessions/ directory if it does not exist. File names:
  <adventureId>-<timestamp>.txt
  <adventureId>-<timestamp>.json

SeededDice lives in com.tas.neo.mechanics alongside RandomDice. It uses
java.util.Random seeded at construction.

Note: a stub OutputEvent.java exists in src/main/java/com/tas/neo/io/ from Phase 1.
Replace it with the full production implementation here. The test double copy in
src/test/java/com/tas/neo/io/OutputEvent.java becomes redundant once the production
class is correct — the Phase 3 Code Agent should verify they are consistent and remove
the test copy if the production class covers all the same cases.

Do not touch src/test/.
```

**Acceptance:** `mvn -q test` exits 0 with all I/O tests (and all prior tests) passing.

---

## Phase 4 — Combat systems

Depends on: Phase 1 (Domain), Phase 2 (Mechanics).

### Step 4.1 — Test Agent: Combat systems

```
You are the Test Agent for TAS Neo. Read workflow/agents/test-agent.md first.

Task: Write all failing tests for the combat systems layer.

Design docs to read:
- docs/design/10-combat-systems.md — CombatSystem, CombatSystemRegistry,
                                     DefaultCombatSystemRegistry, PersonalCombatSystem
- docs/design/06-testability.md    — test doubles to use

Available test doubles (written in Phase 1):
- FixedDice, NoOpScriptEngine, RecordingOutput, ScriptedInput

Key contracts to test:
- PersonalCombatSystem: PLAYER_VICTORY outcome via FixedDice (player always wins)
- PersonalCombatSystem: PLAYER_DEFEAT outcome via FixedDice (player always loses)
- DefaultCombatSystemRegistry: get known id returns system
- DefaultCombatSystemRegistry: get unknown id throws
- CombatEvent without system field defaults to "personal"
- Participant resolution: GameState with a named party member, CombatEvent names it,
  correct member is passed to the system

Place test classes in src/test/java/com/tas/neo/combat/.
```

**Acceptance:** test files exist at correct paths; agent reports no design gaps.

---

### Step 4.2 — Code Agent: Combat systems

```
You are the Code Agent for TAS Neo. Read workflow/agents/code-agent.md first.

Task: Implement the combat systems layer. Make all combat system tests compile and pass.

Failing tests to satisfy:
- src/test/java/com/tas/neo/combat/

Design docs to implement from:
- docs/design/10-combat-systems.md — CombatSystem, CombatSystemRegistry,
                                     DefaultCombatSystemRegistry, PersonalCombatSystem

Package: com.tas.neo.combat

PersonalCombatSystem wraps CombatEngine from the mechanics layer.
DefaultCombatSystemRegistry takes a varargs of CombatSystem at construction;
PersonalCombatSystem is always registered.

Do not touch src/test/.
```

**Acceptance:** `mvn -q test` exits 0 with all combat tests (and all prior tests) passing.

---

## Phase 5 — Loader

Depends on: Phase 1 (Domain).

### Step 5.1 — Test Agent: Loader

```
You are the Test Agent for TAS Neo. Read workflow/agents/test-agent.md first.

Task: Write all failing tests for the adventure loader.

Design docs to read:
- docs/design/05-adventure-loader.md — AdventureLoader, JsonAdventureLoader,
                                       AdventureLoadException, validation rules,
                                       JSON format

Use fixture JSON files in src/test/resources/ — one fixture per validation rule
(a valid case and a malformed case per rule). Do not construct Adventure objects
programmatically in loader tests; use JSON fixtures.

Cover every validation rule in the design doc, including:
- All section/event/item/party member rules
- All grid rules (cell positions, no duplicate (x,y,z) within a grid, passage
  direction targets, toSection references valid, toGrid/toCell targets exist)

Place test class at src/test/java/com/tas/neo/loader/JsonAdventureLoaderTest.java.
Place fixture files at src/test/resources/adventures/.
```

**Acceptance:** test files and fixtures exist at correct paths; agent reports no design gaps.

---

### Step 5.2 — Code Agent: Loader

```
You are the Code Agent for TAS Neo. Read workflow/agents/code-agent.md first.

Task: Implement the adventure loader. Make all loader tests compile and pass.

Failing tests to satisfy:
- src/test/java/com/tas/neo/loader/JsonAdventureLoaderTest.java
- Fixture files under src/test/resources/adventures/

Design docs to implement from:
- docs/design/05-adventure-loader.md — AdventureLoader interface, JsonAdventureLoader,
                                       AdventureLoadException, validation rules

Package: com.tas.neo.loader

Use Jackson for JSON parsing. JsonAdventureLoader receives the adventures/ root Path
at construction. Validation runs at load time; all rule violations throw
AdventureLoadException.

The grids array must be parsed and validated per the design doc.
InMemoryAdventureLoader is already in src/test/ — do not rewrite it.

Do not touch src/test/.
```

**Acceptance:** `mvn -q test` exits 0 with all loader tests (and all prior tests) passing.

---

## Phase 6 — Engine

Depends on: all prior phases.

### Step 6.1 — Test Agent: Engine

```
You are the Test Agent for TAS Neo. Read workflow/agents/test-agent.md first.

Task: Write all failing tests for the engine layer.

Design docs to read:
- docs/design/04-engine.md            — GameState, Game, HookDispatcher, system choices,
                                        character creation, section type handling
- docs/design/06-testability.md       — full game loop test rows
- docs/design/11-location-networks.md — grid navigation test rows
- docs/design/12-session-logging.md   — session logging test strategy rows

Available test doubles: all doubles written in Phase 1.
Use InMemoryAdventureLoader for all engine tests — do not use JsonAdventureLoader.

Key contracts to test (from the design doc test strategy tables):
- VICTORY section ends the game
- INSTANT_DEATH section ends the game
- Player death mid-event (STAMINA → 0) ends game without showing choices
- System choices (inventory, quit) are always injected at every section
- Navigation follows the selected choice to the correct section
- Party member created at start; stats fall within expected rolled ranges
- Grid entry via GridTarget choice → state.isInGrid() true, correct cell
- Grid exit via toSection passage → state.isInGrid() false, correct section number
- Cell events fire on cell entry
- ScenarioRunner with RecordingGameLogger: sessionLog().path() matches navigation sequence
- ScenarioRunner with RecordingGameLogger + broken script → hasErrors() true,
  error source and type correct
- ScenarioRunner with default logger (NoOpGameLogger): no files written to sessions/

Place test classes in src/test/java/com/tas/neo/engine/.
```

**Acceptance:** test files exist at correct paths; agent reports no design gaps.

---

### Step 6.2 — Code Agent: Engine

```
You are the Code Agent for TAS Neo. Read workflow/agents/code-agent.md first.

Task: Implement the engine layer. Make all engine tests compile and pass.

Failing tests to satisfy:
- src/test/java/com/tas/neo/engine/

Design docs to implement from:
- docs/design/04-engine.md            — GameState (including grid navigation:
                                        navigateToCell, currentGrid, currentCell,
                                        isInGrid), HookDispatcher, Game
- docs/design/07-scripting-engine.md  — HookDispatcher (fireSectionHook, fireCellHook,
                                        fireAdventureHook, fireCombatHook, fireItemHook)
- docs/design/12-session-logging.md   — engine integration: Game receives GameLogger,
                                        HookDispatcher catches script errors and calls
                                        logError, PlayerSnapshot.of(state) factory

Package: com.tas.neo.engine

The engine layer may depend on all other layers. Wire up in Game.run():
1. Load adventure via AdventureLoader
2. Roll player stats (SKILL: 1d6+6, STAMINA: 2d6+12, LUCK: 1d6+6)
3. Resolve party member stats via StatDefinition + Dice
4. Fire ON_LOAD, then ON_START
5. Enter main game loop
6. Call logger.logNavigation() on every section/cell transition
7. Call logger.logEvent() for every OutputEvent produced
8. Call logger.logError() for every caught script or navigation error — do NOT
   rethrow; the session continues after an error is logged
9. Call logger.close() when the session ends (victory, game over, or
   unrecoverable fault)

Grid navigation: when a choice has a GridTarget, call state.navigateToCell(grid, cell)
and process the cell (fire events → run hooks → build passage choices + explicit
choices). When a passage has toSection, call state.navigateTo(section).

Game constructor:
  public Game(GameInput input, GameOutput output, AdventureLoader loader,
              Dice dice, ScriptEngine scriptEngine,
              CombatSystemRegistry combatRegistry, GameLogger logger)

Do not touch src/test/.
```

**Acceptance:** `mvn -q test` exits 0 with the full test suite (all phases) passing.

---

## Phase 7 — Integration

Depends on: Phase 6 (Engine). This is the only phase with no Test Agent step —
verification is done by compiling and starting the application.

### Step 7.1 — Code Agent: Main wiring

```
You are the Code Agent for TAS Neo. Read workflow/agents/code-agent.md first.

Task: Write Main.java and verify the full system wires together without errors.

Design doc to implement from:
- docs/design/01-architecture.md — Wiring (Main) section

Package: com.tas.neo (Main.java at the package root)

Write Main exactly as specified in the wiring section. Do not add any logic beyond
what is shown there.

After writing Main.java, run mvn -q test to confirm the full suite still passes and
the project compiles cleanly (compile is implicit in the test lifecycle).

If any wiring issue surfaces a design gap (missing constructor, wrong type, etc.),
report it as a blocker — do not invent a resolution.
```

**Acceptance:** `mvn -q test` exits 0; no design gaps surfaced.

---

## Appendix — Design documents reference

| # | Document | Relevant phases |
|---|----------|-----------------|
| 01 | Architecture | 1.2, 3.4, 7.1 |
| 02 | Domain model | 1.1, 1.2 |
| 03 | Combat engine | 2.1, 2.2 |
| 04 | Game engine | 6.1, 6.2 |
| 05 | Adventure loader | 5.1, 5.2 |
| 06 | Testability strategy | all |
| 07 | Scripting engine | 3.1, 3.3, 6.2 |
| 08 | Item model | 1.1, 1.2, 3.2, 3.4 |
| 09 | Party member model | 1.1, 1.2, 2.1, 2.2 |
| 10 | Combat systems | 4.1, 4.2 |
| 11 | Location networks | 1.1, 1.2, 6.1, 6.2 |
| 12 | Session logging | 1.1, 1.2, 3.2, 3.4, 6.1, 6.2 |
