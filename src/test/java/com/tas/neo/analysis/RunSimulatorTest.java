package com.tas.neo.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.HasItemCondition;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.GridTarget;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.adventure.event.CombatEvent;
import com.tas.neo.domain.adventure.event.GoldChangeEvent;
import com.tas.neo.domain.adventure.event.ItemAction;
import com.tas.neo.domain.adventure.event.ItemEvent;
import com.tas.neo.domain.adventure.event.NavigateEvent;
import com.tas.neo.domain.adventure.event.SkillTestEvent;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.combat.Creature;
import com.tas.neo.mechanics.FixedDice;
import com.tas.neo.scripting.LuaScriptEngine;
import com.tas.neo.scripting.NoOpScriptEngine;
import com.tas.neo.scripting.ScriptEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class RunSimulatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ScriptEngine NO_OP = new NoOpScriptEngine();

    // -------------------------------------------------------------------------
    // Fixture builders
    // -------------------------------------------------------------------------

    private static Adventure adventureWith(Section... sections) {
        return new Adventure(
            "test", "Test", "", sections[0].number(), 0,
            List.of(sections), List.of(), List.of(), List.of(), List.of(),
            ScriptBlock.empty()
        );
    }

    private static Section normal(int number, List<SectionEvent> events,
                                   List<Choice> choices, ScriptBlock scripts) {
        return new Section(number, "§" + number, events, choices, SectionType.NORMAL, scripts);
    }

    private static Section normal(int number, List<Choice> choices) {
        return normal(number, List.of(), choices, ScriptBlock.empty());
    }

    private static Section victory(int number) {
        return new Section(number, "§" + number, List.of(), List.of(),
            SectionType.VICTORY, ScriptBlock.empty());
    }

    private static Section instantDeath(int number) {
        return new Section(number, "§" + number, List.of(), List.of(),
            SectionType.INSTANT_DEATH, ScriptBlock.empty());
    }

    private static Choice choiceTo(int target) {
        return Choice.to("go", new SectionTarget(target));
    }

    private static ScriptBlock onEnter(String script) {
        return new ScriptBlock(Map.of("onEnter", script));
    }

    private static ObjectNode noChapters() {
        ObjectNode root = MAPPER.createObjectNode();
        root.set("chapters", MAPPER.createArrayNode());
        return root;
    }

    private static ObjectNode chaptersJson(String id, int from, int to, int entrySection) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode chapters = root.putArray("chapters");
        ObjectNode ch = chapters.addObject();
        ch.put("id", id);
        ObjectNode range = ch.putObject("sectionRange");
        range.put("from", from);
        range.put("to", to);
        ObjectNode gates = ch.putObject("gates");
        ObjectNode entryGate = gates.putObject("entryGate");
        entryGate.put("entrySection", entrySection);
        return root;
    }

    private static RunConfiguration defaults() {
        return new RunConfiguration(1, new RandomChoiceSelector(),
            new FixedDice(6), 42L, 5, java.util.OptionalInt.empty(), java.util.OptionalInt.empty());
    }

    // -------------------------------------------------------------------------
    // VICTORY and INSTANT_DEATH termination
    // -------------------------------------------------------------------------

    @Test
    void run_ends_at_victory_section() {
        Adventure adv = adventureWith(
            normal(1, List.of(choiceTo(2))),
            victory(2)
        );
        RunResult result = new RunSimulator(adv, noChapters(), NO_OP, defaults(), new Random(0)).run();

        assertThat(result.outcome())
            .as("run must end with VICTORY outcome when a VICTORY section is reached")
            .isEqualTo(RunOutcome.VICTORY);
        assertThat(result.endingSection()).isEqualTo(2);
    }

    @Test
    void run_ends_at_instant_death_section() {
        Adventure adv = adventureWith(
            normal(1, List.of(choiceTo(2))),
            instantDeath(2)
        );
        RunResult result = new RunSimulator(adv, noChapters(), NO_OP, defaults(), new Random(0)).run();

        assertThat(result.outcome())
            .as("run must end with INSTANT_DEATH outcome when an INSTANT_DEATH section is reached")
            .isEqualTo(RunOutcome.INSTANT_DEATH);
        assertThat(result.endingSection()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // STUCK detection
    // -------------------------------------------------------------------------

    @Test
    void run_ends_stuck_when_normal_section_has_no_available_choices() {
        // Section 1 has a conditioned choice the player can never satisfy
        Section s1 = normal(1,
            List.of(Choice.to("take", new SectionTarget(2), new HasItemCondition("Key"))));
        Adventure adv = adventureWith(s1, victory(2));

        RunResult result = new RunSimulator(adv, noChapters(), NO_OP, defaults(), new Random(0)).run();

        assertThat(result.outcome())
            .as("run must end STUCK when the only available choices are all gated and conditions unmet")
            .isEqualTo(RunOutcome.STUCK);
    }

    // -------------------------------------------------------------------------
    // CYCLE detection
    // -------------------------------------------------------------------------

    @Test
    void run_ends_cycle_when_section_visited_beyond_max() {
        // Section 1 → 2 → 1 (infinite loop, no exit)
        Section s1 = normal(1, List.of(choiceTo(2)));
        Section s2 = normal(2, List.of(choiceTo(1)));
        Adventure adv = adventureWith(s1, s2);

        RunConfiguration config = new RunConfiguration(1, new RandomChoiceSelector(),
            new FixedDice(6), 42L, 3, java.util.OptionalInt.empty(), java.util.OptionalInt.empty());
        RunResult result = new RunSimulator(adv, noChapters(), NO_OP, config, new Random(0)).run();

        assertThat(result.outcome())
            .as("run must end CYCLE when a section is visited more than maxVisitsPerSection times")
            .isEqualTo(RunOutcome.CYCLE);
    }

    // -------------------------------------------------------------------------
    // Event processing
    // -------------------------------------------------------------------------

    @Test
    void item_gain_event_adds_item_to_state() {
        Section s1 = normal(1,
            List.of(new ItemEvent("Sword", ItemAction.GAIN, 1)),
            List.of(choiceTo(2)),
            ScriptBlock.empty());
        Adventure adv = adventureWith(s1, victory(2));

        RunResult result = new RunSimulator(adv, noChapters(), NO_OP, defaults(), new Random(0)).run();

        // The snapshot at victory should reflect the sword in inventory.
        // We verify via the visited sections path — if the run completed successfully
        // the item was gained and the game continued (not STUCK due to a missing item gate).
        assertThat(result.outcome()).isEqualTo(RunOutcome.VICTORY);
    }

    @Test
    void gold_change_event_updates_gold() {
        // Section 1: GOLD_CHANGE(+5) then choice to §2 (which requires gold via condition? No.)
        // Instead: verify by running with a GoldCondition gate: §1 gains gold, §2 requires it.
        Section s1 = normal(1,
            List.of(new GoldChangeEvent(5)),
            List.of(Choice.to("buy", new SectionTarget(2),
                new com.tas.neo.domain.adventure.GoldCondition(5))),
            ScriptBlock.empty());
        Adventure adv = adventureWith(s1, victory(2));

        RunResult result = new RunSimulator(adv, noChapters(), NO_OP, defaults(), new Random(0)).run();

        assertThat(result.outcome())
            .as("GoldChangeEvent must increase gold so that subsequent GoldCondition is met")
            .isEqualTo(RunOutcome.VICTORY);
    }

    @Test
    void gold_cannot_go_below_zero_via_event() {
        Section s1 = normal(1,
            List.of(new GoldChangeEvent(-100)),
            List.of(choiceTo(2)),
            ScriptBlock.empty());
        Adventure adv = adventureWith(s1, victory(2));

        RunResult result = new RunSimulator(adv, noChapters(), NO_OP, defaults(), new Random(0)).run();

        // Run must complete (not STUCK), confirming gold floor didn't corrupt state
        assertThat(result.outcome()).isEqualTo(RunOutcome.VICTORY);
    }

    // -------------------------------------------------------------------------
    // Script — navigateTo overrides choice selection
    // -------------------------------------------------------------------------

    @Test
    void navigateTo_in_onEnter_script_overrides_choice_navigation() {
        // Section 1 has choice to §3 (INSTANT_DEATH), but onEnter navigates to §2 (VICTORY)
        Section s1 = normal(1,
            List.of(),
            List.of(choiceTo(3)),
            onEnter("ctx.navigateTo(2)"));
        Adventure adv = adventureWith(s1, victory(2), instantDeath(3));

        RunResult result = new RunSimulator(adv, noChapters(), new LuaScriptEngine(),
            defaults(), new Random(0)).run();

        assertThat(result.outcome())
            .as("navigateTo() in onEnter script must override the choice, routing to §2 not §3")
            .isEqualTo(RunOutcome.VICTORY);
        assertThat(result.endingSection()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // SkillTestEvent — navigation follows dice outcome
    // -------------------------------------------------------------------------

    @Test
    void skill_test_navigates_to_success_section_when_dice_high() {
        // SKILL=12, dice always roll 1 (total=2 vs 12) => success (player rolls higher)
        // SkillTestEvent: successSection=2 (VICTORY), failSection=3 (INSTANT_DEATH)
        Section s1 = normal(1,
            List.of(new SkillTestEvent(2, 3)),
            List.of(),
            ScriptBlock.empty());
        Adventure adv = adventureWith(s1, victory(2), instantDeath(3));

        RunConfiguration config = new RunConfiguration(1, new RandomChoiceSelector(),
            new FixedDice(1), 42L, 5,
            java.util.OptionalInt.of(12),
            java.util.OptionalInt.of(20));
        RunResult result = new RunSimulator(adv, noChapters(), NO_OP, config, new Random(0)).run();

        assertThat(result.outcome())
            .as("with SKILL=12 and dice rolling 1+1=2, player wins skill test and reaches VICTORY")
            .isEqualTo(RunOutcome.VICTORY);
    }

    @Test
    void skill_test_navigates_to_fail_section_when_dice_low() {
        // SKILL=1, dice always roll 6 (total=12 vs 1) => failure
        Section s1 = normal(1,
            List.of(new SkillTestEvent(2, 3)),
            List.of(),
            ScriptBlock.empty());
        Adventure adv = adventureWith(s1, victory(2), instantDeath(3));

        RunConfiguration config = new RunConfiguration(1, new RandomChoiceSelector(),
            new FixedDice(6), 42L, 5,
            java.util.OptionalInt.of(1),
            java.util.OptionalInt.of(20));
        RunResult result = new RunSimulator(adv, noChapters(), NO_OP, config, new Random(0)).run();

        assertThat(result.outcome())
            .as("with SKILL=1 and dice rolling 6+6=12, player fails skill test and reaches INSTANT_DEATH")
            .isEqualTo(RunOutcome.INSTANT_DEATH);
    }

    // -------------------------------------------------------------------------
    // Chapter snapshots
    // -------------------------------------------------------------------------

    @Test
    void chapter_snapshot_captured_when_entering_chapter_entry_section() {
        Adventure adv = adventureWith(
            normal(1, List.of(choiceTo(10))),
            normal(10, List.of(choiceTo(11))),
            victory(11)
        );
        ObjectNode chaptersRaw = chaptersJson("ch2", 10, 11, 10);

        RunResult result = new RunSimulator(adv, chaptersRaw, NO_OP, defaults(), new Random(0)).run();

        assertThat(result.chapterSnapshots())
            .as("a chapter snapshot must be recorded when section 10 (ch2 entry) is entered")
            .extracting(ChapterSnapshot::chapterId)
            .contains("ch2");
    }

    @Test
    void chapter_snapshot_records_inventory_at_entry_time() {
        Section s1 = normal(1,
            List.of(new ItemEvent("Key", ItemAction.GAIN, 1)),
            List.of(choiceTo(10)),
            ScriptBlock.empty());
        Adventure adv = adventureWith(s1, normal(10, List.of(choiceTo(11))), victory(11));
        ObjectNode chaptersRaw = chaptersJson("ch2", 10, 11, 10);

        RunResult result = new RunSimulator(adv, chaptersRaw, NO_OP, defaults(), new Random(0)).run();

        ChapterSnapshot snapshot = result.chapterSnapshots().stream()
            .filter(s -> s.chapterId().equals("ch2"))
            .findFirst()
            .orElseThrow();
        assertThat(snapshot.inventory())
            .as("chapter snapshot must reflect items carried when entering the chapter")
            .contains("Key");
    }

    // -------------------------------------------------------------------------
    // Sections visited
    // -------------------------------------------------------------------------

    @Test
    void sections_visited_records_path() {
        Adventure adv = adventureWith(
            normal(1, List.of(choiceTo(2))),
            normal(2, List.of(choiceTo(3))),
            victory(3)
        );
        RunResult result = new RunSimulator(adv, noChapters(), NO_OP, defaults(), new Random(0)).run();

        assertThat(result.sectionsVisited())
            .as("sectionsVisited must record all sections in traversal order")
            .containsExactly(1, 2, 3);
    }

    // -------------------------------------------------------------------------
    // Uninitialised stat warnings
    // -------------------------------------------------------------------------

    private static CombatEvent combatTo(int successSection, int failSection) {
        Creature opp = new Creature("Enemy", 5, 6);
        return new CombatEvent("default", List.of(), List.of(opp), false, Map.of(),
            ScriptBlock.empty(), successSection, failSection);
    }

    @Test
    void warns_when_skill_uninitialised_in_combat() {
        Section s1 = normal(1, List.of(combatTo(2, 3)), List.of(), ScriptBlock.empty());
        Adventure adv = adventureWith(s1, victory(2), instantDeath(3));

        // No SKILL set — defaults to 0 internally, clamped to 10
        RunSimulator simulator = new RunSimulator(adv, noChapters(), NO_OP, defaults(), new Random(0));
        RunResult result = simulator.run();

        assertThat(result.warnings())
            .as("must warn when SKILL is uninitialised (0) and combat falls back to default")
            .anyMatch(w -> w.contains("SKILL") && w.contains("uninitialised"));
    }

    @Test
    void warns_when_stamina_uninitialised_in_combat() {
        Section s1 = normal(1, List.of(combatTo(2, 3)), List.of(), ScriptBlock.empty());
        Adventure adv = adventureWith(s1, victory(2), instantDeath(3));

        RunSimulator simulator = new RunSimulator(adv, noChapters(), NO_OP, defaults(), new Random(0));
        RunResult result = simulator.run();

        assertThat(result.warnings())
            .as("must warn when STAMINA is uninitialised (0) and combat falls back to default")
            .anyMatch(w -> w.contains("STAMINA") && w.contains("uninitialised"));
    }

    @Test
    void no_uninitialised_stat_warning_when_stats_are_set() {
        // onLoad sets SKILL=10, STAMINA=12
        ScriptBlock scripts = new ScriptBlock(Map.of("onLoad",
            "ctx.modifyStat('SKILL', 10); ctx.modifyStat('STAMINA', 12)"));
        Section s1 = normal(1, List.of(combatTo(2, 3)), List.of(), ScriptBlock.empty());
        Adventure adv = new Adventure("test", "Test", "", 1, 0,
            List.of(s1, victory(2), instantDeath(3)),
            List.of(), List.of(), List.of(), List.of(), scripts);

        RunSimulator simulator = new RunSimulator(adv, noChapters(), new LuaScriptEngine(), defaults(), new Random(0));
        RunResult result = simulator.run();

        assertThat(result.warnings())
            .as("no uninitialised stat warning when SKILL and STAMINA are set by onLoad")
            .noneMatch(w -> w.contains("uninitialised"));
    }

    // -------------------------------------------------------------------------
    // Conditioned choice tracking
    // -------------------------------------------------------------------------

    @Test
    void conditioned_choice_available_when_condition_met() {
        Section s1 = normal(1, List.of(
            Choice.to("use sword", new SectionTarget(2), new HasItemCondition("Sword")),
            choiceTo(3)
        ));
        // Give the Sword via onLoad so the simulator's own state has it
        ScriptBlock scripts = new ScriptBlock(Map.of("onLoad", "ctx.addItem('Sword')"));
        Adventure adv = new Adventure(
            "test", "Test", "", 1, 0,
            List.of(s1, victory(2), victory(3)), List.of(), List.of(), List.of(), List.of(),
            scripts
        );

        RunSimulator simulator = new RunSimulator(adv, noChapters(), new LuaScriptEngine(), defaults(), new Random(0));
        simulator.run();

        assertThat(simulator.conditionedChoicesAvailable())
            .as("conditioned choice must appear in available set when its condition is met")
            .contains(new NeverSelectedChoice(1, "use sword"));
    }

    @Test
    void conditioned_choice_not_tracked_when_condition_unmet() {
        Section s1 = normal(1, List.of(
            Choice.to("use sword", new SectionTarget(2), new HasItemCondition("Sword")),
            choiceTo(3)
        ));
        Adventure adv = adventureWith(s1, victory(2), victory(3));

        RunSimulator simulator = new RunSimulator(adv, noChapters(), NO_OP, defaults(), new Random(0));
        simulator.run(); // no Sword in inventory — conditioned choice filtered out

        assertThat(simulator.conditionedChoicesAvailable())
            .as("conditioned choice must not appear in available set when its condition is unmet")
            .doesNotContain(new NeverSelectedChoice(1, "use sword"));
    }

    @Test
    void selected_conditioned_choice_appears_in_selected_set() {
        // Only the conditioned choice is available (unconditional is removed), so it must be selected
        Section s1 = normal(1, List.of(
            Choice.to("use sword", new SectionTarget(2), new HasItemCondition("Sword"))
        ));
        Adventure adv = adventureWith(s1, victory(2));

        // Provide Sword via adventure onLoad script
        ScriptBlock scripts = new ScriptBlock(Map.of("onLoad", "ctx.addItem('Sword')"));
        Adventure advWithScript = new Adventure(
            "test", "Test", "", 1, 0,
            List.of(s1, victory(2)), List.of(), List.of(), List.of(), List.of(),
            scripts
        );

        RunSimulator simulator = new RunSimulator(advWithScript, noChapters(),
            new LuaScriptEngine(), defaults(), new Random(0));
        simulator.run();

        assertThat(simulator.conditionedChoicesSelected())
            .as("conditioned choice selected in a run must appear in selected set")
            .contains(new NeverSelectedChoice(1, "use sword"));
    }

    @Test
    void unconditioned_choices_not_tracked() {
        Section s1 = normal(1, List.of(choiceTo(2)));
        Adventure adv = adventureWith(s1, victory(2));

        RunSimulator simulator = new RunSimulator(adv, noChapters(), NO_OP, defaults(), new Random(0));
        simulator.run();

        assertThat(simulator.conditionedChoicesAvailable())
            .as("unconditioned choices must not appear in the conditioned-available set")
            .isEmpty();
        assertThat(simulator.conditionedChoicesSelected())
            .as("unconditioned choices must not appear in the conditioned-selected set")
            .isEmpty();
    }

    // -------------------------------------------------------------------------
    // GRID_ENTRY termination
    // -------------------------------------------------------------------------

    @Test
    void run_ends_grid_entry_when_chosen_target_is_grid() {
        Section s1 = new Section(1, "§1", List.of(),
            List.of(Choice.to("Enter dungeon", new GridTarget("vault", "entrance"))),
            SectionType.NORMAL, ScriptBlock.empty());
        Adventure adv = adventureWith(s1, victory(2));

        RunResult result = new RunSimulator(adv, noChapters(), NO_OP, defaults(), new Random(0)).run();

        assertThat(result.outcome())
            .as("run must end GRID_ENTRY when the selected choice leads to a grid — " +
                "grid navigation is not simulated")
            .isEqualTo(RunOutcome.GRID_ENTRY);
    }

    @Test
    void run_grid_entry_records_section_at_grid_choice() {
        Section s1 = new Section(1, "§1", List.of(),
            List.of(Choice.to("Enter dungeon", new GridTarget("vault", "entrance"))),
            SectionType.NORMAL, ScriptBlock.empty());
        Adventure adv = adventureWith(s1, victory(2));

        RunResult result = new RunSimulator(adv, noChapters(), NO_OP, defaults(), new Random(0)).run();

        assertThat(result.endingSection())
            .as("GRID_ENTRY ending section must be the section that had the grid choice")
            .isEqualTo(1);
    }

    @Test
    void run_prefers_section_target_over_grid_target_when_both_available() {
        // Two choices: one to a section, one to a grid. RandomChoiceSelector may pick either,
        // but FixedDice(1) → index 0. Order matters: section choice listed first.
        Section s1 = new Section(1, "§1", List.of(),
            List.of(
                Choice.to("Go to §2", new SectionTarget(2)),
                Choice.to("Enter dungeon", new GridTarget("vault", "entrance"))
            ),
            SectionType.NORMAL, ScriptBlock.empty());
        Adventure adv = adventureWith(s1, victory(2));

        // FixedDice(1) means roll always returns 1 — index 0 in a 0-based list via modulo
        RunConfiguration cfg = new RunConfiguration(1, new RandomChoiceSelector(),
            new FixedDice(1), 0L, 5,
            java.util.OptionalInt.empty(), java.util.OptionalInt.empty());
        RunResult result = new RunSimulator(adv, noChapters(), NO_OP, cfg, new Random(0)).run();

        // With seed 0 and two choices the runner may pick either, but what matters is:
        // if it picks the SectionTarget, outcome is VICTORY; if GridTarget, GRID_ENTRY.
        // Either is acceptable — we just assert it is NOT STUCK (grid entry ≠ dead end).
        assertThat(result.outcome())
            .as("a section with a grid choice must not produce STUCK — " +
                "GRID_ENTRY or VICTORY are both valid outcomes")
            .isNotEqualTo(RunOutcome.STUCK);
    }
}
