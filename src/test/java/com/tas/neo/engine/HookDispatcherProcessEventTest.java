package com.tas.neo.engine;

import com.tas.neo.combat.CombatSystem;
import com.tas.neo.combat.CombatSystemRegistry;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.adventure.event.CombatEvent;
import com.tas.neo.domain.adventure.event.GoldChangeEvent;
import com.tas.neo.domain.adventure.event.ItemAction;
import com.tas.neo.domain.adventure.event.ItemEvent;
import com.tas.neo.domain.adventure.event.LuckTestEvent;
import com.tas.neo.domain.adventure.event.NavigateEvent;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.adventure.event.SkillTestEvent;
import com.tas.neo.domain.adventure.event.StatChangeEvent;
import com.tas.neo.domain.combat.CombatOutcome;
import com.tas.neo.domain.combat.CombatOutcomeType;
import com.tas.neo.domain.combat.CombatResult;
import com.tas.neo.domain.combat.Creature;
import com.tas.neo.domain.item.Inventory;
import com.tas.neo.domain.item.Item;
import com.tas.neo.domain.item.ItemCategory;
import com.tas.neo.domain.player.Attribute;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.domain.player.Player;
import com.tas.neo.io.RecordingGameLogger;
import com.tas.neo.io.RecordingOutput;
import com.tas.neo.io.ScriptedInput;
import com.tas.neo.mechanics.FixedDice;
import com.tas.neo.scripting.AdventureScriptState;
import com.tas.neo.scripting.NoOpScriptEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HookDispatcher#processEvent(SectionEvent)} dispatch.
 *
 * <p>Covers the design contract from {@code docs/design/04-engine.md}:
 * processEvent dispatches over the sealed SectionEvent hierarchy using a Java 21
 * switch expression. Each SectionEvent subtype must be handled correctly.
 */
class HookDispatcherProcessEventTest {

    private HookDispatcher dispatcher;
    private GameState state;
    private RecordingOutput output;

    @BeforeEach
    void setUp() {
        output = new RecordingOutput();
        state = new GameState();
        state.setPlayer(createPlayer(18));

        NoOpScriptEngine scriptEngine = new NoOpScriptEngine();
        ScriptedInput input = new ScriptedInput();
        AdventureScriptState scriptState = new AdventureScriptState();
        FixedDice dice = new FixedDice(3);

        CombatSystemRegistry combatRegistry = new CombatSystemRegistry() {
            @Override
            public CombatSystem get(String id) {
                throw new IllegalArgumentException("No combat systems in this test");
            }

            @Override
            public boolean has(String id) {
                return false;
            }
        };

        dispatcher = new HookDispatcher(scriptEngine, input, output, state,
                                        scriptState, combatRegistry, dice);
    }

    private Player createPlayer(int stamina) {
        Map<AttributeType, Attribute> attrs = new EnumMap<>(AttributeType.class);
        attrs.put(AttributeType.STAMINA, new Attribute(AttributeType.STAMINA, stamina, stamina));
        attrs.put(AttributeType.SKILL, new Attribute(AttributeType.SKILL, 10, 10));
        attrs.put(AttributeType.LUCK, new Attribute(AttributeType.LUCK, 10, 10));
        return new Player(attrs, new Inventory(), 5, 3);
    }

    /**
     * Builds a minimal Adventure containing the given sections with id "test".
     * Start section is always the number of the first section in the list.
     */
    private static Adventure adventureWithSections(Section... sections) {
        return new Adventure(
            "test", "Test Adventure", "", sections[0].number(), 0,
            List.of(sections), List.of(), List.of(), List.of(), List.of(),
            ScriptBlock.empty()
        );
    }

    private static Section normalSection(int number) {
        return new Section(number, ".", List.of(), List.of(),
                           SectionType.NORMAL, ScriptBlock.empty());
    }

    private static Section victorySection(int number) {
        return new Section(number, "You win.", List.of(), List.of(),
                           SectionType.VICTORY, ScriptBlock.empty());
    }

    /**
     * Builds a HookDispatcher wired to the shared state/output and the given logger,
     * with a no-op script engine, scripted input, and no-op combat registry.
     */
    private HookDispatcher dispatcherWithLogger(RecordingGameLogger logger) {
        CombatSystemRegistry noCombat = new CombatSystemRegistry() {
            @Override public CombatSystem get(String id) {
                throw new IllegalArgumentException("No combat in this test");
            }
            @Override public boolean has(String id) { return false; }
        };
        return new HookDispatcher(
            new NoOpScriptEngine(), new ScriptedInput(), output, state,
            new AdventureScriptState(), noCombat, new FixedDice(3), logger);
    }

