package com.tas.neo.engine;

import com.tas.neo.combat.CombatSystemRegistry;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.ChoiceTarget;
import com.tas.neo.domain.adventure.GridTarget;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.adventure.SystemChoiceTarget;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.item.Inventory;
import com.tas.neo.domain.location.Cell;
import com.tas.neo.domain.location.Direction;
import com.tas.neo.domain.location.Grid;
import com.tas.neo.domain.location.Passage;
import com.tas.neo.domain.log.NavigationEntry;
import com.tas.neo.domain.party.PartyMember;
import com.tas.neo.domain.party.PartyMemberDefinition;
import com.tas.neo.domain.party.PartyMemberStat;
import com.tas.neo.domain.player.Attribute;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.domain.player.Player;
import com.tas.neo.io.GameInput;
import com.tas.neo.io.GameLogger;
import com.tas.neo.io.GameOutput;
import com.tas.neo.io.OutputEvent;
import com.tas.neo.loader.AdventureLoader;
import com.tas.neo.loader.AdventureLoadException;
import com.tas.neo.mechanics.DiceFormula;
import com.tas.neo.mechanics.Dice;
import com.tas.neo.mechanics.DiceStatDefinition;
import com.tas.neo.mechanics.FixedStatDefinition;
import com.tas.neo.mechanics.StatDefinition;
import com.tas.neo.scripting.AdventureScriptState;
import com.tas.neo.scripting.ScriptEngine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private AdventureScriptState scriptState;

    public AdventureScriptState scriptState() {
        return scriptState;
    }

    public void run(String adventureId) {
        Adventure adventure;
        try {
            adventure = loader.load(adventureId);
        } catch (AdventureLoadException e) {
            output.showMessage("Failed to load adventure '" + adventureId + "': " + e.getMessage());
            return;
        }

        state.setPlayer(createPlayer(adventure));
        createPartyMembers(adventure);

        scriptState = new AdventureScriptState();
        HookDispatcher hooks = new HookDispatcher(
            scriptEngine, input, output, state, scriptState, combatRegistry, dice, logger);

        hooks.fireAdventureHook(AdventureHook.ON_LOAD, adventure);
        hooks.fireAdventureHook(AdventureHook.ON_START, adventure);

        Section current = adventure.getSection(adventure.startSection());
        state.navigateTo(current);
        logger.logNavigation(new NavigationEntry("START", "section:" + current.number(), "START"));

        runLoop(adventure, hooks);
    }

    public GameState state() {
        return state;
    }

    // -----------------------------------------------------------------------
    // Main game loop
    // -----------------------------------------------------------------------

    private void runLoop(Adventure adventure, HookDispatcher hooks) {
        while (!state.isTerminal()) {
            if (state.isInGrid()) {
                runCellStep(adventure, hooks);
            } else {
                Section current = state.currentSection();
                if (current == null) break;
                runSectionStep(adventure, hooks, current);
            }
        }
        logger.close();
    }

    private void runSectionStep(Adventure adventure, HookDispatcher hooks, Section section) {
        List<Choice> ignored = new ArrayList<>();
        hooks.fireSectionHook(SectionHook.ON_ENTER, section, ignored);

        if (state.isTerminal()) return;

        for (SectionEvent event : section.events()) {
            hooks.processEvent(event, adventure);
            if (state.isTerminal()) return;
        }

        SectionType type = section.type();

        if (type == SectionType.VICTORY) {
            hooks.fireAdventureHook(AdventureHook.ON_VICTORY, adventure);
            output.showVictory(section.narrative());
            logger.logEvent(new OutputEvent.VictoryShown(section.narrative()));
            state.setVictory();
            return;
        }

        if (type == SectionType.INSTANT_DEATH) {
            output.showGameOver(section.narrative());
            logger.logEvent(new OutputEvent.GameOverShown(section.narrative()));
            state.setGameOver();
            return;
        }

        // NORMAL section
        output.clear();
        output.showStatus(state.getPlayer(), state.activePartyMembers());
        output.showNarrative(section.narrative());
        logger.logEvent(new OutputEvent.NarrativeShown(section.narrative()));

        List<Choice> choices = new ArrayList<>(section.choices());
        hooks.fireSectionHook(SectionHook.ON_CHOICES, section, choices);

        if (state.isTerminal()) return;

        injectSystemChoices(choices);

        output.showChoices(choices);

        int chosen = input.readChoice(choices);
        Choice choice = choices.get(chosen - 1);

        String fromLocation = "section:" + section.number();
        navigateFromSection(adventure, choice, fromLocation);
    }

    private void navigateFromSection(Adventure adventure, Choice choice, String fromLocation) {
        switch (choice.target()) {
            case SectionTarget st -> {
                Section next;
                try {
                    next = adventure.getSection(st.sectionNumber());
                } catch (IllegalArgumentException e) {
                    state.setGameOver();
                    return;
                }
                logger.logNavigation(new NavigationEntry(fromLocation, "section:" + next.number(), choice.text()));
                state.navigateTo(next);
            }
            case GridTarget gt -> {
                Grid grid = adventure.getGrid(gt.gridId()).orElse(null);
                if (grid == null) { state.setGameOver(); return; }
                Cell cell = grid.getCellById(gt.cellId()).orElse(null);
                if (cell == null) { state.setGameOver(); return; }
                logger.logNavigation(new NavigationEntry(fromLocation, cellLocationString(grid, cell), choice.text()));
                state.navigateToCell(grid, cell);
            }
            case SystemChoiceTarget st -> handleSystemChoice(st);
        }
    }

    private void runCellStep(Adventure adventure, HookDispatcher hooks) {
        Grid grid = state.currentGrid().orElseThrow();
        Cell cell = state.currentCell().orElseThrow();

        List<Choice> ignored = new ArrayList<>();
        hooks.fireCellHook(SectionHook.ON_ENTER, cell, ignored);

        if (state.isTerminal()) return;

        for (SectionEvent event : cell.events()) {
            hooks.processEvent(event, adventure);
            if (state.isTerminal()) return;
        }

        List<Choice> choices = new ArrayList<>(cell.choices());
        for (Map.Entry<Direction, Passage> entry : cell.passages().entrySet()) {
            Passage passage = entry.getValue();
            String label = passage.label().orElse(entry.getKey().defaultLabel());
            if (passage.toSection().isPresent()) {
                choices.add(Choice.to(label, new SectionTarget(passage.toSection().get())));
            } else {
                Direction dir = entry.getKey();
                int nx = cell.x() + dirDx(dir);
                int ny = cell.y() + dirDy(dir);
                int nz = cell.z();
                choices.add(Choice.to(label, new GridTarget(grid.id(), nx + "," + ny + "," + nz)));
            }
        }

        hooks.fireCellHook(SectionHook.ON_CHOICES, cell, choices);

        if (state.isTerminal()) return;

        if (choices.isEmpty()) {
            // Dead end — no exits from this cell.
            state.setGameOver();
            return;
        }

        output.clear();
        output.showStatus(state.getPlayer(), state.activePartyMembers());
        output.showNarrative(cell.narrative());
        logger.logEvent(new OutputEvent.NarrativeShown(cell.narrative()));
        output.showChoices(choices);

        int chosen = input.readChoice(choices);
        Choice choice = choices.get(chosen - 1);

        String fromLocation = cellLocationString(grid, cell);
        navigateFromCell(adventure, grid, choice, fromLocation);
    }

    private void navigateFromCell(Adventure adventure, Grid currentGrid, Choice choice, String fromLocation) {
        switch (choice.target()) {
            case SectionTarget st -> {
                Section next;
                try {
                    next = adventure.getSection(st.sectionNumber());
                } catch (IllegalArgumentException e) {
                    state.setGameOver();
                    return;
                }
                logger.logNavigation(new NavigationEntry(fromLocation, "section:" + next.number(), choice.text()));
                state.navigateTo(next);
            }
            case GridTarget gt -> {
                Grid targetGrid = adventure.getGrid(gt.gridId()).orElse(null);
                if (targetGrid == null) { state.setGameOver(); return; }
                // Try named cell id first, then coordinate format "x,y,z"
                Optional<Cell> cellOpt = targetGrid.getCellById(gt.cellId());
                if (cellOpt.isEmpty()) {
                    String[] parts = gt.cellId().split(",");
                    if (parts.length == 3) {
                        try {
                            cellOpt = targetGrid.getCell(
                                Integer.parseInt(parts[0]),
                                Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2])
                            );
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                if (cellOpt.isEmpty()) { state.setGameOver(); return; }
                Cell nextCell = cellOpt.get();
                logger.logNavigation(new NavigationEntry(fromLocation, cellLocationString(targetGrid, nextCell), choice.text()));
                state.navigateToCell(targetGrid, nextCell);
            }
            case SystemChoiceTarget st -> handleSystemChoice(st);
        }
    }

    private void handleSystemChoice(SystemChoiceTarget target) {
        switch (target.action()) {
            case "inventory" -> output.showInventory(
                state.player().getInventory().allStacks(),
                state.player().getGold(),
                state.player().getProvisions()
            );
            case "quit" -> state.setGameOver();
            case "eat" -> {
                if (state.player().getProvisions() > 0) {
                    state.player().modifyProvisions(-1);
                    state.player().modifyAttribute(AttributeType.STAMINA, 4);
                }
            }
        }
    }

    private void injectSystemChoices(List<Choice> choices) {
        if (state.player().getProvisions() > 0) {
            choices.add(Choice.to("Eat a provision (restores 4 STAMINA)", new SystemChoiceTarget("eat")));
        }
        choices.add(Choice.to("Check inventory", new SystemChoiceTarget("inventory")));
        choices.add(Choice.to("Quit", new SystemChoiceTarget("quit")));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static String cellLocationString(Grid grid, Cell cell) {
        return "grid:" + grid.id() + ":" + cell.x() + "," + cell.y() + "," + cell.z();
    }

    private static int dirDx(Direction dir) {
        return switch (dir) {
            case EAST -> 1;
            case WEST -> -1;
            default -> 0;
        };
    }

    private static int dirDy(Direction dir) {
        return switch (dir) {
            case NORTH -> -1;
            case SOUTH -> 1;
            default -> 0;
        };
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

    private void createPartyMembers(Adventure adventure) {
        for (PartyMemberDefinition def : adventure.partyMemberDefinitions()) {
            Map<String, PartyMemberStat> stats = new LinkedHashMap<>();
            for (Map.Entry<String, StatDefinition> entry : def.stats().entrySet()) {
                String statName = entry.getKey();
                int initial = switch (entry.getValue()) {
                    case DiceStatDefinition d -> d.resolveInitial(dice);
                    case FixedStatDefinition f -> f.resolveInitial(dice);
                };
                int max = switch (entry.getValue()) {
                    case DiceStatDefinition d -> d.fixedMax().isPresent()
                        ? d.fixedMax().getAsInt() : initial;
                    case FixedStatDefinition f -> f.resolveMax(dice);
                };
                stats.put(statName, new PartyMemberStat(statName, initial, max));
            }
            state.addPartyMember(new PartyMember(
                def.id(), def.displayName(), def.lifeStat(),
                stats, def.onDefeat(), def.initialState()
            ));
        }
    }
}
