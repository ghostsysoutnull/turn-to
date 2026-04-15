package com.tas.neo.engine;

import com.tas.neo.combat.CombatSystem;
import com.tas.neo.combat.CombatSystemRegistry;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.event.CombatEvent;
import com.tas.neo.domain.adventure.event.GoldChangeEvent;
import com.tas.neo.domain.adventure.event.ItemAction;
import com.tas.neo.domain.adventure.event.ItemEvent;
import com.tas.neo.domain.adventure.event.LuckTestEvent;
import com.tas.neo.domain.adventure.event.NavigateEvent;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.adventure.event.SkillTestEvent;
import com.tas.neo.domain.adventure.event.StatChangeEvent;
import com.tas.neo.domain.item.Inventory;
import com.tas.neo.domain.item.Item;
import com.tas.neo.domain.item.ItemCategory;
import com.tas.neo.domain.player.Attribute;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.domain.player.Player;
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
    // NavigateEvent
    // -----------------------------------------------------------------------

    @Test
    void processEvent_NavigateEvent_does_not_throw() {
        // NavigateEvent causes immediate section navigation; the event must be handled
        // without throwing. Correctness of navigation is verified in GameTest.
        NavigateEvent event = new NavigateEvent(5);

        // Must not throw
        dispatcher.processEvent(event);
    }

    // -----------------------------------------------------------------------
    // LuckTestEvent and SkillTestEvent
    // -----------------------------------------------------------------------

    @Test
    void processEvent_LuckTestEvent_does_not_throw() {
        LuckTestEvent event = new LuckTestEvent(10, 20);

        // Must not throw
        dispatcher.processEvent(event);
    }

    @Test
    void processEvent_SkillTestEvent_does_not_throw() {
        SkillTestEvent event = new SkillTestEvent(10, 20);

        // Must not throw
        dispatcher.processEvent(event);
    }

    // -----------------------------------------------------------------------
    // CombatEvent
    // -----------------------------------------------------------------------

    @Test
    void processEvent_CombatEvent_does_not_throw() {
        CombatEvent event = new CombatEvent(
            "personal", List.of(), List.of(),
            false, Map.of(), ScriptBlock.empty()
        );

        // Must not throw (combat may not be fully resolved in stub form, but must not crash)
        dispatcher.processEvent(event);
    }
}
