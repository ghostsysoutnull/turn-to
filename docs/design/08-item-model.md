# Design: Item Model

## Domain Objects

### Item

```java
public class Item {
    private final String name;
    private final String description;
    private final ItemCategory category;
    private final ScriptBlock scripts;
    private boolean equipped;   // only relevant for EQUIPPABLE items

    public boolean isEquipped();
    public void setEquipped(boolean equipped);
    public boolean canBeUsed();    // category == USABLE
    public boolean canBeEquipped();// category == EQUIPPABLE
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
        return name().toLowerCase().replace('_', '');  // "onPickup", "onCombatRound", etc.
    }
}
```

---

## Inventory

`Inventory` is owned by `Player` and manages item quantities:

```java
public class Inventory {
    private final Map<String, ItemStack> stacks = new LinkedHashMap<>();

    public void add(Item item);
    public boolean remove(String itemName);        // removes one; returns false if not present
    public boolean has(String itemName);
    public int count(String itemName);
    public List<Item> allItems();                  // one entry per unique name
    public List<Item> equippedItems();
    public List<Item> passiveItems();
}
```

`ItemStack` is a private record holding `Item` definition and `int quantity`.

`LinkedHashMap` preserves insertion order for deterministic inventory display and `onCombatRound` firing order.

---

## Item Resolution

Items are **defined** in the adventure's item list and **referenced** by name everywhere else (events, scripts, conditions). The loader resolves names to `Item` definitions at load time — a reference to an unknown item name is a load-time error.

```java
// In JsonAdventureLoader validation:
adventure.sections().forEach(section ->
    section.events().stream()
        .filter(e -> e instanceof ItemEvent)
        .map(e -> (ItemEvent) e)
        .forEach(e -> {
            if (!adventure.hasItem(e.itemName()))
                throw new AdventureLoadException("Unknown item: " + e.itemName());
        })
);
```

---

## Integration with CombatEngine

At the start of each combat round, `HookDispatcher.fireItemHook` is called for:
1. All **EQUIPPABLE** items that are currently equipped
2. All **PASSIVE** items in the inventory

```java
// Inside CombatEngine, after resolving the round outcome:
for (Item item : player.getInventory().equippedItems()) {
    dispatcher.fireItemHook(ItemScriptHook.ON_COMBAT_ROUND, item, round);
}
for (Item item : player.getInventory().passiveItems()) {
    dispatcher.fireItemHook(ItemScriptHook.ON_COMBAT_ROUND, item, round);
}
```

`CombatEngine` receives `HookDispatcher` as a constructor dependency, keeping it testable via `NoOpScriptEngine` in combat-focused unit tests.

---

## Inventory Screen Rendering

`GameOutput` gains one method:

```java
void showInventory(List<Item> items, int gold, int provisions);
```

The engine calls this when the player selects "Check inventory" from the system choices. The terminal implementation renders:

```
INVENTORY
─────────────────────────────────────
  Healing Potion    x2    [Use]
  Magic Sword       x1    [Unequip]   ← currently equipped
  Iron Key          x1
  Cursed Amulet     x1
─────────────────────────────────────
  Gold: 15    Provisions: 3
```

`TerminalInput.readInventoryAction` returns the player's selection. For tests, `ScriptedInput` extends to support inventory selections.

---

## Test Strategy

| What | Approach |
|------|----------|
| `Item` equip state | Plain unit test; no dependencies |
| `Inventory` add/remove/count | Plain unit test |
| Item `onUse` script | `LuaScriptEngine` + `RecordingScriptContext` |
| `onCombatRound` passive effect | `FixedDice` combat + `LuaScriptEngine` + assert player stat after round |
| Non-droppable item (`onDrop` refuses) | Script test verifying item re-added and message shown |
| Loader rejects unknown item reference | Fixture JSON with bad item name → assert `AdventureLoadException` |
