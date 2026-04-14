package com.tas.neo.domain.item;

public enum ItemScriptHook {
    ON_PICKUP, ON_DROP, ON_USE, ON_EQUIP, ON_UNEQUIP, ON_COMBAT_ROUND;

    public String hookName() {
        String[] parts = name().split("_");
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
