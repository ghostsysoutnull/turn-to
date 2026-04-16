package com.tas.neo.engine;

import com.tas.neo.combat.CombatSystemRegistry;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.event.CombatEvent;
import com.tas.neo.domain.adventure.event.GoldChangeEvent;
import com.tas.neo.domain.adventure.event.ItemAction;
import com.tas.neo.domain.adventure.event.ItemEvent;
import com.tas.neo.domain.adventure.event.LuckTestEvent;
import com.tas.neo.domain.adventure.event.NavigateEvent;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.adventure.event.SkillTestEvent;
import com.tas.neo.domain.adventure.event.StatChangeEvent;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.combat.CombatOutcome;
import com.tas.neo.domain.combat.CombatRound;
import com.tas.neo.domain.item.Item;
import com.tas.neo.domain.item.ItemCategory;
import com.tas.neo.domain.item.ItemScriptHook;
import com.tas.neo.domain.location.Cell;
import com.tas.neo.domain.log.GameError;
import com.tas.neo.domain.log.PlayerSnapshot;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.io.GameInput;
import com.tas.neo.io.GameLogger;
import com.tas.neo.io.GameOutput;
import com.tas.neo.io.NoOpGameLogger;
import com.tas.neo.mechanics.Dice;
import com.tas.neo.scripting.AdventureScriptState;
import com.tas.neo.scripting.DefaultScriptContext;
import com.tas.neo.scripting.PartyMemberProxy;
import com.tas.neo.scripting.ScriptContext;
import com.tas.neo.scripting.ScriptEngine;
import com.tas.neo.scripting.ScriptException;
import com.tas.neo.domain.adventure.ScriptBlock;

import java.util.List;

public class HookDispatcher {

    private final ScriptEngine scriptEngine;
    private final GameInput input;
    private final GameOutput output;
    private final GameState state;
    private final AdventureScriptState scriptState;
    private final CombatSystemRegistry combatRegistry;
    private final Dice dice;
    private final GameLogger logger;

    public HookDispatcher(ScriptEngine scriptEngine, GameInput input, GameOutput output,
                          GameState state, AdventureScriptState scriptState,
                          CombatSystemRegistry combatRegistry, Dice dice,
                          GameLogger logger) {
        this.scriptEngine = scriptEngine;
        this.input = input;
        this.output = output;
        this.state = state;
        this.scriptState = scriptState;
        this.combatRegistry = combatRegistry;
        this.dice = dice;
        this.logger = logger;
    }

    /** Convenience constructor that discards log events. Used in tests that do not assert on logs. */
    public HookDispatcher(ScriptEngine scriptEngine, GameInput input, GameOutput output,
                          GameState state, AdventureScriptState scriptState,
                          CombatSystemRegistry combatRegistry, Dice dice) {
        this(scriptEngine, input, output, state, scriptState, combatRegistry, dice,
             new NoOpGameLogger());
    }

    public void fireAdventureHook(AdventureHook hook, Adventure adventure) {
        adventure.scripts().get(hookKey(hook)).ifPresent(script ->
            runScript(script, DefaultScriptContext.forSection(
                state.getPlayer(), state, output, List.of(), -1),
                "adventure:" + hook.name().toLowerCase()));
    }

    public void fireSectionHook(SectionHook hook, Section section, List<Choice> mutableChoices) {
        section.scripts().get(hookKey(hook)).ifPresent(script -> {
            ScriptContext ctx = hook == SectionHook.ON_CHOICES
                ? DefaultScriptContext.forChoices(state.getPlayer(), state, output, mutableChoices, section.number())
                : DefaultScriptContext.forSection(state.getPlayer(), state, output, mutableChoices, section.number());
            runScript(script, ctx, "section:" + section.number());
        });
    }

    public void fireCellHook(SectionHook hook, Cell cell, List<Choice> mutableChoices) {
        cell.scripts().get(hookKey(hook)).ifPresent(script ->
            runScript(script, DefaultScriptContext.forCell(state.getPlayer(), state, output, mutableChoices),
                "cell:" + cell.x() + "," + cell.y() + "," + cell.z()));
    }

    public void fireCombatHook(CombatHook hook, CombatEvent event, CombatRound round) {
        // stub — combat hook dispatch handled when combat layer is implemented
    }

    public void fireItemHook(ItemScriptHook hook, Item item) {
        // stub — item hook dispatch handled when item layer is implemented
    }

    /** Convenience overload for tests and contexts where navigation is not needed. */
    public void processEvent(SectionEvent event) {
        processEvent(event, null);
    }

