package com.tas.neo.domain.player;

import com.tas.neo.domain.item.Inventory;
import com.tas.neo.domain.item.Item;
import com.tas.neo.domain.item.ItemCategory;
import com.tas.neo.domain.adventure.ScriptBlock;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerTest {

    private static Player newPlayer(int skill, int stamina, int luck, int gold, int provisions) {
        Map<AttributeType, Attribute> attrs = new EnumMap<>(AttributeType.class);
        attrs.put(AttributeType.SKILL, new Attribute(AttributeType.SKILL, skill, skill));
        attrs.put(AttributeType.STAMINA, new Attribute(AttributeType.STAMINA, stamina, stamina));
        attrs.put(AttributeType.LUCK, new Attribute(AttributeType.LUCK, luck, luck));
        return new Player(attrs, new Inventory(), gold, provisions);
    }

    @Test
    void reports_skill_stamina_and_luck() {
        Player p = newPlayer(9, 20, 8, 0, 0);

        assertThat(p.getSkill()).isEqualTo(9);
        assertThat(p.getStamina()).isEqualTo(20);
        assertThat(p.getLuck()).isEqualTo(8);
    }

    @Test
    void getMaxStamina_returns_initial_rolled_maximum() {
        Player p = newPlayer(9, 20, 8, 0, 0);

        assertThat(p.getMaxStamina()).isEqualTo(20);
    }

    @Test
    void modifyAttribute_reduces_stamina_on_wound() {
        Player p = newPlayer(9, 20, 8, 0, 0);

        p.modifyAttribute(AttributeType.STAMINA, -4);

        assertThat(p.getStamina()).isEqualTo(16);
    }

    @Test
    void modifyAttribute_clamps_stamina_to_zero() {
        Player p = newPlayer(9, 5, 8, 0, 0);

        p.modifyAttribute(AttributeType.STAMINA, -50);

        assertThat(p.getStamina()).isEqualTo(0);
    }

    @Test
    void modifyAttribute_clamps_stamina_to_max() {
        Player p = newPlayer(9, 15, 8, 0, 0);
        // reduce to 10 then heal past max
        p.modifyAttribute(AttributeType.STAMINA, -5);

        p.modifyAttribute(AttributeType.STAMINA, 100);

        assertThat(p.getStamina()).isEqualTo(15);
    }

    @Test
    void isAlive_true_when_stamina_positive() {
        Player p = newPlayer(9, 1, 8, 0, 0);

        assertThat(p.isAlive()).isTrue();
    }

    @Test
    void isAlive_false_when_stamina_zero() {
        Player p = newPlayer(9, 1, 8, 0, 0);

        p.modifyAttribute(AttributeType.STAMINA, -1);

        assertThat(p.isAlive()).isFalse();
    }

    @Test
    void getStat_returns_same_value_as_named_getters() {
        Player p = newPlayer(9, 20, 8, 0, 0);

        assertThat(p.getStat(AttributeType.SKILL)).isEqualTo(9);
        assertThat(p.getStat(AttributeType.STAMINA)).isEqualTo(20);
        assertThat(p.getStat(AttributeType.LUCK)).isEqualTo(8);
    }

    @Test
    void getGold_returns_initial_gold() {
        Player p = newPlayer(9, 20, 8, 15, 3);

        assertThat(p.getGold()).isEqualTo(15);
    }

    @Test
    void modifyGold_adds_delta() {
        Player p = newPlayer(9, 20, 8, 10, 0);

        p.modifyGold(5);

        assertThat(p.getGold()).isEqualTo(15);
    }

    @Test
    void modifyGold_clamps_to_zero() {
        Player p = newPlayer(9, 20, 8, 3, 0);

        p.modifyGold(-100);

        assertThat(p.getGold()).isZero();
    }

    @Test
    void getProvisions_returns_initial_provisions() {
        Player p = newPlayer(9, 20, 8, 0, 5);

        assertThat(p.getProvisions()).isEqualTo(5);
    }

    @Test
    void modifyProvisions_adds_delta() {
        Player p = newPlayer(9, 20, 8, 0, 3);

        p.modifyProvisions(-1);

        assertThat(p.getProvisions()).isEqualTo(2);
    }

    @Test
    void modifyProvisions_clamps_to_zero() {
        Player p = newPlayer(9, 20, 8, 0, 1);

        p.modifyProvisions(-100);

        assertThat(p.getProvisions()).isZero();
    }

    @Test
    void getInventory_returns_the_same_inventory_instance() {
        Player p = newPlayer(9, 20, 8, 0, 0);
        Item torch = new Item("Torch", "A lit torch", ItemCategory.USABLE, false, ScriptBlock.empty());

        p.getInventory().add(torch);

        assertThat(p.getInventory().has("Torch")).isTrue();
    }
}
