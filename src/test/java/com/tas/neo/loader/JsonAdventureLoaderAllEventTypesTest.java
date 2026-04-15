package com.tas.neo.loader;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.GoldCondition;
import com.tas.neo.domain.adventure.HasItemCondition;
import com.tas.neo.domain.adventure.LacksItemCondition;
import com.tas.neo.domain.adventure.StatCondition;
import com.tas.neo.domain.adventure.StateEqualsCondition;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.event.CombatEvent;
import com.tas.neo.domain.adventure.event.GoldChangeEvent;
import com.tas.neo.domain.adventure.event.ItemAction;
import com.tas.neo.domain.adventure.event.ItemEvent;
import com.tas.neo.domain.adventure.event.LuckTestEvent;
import com.tas.neo.domain.adventure.event.NavigateEvent;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.adventure.event.SkillTestEvent;
import com.tas.neo.domain.adventure.event.StatChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads the {@code all-event-types} fixture and asserts every event type and
 * condition type parses correctly, including alias field names (e.g. {@code item}
 * instead of {@code itemName}, {@code failureSection} instead of {@code failSection},
 * {@code amount} instead of {@code minimum}).
 *
 * <p>Fixture lives at {@code src/test/resources/fixtures/all-event-types.json}.
 */
class JsonAdventureLoaderAllEventTypesTest {

    private static final Path FIXTURES =
        Paths.get("src/test/resources/fixtures");

    private JsonAdventureLoader loader;
    private Adventure adventure;

    @BeforeEach
    void loadFixture() throws AdventureLoadException {
        loader = new JsonAdventureLoader(FIXTURES);
        adventure = loader.load("all-event-types");
    }

    // -------------------------------------------------------------------------
    // Top-level adventure properties
    // -------------------------------------------------------------------------

    @Test
    void fixture_loads_without_exception() {
        assertThat(adventure).as("adventure should load without exception").isNotNull();
    }

    @Test
    void adventure_has_expected_section_count() {
        // Sections: 1–9 plus 10 (VICTORY) and 20 = 11 sections
        assertThat(adventure.getSection(1)).as("section 1 should exist").isNotNull();
        assertThat(adventure.getSection(10)).as("section 10 (VICTORY) should exist").isNotNull();
        assertThat(adventure.getSection(20)).as("section 20 should exist").isNotNull();
    }

