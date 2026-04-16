package com.tas.neo.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.adventure.event.CombatEvent;
import com.tas.neo.domain.adventure.event.GoldChangeEvent;
import com.tas.neo.domain.adventure.event.ItemAction;
import com.tas.neo.domain.adventure.event.ItemEvent;
import com.tas.neo.domain.adventure.event.LuckTestEvent;
import com.tas.neo.domain.adventure.event.NavigateEvent;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.adventure.event.SkillTestEvent;
import com.tas.neo.domain.combat.Creature;
import com.tas.neo.domain.DiceStatDefinition;
import com.tas.neo.domain.FixedStatDefinition;
import com.tas.neo.domain.StatDefinition;
import com.tas.neo.domain.party.PartyMemberDefinition;
import com.tas.neo.scripting.AdventureScriptState;
import com.tas.neo.scripting.LuaScriptEngine;
import com.tas.neo.scripting.ScriptEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Simulates a single playthrough of an adventure from start to termination.
 * Not thread-safe — one instance per run.
 */
public class RunSimulator {

    private final Adventure adventure;
    private final JsonNode rawJson;
    private final ScriptEngine scriptEngine;
    private final RunConfiguration config;
    private final Random random;

    // Chapter boundary detection: map from entry section → chapter id
    private final java.util.Map<Integer, String> chapterEntries = new java.util.HashMap<>();

    // Conditioned choice tracking — populated during run()
    private final java.util.Set<NeverSelectedChoice> conditionedAvailable = new java.util.LinkedHashSet<>();
    private final java.util.Set<NeverSelectedChoice> conditionedSelected  = new java.util.LinkedHashSet<>();

    // Warnings accumulated during run()
    private final List<String> runWarnings = new ArrayList<>();

    public RunSimulator(Adventure adventure, JsonNode rawJson, ScriptEngine scriptEngine,
                        RunConfiguration config, Random random) {
        this.adventure = adventure;
        this.rawJson = rawJson;
        this.scriptEngine = scriptEngine;
        this.config = config;
        this.random = random;
        buildChapterIndex();
    }

    private void buildChapterIndex() {
        JsonNode chapters = rawJson.path("chapters");
        if (!chapters.isArray()) return;

        for (JsonNode ch : chapters) {
            String id = ch.path("id").asText();
            JsonNode entryGate = ch.path("gates").path("entryGate");

            if (entryGate.isMissingNode() || entryGate.isNull()) {
                // First chapter — snapshot at the adventure's start section
                chapterEntries.put(adventure.startSection(), id);
            } else {
                // Primary entry section
                int primary = entryGate.path("entrySection").asInt(-1);
                if (primary > 0) chapterEntries.put(primary, id);

                // Branch entry sections (ch3-style multi-entry)
                JsonNode branches = entryGate.path("branches");
                if (branches.isArray()) {
                    for (JsonNode branch : branches) {
                        int branchEntry = branch.path("entrySection").asInt(-1);
                        if (branchEntry > 0) chapterEntries.put(branchEntry, id);
                    }
                }
            }
        }
    }

