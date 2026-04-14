package com.tas.neo.domain.item;

import com.tas.neo.domain.adventure.ScriptBlock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemTest {

    @Test
    void usable_item_canBeUsed_true() {
        Item potion = new Item("Potion", "Heals", ItemCategory.USABLE, true, ScriptBlock.empty());

        assertThat(potion.canBeUsed()).isTrue();
        assertThat(potion.canBeEquipped()).isFalse();
    }

    @Test
    void equippable_item_canBeEquipped_true() {
        Item sword = new Item("Sword", "A sword", ItemCategory.EQUIPPABLE, false, ScriptBlock.empty());

        assertThat(sword.canBeEquipped()).isTrue();
        assertThat(sword.canBeUsed()).isFalse();
    }

    @Test
    void key_item_cannot_be_used_or_equipped() {
        Item key = new Item("Iron Key", "A key", ItemCategory.KEY, false, ScriptBlock.empty());

        assertThat(key.canBeUsed()).isFalse();
        assertThat(key.canBeEquipped()).isFalse();
    }

    @Test
    void passive_item_cannot_be_used_or_equipped() {
        Item amulet = new Item("Cursed Amulet", "Evil", ItemCategory.PASSIVE, false, ScriptBlock.empty());

        assertThat(amulet.canBeUsed()).isFalse();
        assertThat(amulet.canBeEquipped()).isFalse();
    }

    @Test
    void retains_category_name_description_countability() {
        Item arrow = new Item("Arrow", "A sharp arrow", ItemCategory.USABLE, true, ScriptBlock.empty());

        assertThat(arrow.name()).isEqualTo("Arrow");
        assertThat(arrow.description()).isEqualTo("A sharp arrow");
        assertThat(arrow.category()).isEqualTo(ItemCategory.USABLE);
        assertThat(arrow.isCountable()).isTrue();
    }
}
