package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.SectionTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedScriptContextTest {

    private static SimulatedScriptContext ctx(SimulatedGameState state) {
        return new SimulatedScriptContext(state);
    }

    // -------------------------------------------------------------------------
    // Delegation to SimulatedGameState
    // -------------------------------------------------------------------------

    @Test
    void addItem_delegates_to_state() {
        SimulatedGameState state = new SimulatedGameState();
        ctx(state).addItem("Rope");
        assertThat(state.hasItem("Rope"))
            .as("addItem on context must be reflected in SimulatedGameState")
            .isTrue();
    }

    @Test
    void removeItem_delegates_to_state() {
        SimulatedGameState state = new SimulatedGameState();
        state.addItem("Key");
        ctx(state).removeItem("Key");
        assertThat(state.hasItem("Key"))
            .as("removeItem on context must be reflected in SimulatedGameState")
            .isFalse();
    }

    @Test
    void hasItem_reads_from_state() {
        SimulatedGameState state = new SimulatedGameState();
        state.addItem("Lantern");
        assertThat(ctx(state).hasItem("Lantern"))
            .as("hasItem on context must read from SimulatedGameState")
            .isTrue();
    }

    @Test
    void modifyGold_delegates_to_state() {
        SimulatedGameState state = new SimulatedGameState();
        ctx(state).modifyGold(5);
        assertThat(state.gold())
            .as("modifyGold on context must be reflected in SimulatedGameState")
            .isEqualTo(5);
    }

    @Test
    void modifyStat_delegates_to_state() {
        SimulatedGameState state = new SimulatedGameState();
        state.setStat("STAMINA", 20);
        ctx(state).modifyStat("STAMINA", -4);
        assertThat(state.stat("STAMINA"))
            .as("modifyStat on context must be reflected in SimulatedGameState")
            .isEqualTo(16);
    }

    @Test
    void getStat_reads_from_state() {
        SimulatedGameState state = new SimulatedGameState();
        state.setStat("SKILL", 11);
        assertThat(ctx(state).getStat("SKILL"))
            .as("getStat on context must read from SimulatedGameState")
            .isEqualTo(11);
    }

    @Test
    void getGold_reads_from_state() {
        SimulatedGameState state = new SimulatedGameState();
        state.modifyGold(8);
        assertThat(ctx(state).getGold())
            .as("getGold on context must read from SimulatedGameState")
            .isEqualTo(8);
    }

    // -------------------------------------------------------------------------
    // navigateTo — captured for RunSimulator consumption
    // -------------------------------------------------------------------------

    @Test
    void navigateTo_sets_navigation_target() {
        SimulatedScriptContext ctx = ctx(new SimulatedGameState());
        ctx.navigateTo(42);
        assertThat(ctx.navigationTarget())
            .as("navigateTo must set the navigationTarget for the simulator to consume")
            .hasValue(42);
    }

    @Test
    void navigateTo_not_called_leaves_navigation_target_empty() {
        SimulatedScriptContext ctx = ctx(new SimulatedGameState());
        assertThat(ctx.navigationTarget())
            .as("navigationTarget must be empty when navigateTo was not called")
            .isEmpty();
    }

    @Test
    void navigateTo_last_call_wins() {
        SimulatedScriptContext ctx = ctx(new SimulatedGameState());
        ctx.navigateTo(10);
        ctx.navigateTo(20);
        assertThat(ctx.navigationTarget())
            .as("when navigateTo is called multiple times, last call wins")
            .hasValue(20);
    }

    // -------------------------------------------------------------------------
    // showMessage — no-op
    // -------------------------------------------------------------------------

    @Test
    void showMessage_is_noop_and_produces_no_side_effects() {
        SimulatedGameState state = new SimulatedGameState();
        SimulatedScriptContext ctx = ctx(state);
        ctx.showMessage("Hello world");
        // no exception, inventory and gold unchanged
        assertThat(state.inventory()).isEmpty();
        assertThat(state.gold()).isZero();
    }

    // -------------------------------------------------------------------------
    // addChoice — dynamic choice injection
    // -------------------------------------------------------------------------

    @Test
    void addChoice_appends_to_dynamic_choices() {
        SimulatedScriptContext ctx = ctx(new SimulatedGameState());
        ctx.addChoice("Go north", 7);
        assertThat(ctx.dynamicChoices())
            .as("addChoice must append the choice to dynamicChoices")
            .hasSize(1);
        assertThat(ctx.dynamicChoices().get(0).text()).isEqualTo("Go north");
    }

    @Test
    void addChoice_multiple_calls_appends_in_order() {
        SimulatedScriptContext ctx = ctx(new SimulatedGameState());
        ctx.addChoice("Option A", 1);
        ctx.addChoice("Option B", 2);
        assertThat(ctx.dynamicChoices())
            .extracting(Choice::text)
            .containsExactly("Option A", "Option B");
    }

    @Test
    void dynamicChoices_empty_when_none_added() {
        assertThat(ctx(new SimulatedGameState()).dynamicChoices())
            .as("dynamicChoices must be empty when addChoice was never called")
            .isEmpty();
    }

    // -------------------------------------------------------------------------
    // Unsupported operations — logged as warnings, no exception thrown
    // -------------------------------------------------------------------------

    @Test
    void getPartyMember_logs_warning_and_does_not_throw() {
        SimulatedScriptContext ctx = ctx(new SimulatedGameState());
        ctx.getPartyMember("ally");
        assertThat(ctx.warnings())
            .as("getPartyMember must log a warning as it is unsupported in simulation")
            .isNotEmpty();
    }

    @Test
    void addPartyMember_logs_warning_and_does_not_throw() {
        SimulatedScriptContext ctx = ctx(new SimulatedGameState());
        ctx.addPartyMember("ally");
        assertThat(ctx.warnings())
            .as("addPartyMember must log a warning as it is unsupported in simulation")
            .isNotEmpty();
    }

    @Test
    void hideChoice_logs_warning_and_does_not_throw() {
        SimulatedScriptContext ctx = ctx(new SimulatedGameState());
        ctx.hideChoice("choice-1");
        assertThat(ctx.warnings())
            .as("hideChoice must log a warning as it is unsupported in simulation")
            .isNotEmpty();
    }
}