    public RunResult run() {
        SimulatedGameState state = new SimulatedGameState();

        // Roll initial player stats from adventure definitions, then apply fixed overrides
        for (Map.Entry<String, StatDefinition> entry : adventure.playerStats().entrySet()) {
            int value = switch (entry.getValue()) {
                case DiceStatDefinition d -> d.resolveInitial(config.dice());
                case FixedStatDefinition f -> f.resolveInitial(config.dice());
            };
            state.setStat(entry.getKey(), value);
        }
        config.fixedSkill().ifPresent(v -> state.setStat("SKILL", v));
        config.fixedStamina().ifPresent(v -> state.setStat("STAMINA", v));

        state.modifyGold(adventure.initialGold());

        // Initialise party member states from definitions
        for (PartyMemberDefinition def : adventure.partyMemberDefinitions()) {
            state.initPartyMember(def.id(), def.initialState());
        }

        // Execute adventure-level lifecycle scripts (initialises state variables)
        SimulatedScriptContext initCtx = new SimulatedScriptContext(state);
        executeScript(adventure.scripts().get("onLoad"), initCtx, state);
        executeScript(adventure.scripts().get("onStart"), initCtx, state);

        int currentSection = adventure.startSection();

        while (true) {
            // Cycle detection
            state.recordVisit(currentSection);
            if (state.visitCount(currentSection) > config.maxVisitsPerSection()) {
                return new RunResult(RunOutcome.CYCLE, currentSection,
                    state.sectionsVisited(), state.chapterSnapshots(), List.copyOf(runWarnings));
            }

            // Chapter snapshot on entry
            if (chapterEntries.containsKey(currentSection)) {
                state.recordChapterSnapshot(chapterEntries.get(currentSection), currentSection);
            }

            Section section = adventure.getSection(currentSection);

            // Termination sections
            if (section.type() == SectionType.VICTORY) {
                return new RunResult(RunOutcome.VICTORY, currentSection,
                    state.sectionsVisited(), state.chapterSnapshots(), List.copyOf(runWarnings));
            }
            if (section.type() == SectionType.INSTANT_DEATH) {
                return new RunResult(RunOutcome.INSTANT_DEATH, currentSection,
                    state.sectionsVisited(), state.chapterSnapshots(), List.copyOf(runWarnings));
            }

            // Run onEnter script first — it may override navigation
            SimulatedScriptContext scriptCtx = new SimulatedScriptContext(state);
            scriptCtx.setCurrentSection(currentSection);
            executeScript(section.scripts().get("onEnter"), scriptCtx, state);

            // onEnter navigateTo overrides all other navigation
            if (scriptCtx.navigationTarget().isPresent()) {
                currentSection = scriptCtx.navigationTarget().getAsInt();
                continue;
            }

            // Process events — may navigate immediately
            Integer eventNavTarget = processEvents(section, state);
            if (eventNavTarget != null) {
                currentSection = eventNavTarget;
                continue;
            }

            // Collect available choices (static + dynamic from script)
            List<Choice> available = availableChoices(section, state, scriptCtx);

            if (available.isEmpty()) {
                List<String> allWarnings = new ArrayList<>(runWarnings);
                allWarnings.addAll(scriptCtx.warnings());
                return new RunResult(RunOutcome.STUCK, currentSection,
                    state.sectionsVisited(), state.chapterSnapshots(), allWarnings);
            }

            // Select a choice
            int idx = config.choiceSelector().select(available, state, random);
            Choice chosen = available.get(idx);

            // Track conditioned choices: record all that were available, and which was selected
            for (Choice c : available) {
                if (c.condition().isPresent()) {
                    conditionedAvailable.add(new NeverSelectedChoice(currentSection, c.text()));
                }
            }
            if (chosen.condition().isPresent()) {
                conditionedSelected.add(new NeverSelectedChoice(currentSection, chosen.text()));
            }

            if (chosen.target() instanceof SectionTarget t) {
                currentSection = t.sectionNumber();
            } else if (chosen.target() instanceof com.tas.neo.domain.adventure.GridTarget) {
                // Grid navigation is not simulated — record as GRID_ENTRY, not STUCK
                return new RunResult(RunOutcome.GRID_ENTRY, currentSection,
                    state.sectionsVisited(), state.chapterSnapshots(), List.of());
            } else {
                // SystemChoiceTarget or other — treat as stuck
                return new RunResult(RunOutcome.STUCK, currentSection,
                    state.sectionsVisited(), state.chapterSnapshots(), List.of());
            }
        }
    }

