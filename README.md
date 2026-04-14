# TAS Neo

A text-based terminal adventure game inspired by the Fighting Fantasy gamebook series.

## Documentation

### Specifications

| Document | Description |
|----------|-------------|
| [01 - Game Mechanics](docs/specs/01-game-mechanics.md) | Core rules: attributes, dice, luck tests, provisions |
| [02 - Combat](docs/specs/02-combat.md) | Turn-based combat system |
| [03 - Adventure Structure](docs/specs/03-adventure-structure.md) | Sections, choices, events, conditions |
| [04 - Terminal UI](docs/specs/04-terminal-ui.md) | Screen layout, input/output behaviour |

### Technical Design

| Document | Description |
|----------|-------------|
| [01 - Architecture](docs/design/01-architecture.md) | Layer overview, package structure, key interfaces |
| [02 - Domain Model](docs/design/02-domain-model.md) | Player, Section, Event, Creature class designs |
| [03 - Combat Engine](docs/design/03-combat-engine.md) | CombatEngine, LuckTest, SkillTest design |
| [04 - Engine](docs/design/04-engine.md) | Game loop, EventProcessor, GameState |
| [05 - Adventure Loader](docs/design/05-adventure-loader.md) | JSON format, validation rules |
| [06 - Testability](docs/design/06-testability.md) | Test doubles, test strategy per layer |

## Tech Stack

- Java (latest)
- Maven
- JUnit 5 + AssertJ
