package com.tas.neo.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.GoldCondition;
import com.tas.neo.domain.adventure.HasItemCondition;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.adventure.event.GoldChangeEvent;
import com.tas.neo.domain.adventure.event.ItemAction;
import com.tas.neo.domain.adventure.event.ItemEvent;
import com.tas.neo.domain.adventure.event.NavigateEvent;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.adventure.event.SkillTestEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdventureSectionInspectorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Construction helpers — domain objects
    // -------------------------------------------------------------------------

    private static Adventure adventureWith(Section... sections) {
        return new Adventure(
            "test", "Test", "", sections[0].number(), 0,
            List.of(sections), List.of(), List.of(), List.of(), List.of(),
            ScriptBlock.empty()
        );
    }

    private static Section normalSection(int number, List<SectionEvent> events,
                                         List<Choice> choices, ScriptBlock scripts) {
        return new Section(number, "Narrative for " + number + ".", events, choices,
            SectionType.NORMAL, scripts);
    }

    private static Section victorySection(int number) {
        return new Section(number, "Victory.", List.of(), List.of(),
            SectionType.VICTORY, ScriptBlock.empty());
    }

    private static Choice choiceTo(int target) {
        return Choice.to("go", new SectionTarget(target));
    }

    private static Choice conditionedChoiceTo(int target, com.tas.neo.domain.adventure.Condition cond) {
        return Choice.to("go", new SectionTarget(target), cond);
    }

    private static ScriptBlock onEnter(String script) {
        return new ScriptBlock(Map.of("onEnter", script));
    }

    // -------------------------------------------------------------------------
    // Construction helpers — raw JSON
    // -------------------------------------------------------------------------

    private static ObjectNode sectionJson(int number) {
        ObjectNode s = MAPPER.createObjectNode();
        s.put("number", number);
        s.put("type", "NORMAL");
        s.put("narrative", "Narrative for section " + number + ".");
        s.set("events", MAPPER.createArrayNode());
        s.set("choices", MAPPER.createArrayNode());
        return s;
    }

    private static ObjectNode adventureJson(ObjectNode... sections) {
        ArrayNode arr = MAPPER.createArrayNode();
        for (ObjectNode s : sections) arr.add(s);
        ObjectNode root = MAPPER.createObjectNode();
        root.put("id", "test");
        root.set("sections", arr);
        root.set("chapters", MAPPER.createArrayNode());
        return root;
    }

    private static ObjectNode chapterJson(String id, int from, int to, ObjectNode gates) {
        ObjectNode range = MAPPER.createObjectNode().put("from", from).put("to", to);
        ObjectNode ch = MAPPER.createObjectNode();
        ch.put("id", id);
        ch.set("sectionRange", range);
        ch.set("gates", gates);
        return ch;
    }

    private static ObjectNode adventureJsonWithChapters(ObjectNode[] sections, ObjectNode[] chapters) {
        ArrayNode sArr = MAPPER.createArrayNode();
        for (ObjectNode s : sections) sArr.add(s);
        ArrayNode cArr = MAPPER.createArrayNode();
        for (ObjectNode c : chapters) cArr.add(c);
        ObjectNode root = MAPPER.createObjectNode();
        root.put("id", "test");
        root.set("sections", sArr);
        root.set("chapters", cArr);
        return root;
    }

    // -------------------------------------------------------------------------
    // inspectSections — read raw JSON for one or more section numbers
    // -------------------------------------------------------------------------

    @Test
    void inspectSections_single_known_section_output_contains_number() {
        ObjectNode raw = adventureJson(sectionJson(88), sectionJson(99));

        String output = AdventureSectionInspector.inspectSections(raw, List.of(88));

        assertThat(output)
            .as("output must identify the requested section number")
            .contains("88");
    }

    @Test
    void inspectSections_multiple_sections_all_present_in_output() {
        ObjectNode raw = adventureJson(sectionJson(10), sectionJson(20), sectionJson(30));

        String output = AdventureSectionInspector.inspectSections(raw, List.of(10, 30));

        assertThat(output)
            .as("output must include both requested section numbers")
            .contains("10", "30");
        assertThat(output)
            .as("output must not include the unrequested section 20")
            .doesNotContain("\"number\" : 20");
    }

    @Test
    void inspectSections_unknown_number_reports_not_found() {
        ObjectNode raw = adventureJson(sectionJson(1));

        String output = AdventureSectionInspector.inspectSections(raw, List.of(999));

        assertThat(output)
            .as("output must indicate that section 999 was not found")
            .containsIgnoringCase("999")
            .containsIgnoringCase("not found");
    }

    // -------------------------------------------------------------------------
    // inspectRefs — inbound references to a section
    // -------------------------------------------------------------------------

    @Test
    void inspectRefs_choice_edge_detected() {
        // §1 has a choice → §2; inspectRefs(2) must mention §1
        Section s1 = normalSection(1, List.of(), List.of(choiceTo(2)), ScriptBlock.empty());
        Section s2 = victorySection(2);
        Adventure adv = adventureWith(s1, s2);
        SectionGraph graph = SectionGraph.of(adv);

        String output = AdventureSectionInspector.inspectRefs(adv, graph, 2);

        assertThat(output)
            .as("output must identify §1 as having a reference to §2")
            .contains("1");
    }

    @Test
    void inspectRefs_event_edge_detected() {
        // §5 has a NavigateEvent → §9; inspectRefs(9) must mention §5
        Section s5 = normalSection(5, List.of(new NavigateEvent(9)), List.of(), ScriptBlock.empty());
        Section s9 = victorySection(9);
        Adventure adv = adventureWith(s5, s9);
        SectionGraph graph = SectionGraph.of(adv);

        String output = AdventureSectionInspector.inspectRefs(adv, graph, 9);

        assertThat(output)
            .as("output must identify §5 as having a NavigateEvent to §9")
            .contains("5");
    }

    @Test
    void inspectRefs_multiple_inbound_edges_all_reported() {
        // §1 and §2 both reference §3
        Section s1 = normalSection(1, List.of(), List.of(choiceTo(3)), ScriptBlock.empty());
        Section s2 = normalSection(2, List.of(), List.of(choiceTo(3)), ScriptBlock.empty());
        Section s3 = victorySection(3);
        Adventure adv = adventureWith(s1, s2, s3);
        SectionGraph graph = SectionGraph.of(adv);

        String output = AdventureSectionInspector.inspectRefs(adv, graph, 3);

        assertThat(output)
            .as("output must identify both §1 and §2 as references to §3")
            .contains("1", "2");
    }

    @Test
    void inspectRefs_no_inbound_edges_reports_none() {
        // §99 has no inbound edges
        Section s1 = normalSection(1, List.of(), List.of(choiceTo(2)), ScriptBlock.empty());
        Section s2 = victorySection(2);
        Section s99 = victorySection(99);
        Adventure adv = adventureWith(s1, s2, s99);
        SectionGraph graph = SectionGraph.of(adv);

        String output = AdventureSectionInspector.inspectRefs(adv, graph, 99);

        assertThat(output)
            .as("output must indicate that no sections link to §99")
            .containsIgnoringCase("none");
    }

    // -------------------------------------------------------------------------
    // inspectByEvent — find sections by event type and optional item filter
    // -------------------------------------------------------------------------

    @Test
    void inspectByEvent_item_gain_finds_correct_section() {
        Section s1 = normalSection(10, List.of(new ItemEvent("Sword", ItemAction.GAIN, 1)),
            List.of(choiceTo(11)), ScriptBlock.empty());
        Section s2 = normalSection(11, List.of(), List.of(), ScriptBlock.empty());
        Adventure adv = adventureWith(s1, s2);

        String output = AdventureSectionInspector.inspectByEvent(adv, "ITEM_GAIN", null);

        assertThat(output)
            .as("output must identify §10 as having an ITEM_GAIN event")
            .contains("10");
    }

    @Test
    void inspectByEvent_item_filter_excludes_non_matching_item() {
        Section s1 = normalSection(10, List.of(new ItemEvent("Sword", ItemAction.GAIN, 1)),
            List.of(choiceTo(20)), ScriptBlock.empty());
        Section s2 = normalSection(20, List.of(new ItemEvent("Shield", ItemAction.GAIN, 1)),
            List.of(), ScriptBlock.empty());
        Adventure adv = adventureWith(s1, s2);

        String output = AdventureSectionInspector.inspectByEvent(adv, "ITEM_GAIN", "Shield");

        assertThat(output)
            .as("item filter 'Shield' must include §20")
            .contains("20");
        assertThat(output)
            .as("item filter 'Shield' must exclude §10 which gains 'Sword'")
            .doesNotContain("§10");
    }

    @Test
    void inspectByEvent_no_matches_reports_none() {
        Section s1 = normalSection(1, List.of(new GoldChangeEvent(-3)),
            List.of(choiceTo(2)), ScriptBlock.empty());
        Section s2 = victorySection(2);
        Adventure adv = adventureWith(s1, s2);

        String output = AdventureSectionInspector.inspectByEvent(adv, "SKILL_TEST", null);

        assertThat(output)
            .as("output must indicate no SKILL_TEST events were found")
            .containsIgnoringCase("none");
    }

    @Test
    void inspectByEvent_skill_test_event_found() {
        Section s1 = normalSection(5, List.of(new SkillTestEvent(10, 20)),
            List.of(), ScriptBlock.empty());
        Section s2 = victorySection(10);
        Section s3 = victorySection(20);
        Adventure adv = adventureWith(s1, s2, s3);

        String output = AdventureSectionInspector.inspectByEvent(adv, "SKILL_TEST", null);

        assertThat(output)
            .as("output must identify §5 as having a SKILL_TEST event")
            .contains("5");
    }

    // -------------------------------------------------------------------------
    // inspectByCondition — find sections by choice condition type
    // -------------------------------------------------------------------------

    @Test
    void inspectByCondition_has_gold_finds_correct_section() {
        Section s1 = normalSection(7,
            List.of(),
            List.of(conditionedChoiceTo(8, new GoldCondition(5))),
            ScriptBlock.empty());
        Section s2 = victorySection(8);
        Adventure adv = adventureWith(s1, s2);

        String output = AdventureSectionInspector.inspectByCondition(adv, "GoldCondition");

        assertThat(output)
            .as("output must identify §7 as having a GoldCondition choice")
            .contains("7");
    }

    @Test
    void inspectByCondition_has_item_finds_section_with_item_filter() {
        Section s1 = normalSection(3,
            List.of(),
            List.of(conditionedChoiceTo(4, new HasItemCondition("Key"))),
            ScriptBlock.empty());
        Section s2 = victorySection(4);
        Adventure adv = adventureWith(s1, s2);

        String output = AdventureSectionInspector.inspectByCondition(adv, "HasItemCondition");

        assertThat(output)
            .as("output must identify §3 as having a HasItemCondition choice")
            .contains("3");
    }

    // -------------------------------------------------------------------------
    // inspectDeadEnds — NORMAL sections with no successors
    // -------------------------------------------------------------------------

    @Test
    void inspectDeadEnds_normal_section_with_no_successors_is_flagged() {
        // §5 is NORMAL with no choices, no events, no scripts — a dead end
        Section s1 = normalSection(1, List.of(), List.of(choiceTo(5)), ScriptBlock.empty());
        Section s5 = new Section(5, ".", List.of(), List.of(), SectionType.NORMAL, ScriptBlock.empty());
        Adventure adv = adventureWith(s1, s5);
        SectionGraph graph = SectionGraph.of(adv);

        String output = AdventureSectionInspector.inspectDeadEnds(adv, graph);

        assertThat(output)
            .as("NORMAL section §5 with no successors must be reported as a dead end")
            .contains("5");
    }

    @Test
    void inspectDeadEnds_victory_section_not_flagged() {
        // §2 is VICTORY — terminal by design, not a dead end
        Section s1 = normalSection(1, List.of(), List.of(choiceTo(2)), ScriptBlock.empty());
        Section s2 = victorySection(2);
        Adventure adv = adventureWith(s1, s2);
        SectionGraph graph = SectionGraph.of(adv);

        String output = AdventureSectionInspector.inspectDeadEnds(adv, graph);

        assertThat(output)
            .as("VICTORY section §2 must not appear in dead ends report")
            .doesNotContain("§2");
        assertThat(output)
            .as("no dead ends present — output must confirm none found")
            .containsIgnoringCase("none");
    }

    @Test
    void inspectDeadEnds_no_dead_ends_confirms_clean() {
        // 1 → 2 (victory), no NORMAL sections without exits
        Section s1 = normalSection(1, List.of(), List.of(choiceTo(2)), ScriptBlock.empty());
        Section s2 = victorySection(2);
        Adventure adv = adventureWith(s1, s2);
        SectionGraph graph = SectionGraph.of(adv);

        String output = AdventureSectionInspector.inspectDeadEnds(adv, graph);

        assertThat(output)
            .as("output must indicate no dead ends when none exist")
            .containsIgnoringCase("none");
    }

    // -------------------------------------------------------------------------
    // inspectState — sections touching a named state variable
    // -------------------------------------------------------------------------

    @Test
    void inspectState_finds_section_with_set_call() {
        Section s1 = normalSection(3,
            List.of(),
            List.of(choiceTo(4)),
            onEnter("state.set('visited', true)"));
        Section s2 = victorySection(4);
        Adventure adv = adventureWith(s1, s2);

        String output = AdventureSectionInspector.inspectState(adv, "visited");

        assertThat(output)
            .as("output must identify §3 as setting the 'visited' state variable")
            .contains("3");
    }

    @Test
    void inspectState_finds_section_with_get_call() {
        Section s1 = normalSection(7,
            List.of(),
            List.of(choiceTo(8)),
            onEnter("if state.get('suspicion') > 5 then ctx.navigateTo(9) end"));
        Section s2 = victorySection(8);
        Section s3 = victorySection(9);
        Adventure adv = adventureWith(s1, s2, s3);

        String output = AdventureSectionInspector.inspectState(adv, "suspicion");

        assertThat(output)
            .as("output must identify §7 as reading the 'suspicion' state variable")
            .contains("7");
    }

    @Test
    void inspectState_variable_not_present_reports_none() {
        Section s1 = normalSection(1, List.of(), List.of(choiceTo(2)), ScriptBlock.empty());
        Section s2 = victorySection(2);
        Adventure adv = adventureWith(s1, s2);

        String output = AdventureSectionInspector.inspectState(adv, "nonexistent");

        assertThat(output)
            .as("output must indicate that variable 'nonexistent' is not used anywhere")
            .containsIgnoringCase("none");
    }

    // -------------------------------------------------------------------------
    // inspectGate — raw JSON gate contracts for a chapter
    // -------------------------------------------------------------------------

    @Test
    void inspectGate_returns_gate_json_for_chapter() {
        ObjectNode entryGate = MAPPER.createObjectNode().put("entrySection", 41);
        ObjectNode gates = MAPPER.createObjectNode();
        gates.set("entryGate", entryGate);
        gates.set("exitGates", MAPPER.createArrayNode());

        ObjectNode ch = chapterJson("ch2", 41, 90, gates);
        ObjectNode raw = adventureJsonWithChapters(
            new ObjectNode[]{sectionJson(41)},
            new ObjectNode[]{ch}
        );

        String output = AdventureSectionInspector.inspectGate(raw, "ch2", null);

        assertThat(output)
            .as("gate output must include the chapter id")
            .contains("ch2");
        assertThat(output)
            .as("gate output must include the entrySection value")
            .contains("41");
    }

    @Test
    void inspectGate_unknown_chapter_reports_not_found() {
        ObjectNode raw = adventureJson(sectionJson(1));

        String output = AdventureSectionInspector.inspectGate(raw, "ch99", null);

        assertThat(output)
            .as("output must indicate that chapter ch99 was not found")
            .containsIgnoringCase("ch99")
            .containsIgnoringCase("not found");
    }

    @Test
    void inspectGate_direction_in_returns_entry_gate_content() {
        ObjectNode entryGate = MAPPER.createObjectNode().put("entrySection", 91);
        ObjectNode gates = MAPPER.createObjectNode();
        gates.set("entryGate", entryGate);
        gates.set("exitGates", MAPPER.createArrayNode());

        ObjectNode ch = chapterJson("ch3", 91, 130, gates);
        ObjectNode raw = adventureJsonWithChapters(
            new ObjectNode[]{sectionJson(91)},
            new ObjectNode[]{ch}
        );

        String output = AdventureSectionInspector.inspectGate(raw, "ch3", "in");

        assertThat(output)
            .as("direction=in must include entry gate content with entrySection 91")
            .contains("91");
        assertThat(output)
            .as("direction=in must not include exitGates section")
            .doesNotContain("exitGates");
    }
}
