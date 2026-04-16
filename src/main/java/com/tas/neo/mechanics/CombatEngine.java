package com.tas.neo.mechanics;

import com.tas.neo.domain.Dice;
import com.tas.neo.domain.combat.CombatResult;
import com.tas.neo.domain.combat.CombatRound;
import com.tas.neo.domain.combat.CombatRoundOutcome;
import com.tas.neo.domain.combat.Creature;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.domain.player.Player;
import com.tas.neo.io.GameInput;
import com.tas.neo.io.GameOutput;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates personal combat rounds and returns a result. Does not modify
 * the player's attributes — the caller is responsible for applying the result.
 */
public class CombatEngine {

    private static final int NORMAL_WOUND = 2;
    private static final int LUCKY_PLAYER_WOUNDS_CREATURE = 4;
    private static final int UNLUCKY_PLAYER_WOUNDS_CREATURE = 1;
    private static final int LUCKY_CREATURE_WOUNDS_PLAYER = 1;
    private static final int UNLUCKY_CREATURE_WOUNDS_PLAYER = 3;

    private final Dice dice;
    private final GameInput input;
    private final GameOutput output;

    public CombatEngine(Dice dice, GameInput input, GameOutput output) {
        this.dice = dice;
        this.input = input;
        this.output = output;
    }

    public CombatResult fight(Player player, List<Creature> creatures, boolean simultaneous) {
        if (simultaneous) {
            return fightSimultaneous(player, creatures);
        }
        return fightSequential(player, creatures);
    }

    private CombatResult fightSequential(Player player, List<Creature> creatures) {
        int roundsFought = 0;
        int playerStaminaLost = 0;
        int playerInitialStamina = player.getStamina();

        for (Creature initial : creatures) {
            Creature creature = initial;
            while (creature.isAlive() && playerStaminaLost < playerInitialStamina) {
                roundsFought++;
                int playerRoll = dice.roll2d6();
                int creatureRoll = dice.roll2d6();
                int playerAS = player.getSkill() + playerRoll;
                int creatureAS = creature.skill() + creatureRoll;
                CombatRoundOutcome outcome = determineOutcome(playerAS, creatureAS);

                output.showCombatRound(new CombatRound(
                    roundsFought, playerAS, creatureAS, playerRoll, creatureRoll, outcome
                ));

                if (outcome == CombatRoundOutcome.PLAYER_WOUNDS) {
                    int damage = resolvePlayerWoundsCreature(player);
                    creature = creature.wound(damage);
                } else if (outcome == CombatRoundOutcome.CREATURE_WOUNDS) {
                    int damage = resolveCreatureWoundsPlayer(player);
                    playerStaminaLost += damage;
                }
            }
            if (playerStaminaLost >= playerInitialStamina) {
                return new CombatResult(false, roundsFought, playerStaminaLost);
            }
        }

        return new CombatResult(true, roundsFought, playerStaminaLost);
    }

    private CombatResult fightSimultaneous(Player player, List<Creature> creatures) {
        List<Creature> alive = new ArrayList<>(creatures);
        int roundsFought = 0;
        int playerStaminaLost = 0;
        int playerInitialStamina = player.getStamina();

        while (!alive.isEmpty() && playerStaminaLost < playerInitialStamina) {
            roundsFought++;
            List<Creature> nextAlive = new ArrayList<>();
            for (int i = 0; i < alive.size(); i++) {
                Creature creature = alive.get(i);
                int playerRoll = dice.roll2d6();
                int creatureRoll = dice.roll2d6();
                int playerAS = player.getSkill() + playerRoll;
                int creatureAS = creature.skill() + creatureRoll;
                CombatRoundOutcome outcome = determineOutcome(playerAS, creatureAS);

                output.showCombatRound(new CombatRound(
                    roundsFought, playerAS, creatureAS, playerRoll, creatureRoll, outcome
                ));

                if (outcome == CombatRoundOutcome.PLAYER_WOUNDS) {
                    int damage = resolvePlayerWoundsCreature(player);
                    creature = creature.wound(damage);
                } else if (outcome == CombatRoundOutcome.CREATURE_WOUNDS) {
                    int damage = resolveCreatureWoundsPlayer(player);
                    playerStaminaLost += damage;
                }

                if (creature.isAlive()) {
                    nextAlive.add(creature);
                }

                if (playerStaminaLost >= playerInitialStamina) {
                    return new CombatResult(false, roundsFought, playerStaminaLost);
                }
            }
            alive = nextAlive;
        }

        return new CombatResult(playerStaminaLost < playerInitialStamina, roundsFought, playerStaminaLost);
    }

    private CombatRoundOutcome determineOutcome(int playerAS, int creatureAS) {
        if (playerAS > creatureAS) return CombatRoundOutcome.PLAYER_WOUNDS;
        if (creatureAS > playerAS) return CombatRoundOutcome.CREATURE_WOUNDS;
        return CombatRoundOutcome.DRAW;
    }

    private int resolvePlayerWoundsCreature(Player player) {
        if (input.askTestLuck()) {
            boolean lucky = dice.roll2d6() <= player.getLuck();
            player.modifyAttribute(AttributeType.LUCK, -1);
            return lucky ? LUCKY_PLAYER_WOUNDS_CREATURE : UNLUCKY_PLAYER_WOUNDS_CREATURE;
        }
        return NORMAL_WOUND;
    }

    private int resolveCreatureWoundsPlayer(Player player) {
        if (input.askTestLuck()) {
            boolean lucky = dice.roll2d6() <= player.getLuck();
            player.modifyAttribute(AttributeType.LUCK, -1);
            return lucky ? LUCKY_CREATURE_WOUNDS_PLAYER : UNLUCKY_CREATURE_WOUNDS_PLAYER;
        }
        return NORMAL_WOUND;
    }
}
