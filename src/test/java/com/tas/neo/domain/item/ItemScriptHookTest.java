package com.tas.neo.domain.item;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemScriptHookTest {

    @Test
    void hookName_is_camelCase_for_single_word_hooks() {
        assertThat(ItemScriptHook.ON_USE.hookName()).isEqualTo("onUse");
        assertThat(ItemScriptHook.ON_DROP.hookName()).isEqualTo("onDrop");
    }

    @Test
    void hookName_is_camelCase_for_two_word_hooks() {
        assertThat(ItemScriptHook.ON_PICKUP.hookName()).isEqualTo("onPickup");
        assertThat(ItemScriptHook.ON_EQUIP.hookName()).isEqualTo("onEquip");
        assertThat(ItemScriptHook.ON_UNEQUIP.hookName()).isEqualTo("onUnequip");
    }

    @Test
    void hookName_is_camelCase_for_multi_word_hooks() {
        assertThat(ItemScriptHook.ON_COMBAT_ROUND.hookName()).isEqualTo("onCombatRound");
    }

    @Test
    void defines_six_hooks() {
        assertThat(ItemScriptHook.values())
            .containsExactlyInAnyOrder(
                ItemScriptHook.ON_PICKUP,
                ItemScriptHook.ON_DROP,
                ItemScriptHook.ON_USE,
                ItemScriptHook.ON_EQUIP,
                ItemScriptHook.ON_UNEQUIP,
                ItemScriptHook.ON_COMBAT_ROUND
            );
    }
}
