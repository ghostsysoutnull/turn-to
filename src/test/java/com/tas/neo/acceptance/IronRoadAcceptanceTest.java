package com.tas.neo.acceptance;

import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.engine.ScenarioResult;
import com.tas.neo.engine.ScenarioRunner;
import com.tas.neo.io.OutputEvent;
import com.tas.neo.loader.JsonAdventureLoader;
import com.tas.neo.mechanics.FixedDice;
import com.tas.neo.mechanics.SequenceDice;
import com.tas.neo.scripting.LuaScriptEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance tests that run the iron road adventure with real Lua scripts and real JSON.
 *
 * <p>These tests exist to validate the full engine pipeline end-to-end: Lua script execution,
 * event logging, and section navigation on production adventure content. Unit tests in
 * {@code HookDispatcherProcessEventTest} cover the same mechanics with synthetic adventures;
 * these tests catch gaps that only appear when real Lua scripts and real JSON interact.
 *
 * <p>All paths are deterministic: dice are fully sequenced and choices are scripted.
 */
class IronRoadAcceptanceTest {

    private static Adventure IRON_ROAD;

    @BeforeAll
    static void loadAdventure() throws Exception {
        IRON_ROAD = new JsonAdventureLoader(Path.of("adventures")).load("the-iron-road");
    }

    // -----------------------------------------------------------------------
    // Tier 1 — Lua onLoad initializes state correctly
    // -----------------------------------------------------------------------

    /**
     * Verifies that the adventure-level {@code onLoad} Lua script runs at game start and sets
     * all four state variables to their expected initial values.
     *
     * <p>Path: §1 (choice 2 → §2) → §2 (choice 1 → §4 INSTANT_DEATH).
     * No Lua scripts fire on §2 or §4, so the only script executed is {@code onLoad}.
     *
     * <p>Dice: FixedDice(6) — SKILL 12, STAMINA 24, LUCK 12.
     * Input: ScriptedInput(2, 1) — two choices: §1→§2, §2→§4.
     */
    @Test
    void onLoad_script_initializes_state_variables() {
        ScenarioResult result = ScenarioRunner
                .scripted(IRON_ROAD, new FixedDice(6), 2, 1)
                .withScriptEngine(new LuaScriptEngine())
                .run();

        assertThat(result.sessionLog().errors())
                .as("no Lua errors should occur during a clean run")
                .isEmpty();

        assertThat(result.scriptState().get("contactAlive"))
                .as("onLoad sets contactAlive = true")
                .isEqualTo(Boolean.TRUE);

        assertThat(result.scriptState().get("suspicion"))
                .as("onLoad sets suspicion = 0")
                .isEqualTo(0);

        assertThat(result.scriptState().get("satchelOpened"))
                .as("onLoad sets satchelOpened = false")
                .isEqualTo(Boolean.FALSE);

        assertThat(result.scriptState().get("spymasterDebt"))
                .as("onLoad sets spymasterDebt = 0")
                .isEqualTo(0);
    }

    // -----------------------------------------------------------------------
    // Tier 2 — Item event logged from real adventure section
    // -----------------------------------------------------------------------

    /**
     * Verifies that the ITEM_GAIN event at §1 produces an {@code ItemGained} entry in the
     * session log when the engine runs against the real adventure JSON.
     *
     * <p>§1 fires ITEM_GAIN for "Sealed Satchel" unconditionally on entry, regardless of
     * which choice is made afterwards.
     *
     * <p>Same path and dice as tier 1: §1→§2→§4.
     */
    @Test
    void item_gain_event_at_section_1_is_logged() {
        ScenarioResult result = ScenarioRunner
                .scripted(IRON_ROAD, new FixedDice(6), 2, 1)
                .withScriptEngine(new LuaScriptEngine())
                .run();

        assertThat(result.sessionLog().events())
                .as("session log should contain ItemGained(Sealed Satchel) from §1 events")
                .anySatisfy(e -> {
                    assertThat(e).isInstanceOf(OutputEvent.ItemGained.class);
                    assertThat(((OutputEvent.ItemGained) e).itemName())
                            .isEqualTo("Sealed Satchel");
                });
    }

    // -----------------------------------------------------------------------
    // Tier 3 — Stat change event logged from real adventure section
    // -----------------------------------------------------------------------

    /**
     * Verifies that the STAT_CHANGE event at §3 (STAMINA -1) produces a {@code StatChanged}
     * entry in the session log. §3 is reached by choice 2 at §1 (→§2) then choice 2 at §2
     * (→§3), confirming the event fires on a non-start section.
     *
     * <p>Path: §1(2→§2) → §2(2→§3) → §3(3→§8, LUCK_TEST) → §8 provision → §16(1→§4).
     *
     * <p>Dice breakdown (SequenceDice):
     * <pre>
     *   Rolls 1–4:   player creation — SKILL=7, STAMINA=14, LUCK=7
     *   Rolls 5–6:   §8 LUCK_TEST — 2d6=12, LUCK=7, 12 > 7 → FAIL → §16
     * </pre>
     *
     * <p>Input breakdown (ScriptedInput):
     * <pre>
     *   readChoice  §1   : 2 → §2
     *   readChoice  §2   : 2 → §3  (choices: §4, §3, §5, provision)
     *   readChoice  §3   : 3 → §8  (choices: §6, §7, §8, provision)
     *   readChoice  §8   : 1 → eat provision  (§8 has no section choices after LUCK_TEST navigates)
     *   readChoice  §16  : 1 → §4  (INSTANT_DEATH)
     * </pre>
     *
     * <p>Expected StatChanged: attribute="STAMINA", delta=-1, newValue=13
     * (STAMINA starts at 14 from creation dice, §3 applies -1 → 13).
     */
    @Test
    void stat_change_event_at_section_3_is_logged() {
        // Rolls: SKILL(1d6), STAMINA(2d6), LUCK(1d6), §8 LUCK_TEST(2d6)
        SequenceDice dice = new SequenceDice(1, 1, 1, 1, 6, 6);

        ScenarioResult result = ScenarioRunner
                .scripted(IRON_ROAD, dice, 2, 2, 3, 1, 1)
                .withScriptEngine(new LuaScriptEngine())
                .run();

        assertThat(result.sessionLog().errors())
                .as("no Lua errors should occur")
                .isEmpty();

        assertThat(result.sessionLog().events())
                .as("session log should contain StatChanged(STAMINA, -1, 13) from §3 events")
                .anySatisfy(e -> {
                    assertThat(e).isInstanceOf(OutputEvent.StatChanged.class);
                    OutputEvent.StatChanged sc = (OutputEvent.StatChanged) e;
                    assertThat(sc.attribute())
                            .as("attribute should be STAMINA")
                            .isEqualTo("STAMINA");
                    assertThat(sc.delta())
                            .as("delta should be -1")
                            .isEqualTo(-1);
                    assertThat(sc.newValue())
                            .as("newValue should be 13 (14 initial - 1)")
                            .isEqualTo(13);
                });
    }
}