    /**
     * Builds a HookDispatcher that uses the given dice and the shared
     * {@code state} and {@code output} fields, with a no-op combat registry.
     */
    private HookDispatcher dispatcherWithDice(FixedDice dice) {
        CombatSystemRegistry noCombat = new CombatSystemRegistry() {
            @Override public CombatSystem get(String id) {
                throw new IllegalArgumentException("No combat in this test");
            }
            @Override public boolean has(String id) { return false; }
        };
        return new HookDispatcher(
            new NoOpScriptEngine(), new ScriptedInput(), output, state,
            new AdventureScriptState(), noCombat, dice);
    }

    /**
     * Builds a HookDispatcher with the given combat registry and shared state/output.
     */
    private HookDispatcher dispatcherWithCombatRegistry(CombatSystemRegistry registry) {
        return new HookDispatcher(
            new NoOpScriptEngine(), new ScriptedInput(), output, state,
            new AdventureScriptState(), registry, new FixedDice(3));
    }

    // -----------------------------------------------------------------------
    // StatChangeEvent
    // -----------------------------------------------------------------------

    @Test
    void processEvent_StatChangeEvent_modifies_player_stamina() {
        StatChangeEvent event = new StatChangeEvent(AttributeType.STAMINA, -3);

        dispatcher.processEvent(event);

        assertThat(state.player().getStamina())
            .as("processEvent(StatChangeEvent) must apply the delta to the specified attribute")
            .isEqualTo(15);
    }

    @Test
    void processEvent_StatChangeEvent_modifies_player_skill() {
        StatChangeEvent event = new StatChangeEvent(AttributeType.SKILL, -2);

        dispatcher.processEvent(event);

        assertThat(state.player().getStat(AttributeType.SKILL))
            .as("processEvent(StatChangeEvent) must apply the delta to SKILL")
            .isEqualTo(8);
    }

    @Test
    void processEvent_StatChangeEvent_stamina_zero_sets_game_over() {
        // Reduce stamina from 18 to 0 in a single event
        StatChangeEvent killEvent = new StatChangeEvent(AttributeType.STAMINA, -100);

        dispatcher.processEvent(killEvent);

        assertThat(state.isGameOver())
            .as("When StatChangeEvent reduces STAMINA to 0, HookDispatcher must set game over")
            .isTrue();
    }

    @Test
    void processEvent_StatChangeEvent_stamina_above_zero_does_not_set_game_over() {
        StatChangeEvent event = new StatChangeEvent(AttributeType.STAMINA, -1);

        dispatcher.processEvent(event);

        assertThat(state.isGameOver())
            .as("StatChangeEvent that leaves STAMINA > 0 must not set game over")
            .isFalse();
    }

    // -----------------------------------------------------------------------
    // GoldChangeEvent
    // -----------------------------------------------------------------------

    @Test
    void processEvent_GoldChangeEvent_increases_player_gold() {
        GoldChangeEvent event = new GoldChangeEvent(10);

        dispatcher.processEvent(event);

        assertThat(state.player().getGold())
            .as("processEvent(GoldChangeEvent) with positive delta must increase player gold")
            .isEqualTo(15);
    }

