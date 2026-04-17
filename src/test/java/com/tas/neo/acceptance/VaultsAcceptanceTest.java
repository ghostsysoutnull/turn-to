package com.tas.neo.acceptance;

import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.engine.ScenarioResult;
import com.tas.neo.engine.ScenarioRunner;
import com.tas.neo.io.OutputEvent;
import com.tas.neo.loader.JsonAdventureLoader;
import com.tas.neo.mechanics.FixedDice;
import com.tas.neo.scripting.LuaScriptEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance tests that run The Vaults of Stonebridge with real Lua scripts and real JSON.
 *
 * <p>All paths are deterministic: dice are fixed and choices are scripted. Tests verify
 * end-to-end behaviours that only appear when real authored content and the engine interact.
 */
class VaultsAcceptanceTest {

    private static Adventure VAULTS;

    @BeforeAll
    static void loadAdventure() throws Exception {
        VAULTS = new JsonAdventureLoader(Path.of("adventures")).load("the-vaults-of-stonebridge");
    }

    // -----------------------------------------------------------------------
    // Tier 1 — Ch1 stealth path: Dorian joins, companionSurvived set at ch2 entry
    // -----------------------------------------------------------------------

    /**
     * Walks the inn→Dorian→stealth path through ch1, navigates the vault dungeon grid,
     * enters ch2, and terminates at the locked antechamber (no Vault Key).
     *
     * <p>Verifies that §4 onEnter recruits Dorian as a party member and §20 onEnter
     * records {@code companionSurvived = true} because Dorian reached ch2 without
     * being defeated.
     *
     * <p>Path: §1→§2→§3→§4→§8→§11(LUCK pass)→§12→§13→§15→grid→§20→§17→§21→§22→§23
     *
     * <p>Dice: FixedDice(1) — SKILL 7, STAMINA 14, LUCK 7.
     * LUCK test at §11: 2d6=2, 2 &lt; LUCK 7 → pass → §12.
     *
     * <p>Input breakdown (18 inputs):
     * <pre>
     *   §1(1→§2), §2(1→§3), §3(1→§4), §4(1→§8), §8(2→§11 stealth)
     *   §11: LUCK_TEST auto-navigates to §12; event guard returns immediately (no phantom re-render)
     *   §12(1→§13), §13(2→§15 bypass guardhouse), §15(1→grid entrance)
     *   grid: (0,0)south=2→(0,1), south=3→(0,2), east=2→(1,2), east=2→(2,2), east=3→(3,2), south=3→§20
     *   §20(1→§17), §17(1→§21 south), §21(1→§22), §22(2→§23 no Vault Key)
     * </pre>
     *
     * <p>Grid path avoids (3,0) combat and (2,1) Vault Key: bottom row (0,2)→(1,2)→(2,2)→(3,2).
     */
    @Test
    void dorian_survives_to_ch2_and_companionSurvived_is_true() {
        ScenarioResult result = ScenarioRunner
                .scripted(VAULTS, new FixedDice(1), 1, 1, 1, 1, 2, 1, 2, 1, 2, 3, 2, 2, 3, 3, 1, 1, 1, 2)
                .withScriptEngine(new LuaScriptEngine())
                .run();

        assertThat(result.sessionLog().errors())
                .as("no Lua errors should occur on the stealth path through ch1")
                .isEmpty();

        assertThat(result.finalState().isGameOver())
                .as("game must end at §23 INSTANT_DEATH — no Vault Key present")
                .isTrue();

        assertThat(result.scriptState().get("companionSurvived"))
                .as("§20 onEnter must set companionSurvived=true — Dorian joined at §4 and " +
                    "was not defeated before reaching ch2")
                .isEqualTo(Boolean.TRUE);
    }

    // -----------------------------------------------------------------------
    // Tier 2 — Ch1 combat path: Guard's Pass obtained, §18 condition satisfied
    // -----------------------------------------------------------------------

    /**
     * Walks the inn→Dorian→fight path through ch1: defeats the Gate Guard at §10,
     * gains the Guard's Pass, navigates the vault dungeon grid, then uses the Guard's
     * Pass at §18 to bypass the vault checkpoint without confrontation.
     *
     * <p>Verifies that the COMBAT event at §10 fires, the post-combat ITEM_GAIN grants
     * Guard's Pass, and that choice 1 at §18 (show Guard's Pass) leads to §22→§23.
     *
     * <p>Path: §1→§2→§3→§4→§8→§10(COMBAT win)→§13→§14→§15→grid→§20→§17→§19→§18→§22→§23
     *
     * <p>Dice: FixedDice(6) — SKILL 12, STAMINA 24, LUCK 12.
     * Gate Guard SKILL 7, STAMINA 8: player AS=24 beats guard AS=19 every round.
     * Guard dies in 4 rounds; player takes no damage.
     *
     * <p>Input breakdown (24 inputs):
     * <pre>
     *   §1(1→§2), §2(1→§3), §3(1→§4), §4(1→§8), §8(1→§10 fight)
     *   §10 COMBAT: Gate Guard STAMINA=8, player wins 4 rounds @ 2 damage each.
     *       Each player-wins round offers a luck test (readYesNo); 0=decline all 4.
     *   §10 post-combat: 1→§13; §13(1→§14 search guardhouse), §14(1→§15), §15(1→grid entrance)
     *   grid: (0,0)south=2→(0,1), south=3→(0,2), east=2→(1,2), east=2→(2,2), east=3→(3,2), south=3→§20
     *   §20(1→§17), §17(2→§19 eastern passage), §19(1→§18 continue south)
     *   §18(1→§22 show Guard's Pass), §22(2→§23 no Vault Key)
     * </pre>
     *
     * <p>Grid path avoids (3,0) combat and (2,1) Vault Key: bottom row (0,2)→(1,2)→(2,2)→(3,2).
     */
    @Test
    void guard_pass_obtained_satisfies_vault_checkpoint_condition() {
        ScenarioResult result = ScenarioRunner
                .scripted(VAULTS, new FixedDice(6), 1, 1, 1, 1, 1, 0, 0, 0, 0, 1, 1, 1, 1, 2, 3, 2, 2, 3, 3, 1, 2, 1, 1, 2)
                .withScriptEngine(new LuaScriptEngine())
                .run();

        assertThat(result.sessionLog().errors())
                .as("no Lua errors should occur on the combat path through ch1")
                .isEmpty();

        assertThat(result.sessionLog().events())
                .as("session log must contain ItemGained(Guard's Pass) from §10 post-combat event")
                .anySatisfy(e -> {
                    assertThat(e).isInstanceOf(OutputEvent.ItemGained.class);
                    assertThat(((OutputEvent.ItemGained) e).itemName())
                            .isEqualTo("Guard's Pass");
                });

        assertThat(result.finalState().isGameOver())
                .as("game must end at §23 INSTANT_DEATH — no Vault Key despite having Guard's Pass")
                .isTrue();
    }
}
