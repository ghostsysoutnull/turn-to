package com.tas.neo.engine;

import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.adventure.event.StatChangeEvent;
import com.tas.neo.domain.location.Cell;
import com.tas.neo.domain.location.Direction;
import com.tas.neo.domain.location.Grid;
import com.tas.neo.domain.location.Passage;
import com.tas.neo.domain.party.GameOverConsequence;
import com.tas.neo.domain.party.MemberState;
import com.tas.neo.domain.party.PartyMemberDefinition;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.io.OutputEvent;
import com.tas.neo.io.RecordingGameLogger;
import com.tas.neo.io.NoOpGameLogger;
import com.tas.neo.domain.DiceFormula;
import com.tas.neo.domain.DiceStatDefinition;
import com.tas.neo.domain.StatDefinition;
import com.tas.neo.mechanics.FixedDice;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full engine loop tests using {@link ScenarioRunner}.
 *
 * <p>Covers the test strategy rows from {@code docs/design/04-engine.md}:
 * <ul>
 *   <li>VICTORY section ends game: state.isVictory() == true</li>
 *   <li>INSTANT_DEATH section ends game: state.isGameOver() == true</li>
 *   <li>Player death mid-event (STAMINA → 0 via StatChangeEvent) ends game</li>
 *   <li>System choices injected: "Check inventory" and "Quit" always present</li>
 *   <li>Navigation follows selected choice to correct section</li>
 *   <li>Party member created at start; stats within expected rolled range</li>
 *   <li>Enter grid via GridTarget choice → state.isInGrid() == true</li>
 *   <li>Exit grid via passage with toSection → state.isInGrid() == false</li>
 *   <li>Cell events fire on cell entry (StatChangeEvent → player stat modified)</li>
 * </ul>
 */
class GameTest {

    // -----------------------------------------------------------------------
    // Adventure builders
    // -----------------------------------------------------------------------

    private static Adventure singleSectionAdventure(Section section) {
        return new Adventure(
            "test-adventure", "Test", "A test adventure",
            section.number(), 0,
            List.of(section),
            List.of(), List.of(), List.of("personal"), List.of(),
            ScriptBlock.empty(), standardPlayerStats()
        );
    }

    private static Adventure twoSectionAdventure(Section first, Section second) {
        return new Adventure(
            "test-adventure", "Test", "A test adventure",
            first.number(), 0,
            List.of(first, second),
            List.of(), List.of(), List.of("personal"), List.of(),
            ScriptBlock.empty(), standardPlayerStats()
        );
    }

    private static Map<String, StatDefinition> standardPlayerStats() {
        return Map.of(
            "SKILL",   new DiceStatDefinition(DiceFormula.parse("1d6+6"),  OptionalInt.empty()),
            "STAMINA", new DiceStatDefinition(DiceFormula.parse("2d6+12"), OptionalInt.empty()),
            "LUCK",    new DiceStatDefinition(DiceFormula.parse("1d6+6"),  OptionalInt.empty())
        );
    }

    private static Section normalSection(int number, String narrative) {
        return new Section(number, narrative, List.of(), List.of(),
                           SectionType.NORMAL, ScriptBlock.empty());
    }

    private static Section normalSectionWithChoices(int number, String narrative,
                                                     List<Choice> choices) {
        return new Section(number, narrative, List.of(), choices,
                           SectionType.NORMAL, ScriptBlock.empty());
    }

    private static Section normalSectionWithEvents(int number, String narrative,
                                                    List<com.tas.neo.domain.adventure.event.SectionEvent> events) {
        return new Section(number, narrative, events, List.of(),
                           SectionType.NORMAL, ScriptBlock.empty());
    }

    private static Section victorySection(int number) {
        return new Section(number, "You win!", List.of(), List.of(),
                           SectionType.VICTORY, ScriptBlock.empty());
    }

