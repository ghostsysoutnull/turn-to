package com.tas.neo.engine;

import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.location.Cell;
import com.tas.neo.domain.location.Direction;
import com.tas.neo.domain.location.Grid;
import com.tas.neo.domain.party.GameOverConsequence;
import com.tas.neo.domain.party.MemberState;
import com.tas.neo.domain.party.PartyMember;
import com.tas.neo.domain.party.PartyMemberStat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GameState}.
 *
 * <p>Covers the GameState contract from {@code docs/design/04-engine.md}:
 * <ul>
 *   <li>isTerminal returns true when gameOver or victory is set</li>
 *   <li>navigateTo clears grid state</li>
 *   <li>navigateToCell clears section state</li>
 *   <li>isInGrid reflects grid/section transitions</li>
 *   <li>party member lifecycle state transitions</li>
 * </ul>
 */
class GameStateTest {

    private GameState state;

    @BeforeEach
    void setUp() {
        state = new GameState();
    }

    private Section section(int number) {
        return new Section(number, "narrative", List.of(), List.of(),
                           SectionType.NORMAL, ScriptBlock.empty());
    }

    private Cell cell(int x, int y) {
        return new Cell(Optional.empty(), x, y, 0, "A cell.",
                        List.of(), ScriptBlock.empty(), Map.of(), List.of());
    }

    private Grid grid(String id, Cell... cells) {
        var cellMap = new java.util.LinkedHashMap<String, Cell>();
        for (Cell c : cells) {
            cellMap.put(c.x() + "," + c.y() + ",0", c);
        }
        return new Grid(id, 10, 10, 1, cellMap);
    }

    // -----------------------------------------------------------------------
    // Terminal flag behaviour
    // -----------------------------------------------------------------------

    @Test
    void isTerminal_returns_false_initially() {
        assertThat(state.isTerminal())
            .as("GameState must not be terminal before any flag is set")
            .isFalse();
    }

    @Test
    void isGameOver_returns_false_initially() {
        assertThat(state.isGameOver())
            .as("isGameOver must return false before setGameOver is called")
            .isFalse();
    }

    @Test
    void isVictory_returns_false_initially() {
        assertThat(state.isVictory())
            .as("isVictory must return false before setVictory is called")
            .isFalse();
    }

    @Test
    void setGameOver_makes_isTerminal_true() {
        state.setGameOver();

        assertThat(state.isTerminal())
            .as("isTerminal must return true after setGameOver")
            .isTrue();
    }

    @Test
    void setVictory_makes_isTerminal_true() {
        state.setVictory();

        assertThat(state.isTerminal())
            .as("isTerminal must return true after setVictory")
            .isTrue();
    }

    @Test
    void setGameOver_sets_gameOver_flag() {
        state.setGameOver();

        assertThat(state.isGameOver())
            .as("isGameOver must return true after setGameOver is called")
            .isTrue();
    }

    @Test
    void setVictory_sets_victory_flag() {
        state.setVictory();

        assertThat(state.isVictory())
            .as("isVictory must return true after setVictory is called")
            .isTrue();
    }

    // -----------------------------------------------------------------------
    // Section navigation
    // -----------------------------------------------------------------------

    @Test
    void navigateTo_sets_current_section() {
        Section s = section(42);

        state.navigateTo(s);

        assertThat(state.currentSection())
            .as("currentSection must return the section passed to navigateTo")
            .isSameAs(s);
    }

    @Test
    void navigateTo_clears_grid_state() {
        Cell c = cell(0, 0);
        Grid g = grid("dungeon", c);
        state.navigateToCell(g, c);

        state.navigateTo(section(1));

        assertThat(state.isInGrid())
            .as("navigateTo must clear any active grid state")
            .isFalse();
        assertThat(state.currentGrid())
            .as("currentGrid must be empty after navigateTo")
            .isEmpty();
        assertThat(state.currentCell())
            .as("currentCell must be empty after navigateTo")
            .isEmpty();
    }

