package com.tas.neo;

import com.tas.neo.combat.CombatSystemRegistry;
import com.tas.neo.combat.DefaultCombatSystemRegistry;
import com.tas.neo.combat.personal.PersonalCombatSystem;
import com.tas.neo.engine.Game;
import com.tas.neo.io.FileGameLogger;
import com.tas.neo.io.GameInput;
import com.tas.neo.io.GameLogger;
import com.tas.neo.io.GameOutput;
import com.tas.neo.io.TerminalInput;
import com.tas.neo.io.TerminalOutput;
import com.tas.neo.loader.AdventureLoader;
import com.tas.neo.loader.JsonAdventureLoader;
import com.tas.neo.mechanics.CombatEngine;
import com.tas.neo.domain.Dice;
import com.tas.neo.mechanics.RandomDice;
import com.tas.neo.scripting.LuaScriptEngine;
import com.tas.neo.scripting.ScriptEngine;

import java.nio.file.Path;

public class Main {

    public static void main(String[] args) {
        String adventureId = args.length > 0 ? args[0] : "the-warlock-of-firetop-mountain";
        Dice dice                           = new RandomDice();
        GameInput input                     = new TerminalInput(System.in, System.out);
        GameOutput output                   = new TerminalOutput(System.out);
        GameLogger logger                   = new FileGameLogger(adventureId, Path.of("sessions"));
        ScriptEngine scriptEngine           = new LuaScriptEngine();
        AdventureLoader loader              = new JsonAdventureLoader(Path.of("adventures"));
        CombatSystemRegistry combatRegistry = new DefaultCombatSystemRegistry(
            new PersonalCombatSystem(new CombatEngine(dice, input, output))
        );
        Game game = new Game(input, output, loader, dice, scriptEngine, combatRegistry, logger);
        game.run(adventureId);
    }
}