    private static Section instantDeathSection(int number) {
        return new Section(number, "You are dead.", List.of(), List.of(),
                           SectionType.INSTANT_DEATH, ScriptBlock.empty());
    }

    // -----------------------------------------------------------------------
    // Section type handling
    // -----------------------------------------------------------------------

    @Test
    void victory_section_sets_state_isVictory() {
        Adventure adventure = singleSectionAdventure(victorySection(1));

        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3)).run();

        assertThat(result.victory())
            .as("A VICTORY section must set the victory flag on the final state")
            .isTrue();
    }

    @Test
    void victory_section_makes_state_terminal() {
        Adventure adventure = singleSectionAdventure(victorySection(1));

        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3)).run();

        assertThat(result.finalState().isTerminal())
            .as("A VICTORY section must make the game terminal")
            .isTrue();
    }

    @Test
    void instant_death_section_sets_state_isGameOver() {
        Adventure adventure = singleSectionAdventure(instantDeathSection(1));

        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3)).run();

        assertThat(result.gameOver())
            .as("An INSTANT_DEATH section must set the gameOver flag on the final state")
            .isTrue();
    }

    @Test
    void instant_death_section_makes_state_terminal() {
        Adventure adventure = singleSectionAdventure(instantDeathSection(1));

        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3)).run();

        assertThat(result.finalState().isTerminal())
            .as("An INSTANT_DEATH section must make the game terminal")
            .isTrue();
    }

    @Test
    void instant_death_section_shows_game_over_in_output() {
        Adventure adventure = singleSectionAdventure(instantDeathSection(1));

        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3)).run();

        assertThat(result.output().wasGameOverShown())
            .as("An INSTANT_DEATH section must trigger the game over output event")
            .isTrue();
    }

    @Test
    void victory_section_shows_victory_in_output() {
        Adventure adventure = singleSectionAdventure(victorySection(1));

        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3)).run();

        assertThat(result.output().wasVictoryShown())
            .as("A VICTORY section must trigger the victory output event")
            .isTrue();
    }

    @Test
    void narrative_shown_event_includes_section_number() {
        Adventure adventure = singleSectionAdventure(normalSection(42, "You stand in a dark cave."));

        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3), 2).run();

        OutputEvent.NarrativeShown event = result.output().events().stream()
            .filter(e -> e instanceof OutputEvent.NarrativeShown)
            .map(e -> (OutputEvent.NarrativeShown) e)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No NarrativeShown event was recorded"));

        assertThat(event.sectionNumber())
            .as("NarrativeShown must carry the section number of the section being displayed")
            .isEqualTo(42);
    }

    // -----------------------------------------------------------------------
    // Player death mid-event
    // -----------------------------------------------------------------------

    @Test
    void player_death_via_stat_change_event_sets_game_over() {
        // Section has a kill event AND authored choices — the "no choices" fallback must not fire.
        // Only the StatChangeEvent (STAMINA → 0) should trigger game over.
        StatChangeEvent killEvent = new StatChangeEvent(AttributeType.STAMINA, -100);
        Section deathSection = new Section(
            1, "You are mortally wounded.",
            List.of(killEvent),
            List.of(Choice.to("Escape", new SectionTarget(2))),
            SectionType.NORMAL,
            ScriptBlock.empty()
        );
        Section escapeSection = victorySection(2);
        Adventure adventure = twoSectionAdventure(deathSection, escapeSection);

        // If events are processed, player dies before choices are shown → game over
        // If events are NOT processed (stub), player sees choices and we'd need to provide input
        // FixedDice(1): skill=7, stamina=14, luck=7
        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(1), 1).run();

        assertThat(result.gameOver())
            .as("Player death via StatChangeEvent must set the gameOver flag before choices are shown")
            .isTrue();
    }

    @Test
    void player_death_via_stat_change_event_shows_no_choices() {
        StatChangeEvent killEvent = new StatChangeEvent(AttributeType.STAMINA, -100);
        Section deathSection = new Section(
            1, "You die.",
            List.of(killEvent),
            List.of(Choice.to("Escape", new SectionTarget(2))),
            SectionType.NORMAL,
            ScriptBlock.empty()
        );
        Section escapeSection = victorySection(2);
        Adventure adventure = twoSectionAdventure(deathSection, escapeSection);

        // Choice 1 provided but should never be used if player dies from the event
        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(1), 1).run();

        boolean choicesWereShown = result.output().events().stream()
            .anyMatch(e -> e instanceof OutputEvent.ChoicesShown);

        assertThat(choicesWereShown)
            .as("No choices must be shown after player death via StatChangeEvent")
            .isFalse();
    }

    // -----------------------------------------------------------------------
    // System choices injected
    // -----------------------------------------------------------------------

    @Test
    void system_choices_include_check_inventory_in_normal_section() {
        // Section has one authored choice leading to victory so the run terminates.
        Section victory = victorySection(2);
        Section start = normalSectionWithChoices(1, "You begin.", List.of(
            Choice.to("Continue", new SectionTarget(2))
        ));
        Adventure adventure = twoSectionAdventure(start, victory);

        // Choice 1 = first authored choice → goes to victory
        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3), 1).run();

        boolean hasInventoryChoice = result.output().events().stream()
            .filter(e -> e instanceof OutputEvent.ChoicesShown)
            .map(e -> (OutputEvent.ChoicesShown) e)
            .anyMatch(cs -> cs.choices().stream()
                .anyMatch(c -> c.text().equals("Check inventory")));

        assertThat(hasInventoryChoice)
            .as("\"Check inventory\" must appear in the choices shown for every normal section")
            .isTrue();
    }

    @Test
    void system_choices_include_quit_in_normal_section() {
        Section victory = victorySection(2);
        Section start = normalSectionWithChoices(1, "You begin.", List.of(
            Choice.to("Continue", new SectionTarget(2))
        ));
        Adventure adventure = twoSectionAdventure(start, victory);

        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3), 1).run();

        boolean hasQuitChoice = result.output().events().stream()
            .filter(e -> e instanceof OutputEvent.ChoicesShown)
            .map(e -> (OutputEvent.ChoicesShown) e)
            .anyMatch(cs -> cs.choices().stream()
                .anyMatch(c -> c.text().equals("Quit")));

        assertThat(hasQuitChoice)
            .as("\"Quit\" must appear in the choices shown for every normal section")
            .isTrue();
    }

    // -----------------------------------------------------------------------
    // Navigation follows choice
    // -----------------------------------------------------------------------

    @Test
    void navigation_follows_selected_choice_to_target_section() {
        Section start = normalSectionWithChoices(1, "You stand at a crossroads.", List.of(
            Choice.to("Go to section 2", new SectionTarget(2)),
            Choice.to("Go to section 3", new SectionTarget(3))
        ));
        Section target = victorySection(2);
        Section other = victorySection(3);

        Adventure adventure = new Adventure(
            "test-adventure", "Test", "A test adventure", 1, 0,
            List.of(start, target, other),
            List.of(), List.of(), List.of("personal"), List.of(),
            ScriptBlock.empty(), Map.of()
        );

        // Choice 1 → go to section 2 (the first authored choice)
        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3), 1).run();

        assertThat(result.finalSection())
            .as("Selecting choice 1 must navigate to section 2")
            .isEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // Party member creation
    // -----------------------------------------------------------------------

    @Test
    void party_member_created_at_adventure_start_with_correct_state() {
        DiceStatDefinition hpDef = new DiceStatDefinition(
            DiceFormula.parse("1d6+4"), OptionalInt.empty());

        PartyMemberDefinition def = new PartyMemberDefinition(
            "sidekick", "Sidekick", "hp",
            new GameOverConsequence("Sidekick died"),
            MemberState.ACTIVE,
            Map.of("hp", hpDef)
        );

        Adventure adventure = new Adventure(
            "test-adventure", "Test", "A test adventure", 1, 0,
            List.of(victorySection(1)),
            List.of(), List.of(def), List.of("personal"), List.of(),
            ScriptBlock.empty(), Map.of()
        );

        // FixedDice(3) → 1d6+4 = 3+4 = 7
        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3)).run();

        assertThat(result.finalState().getPartyMember("sidekick"))
            .as("Adventure with a party member definition must create the member in GameState")
            .isNotNull();
    }

    @Test
    void party_member_initial_state_reflects_definition() {
        DiceStatDefinition hpDef = new DiceStatDefinition(
            DiceFormula.parse("1d6"), OptionalInt.empty());

        PartyMemberDefinition waitingDef = new PartyMemberDefinition(
            "companion", "Companion", "hp",
            new GameOverConsequence("Companion died"),
            MemberState.WAITING,
            Map.of("hp", hpDef)
        );

        Adventure adventure = new Adventure(
            "test-adventure", "Test", "A test adventure", 1, 0,
            List.of(victorySection(1)),
            List.of(), List.of(waitingDef), List.of("personal"), List.of(),
            ScriptBlock.empty(), Map.of()
        );

        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(4)).run();

        assertThat(result.finalState().getPartyMember("companion").state())
            .as("A party member with WAITING initialState must start in WAITING state")
            .isEqualTo(MemberState.WAITING);
    }

    @Test
    void party_member_stat_within_expected_rolled_range() {
        // FixedDice(3) → 1d6+4 = 3+4 = 7 for every roll
        DiceStatDefinition hpDef = new DiceStatDefinition(
            DiceFormula.parse("1d6+4"), OptionalInt.empty());

        PartyMemberDefinition def = new PartyMemberDefinition(
            "warrior", "Warrior", "hp",
            new GameOverConsequence("Warrior died"),
            MemberState.ACTIVE,
            Map.of("hp", hpDef)
        );

        Adventure adventure = new Adventure(
            "test-adventure", "Test", "A test adventure", 1, 0,
            List.of(victorySection(1)),
            List.of(), List.of(def), List.of("personal"), List.of(),
            ScriptBlock.empty(), Map.of()
        );

        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3)).run();

        int hp = result.finalState().getPartyMember("warrior").getStat("hp");
        assertThat(hp)
            .as("Party member stat must be within the range defined by the DiceFormula (1d6+4 with FixedDice(3) = 7)")
            .isEqualTo(7);
    }

    // -----------------------------------------------------------------------
    // Player creation — stats rolled from adventure-defined formulas
    // -----------------------------------------------------------------------

    @Test
    void player_skill_is_rolled_from_adventure_stat_formula_on_game_start() {
        // FixedDice(1): SKILL = 1d6+6 = 1+6 = 7
        ScenarioResult result = ScenarioRunner
            .scripted(singleSectionAdventure(victorySection(1)), new FixedDice(1))
            .run();

        assertThat(result.finalState().player().getSkill())
            .as("Player SKILL must be rolled from adventure playerStats formula (1d6+6) — " +
                "FixedDice(1) produces 1+6=7; zero means createPlayer() ignored the formula")
            .isEqualTo(7);
    }

    @Test
    void player_stamina_is_rolled_from_adventure_stat_formula_on_game_start() {
        // FixedDice(1): STAMINA = 2d6+12 = 1+1+12 = 14
        ScenarioResult result = ScenarioRunner
            .scripted(singleSectionAdventure(victorySection(1)), new FixedDice(1))
            .run();

        assertThat(result.finalState().player().getStamina())
            .as("Player STAMINA must be rolled from adventure playerStats formula (2d6+12) — " +
                "FixedDice(1) produces 1+1+12=14; zero means createPlayer() ignored the formula")
            .isEqualTo(14);
    }

    @Test
    void player_luck_is_rolled_from_adventure_stat_formula_on_game_start() {
        // FixedDice(1): LUCK = 1d6+6 = 1+6 = 7
        ScenarioResult result = ScenarioRunner
            .scripted(singleSectionAdventure(victorySection(1)), new FixedDice(1))
            .run();

        assertThat(result.finalState().player().getLuck())
            .as("Player LUCK must be rolled from adventure playerStats formula (1d6+6) — " +
                "FixedDice(1) produces 1+6=7; zero means createPlayer() ignored the formula")
            .isEqualTo(7);
    }

    @Test
    void player_gold_is_initialised_from_adventure_initial_gold_on_game_start() {
        Adventure adventure = new Adventure(
            "test-adventure", "Test", "A test adventure", 1, 0, 15,
            List.of(victorySection(1)),
            List.of(), List.of(), List.of("personal"), List.of(),
            ScriptBlock.empty(), standardPlayerStats()
        );

        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(1)).run();

        assertThat(result.finalState().player().getGold())
            .as("Player gold must be initialised from adventure initialGold (15) — " +
                "zero means createPlayer() ignored initialGold")
            .isEqualTo(15);
    }

    // -----------------------------------------------------------------------
    // Grid navigation — enter grid via GridTarget choice
    // -----------------------------------------------------------------------

    @Test
    void entering_grid_via_choice_sets_isInGrid_true() {
        Cell entryCell = new Cell(
            Optional.of("entrance"), 0, 0, 0, "Cold stone floor.",
            List.of(), ScriptBlock.empty(), Map.of(), List.of()
        );
        Grid dungeon = new Grid("dungeon", 5, 5, 1,
            Map.of("0,0,0", entryCell));

        Adventure adventure = new Adventure(
            "test-adventure", "Test", "A test adventure", 1, 0,
            List.of(normalSectionWithChoices(1, "Before dungeon.", List.of(
                Choice.to("Enter dungeon", new com.tas.neo.domain.adventure.GridTarget("dungeon", "entrance"))
            ))),
            List.of(), List.of(), List.of("personal"), List.of(dungeon),
            ScriptBlock.empty(), standardPlayerStats()
        );

        // Choice 1 → enter dungeon
        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3), 1).run();

        assertThat(result.isInGrid())
            .as("Selecting a GridTarget choice must place the player in a grid")
            .isTrue();
    }

    @Test
    void entering_grid_via_choice_sets_correct_entry_cell() {
        Cell entryCell = new Cell(
            Optional.of("entrance"), 0, 0, 0, "Cold stone floor.",
            List.of(), ScriptBlock.empty(), Map.of(), List.of()
        );
        Grid dungeon = new Grid("dungeon", 5, 5, 1,
            Map.of("0,0,0", entryCell));

        Adventure adventure = new Adventure(
            "test-adventure", "Test", "A test adventure", 1, 0,
            List.of(normalSectionWithChoices(1, "Before dungeon.", List.of(
                Choice.to("Enter dungeon", new com.tas.neo.domain.adventure.GridTarget("dungeon", "entrance"))
            ))),
            List.of(), List.of(), List.of("personal"), List.of(dungeon),
            ScriptBlock.empty(), standardPlayerStats()
        );

        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3), 1).run();

        assertThat(result.finalState().currentCell())
            .as("Entering a grid must place the player at the named entry cell")
            .contains(entryCell);
    }

    // -----------------------------------------------------------------------
    // Grid navigation — exit grid via passage toSection
    // -----------------------------------------------------------------------

    @Test
    void exiting_grid_via_passage_toSection_clears_isInGrid() {
        // Exit passage leads back to section 2 (victory)
        Passage exitPassage = new Passage(
            Optional.of("Exit dungeon"), Optional.empty(), Optional.of(2));

        Cell dungeonCell = new Cell(
            Optional.of("entrance"), 0, 0, 0, "Dungeon cell.",
            List.of(), ScriptBlock.empty(),
            Map.of(Direction.NORTH, exitPassage), List.of()
        );
        Grid dungeon = new Grid("dungeon", 5, 5, 1,
            Map.of("0,0,0", dungeonCell));

        Section exitSection = victorySection(2);
        Section entrySection = normalSectionWithChoices(1, "Before dungeon.", List.of(
            Choice.to("Enter dungeon", new com.tas.neo.domain.adventure.GridTarget("dungeon", "entrance"))
        ));

        Adventure adventure = new Adventure(
            "test-adventure", "Test", "A test adventure", 1, 0,
            List.of(entrySection, exitSection),
            List.of(), List.of(), List.of("personal"), List.of(dungeon),
            ScriptBlock.empty(), standardPlayerStats()
        );

        // Choice 1 → enter dungeon, then choice 1 in grid → exit via north passage
        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3), 1, 1).run();

        assertThat(result.isInGrid())
            .as("Selecting a passage with toSection must exit the grid")
            .isFalse();
    }

    @Test
    void exiting_grid_via_passage_toSection_sets_correct_section() {
        Passage exitPassage = new Passage(
            Optional.of("Exit dungeon"), Optional.empty(), Optional.of(2));

        Cell dungeonCell = new Cell(
            Optional.of("entrance"), 0, 0, 0, "Dungeon cell.",
            List.of(), ScriptBlock.empty(),
            Map.of(Direction.NORTH, exitPassage), List.of()
        );
        Grid dungeon = new Grid("dungeon", 5, 5, 1,
            Map.of("0,0,0", dungeonCell));

        Section exitSection = victorySection(2);
        Section entrySection = normalSectionWithChoices(1, "Before dungeon.", List.of(
            Choice.to("Enter dungeon", new com.tas.neo.domain.adventure.GridTarget("dungeon", "entrance"))
        ));

        Adventure adventure = new Adventure(
            "test-adventure", "Test", "A test adventure", 1, 0,
            List.of(entrySection, exitSection),
            List.of(), List.of(), List.of("personal"), List.of(dungeon),
            ScriptBlock.empty(), standardPlayerStats()
        );

        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3), 1, 1).run();

        assertThat(result.finalSection())
            .as("Exiting a grid via toSection must navigate to the specified section number")
            .isEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // Cell events fire on entry
    // -----------------------------------------------------------------------

    @Test
    void cell_stat_change_event_modifies_player_stat_on_entry() {
        // FixedDice(3): player STAMINA = 2d6+12 = 3+3+12 = 18
        // StatChangeEvent(-5) should reduce stamina to 13
        StatChangeEvent healEvent = new StatChangeEvent(AttributeType.STAMINA, -5);

        Cell dungeonCell = new Cell(
            Optional.of("entrance"), 0, 0, 0, "Dungeon cell.",
            List.of(healEvent), ScriptBlock.empty(), Map.of(), List.of()
        );
        Grid dungeon = new Grid("dungeon", 5, 5, 1,
            Map.of("0,0,0", dungeonCell));

        Section entrySection = normalSectionWithChoices(1, "Before dungeon.", List.of(
            Choice.to("Enter dungeon", new com.tas.neo.domain.adventure.GridTarget("dungeon", "entrance"))
        ));

        Adventure adventure = new Adventure(
            "test-adventure", "Test", "A test adventure", 1, 0,
            List.of(entrySection),
            List.of(), List.of(), List.of("personal"), List.of(dungeon),
            ScriptBlock.empty(), standardPlayerStats()
        );

        // Choice 1 → enter dungeon
        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3), 1).run();

        int stamina = result.finalState().player().getStamina();
        // FixedDice(3): 2d6+12 = 18, minus 5 = 13
        assertThat(stamina)
            .as("A StatChangeEvent in a cell must modify the player's stat on cell entry")
            .isEqualTo(13);
    }
}
