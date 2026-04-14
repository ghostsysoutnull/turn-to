# Implementation Prompts

Ready-to-use prompts for each agent run in the implementation sequence. Copy each prompt verbatim as the agent's task input.

---

## How to use this document

1. Copy the prompt block for the current phase.
2. Invoke the agent with that prompt.
3. Review the output before proceeding to the next prompt.
4. Update `docs/IMPLEMENTATION-PLAN.md` to mark components done.

The phases follow the layer dependency order from `docs/IMPLEMENTATION-PLAN.md`. Complete each phase before starting the next.

---

## Testability covenant — applies to every prompt

Every agent invoked from this document must honour the following rules. They are repeated here so no agent can miss them.

- **No human interaction in tests.** `TerminalInput` and `TerminalOutput` never appear in test code. Use `ScriptedInput` and `RecordingOutput`.
- **No console output on success.** Tests must not call `System.out.println` or log to stdout. A passing suite produces only the Maven summary line.
- **Failure messages must be self-diagnosing.** An agent reading a failure must identify the broken invariant without running the code. Use `.as("description")` on non-obvious AssertJ assertions.
- **No non-determinism.** All dice use `FixedDice` or `SequenceDice`.
- **Full playthroughs use `ScenarioRunner`.** Do not wire up `Game` manually in engine or integration tests. Use `ScenarioRunner` (defined in `docs/design/06-testability.md`) instead.
- **Maven Surefire must be configured** in `pom.xml` per `docs/design/06-testability.md` before any tests are written.

If any Code Agent output introduces terminal-touching code in tests or produces console noise, treat it as a defect and invoke the Code Agent again to fix it before proceeding.

---

## Note on TDD and compilation

This project uses TDD: tests are written before production code. For a greenfield Java project this means the Test Agent's output will reference classes that do not yet exist — the tests will not compile until the Code Agent runs. That is expected and correct.

The Code Agent's job for each phase is to write production code that makes the Test Agent's tests both **compile** and **pass**.

---

## Phase 1 — Domain layer

### Prompt: Test Agent — Domain

```
You are the Test Agent for TAS Neo. Read workflow/agents/test-agent.md first.

Task: Write all failing tests for the domain layer.

Design docs to read (all test strategy tables apply):
- docs/design/02-domain-model.md  — Player, Attribute, Adventure, Section, Choice, ChoiceTarget, Condition hierarchy, Events, Creature, combat records
- docs/design/08-item-model.md    — Item, ItemCategory, Inventory, ItemStack, onDrop/onPickup rules
- docs/design/09-party-member-model.md — PartyMember, PartyMemberStat, MemberState, DefeatConsequence, StatDefinition, DiceFormula, PartyMemberDefinition, party member conditions
- docs/design/11-location-networks.md — Grid, Cell, Passage, Direction
- docs/design/12-session-logging.md — NavigationEntry, PlayerSnapshot, GameError, SessionLog (domain records only — GameLogger interface is in Phase 3)
- docs/design/06-testability.md   — available test doubles and their signatures

Also write the following test doubles and utilities (needed by tests in this and all later phases):
- FixedDice
- SequenceDice
- ScriptedInput
- RecordingOutput (with OutputEvent sealed hierarchy — see docs/design/06-testability.md)
- RecordingScriptContext
- NoOpScriptEngine
- InMemoryAdventureLoader
- ScenarioRunner + ScenarioResult (scripted and random static factories — see docs/design/06-testability.md)
- RecordingGameLogger (accumulates NavigationEntry, OutputEvent, GameError in memory)

Place test doubles in src/test/java/com/tas/neo/ at the appropriate sub-package. Place test classes in the mirrored path of the class under test (e.g. PlayerTest → src/test/java/com/tas/neo/domain/player/PlayerTest.java).

The production classes do not exist yet. Tests will not compile until the Code Agent runs — that is expected. Write tests that correctly assert the behaviour specified in the design docs.

Out of scope: production code, tests for any layer above domain (mechanics, scripting, io, combat, loader, engine).
```

