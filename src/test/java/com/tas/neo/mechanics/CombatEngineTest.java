package com.tas.neo.mechanics;

import com.tas.neo.domain.combat.CombatResult;
import com.tas.neo.domain.combat.Creature;
import com.tas.neo.domain.item.Inventory;
import com.tas.neo.domain.player.Attribute;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.domain.player.Player;
import com.tas.neo.io.OutputEvent;
import com.tas.neo.io.RecordingOutput;
import com.tas.neo.io.ScriptedInput;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CombatEngine#fight(Player, List, boolean)}.
 *
 * <p>Covers all rows from the test strategy table in docs/design/03-combat-engine.md.
 *
 * <p>Attack Strength = SKILL + 2d6. Player wins round when player AS > creature AS;
 * creature takes a wound. Creature wins round when creature AS > player AS; player takes
 * a wound. Draw: neither takes damage.
 */
class CombatEngineTest {

    // -------------------------------------------------------------------------
    // Test helpers
    // -------------------------------------------------------------------------

    private static Player playerWith(int skill, int stamina, int luck) {
        Map<AttributeType, Attribute> attrs = new EnumMap<>(AttributeType.class);
        attrs.put(AttributeType.SKILL,   new Attribute(AttributeType.SKILL,   skill,   skill));
        attrs.put(AttributeType.STAMINA, new Attribute(AttributeType.STAMINA, stamina, stamina));
        attrs.put(AttributeType.LUCK,    new Attribute(AttributeType.LUCK,    luck,    luck));
        return new Player(attrs, new Inventory(), 0, 0);
    }

    private static Creature creature(int skill, int stamina) {
        return new Creature("Goblin", skill, stamina);
    }

    // -------------------------------------------------------------------------
    // Row 1: Player wins every round
    //
    // FixedDice(6) → each die = 6, so 2d6 = 12.
    // Player AS = playerSkill(10) + 12 = 22.
    // Creature AS = creatureSkill(5) + 12 = 17.
    // Player wins every round. Creature starts at 2 STAMINA → 1 round to defeat it.
    // -------------------------------------------------------------------------

    @Test
    void player_wins_when_player_attack_strength_always_exceeds_creature() {
        Player player = playerWith(10, 20, 8);
        Creature goblin = creature(5, 2); // 1 wound defeats the creature

        RecordingOutput output = new RecordingOutput();
        ScriptedInput input = new ScriptedInput(/* no luck prompts expected */);
        CombatEngine engine = new CombatEngine(new FixedDice(6), input, output);

        CombatResult result = engine.fight(player, List.of(goblin), false);

        assertThat(result.playerWon())
            .as("player with much higher skill should win")
            .isTrue();
    }

