# Design: Item Model

## Domain Objects

### Item

```java
public class Item {
    private final String name;
    private final String description;
    private final ItemCategory category;
    private final boolean countable;       // display hint: always show quantity
    private final ScriptBlock scripts;
    private boolean equipped;              // only relevant for EQUIPPABLE items

    public boolean isEquipped();
    public void setEquipped(boolean equipped);
    public boolean canBeUsed();            // category == USABLE
    public boolean canBeEquipped();        // category == EQUIPPABLE
    public boolean isCountable();
}
```

`Item` is not a record because it carries mutable equip state. All other fields are final.

---

### ItemCategory

```java
public enum ItemCategory {
    USABLE,      // has onUse hook; player can invoke from inventory
    EQUIPPABLE,  // has onEquip/onUnequip hooks; onCombatRound fires while equipped
    KEY,         // no action; used as a flag via hasItem()
    PASSIVE      // onCombatRound always fires; onDrop can refuse removal
}
```

---

### ItemScriptHook

```java
public enum ItemScriptHook {
    ON_PICKUP,
    ON_DROP,
    ON_USE,
    ON_EQUIP,
    ON_UNEQUIP,
    ON_COMBAT_ROUND;

    public String hookName() {
        // "ON_COMBAT_ROUND" → "onCombatRound"
        String[] parts = name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++)
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        return sb.toString();
    }
}
```

---

## Inventory

`Inventory` is owned by `Player` and manages item quantities via `ItemStack`:

```java
public class Inventory {
    private final Map<String, ItemStack> stacks = new LinkedHashMap<>();

    public void add(Item item, int quantity);
    public void add(Item item);                    // delegates to add(item, 1)

    /**
     * Removes up to `quantity` units.
     * Returns the number actually removed (may be less if stock is insufficient).
     * Callers use the return value to decide whether onDrop should fire.
     */
    public int remove(String itemName, int quantity);
    public int remove(String itemName);            // delegates to remove(name, 1)

    public boolean has(String itemName);
    public int count(String itemName);
    public List<ItemStack> allStacks();
    public List<Item> equippedItems();
    public List<Item> passiveItems();
}
```

### ItemStack

```java
public record ItemStack(Item item, int quantity) {
    public ItemStack add(int delta) {
        return new ItemStack(item, quantity + delta);
    }
    public ItemStack remove(int delta) {
        return new ItemStack(item, Math.max(0, quantity - delta));
    }
    public boolean isEmpty() {
        return quantity == 0;
    }
    public String displayName() {
        if (item.isCountable() || quantity > 1)
            return item.name() + " x" + quantity;
        return item.name();
    }
}
```

`LinkedHashMap` preserves insertion order for deterministic inventory display and `onCombatRound` firing order.

---

## `onDrop` Firing Logic

`onDrop` fires only when the stack reaches 0 — the last unit leaves the inventory. This is enforced in `DefaultScriptContext.removeItem`:

```java
@Override
public void removeItem(String itemName, int quantity) {
    int removed = player.getInventory().remove(itemName, quantity);
    if (removed > 0 && player.getInventory().count(itemName) == 0) {
        Item item = adventure.getItem(itemName);
        dispatcher.fireItemHook(ItemScriptHook.ON_DROP, item);
    }
}
```

`onPickup` fires on every `addItem` call regardless of quantity:

```java
@Override
public void addItem(String itemName, int quantity) {
    Item item = adventure.getItem(itemName);
    player.getInventory().add(item, quantity);
    dispatcher.fireItemHook(ItemScriptHook.ON_PICKUP, item);
}
```

---

## ScriptContext — Quantity-Aware API

```java
public interface ScriptContext {
    // existing methods ...

    void addItem(String itemName);                  // adds 1
    void addItem(String itemName, int quantity);    // adds quantity
    void removeItem(String itemName);               // removes 1
    void removeItem(String itemName, int quantity); // removes quantity (capped at current stock)
    boolean hasItem(String itemName);               // true if count > 0
    int getItemCount(String itemName);              // current quantity (0 if absent)
}
```

The quantity-less overloads delegate to the quantity variants with `1`. Lua sees all four methods via LuaJ's Java binding — calling `ctx.addItem('Arrow', 10)` and `ctx.addItem('Arrow')` both work.

---

## ITEM_GAIN Event — Quantity Support

The structured `ITEM_GAIN` event also supports quantity:

```json
{ "type": "ITEM_GAIN", "itemName": "Arrow", "quantity": 20 }
```

`quantity` defaults to 1 if omitted. The `ItemEvent` record gains an optional quantity field:

```java
public record ItemEvent(String itemName, ItemAction action, int quantity) implements SectionEvent {
    public ItemEvent(String itemName, ItemAction action) {
        this(itemName, action, 1);
    }
}
```

---

## Item Resolution

Items are **defined** in the adventure's item list and **referenced** by name everywhere else. The loader resolves names to `Item` definitions at load time — a reference to an unknown item name is a load-time error.

---

## Integration with CombatEngine

At each combat round, `HookDispatcher.fireItemHook` is called for all equipped and passive items. Item quantity does not affect combat hook firing — an item fires `onCombatRound` as long as it is present (quantity ≥ 1).

---

## Inventory Screen Rendering

```java
void showInventory(List<ItemStack> stacks, int gold, int provisions);
```

The terminal implementation renders:

```
INVENTORY
─────────────────────────────────────
  Healing Potion x3    [Use]
  Arrow x20            [Use]
  Torch x5             [Use]
  Magic Sword          [Unequip]   ← currently equipped
  Iron Key
  Cursed Amulet
─────────────────────────────────────
  Gold: 15    Provisions: 3
```

---

## Test Strategy

| What | Approach |
|------|----------|
| `Inventory` add/remove quantity | Plain unit test; assert `count()` after each operation |
| `onDrop` fires only at 0 | Remove N-1 units, assert not fired; remove last unit, assert fired |
| `onPickup` fires per call, not per unit | `addItem('Arrow', 10)` → assert fired once |
| `removeItem` capped at stock | Remove 10 of 5 → assert 5 removed, `onDrop` fired |
| `ITEM_GAIN` with quantity | Loader test: section event adds 20 arrows → inventory count 20 |
| `countable` display | `ItemStack.displayName()` unit test for both `true` and `false` cases |
| Cursed amulet refuses drop | Script test: `onDrop` re-adds item; assert count still 1 after removal attempt |
