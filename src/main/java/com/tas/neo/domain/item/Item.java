package com.tas.neo.domain.item;

import com.tas.neo.domain.adventure.ScriptBlock;

public record Item(String name, String description, ItemCategory category,
                   boolean countable, ScriptBlock scripts) {

    public boolean canBeUsed() {
        return category == ItemCategory.USABLE;
    }

    public boolean canBeEquipped() {
        return category == ItemCategory.EQUIPPABLE;
    }

    public boolean isCountable() {
        return countable;
    }
}