---

### Prompt: Code Agent — Domain

```
You are the Code Agent for TAS Neo. Read workflow/agents/code-agent.md first.

Task: Implement the domain layer. Make all domain tests compile and pass.

Failing tests to satisfy:
- src/test/java/com/tas/neo/domain/ (all test classes written by the Test Agent)

Design docs to implement from:
- docs/design/01-architecture.md  — package structure, layer dependencies
- docs/design/02-domain-model.md  — Player, Attribute, AttributeType, Adventure, Section, SectionType, Choice, ChoiceTarget (SectionTarget, GridTarget), ScriptBlock, SectionEvent sealed hierarchy, Condition sealed hierarchy, Creature, CombatRound, CombatResult, CombatOutcome, ConditionEvaluator
- docs/design/08-item-model.md    — Item, ItemCategory, ItemScriptHook, Inventory, ItemStack
- docs/design/09-party-member-model.md — PartyMember, PartyMemberStat, MemberState, DefeatConsequence sealed hierarchy, StatDefinition sealed hierarchy, PartyMemberDefinition
- docs/design/11-location-networks.md — Direction, Passage, Cell, Grid
- docs/design/12-session-logging.md — NavigationEntry, PlayerSnapshot, GameError, SessionLog (domain records in com.tas.neo.domain.log)

Package root: com.tas.neo
Domain package: com.tas.neo.domain

DiceFormula lives in com.tas.neo.mechanics (it is a mechanics concern referenced by domain). Write it there — the domain layer does not need to import it directly. ConditionEvaluator is a service class; it lives in domain alongside the Condition types it evaluates.

Before writing any production class, configure maven-surefire-plugin in pom.xml exactly as specified in docs/design/06-testability.md. This must be in place before the test suite is run for the first time.

Do not implement any class outside the domain and mechanics.DiceFormula. Do not touch src/test/.
```

---

## Phase 2 — Mechanics layer

### Prompt: Test Agent — Mechanics

```
You are the Test Agent for TAS Neo. Read workflow/agents/test-agent.md first.

Task: Write all failing tests for the mechanics layer.

Design docs to read:
- docs/design/03-combat-engine.md — CombatEngine, LuckTest, SkillTest
- docs/design/09-party-member-model.md — DiceFormula, StatDefinition (test strategy rows for these)
- docs/design/06-testability.md   — test doubles to use

Available test doubles (already written in Phase 1):
- FixedDice, SequenceDice, ScriptedInput, RecordingOutput

Place test classes in src/test/java/com/tas/neo/mechanics/.

Production code from Phase 1 exists. These tests should compile against it.
```

---

### Prompt: Code Agent — Mechanics

```
You are the Code Agent for TAS Neo. Read workflow/agents/code-agent.md first.

Task: Implement the mechanics layer. Make all mechanics tests compile and pass.

Failing tests to satisfy:
- src/test/java/com/tas/neo/mechanics/ (all test classes written by the Test Agent)

Design docs to implement from:
- docs/design/03-combat-engine.md — CombatEngine, LuckTest, SkillTest
- docs/design/09-party-member-model.md — DiceFormula, StatDefinition sealed hierarchy (FixedStatDefinition, DiceStatDefinition)

Package: com.tas.neo.mechanics

DiceFormula may already exist from Phase 1 — if so, verify it matches the design and do not rewrite it.

Do not touch src/test/.
```

---

## Phase 3 — Scripting and I/O layers (run in parallel)

These two layers both depend only on the domain layer and can be implemented simultaneously.

---

### Prompt: Test Agent — Scripting

