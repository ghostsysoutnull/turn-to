package com.tas.neo.analysis;

import com.tas.neo.combat.CombatSystemRegistry;
import com.tas.neo.combat.DefaultCombatSystemRegistry;
import com.tas.neo.combat.personal.PersonalCombatSystem;
import com.tas.neo.engine.Game;
import com.tas.neo.io.GameInput;
import com.tas.neo.io.GameLogger;
import com.tas.neo.io.GameOutput;
import com.tas.neo.io.NoOpGameLogger;
import com.tas.neo.io.TerminalOutput;
import com.tas.neo.loader.AdventureLoader;
import com.tas.neo.loader.JsonAdventureLoader;
import com.tas.neo.mechanics.CombatEngine;
import com.tas.neo.mechanics.Dice;
import com.tas.neo.mechanics.RandomDice;
import com.tas.neo.scripting.LuaScriptEngine;
import com.tas.neo.scripting.ScriptEngine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs a {@link PlaybackScenario} against the real {@link TerminalOutput} with
 * captured I/O, returning the full rendered output as a string.
 *
 * <p>Use this tool to verify that the terminal UI produces legible, correctly
 * structured output for a known path through an adventure. The output is written
 * to {@code adventures/<id>-playback-<firstSection>-<lastSection>.txt} for
 * human inspection and future comparison.
 *
 * <p>If the UI layer changes (new status bar fields, reformatted choices, etc.)
 * re-running the playback will reveal the difference immediately. Scenarios do not
 * need updating unless the adventure's section structure changes, because
 * {@link PathFollowingInput} resolves choices by target section, not by index.
 *
 * <p>CLI usage:
 * <pre>
 *   mvn exec:java \
 *     -Dexec.mainClass=com.tas.neo.analysis.GamePlaybackRunner \
 *     -Dexec.args="adventures/the-iron-road.json 2,4"
 * </pre>
 */
public class GamePlaybackRunner {

    /**
     * Runs the scenario and returns the full captured terminal output.
     *
     * @param scenario     the adventure and intended section path
     * @param adventuresDir directory containing adventure JSON files
     * @return the complete output rendered by {@link TerminalOutput}
     */
    public static String run(PlaybackScenario scenario, Path adventuresDir) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream capturedStream = new PrintStream(captured, true, StandardCharsets.UTF_8);

        Dice dice                           = new RandomDice();
        GameInput input                     = new PathFollowingInput(scenario.path());
        GameOutput output                   = new TerminalOutput(capturedStream);
        GameLogger logger                   = new NoOpGameLogger();
        ScriptEngine scriptEngine           = new LuaScriptEngine();
        AdventureLoader loader              = new JsonAdventureLoader(adventuresDir);
        CombatSystemRegistry combatRegistry = new DefaultCombatSystemRegistry(
            new PersonalCombatSystem(new CombatEngine(dice, input, output))
        );

        Game game = new Game(input, output, loader, dice, scriptEngine, combatRegistry, logger);
        game.run(scenario.adventureId());

        return captured.toString(StandardCharsets.UTF_8);
    }

    /** CLI entry point: args[0] = path to adventure JSON, args[1] = comma-separated target sections */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: GamePlaybackRunner <path-to-adventure.json> <section,section,...>");
            System.err.println("Example: GamePlaybackRunner adventures/the-iron-road.json 2,4");
            System.exit(1);
        }

        Path adventurePath  = Path.of(args[0]);
        Path adventuresDir  = adventurePath.getParent();
        String adventureId  = adventurePath.getFileName().toString().replace(".json", "");
        String pathArg      = args[1];

        PlaybackScenario scenario = PlaybackScenario.of(adventureId, pathArg);

        String output = run(scenario, adventuresDir);
        System.out.print(output);

        String filename = adventureId + "-playback-" + pathArg.replace(",", "-") + ".txt";
        Path outputPath = adventuresDir.resolve(filename);
        Files.writeString(outputPath, output);
        System.err.println("Playback output written to " + outputPath);
    }
}
