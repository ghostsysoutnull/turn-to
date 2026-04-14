package com.tas.neo.domain.item;

import com.tas.neo.domain.adventure.ScriptBlock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemStackTest {

    @Test
    void countable_item_always_shows_xN_even_at_one() {
        Item arrow = new Item("Arrow", "A sharp arrow", ItemCategory.USABLE, true, ScriptBlock.empty());

        ItemStack stack = new ItemStack(arrow, 1);

        assertThat(stack.displayName()).isEqualTo("Arrow x1");
    }

    @Test
    void countable_item_shows_xN_for_quantity_greater_than_one() {
        Item arrow = new Item("Arrow", "A sharp arrow", ItemCategory.USABLE, true, ScriptBlock.empty());

        ItemStack stack = new ItemStack(arrow, 20);

        assertThat(stack.displayName()).isEqualTo("Arrow x20");
    }

    @Test
    void non_countable_item_omits_suffix_at_quantity_one() {
        Item sword = new Item("Magic Sword", "A gleaming blade", ItemCategory.EQUIPPABLE, false, ScriptBlock.empty());

        ItemStack stack = new ItemStack(sword, 1);

        assertThat(stack.displayName()).isEqualTo("Magic Sword");
    }

    @Test
    void non_countable_item_shows_xN_at_quantity_greater_than_one() {
        Item sword = new Item("Magic Sword", "A gleaming blade", ItemCategory.EQUIPPABLE, false, ScriptBlock.empty());

        ItemStack stack = new ItemStack(sword, 2);

        assertThat(stack.displayName()).isEqualTo("Magic Sword x2");
    }
}