```
You are the Test Agent for TAS Neo. Read workflow/agents/test-agent.md first.

Task: Write all failing tests for the scripting layer.

Design docs to read:
- docs/design/07-scripting-engine.md — ScriptEngine, LuaScriptEngine, ScriptContext, DefaultScriptContext, AdventureScriptState, PartyMemberProxy, HookDispatcher
- docs/design/06-testability.md — test doubles to use

Available test doubles: FixedDice, RecordingScriptContext, NoOpScriptEngine, InMemoryAdventureLoader (all written in Phase 1).

Key contracts to test:
- LuaScriptEngine executes a script and calls ctx methods — use RecordingScriptContext
- Failed script does not crash — ScriptException caught, game continues
- Sandboxing: io.open() from a script throws, no file access
- HookDispatcher fires the correct hook for a given ScriptBlock
- navigateTo blocked in onChoices context — UnsupportedOperationException
- ctx.currentSection() returns -1 in a grid cell context
- ctx.hideChoice(id) removes the matching choice; no-op if id not found
- Party member proxy no-ops on unknown id

Place test classes in src/test/java/com/tas/neo/scripting/.
```

---

### Prompt: Code Agent — Scripting

```
You are the Code Agent for TAS Neo. Read workflow/agents/code-agent.md first.

Task: Implement the scripting layer. Make all scripting tests compile and pass.

Failing tests to satisfy:
- src/test/java/com/tas/neo/scripting/

Design docs to implement from:
- docs/design/07-scripting-engine.md — ScriptEngine, LuaScriptEngine (uses LuaJ), ScriptContext, DefaultScriptContext, AdventureScriptState, PartyMemberProxy, HookDispatcher

Package: com.tas.neo.scripting

LuaJ is the embedded Lua interpreter. Add it as a Maven dependency if not already present. Remove io, os, package, require, dofile, load, loadfile from the Lua environment before executing any script.

DefaultScriptContext must throw UnsupportedOperationException for navigateTo when operating in onChoices mode, and for addChoice/hideChoice when outside onChoices mode.

Do not touch src/test/.
```

---

### Prompt: Test Agent — I/O

```
You are the Test Agent for TAS Neo. Read workflow/agents/test-agent.md first.

Task: Write all failing tests for the I/O layer (including session logging implementations).

Design docs to read:
- docs/design/01-architecture.md — GameInput, GameOutput, GameLogger interface signatures
- docs/design/08-item-model.md   — inventory screen rendering (showInventory format)
- docs/design/12-session-logging.md — GameLogger, FileGameLogger, NoOpGameLogger; text log and JSON log formats; test strategy table
- docs/design/06-testability.md  — TerminalOutput and TerminalInput test rows

Key contracts to test:
- TerminalOutput: showNarrative, showChoices, showMessage write expected strings to the PrintStream
- TerminalInput: readChoice returns the player's 1-based selection; re-prompts on invalid input
- TerminalInput: readYesNo parses y/n correctly; re-prompts on other input
- Inventory screen renders countable items with quantity suffix, non-countable without
- FileGameLogger: ScenarioRunner with FileGameLogger pointing to a temp dir → both .txt and .json files written on close(), JSON parses correctly
- NoOpGameLogger: all calls succeed silently, no files written
- SeededDice reproducibility: two ScenarioRunner.random() runs with the same seed produce identical navigation paths

Use a captured PrintStream for TerminalOutput tests. Use ByteArrayInputStream for TerminalInput tests.

Place test classes in src/test/java/com/tas/neo/io/.
SeededDice test may go in src/test/java/com/tas/neo/mechanics/.
```

---

### Prompt: Code Agent — I/O

```
You are the Code Agent for TAS Neo. Read workflow/agents/code-agent.md first.

Task: Implement the I/O layer (including session logging). Make all I/O tests compile and pass.

Failing tests to satisfy:
- src/test/java/com/tas/neo/io/
- src/test/java/com/tas/neo/mechanics/ (SeededDice reproducibility test)

Design docs to implement from:
- docs/design/01-architecture.md — GameInput, GameOutput, GameLogger interfaces
- docs/design/08-item-model.md   — inventory screen format
- docs/design/12-session-logging.md — GameLogger interface, FileGameLogger (text + JSON via Jackson), NoOpGameLogger; SeededDice (in com.tas.neo.mechanics)

Package: com.tas.neo.io

TerminalInput and TerminalOutput are the only classes that may use System.in and System.out — via injected streams, not directly. All other layers use the interfaces.

FileGameLogger builds the SessionLog in memory and writes both files in close(). The sessions/ directory is created if it does not exist. File names follow the pattern: <adventureId>-<timestamp>.txt and .json.

SeededDice lives in com.tas.neo.mechanics alongside RandomDice. It uses java.util.Random seeded at construction.

Do not touch src/test/.
```

