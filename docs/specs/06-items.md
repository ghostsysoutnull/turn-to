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
| scripts     | ScriptBlock | Lifecycle hook scripts for this item |

---

## Item Categories

| Category    | Description |
|-------------|-------------|
| USABLE      | Can be used from the inventory; fires `onUse`. Single-use items remove themselves in their `onUse` script. |
| EQUIPPABLE  | Can be equipped or unequipped; fires `onEquip`, `onUnequip`, and `onCombatRound` while equipped. |
| KEY         | Narrative/gate items; not directly usable by the player but checked via `ctx.hasItem()` in scripts and conditions. |
| PASSIVE     | Always-active effect; fires `onCombatRound` every combat round regardless of equip state. |

---

## Inventory Rules

- The inventory has no capacity limit in the base ruleset.
- An item is identified by its **name** — names must be unique within an adventure.
- The same item name may appear **multiple times** in the inventory (stacking). Quantities are tracked as a count.
- `ctx.removeItem(name)` removes one instance. Removing the last instance removes it from the inventory entirely.
- Some items are **non-droppable** — the `onDrop` hook can call `ctx.addItem(name)` to refuse the drop and show a message.

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
| `onPickup`      | Item added to inventory by any means (event or script) | |
| `onDrop`        | Item removed from inventory by any means | Can refuse removal by re-adding the item |
| `onUse`         | Player selects "Use" from inventory | Only available for USABLE items |
| `onEquip`       | Player equips the item | Only available for EQUIPPABLE items |
| `onUnequip`     | Player unequips the item | Only available for EQUIPPABLE items |
| `onCombatRound` | Each round of combat | Available for EQUIPPABLE (if equipped) and PASSIVE items |

---

## Inventory Screen

When the player opens the inventory:

- All carried items are listed with name, description, and quantity.
- USABLE items show a **[Use]** option.
- EQUIPPABLE items show **[Equip]** or **[Unequip]** depending on current state.
- KEY and PASSIVE items show no action button — they are informational.

---

## Examples

### Healing Potion (USABLE)
```json
{
  "name": "Healing Potion",
  "description": "A small vial of red liquid. Restores 6 STAMINA when drunk.",
  "category": "USABLE",
  "scripts": {
    "onUse": "ctx.modifyStat('STAMINA', 6); ctx.showMessage('You feel restored.'); ctx.removeItem('Healing Potion')"
  }
}
```

### Magic Sword (EQUIPPABLE)
```json
{
  "name": "Magic Sword",
  "description": "A gleaming blade that hums with power. Adds 2 to your SKILL while equipped.",
  "category": "EQUIPPABLE",
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
  "scripts": {}
}
```

### Cursed Amulet (PASSIVE)
```json
{
  "name": "Cursed Amulet",
  "description": "A dark amulet you cannot bring yourself to discard.",
  "category": "PASSIVE",
  "scripts": {
    "onDrop":        "ctx.addItem('Cursed Amulet'); ctx.showMessage('You cannot bring yourself to leave it behind.')",
    "onCombatRound": "ctx.modifyStat('STAMINA', -1); ctx.showMessage('The amulet burns against your skin.')"
  }
}
```
