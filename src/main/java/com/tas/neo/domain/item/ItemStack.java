package com.tas.neo.domain.item;

public record ItemStack(Item item, int quantity) {

    public String displayName() {
        if (item.isCountable() || quantity > 1) {
            return item.name() + " x" + quantity;
        }
        return item.name();
    }
}
