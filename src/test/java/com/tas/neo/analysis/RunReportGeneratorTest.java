package com.tas.neo.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.HasItemCondition;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.item.Item;
import com.tas.neo.domain.item.ItemCategory;
import com.tas.neo.mechanics.FixedDice;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RunReportGenerator} focusing on the three sections added for T2-2:
 * ITEM FLOW AT CHAPTER BOUNDARIES, GOLD AND STATE DISTRIBUTION AT CHAPTER BOUNDARIES, GATING.
 */
class RunReportGeneratorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Fixture builders
    // -------------------------------------------------------------------------

    private static Adventure adventureWithItems(String... itemNames) {
        List<Item> items = List.of(itemNames).stream()
            .map(n -> new Item(n, "", ItemCategory.KEY, false, ScriptBlock.empty()))
            .toList();
        Section s1 = new Section(1, "Start.", List.of(),
            List.of(Choice.to("Go", new SectionTarget(2))),
            SectionType.NORMAL, ScriptBlock.empty());
        Section s2 = new Section(2, "End.", List.of(), List.of(),
            SectionType.VICTORY, ScriptBlock.empty());
        return new Adventure("test", "Test", "", 1, 0,
            List.of(s1, s2), items, List.of(), List.of(), List.of(), ScriptBlock.empty());
    }

    private static Adventure adventureWithConditionedChoice(String itemName) {
        Section s1 = new Section(1, "Start.", List.of(),
            List.of(
                Choice.to("Unconditioned", new SectionTarget(2)),
                Choice.to("Use " + itemName, new SectionTarget(3),
                    new HasItemCondition(itemName))
            ),
            SectionType.NORMAL, ScriptBlock.empty());
        Section s2 = new Section(2, "End A.", List.of(), List.of(),
            SectionType.VICTORY, ScriptBlock.empty());
        Section s3 = new Section(3, "End B.", List.of(), List.of(),
            SectionType.VICTORY, ScriptBlock.empty());
        return new Adventure("test", "Test", "", 1, 0,
            List.of(s1, s2, s3), List.of(), List.of(), List.of(), List.of(), ScriptBlock.empty());
    }

    private static JsonNode chaptersJson(String... chapterIds) {
        ArrayNode array = MAPPER.createArrayNode();
        int rangeStart = 1;
        for (int i = 0; i < chapterIds.length; i++) {
            ObjectNode ch = array.addObject();
            ch.put("id", chapterIds[i]);
            ObjectNode range = ch.putObject("sectionRange");
            range.put("from", rangeStart);
            range.put("to", rangeStart + 49);
            if (i > 0) {
                ObjectNode gates = ch.putObject("gates");
                ObjectNode entryGate = gates.putObject("entryGate");
                entryGate.put("entrySection", rangeStart);
            }
            rangeStart += 50;
        }
        return MAPPER.createObjectNode().set("chapters", array);
    }

    private static JsonNode noChaptersJson() {
        return MAPPER.createObjectNode();
    }

    private static RunConfiguration config(int runs) {
        return new RunConfiguration(runs, new RandomChoiceSelector(),
            new FixedDice(3), 42L, 5, OptionalInt.empty(), OptionalInt.empty());
    }

    private static ChapterSnapshot snapshot(String chId, Set<String> inventory,
                                              int gold, Map<String, Object> state) {
        return new ChapterSnapshot(chId, 1, inventory, gold, state);
    }

    private static RunResult victory(List<ChapterSnapshot> snapshots) {
        return new RunResult(RunOutcome.VICTORY, 2, List.of(1, 2), snapshots, List.of());
    }

    // -------------------------------------------------------------------------
    // ITEM FLOW AT CHAPTER BOUNDARIES
    // -------------------------------------------------------------------------

    @Test
    void item_flow_section_header_present_when_chapters_exist() {
        Adventure adv = adventureWithItems("Sword");
        JsonNode raw = chaptersJson("ch1", "ch2");
        RunBatchResult result = new RunBatchResult(List.of(
            victory(List.of(snapshot("ch1", Set.of(), 0, Map.of()),
                            snapshot("ch2", Set.of("Sword"), 0, Map.of())))
        ));

        String report = RunReportGenerator.generate(adv, raw, result, config(1));

        assertThat(report)
            .as("report must include ITEM FLOW AT CHAPTER BOUNDARIES section when chapters exist")
            .contains("ITEM FLOW AT CHAPTER BOUNDARIES");
    }

    @Test
    void item_flow_shows_item_with_nonzero_carry_rate() {
        Adventure adv = adventureWithItems("Guard's Pass");
        JsonNode raw = chaptersJson("ch1", "ch2");
        RunBatchResult result = new RunBatchResult(List.of(
            victory(List.of(snapshot("ch1", Set.of(), 0, Map.of()),
                            snapshot("ch2", Set.of("Guard's Pass"), 0, Map.of()))),
            victory(List.of(snapshot("ch1", Set.of(), 0, Map.of()),
                            snapshot("ch2", Set.of(), 0, Map.of())))
        ));

        String report = RunReportGenerator.generate(adv, raw, result, config(2));

        assertThat(report)
            .as("ITEM FLOW must show items with at least one nonzero carry rate")
            .contains("Guard's Pass");
    }

    @Test
    void item_flow_omits_item_never_carried_at_any_boundary() {
        Adventure adv = adventureWithItems("Rare Gem");
        JsonNode raw = chaptersJson("ch1", "ch2");
        RunBatchResult result = new RunBatchResult(List.of(
            victory(List.of(snapshot("ch1", Set.of(), 0, Map.of()),
                            snapshot("ch2", Set.of(), 0, Map.of())))
        ));

        String report = RunReportGenerator.generate(adv, raw, result, config(1));

        assertThat(report)
            .as("ITEM FLOW must omit items that were never carried at any chapter boundary")
            .doesNotContain("Rare Gem");
    }

    @Test
    void item_flow_shows_dash_for_zero_carry_rate_at_chapter() {
        Adventure adv = adventureWithItems("Key");
        JsonNode raw = chaptersJson("ch1", "ch2", "ch3");
        // Key carried at ch2 but not ch1 or ch3
        RunBatchResult result = new RunBatchResult(List.of(
            victory(List.of(
                snapshot("ch1", Set.of(), 0, Map.of()),
                snapshot("ch2", Set.of("Key"), 0, Map.of()),
                snapshot("ch3", Set.of(), 0, Map.of())
            ))
        ));

        String report = RunReportGenerator.generate(adv, raw, result, config(1));

        assertThat(report)
            .as("ITEM FLOW must show '—' for chapters where the item was never carried")
            .contains("—");
    }

    @Test
    void item_flow_section_absent_when_no_chapters() {
        Adventure adv = adventureWithItems("Sword");
        JsonNode raw = noChaptersJson();
        RunBatchResult result = new RunBatchResult(List.of(
            victory(List.of())
        ));

        String report = RunReportGenerator.generate(adv, raw, result, config(1));

        assertThat(report)
            .as("ITEM FLOW section must be omitted when no chapters are defined")
            .doesNotContain("ITEM FLOW AT CHAPTER BOUNDARIES");
    }

    // -------------------------------------------------------------------------
    // GOLD AND STATE DISTRIBUTION AT CHAPTER BOUNDARIES
    // -------------------------------------------------------------------------

    @Test
    void gold_distribution_section_present_when_chapters_exist() {
        Adventure adv = adventureWithItems();
        JsonNode raw = chaptersJson("ch1", "ch2");
        RunBatchResult result = new RunBatchResult(List.of(
            victory(List.of(snapshot("ch2", Set.of(), 15, Map.of())))
        ));

        String report = RunReportGenerator.generate(adv, raw, result, config(1));

        assertThat(report)
            .as("report must include GOLD AND STATE DISTRIBUTION section when chapters exist")
            .contains("GOLD AND STATE DISTRIBUTION AT CHAPTER BOUNDARIES");
    }

    @Test
    void gold_distribution_shows_avg_and_range() {
        Adventure adv = adventureWithItems();
        JsonNode raw = chaptersJson("ch1", "ch2");
        RunBatchResult result = new RunBatchResult(List.of(
            victory(List.of(snapshot("ch2", Set.of(), 5, Map.of()))),
            victory(List.of(snapshot("ch2", Set.of(), 15, Map.of())))
        ));

        String report = RunReportGenerator.generate(adv, raw, result, config(2));

        assertThat(report)
            .as("gold distribution must show the chapter id in the line")
            .contains("ch2");
        assertThat(report)
            .as("gold distribution must show average value")
            .contains("10");
        assertThat(report)
            .as("gold distribution must show min–max range")
            .contains("5")
            .contains("15");
    }

    @Test
    void state_distribution_numeric_shows_avg_and_range() {
        Adventure adv = adventureWithItems();
        JsonNode raw = chaptersJson("ch1", "ch2");
        RunBatchResult result = new RunBatchResult(List.of(
            victory(List.of(snapshot("ch2", Set.of(), 0, Map.of("suspicion", 2)))),
            victory(List.of(snapshot("ch2", Set.of(), 0, Map.of("suspicion", 8))))
        ));

        String report = RunReportGenerator.generate(adv, raw, result, config(2));

        assertThat(report)
            .as("state distribution must include variable name 'suspicion'")
            .contains("suspicion");
    }

    @Test
    void state_distribution_boolean_shows_true_false_percentages() {
        Adventure adv = adventureWithItems();
        JsonNode raw = chaptersJson("ch1", "ch2");
        RunBatchResult result = new RunBatchResult(List.of(
            victory(List.of(snapshot("ch2", Set.of(), 0, Map.of("contactAlive", true)))),
            victory(List.of(snapshot("ch2", Set.of(), 0, Map.of("contactAlive", false))))
        ));

        String report = RunReportGenerator.generate(adv, raw, result, config(2));

        assertThat(report)
            .as("boolean state distribution must include variable name 'contactAlive'")
            .contains("contactAlive");
        assertThat(report)
            .as("boolean state distribution must include 'true' label")
            .contains("true:");
        assertThat(report)
            .as("boolean state distribution must include 'false' label")
            .contains("false:");
    }

    @Test
    void state_distribution_omits_variables_never_set() {
        Adventure adv = adventureWithItems();
        JsonNode raw = chaptersJson("ch1", "ch2");
        // ch2 snapshots have no state variables
        RunBatchResult result = new RunBatchResult(List.of(
            victory(List.of(snapshot("ch2", Set.of(), 0, Map.of())))
        ));

        String report = RunReportGenerator.generate(adv, raw, result, config(1));

        assertThat(report)
            .as("state distribution must not include 'neverSet' when variable was never set")
            .doesNotContain("neverSet");
    }

    // -------------------------------------------------------------------------
    // GATING
    // -------------------------------------------------------------------------

    @Test
    void gating_section_always_present() {
        Adventure adv = adventureWithItems();
        JsonNode raw = noChaptersJson();
        RunBatchResult result = new RunBatchResult(List.of(
            victory(List.of())
        ));

        String report = RunReportGenerator.generate(adv, raw, result, config(1));

        assertThat(report)
            .as("GATING section must always appear in the report")
            .contains("GATING");
    }

    @Test
    void gating_shows_none_when_all_conditioned_choices_met() {
        Adventure adv = adventureWithConditionedChoice("Sword");
        JsonNode raw = noChaptersJson();
        // Conditioned choice was available (condition was met) in this run
        NeverSelectedChoice condChoice = new NeverSelectedChoice(1, "Use Sword");
        RunBatchResult result = new RunBatchResult(
            List.of(victory(List.of())),
            Set.of(condChoice),  // was available
            Set.of(condChoice)   // also selected
        );

        String report = RunReportGenerator.generate(adv, raw, result, config(1));

        assertThat(report)
            .as("GATING must show 'none ✓' when all conditioned choices were met at least once")
            .contains("none ✓");
    }

    @Test
    void gating_lists_choices_whose_condition_was_never_met() {
        Adventure adv = adventureWithConditionedChoice("Rare Key");
        JsonNode raw = noChaptersJson();
        // Conditioned choice was never available (never in everAvailable)
        RunBatchResult result = new RunBatchResult(
            List.of(victory(List.of())),
            Set.of(),  // never available
            Set.of()   // never selected
        );

        String report = RunReportGenerator.generate(adv, raw, result, config(1));

        assertThat(report)
            .as("GATING must list choices whose condition was never met")
            .contains("§1");
        assertThat(report)
            .as("GATING must include the choice text for the never-met conditioned choice")
            .contains("Use Rare Key");
    }

    @Test
    void gating_does_not_duplicate_choices_that_were_available_but_never_selected() {
        // A conditioned choice that WAS available (condition met) but never selected
        // should NOT appear in GATING (it appears in ISSUES as neverSelected)
        Adventure adv = adventureWithConditionedChoice("Common Sword");
        JsonNode raw = noChaptersJson();
        NeverSelectedChoice condChoice = new NeverSelectedChoice(1, "Use Common Sword");
        RunBatchResult result = new RunBatchResult(
            List.of(victory(List.of())),
            Set.of(condChoice),  // was available
            Set.of()             // never selected
        );

        String report = RunReportGenerator.generate(adv, raw, result, config(1));

        // The choice's condition WAS met (it was available), so GATING should show none ✓
        assertThat(report)
            .as("GATING must show 'none ✓' for conditioned choices that were available " +
                "at least once, even if never selected")
            .contains("none ✓");
    }
}
