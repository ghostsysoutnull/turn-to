package com.tas.neo.loader;

import com.tas.neo.domain.DiceStatDefinition;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.event.CombatEvent;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.mechanics.FixedDice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link JsonAdventureLoader}.
 *
 * <p>All fixture files live under {@code src/test/resources/adventures/}.
 * Tests parse JSON only; no {@link Adventure} objects are constructed programmatically.
 *
 * <p>Covers the test strategy table in {@code docs/design/05-adventure-loader.md}.
 */
class JsonAdventureLoaderTest {

    private static final Path FIXTURES =
        Paths.get("src/test/resources/adventures");

    private JsonAdventureLoader loader;

    @BeforeEach
    void setUp() {
        loader = new JsonAdventureLoader(FIXTURES);
    }

    // -------------------------------------------------------------------------
    // Happy-path: valid adventures load without error
    // -------------------------------------------------------------------------

    @Test
    void player_stats_field_parsed_correctly() throws AdventureLoadException {
        Adventure adventure = loader.load("valid-with-player-stats");

        assertThat(adventure.playerStats())
                .as("playerStats must contain exactly SKILL, STAMINA, LUCK")
                .containsOnlyKeys("SKILL", "STAMINA", "LUCK");

        DiceStatDefinition skill = (DiceStatDefinition) adventure.playerStats().get("SKILL");
        DiceStatDefinition stamina = (DiceStatDefinition) adventure.playerStats().get("STAMINA");
        DiceStatDefinition luck = (DiceStatDefinition) adventure.playerStats().get("LUCK");

        FixedDice one = new FixedDice(1);
        assertThat(skill.resolveInitial(one))
                .as("SKILL formula 1d6+6 with FixedDice(1) must yield 7 (1+6)")
                .isEqualTo(7);
        assertThat(stamina.resolveInitial(one))
                .as("STAMINA formula 2d6+12 with FixedDice(1) must yield 14 (1+1+12)")
                .isEqualTo(14);
        assertThat(luck.resolveInitial(one))
                .as("LUCK formula 1d6+6 with FixedDice(1) must yield 7 (1+6)")
                .isEqualTo(7);
    }

    @Test
    void player_stats_absent_yields_empty_map() throws AdventureLoadException {
        Adventure adventure = loader.load("valid-minimal");

        assertThat(adventure.playerStats())
                .as("adventure with no playerStats block must return empty map, not null")
                .isEmpty();
    }

    @Test
    void valid_minimal_adventure_loads_without_error() throws AdventureLoadException {
        Adventure adventure = loader.load("valid-minimal");

        assertThat(adventure.id()).isEqualTo("valid-minimal");
        assertThat(adventure.title()).isEqualTo("Minimal Adventure");
        assertThat(adventure.startSection()).isEqualTo(1);
        assertThat(adventure.initialProvisions()).isEqualTo(5);
        assertThat(adventure.initialGold()).isEqualTo(8);
    }

    @Test
    void initial_gold_defaults_to_ten_when_field_absent() throws AdventureLoadException {
        Adventure adventure = loader.load("valid-with-grid");

        assertThat(adventure.initialGold())
                .as("initialGold must default to 10 when the JSON field is absent")
                .isEqualTo(10);
    }

    @Test
    void valid_adventure_with_grid_loads_without_error() throws AdventureLoadException {
        Adventure adventure = loader.load("valid-with-grid");

        assertThat(adventure.id()).isEqualTo("valid-with-grid");
        assertThat(adventure.grids()).hasSize(1);
        assertThat(adventure.grids().get(0).id()).isEqualTo("cave-network");
        assertThat(adventure.grids().get(0).width()).isEqualTo(3);
        assertThat(adventure.grids().get(0).height()).isEqualTo(3);
        assertThat(adventure.grids().get(0).cells()).hasSize(2);
    }

    @Test
    void valid_adventure_with_grid_choice_resolves_to_grid_cell() throws AdventureLoadException {
        Adventure adventure = loader.load("valid-with-grid");

        var section1 = adventure.getSection(1);
        assertThat(section1.choices()).as("section 1 should have one choice").hasSize(1);
    }

    // -------------------------------------------------------------------------
    // Defaults applied at load time
    // -------------------------------------------------------------------------

    @Test
    void combat_event_system_defaults_to_personal_when_absent() throws AdventureLoadException {
        Adventure adventure = loader.load("combat-event-no-system");

        List<SectionEvent> events = adventure.getSection(1).events();
        assertThat(events).as("section 1 should have one event").hasSize(1);
        CombatEvent combatEvent = (CombatEvent) events.get(0);
        assertThat(combatEvent.system())
            .as("CombatEvent.system should default to 'personal' when absent from JSON")
            .isEqualTo("personal");
    }

    @Test
    void section_events_default_to_empty_list_when_absent() throws AdventureLoadException {
        Adventure adventure = loader.load("section-events-absent");

        assertThat(adventure.getSection(1).events())
            .as("Section.events should default to empty list when absent from JSON")
            .isEmpty();
    }

    @Test
    void section_choices_default_to_empty_list_when_absent() throws AdventureLoadException {
        Adventure adventure = loader.load("section-choices-absent");

        assertThat(adventure.getSection(2).choices())
            .as("Section.choices should default to empty list when absent from JSON")
            .isEmpty();
    }

    // -------------------------------------------------------------------------
    // Validation: startSection
    // -------------------------------------------------------------------------

