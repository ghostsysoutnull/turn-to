package com.tas.neo.combat.personal;

import com.tas.neo.combat.CombatSystem;
import com.tas.neo.combat.CombatSystemRegistry;
import com.tas.neo.combat.DefaultCombatSystemRegistry;
import com.tas.neo.domain.combat.CombatOutcome;
import com.tas.neo.domain.combat.CombatOutcomeType;
import com.tas.neo.domain.combat.Creature;
import com.tas.neo.domain.item.Inventory;
import com.tas.neo.domain.party.PartyMember;
import com.tas.neo.domain.player.Attribute;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.domain.player.Player;
import com.tas.neo.engine.HookDispatcher;
import com.tas.neo.io.RecordingOutput;
import com.tas.neo.io.ScriptedInput;
import com.tas.neo.mechanics.CombatEngine;
import com.tas.neo.mechanics.FixedDice;
import com.tas.neo.mechanics.SequenceDice;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PersonalCombatSystem}.
 *
 * <p>Covers the test strategy rows from docs/design/10-combat-systems.md:
 * <ul>
 *   <li>PersonalCombatSystem VICTORY outcome via FixedDice favouring player</li>
 *   <li>PersonalCombatSystem DEFEAT outcome via FixedDice favouring creature</li>
 *   <li>Applies playerStaminaLost to player after engine.fight() returns</li>
 *   <li>id() returns "personal"</li>
 * </ul>
 *
 * <p>Attack Strength = SKILL + 2d6. {@link FixedDice}(6) means each die = 6, so 2d6 = 12.
 * Player SKILL 10 → AS 22; creature SKILL 5 → AS 17. Player wins every round (VICTORY).
 * {@link SequenceDice} with low player rolls and high creature rolls forces creature wins (DEFEAT).
 */
class PersonalCombatSystemTest {

    private RecordingOutput output;
    private ScriptedInput input;

