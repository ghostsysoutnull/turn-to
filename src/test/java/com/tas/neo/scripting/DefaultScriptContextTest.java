package com.tas.neo.scripting;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.item.Inventory;
import com.tas.neo.domain.player.Attribute;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.domain.player.Player;
import com.tas.neo.io.RecordingOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@code DefaultScriptContext}.
 *
 * <p>Covers the test strategy rows from {@code docs/design/07-scripting-engine.md}:
 * <ul>
 *   <li>navigateTo blocked in onChoices context — UnsupportedOperationException</li>
 *   <li>ctx.currentSection() returns -1 when called from a cell (grid) context</li>
 *   <li>ctx.hideChoice(id) removes matching choice; no-op if id not found</li>
 * </ul>
 */
class DefaultScriptContextTest {

    private RecordingOutput output;
    private Player player;

    @BeforeEach
    void setUp() {
        output = new RecordingOutput();
        player = playerWithStats(10, 20, 6);
    }

    // -----------------------------------------------------------------------
    // navigateTo blocked in onChoices context
    // -----------------------------------------------------------------------

    @Test
    void navigateTo_throws_when_context_is_in_choices_mode() {
        List<Choice> choices = new ArrayList<>();
        DefaultScriptContext ctx = DefaultScriptContext.forChoices(player, output, choices, 1);

        assertThatThrownBy(() -> ctx.navigateTo(5))
            .as("navigateTo must be disallowed in the onChoices hook context")
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // -----------------------------------------------------------------------
    // currentSection() returns -1 from a cell context
    // -----------------------------------------------------------------------

    @Test
    void currentSection_returns_minus_one_from_cell_context() {
        DefaultScriptContext ctx = DefaultScriptContext.forCell(player, output, new ArrayList<>());

        assertThat(ctx.currentSection())
            .as("currentSection() must return -1 when context is a grid cell (no section number)")
            .isEqualTo(-1);
    }

    // -----------------------------------------------------------------------
    // currentSection() returns the correct section number from section context
    // -----------------------------------------------------------------------

    @Test
    void currentSection_returns_section_number_from_section_context() {
        DefaultScriptContext ctx = DefaultScriptContext.forSection(player, output, new ArrayList<>(), 7);

        assertThat(ctx.currentSection())
            .as("currentSection() must return the section number passed at construction")
            .isEqualTo(7);
    }

    // -----------------------------------------------------------------------
    // hideChoice
    // -----------------------------------------------------------------------

    @Test
    void hideChoice_removes_choice_with_matching_id() {
        List<Choice> choices = new ArrayList<>();
        choices.add(new Choice("Go north", new SectionTarget(2), Optional.empty(), Optional.of("go-north")));
        choices.add(new Choice("Go south", new SectionTarget(3), Optional.empty(), Optional.of("go-south")));

        DefaultScriptContext ctx = DefaultScriptContext.forChoices(player, output, choices, 1);

        ctx.hideChoice("go-north");

        assertThat(choices)
            .as("hideChoice must remove the choice whose id matches the given id")
            .hasSize(1);
        assertThat(choices.get(0).id())
            .as("remaining choice must be the one that was not hidden")
            .contains("go-south");
    }

    @Test
    void hideChoice_is_noop_when_id_not_found() {
        List<Choice> choices = new ArrayList<>();
        choices.add(new Choice("Go west", new SectionTarget(4), Optional.empty(), Optional.of("go-west")));

        DefaultScriptContext ctx = DefaultScriptContext.forChoices(player, output, choices, 1);

        ctx.hideChoice("nonexistent-id");

        assertThat(choices)
            .as("hideChoice must not modify the list when no choice matches the given id")
            .hasSize(1);
    }

    @Test
    void hideChoice_is_noop_when_choice_has_no_id() {
        List<Choice> choices = new ArrayList<>();
        choices.add(new Choice("Unnamed", new SectionTarget(5), Optional.empty(), Optional.empty()));

        DefaultScriptContext ctx = DefaultScriptContext.forChoices(player, output, choices, 1);

        ctx.hideChoice("some-id");

        assertThat(choices)
            .as("hideChoice must not remove choices that have no id")
            .hasSize(1);
    }

    // -----------------------------------------------------------------------
    // showMessage delegates to output
    // -----------------------------------------------------------------------

    @Test
    void showMessage_writes_to_output() {
        DefaultScriptContext ctx = DefaultScriptContext.forSection(player, output, new ArrayList<>(), 1);

        ctx.showMessage("Hello from script");

        assertThat(output.messages())
            .as("showMessage must forward the message to GameOutput")
            .containsExactly("Hello from script");
    }

    // -----------------------------------------------------------------------
    // modifyStat and getStat delegate to Player
    // -----------------------------------------------------------------------

    @Test
    void modifyStat_adjusts_player_attribute() {
        DefaultScriptContext ctx = DefaultScriptContext.forSection(player, output, new ArrayList<>(), 1);

        ctx.modifyStat("SKILL", -1);

        assertThat(ctx.getStat("SKILL"))
            .as("modifyStat must adjust the player's stat; getStat must reflect the change")
            .isEqualTo(9);
    }

    // -----------------------------------------------------------------------
    // modifyGold and getGold delegate to Player
    // -----------------------------------------------------------------------

    @Test
    void modifyGold_and_getGold_round_trip() {
        DefaultScriptContext ctx = DefaultScriptContext.forSection(player, output, new ArrayList<>(), 1);

        ctx.modifyGold(5);

        assertThat(ctx.getGold())
            .as("modifyGold must update player gold; getGold must reflect the new amount")
            .isEqualTo(5);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Player playerWithStats(int skill, int stamina, int luck) {
        Map<AttributeType, Attribute> attributes = new EnumMap<>(AttributeType.class);
        attributes.put(AttributeType.SKILL, new Attribute(AttributeType.SKILL, skill, skill));
        attributes.put(AttributeType.STAMINA, new Attribute(AttributeType.STAMINA, stamina, stamina));
        attributes.put(AttributeType.LUCK, new Attribute(AttributeType.LUCK, luck, luck));
        return new Player(attributes, new Inventory(), 0, 0);
    }
}