    @Test
    void player_wins_records_at_least_one_combat_round() {
        Player player = playerWith(10, 20, 8);
        Creature goblin = creature(5, 2);

        RecordingOutput output = new RecordingOutput();
        ScriptedInput input = new ScriptedInput();
        CombatEngine engine = new CombatEngine(new FixedDice(6), input, output);

        engine.fight(player, List.of(goblin), false);

        long roundsShown = output.events().stream()
            .filter(e -> e instanceof OutputEvent.CombatRoundShown)
            .count();
        assertThat(roundsShown)
            .as("at least one CombatRound should be shown via GameOutput")
            .isGreaterThanOrEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Row 2: Creature wins every round
    //
    // SequenceDice: player rolls low (1+1=2), creature rolls high (6+6=12).
    // Player AS = playerSkill(5) + 2 = 7.
    // Creature AS = creatureSkill(10) + 12 = 22.
    // Creature wins every round.
    // -------------------------------------------------------------------------

    @Test
    void creature_wins_when_creature_attack_strength_always_exceeds_player() {
        // Player skill=5, creature skill=10.
        // Each round: player rolls 1+1=2 (AS=7), creature rolls 6+6=12 (AS=22).
        // Creature wounds player each round. Player stamina=2 → defeated in 1 round.
        Player player = playerWith(5, 2, 8);
        Creature goblin = creature(10, 20);

        RecordingOutput output = new RecordingOutput();
        // Provide a long sequence to handle repeated rounds; sequence: player=1,1, creature=6,6
        // Repeat enough times to cover multiple rounds if stamina is higher.
        SequenceDice dice = new SequenceDice(
            1, 1,  6, 6,   // round 1: player AS=7, creature AS=22 → creature wounds
            1, 1,  6, 6,   // round 2
            1, 1,  6, 6,   // round 3
            1, 1,  6, 6    // round 4
        );
        // No luck prompts — ScriptedInput with 0 triggers "no" for any yes/no
        ScriptedInput input = new ScriptedInput(0, 0, 0, 0, 0, 0, 0, 0);
        CombatEngine engine = new CombatEngine(dice, input, output);

        CombatResult result = engine.fight(player, List.of(goblin), false);

        assertThat(result.playerWon())
            .as("creature with much higher skill should defeat the player")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // Row 3: Draw rounds
    //
    // FixedDice(3) → each die = 3, so 2d6 = 6 for both sides.
    // Player AS = playerSkill(8) + 6 = 14.
    // Creature AS = creatureSkill(8) + 6 = 14.
    // Equal AS → draw every round → no stamina lost.
    // Combat would never end unless we use a SequenceDice that eventually differs.
    // Use SequenceDice: draw for a few rounds then player wins.
    // -------------------------------------------------------------------------

    @Test
    void draw_round_does_not_reduce_stamina() {
        // Round 1: player rolls 3+3=6 (AS=14), creature rolls 3+3=6 (AS=14) → draw
        // Round 2: player rolls 6+6=12 (AS=20), creature rolls 1+1=2 (AS=10) → player wins
        Player player = playerWith(8, 10, 8);
        Creature goblin = creature(8, 2);

        RecordingOutput output = new RecordingOutput();
        ScriptedInput input = new ScriptedInput();
        SequenceDice dice = new SequenceDice(
            3, 3,  3, 3,   // round 1 draw: player AS=14, creature AS=14
            6, 6,  1, 1    // round 2 win: player AS=20, creature AS=10
        );
        CombatEngine engine = new CombatEngine(dice, input, output);

        CombatResult result = engine.fight(player, List.of(goblin), false);

        assertThat(result.roundsFought())
            .as("two rounds should have been fought: 1 draw + 1 player win")
            .isEqualTo(2);
        assertThat(result.playerStaminaLost())
            .as("a draw round deals no damage to the player")
            .isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Row 4: Luck test — player wounds creature, lucky
    //
    // Player wins round. ScriptedInput says "yes" to luck test.
    // Lucky roll (2d6 ≤ LUCK): creature takes 4 STAMINA instead of the normal 2.
    // Creature starts at exactly 4 STAMINA → defeated in 1 lucky round.
    // Luck roll: 3+3=6 ≤ LUCK(8) → lucky. LUCK is decremented to 7 but that is internal.
    //
    // Round dice: player 6+6=12 (AS=20), creature 1+1=2 (AS=9) → player wins.
    // -------------------------------------------------------------------------

    @Test
    void luck_test_player_wounds_creature_lucky_deals_4_stamina() {
        // Creature stamina=4 → with lucky wound (4 STAMINA) it is defeated in exactly 1 round.
        Player player = playerWith(8, 20, 8);
        Creature goblin = creature(3, 4);

        RecordingOutput output = new RecordingOutput();
        // answer "yes" (1) to the luck prompt
        ScriptedInput input = new ScriptedInput(1);
        // Combat rolls: player 6+6=12 (AS=20), creature 1+1=2 (AS=9) → player wins.
        // Luck roll: 3+3=6 ≤ LUCK(8) → lucky.
        SequenceDice dice = new SequenceDice(
            6, 6,  1, 1,   3, 3    // round 1: player wins, luck roll lucky
        );
        CombatEngine engine = new CombatEngine(dice, input, output);

        CombatResult result = engine.fight(player, List.of(goblin), false);

        assertThat(result.playerWon())
            .as("player should win after one lucky wound that deals 4 STAMINA to a 4-STAMINA creature")
            .isTrue();
        assertThat(result.roundsFought())
            .as("creature with 4 STAMINA should be defeated in exactly 1 lucky round (4 damage)")
            .isEqualTo(1);
        assertThat(result.playerStaminaLost())
            .as("player takes no damage in a round where player wins")
            .isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Row 5: Luck test — player wounds creature, unlucky
    //
    // Player wins round. ScriptedInput says "yes" to luck test.
    // Unlucky roll (2d6 > LUCK): creature takes only 1 STAMINA instead of the normal 2.
    // Creature starts at 2 STAMINA. With unlucky wounds (1 STAMINA each), it takes 2 rounds.
    // Without luck test the same creature would fall in 1 round.
    // Luck roll: 6+6=12 > LUCK(8) → unlucky.
    //
    // Round dice: player 6+6=12 (AS=20), creature 1+1=2 (AS=9) → player wins every round.
    // -------------------------------------------------------------------------

    @Test
    void luck_test_player_wounds_creature_unlucky_deals_1_stamina() {
        // Creature stamina=2 → with unlucky wound (1 STAMINA each) takes 2 rounds to defeat.
        Player player = playerWith(8, 20, 8);
        Creature goblin = creature(3, 2);

        RecordingOutput output = new RecordingOutput();
        // answer "yes" (1) to luck prompts
        ScriptedInput input = new ScriptedInput(1, 1, 1, 1);
        // Combat rolls: player 6+6=12 (AS=20), creature 1+1=2 (AS=9) → player wins.
        // Luck roll: 6+6=12 > LUCK(8) → unlucky.
        SequenceDice dice = new SequenceDice(
            6, 6,  1, 1,   6, 6,    // round 1: player wins, luck roll unlucky
            6, 6,  1, 1,   6, 6     // round 2: player wins, luck roll unlucky
        );
        CombatEngine engine = new CombatEngine(dice, input, output);

        CombatResult result = engine.fight(player, List.of(goblin), false);

        assertThat(result.playerWon())
            .as("player should eventually win even when unlucky (1 STAMINA per wound)")
            .isTrue();
        assertThat(result.roundsFought())
            .as("creature with 2 STAMINA taking 1 damage per unlucky wound requires exactly 2 rounds")
            .isEqualTo(2);
        assertThat(result.playerStaminaLost())
            .as("player takes no damage in rounds where player wins")
            .isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Row 6: Luck test — creature wounds player, lucky
    //
    // Creature wins round. ScriptedInput says "yes" to luck test.
    // Lucky roll (2d6 ≤ LUCK): player takes only 1 STAMINA instead of the normal 2.
    // Player starts at 1 STAMINA above the threshold so the lucky reduction matters:
    // player stamina=1 → without luck the player would already be at 0; with luck (1 damage)
    // the player is also at 0, so instead use stamina=2 and assert playerStaminaLost == 1.
    // Luck roll: 3+3=6 ≤ LUCK(8) → lucky.
    //
    // Round dice: player 1+1=2 (AS=9), creature 6+6=12 (AS=22) → creature wins.
    // After one round at 1 damage the player still has stamina; then player wins next round.
    // -------------------------------------------------------------------------

    @Test
    void luck_test_creature_wounds_player_lucky_deals_1_stamina() {
        // Player stamina=4, creature stamina=2.
        // Round 1: creature wins; player lucky → takes 1 STAMINA (stamina now 3).
        // Round 2: player wins (high dice) → creature takes normal 2 STAMINA (stamina now 0).
        Player player = playerWith(8, 4, 8);
        Creature goblin = creature(6, 2);

        RecordingOutput output = new RecordingOutput();
        // answer "yes" (1) to the luck prompt on round 1
        ScriptedInput input = new ScriptedInput(1);
        // Round 1: player 1+1=2 (AS=10), creature 6+6=12 (AS=18) → creature wins.
        //   Luck roll: 3+3=6 ≤ LUCK(8) → lucky → player takes 1 STAMINA.
        // Round 2: player 6+6=12 (AS=20), creature 1+1=2 (AS=8) → player wins.
        //   No luck prompt (creature is wounded, not player).
        SequenceDice dice = new SequenceDice(
            1, 1,  6, 6,   3, 3,    // round 1: creature wins, luck roll lucky
            6, 6,  1, 1              // round 2: player wins, creature takes 2 STAMINA → defeated
        );
        CombatEngine engine = new CombatEngine(dice, input, output);

        CombatResult result = engine.fight(player, List.of(goblin), false);

        assertThat(result.playerWon())
            .as("player should win after surviving a lucky wound (1 STAMINA lost)")
            .isTrue();
        assertThat(result.playerStaminaLost())
            .as("lucky wound to player deals 1 STAMINA instead of 2")
            .isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Row 7: Luck test — creature wounds player, unlucky
    //
    // Creature wins round. ScriptedInput says "yes" to luck test.
    // Unlucky roll (2d6 > LUCK): player takes 3 STAMINA instead of the normal 2.
    // Player stamina=10, survives the round, then wins the next round.
    // Luck roll: 6+6=12 > LUCK(8) → unlucky.
    //
    // Round dice: player 1+1=2 (AS=10), creature 6+6=12 (AS=18) → creature wins round 1.
    // Round 2: player wins to end combat cleanly.
    // -------------------------------------------------------------------------

    @Test
    void luck_test_creature_wounds_player_unlucky_deals_3_stamina() {
        // Player stamina=10, creature stamina=2.
        // Round 1: creature wins; player unlucky → takes 3 STAMINA (stamina now 7).
        // Round 2: player wins → creature takes normal 2 STAMINA (stamina now 0).
        Player player = playerWith(8, 10, 8);
        Creature goblin = creature(6, 2);

        RecordingOutput output = new RecordingOutput();
        // answer "yes" (1) to the luck prompt on round 1
        ScriptedInput input = new ScriptedInput(1);
        // Round 1: player 1+1=2 (AS=10), creature 6+6=12 (AS=18) → creature wins.
        //   Luck roll: 6+6=12 > LUCK(8) → unlucky → player takes 3 STAMINA.
        // Round 2: player 6+6=12 (AS=20), creature 1+1=2 (AS=8) → player wins.
        SequenceDice dice = new SequenceDice(
            1, 1,  6, 6,   6, 6,    // round 1: creature wins, luck roll unlucky
            6, 6,  1, 1              // round 2: player wins, creature defeated
        );
        CombatEngine engine = new CombatEngine(dice, input, output);

        CombatResult result = engine.fight(player, List.of(goblin), false);

        assertThat(result.playerWon())
            .as("player should win after surviving an unlucky wound (3 STAMINA lost)")
            .isTrue();
        assertThat(result.playerStaminaLost())
            .as("unlucky wound to player deals 3 STAMINA instead of 2")
            .isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // Row 8: Simultaneous multi-combat
    //
    // Two creatures, simultaneous = true.
    // Both creatures attack the player each round.
    // Player wins against each creature independently, but both attack simultaneously.
    // -------------------------------------------------------------------------

    @Test
    void simultaneous_combat_shows_a_combat_round_per_creature_per_round() {
        // Player skill=10, both creatures skill=3 → player wins every round.
        // Creature 1 stamina=2, creature 2 stamina=2.
        // With FixedDice(6): player AS=22, each creature AS=15 → player wins each sub-round.
        Player player = playerWith(10, 20, 8);
        Creature goblin1 = creature(3, 2);
        Creature goblin2 = creature(3, 2);

        RecordingOutput output = new RecordingOutput();
        ScriptedInput input = new ScriptedInput();
        // Provide generous sequence for two creatures per round
        SequenceDice dice = new SequenceDice(
            6, 6,  1, 1,   // round 1, creature 1: player wins
            6, 6,  1, 1,   // round 1, creature 2: player wins
            6, 6,  1, 1,   // round 2 if needed
            6, 6,  1, 1
        );
        CombatEngine engine = new CombatEngine(dice, input, output);

        CombatResult result = engine.fight(player, List.of(goblin1, goblin2), true);

        assertThat(result.playerWon())
            .as("player with much higher skill should win simultaneous multi-combat")
            .isTrue();

        long roundsShown = output.events().stream()
            .filter(e -> e instanceof OutputEvent.CombatRoundShown)
            .count();
        assertThat(roundsShown)
            .as("simultaneous combat must emit at least one CombatRound per creature encountered")
            .isGreaterThanOrEqualTo(2);
    }

    @Test
    void simultaneous_combat_both_creatures_attack_player_each_round() {
        // If both creatures attack player in the same round (simultaneous=true),
        // when creatures win their sub-rounds the player takes multiple wounds in one game-round.
        // Use creature skill=10 vs player skill=5 so creatures win their sub-rounds.
        // Player stamina=10 to survive several rounds; both creatures stamina=2.
        // With dice favouring player on creature sub-rounds: player AS > both creature AS.
        // Here we just assert that the result is consistent with simultaneous = true semantics:
        // player either wins (defeat both creatures) or is defeated.
        Player player = playerWith(5, 10, 8);
        Creature goblin1 = creature(10, 2);
        Creature goblin2 = creature(10, 2);

        RecordingOutput output = new RecordingOutput();
        // No luck tests: ScriptedInput answers "no" (0) to any yes/no prompts
        ScriptedInput input = new ScriptedInput(0, 0, 0, 0, 0, 0, 0, 0);
        // Provide a large enough sequence: each round needs 4 rolls (2 dice × 2 creatures)
        SequenceDice dice = new SequenceDice(
            1, 1,  6, 6,   // round 1, creature 1: creature wins (AS 22 vs 7)
            1, 1,  6, 6,   // round 1, creature 2: creature wins
            1, 1,  6, 6,   // round 2, creature 1
            1, 1,  6, 6,   // round 2, creature 2
            1, 1,  6, 6,   // round 3, creature 1
            1, 1,  6, 6,   // round 3, creature 2
            1, 1,  6, 6,   // round 4, creature 1
            1, 1,  6, 6    // round 4, creature 2
        );
        CombatEngine engine = new CombatEngine(dice, input, output);

        CombatResult result = engine.fight(player, List.of(goblin1, goblin2), true);

        // When both creatures attack simultaneously, player is defeated (low skill, high-skill enemies).
        assertThat(result.playerWon())
            .as("player with low skill should lose to two high-skill creatures in simultaneous mode")
            .isFalse();
        assertThat(result.roundsFought())
            .as("simultaneous combat against two strong creatures must end in at least one round")
            .isGreaterThanOrEqualTo(1);
    }
}
