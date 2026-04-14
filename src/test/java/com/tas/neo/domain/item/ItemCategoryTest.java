package com.tas.neo.domain.item;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemCategoryTest {

    @Test
    void defines_four_categories() {
        assertThat(ItemCategory.values())
            .containsExactlyInAnyOrder(
                ItemCategory.USABLE,
                ItemCategory.EQUIPPABLE,
                ItemCategory.KEY,
                ItemCategory.PASSIVE
            );
    }
}
