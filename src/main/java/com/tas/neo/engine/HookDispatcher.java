package com.tas.neo.engine;

import com.tas.neo.combat.CombatSystemRegistry;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.event.CombatEvent;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.combat.CombatOutcome;
import com.tas.neo.domain.combat.CombatRound;
import com.tas.neo.domain.item.Item;
import com.tas.neo.domain.item.ItemScriptHook;
import com.tas.neo.domain.location.Cell;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.io.GameInput;
import com.tas.neo.io.GameOutput;
import com.tas.neo.mechanics.Dice;
import com.tas.neo.scripting.AdventureScriptState;
import com.tas.neo.scripting.DefaultScriptContext;
import com.tas.neo.scripting.ScriptContext;
import com.tas.neo.scripting.ScriptEngine;

import java.util.List;

public class HookDispatcher {

    private final ScriptEngine scriptEngine;
    private final GameInput input;
    private final GameOutput output;
    private final GameState state;
    private final AdventureScriptState scriptState;
    private final CombatSystemRegistry combatRegistry;
    private final Dice dice;

    public HookDispatcher(ScriptEngine scriptEngine, GameInput input, GameOutput output,
                          GameState state, AdventureScriptState scriptState,
                          CombatSystemRegistry combatRegistry, Dice dice) {
        this.scriptEngine = scriptEngine;
        this.input = input;
        this.output = output;
        this.state = state;
        this.scriptState = scriptState;
        this.combatRegistry = combatRegistry;
        this.dice = dice;
    }

    public void fireAdventureHook(AdventureHook hook, Adventure adventure) {
        adventure.scripts().get(hookKey(hook)).ifPresent(script ->
            runScript(script, DefaultScriptContext.forSection(
                state.getPlayer(), output, List.of(), -1)));
    }

    public void fireSectionHook(SectionHook hook, Section section, List<Choice> mutableChoices) {
        section.scripts().get(hookKey(hook)).ifPresent(script -> {
            ScriptContext ctx = hook == SectionHook.ON_CHOICES
                ? DefaultScriptContext.forChoices(state.getPlayer(), output, mutableChoices, section.number())
                : DefaultScriptContext.forSection(state.getPlayer(), output, mutableChoices, section.number());
            runScript(script, ctx);
        });
    }

    public void fireCellHook(SectionHook hook, Cell cell, List<Choice> mutableChoices) {
        cell.scripts().get(hookKey(hook)).ifPresent(script ->
            runScript(script, DefaultScriptContext.forCell(state.getPlayer(), output, mutableChoices)));
    }

    public void fireCombatHook(CombatHook hook, CombatEvent event, CombatRound round) {
        // stub — combat hook dispatch handled when combat layer is implemented
    }

    public void fireItemHook(ItemScriptHook hook, Item item) {
        // stub — item hook dispatch handled when item layer is implemented
    }

    public CombatOutcome processCombatEvent(CombatEvent event) {
        // stub
        return null;
    }

    public void processEvent(SectionEvent event) {
        // stub
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void runScript(String script, ScriptContext ctx) {
        try {
            scriptEngine.execute(script, ctx);
        } catch (com.tas.neo.scripting.ScriptException e) {
            // Failing scripts do not crash the game.
        }
    }

    private static String hookKey(AdventureHook hook) {
        return toCamelCase(hook.name());
    }

    private static String hookKey(SectionHook hook) {
        return toCamelCase(hook.name());
    }

    private static String hookKey(CombatHook hook) {
        return toCamelCase(hook.name());
    }

    /** Converts UPPER_SNAKE_CASE enum name to lowerCamelCase. ON_ENTER → onEnter. */
    private static String toCamelCase(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