    public void processEvent(SectionEvent event, Adventure adventure) {
        switch (event) {
            case StatChangeEvent e -> {
                state.player().modifyAttribute(e.attribute(), e.delta());
                int newValue = state.player().getStat(e.attribute());
                logger.logEvent(new com.tas.neo.io.OutputEvent.StatChanged(
                    e.attribute().name(), e.delta(), newValue));
                if (!state.player().isAlive()) {
                    state.setGameOver();
                }
            }
            case GoldChangeEvent e -> state.player().modifyGold(e.delta());
            case ItemEvent e -> {
                if (e.action() == ItemAction.GAIN) {
                    Item item = new Item(e.itemName(), "", ItemCategory.PASSIVE, false, ScriptBlock.empty());
                    state.player().getInventory().add(item, e.quantity());
                    logger.logEvent(new com.tas.neo.io.OutputEvent.ItemGained(e.itemName()));
                } else {
                    state.player().getInventory().remove(e.itemName(), e.quantity());
                    logger.logEvent(new com.tas.neo.io.OutputEvent.ItemLost(e.itemName()));
                }
            }
            case NavigateEvent e -> navigateTo(adventure, e.targetSection());
            case LuckTestEvent e -> {
                int roll = dice.roll2d6();
                int luck = state.player().getLuck();
                state.player().modifyAttribute(AttributeType.LUCK, -1);
                navigateTo(adventure, roll <= luck ? e.successSection() : e.failSection());
            }
            case SkillTestEvent e -> {
                int roll = dice.roll2d6();
                int skill = state.player().getSkill();
                navigateTo(adventure, roll <= skill ? e.successSection() : e.failSection());
            }
            case CombatEvent e -> {
                CombatOutcome outcome = combatRegistry.get(e.system()).run(
                    state.player(), state.activePartyMembers(),
                    e.opponents(), e.params(),
                    combatRegistry, this, input, output, dice);
                outcome.combatResult().ifPresent(cr -> {
                    String opponentName = e.opponents().isEmpty() ? "Unknown" : e.opponents().get(0).name();
                    int oppSkill   = e.opponents().isEmpty() ? 0 : e.opponents().get(0).skill();
                    int oppStamina = e.opponents().isEmpty() ? 0 : e.opponents().get(0).stamina();
                    logger.logEvent(new com.tas.neo.io.OutputEvent.CombatResolved(
                        opponentName, oppSkill, oppStamina,
                        cr.playerWon(), cr.roundsFought(), cr.playerStaminaLost()));
                });
                if (outcome.navigateTo().isPresent()) {
                    navigateTo(adventure, outcome.navigateTo().get());
                } else if (outcome.type() == com.tas.neo.domain.combat.CombatOutcomeType.DEFEAT) {
                    if (e.failureSection() > 0) {
                        // Navigate to the failure section and let the game loop render it
                        // (typically INSTANT_DEATH). Do NOT set game over here — the section's
                        // type handler in runSectionStep is responsible.
                        navigateTo(adventure, e.failureSection());
                    } else {
                        state.setGameOver();
                    }
                } else if (e.successSection() > 0
                           && outcome.type() == com.tas.neo.domain.combat.CombatOutcomeType.VICTORY) {
                    navigateTo(adventure, e.successSection());
                }
            }
        }
    }

    private void navigateTo(Adventure adventure, int sectionNumber) {
        if (adventure == null) return;
        try {
            state.navigateTo(adventure.getSection(sectionNumber));
        } catch (IllegalArgumentException e) {
            state.setGameOver();
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void runScript(String script, ScriptContext ctx, String source) {
        ErrorCapturingContext wrapped = new ErrorCapturingContext(ctx, source);
        try {
            scriptEngine.execute(script, wrapped, scriptState);
        } catch (ScriptException e) {
            logger.logError(new GameError(source, "ScriptError", e.getMessage(),
                PlayerSnapshot.of(state)));
        }
        if (wrapped.hasError()) {
            logger.logError(new GameError(source, "ScriptError", wrapped.errorMessage(),
                PlayerSnapshot.of(state)));
        }
    }

    /** Delegates all calls to the wrapped context; captures the first script error message. */
    private static final class ErrorCapturingContext implements ScriptContext {
        private final ScriptContext delegate;
        private final String source;
        private String errorMessage;

        ErrorCapturingContext(ScriptContext delegate, String source) {
            this.delegate = delegate;
            this.source = source;
        }

        boolean hasError() { return errorMessage != null; }
        String errorMessage() { return errorMessage; }

        @Override public void onScriptError(String msg) { if (errorMessage == null) errorMessage = msg; }

        @Override public void modifyStat(String a, int d)   { delegate.modifyStat(a, d); }
        @Override public int getStat(String a)               { return delegate.getStat(a); }
        @Override public void modifyGold(int d)              { delegate.modifyGold(d); }
        @Override public int getGold()                       { return delegate.getGold(); }
        @Override public void addItem(String n)              { delegate.addItem(n); }
        @Override public void addItem(String n, int q)       { delegate.addItem(n, q); }
        @Override public void removeItem(String n)           { delegate.removeItem(n); }
        @Override public void removeItem(String n, int q)    { delegate.removeItem(n, q); }
        @Override public boolean hasItem(String n)           { return delegate.hasItem(n); }
        @Override public int getItemCount(String n)          { return delegate.getItemCount(n); }
        @Override public PartyMemberProxy getPartyMember(String id) { return delegate.getPartyMember(id); }
        @Override public void addPartyMember(String id)     { delegate.addPartyMember(id); }
        @Override public void removePartyMember(String id)  { delegate.removePartyMember(id); }
        @Override public void navigateTo(int s)              { delegate.navigateTo(s); }
        @Override public int currentSection()                { return delegate.currentSection(); }
        @Override public void showMessage(String m)          { delegate.showMessage(m); }
        @Override public void addChoice(String t, int s)     { delegate.addChoice(t, s); }
        @Override public void hideChoice(String id)          { delegate.hideChoice(id); }
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