    @BeforeEach
    void setUp() {
        output = new RecordingOutput();
        input = new ScriptedInput(/* no luck prompts offered by default */);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Player playerWith(int skill, int stamina) {
        Map<AttributeType, Attribute> attrs = new EnumMap<>(AttributeType.class);
        attrs.put(AttributeType.SKILL,   new Attribute(AttributeType.SKILL,   skill,   skill));
        attrs.put(AttributeType.STAMINA, new Attribute(AttributeType.STAMINA, stamina, stamina));
        attrs.put(AttributeType.LUCK,    new Attribute(AttributeType.LUCK,    8,       8));
        return new Player(attrs, new Inventory(), 0, 0);
    }

    private static PersonalCombatSystem systemWith(FixedDice dice, ScriptedInput input,
                                                    RecordingOutput output) {
        CombatEngine engine = new CombatEngine(dice, input, output);
        return new PersonalCombatSystem(engine);
    }

    private static PersonalCombatSystem systemWith(SequenceDice dice, ScriptedInput input,
                                                    RecordingOutput output) {
        CombatEngine engine = new CombatEngine(dice, input, output);
        return new PersonalCombatSystem(engine);
    }

    private static CombatSystemRegistry emptyRegistry() {
        return new DefaultCombatSystemRegistry(/* no systems needed for personal-only tests */);
    }

    // -------------------------------------------------------------------------
    // id()
    // -------------------------------------------------------------------------

    @Test
    void id_returns_personal() {
        PersonalCombatSystem system = systemWith(new FixedDice(1), input, output);

        assertThat(system.id())
            .as("PersonalCombatSystem.id() must return \"personal\"")
            .isEqualTo("personal");
    }

    // -------------------------------------------------------------------------
    // Row: VICTORY outcome — FixedDice favouring player
    //
    // FixedDice(6) → 2d6 = 12.
    // Player SKILL 10 → AS 22; creature SKILL 5 → AS 17.
    // Player wins every round. Creature stamina 2 → defeated in 1 round.
    // -------------------------------------------------------------------------

    @Test
    void run_returns_victory_when_dice_strongly_favour_player() {
        Player player = playerWith(10, 20);
        Creature goblin = new Creature("Goblin", 5, 2);

        PersonalCombatSystem system = systemWith(new FixedDice(6), input, output);

        CombatOutcome outcome = system.run(
            player, List.of(), List.of(goblin), Map.of(),
            emptyRegistry(), null, input, output, new FixedDice(6));

        assertThat(outcome.type())
            .as("player with much higher skill should produce VICTORY outcome")
            .isEqualTo(CombatOutcomeType.VICTORY);
    }

    @Test
    void victory_outcome_navigateTo_is_empty() {
        Player player = playerWith(10, 20);
        Creature goblin = new Creature("Goblin", 5, 2);
        PersonalCombatSystem system = systemWith(new FixedDice(6), input, output);

        CombatOutcome outcome = system.run(
            player, List.of(), List.of(goblin), Map.of(),
            emptyRegistry(), null, input, output, new FixedDice(6));

        assertThat(outcome.navigateTo())
            .as("VICTORY outcome from personal combat has no forced navigation target")
            .isEmpty();
    }

    // -------------------------------------------------------------------------
    // Row: DEFEAT outcome — FixedDice favouring creature
    //
    // SequenceDice: player rolls 1+1=2, creature rolls 6+6=12.
    // Player SKILL 5 → AS 7; creature SKILL 10 → AS 22.
    // Creature wounds player every round. Player stamina 2 → defeated in 1 round.
    // -------------------------------------------------------------------------

    @Test
    void run_returns_defeat_when_dice_strongly_favour_creature() {
        Player player = playerWith(5, 2);
        Creature goblin = new Creature("Troll", 10, 20);

        // Round 1: player rolls 1+1=2 (AS=7), creature rolls 6+6=12 (AS=22) → creature wins.
        // Player stamina=2 → takes normal 2 STAMINA → reduced to 0 → defeated.
        SequenceDice dice = new SequenceDice(
            1, 1,  6, 6,   // round 1: creature wins
            1, 1,  6, 6,   // guard rounds in case of clamping differences
            1, 1,  6, 6
        );
        ScriptedInput noLuck = new ScriptedInput(0, 0, 0, 0, 0, 0);
        PersonalCombatSystem system = systemWith(dice, noLuck, output);

        CombatOutcome outcome = system.run(
            player, List.of(), List.of(goblin), Map.of(),
            emptyRegistry(), null, noLuck, output, dice);

        assertThat(outcome.type())
            .as("creature with much higher skill should produce DEFEAT outcome")
            .isEqualTo(CombatOutcomeType.DEFEAT);
    }

    // -------------------------------------------------------------------------
    // Row: applies playerStaminaLost to player after engine.fight() returns
    //
    // CombatEngine.fight() returns CombatResult.playerStaminaLost().
    // PersonalCombatSystem.run() must apply that to player via
    // player.modifyAttribute(STAMINA, -staminaLost) before returning.
    //
    // Setup: player SKILL 5, creature SKILL 10 → creature wins all rounds.
    // Player stamina=10. Creature deals normal 2 STAMINA per round.
    // After 1 creature-win round: player stamina should be 8.
    // -------------------------------------------------------------------------

    @Test
    void run_applies_stamina_lost_to_player_before_returning() {
        Player player = playerWith(5, 10);
        Creature goblin = new Creature("Troll", 10, 10); // stamina=10 so 5 player wins (5×2=10) exhaust it

        // Round 1: creature wins → player loses 2 STAMINA.
        // Round 2 onwards: player eventually wins to end the fight (high player rolls).
        SequenceDice dice = new SequenceDice(
            1, 1,  6, 6,   // round 1: creature wins, player -2 STAMINA
            6, 6,  1, 1,   // round 2: player wins, creature -2 STAMINA
            6, 6,  1, 1,   // round 3 if needed
            6, 6,  1, 1,
            6, 6,  1, 1,
            6, 6,  1, 1
        );
        ScriptedInput noLuck = new ScriptedInput(0, 0, 0, 0, 0, 0, 0, 0);
        PersonalCombatSystem system = systemWith(dice, noLuck, output);

        system.run(
            player, List.of(), List.of(goblin), Map.of(),
            emptyRegistry(), null, noLuck, output, dice);

        assertThat(player.getStamina())
            .as("PersonalCombatSystem must apply stamina lost during combat to the player")
            .isLessThan(10);
    }

    @Test
    void run_reduces_player_stamina_by_amount_from_combat_result() {
        // With no luck tests and creature winning one round dealing normal 2 STAMINA,
        // then player winning subsequent rounds to end combat, total loss is exactly 2.
        Player player = playerWith(5, 10);
        Creature goblin = new Creature("Troll", 10, 2);

        // Round 1: creature wins → player loses 2 STAMINA.
        // Round 2: player wins → creature takes 2 STAMINA (stamina 0 → defeated).
        SequenceDice dice = new SequenceDice(
            1, 1,  6, 6,   // round 1: creature wins
            6, 6,  1, 1    // round 2: player wins, goblin defeated
        );
        ScriptedInput noLuck = new ScriptedInput(0, 0);
        PersonalCombatSystem system = systemWith(dice, noLuck, output);

        system.run(
            player, List.of(), List.of(goblin), Map.of(),
            emptyRegistry(), null, noLuck, output, dice);

        assertThat(player.getStamina())
            .as("player stamina should be reduced by 2 (normal wound, no luck test) after losing one round")
            .isEqualTo(8);
    }

    // -------------------------------------------------------------------------
    // participants are ignored — personal combat only involves the player
    // -------------------------------------------------------------------------

    @Test
    void run_completes_without_error_when_participants_list_is_non_empty() {
        // PersonalCombatSystem ignores participants — this must not cause an error.
        Player player = playerWith(10, 20);
        Creature goblin = new Creature("Goblin", 5, 2);

        // Build a minimal party member to pass as participant
        com.tas.neo.domain.party.PartyMemberStat hpStat = new com.tas.neo.domain.party.PartyMemberStat("HP", 10, 10);
        PartyMember member = new com.tas.neo.domain.party.PartyMember(
            "companion", "Companion", "HP",
            Map.of("HP", hpStat),
            new com.tas.neo.domain.party.RemoveConsequence("Gone."),
            com.tas.neo.domain.party.MemberState.ACTIVE);

        PersonalCombatSystem system = systemWith(new FixedDice(6), input, output);

        CombatOutcome outcome = system.run(
            player, List.of(member), List.of(goblin), Map.of(),
            emptyRegistry(), null, input, output, new FixedDice(6));

        assertThat(outcome.type())
            .as("PersonalCombatSystem must ignore participants and still produce a valid outcome")
            .isEqualTo(CombatOutcomeType.VICTORY);
    }

    // -------------------------------------------------------------------------
    // CombatSystem interface contract: PersonalCombatSystem implements CombatSystem
    // -------------------------------------------------------------------------

    @Test
    void personal_combat_system_implements_combat_system_interface() {
        CombatEngine engine = new CombatEngine(new FixedDice(1), input, output);
        CombatSystem system = new PersonalCombatSystem(engine);

        assertThat(system)
            .as("PersonalCombatSystem must implement CombatSystem")
            .isInstanceOf(CombatSystem.class);
        assertThat(system.id())
            .as("CombatSystem.id() via PersonalCombatSystem must return \"personal\"")
            .isEqualTo("personal");
    }
}