---

## Phase 4 — Combat systems

### Prompt: Test Agent — Combat systems

```
You are the Test Agent for TAS Neo. Read workflow/agents/test-agent.md first.

Task: Write all failing tests for the combat systems layer.

Design docs to read:
- docs/design/10-combat-systems.md — CombatSystem, CombatSystemRegistry, DefaultCombatSystemRegistry, PersonalCombatSystem
- docs/design/06-testability.md   — test doubles to use

Available test doubles: FixedDice, NoOpScriptEngine, RecordingOutput, ScriptedInput.

Key contracts to test:
- PersonalCombatSystem victory and defeat outcomes via FixedDice
- DefaultCombatSystemRegistry: get known id returns system, get unknown id throws
- CombatEvent without system field defaults to "personal"
- Participant resolution: GameState with party member, CombatEvent names it, correct member passed

Place test classes in src/test/java/com/tas/neo/combat/.
```

---

### Prompt: Code Agent — Combat systems

```
You are the Code Agent for TAS Neo. Read workflow/agents/code-agent.md first.

Task: Implement the combat systems layer. Make all combat system tests compile and pass.

Failing tests to satisfy:
- src/test/java/com/tas/neo/combat/

Design docs to implement from:
- docs/design/10-combat-systems.md — CombatSystem, CombatSystemRegistry, DefaultCombatSystemRegistry, PersonalCombatSystem

Package: com.tas.neo.combat

PersonalCombatSystem wraps CombatEngine from the mechanics layer. DefaultCombatSystemRegistry takes a varargs of CombatSystem at construction; PersonalCombatSystem is always included.

Do not touch src/test/.
```

---

## Phase 5 — Loader

### Prompt: Test Agent — Loader

```
You are the Test Agent for TAS Neo. Read workflow/agents/test-agent.md first.

Task: Write all failing tests for the adventure loader.

Design docs to read:
- docs/design/05-adventure-loader.md — AdventureLoader, JsonAdventureLoader, validation rules, JSON format

Use fixture JSON files in src/test/resources/ — one fixture per validation rule (valid case + one malformed case per rule). Do not construct Adventure objects programmatically in loader tests; use JSON fixtures.

Cover every validation rule in the design doc, including:
- All existing section/event/item/party member rules
- All grid validation rules added in the latest update (cell positions, no duplicate positions, passage targets, toSection references, toGrid/toCell resolution)

Place test class at src/test/java/com/tas/neo/loader/JsonAdventureLoaderTest.java.
Place fixture files at src/test/resources/adventures/.
```

---

### Prompt: Code Agent — Loader

```
You are the Code Agent for TAS Neo. Read workflow/agents/code-agent.md first.

Task: Implement the adventure loader. Make all loader tests compile and pass.

Failing tests to satisfy:
- src/test/java/com/tas/neo/loader/JsonAdventureLoaderTest.java
- Fixture files under src/test/resources/adventures/

Design docs to implement from:
- docs/design/05-adventure-loader.md — AdventureLoader interface, JsonAdventureLoader, AdventureLoadException, validation rules

Package: com.tas.neo.loader

Use Jackson for JSON parsing. JsonAdventureLoader receives the adventures/ root Path at construction. Validation runs at load time; all violations throw AdventureLoadException.

The grids array must be parsed and validated according to the rules in the design doc. InMemoryAdventureLoader is already written in src/test/ — do not rewrite it.

Do not touch src/test/.
```

---

## Phase 6 — Engine

