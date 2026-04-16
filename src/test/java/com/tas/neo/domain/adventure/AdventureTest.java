package com.tas.neo.domain.adventure;

import com.tas.neo.domain.item.Item;
import com.tas.neo.domain.item.ItemCategory;
import com.tas.neo.domain.location.Grid;
import com.tas.neo.domain.party.PartyMemberDefinition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdventureTest {

    private static Section section(int number) {
        return new Section(
            number,
            "Narrative for section " + number,
            List.of(),
            List.of(),
            SectionType.NORMAL,
            ScriptBlock.empty()
        );
    }

    private static Adventure newAdventure(List<Section> sections, List<Item> items,
                                          List<Grid> grids) {
        return new Adventure(
            "test-adventure",
            "Test Adventure",
            "A test.",
            1,
            10,
            sections,
            items,
            List.<PartyMemberDefinition>of(),
            List.of(),
            grids,
            ScriptBlock.empty(), Map.of()
        );
    }

    @Test
    void exposes_id_title_description_startSection_initialProvisions() {
        Adventure a = newAdventure(List.of(section(1)), List.of(), List.of());

        assertThat(a.id()).isEqualTo("test-adventure");
        assertThat(a.title()).isEqualTo("Test Adventure");
        assertThat(a.description()).isEqualTo("A test.");
        assertThat(a.startSection()).isEqualTo(1);
        assertThat(a.initialProvisions()).isEqualTo(10);
    }

    @Test
    void getSection_returns_section_with_matching_number() {
        Section s1 = section(1);
        Section s2 = section(2);
        Adventure a = newAdventure(List.of(s1, s2), List.of(), List.of());

        assertThat(a.getSection(2)).isEqualTo(s2);
    }

    @Test
    void hasItem_and_getItem_resolve_by_name() {
        Item key = new Item("Iron Key", "A key", ItemCategory.KEY, false, ScriptBlock.empty());
        Adventure a = newAdventure(List.of(section(1)), List.of(key), List.of());

        assertThat(a.hasItem("Iron Key")).isTrue();
        assertThat(a.hasItem("Unknown")).isFalse();
        assertThat(a.getItem("Iron Key")).isEqualTo(key);
    }

    @Test
    void grids_and_getGrid_resolve_by_id() {
        Grid grid = new Grid("cave", 3, 3, 1, Map.of());
        Adventure a = newAdventure(List.of(section(1)), List.of(), List.of(grid));

        assertThat(a.grids()).containsExactly(grid);
        assertThat(a.getGrid("cave")).contains(grid);
        assertThat(a.getGrid("nope")).isEmpty();
    }

    @Test
    void partyMemberDefinitions_and_combatSystems_accessible() {
        Adventure a = newAdventure(List.of(section(1)), List.of(), List.of());

        assertThat(a.partyMemberDefinitions()).isEmpty();
        assertThat(a.combatSystems()).isEmpty();
    }
}
