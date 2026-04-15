package com.tas.neo.loader;

import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.GoldCondition;
import com.tas.neo.domain.adventure.event.CombatEvent;
import com.tas.neo.domain.adventure.event.ItemAction;
import com.tas.neo.domain.adventure.event.ItemEvent;
import com.tas.neo.domain.adventure.event.LuckTestEvent;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.adventure.event.SkillTestEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip JSON parsing tests for individual event and condition types.
 *
 * <p>Each test builds a minimal complete adventure JSON string, writes it to a
 * temp file, loads it through {@link JsonAdventureLoader}, and inspects the
 * resulting domain objects.
 *
 * <p>This test verifies the alias field names: {@code item} (instead of
 * {@code itemName}), {@code failureSection} (instead of {@code failSection}),
 * and {@code amount} (instead of {@code minimum}).
 */
class JsonEventParsingTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Writes a JSON string to {@code tempDir/<id>.json} and loads it.
     * Section 99 is used as a throwaway VICTORY terminal section.
     */
    private Adventure loadJson(String id, String json) throws AdventureLoadException, IOException {
        Path file = tempDir.resolve(id + ".json");
        Files.writeString(file, json);
        return new JsonAdventureLoader(tempDir).load(id);
    }

    /**
     * Builds a minimal adventure JSON that contains exactly one event in section 1
     * and one terminal section so the loader validates correctly.
     *
     * <p>The {@code eventJson} block must not reference items unless the adventure
     * declares them.  Use {@code itemsJson} to include item definitions.
     *
     * <p>Section 1 is given a choice to section 99 so the "no exit" validator is
     * satisfied for ITEM_GAIN and ITEM_LOSS events (which do not themselves provide
     * navigation).
     */
    private String adventureWithEvent(String id, String eventJson, String itemsJson,
                                       String extraSections) {
        String items = (itemsJson == null || itemsJson.isBlank()) ? "[]" : itemsJson;
        String extra = (extraSections == null || extraSections.isBlank()) ? "" : "," + extraSections;
        return """
            {
              "id": "%s",
              "title": "Test",
              "startSection": 1,
              "items": %s,
              "sections": [
                {
                  "number": 1,
                  "type": "NORMAL",
                  "narrative": ".",
                  "events": [ %s ],
                  "choices": [
                    { "text": "Continue", "targetSection": 99 }
                  ]
                }%s,
                {
                  "number": 99,
                  "type": "VICTORY",
                  "narrative": "You win."
                }
              ]
            }
            """.formatted(id, items, eventJson, extra);
    }

    /**
     * Builds a minimal adventure JSON where section 1 has no events but has
     * a single choice whose condition is the given JSON.
     */
    private String adventureWithConditionChoice(String id, String conditionJson) {
        return """
            {
              "id": "%s",
              "title": "Test",
              "startSection": 1,
              "items": [],
              "sections": [
                {
                  "number": 1,
                  "type": "NORMAL",
                  "narrative": ".",
                  "choices": [
                    {
                      "text": "Go",
                      "targetSection": 99,
                      "condition": %s
                    }
                  ]
                },
                {
                  "number": 99,
                  "type": "VICTORY",
                  "narrative": "You win."
                }
              ]
            }
            """.formatted(id, conditionJson);
    }

    // -------------------------------------------------------------------------
    // ITEM_GAIN — uses alias field 'item'
    // -------------------------------------------------------------------------

    @Test
    void item_gain_event_with_item_alias_field_parses_action_GAIN_and_name() throws Exception {
        String eventJson = """
            { "type": "ITEM_GAIN", "item": "Torch" }
            """;
        String itemsJson = """
            [{ "name": "Torch", "description": "A torch." }]
            """;
        Adventure adventure = loadJson("item-gain-alias", adventureWithEvent("item-gain-alias",
            eventJson, itemsJson, ""));

        List<SectionEvent> events = adventure.getSection(1).events();
        assertThat(events).as("section 1 must have exactly one event").hasSize(1);
        assertThat(events.get(0)).as("event must be ItemEvent").isInstanceOf(ItemEvent.class);
        ItemEvent item = (ItemEvent) events.get(0);
        assertThat(item.action())
            .as("ITEM_GAIN must produce ItemEvent with action GAIN")
            .isEqualTo(ItemAction.GAIN);
        assertThat(item.itemName())
            .as("ITEM_GAIN must produce ItemEvent with itemName matching 'item' alias field")
            .isEqualTo("Torch");
    }

    // -------------------------------------------------------------------------
    // ITEM_LOSS — uses alias field 'item'
    // -------------------------------------------------------------------------

    @Test
    void item_loss_event_with_item_alias_field_parses_action_LOSS() throws Exception {
        String eventJson = """
            { "type": "ITEM_LOSS", "item": "Torch" }
            """;
        String itemsJson = """
            [{ "name": "Torch", "description": "A torch." }]
            """;
        Adventure adventure = loadJson("item-loss-alias", adventureWithEvent("item-loss-alias",
            eventJson, itemsJson, ""));

        SectionEvent event = adventure.getSection(1).events().get(0);
        assertThat(event).as("event must be ItemEvent").isInstanceOf(ItemEvent.class);
        assertThat(((ItemEvent) event).action())
            .as("ITEM_LOSS must produce ItemEvent with action LOSS")
            .isEqualTo(ItemAction.LOSS);
    }

    // -------------------------------------------------------------------------
    // LUCK_TEST — uses 'failureSection' alias
    // -------------------------------------------------------------------------

    @Test
    void luck_test_event_with_failureSection_alias_parses_both_sections() throws Exception {
        String eventJson = """
            { "type": "LUCK_TEST", "successSection": 10, "failureSection": 20 }
            """;
        String extraSections = """
            {
              "number": 10,
              "type": "VICTORY",
              "narrative": "Lucky win."
            },
            {
              "number": 20,
              "type": "NORMAL",
              "narrative": "Bad luck.",
              "choices": [{ "text": "Done", "targetSection": 99 }]
            }
            """;
        Adventure adventure = loadJson("luck-test-alias",
            adventureWithEvent("luck-test-alias", eventJson, null, extraSections));

        SectionEvent event = adventure.getSection(1).events().get(0);
        assertThat(event).as("event must be LuckTestEvent").isInstanceOf(LuckTestEvent.class);
        LuckTestEvent luckEvent = (LuckTestEvent) event;
        assertThat(luckEvent.successSection())
            .as("LuckTestEvent successSection must be 10")
            .isEqualTo(10);
        assertThat(luckEvent.failSection())
            .as("LuckTestEvent failSection must be 20 (parsed from 'failureSection' alias)")
            .isEqualTo(20);
    }

    // -------------------------------------------------------------------------
    // SKILL_TEST — uses 'failureSection' alias
    // -------------------------------------------------------------------------

    @Test
    void skill_test_event_with_failureSection_alias_parses_both_sections() throws Exception {
        String eventJson = """
            { "type": "SKILL_TEST", "successSection": 10, "failureSection": 20 }
            """;
        String extraSections = """
            {
              "number": 10,
              "type": "VICTORY",
              "narrative": "Skilled win."
            },
            {
              "number": 20,
              "type": "NORMAL",
              "narrative": "Failed skill.",
              "choices": [{ "text": "Done", "targetSection": 99 }]
            }
            """;
        Adventure adventure = loadJson("skill-test-alias",
            adventureWithEvent("skill-test-alias", eventJson, null, extraSections));

        SectionEvent event = adventure.getSection(1).events().get(0);
        assertThat(event).as("event must be SkillTestEvent").isInstanceOf(SkillTestEvent.class);
        SkillTestEvent skillEvent = (SkillTestEvent) event;
        assertThat(skillEvent.successSection())
            .as("SkillTestEvent successSection must be 10")
            .isEqualTo(10);
        assertThat(skillEvent.failSection())
            .as("SkillTestEvent failSection must be 20 (parsed from 'failureSection' alias)")
            .isEqualTo(20);
    }

    // -------------------------------------------------------------------------
    // COMBAT — 'enemy' single-object format with successSection/failureSection
    // -------------------------------------------------------------------------

    @Test
    void combat_event_with_enemy_object_and_sections_parses_opponent_and_navigation() throws Exception {
        String eventJson = """
            {
              "type": "COMBAT",
              "enemy": { "name": "Orc", "skill": 7, "stamina": 8 },
              "successSection": 30,
              "failureSection": 40
            }
            """;
        String extraSections = """
            {
              "number": 30,
              "type": "VICTORY",
              "narrative": "Victory!"
            },
            {
              "number": 40,
              "type": "NORMAL",
              "narrative": "Defeated.",
              "choices": [{ "text": "Done", "targetSection": 99 }]
            }
            """;
        Adventure adventure = loadJson("combat-enemy-alias",
            adventureWithEvent("combat-enemy-alias", eventJson, null, extraSections));

        SectionEvent event = adventure.getSection(1).events().get(0);
        assertThat(event).as("event must be CombatEvent").isInstanceOf(CombatEvent.class);
        CombatEvent combatEvent = (CombatEvent) event;
        assertThat(combatEvent.opponents())
            .as("CombatEvent must have exactly one opponent parsed from 'enemy' object")
            .hasSize(1);
        assertThat(combatEvent.opponents().get(0).name())
            .as("opponent name must be 'Orc'")
            .isEqualTo("Orc");
        assertThat(combatEvent.successSection())
            .as("CombatEvent successSection must be 30")
            .isEqualTo(30);
        assertThat(combatEvent.failureSection())
            .as("CombatEvent failureSection must be 40")
            .isEqualTo(40);
    }

    // -------------------------------------------------------------------------
    // HAS_GOLD condition — 'amount' alias for 'minimum'
    // -------------------------------------------------------------------------

    @Test
    void has_gold_condition_with_amount_alias_field_parses_minimum() throws Exception {
        String conditionJson = """
            { "type": "HAS_GOLD", "amount": 25 }
            """;
        Adventure adventure = loadJson("has-gold-alias",
            adventureWithConditionChoice("has-gold-alias", conditionJson));

        var choices = adventure.getSection(1).choices();
        assertThat(choices).as("section 1 must have one choice").hasSize(1);
        assertThat(choices.get(0).condition())
            .as("choice must have a condition present")
            .isPresent();
        assertThat(choices.get(0).condition().get())
            .as("condition must be GoldCondition")
            .isInstanceOf(GoldCondition.class);
        GoldCondition gold = (GoldCondition) choices.get(0).condition().get();
        assertThat(gold.minimum())
            .as("GoldCondition minimum must be 25 (parsed from 'amount' alias field)")
            .isEqualTo(25);
    }
}
