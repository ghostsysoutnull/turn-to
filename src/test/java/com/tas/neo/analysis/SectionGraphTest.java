package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.adventure.event.CombatEvent;
import com.tas.neo.domain.adventure.event.LuckTestEvent;
import com.tas.neo.domain.adventure.event.NavigateEvent;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.adventure.event.SkillTestEvent;
import com.tas.neo.domain.adventure.event.StatChangeEvent;
import com.tas.neo.domain.player.AttributeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SectionGraphTest {

    // -------------------------------------------------------------------------
    // Construction helpers
    // -------------------------------------------------------------------------

    private static Adventure adventureWith(Section... sections) {
        return new Adventure(
            "test", "Test", "", sections[0].number(), 0,
            List.of(sections), List.of(), List.of(), List.of(), List.of(),
            ScriptBlock.empty()
        );
    }

    private static Section section(int number, List<SectionEvent> events,
                                   List<Choice> choices, ScriptBlock scripts) {
        return new Section(number, ".", events, choices, SectionType.NORMAL, scripts);
    }

    private static Section normal(int number) {
        return section(number, List.of(),
            List.of(Choice.to("→", new SectionTarget(number + 1))),
            ScriptBlock.empty());
    }

    private static Section leaf(int number) {
        return new Section(number, ".", List.of(), List.of(), SectionType.VICTORY, ScriptBlock.empty());
    }

    private static Choice choiceTo(int target) {
        return Choice.to("go", new SectionTarget(target));
    }

    private static ScriptBlock onEnter(String script) {
        return new ScriptBlock(Map.of("onEnter", script));
    }

    // -------------------------------------------------------------------------
    // successors — choices
    // -------------------------------------------------------------------------

    @Test
    void successors_choice_to_section_target() {
        Section s = section(1, List.of(), List.of(choiceTo(5), choiceTo(9)), ScriptBlock.empty());
        SectionGraph graph = SectionGraph.of(adventureWith(s, leaf(5), leaf(9)));

        assertThat(graph.successors(1))
            .as("successors must include all SectionTarget choice targets")
            .containsExactlyInAnyOrder(5, 9);
    }

    // -------------------------------------------------------------------------
    // successors — events
    // -------------------------------------------------------------------------

    @Test
    void successors_navigate_event() {
        Section s = section(1, List.of(new NavigateEvent(7)), List.of(), ScriptBlock.empty());
        SectionGraph graph = SectionGraph.of(adventureWith(s, leaf(7)));

        assertThat(graph.successors(1))
            .as("NavigateEvent targetSection must appear in successors")
            .contains(7);
    }

    @Test
    void successors_luck_test_event_includes_both_branches() {
        Section s = section(1, List.of(new LuckTestEvent(10, 20)), List.of(), ScriptBlock.empty());
        SectionGraph graph = SectionGraph.of(adventureWith(s, leaf(10), leaf(20)));

        assertThat(graph.successors(1))
            .as("LuckTestEvent must contribute both successSection and failSection")
            .containsExactlyInAnyOrder(10, 20);
    }

    @Test
    void successors_skill_test_event_includes_both_branches() {
        Section s = section(1, List.of(new SkillTestEvent(10, 20)), List.of(), ScriptBlock.empty());
        SectionGraph graph = SectionGraph.of(adventureWith(s, leaf(10), leaf(20)));

        assertThat(graph.successors(1))
            .as("SkillTestEvent must contribute both successSection and failSection")
            .containsExactlyInAnyOrder(10, 20);
    }

    @Test
    void successors_combat_event_includes_success_and_failure_sections() {
        CombatEvent combat = new CombatEvent(
            "personal", List.of(), List.of(), false, Map.of(), ScriptBlock.empty(), 30, 40);
        Section s = section(1, List.of(combat), List.of(), ScriptBlock.empty());
        SectionGraph graph = SectionGraph.of(adventureWith(s, leaf(30), leaf(40)));

        assertThat(graph.successors(1))
            .as("CombatEvent must contribute successSection and failureSection")
            .containsExactlyInAnyOrder(30, 40);
    }

    @Test
    void successors_combat_event_zero_sections_excluded() {
        CombatEvent combat = new CombatEvent(
            "personal", List.of(), List.of(), false, Map.of(), ScriptBlock.empty(), 30, 0);
        Section s = section(1, List.of(combat), List.of(), ScriptBlock.empty());
        SectionGraph graph = SectionGraph.of(adventureWith(s, leaf(30)));

        assertThat(graph.successors(1))
            .as("CombatEvent failureSection=0 must not appear in successors")
            .containsExactly(30);
    }

    @Test
    void successors_combat_event_script_navigateTo_included() {
        ScriptBlock hook = new ScriptBlock(Map.of("onCombatEnd", "ctx.navigateTo(55)"));
        CombatEvent combat = new CombatEvent(
            "personal", List.of(), List.of(), false, Map.of(), hook, 0, 0);
        Section s = section(1, List.of(combat), List.of(), ScriptBlock.empty());
        SectionGraph graph = SectionGraph.of(adventureWith(s, leaf(55)));

        assertThat(graph.successors(1))
            .as("navigateTo() in CombatEvent script hook must appear in successors")
            .contains(55);
    }

    @Test
    void successors_non_navigating_events_produce_no_successors() {
        Section s = section(1,
            List.of(new StatChangeEvent(AttributeType.STAMINA, -1)),
            List.of(choiceTo(2)), ScriptBlock.empty());
        SectionGraph graph = SectionGraph.of(adventureWith(s, leaf(2)));

        assertThat(graph.successors(1))
            .as("StatChangeEvent must not contribute any successors")
            .containsExactly(2);
    }

    // -------------------------------------------------------------------------
    // successors — onEnter script
    // -------------------------------------------------------------------------

    @Test
    void successors_onenter_script_navigateTo_included() {
        Section s = section(1, List.of(), List.of(), onEnter("ctx.navigateTo(42)"));
        SectionGraph graph = SectionGraph.of(adventureWith(s, leaf(42)));

        assertThat(graph.successors(1))
            .as("navigateTo() in onEnter script must appear in successors")
            .contains(42);
    }

    @Test
    void successors_unknown_section_returns_empty() {
        SectionGraph graph = SectionGraph.of(adventureWith(leaf(1)));

        assertThat(graph.successors(999))
            .as("successors of an unknown section number must return empty set")
            .isEmpty();
    }

    // -------------------------------------------------------------------------
    // reachableFrom — unbounded
    // -------------------------------------------------------------------------

    @Test
    void reachableFrom_follows_linear_chain() {
        // 1 → 2 → 3 (victory)
        Adventure adv = adventureWith(normal(1), normal(2), leaf(3));
        SectionGraph graph = SectionGraph.of(adv);

        assertThat(graph.reachableFrom(1))
            .as("reachableFrom(1) must include all sections in the linear chain 1→2→3")
            .containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    void reachableFrom_excludes_unreachable_section() {
        // 1 → 2; section 99 has no incoming edges
        Section s1 = section(1, List.of(), List.of(choiceTo(2)), ScriptBlock.empty());
        Adventure adv = adventureWith(s1, leaf(2), leaf(99));
        SectionGraph graph = SectionGraph.of(adv);

        assertThat(graph.reachableFrom(1))
            .as("section 99 with no incoming edges must not appear in reachableFrom(1)")
            .doesNotContain(99);
    }

    @Test
    void reachableFrom_handles_cycles_without_infinite_loop() {
        // 1 → 2 → 1 (cycle), plus exit 3
        Section s1 = section(1, List.of(), List.of(choiceTo(2), choiceTo(3)), ScriptBlock.empty());
        Section s2 = section(2, List.of(), List.of(choiceTo(1)), ScriptBlock.empty());
        Adventure adv = adventureWith(s1, s2, leaf(3));
        SectionGraph graph = SectionGraph.of(adv);

        assertThat(graph.reachableFrom(1))
            .as("BFS must handle cycles without looping infinitely")
            .containsExactlyInAnyOrder(1, 2, 3);
    }

    // -------------------------------------------------------------------------
    // reachableFrom — range-bounded
    // -------------------------------------------------------------------------

    @Test
    void reachableFrom_range_does_not_traverse_outside_range() {
        // sections 1,2,3 in range [1,3]; section 4 is outside
        Section s1 = section(1, List.of(), List.of(choiceTo(2)), ScriptBlock.empty());
        Section s2 = section(2, List.of(), List.of(choiceTo(3), choiceTo(4)), ScriptBlock.empty());
        Section s3 = section(3, List.of(), List.of(choiceTo(4)), ScriptBlock.empty());
        Section s4 = section(4, List.of(), List.of(choiceTo(99)), ScriptBlock.empty());
        Adventure adv = adventureWith(s1, s2, s3, s4, leaf(99));
        SectionGraph graph = SectionGraph.of(adv);

        Set<Integer> reachable = graph.reachableFrom(1, 1, 3);

        assertThat(reachable)
            .as("range-bounded BFS must not traverse successors of sections outside [1,3]")
            .doesNotContain(99);
    }

    @Test
    void reachableFrom_multi_start_unions_reachable_sets() {
        // Sections 1 and 10 are parallel entry points; 2 is only reachable from 1,
        // 11 is only reachable from 10. Both must appear when seeded from {1, 10}.
        Section s1  = section(1,  List.of(), List.of(choiceTo(2)),  ScriptBlock.empty());
        Section s2  = leaf(2);
        Section s10 = section(10, List.of(), List.of(choiceTo(11)), ScriptBlock.empty());
        Section s11 = leaf(11);
        Adventure adv = adventureWith(s1, s2, s10, s11);
        SectionGraph graph = SectionGraph.of(adv);

        assertThat(graph.reachableFrom(Set.of(1, 10), 1, 11))
            .as("multi-start BFS must include sections reachable from either start")
            .containsExactlyInAnyOrder(1, 2, 10, 11);
    }

    // -------------------------------------------------------------------------
    // predecessors
    // -------------------------------------------------------------------------

    @Test
    void predecessors_single_inbound_choice_edge() {
        // 1 → 2; predecessors(2) must contain 1
        Section s1 = section(1, List.of(), List.of(choiceTo(2)), ScriptBlock.empty());
        SectionGraph graph = SectionGraph.of(adventureWith(s1, leaf(2)));

        assertThat(graph.predecessors(2))
            .as("predecessors(2) must include section 1 which has a choice targeting 2")
            .containsExactly(1);
    }

    @Test
    void predecessors_multiple_inbound_edges() {
        // 1 → 3, 2 → 3; predecessors(3) must contain both 1 and 2
        Section s1 = section(1, List.of(), List.of(choiceTo(3)), ScriptBlock.empty());
        Section s2 = section(2, List.of(), List.of(choiceTo(3)), ScriptBlock.empty());
        SectionGraph graph = SectionGraph.of(adventureWith(s1, s2, leaf(3)));

        assertThat(graph.predecessors(3))
            .as("predecessors(3) must include all sections that link to 3")
            .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void predecessors_no_inbound_edges_returns_empty() {
        // section 99 exists but nothing links to it
        Section s1 = section(1, List.of(), List.of(choiceTo(2)), ScriptBlock.empty());
        SectionGraph graph = SectionGraph.of(adventureWith(s1, leaf(2), leaf(99)));

        assertThat(graph.predecessors(99))
            .as("section 99 with no incoming edges must have empty predecessors set")
            .isEmpty();
    }

    @Test
    void predecessors_via_navigate_event() {
        // section 1 has a NavigateEvent to section 5; predecessors(5) must contain 1
        Section s1 = section(1, List.of(new NavigateEvent(5)), List.of(), ScriptBlock.empty());
        SectionGraph graph = SectionGraph.of(adventureWith(s1, leaf(5)));

        assertThat(graph.predecessors(5))
            .as("NavigateEvent must be counted as an inbound edge for predecessor tracking")
            .containsExactly(1);
    }

    @Test
    void reachableFrom_range_records_exit_targets_in_visited_set() {
        // section 3 in range [1,3] exits to section 10 (outside range)
        Section s1 = section(1, List.of(), List.of(choiceTo(2)), ScriptBlock.empty());
        Section s2 = section(2, List.of(), List.of(choiceTo(3)), ScriptBlock.empty());
        Section s3 = section(3, List.of(), List.of(choiceTo(10)), ScriptBlock.empty());
        Adventure adv = adventureWith(s1, s2, s3, leaf(10));
        SectionGraph graph = SectionGraph.of(adv);

        Set<Integer> reachable = graph.reachableFrom(1, 1, 3);

        assertThat(reachable)
            .as("exit section 10 (outside range) must be recorded as visited so exit-gate checks work")
            .contains(10);
    }
}
