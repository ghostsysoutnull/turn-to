# Spec: Terminal UI

## Overview

The game runs entirely in a text terminal. The UI has two concerns: **output** (what is displayed to the player) and **input** (how the player makes choices). Both must be fully replaceable for testing purposes.

---

## Screen Layout

The terminal is divided into logical areas rendered on each section transition:

```
┌─────────────────────────────────────────────────────┐
│  [ STATUS BAR ]                                     │
│  SKILL: 10   STAMINA: 18/20   LUCK: 8   Gold: 5    │
├─────────────────────────────────────────────────────┤
│                                                     │
│  [ SECTION NARRATIVE ]                              │
│  You stand before a dark cave. The stench of        │
│  something rotten drifts toward you...              │
│                                                     │
├─────────────────────────────────────────────────────┤
│  [ CHOICES ]                                        │
│  [1] Enter the cave                                 │
│  [2] Turn back and head north                       │
│  [3] Eat a provision (Provisions: 3)                │
└─────────────────────────────────────────────────────┘
```

---

## Output Behaviours

| Situation               | Output |
|-------------------------|--------|
| Section entered         | Clear screen, show status bar, show narrative, show choices |
| Event fires             | Print event result below narrative before choices appear |
| Combat round            | Print round summary: attack strengths, dice, outcome |
| Combat ends             | Print win/loss message; if win, continue to choices |
| Luck test               | Print roll, result (Lucky/Unlucky), new LUCK score |
| Stat change             | Print "You gain/lose X ATTRIBUTE" |
| Item gained/lost        | Print "You pick up / drop [item name]" |
| Game over (death)       | Print death message, final stats, "YOUR ADVENTURE ENDS HERE" |
| Victory                 | Print victory narrative, "CONGRATULATIONS" |

---

## Input Behaviours

| Situation         | Input expected |
|-------------------|----------------|
| Choices presented | Player types the number of their choice and presses Enter |
| Invalid input     | Print "Invalid choice. Try again." and re-prompt |
| Test Luck prompt  | Player types Y or N (case-insensitive) |
| Press any key     | Player presses Enter to continue (used after long combat sequences) |

---

## Persistent UI Options

The following options are always available regardless of section:

| Option | Trigger | Behaviour |
|--------|---------|-----------|
| Eat provision | Listed as a choice when provisions > 0 | Consumes 1 provision, restores 4 STAMINA (up to max) |
| View inventory | Always available as a choice | Lists current items and gold |
| Quit | Always available as a choice | Prompts confirmation, exits if confirmed |

These are injected into the choice list by the game engine, not authored per section.

---

## Display Rules

- Section narrative is displayed **word-wrapped** to terminal width.
- Narrative supports **paragraph breaks** (blank lines between paragraphs).
- Combat is shown as a sequence of round summaries; a "Press Enter to continue" pause is inserted after every **5 rounds**.
- The status bar is shown at the top of **every** screen refresh.
- Text is output in plain ASCII; no ANSI colour is required in v1.
