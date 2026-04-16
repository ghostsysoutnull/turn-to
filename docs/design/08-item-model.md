# Design: Item Model

## Item

```java
public record Item(String name, String description, ItemCategory category,
                   boolean countable, ScriptBlock scripts) {
    public boolean canBeUsed();
    public boolean canBeEquipped();
}
```

`Item` is a fully immutable record. Equip state is not an item definition concern — it is tracked in `Inventory` as a `Set<String>` of equipped item names. `canBeUsed()` and `canBeEquipped()` are convenience methods derived from `category`.

---

## ItemCategory

```java
public enum ItemCategory {
    USABLE,      // onUse hook; player can invoke from inventory
    EQUIPPABLE,  // onEquip/onUnequip hooks; onCombatRound fires while equipped
    KEY,         // no action; used as a flag via ctx.hasItem()
    PASSIVE      // onCombatRound always fires regardless of equip state
}
```

---

## ItemScriptHook

```java
public enum ItemScriptHook {
    ON_PICKUP, ON_DROP, ON_USE, ON_EQUIP, ON_UNEQUIP, ON_COMBAT_ROUND;
    public String hookName();
}
```

`hookName()` converts the enum constant to camelCase (e.g. `ON_COMBAT_ROUND` → `"onCombatRound"`).

---

## Inventory

```java
public class Inventory {
    public void add(Item item);
    public void add(Item item, int quantity);
    public int remove(String itemName);
    public int remove(String itemName, int quantity);
    public boolean has(String itemName);
    public int count(String itemName);
    public void equip(String itemName);
    public void unequip(String itemName);
    public boolean isEquipped(String itemName);
    public List<ItemStack> allStacks();
    public List<Item> equippedItems();
    public List<Item> passiveItems();
}
```

`remove` returns the number of units actually removed (may be less than requested if stock is insufficient). The caller uses this return value to decide whether to fire `onDrop`. When quantity reaches 0, the map entry is removed — `has()` returns false and the item no longer appears in `allStacks()`.

`equip`/`unequip` are no-ops if the item is not in the inventory. `isEquipped` returns false for unknown items. `equippedItems()` returns items whose names are in the equipped set and whose category is `EQUIPPABLE`.

`LinkedHashMap` preserves insertion order for deterministic display and `onCombatRound` firing order.

---

## ItemStack

```java
public record ItemStack(Item item, int quantity) {
    public String displayName();
}
```

`displayName()` respects `Item.isCountable()`: always appends `" xN"` for countable items; omits it when quantity is 1 for non-countable items.

---

## `onDrop` Firing Rule

`onDrop` fires only when the stack quantity reaches 0 — the last unit leaves the inventory. Removing N of M units where N < M does not fire `onDrop`.

`onPickup` fires on every `addItem` call regardless of quantity added.

Both rules are enforced in `DefaultScriptContext`, not in `Inventory` itself.

---

## Combat Integration

`HookDispatcher` fires `ON_COMBAT_ROUND` after each round for:
1. All EQUIPPABLE items currently equipped
2. All PASSIVE items in the inventory

The firing order follows inventory insertion order.

---

## Inventory Screen

```java
// GameOutput addition
void showInventory(List<ItemStack> stacks, int gold, int provisions);
```

Terminal rendering example:

```
INVENTORY
─────────────────────────────────────
  Healing Potion x3    [Use]
  Arrow x20            [Use]
  Torch x5             [Use]
  Magic Sword          [Unequip]
  Iron Key
  Cursed Amulet
─────────────────────────────────────
  Gold: 15    Provisions: 3
```

---

## Test Strategy

| Layer | What | Approach |
|-------|------|----------|
| unit | `Inventory` add/remove quantity | Plain unit test; assert `count()` after each operation |
| unit | `onDrop` fires only at 0 | Remove N-1 units, assert not fired; remove last, assert fired once |
| unit | `onPickup` fires per call | `addItem('Arrow', 10)` → assert fired once, not ten times |
| unit | `remove` capped at stock | Remove 10 of 5 → assert 5 returned, `onDrop` fired |
| unit | `ItemStack.displayName` | Unit test both `countable` values at quantity 1 and > 1 |
| unit | Cursed amulet refuses drop | `onDrop` script re-adds item; assert count still 1 after removal |
| unit | `onCombatRound` firing order | Two passive items; assert hooks fired in insertion order |
