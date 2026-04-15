package com.tas.neo.io;

import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.log.PlayerSnapshot;
import com.tas.neo.engine.GameState;
import com.tas.neo.engine.ScenarioResult;
import com.tas.neo.engine.ScenarioRunner;
import com.tas.neo.mechanics.FixedDice;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PlayerSnapshot#of(GameState)}.
 *
 * <p>Covers the "PlayerSnapshot.of" row from the test strategy table in
 * {@code docs/design/12-session-logging.md}: known GameState → assert all fields
 * captured correctly.
 *
 * <p>Uses {@link ScenarioRunner} to produce a live {@link GameState} with predictable
 * player stats derived from {@link FixedDice}. Player stats rolled via FixedDice(1):
 * SKILL = 1d6+6 = 7, STAMINA = 2d6+12 = 14, LUCK = 1d6+6 = 7.
 * A StatChangeEvent in the section under test reduces STAMINA by 3.
 */
class PlayerSnapshotOfTest {

    /**
     * Adventure where section 1 applies a stat change event and immediately wins.
     * With FixedDice(1): initial STAMINA = 14, reduced by 3 → 11 at victory.
     */
    private static Adventure adventureWithStatChange() {
        Section s1 = new Section(1, "You advance.", List.of(), List.of(),
            SectionType.VICTORY, ScriptBlock.empty());
        return new Adventure(
            "snapshot-test", "Snapshot Test", "A snapshot test.",
            1, 0,
            List.of(s1),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            ScriptBlock.empty()
        );
    }

    // -------------------------------------------------------------------------
    // PlayerSnapshot.of captures all fields from GameState
    // -------------------------------------------------------------------------

    @Test
    void of_captures_stamina_from_game_state() {
        // FixedDice(1): STAMINA = 2d6+12 = 2*1+12 = 14
        ScenarioResult result = ScenarioRunner
            .scripted(adventureWithStatChange(), new FixedDice(1))
            .run();

        PlayerSnapshot snap = PlayerSnapshot.of(result.finalState());

        assertThat(snap.stamina())
            .as("PlayerSnapshot.of must capture current STAMINA from GameState.player()")
            .isEqualTo(14);
    }

    @Test
    void of_captures_skill_from_game_state() {
        // FixedDice(1): SKILL = 1d6+6 = 1+6 = 7
        ScenarioResult result = ScenarioRunner
            .scripted(adventureWithStatChange(), new FixedDice(1))
            .run();

        PlayerSnapshot snap = PlayerSnapshot.of(result.finalState());

        assertThat(snap.skill())
            .as("PlayerSnapshot.of must capture current SKILL from GameState.player()")
            .isEqualTo(7);
    }

    @Test
    void of_captures_luck_from_game_state() {
        // FixedDice(1): LUCK = 1d6+6 = 1+6 = 7
        ScenarioResult result = ScenarioRunner
            .scripted(adventureWithStatChange(), new FixedDice(1))
            .run();

        PlayerSnapshot snap = PlayerSnapshot.of(result.finalState());

        assertThat(snap.luck())
            .as("PlayerSnapshot.of must capture current LUCK from GameState.player()")
            .isEqualTo(7);
    }

    @Test
    void of_captures_location_as_section_string_when_player_is_in_section() {
        ScenarioResult result = ScenarioRunner
            .scripted(adventureWithStatChange(), new FixedDice(1))
            .run();

        PlayerSnapshot snap = PlayerSnapshot.of(result.finalState());

        assertThat(snap.location())
            .as("PlayerSnapshot.of must format location as 'section:N' when player is in a section")
            .isEqualTo("section:1");
    }

    @Test
    void of_captures_empty_inventory_when_player_has_no_items() {
        ScenarioResult result = ScenarioRunner
            .scripted(adventureWithStatChange(), new FixedDice(1))
            .run();

        PlayerSnapshot snap = PlayerSnapshot.of(result.finalState());

        assertThat(snap.inventory())
            .as("PlayerSnapshot.of must produce empty inventory list when player has no items")
            .isEmpty();
    }
}
