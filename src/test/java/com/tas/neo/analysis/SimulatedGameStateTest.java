package com.tas.neo.analysis;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedGameStateTest {

    // -------------------------------------------------------------------------
    // Inventory
    // -------------------------------------------------------------------------

    @Test
    void addItem_makes_item_available() {
        SimulatedGameState state = new SimulatedGameState();
        state.addItem("Sword");
        assertThat(state.hasItem("Sword"))
            .as("item added via addItem must be in inventory")
            .isTrue();
    }

    @Test
    void addItem_same_item_twice_is_still_present() {
        SimulatedGameState state = new SimulatedGameState();
        state.addItem("Potion");
        state.addItem("Potion");
        assertThat(state.hasItem("Potion"))
            .as("adding same item twice must not corrupt inventory")
            .isTrue();
    }

    @Test
    void removeItem_removes_from_inventory() {
        SimulatedGameState state = new SimulatedGameState();
        state.addItem("Key");
        state.removeItem("Key");
        assertThat(state.hasItem("Key"))
            .as("item must no longer be present after removeItem")
            .isFalse();
    }

    @Test
    void removeItem_not_in_inventory_is_noop() {
        SimulatedGameState state = new SimulatedGameState();
        state.removeItem("Ghost");
        assertThat(state.inventory())
            .as("removing a non-existent item must leave inventory unchanged")
            .isEmpty();
    }

    @Test
    void inventory_returns_all_held_items() {
        SimulatedGameState state = new SimulatedGameState();
        state.addItem("Map");
        state.addItem("Coin");
        assertThat(state.inventory())
            .as("inventory must return all added items")
            .containsExactlyInAnyOrder("Map", "Coin");
    }

    @Test
    void inventory_snapshot_is_independent_of_later_mutations() {
        SimulatedGameState state = new SimulatedGameState();
        state.addItem("Ring");
        Set<String> snapshot = state.inventory();
        state.addItem("Cloak");
        assertThat(snapshot)
            .as("inventory snapshot must not reflect mutations made after the call")
            .doesNotContain("Cloak");
    }

    // -------------------------------------------------------------------------
    // Gold
    // -------------------------------------------------------------------------

    @Test
    void gold_starts_at_zero() {
        assertThat(new SimulatedGameState().gold())
            .as("gold must start at 0")
            .isZero();
    }

    @Test
    void modifyGold_increases_gold() {
        SimulatedGameState state = new SimulatedGameState();
        state.modifyGold(10);
        assertThat(state.gold()).isEqualTo(10);
    }

    @Test
    void modifyGold_negative_delta_decreases_gold() {
        SimulatedGameState state = new SimulatedGameState();
        state.modifyGold(5);
        state.modifyGold(-3);
        assertThat(state.gold()).isEqualTo(2);
    }

    @Test
    void modifyGold_floors_at_zero() {
        SimulatedGameState state = new SimulatedGameState();
        state.modifyGold(-100);
        assertThat(state.gold())
            .as("gold must not go below 0")
            .isZero();
    }

    // -------------------------------------------------------------------------
    // Stats
    // -------------------------------------------------------------------------

    @Test
    void stat_returns_value_set_via_setStat() {
        SimulatedGameState state = new SimulatedGameState();
        state.setStat("SKILL", 10);
        assertThat(state.stat("SKILL")).isEqualTo(10);
    }

    @Test
    void modifyStat_applies_delta() {
        SimulatedGameState state = new SimulatedGameState();
        state.setStat("STAMINA", 20);
        state.modifyStat("STAMINA", -3);
        assertThat(state.stat("STAMINA")).isEqualTo(17);
    }

    @Test
    void modifyStat_floors_at_zero() {
        SimulatedGameState state = new SimulatedGameState();
        state.setStat("STAMINA", 2);
        state.modifyStat("STAMINA", -10);
        assertThat(state.stat("STAMINA"))
            .as("stat must not go below 0")
            .isZero();
    }

    // -------------------------------------------------------------------------
    // Visit tracking
    // -------------------------------------------------------------------------

    @Test
    void recordVisit_increments_visitCount() {
        SimulatedGameState state = new SimulatedGameState();
        state.recordVisit(5);
        state.recordVisit(5);
        assertThat(state.visitCount(5))
            .as("visitCount must reflect the number of times a section was visited")
            .isEqualTo(2);
    }

    @Test
    void visitCount_unvisited_section_returns_zero() {
        assertThat(new SimulatedGameState().visitCount(99))
            .as("visitCount for an unvisited section must return 0")
            .isZero();
    }

    @Test
    void sectionsVisited_preserves_insertion_order() {
        SimulatedGameState state = new SimulatedGameState();
        state.recordVisit(1);
        state.recordVisit(3);
        state.recordVisit(2);
        assertThat(state.sectionsVisited())
            .as("sectionsVisited must preserve the order sections were visited")
            .containsExactly(1, 3, 2);
    }

    // -------------------------------------------------------------------------
    // Chapter snapshots
    // -------------------------------------------------------------------------

    @Test
    void chapterSnapshot_captures_inventory_at_snapshot_time() {
        SimulatedGameState state = new SimulatedGameState();
        state.addItem("Pass");
        state.recordChapterSnapshot("ch2", 41);
        state.addItem("Sword");

        ChapterSnapshot snapshot = state.chapterSnapshots().get(0);
        assertThat(snapshot.inventory())
            .as("chapter snapshot inventory must reflect items held at snapshot time, not after")
            .containsExactly("Pass")
            .doesNotContain("Sword");
    }

    @Test
    void chapterSnapshot_captures_gold_at_snapshot_time() {
        SimulatedGameState state = new SimulatedGameState();
        state.modifyGold(7);
        state.recordChapterSnapshot("ch2", 41);
        state.modifyGold(3);

        ChapterSnapshot snapshot = state.chapterSnapshots().get(0);
        assertThat(snapshot.gold())
            .as("chapter snapshot gold must reflect gold held at snapshot time, not after")
            .isEqualTo(7);
    }

    @Test
    void chapterSnapshot_captures_state_variables_at_snapshot_time() {
        SimulatedGameState state = new SimulatedGameState();
        state.scriptState().set("suspicion", 3);
        state.recordChapterSnapshot("ch2", 41);
        state.scriptState().set("suspicion", 9);

        ChapterSnapshot snapshot = state.chapterSnapshots().get(0);
        assertThat(snapshot.stateVariables().get("suspicion"))
            .as("chapter snapshot state variables must reflect values at snapshot time")
            .isEqualTo(3);
    }

    @Test
    void chapterSnapshots_are_recorded_in_order() {
        SimulatedGameState state = new SimulatedGameState();
        state.recordChapterSnapshot("ch1", 1);
        state.recordChapterSnapshot("ch2", 41);
        assertThat(state.chapterSnapshots())
            .extracting(ChapterSnapshot::chapterId)
            .containsExactly("ch1", "ch2");
    }
}
