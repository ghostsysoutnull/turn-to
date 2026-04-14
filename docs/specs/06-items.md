# Spec: Items

## Overview

Items are objects the player can carry in their inventory. They are defined once at the adventure level and referenced by name throughout sections, events, and scripts. Each item has a category that determines how it can be used and which lifecycle hooks are relevant.

---

## Item Definition

| Field       | Type        | Description |
|-------------|-------------|-------------|
| name        | string      | Unique identifier and display name within the adventure |
| description | string      | Shown in the inventory screen |
| category    | ItemCategory| USABLE, EQUIPPABLE, KEY, or PASSIVE |
| countable   | boolean     | Whether the item has a meaningful quantity (arrows, torches, charges). Affects display only — mechanics are identical. |
| scripts     | ScriptBlock | Lifecycle hook scripts for this item |

---

## Item Categories

| Category    | Description |
|-------------|-------------|
| USABLE      | Can be used from the inventory; fires `onUse`. Scripts handle their own quantity reduction. |
| EQUIPPABLE  | Can be equipped or unequipped; fires `onEquip`, `onUnequip`, and `onCombatRound` while equipped. |
| KEY         | Narrative/gate items; not directly usable by the player but checked via `ctx.hasItem()` in scripts and conditions. |
| PASSIVE     | Always-active effect; fires `onCombatRound` every combat round regardless of equip state. |

---

## Inventory Rules

- The inventory has no capacity limit in the base ruleset.
- An item is identified by its **name** — names must be unique within an adventure.
- All items are tracked with a **quantity** (minimum 0). An item with quantity 0 is not present in the inventory.
- `ctx.addItem(name)` adds 1 unit. `ctx.addItem(name, quantity)` adds the specified amount.
- `ctx.removeItem(name)` removes 1 unit. `ctx.removeItem(name, quantity)` removes the specified amount.
- Attempting to remove more units than present removes all remaining units and fires `onDrop`.
- `ctx.getItemCount(name)` returns the current quantity (0 if not carried).

---

## Quantity and Display

| `countable` | Quantity | Displayed as |
|-------------|----------|--------------|
| `false`     | 1        | `Magic Sword` |
| `false`     | 3        | `Magic Sword x3` |
| `true`      | 1        | `Arrow x1` |
| `true`      | 20       | `Arrow x20` |

`countable` is a display hint. An item marked `countable: true` always shows its quantity. An item marked `countable: false` omits the quantity when it is 1.

---

## `onPickup` and `onDrop` Firing Rules

| Hook       | Fires when |
|------------|-----------|
| `onPickup` | Every time `ctx.addItem` is called for this item, regardless of quantity |
| `onDrop`   | Only when the item's quantity reaches **0** — i.e. the last unit leaves the inventory |

This means: picking up 5 arrows fires `onPickup` once. Removing 3 of 5 arrows does not fire `onDrop`. Removing the last 2 fires `onDrop` once.

---

## Equipment

- Only **EQUIPPABLE** items can be equipped.
- There is no slot system in the base ruleset; any number of items may be equipped simultaneously.
- Equipped state persists across sections.
- `onEquip` fires when the player equips the item.
- `onUnequip` fires when the player unequips it.
- `onCombatRound` fires once per combat round for each equipped item.

---

## Item Lifecycle Hooks

See **Spec: Scripting** for the full `ctx` API available in each hook.

| Hook            | When it fires | Notes |
|-----------------|---------------|-------|
| `onPickup`      | Every `ctx.addItem` call for this item | Fires once per call, not per unit |
| `onDrop`        | When quantity reaches 0 | Can refuse by re-adding the item |
| `onUse`         | Player selects "Use" from inventory | Only available for USABLE items |
| `onEquip`       | Player equips the item | Only available for EQUIPPABLE items |
| `onUnequip`     | Player unequips the item | Only available for EQUIPPABLE items |
| `onCombatRound` | Each round of combat | Available for EQUIPPABLE (if equipped) and PASSIVE items |

---

## Inventory Screen

When the player opens the inventory:

- All carried items are listed with name and quantity (per display rules above).
- USABLE items show a **[Use]** option.
- EQUIPPABLE items show **[Equip]** or **[Unequip]** depending on current state.
- KEY and PASSIVE items show no action button — they are informational.

---

## Examples

### Healing Potion (USABLE, not countable)
```json
{
  "name": "Healing Potion",
  "description": "A small vial of red liquid. Restores 6 STAMINA when drunk.",
  "category": "USABLE",
  "countable": false,
  "scripts": {
    "onUse": "ctx.modifyStat('STAMINA', 6); ctx.showMessage('You feel restored.'); ctx.removeItem('Healing Potion')"
  }
}
```
Picked up one at a time. Display: `Healing Potion x3` when carrying 3.

### Arrow (USABLE, countable)
```json
{
  "name": "Arrow",
  "description": "A straight-fletched arrow. Fired from a bow.",
  "category": "USABLE",
  "countable": true,
  "scripts": {
    "onUse": "ctx.removeItem('Arrow'); ctx.showMessage('You fire an arrow.')"
  }
}
```
Added in bulk: `ctx.addItem('Arrow', 20)`. Display: `Arrow x20`.

### Torch (USABLE, countable)
```json
{
  "name": "Torch",
  "description": "A tar-soaked torch. Each use burns for one section.",
  "category": "USABLE",
  "countable": true,
  "scripts": {
    "onUse": "ctx.removeItem('Torch'); state.set('torchLit', true); ctx.showMessage('You light a torch.')",
    "onDrop": "state.set('torchLit', false); ctx.showMessage('Your last torch goes out.')"
  }
}
```

### Magic Sword (EQUIPPABLE)
```json
{
  "name": "Magic Sword",
  "description": "A gleaming blade that hums with power. Adds 2 to your SKILL while equipped.",
  "category": "EQUIPPABLE",
  "countable": false,
  "scripts": {
    "onEquip":   "ctx.modifyStat('SKILL', 2); ctx.showMessage('You feel stronger.')",
    "onUnequip": "ctx.modifyStat('SKILL', -2); ctx.showMessage('Your confidence fades.')"
  }
}
```

### Iron Key (KEY)
```json
{
  "name": "Iron Key",
  "description": "A heavy iron key engraved with a serpent.",
  "category": "KEY",
  "countable": false,
  "scripts": {}
}
```

### Cursed Amulet (PASSIVE)
```json
{
  "name": "Cursed Amulet",
  "description": "A dark amulet you cannot bring yourself to discard.",
  "category": "PASSIVE",
  "countable": false,
  "scripts": {
    "onDrop":        "ctx.addItem('Cursed Amulet'); ctx.showMessage('You cannot bring yourself to leave it behind.')",
    "onCombatRound": "ctx.modifyStat('STAMINA', -1); ctx.showMessage('The amulet burns against your skin.')"
  }
}
```
