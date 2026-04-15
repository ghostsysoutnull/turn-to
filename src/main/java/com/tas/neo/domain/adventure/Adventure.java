package com.tas.neo.domain.adventure;

import com.tas.neo.domain.item.Item;
import com.tas.neo.domain.location.Grid;
import com.tas.neo.domain.party.PartyMemberDefinition;
import java.util.List;
import java.util.Optional;

public class Adventure {

    private final String id;
    private final String title;
    private final String description;
    private final int startSection;
    private final int initialProvisions;
    private final List<Section> sections;
    private final List<Item> items;
    private final List<PartyMemberDefinition> partyMemberDefinitions;
    private final List<String> combatSystems;
    private final List<Grid> grids;
    private final ScriptBlock scripts;

    public Adventure(String id, String title, String description,
                     int startSection, int initialProvisions,
                     List<Section> sections, List<Item> items,
                     List<PartyMemberDefinition> partyMemberDefinitions,
                     List<String> combatSystems, List<Grid> grids,
                     ScriptBlock scripts) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.startSection = startSection;
        this.initialProvisions = initialProvisions;
        this.sections = sections;
        this.items = items;
        this.partyMemberDefinitions = partyMemberDefinitions;
        this.combatSystems = combatSystems;
        this.grids = grids;
        this.scripts = scripts;
    }

    public String id() { return id; }
    public String title() { return title; }
    public String description() { return description; }
    public int startSection() { return startSection; }
    public int initialProvisions() { return initialProvisions; }

    public List<Section> sections() { return sections; }

    public Section getSection(int number) {
        return sections.stream()
            .filter(s -> s.number() == number)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Section not found: " + number));
    }

    public boolean hasItem(String name) {
        return items.stream().anyMatch(i -> i.name().equals(name));
    }

    public Item getItem(String name) {
        return items.stream()
            .filter(i -> i.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Item not found: " + name));
    }

    public List<PartyMemberDefinition> partyMemberDefinitions() { return partyMemberDefinitions; }
    public List<String> combatSystems() { return combatSystems; }

    public Optional<Grid> getGrid(String id) {
        return grids.stream().filter(g -> g.id().equals(id)).findFirst();
    }

    public List<Grid> grids() { return grids; }
    public ScriptBlock scripts() { return scripts; }
}
