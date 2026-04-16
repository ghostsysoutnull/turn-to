# TAS Neo

A text-based terminal adventure game inspired by the Fighting Fantasy gamebook series.

## Documentation

### Specifications

| Document | Description |
|----------|-------------|
| [01 - Game Mechanics](docs/specs/01-game-mechanics.md) | Core rules: attributes, dice, luck tests, provisions |
| [02 - Combat](docs/specs/02-combat.md) | Turn-based combat system |
| [03 - Adventure Structure](docs/specs/03-adventure-structure.md) | Sections, choices, events, conditions, lifecycle hooks |
| [04 - Terminal UI](docs/specs/04-terminal-ui.md) | Screen layout, input/output behaviour |
| [05 - Scripting](docs/specs/05-scripting.md) | Lua scripting system, lifecycle hook points, ScriptContext API |
| [06 - Items](docs/specs/06-items.md) | Item categories, inventory rules, item lifecycle hooks |
| [07 - Party Members](docs/specs/07-party-members.md) | Configurable companion entities: ships, companions, crews — fixed and dice-formula stats |
| [08 - Adventure Authoring](docs/specs/08-adventure-authoring.md) | Chapters, gates, briefs, and the adventure manifest |
| [09 - Location Networks](docs/specs/09-location-networks.md) | Location graph, travel events, region-based navigation |
| [10 - Session Logging](docs/specs/10-session-logging.md) | Session log format, event recording, replay |
| [11 - Adventure Runner](docs/specs/11-adventure-runner.md) | Automated simulation: strategies, coverage, report format |

### Technical Design

| Document | Description |
|----------|-------------|
| [01 - Architecture](docs/design/01-architecture.md) | Layer overview, package structure, key interfaces |
| [02 - Domain Model](docs/design/02-domain-model.md) | Player, Section, Event, Creature class designs |
| [03 - Combat Engine](docs/design/03-combat-engine.md) | CombatEngine, LuckTest, SkillTest design |
| [04 - Engine](docs/design/04-engine.md) | Game loop, HookDispatcher, GameState |
| [05 - Adventure Loader](docs/design/05-adventure-loader.md) | JSON format, validation rules |
| [06 - Testability](docs/design/06-testability.md) | Test doubles, test strategy per layer |
| [07 - Scripting Engine](docs/design/07-scripting-engine.md) | LuaJ integration, HookDispatcher, ScriptContext, sandboxing |
| [08 - Item Model](docs/design/08-item-model.md) | Item, Inventory, equip system, combat integration |
| [09 - Party Member Model](docs/design/09-party-member-model.md) | PartyMember, StatDefinition, DiceFormula, defeat consequences |
| [10 - Combat Systems](docs/design/10-combat-systems.md) | Pluggable CombatSystem interface, registry, PersonalCombatSystem, system composition |
| [11 - Location Networks](docs/design/11-location-networks.md) | Location graph, travel event types, region data model |
| [12 - Session Logging](docs/design/12-session-logging.md) | SessionLogger interface, log record types, replay strategy |
| [13 - OO Design Guidelines](docs/design/13-oo-design-guidelines.md) | Refactoring decision framework: thresholds, patterns, TDD sequence |

### Adventure Analysis Tools

| Tool | Purpose |
|------|---------|
| `AdventureReportGenerator` | Structural summary: reachability, section types, chapter coverage |
| `AdventureStateReport` | State variable map: where each variable is set, read, checked, removed |
| `AdventureItemReport` | Item lifecycle: GAIN/LOSS/REQUIRED/FORBIDDEN per item |
| `AdventureGateDigest` | Gate contracts for all chapters in one view |
| `AdventureSectionDigest` | Compact per-chapter narrative digest |
| `AdventureSectionInspector` | Targeted queries: read sections, inbound refs, event/condition search, dead ends, state var focus, gate JSON |
| `AdventureRunner` | Batch simulation: N runs with configurable strategy, coverage report, unreached sections, never-selected choices |
| `GamePlaybackRunner` | Terminal UI verification: runs a planned section path through real `TerminalOutput`, writes captured output to file |

## Agent Workflow

### Development pipeline

Spec → Design → Test → Code. See [`workflow/WORKFLOW.md`](workflow/WORKFLOW.md) for the full process.

| Agent | Role | Definition |
|-------|------|------------|
| Spec | Writes and validates specs | [`workflow/agents/spec-agent.md`](workflow/agents/spec-agent.md) |
| Design | Writes technical design | [`workflow/agents/design-agent.md`](workflow/agents/design-agent.md) |
| Test | Writes failing tests from design strategy tables | [`workflow/agents/test-agent.md`](workflow/agents/test-agent.md) |
| Code | Implements to make tests pass | [`workflow/agents/code-agent.md`](workflow/agents/code-agent.md) |

### Adventure authoring pipeline

See [`workflow/ADVENTURE-AUTHORING-PIPELINE.md`](workflow/ADVENTURE-AUTHORING-PIPELINE.md) for the full process.

| Agent | Role | Definition |
|-------|------|------------|
| Adventure Architect | Designs chapter scaffold, manifest, gate contracts, and chapter briefs | [`workflow/agents/adventure-architect-agent.md`](workflow/agents/adventure-architect-agent.md) |
| Adventure Reviewer | Reviews scaffold before authoring begins | [`workflow/agents/adventure-reviewer-agent.md`](workflow/agents/adventure-reviewer-agent.md) |
| Adventure Author | Authors one chapter per invocation | [`workflow/agents/adventure-author-agent.md`](workflow/agents/adventure-author-agent.md) |
| Chapter Reviewer | Reviews each chapter immediately after authoring | [`workflow/agents/chapter-reviewer-agent.md`](workflow/agents/chapter-reviewer-agent.md) |
| Consistency Check | Cross-chapter chain integrity after all chapters are approved | [`workflow/agents/consistency-check-agent.md`](workflow/agents/consistency-check-agent.md) |

## Tech Stack

- Java 21
- Maven
- JUnit 5 + AssertJ