    // -----------------------------------------------------------------------
    // Grid navigation
    // -----------------------------------------------------------------------

    @Test
    void navigateToCell_sets_isInGrid_true() {
        Cell c = cell(1, 2);
        Grid g = grid("dungeon", c);

        state.navigateToCell(g, c);

        assertThat(state.isInGrid())
            .as("isInGrid must return true after navigateToCell")
            .isTrue();
    }

    @Test
    void navigateToCell_returns_correct_cell() {
        Cell c = cell(3, 5);
        Grid g = grid("dungeon", c);

        state.navigateToCell(g, c);

        assertThat(state.currentCell())
            .as("currentCell must return the cell passed to navigateToCell")
            .contains(c);
    }

    @Test
    void navigateToCell_returns_correct_grid() {
        Cell c = cell(0, 0);
        Grid g = grid("dungeon-level-1", c);

        state.navigateToCell(g, c);

        assertThat(state.currentGrid())
            .as("currentGrid must return the grid passed to navigateToCell")
            .contains(g);
    }

    @Test
    void navigateToCell_clears_section_context() {
        state.navigateTo(section(5));

        Cell c = cell(0, 0);
        Grid g = grid("dungeon", c);
        state.navigateToCell(g, c);

        assertThat(state.currentSection())
            .as("currentSection must be null when the player is in a grid")
            .isNull();
    }

    @Test
    void isInGrid_returns_false_when_in_section() {
        state.navigateTo(section(1));

        assertThat(state.isInGrid())
            .as("isInGrid must return false when the player is in a section")
            .isFalse();
    }

    // -----------------------------------------------------------------------
    // Party member management
    // -----------------------------------------------------------------------

    private PartyMember partyMember(String id, MemberState memberState) {
        PartyMemberStat hp = new PartyMemberStat("hp", 10, 10);
        return new PartyMember(id, "Friend " + id, "hp",
                               Map.of("hp", hp),
                               new GameOverConsequence("Game over"),
                               memberState);
    }

    @Test
    void getPartyMember_returns_member_by_id() {
        PartyMember member = partyMember("ally-1", MemberState.ACTIVE);
        state.addPartyMember(member);

        assertThat(state.getPartyMember("ally-1"))
            .as("getPartyMember must return the member with the given id")
            .isSameAs(member);
    }

    @Test
    void getPartyMember_returns_null_for_unknown_id() {
        assertThat(state.getPartyMember("unknown"))
            .as("getPartyMember must return null for an id that was never added")
            .isNull();
    }

    @Test
    void activePartyMembers_returns_only_active_members() {
        state.addPartyMember(partyMember("active-1", MemberState.ACTIVE));
        state.addPartyMember(partyMember("waiting-1", MemberState.WAITING));
        state.addPartyMember(partyMember("removed-1", MemberState.REMOVED));

        List<PartyMember> active = state.activePartyMembers();

        assertThat(active)
            .as("activePartyMembers must return only ACTIVE members")
            .hasSize(1);
        assertThat(active.get(0).id())
            .as("The returned active member must be the one with ACTIVE state")
            .isEqualTo("active-1");
    }

    @Test
    void setPartyMemberState_transitions_member_state() {
        state.addPartyMember(partyMember("ally-1", MemberState.WAITING));

        state.setPartyMemberState("ally-1", MemberState.ACTIVE);

        assertThat(state.getPartyMember("ally-1").state())
            .as("setPartyMemberState must update the member's state to the given value")
            .isEqualTo(MemberState.ACTIVE);
    }

    @Test
    void setPartyMemberState_is_no_op_for_unknown_id() {
        // Must not throw when the member id is not registered
        state.setPartyMemberState("ghost", MemberState.REMOVED);

        assertThat(state.getPartyMember("ghost"))
            .as("setPartyMemberState for an unknown id must leave state unchanged (no member added)")
            .isNull();
    }
}
