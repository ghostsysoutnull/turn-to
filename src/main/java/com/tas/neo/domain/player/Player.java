package com.tas.neo.domain.player;

import com.tas.neo.domain.item.Inventory;
import java.util.Map;

public class Player {

    private final Map<AttributeType, Attribute> attributes;
    private final Inventory inventory;
    private int gold;
    private int provisions;

    public Player(Map<AttributeType, Attribute> attributes, Inventory inventory,
                  int gold, int provisions) {
        this.attributes = attributes;
        this.inventory = inventory;
        this.gold = gold;
        this.provisions = provisions;
    }

    public int getSkill() {
        return attributes.get(AttributeType.SKILL).current();
    }

    public int getStamina() {
        return attributes.get(AttributeType.STAMINA).current();
    }

    public int getMaxStamina() {
        return attributes.get(AttributeType.STAMINA).max();
    }

    public int getLuck() {
        return attributes.get(AttributeType.LUCK).current();
    }

    public int getStat(AttributeType type) {
        return attributes.get(type).current();
    }

    public void modifyAttribute(AttributeType type, int delta) {
        attributes.put(type, attributes.get(type).modify(delta));
    }

    public int getGold() {
        return gold;
    }

    public void modifyGold(int delta) {
        gold = Math.max(0, gold + delta);
    }

    public int getProvisions() {
        return provisions;
    }

    public void modifyProvisions(int delta) {
        provisions = Math.max(0, provisions + delta);
    }

    public Inventory getInventory() {
        return inventory;
    }

    public boolean isAlive() {
        return getStamina() > 0;
    }
}
