package com.tas.neo.domain.item;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Inventory {

    private final Map<String, ItemStack> stacks = new LinkedHashMap<>();
    private final Set<String> equipped = new HashSet<>();

    public void add(Item item) {
        add(item, 1);
    }

    public void add(Item item, int quantity) {
        stacks.merge(item.name(), new ItemStack(item, quantity),
            (existing, added) -> new ItemStack(existing.item(), existing.quantity() + added.quantity()));
    }

    public int remove(String itemName) {
        return remove(itemName, 1);
    }

    public int remove(String itemName, int quantity) {
        ItemStack existing = stacks.get(itemName);
        if (existing == null) {
            return 0;
        }
        int actual = Math.min(quantity, existing.quantity());
        int remaining = existing.quantity() - actual;
        if (remaining == 0) {
            stacks.remove(itemName);
            equipped.remove(itemName);
        } else {
            stacks.put(itemName, new ItemStack(existing.item(), remaining));
        }
        return actual;
    }

    public boolean has(String itemName) {
        return stacks.containsKey(itemName);
    }

    public int count(String itemName) {
        ItemStack stack = stacks.get(itemName);
        return stack == null ? 0 : stack.quantity();
    }

    public void equip(String itemName) {
        if (stacks.containsKey(itemName)) {
            equipped.add(itemName);
        }
    }

    public void unequip(String itemName) {
        equipped.remove(itemName);
    }

    public boolean isEquipped(String itemName) {
        return equipped.contains(itemName);
    }

    public List<ItemStack> allStacks() {
        return new ArrayList<>(stacks.values());
    }

    public List<Item> equippedItems() {
        List<Item> result = new ArrayList<>();
        for (ItemStack stack : stacks.values()) {
            if (equipped.contains(stack.item().name()) && stack.item().category() == ItemCategory.EQUIPPABLE) {
                result.add(stack.item());
            }
        }
        return result;
    }

    public List<Item> passiveItems() {
        List<Item> result = new ArrayList<>();
        for (ItemStack stack : stacks.values()) {
            if (stack.item().category() == ItemCategory.PASSIVE) {
                result.add(stack.item());
            }
        }
        return result;
    }
}
