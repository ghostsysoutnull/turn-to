# Spec: Scripting System

## Overview

The scripting system allows adventure authors to attach behaviour to well-defined **lifecycle hook points** in the game engine. Scripts are written in Lua and are stored inline in the adventure JSON file. The engine owns the hook points — scripts are passengers, not drivers.

Scripts complement the structured event system. Structured events handle simple, predictable effects. Scripts handle conditional, dynamic, or composable logic that cannot be expressed as data alone.

---

## Scripting Language

**Lua** is the embedded scripting language. It was designed for game embedding: small footprint, simple syntax, sandboxable, and widely used in the game industry.

Scripts interact with the game exclusively through a **ScriptContext** object (`ctx`) and an **AdventureState** object (`state`). Direct access to Java objects is not available.

---

## ScriptContext API

Every script receives two globals: `ctx` and `state`.

### `ctx` — game interaction

| Method | Description |
|--------|-------------|
| `ctx.modifyStat(attr, delta)` | Modify SKILL, STAMINA, or LUCK by delta (clamped to valid range) |
| `ctx.getStat(attr)` | Return current value of an attribute |
| `ctx.modifyGold(delta)` | Add or subtract gold |
| `ctx.getGold()` | Return current gold |
| `ctx.addItem(name)` | Add named item to inventory |
| `ctx.removeItem(name)` | Remove named item from inventory |
| `ctx.hasItem(name)` | Return true if player carries the item |
| `ctx.navigateTo(section)` | Navigate to a section (valid in `onEnter`, `onExit`, `onCombatEnd`) |
| `ctx.currentSection()` | Return current section number |
| `ctx.showMessage(text)` | Display a message to the player |
| `ctx.addChoice(text, section)` | Add a choice to the current list (`onChoices` hook only) |
| `ctx.removeChoice(text)` | Remove a choice by its text (`onChoices` hook only) |

### `state` — adventure-scoped persistent state

Key/value store scoped to the current adventure run. Values persist across sections.

| Method | Description |
|--------|-------------|
| `state.set(key, value)` | Store a value (string, number, boolean) |
| `state.get(key)` | Retrieve a stored value (nil if not set) |
| `state.has(key)` | Return true if key has been set |

---

## Lifecycle Hook Points

### Adventure Hooks

Defined in the adventure's top-level `scripts` block.

| Hook        | `ctx.navigateTo` allowed | Typical use |
|-------------|--------------------------|-------------|
| `onLoad`    | No                       | Initialise `state` variables |
| `onStart`   | No                       | Show opening messages, set flags |
| `onVictory` | No                       | Custom win message, final stats display |
| `onGameOver`| No                       | Custom death message |

### Section Hooks

Defined per section in the section's `scripts` block.

| Hook        | `ctx.navigateTo` allowed | Typical use |
|-------------|--------------------------|-------------|
| `onEnter`   | Yes                      | Conditional navigation, passive item effects, scene setup |
| `onDisplay` | No                       | Dynamic narrative text based on state |
| `onChoices` | No                       | Add/remove choices based on inventory or state |
| `onExit`    | Yes                      | Trigger effects when player leaves; set state flags |

### Combat Hooks

Defined within a `CombatEvent` in the section's event list.

| Hook            | Typical use |
|-----------------|-------------|
| `onCombatStart` | Flavour message, pre-combat effects |
| `onRoundStart`  | Creature special abilities that trigger before attack |
| `onRoundEnd`    | Poison, regeneration, fear effects after each round |
| `onCombatEnd`   | Loot, navigation after victory, conditional outcomes |

### Item Hooks

Defined per item in the adventure's `items` list.

| Hook             | Fires when |
|------------------|-----------|
| `onPickup`       | Item added to inventory |
| `onDrop`         | Item removed from inventory |
| `onUse`          | Player selects "Use" from inventory |
| `onEquip`        | Player equips the item |
| `onUnequip`      | Player unequips the item |
| `onCombatRound`  | Each combat round while item is equipped (passive effects) |

---

## Script Isolation Rules

- Scripts **cannot** import Lua modules or access the filesystem.
- Scripts **cannot** call Java classes directly.
- Scripts **cannot** define new hook points or call other scripts by name.
- Scripts **can** read and write `state` to share data across hook points.
- A script that throws a Lua error logs the error and **does not crash the game** — the hook is skipped.

---

## Script Execution Order

When multiple hooks fire at the same point (e.g. section `onEnter` and an equipped item's `onCombatRound`), execution order is:

1. Structured events (in order)
2. Section lifecycle script
3. Active item scripts (in inventory order)

---

## Example Scripts

```lua
-- Adventure onLoad: initialise state
state.set('bridgeCrossed', false)
state.set('keyCount', 0)

-- Section onEnter: conditional navigation
if not ctx.hasItem('Lantern') and state.get('caveEntered') then
    ctx.showMessage('Without light, you stumble and fall into a pit.')
    ctx.navigateTo(199)
end

-- Section onChoices: dynamic choice injection
if state.get('bridgeCrossed') and ctx.hasItem('Iron Key') then
    ctx.addChoice('Unlock the iron gate', 312)
end

-- Item onUse: healing potion
ctx.modifyStat('STAMINA', 6)
ctx.showMessage('You drink the potion. Warmth spreads through your body.')
ctx.removeItem('Healing Potion')

-- Item onCombatRound: cursed amulet passive damage
ctx.modifyStat('STAMINA', -1)
ctx.showMessage('The cursed amulet burns against your skin.')
```