    @Test
    void processEvent_GoldChangeEvent_decreases_player_gold() {
        GoldChangeEvent event = new GoldChangeEvent(-3);

        dispatcher.processEvent(event);

        assertThat(state.player().getGold())
            .as("processEvent(GoldChangeEvent) with negative delta must decrease player gold")
            .isEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // ItemEvent
    // -----------------------------------------------------------------------

    @Test
    void processEvent_ItemEvent_add_adds_item_to_inventory() {
        ItemEvent event = new ItemEvent("Torch", ItemAction.GAIN, 1);

        dispatcher.processEvent(event);

        assertThat(state.player().getInventory().has("Torch"))
            .as("processEvent(ItemEvent GAIN) must add the named item to the player's inventory")
            .isTrue();
    }

    @Test
    void processEvent_ItemEvent_remove_removes_item_from_inventory() {
        // First add directly to inventory, then remove via processEvent
        Item key = new Item("Key", "A brass key.", ItemCategory.PASSIVE, false, ScriptBlock.empty());
        state.player().getInventory().add(key);
        ItemEvent removeEvent = new ItemEvent("Key", ItemAction.LOSS, 1);

        dispatcher.processEvent(removeEvent);

        assertThat(state.player().getInventory().has("Key"))
            .as("processEvent(ItemEvent LOSS) must remove the named item from inventory")
            .isFalse();
    }

    // -----------------------------------------------------------------------
    // NavigateEvent — behavioral: navigates state to target section
    // -----------------------------------------------------------------------

    @Test
    void processEvent_NavigateEvent_navigates_state_to_target_section() {
        Adventure adventure = adventureWithSections(normalSection(1), normalSection(5));
        state.navigateTo(adventure.getSection(1));
        NavigateEvent event = new NavigateEvent(5);

        dispatcherWithDice(new FixedDice(3)).processEvent(event, adventure);

        assertThat(state.currentSection().number())
            .as("processEvent(NavigateEvent(5)) must navigate state to section 5")
            .isEqualTo(5);
    }

    // -----------------------------------------------------------------------
    // LuckTestEvent — passing roll: FixedDice(1) → 2d6=2, LUCK=10 → success
    // -----------------------------------------------------------------------

    @Test
    void processEvent_LuckTestEvent_passing_roll_navigates_to_successSection() {
        // FixedDice(1) → roll2d6() = 1+1 = 2; LUCK=10; 2 <= 10 → success
        Adventure adventure = adventureWithSections(
            normalSection(1), victorySection(10), normalSection(20));
        state.navigateTo(adventure.getSection(1));

        dispatcherWithDice(new FixedDice(1)).processEvent(new LuckTestEvent(10, 20), adventure);

        assertThat(state.currentSection().number())
            .as("LuckTestEvent passing roll (2d6=2 <= LUCK=10) must navigate to successSection 10")
            .isEqualTo(10);
    }

    @Test
    void processEvent_LuckTestEvent_passing_roll_decreases_luck_by_one() {
        // LUCK starts at 10; after test it must be 9 regardless of outcome
        Adventure adventure = adventureWithSections(
            normalSection(1), victorySection(10), normalSection(20));
        state.navigateTo(adventure.getSection(1));
        int luckBefore = state.player().getLuck();

        dispatcherWithDice(new FixedDice(1)).processEvent(new LuckTestEvent(10, 20), adventure);

        assertThat(state.player().getLuck())
            .as("LuckTestEvent must always decrease LUCK by 1 after the test")
            .isEqualTo(luckBefore - 1);
    }

    // -----------------------------------------------------------------------
    // LuckTestEvent — failing roll: FixedDice(6) → 2d6=12, LUCK=10 → failure
    // -----------------------------------------------------------------------

    @Test
    void processEvent_LuckTestEvent_failing_roll_navigates_to_failSection() {
        // FixedDice(6) → roll2d6() = 6+6 = 12; LUCK=10; 12 > 10 → failure
        Adventure adventure = adventureWithSections(
            normalSection(1), victorySection(10), normalSection(20));
        state.navigateTo(adventure.getSection(1));

        dispatcherWithDice(new FixedDice(6)).processEvent(new LuckTestEvent(10, 20), adventure);

        assertThat(state.currentSection().number())
            .as("LuckTestEvent failing roll (2d6=12 > LUCK=10) must navigate to failSection 20")
            .isEqualTo(20);
    }

    @Test
    void processEvent_LuckTestEvent_failing_roll_decreases_luck_by_one() {
        Adventure adventure = adventureWithSections(
            normalSection(1), victorySection(10), normalSection(20));
        state.navigateTo(adventure.getSection(1));
        int luckBefore = state.player().getLuck();

        dispatcherWithDice(new FixedDice(6)).processEvent(new LuckTestEvent(10, 20), adventure);

        assertThat(state.player().getLuck())
            .as("LuckTestEvent must always decrease LUCK by 1 even on a failing roll")
            .isEqualTo(luckBefore - 1);
    }

    // -----------------------------------------------------------------------
    // SkillTestEvent — passing roll: FixedDice(1) → 2d6=2, SKILL=10 → success
    // -----------------------------------------------------------------------

    @Test
    void processEvent_SkillTestEvent_passing_roll_navigates_to_successSection() {
        // FixedDice(1) → roll2d6() = 2; SKILL=10; 2 <= 10 → success
        Adventure adventure = adventureWithSections(
            normalSection(1), victorySection(10), normalSection(20));
        state.navigateTo(adventure.getSection(1));

        dispatcherWithDice(new FixedDice(1)).processEvent(new SkillTestEvent(10, 20), adventure);

        assertThat(state.currentSection().number())
            .as("SkillTestEvent passing roll (2d6=2 <= SKILL=10) must navigate to successSection 10")
            .isEqualTo(10);
    }

    // -----------------------------------------------------------------------
    // SkillTestEvent — failing roll: FixedDice(6) → 2d6=12, SKILL=10 → failure
    // -----------------------------------------------------------------------

    @Test
    void processEvent_SkillTestEvent_failing_roll_navigates_to_failSection() {
        // FixedDice(6) → roll2d6() = 12; SKILL=10; 12 > 10 → failure
        Adventure adventure = adventureWithSections(
            normalSection(1), victorySection(10), normalSection(20));
        state.navigateTo(adventure.getSection(1));

        dispatcherWithDice(new FixedDice(6)).processEvent(new SkillTestEvent(10, 20), adventure);

        assertThat(state.currentSection().number())
            .as("SkillTestEvent failing roll (2d6=12 > SKILL=10) must navigate to failSection 20")
            .isEqualTo(20);
    }

    // -----------------------------------------------------------------------
    // CombatEvent — defeat with failureSection navigates there (does NOT set game over)
    // -----------------------------------------------------------------------

    @Test
    void processEvent_CombatEvent_defeat_navigates_to_failureSection_without_setting_game_over() {
        // The failure section (typically INSTANT_DEATH) must be rendered by the main game loop.
        // Prematurely setting game over here would skip that section's narrative.
        CombatSystemRegistry defeatRegistry = new CombatSystemRegistry() {
            @Override
            public CombatSystem get(String id) {
                return new CombatSystem() {
                    @Override public String id() { return id; }
                    @Override
                    public CombatOutcome run(
                            Player player,
                            java.util.List<com.tas.neo.domain.party.PartyMember> participants,
                            java.util.List<com.tas.neo.domain.combat.Creature> opponents,
                            java.util.Map<String, Object> params,
                            CombatSystemRegistry reg,
                            HookDispatcher hooks,
                            com.tas.neo.io.GameInput inp,
                            com.tas.neo.io.GameOutput out,
                            com.tas.neo.mechanics.Dice dice) {
                        return new CombatOutcome(CombatOutcomeType.DEFEAT, Optional.empty());
                    }
                };
            }
            @Override public boolean has(String id) { return true; }
        };

        Adventure adventure = adventureWithSections(
            normalSection(1), normalSection(30), normalSection(40));
        state.navigateTo(adventure.getSection(1));

        CombatEvent event = new CombatEvent(
            "personal", List.of(), List.of(),
            false, Map.of(), ScriptBlock.empty(), 30, 40
        );

        dispatcherWithCombatRegistry(defeatRegistry).processEvent(event, adventure);

        assertThat(state.currentSection().number())
            .as("processEvent(CombatEvent) with DEFEAT outcome and failureSection=40 must navigate to section 40")
            .isEqualTo(40);
        assertThat(state.isGameOver())
            .as("game over must NOT be set by processEvent — the game loop renders the failure section and sets it")
            .isFalse();
    }

    @Test
    void processEvent_CombatEvent_defeat_with_no_failureSection_sets_game_over() {
        CombatSystemRegistry defeatRegistry = new CombatSystemRegistry() {
            @Override
            public CombatSystem get(String id) {
                return new CombatSystem() {
                    @Override public String id() { return id; }
                    @Override
                    public CombatOutcome run(
                            Player player,
                            java.util.List<com.tas.neo.domain.party.PartyMember> participants,
                            java.util.List<com.tas.neo.domain.combat.Creature> opponents,
                            java.util.Map<String, Object> params,
                            CombatSystemRegistry reg,
                            HookDispatcher hooks,
                            com.tas.neo.io.GameInput inp,
                            com.tas.neo.io.GameOutput out,
                            com.tas.neo.mechanics.Dice dice) {
                        return new CombatOutcome(CombatOutcomeType.DEFEAT, Optional.empty());
                    }
                };
            }
            @Override public boolean has(String id) { return true; }
        };

        Adventure adventure = adventureWithSections(normalSection(1));
        state.navigateTo(adventure.getSection(1));

        CombatEvent event = new CombatEvent(
            "personal", List.of(), List.of(),
            false, Map.of(), ScriptBlock.empty(), 0, 0  // no successSection, no failureSection
        );

        dispatcherWithCombatRegistry(defeatRegistry).processEvent(event, adventure);

        assertThat(state.isGameOver())
            .as("DEFEAT with no failureSection must set game over immediately")
            .isTrue();
    }

    // CombatEvent — victory with successSection navigates to that section
    // -----------------------------------------------------------------------

    @Test
    void processEvent_CombatEvent_victory_navigates_to_successSection() {
        CombatSystemRegistry victoryRegistry = new CombatSystemRegistry() {
            @Override
            public CombatSystem get(String id) {
                return new CombatSystem() {
                    @Override public String id() { return id; }
                    @Override
                    public CombatOutcome run(
                            Player player,
                            java.util.List<com.tas.neo.domain.party.PartyMember> participants,
                            java.util.List<com.tas.neo.domain.combat.Creature> opponents,
                            java.util.Map<String, Object> params,
                            CombatSystemRegistry reg,
                            HookDispatcher hooks,
                            com.tas.neo.io.GameInput inp,
                            com.tas.neo.io.GameOutput out,
                            com.tas.neo.mechanics.Dice dice) {
                        return new CombatOutcome(CombatOutcomeType.VICTORY, Optional.empty());
                    }
                };
            }
            @Override public boolean has(String id) { return true; }
        };

        Adventure adventure = adventureWithSections(
            normalSection(1), victorySection(30), normalSection(40));
        state.navigateTo(adventure.getSection(1));

        CombatEvent event = new CombatEvent(
            "personal", List.of(), List.of(),
            false, Map.of(), ScriptBlock.empty(), 30, 40
        );

        dispatcherWithCombatRegistry(victoryRegistry).processEvent(event, adventure);

        assertThat(state.currentSection().number())
            .as("processEvent(CombatEvent) with VICTORY outcome and successSection=30 must navigate to section 30")
            .isEqualTo(30);
    }

    // -----------------------------------------------------------------------
    // Logging: ItemEvent and StatChangeEvent emit OutputEvents to GameLogger
    // -----------------------------------------------------------------------

    @Test
    void processEvent_ItemEvent_gain_logs_ItemGained_event() {
        RecordingGameLogger logger = new RecordingGameLogger();
        ItemEvent event = new ItemEvent("Rope", ItemAction.GAIN, 1);

        dispatcherWithLogger(logger).processEvent(event);

        assertThat(logger.sessionLog().events())
            .as("processEvent(ItemEvent GAIN) must log an OutputEvent.ItemGained to the GameLogger")
            .hasAtLeastOneElementOfType(com.tas.neo.io.OutputEvent.ItemGained.class);
        com.tas.neo.io.OutputEvent.ItemGained logged =
            (com.tas.neo.io.OutputEvent.ItemGained) logger.sessionLog().events().stream()
                .filter(e -> e instanceof com.tas.neo.io.OutputEvent.ItemGained)
                .findFirst().orElseThrow();
        assertThat(logged.itemName())
            .as("ItemGained event must carry the item name from the ItemEvent")
            .isEqualTo("Rope");
    }

    @Test
    void processEvent_ItemEvent_loss_logs_ItemLost_event() {
        RecordingGameLogger logger = new RecordingGameLogger();
        Item key = new Item("Key", "", ItemCategory.PASSIVE, false, ScriptBlock.empty());
        state.player().getInventory().add(key);
        ItemEvent event = new ItemEvent("Key", ItemAction.LOSS, 1);

        dispatcherWithLogger(logger).processEvent(event);

        assertThat(logger.sessionLog().events())
            .as("processEvent(ItemEvent LOSS) must log an OutputEvent.ItemLost to the GameLogger")
            .hasAtLeastOneElementOfType(com.tas.neo.io.OutputEvent.ItemLost.class);
        com.tas.neo.io.OutputEvent.ItemLost logged =
            (com.tas.neo.io.OutputEvent.ItemLost) logger.sessionLog().events().stream()
                .filter(e -> e instanceof com.tas.neo.io.OutputEvent.ItemLost)
                .findFirst().orElseThrow();
        assertThat(logged.itemName())
            .as("ItemLost event must carry the item name from the ItemEvent")
            .isEqualTo("Key");
    }

    @Test
    void processEvent_CombatEvent_logs_CombatResolved_when_outcome_has_combat_result() {
        RecordingGameLogger logger = new RecordingGameLogger();
        CombatResult combatResult = new CombatResult(true, 3, 4);
        CombatSystemRegistry registry = new CombatSystemRegistry() {
            @Override public CombatSystem get(String id) {
                return new CombatSystem() {
                    @Override public String id() { return id; }
                    @Override
                    public CombatOutcome run(
                            Player player,
                            java.util.List<com.tas.neo.domain.party.PartyMember> participants,
                            java.util.List<com.tas.neo.domain.combat.Creature> opponents,
                            java.util.Map<String, Object> params,
                            CombatSystemRegistry reg, HookDispatcher hooks,
                            com.tas.neo.io.GameInput inp, com.tas.neo.io.GameOutput out,
                            com.tas.neo.mechanics.Dice dice) {
                        return new CombatOutcome(CombatOutcomeType.VICTORY, Optional.empty(),
                            Optional.of(combatResult));
                    }
                };
            }
            @Override public boolean has(String id) { return true; }
        };

        Adventure adventure = adventureWithSections(normalSection(1), victorySection(30), normalSection(40));
        state.navigateTo(adventure.getSection(1));

        Creature guard = new Creature("Gate Guard", 7, 8);
        CombatEvent event = new CombatEvent(
            "personal", List.of(), List.of(guard),
            false, Map.of(), ScriptBlock.empty(), 30, 40);

        new HookDispatcher(new NoOpScriptEngine(), new ScriptedInput(), output, state,
            new AdventureScriptState(), registry, new FixedDice(3), logger)
            .processEvent(event, adventure);

        assertThat(logger.sessionLog().events())
            .as("processEvent(CombatEvent) with a combatResult must log a CombatResolved event")
            .hasAtLeastOneElementOfType(com.tas.neo.io.OutputEvent.CombatResolved.class);
        com.tas.neo.io.OutputEvent.CombatResolved resolved =
            (com.tas.neo.io.OutputEvent.CombatResolved) logger.sessionLog().events().stream()
                .filter(e -> e instanceof com.tas.neo.io.OutputEvent.CombatResolved)
                .findFirst().orElseThrow();
        assertThat(resolved.opponentName())
            .as("CombatResolved must carry the first opponent's name")
            .isEqualTo("Gate Guard");
        assertThat(resolved.rounds())
            .as("CombatResolved must carry the round count from combatResult")
            .isEqualTo(3);
        assertThat(resolved.staminaLost())
            .as("CombatResolved must carry staminaLost from combatResult")
            .isEqualTo(4);
        assertThat(resolved.playerWon())
            .as("CombatResolved playerWon must be true when the outcome type is VICTORY")
            .isTrue();
    }

    @Test
    void processEvent_StatChangeEvent_logs_StatChanged_event_with_delta_and_new_value() {
        RecordingGameLogger logger = new RecordingGameLogger();
        StatChangeEvent event = new StatChangeEvent(AttributeType.STAMINA, -4);

        dispatcherWithLogger(logger).processEvent(event);

        assertThat(logger.sessionLog().events())
            .as("processEvent(StatChangeEvent) must log an OutputEvent.StatChanged to the GameLogger")
            .hasAtLeastOneElementOfType(com.tas.neo.io.OutputEvent.StatChanged.class);
        com.tas.neo.io.OutputEvent.StatChanged logged =
            (com.tas.neo.io.OutputEvent.StatChanged) logger.sessionLog().events().stream()
                .filter(e -> e instanceof com.tas.neo.io.OutputEvent.StatChanged)
                .findFirst().orElseThrow();
        assertThat(logged.attribute())
            .as("StatChanged event must carry the attribute name")
            .isEqualTo("STAMINA");
        assertThat(logged.delta())
            .as("StatChanged event must carry the delta")
            .isEqualTo(-4);
        assertThat(logged.newValue())
            .as("StatChanged event must carry the post-change value (18 - 4 = 14)")
            .isEqualTo(14);
    }
}
