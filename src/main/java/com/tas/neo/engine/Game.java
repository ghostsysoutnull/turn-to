package com.tas.neo.engine;

import com.tas.neo.combat.CombatSystemRegistry;
import com.tas.neo.io.GameInput;
import com.tas.neo.io.GameLogger;
import com.tas.neo.io.GameOutput;
import com.tas.neo.loader.AdventureLoader;
import com.tas.neo.mechanics.Dice;
import com.tas.neo.scripting.ScriptEngine;

public class Game {

    // Stub — to be fully implemented in engine layer

    private final GameState state = new GameState();

    public Game(GameInput input, GameOutput output, AdventureLoader loader,
                Dice dice, ScriptEngine scriptEngine,
                CombatSystemRegistry combatRegistry, GameLogger logger) {
        // stub
    }

    public void run(String adventureId) {
        // stub
    }

    public GameState state() {
        return state;
    }
}
