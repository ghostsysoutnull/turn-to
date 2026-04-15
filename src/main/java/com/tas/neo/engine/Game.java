package com.tas.neo.engine;

import com.tas.neo.combat.CombatSystemRegistry;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.ChoiceTarget;
import com.tas.neo.domain.adventure.GridTarget;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.item.Inventory;
import com.tas.neo.domain.location.Grid;
import com.tas.neo.domain.log.NavigationEntry;
import com.tas.neo.domain.player.Attribute;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.domain.player.Player;
import com.tas.neo.io.GameInput;
import com.tas.neo.io.GameLogger;
import com.tas.neo.io.GameOutput;
import com.tas.neo.loader.AdventureLoader;
import com.tas.neo.loader.AdventureLoadException;
import com.tas.neo.mechanics.DiceFormula;
import com.tas.neo.mechanics.Dice;
import com.tas.neo.scripting.AdventureScriptState;
import com.tas.neo.scripting.ScriptEngine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class Game {

    private final GameInput input;
    private final GameOutput output;
    private final AdventureLoader loader;
    private final Dice dice;
    private final ScriptEngine scriptEngine;
    private final CombatSystemRegistry combatRegistry;
    private final GameLogger logger;
    private final GameState state = new GameState();

    public Game(GameInput input, GameOutput output, AdventureLoader loader,
                Dice dice, ScriptEngine scriptEngine,
                CombatSystemRegistry combatRegistry, GameLogger logger) {
        this.input = input;
        this.output = output;
        this.loader = loader;
        this.dice = dice;
        this.scriptEngine = scriptEngine;
        this.combatRegistry = combatRegistry;
        this.logger = logger;
    }

    public void run(String adventureId) {
        Adventure adventure;
        try {
            adventure = loader.load(adventureId);
        } catch (AdventureLoadException e) {
            return;
        }

        state.setPlayer(createPlayer(adventure));

        AdventureScriptState scriptState = new AdventureScriptState();
        HookDispatcher hooks = new HookDispatcher(
            scriptEngine, input, output, state, scriptState, combatRegistry, dice);

        Section current = adventure.getSection(adventure.startSection());
        state.navigateTo(current);

        runSectionLoop(adventure, hooks, current);
    }

    public GameState state() {
        return state;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void runSectionLoop(Adventure adventure, HookDispatcher hooks, Section startSection) {
        Section current = startSection;

        while (true) {
            hooks.fireSectionHook(SectionHook.ON_ENTER, current, new ArrayList<>());

            if (state.isTerminal()) break;

            SectionType type = current.type();

            if (type == SectionType.VICTORY) {
                output.showNarrative(current.narrative());
                output.showVictory(current.narrative());
                state.setVictory();
                logger.close();
                break;
            }

            if (type == SectionType.INSTANT_DEATH) {
                output.showNarrative(current.narrative());
                output.showGameOver(current.narrative());
                state.setGameOver();
                logger.close();
                break;
            }

            // NORMAL section
            output.showNarrative(current.narrative());

            List<Choice> choices = new ArrayList<>(current.choices());
            hooks.fireSectionHook(SectionHook.ON_CHOICES, current, choices);

            if (state.isTerminal()) break;

            if (choices.isEmpty()) {
                // No choices — treat as dead end / game over
                state.setGameOver();
                logger.close();
                break;
            }

            output.showChoices(choices);

            int chosen = input.readChoice(choices);
            Choice choice = choices.get(chosen - 1);
            ChoiceTarget target = choice.target();

            String fromLocation = locationString(current, null, null);

            Section next = resolveTarget(adventure, target, current);
            if (next == null) {
                state.setGameOver();
                logger.close();
                break;
            }

            String toLocation = locationString(next, null, null);
            logger.logNavigation(new NavigationEntry(fromLocation, toLocation, choice.text()));

            state.navigateTo(next);
            current = next;
        }
    }

    private Section resolveTarget(Adventure adventure, ChoiceTarget target, Section current) {
        return switch (target) {
            case SectionTarget st -> {
                try {
                    yield adventure.getSection(st.sectionNumber());
                } catch (IllegalArgumentException e) {
                    yield null;
                }
            }
            case GridTarget gt -> null; // grid navigation not implemented in this phase
        };
    }

    private String locationString(Section section, Grid grid, Object cell) {
        if (section != null) {
            return "section:" + section.number();
        }
        return "unknown";
    }

    private Player createPlayer(Adventure adventure) {
        Map<AttributeType, Attribute> attributes = new EnumMap<>(AttributeType.class);

        int skill = DiceFormula.parse("1d6+6").roll(dice);
        attributes.put(AttributeType.SKILL, new Attribute(AttributeType.SKILL, skill, skill));

        int stamina = DiceFormula.parse("2d6+12").roll(dice);
        attributes.put(AttributeType.STAMINA, new Attribute(AttributeType.STAMINA, stamina, stamina));

        int luck = DiceFormula.parse("1d6+6").roll(dice);
        attributes.put(AttributeType.LUCK, new Attribute(AttributeType.LUCK, luck, luck));

        return new Player(attributes, new Inventory(), 0, adventure.initialProvisions());
    }
}
