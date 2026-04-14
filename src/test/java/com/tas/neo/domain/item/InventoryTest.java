package com.tas.neo.domain.item;

import com.tas.neo.domain.adventure.ScriptBlock;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryTest {

    private static Item countable(String name) {
        return new Item(name, name + " description", ItemCategory.USABLE, true, ScriptBlock.empty());
    }

    private static Item unique(String name) {
        return new Item(name, name + " description", ItemCategory.KEY, false, ScriptBlock.empty());
    }

    private static Item equippable(String name) {
        return new Item(name, name + " description", ItemCategory.EQUIPPABLE, false, ScriptBlock.empty());
    }

    private static Item passive(String name) {
        return new Item(name, name + " description", ItemCategory.PASSIVE, false, ScriptBlock.empty());
    }

    @Test
    void add_single_item_increments_count_to_one() {
        Inventory inv = new Inventory();

        inv.add(unique("Iron Key"));

        assertThat(inv.count("Iron Key")).isEqualTo(1);
        assertThat(inv.has("Iron Key")).isTrue();
    }

    @Test
    void add_with_quantity_increments_count_by_that_amount() {
        Inventory inv = new Inventory();

        inv.add(countable("Arrow"), 20);

        assertThat(inv.count("Arrow")).isEqualTo(20);
    }

    @Test
    void has_returns_false_for_unknown_item() {
        Inventory inv = new Inventory();

        assertThat(inv.has("Nonexistent")).isFalse();
        assertThat(inv.count("Nonexistent")).isZero();
    }

    @Test
    void remove_default_reduces_count_by_one() {
        Inventory inv = new Inventory();
        inv.add(countable("Arrow"), 5);

        int removed = inv.remove("Arrow");

        assertThat(removed).isEqualTo(1);
        assertThat(inv.count("Arrow")).isEqualTo(4);
    }

    @Test
    void remove_with_quantity_reduces_count_by_that_amount() {
        Inventory inv = new Inventory();
        inv.add(countable("Arrow"), 5);

        int removed = inv.remove("Arrow", 3);

        assertThat(removed).isEqualTo(3);
        assertThat(inv.count("Arrow")).isEqualTo(2);
    }

    @Test
    void remove_caps_at_current_stock_and_returns_actual_removed() {
        Inventory inv = new Inventory();
        inv.add(countable("Arrow"), 5);

        int removed = inv.remove("Arrow", 10);

        assertThat(removed).as("remove returns how many were actually removed").isEqualTo(5);
        assertThat(inv.count("Arrow")).isZero();
    }

    @Test
    void remove_on_empty_stock_returns_zero() {
        Inventory inv = new Inventory();

        int removed = inv.remove("Nothing");

        assertThat(removed).isZero();
    }

    @Test
    void allStacks_lists_each_item_with_quantity_in_insertion_order() {
        Inventory inv = new Inventory();
        inv.add(unique("Iron Key"));
        inv.add(countable("Arrow"), 10);
        inv.add(unique("Torch"));

        List<ItemStack> stacks = inv.allStacks();

        assertThat(stacks).hasSize(3);
        assertThat(stacks.get(0).item().name()).isEqualTo("Iron Key");
        assertThat(stacks.get(1).item().name()).isEqualTo("Arrow");
        assertThat(stacks.get(1).quantity()).isEqualTo(10);
        assertThat(stacks.get(2).item().name()).isEqualTo("Torch");
    }

    @Test
    void equip_marks_item_as_equipped() {
        Inventory inv = new Inventory();
        inv.add(equippable("Magic Sword"));

        inv.equip("Magic Sword");

        assertThat(inv.isEquipped("Magic Sword")).isTrue();
    }

    @Test
    void unequip_clears_equipped_state() {
        Inventory inv = new Inventory();
        inv.add(equippable("Magic Sword"));
        inv.equip("Magic Sword");

        inv.unequip("Magic Sword");

        assertThat(inv.isEquipped("Magic Sword")).isFalse();
    }

    @Test
    void equip_on_absent_item_is_noop() {
        Inventory inv = new Inventory();

        inv.equip("Ghost Sword");

        assertThat(inv.isEquipped("Ghost Sword")).isFalse();
    }

    @Test
    void equippedItems_returns_only_equipped_items() {
        Inventory inv = new Inventory();
        inv.add(equippable("Magic Sword"));
        inv.add(equippable("Shield"));
        inv.equip("Magic Sword");

        List<Item> equipped = inv.equippedItems();

        assertThat(equipped).extracting(Item::name).containsExactly("Magic Sword");
    }

    @Test
    void remove_last_unit_clears_entry_from_inventory() {
        Inventory inv = new Inventory();
        inv.add(countable("Arrow"), 1);

        inv.remove("Arrow");

        assertThat(inv.has("Arrow")).isFalse();
        assertThat(inv.allStacks()).isEmpty();
    }

    @Test
    void passiveItems_returns_only_passive_items() {
        Inventory inv = new Inventory();
        inv.add(passive("Cursed Amulet"));
        inv.add(equippable("Magic Sword"));
        inv.add(passive("Lucky Coin"));

        List<Item> passives = inv.passiveItems();

        assertThat(passives).extracting(Item::name).containsExactly("Cursed Amulet", "Lucky Coin");
    }

    @Test
    void allStacks_preserves_insertion_order_for_combat_round_firing() {
        Inventory inv = new Inventory();
        inv.add(passive("First Amulet"));
        inv.add(passive("Second Amulet"));

        List<ItemStack> stacks = inv.allStacks();

        assertThat(stacks)
            .extracting(s -> s.item().name())
            .containsExactly("First Amulet", "Second Amulet");
    }
}
