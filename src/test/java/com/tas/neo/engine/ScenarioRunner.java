package com.tas.neo.engine;

import com.tas.neo.combat.CombatSystemRegistry;
import com.tas.neo.combat.DefaultCombatSystemRegistry;
import com.tas.neo.combat.personal.PersonalCombatSystem;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.io.GameInput;
import com.tas.neo.io.GameLogger;
import com.tas.neo.io.RecordingGameLogger;
import com.tas.neo.io.RecordingOutput;
import com.tas.neo.io.ScriptedInput;
import com.tas.neo.loader.InMemoryAdventureLoader;
import com.tas.neo.mechanics.CombatEngine;
import com.tas.neo.mechanics.Dice;
import com.tas.neo.scripting.NoOpScriptEngine;
import com.tas.neo.scripting.ScriptEngine;

import java.util.List;
import java.util.Random;

/**
 * Drives a complete game run without terminal interaction.
 *
 * <p>Defined in {@code docs/design/06-testability.md}.
 */
public class ScenarioRunner {

    private final Adventure adventure;
    private final Dice dice;
    private final GameInput input;
    private GameLogger logger;

    private ScenarioRunner(Adventure adventure, Dice dice, GameInput input) {
        this.adventure = adventure;
        this.dice = dice;
        this.input = input;
        this.logger = new RecordingGameLogger();
    }

    public static ScenarioRunner scripted(Adventure adventure, Dice dice, int... choices) {
        return new ScenarioRunner(adventure, dice, new ScriptedInput(choices));
    }

    public static ScenarioRunner random(Adventure adventure, Dice dice) {
        return new ScenarioRunner(adventure, dice, new RandomChoiceInput(dice));
    }

    private ScriptEngine scriptEngine = new NoOpScriptEngine();

    public ScenarioRunner withLogger(GameLogger logger) {
        this.logger = logger;
        return this;
    }

    public ScenarioRunner withScriptEngine(ScriptEngine engine) {
        this.scriptEngine = engine;
        return this;
    }

    public ScenarioResult run() {
        RecordingOutput output = new RecordingOutput();
        InMemoryAdventureLoader loader = new InMemoryAdventureLoader(adventure);
        CombatEngine combatEngine = new CombatEngine(dice, input, output);
        CombatSystemRegistry combatRegistry = new DefaultCombatSystemRegistry(
            new PersonalCombatSystem(combatEngine)
        );
        Game game = new Game(input, output, loader, dice, scriptEngine, combatRegistry, logger);
        game.run(adventure.id());

        GameState state = game.state();
        RecordingGameLogger recording =
            logger instanceof RecordingGameLogger r ? r : new RecordingGameLogger();
        return new ScenarioResult(
            state,
            output,
            recording.sessionLog(),
            state.isVictory(),
            state.isGameOver(),
            game.scriptState()
        );
    }

    private static final class RandomChoiceInput implements GameInput {
        private final Random random;

        RandomChoiceInput(Dice dice) {
            // Derive a Random from the dice so that seed-driven reproducibility survives.
            this.random = new Random(dice.roll(Integer.MAX_VALUE));
        }

        @Override
        public int readChoice(List<Choice> available) {
            return random.nextInt(available.size()) + 1;
        }

        @Override
        public boolean readYesNo(String prompt) {
            return random.nextBoolean();
        }

        @Override
        public void waitForEnter() {
            // no-op
        }
    }
}