    /**
     * Processes section events in order. Returns the target section if an event forces
     * navigation (NavigateEvent, SkillTestEvent, LuckTestEvent), or null to continue
     * with choice selection.
     */
    private Integer processEvents(Section section, SimulatedGameState state) {
        for (SectionEvent event : section.events()) {
            switch (event) {
                case ItemEvent e -> {
                    if (e.action() == ItemAction.GAIN) {
                        state.addItem(e.itemName());
                    } else {
                        state.removeItem(e.itemName());
                    }
                }
                case GoldChangeEvent e -> state.modifyGold(e.delta());
                case NavigateEvent e -> { return e.targetSection(); }
                case SkillTestEvent e -> {
                    int roll = config.dice().roll2d6();
                    int skill = state.stat("SKILL");
                    return roll <= skill ? e.successSection() : e.failSection();
                }
                case LuckTestEvent e -> {
                    int roll = config.dice().roll2d6();
                    int luck = state.stat("LUCK");
                    return roll <= luck ? e.successSection() : e.failSection();
                }
                case CombatEvent e -> {
                    if (e.successSection() > 0 || e.failureSection() > 0) {
                        return simulateCombat(e, state);
                    }
                    // Combat with no explicit sections (script-driven) — fall through to choices
                }
                default -> { /* StatChangeEvent and others — skip in simulation */ }
            }
        }
        return null;
    }

    /**
     * Simulates a combat event by rolling dice per round until one side's stamina drops.
     * Returns the successSection or failureSection accordingly.
     */
    private int simulateCombat(CombatEvent event, SimulatedGameState state) {
        int playerSkill   = state.stat("SKILL");
        int playerStamina = state.stat("STAMINA");

        // Take the first opponent (multi-opponent combat simplified to sequential)
        List<Creature> opponents = event.opponents();
        if (opponents.isEmpty()) {
            // No opponent defined — coin flip
            return config.dice().roll(2) == 1 ? event.successSection() : event.failureSection();
        }

        Creature opponent = opponents.get(0);
        int oppSkill = opponent.skill();
        int oppStamina = opponent.stamina();

        int maxRounds = 50;
        for (int round = 0; round < maxRounds; round++) {
            int playerAttack = config.dice().roll2d6() + playerSkill;
            int oppAttack = config.dice().roll2d6() + oppSkill;

            if (playerAttack > oppAttack) {
                oppStamina -= 2;
            } else if (oppAttack > playerAttack) {
                playerStamina -= 2;
            }
            // Tie: no damage

            if (oppStamina <= 0) return event.successSection();
            if (playerStamina <= 0) return event.failureSection();
        }

        // Timeout: treat as player win (simulation safety net)
        return event.successSection();
    }

    private void executeScript(Optional<String> maybeScript, SimulatedScriptContext ctx,
                               SimulatedGameState state) {
        maybeScript.ifPresent(script -> {
            try {
                if (scriptEngine instanceof LuaScriptEngine lua) {
                    lua.execute(script, ctx, state.scriptState());
                } else {
                    scriptEngine.execute(script, ctx);
                }
            } catch (Exception e) {
                // Script errors are non-fatal in simulation
            }
        });
    }

    /** Conditioned choices that were available (condition met) in this run. Call after run(). */
    public java.util.Set<NeverSelectedChoice> conditionedChoicesAvailable() {
        return java.util.Collections.unmodifiableSet(conditionedAvailable);
    }

    /** Conditioned choices that were selected in this run. Call after run(). */
    public java.util.Set<NeverSelectedChoice> conditionedChoicesSelected() {
        return java.util.Collections.unmodifiableSet(conditionedSelected);
    }

    private List<Choice> availableChoices(Section section, SimulatedGameState state,
                                          SimulatedScriptContext scriptCtx) {
        List<Choice> all = new ArrayList<>(section.choices());
        all.addAll(scriptCtx.dynamicChoices());

        return all.stream()
            .filter(c -> c.condition().isEmpty()
                || ConditionEvaluator.evaluate(c.condition().get(), state))
            .filter(c -> c.target() instanceof SectionTarget
                      || c.target() instanceof com.tas.neo.domain.adventure.GridTarget)
            .toList();
    }
}