### Prompt: Test Agent — Engine

```
You are the Test Agent for TAS Neo. Read workflow/agents/test-agent.md first.

Task: Write all failing tests for the engine layer.

Design docs to read:
- docs/design/04-engine.md — GameState, Game, HookDispatcher, system choices, character creation, section type handling
- docs/design/06-testability.md — full game loop test rows
- docs/design/11-location-networks.md — grid navigation test rows

Available test doubles: all doubles from Phase 1. Use InMemoryAdventureLoader for all engine tests — do not use JsonAdventureLoader.

Key contracts to test (all from the design doc test strategy tables):
- VICTORY section ends game
- INSTANT_DEATH ends game
- Player death mid-event (STAMINA → 0) ends game without showing choices
- System choices (inventory, quit) always injected
- Navigation follows choice selection
- Party member created at start, stats within expected range
- Grid entry via toGrid/toCell choice → state.isInGrid() true, correct cell
- Grid exit via toSection passage → state.isInGrid() false, correct section
- Cell events fire on cell entry
- ScenarioRunner with RecordingGameLogger: sessionLog().path() matches navigation sequence
- ScenarioRunner with RecordingGameLogger: broken script → hasErrors() true, source and type correct
- ScenarioRunner default (no logger override): no files written to sessions/

Place test classes in src/test/java/com/tas/neo/engine/.
```

---

### Prompt: Code Agent — Engine

```
You are the Code Agent for TAS Neo. Read workflow/agents/code-agent.md first.

Task: Implement the engine layer. Make all engine tests compile and pass.

Failing tests to satisfy:
- src/test/java/com/tas/neo/engine/

Design docs to implement from:
- docs/design/04-engine.md — GameState (including grid navigation: navigateToCell, currentGrid, currentCell, isInGrid), HookDispatcher, Game
- docs/design/07-scripting-engine.md — HookDispatcher (fireSectionHook, fireCellHook, fireAdventureHook, fireCombatHook, fireItemHook)
- docs/design/12-session-logging.md — engine integration section: Game receives GameLogger, HookDispatcher catches script errors and calls logError, PlayerSnapshot.of(state) factory

Package: com.tas.neo.engine

The engine layer may depend on all other layers. Wire up in Game.run():
1. Load adventure via AdventureLoader
2. Roll player stats (SKILL: 1d6+6, STAMINA: 2d6+12, LUCK: 1d6+6)
3. Resolve party member stats via StatDefinition + Dice
4. Fire ON_LOAD, then ON_START
5. Enter main game loop
6. Call logger.logNavigation() on every section/cell transition
7. Call logger.logEvent() for every OutputEvent produced
8. Call logger.logError() for every caught script or navigation error (do NOT rethrow — session continues)
9. Call logger.close() when the session ends (victory, game over, or unrecoverable fault)

Grid navigation: when a choice has a GridTarget, call state.navigateToCell(grid, cell) and process the cell (events → hooks → passage choices + explicit choices). When a passage has toSection, call state.navigateTo(section).

Game constructor signature:
  public Game(GameInput input, GameOutput output, AdventureLoader loader,
              Dice dice, ScriptEngine scriptEngine,
              CombatSystemRegistry combatRegistry, GameLogger logger)

Do not touch src/test/.
```

---

## Phase 7 — Integration smoke test

After Phase 6 passes, verify the full wiring compiles and runs:

### Prompt: Code Agent — Main wiring

```
You are the Code Agent for TAS Neo. Read workflow/agents/code-agent.md first.

Task: Write Main.java and verify the full system wires together.

Design doc to implement from:
- docs/design/01-architecture.md — Wiring (Main) section

Package: com.tas.neo (Main.java at root)

Write Main exactly as specified in the wiring section. Do not add any logic beyond what is shown there. Run the project against a minimal adventure JSON (or the fixture files from the loader tests) to confirm startup without exceptions.

If any wiring issue surfaces a design gap, report it as a blocker — do not invent a resolution.
```