    @Test
    void start_section_is_one() {
        assertThat(adventure.startSection())
            .as("startSection must be 1 as declared in fixture")
            .isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // ITEM_GAIN — uses alias field 'item'
    // -------------------------------------------------------------------------

    @Test
    void item_gain_event_has_action_GAIN_and_correct_item_name() {
        SectionEvent event = adventure.getSection(1).events().get(0);
        assertThat(event).as("section 1 event should be an ItemEvent").isInstanceOf(ItemEvent.class);
        ItemEvent itemEvent = (ItemEvent) event;
        assertThat(itemEvent.action())
            .as("ITEM_GAIN event must have action GAIN")
            .isEqualTo(ItemAction.GAIN);
        assertThat(itemEvent.itemName())
            .as("ITEM_GAIN event must parse 'item' alias as itemName")
            .isEqualTo("Sword");
    }

    // -------------------------------------------------------------------------
    // ITEM_LOSS — uses alias field 'item'
    // -------------------------------------------------------------------------

    @Test
    void item_loss_event_has_action_LOSS_and_correct_item_name() {
        SectionEvent event = adventure.getSection(2).events().get(0);
        assertThat(event).as("section 2 event should be an ItemEvent").isInstanceOf(ItemEvent.class);
        ItemEvent itemEvent = (ItemEvent) event;
        assertThat(itemEvent.action())
            .as("ITEM_LOSS event must have action LOSS")
            .isEqualTo(ItemAction.LOSS);
        assertThat(itemEvent.itemName())
            .as("ITEM_LOSS event must parse 'item' alias as itemName")
            .isEqualTo("Sword");
    }

    // -------------------------------------------------------------------------
    // COMBAT — uses 'enemy' single-object format and successSection/failureSection
    // -------------------------------------------------------------------------

    @Test
    void combat_event_parses_enemy_object_format_with_correct_opponent() {
        SectionEvent event = adventure.getSection(3).events().get(0);
        assertThat(event).as("section 3 event should be a CombatEvent").isInstanceOf(CombatEvent.class);
        CombatEvent combatEvent = (CombatEvent) event;
        assertThat(combatEvent.opponents())
            .as("CombatEvent must have exactly one opponent from 'enemy' object")
            .hasSize(1);
        assertThat(combatEvent.opponents().get(0).name())
            .as("CombatEvent opponent name must be 'Goblin'")
            .isEqualTo("Goblin");
    }

    @Test
    void combat_event_parses_success_and_failure_sections() {
        CombatEvent combatEvent = (CombatEvent) adventure.getSection(3).events().get(0);
        assertThat(combatEvent.successSection())
            .as("CombatEvent successSection must be 10")
            .isEqualTo(10);
        assertThat(combatEvent.failureSection())
            .as("CombatEvent failureSection must be 20 (parsed from 'failureSection' alias)")
            .isEqualTo(20);
    }

    // -------------------------------------------------------------------------
    // LUCK_TEST — uses 'failureSection' alias for failSection
    // -------------------------------------------------------------------------

    @Test
    void luck_test_event_parses_success_and_fail_sections_from_failureSection_alias() {
        SectionEvent event = adventure.getSection(4).events().get(0);
        assertThat(event).as("section 4 event should be a LuckTestEvent").isInstanceOf(LuckTestEvent.class);
        LuckTestEvent luckEvent = (LuckTestEvent) event;
        assertThat(luckEvent.successSection())
            .as("LuckTestEvent successSection must be 10")
            .isEqualTo(10);
        assertThat(luckEvent.failSection())
            .as("LuckTestEvent failSection must be 20 (parsed from 'failureSection' JSON field)")
            .isEqualTo(20);
    }

    // -------------------------------------------------------------------------
    // SKILL_TEST — uses 'failureSection' alias for failSection
    // -------------------------------------------------------------------------

    @Test
    void skill_test_event_parses_success_and_fail_sections_from_failureSection_alias() {
        SectionEvent event = adventure.getSection(5).events().get(0);
        assertThat(event).as("section 5 event should be a SkillTestEvent").isInstanceOf(SkillTestEvent.class);
        SkillTestEvent skillEvent = (SkillTestEvent) event;
        assertThat(skillEvent.successSection())
            .as("SkillTestEvent successSection must be 10")
            .isEqualTo(10);
        assertThat(skillEvent.failSection())
            .as("SkillTestEvent failSection must be 20 (parsed from 'failureSection' JSON field)")
            .isEqualTo(20);
    }

    // -------------------------------------------------------------------------
    // NAVIGATE
    // -------------------------------------------------------------------------

    @Test
    void navigate_event_parses_target_section() {
        SectionEvent event = adventure.getSection(6).events().get(0);
        assertThat(event).as("section 6 event should be a NavigateEvent").isInstanceOf(NavigateEvent.class);
        NavigateEvent navigateEvent = (NavigateEvent) event;
        assertThat(navigateEvent.targetSection())
            .as("NavigateEvent targetSection must be 10")
            .isEqualTo(10);
    }

    // -------------------------------------------------------------------------
    // STAT_CHANGE
    // -------------------------------------------------------------------------

    @Test
    void stat_change_event_parses_attribute_and_delta() {
        SectionEvent event = adventure.getSection(7).events().get(0);
        assertThat(event).as("section 7 event should be a StatChangeEvent").isInstanceOf(StatChangeEvent.class);
        StatChangeEvent statEvent = (StatChangeEvent) event;
        assertThat(statEvent.attribute())
            .as("StatChangeEvent attribute must be STAMINA")
            .isEqualTo(com.tas.neo.domain.player.AttributeType.STAMINA);
        assertThat(statEvent.delta())
            .as("StatChangeEvent delta must be -2")
            .isEqualTo(-2);
    }

    // -------------------------------------------------------------------------
    // GOLD_CHANGE
    // -------------------------------------------------------------------------

    @Test
    void gold_change_event_parses_delta() {
        SectionEvent event = adventure.getSection(8).events().get(0);
        assertThat(event).as("section 8 event should be a GoldChangeEvent").isInstanceOf(GoldChangeEvent.class);
        GoldChangeEvent goldEvent = (GoldChangeEvent) event;
        assertThat(goldEvent.delta())
            .as("GoldChangeEvent delta must be 5")
            .isEqualTo(5);
    }

    // -------------------------------------------------------------------------
    // Condition types in choices (section 9)
    // -------------------------------------------------------------------------

    @Test
    void has_item_condition_parses_item_name_from_alias_field() {
        List<Choice> choices = adventure.getSection(9).choices();
        Choice hasItemChoice = choices.get(0);
        assertThat(hasItemChoice.condition())
            .as("first choice should have a HasItemCondition present")
            .isPresent();
        assertThat(hasItemChoice.condition().get())
            .as("first choice condition should be HasItemCondition")
            .isInstanceOf(HasItemCondition.class);
        HasItemCondition condition = (HasItemCondition) hasItemChoice.condition().get();
        assertThat(condition.itemName())
            .as("HasItemCondition must parse 'item' alias as itemName")
            .isEqualTo("Shield");
    }

    @Test
    void lacks_item_condition_parses_item_name_from_alias_field() {
        List<Choice> choices = adventure.getSection(9).choices();
        Choice lacksItemChoice = choices.get(1);
        assertThat(lacksItemChoice.condition())
            .as("second choice should have a LacksItemCondition present")
            .isPresent();
        assertThat(lacksItemChoice.condition().get())
            .as("second choice condition should be LacksItemCondition")
            .isInstanceOf(LacksItemCondition.class);
        LacksItemCondition condition = (LacksItemCondition) lacksItemChoice.condition().get();
        assertThat(condition.itemName())
            .as("LacksItemCondition must parse 'item' alias as itemName")
            .isEqualTo("Shield");
    }

    @Test
    void has_gold_condition_parses_minimum_from_amount_alias_field() {
        List<Choice> choices = adventure.getSection(9).choices();
        Choice hasGoldChoice = choices.get(2);
        assertThat(hasGoldChoice.condition())
            .as("third choice should have a GoldCondition present")
            .isPresent();
        assertThat(hasGoldChoice.condition().get())
            .as("third choice condition should be GoldCondition")
            .isInstanceOf(GoldCondition.class);
        GoldCondition condition = (GoldCondition) hasGoldChoice.condition().get();
        assertThat(condition.minimum())
            .as("GoldCondition minimum must be 10 (parsed from 'amount' alias field)")
            .isEqualTo(10);
    }

    @Test
    void stat_condition_parses_correctly() {
        List<Choice> choices = adventure.getSection(9).choices();
        Choice statChoice = choices.get(3);
        assertThat(statChoice.condition())
            .as("fourth choice should have a StatCondition present")
            .isPresent();
        assertThat(statChoice.condition().get())
            .as("fourth choice condition should be StatCondition")
            .isInstanceOf(StatCondition.class);
        StatCondition condition = (StatCondition) statChoice.condition().get();
        assertThat(condition.attribute())
            .as("StatCondition attribute must be SKILL")
            .isEqualTo(com.tas.neo.domain.player.AttributeType.SKILL);
    }

    @Test
    void state_equals_condition_parses_key_and_value() {
        List<Choice> choices = adventure.getSection(9).choices();
        Choice stateChoice = choices.get(4);
        assertThat(stateChoice.condition())
            .as("fifth choice should have a StateEqualsCondition present")
            .isPresent();
        assertThat(stateChoice.condition().get())
            .as("fifth choice condition should be StateEqualsCondition")
            .isInstanceOf(StateEqualsCondition.class);
        StateEqualsCondition condition = (StateEqualsCondition) stateChoice.condition().get();
        assertThat(condition.key())
            .as("StateEqualsCondition key must be 'questPhase'")
            .isEqualTo("questPhase");
        assertThat(condition.value().toString())
            .as("StateEqualsCondition value must be 'started'")
            .isEqualTo("started");
    }
}