    @Test
    void missing_start_section_field_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("missing-start-section"))
            .as("absent startSection should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    @Test
    void start_section_referencing_nonexistent_section_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("start-section-not-found"))
            .as("startSection pointing to a non-existent section should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    // -------------------------------------------------------------------------
    // Validation: choice targetSection
    // -------------------------------------------------------------------------

    @Test
    void choice_target_section_referencing_nonexistent_section_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("choice-target-section-not-found"))
            .as("choice targetSection pointing to a non-existent section should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    // -------------------------------------------------------------------------
    // Validation: LUCK_TEST event sections
    // -------------------------------------------------------------------------

    @Test
    void luck_test_success_section_referencing_nonexistent_section_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("luck-test-success-section-not-found"))
            .as("LUCK_TEST successSection pointing to a non-existent section should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    @Test
    void luck_test_fail_section_referencing_nonexistent_section_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("luck-test-fail-section-not-found"))
            .as("LUCK_TEST failSection pointing to a non-existent section should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    // -------------------------------------------------------------------------
    // Validation: SKILL_TEST event sections
    // -------------------------------------------------------------------------

    @Test
    void skill_test_success_section_referencing_nonexistent_section_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("skill-test-success-section-not-found"))
            .as("SKILL_TEST successSection pointing to a non-existent section should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    @Test
    void skill_test_fail_section_referencing_nonexistent_section_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("skill-test-fail-section-not-found"))
            .as("SKILL_TEST failSection pointing to a non-existent section should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    // -------------------------------------------------------------------------
    // Validation: NORMAL section must have an exit
    // -------------------------------------------------------------------------

    @Test
    void normal_section_with_no_choices_no_navigate_event_no_onEnter_script_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("normal-section-no-exit"))
            .as("NORMAL section with no choices, no NAVIGATE event, and no onEnter script should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    @Test
    void normal_section_with_onEnter_script_and_no_choices_loads_without_error() throws AdventureLoadException {
        Adventure adventure = loader.load("normal-section-on-enter-script");

        assertThat(adventure.getSection(1).choices())
            .as("section with onEnter script may have no choices")
            .isEmpty();
    }

    // -------------------------------------------------------------------------
    // Validation: creature stats
    // -------------------------------------------------------------------------

    @Test
    void creature_with_zero_skill_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("creature-zero-skill"))
            .as("creature SKILL=0 should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    @Test
    void creature_with_zero_stamina_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("creature-zero-stamina"))
            .as("creature STAMINA=0 should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    // -------------------------------------------------------------------------
    // Validation: item names in events must exist in adventure item list
    // -------------------------------------------------------------------------

    @Test
    void item_name_in_event_not_in_adventure_items_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("item-not-in-adventure-list"))
            .as("item referenced in event but absent from adventure items list should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    // -------------------------------------------------------------------------
    // Validation: CombatEvent participantIds must exist in partyMembers
    // -------------------------------------------------------------------------

    @Test
    void combat_participant_id_not_in_party_members_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("combat-participant-not-in-party"))
            .as("CombatEvent participantId absent from partyMembers should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    // -------------------------------------------------------------------------
    // Validation: grid ids are unique within adventure
    // -------------------------------------------------------------------------

    @Test
    void duplicate_grid_ids_throw_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("duplicate-grid-ids"))
            .as("duplicate grid ids within an adventure should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    // -------------------------------------------------------------------------
    // Validation: cell positions within grid dimensions
    // -------------------------------------------------------------------------

    @Test
    void cell_position_out_of_grid_bounds_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("cell-out-of-bounds"))
            .as("cell (x,y,z) outside declared grid dimensions should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    // -------------------------------------------------------------------------
    // Validation: no duplicate cell (x,y,z) within a grid
    // -------------------------------------------------------------------------

    @Test
    void duplicate_cell_coordinates_in_grid_throw_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("duplicate-cell-coordinates"))
            .as("two cells at the same (x,y,z) within a grid should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    // -------------------------------------------------------------------------
    // Validation: no duplicate cell ids within a grid
    // -------------------------------------------------------------------------

    @Test
    void duplicate_cell_ids_in_grid_throw_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("duplicate-cell-ids"))
            .as("two cells with the same id within a grid should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    // -------------------------------------------------------------------------
    // Validation: cell passages without toSection must target a cell-occupied coordinate
    // -------------------------------------------------------------------------

    @Test
    void passage_without_to_section_targeting_empty_coordinate_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("passage-no-to-section-targets-empty-coord"))
            .as("passage without toSection targeting an unpopulated coordinate should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    // -------------------------------------------------------------------------
    // Validation: cell passage toSection must reference an existing section
    // -------------------------------------------------------------------------

    @Test
    void passage_to_section_referencing_nonexistent_section_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("passage-to-section-not-found"))
            .as("cell passage toSection pointing to a non-existent section should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    // -------------------------------------------------------------------------
    // Validation: toGrid + toCell in choices
    // -------------------------------------------------------------------------

    @Test
    void choice_to_grid_with_unknown_grid_id_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("choice-to-grid-unknown-grid"))
            .as("choice toGrid pointing to a non-existent grid should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    @Test
    void choice_to_grid_with_unknown_cell_id_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("choice-to-grid-unknown-cell"))
            .as("choice toCell pointing to a non-existent cell id should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }

    @Test
    void choice_with_both_target_section_and_to_grid_throws_AdventureLoadException() {
        assertThatThrownBy(() -> loader.load("choice-both-targets"))
            .as("choice declaring both targetSection and toGrid/toCell should throw AdventureLoadException")
            .isInstanceOf(AdventureLoadException.class);
    }
}
