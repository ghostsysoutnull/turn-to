package com.tas.neo.scripting;

import com.tas.neo.combat.CombatSystemRegistry;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.adventure.event.LuckTestEvent;
import com.tas.neo.domain.adventure.event.SkillTestEvent;
import com.tas.neo.domain.item.Inventory;
import com.tas.neo.domain.player.Attribute;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.domain.player.Player;
import com.tas.neo.engine.HookDispatcher;
import com.tas.neo.engine.GameState;
import com.tas.neo.engine.SectionHook;
import com.tas.neo.io.OutputEvent;
import com.tas.neo.io.RecordingOutput;
import com.tas.neo.io.ScriptedInput;
import com.tas.neo.domain.Dice;
import com.tas.neo.mechanics.FixedDice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HookDispatcher}.
 *
 * <p>Covers the test strategy rows from {@code docs/design/07-scripting-engine.md}:
 * <ul>
 *   <li>HookDispatcher is a no-op on an empty ScriptBlock — ScriptEngine.execute never called</li>
 *   <li>HookDispatcher fires the correct hook for a section's ScriptBlock</li>
 * </ul>
 */
class HookDispatcherTest {

    /**
     * A {@link ScriptEngine} that counts how many times {@code execute} was invoked
     * and records the most recent script string. Used to verify dispatch behaviour.
     */
    private static class CountingScriptEngine implements ScriptEngine {
        int executeCount = 0;
        String lastScript = null;

        @Override
        public void execute(String script, ScriptContext context) {
            executeCount++;
            lastScript = script;
        }
    }

    private CountingScriptEngine scriptEngine;
    private RecordingOutput output;
    private ScriptedInput input;
    private GameState gameState;
    private AdventureScriptState scriptState;
    private Dice dice;

    @BeforeEach
    void setUp() {
        scriptEngine = new CountingScriptEngine();
        output = new RecordingOutput();
        input = new ScriptedInput();
        gameState = new GameState();
        scriptState = new AdventureScriptState();
        dice = new FixedDice(3);
    }

    private HookDispatcher dispatcher() {
        CombatSystemRegistry combatRegistry = new CombatSystemRegistry() {
            @Override
            public com.tas.neo.combat.CombatSystem get(String id) {
                throw new IllegalArgumentException("No combat systems in test");
            }

            @Override
            public boolean has(String id) {
                return false;
            }
        };
        return new HookDispatcher(scriptEngine, input, output, gameState, scriptState,
                                  combatRegistry, dice);
    }

    private Section sectionWithOnEnter(int number, String script) {
        ScriptBlock scripts = new ScriptBlock(Map.of("onEnter", script));
        return new Section(number, "narrative", List.of(), List.of(), SectionType.NORMAL, scripts);
    }

    private Section sectionWithOnChoices(int number, String script) {
        ScriptBlock scripts = new ScriptBlock(Map.of("onChoices", script));
        return new Section(number, "narrative", List.of(), List.of(), SectionType.NORMAL, scripts);
    }

    private Section sectionWithNoScripts(int number) {
        return new Section(number, "narrative", List.of(), List.of(), SectionType.NORMAL,
                           ScriptBlock.empty());
    }

    // -----------------------------------------------------------------------
    // No-op on empty ScriptBlock
    // -----------------------------------------------------------------------

    @Test
    void fireSectionHook_does_not_call_execute_when_scriptblock_is_empty() {
        Section section = sectionWithNoScripts(1);
        HookDispatcher d = dispatcher();

        d.fireSectionHook(SectionHook.ON_ENTER, section, new ArrayList<>());

        assertThat(scriptEngine.executeCount)
            .as("ScriptEngine.execute must not be called when the section has no scripts")
            .isEqualTo(0);
    }

    @Test
    void fireSectionHook_does_not_call_execute_when_hook_absent_from_scriptblock() {
        // Section has onChoices but not onEnter
        Section section = sectionWithOnChoices(1, "ctx.showMessage('choices')");
        HookDispatcher d = dispatcher();

        d.fireSectionHook(SectionHook.ON_ENTER, section, new ArrayList<>());

        assertThat(scriptEngine.executeCount)
            .as("ScriptEngine.execute must not be called when the requested hook is absent")
            .isEqualTo(0);
    }

    // -----------------------------------------------------------------------
    // Fires correct hook
    // -----------------------------------------------------------------------

    @Test
    void fireSectionHook_onEnter_calls_execute_with_correct_script() {
        Section section = sectionWithOnEnter(1, "ctx.showMessage('entered')");
        HookDispatcher d = dispatcher();

        d.fireSectionHook(SectionHook.ON_ENTER, section, new ArrayList<>());

        assertThat(scriptEngine.executeCount)
            .as("ScriptEngine.execute must be called exactly once for an onEnter hook")
            .isEqualTo(1);
        assertThat(scriptEngine.lastScript)
            .as("The script passed to execute must be the onEnter script from the section")
            .isEqualTo("ctx.showMessage('entered')");
    }

    @Test
    void fireSectionHook_onChoices_calls_execute_with_correct_script() {
        Section section = sectionWithOnChoices(2, "ctx.hideChoice('skip')");
        HookDispatcher d = dispatcher();

        d.fireSectionHook(SectionHook.ON_CHOICES, section, new ArrayList<>());

        assertThat(scriptEngine.executeCount)
            .as("ScriptEngine.execute must be called exactly once for an onChoices hook")
            .isEqualTo(1);
        assertThat(scriptEngine.lastScript)
            .as("The script passed to execute must be the onChoices script from the section")
            .isEqualTo("ctx.hideChoice('skip')");
    }

    @Test
    void fireSectionHook_onExit_calls_execute_with_correct_script() {
        ScriptBlock scripts = new ScriptBlock(Map.of("onExit", "ctx.showMessage('bye')"));
        Section section = new Section(3, "n", List.of(), List.of(), SectionType.NORMAL, scripts);
        HookDispatcher d = dispatcher();

        d.fireSectionHook(SectionHook.ON_EXIT, section, new ArrayList<>());

        assertThat(scriptEngine.executeCount)
            .as("ScriptEngine.execute must be called exactly once for an onExit hook")
            .isEqualTo(1);
        assertThat(scriptEngine.lastScript)
            .as("The script must be the onExit script from the section")
            .isEqualTo("ctx.showMessage('bye')");
    }

    @Test
    void fireSectionHook_called_twice_fires_execute_twice() {
        Section s1 = sectionWithOnEnter(1, "ctx.showMessage('s1')");
        Section s2 = sectionWithOnEnter(2, "ctx.showMessage('s2')");
        HookDispatcher d = dispatcher();

        d.fireSectionHook(SectionHook.ON_ENTER, s1, new ArrayList<>());
        d.fireSectionHook(SectionHook.ON_ENTER, s2, new ArrayList<>());

        assertThat(scriptEngine.executeCount)
            .as("ScriptEngine.execute must be called once per fireSectionHook call")
            .isEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // LuckTestEvent output
    // -----------------------------------------------------------------------

    @Test
    void processEvent_luckTest_emits_LuckTestShown_and_decrements_luck_when_roll_passes() {
        gameState.setPlayer(playerWith(10, 8, 20));
        HookDispatcher d = dispatcherWith(new FixedDice(3)); // roll2d6 = 6, luck = 8 → passed

        d.processEvent(new LuckTestEvent(10, 20));

        assertThat(output.events())
            .as("LuckTestShown(roll=6, luck=8, passed=true) must be recorded when roll ≤ luck")
            .contains(new OutputEvent.LuckTestShown(6, 8, true));
        assertThat(gameState.player().getLuck())
            .as("LUCK must be decremented by 1 after a luck test regardless of outcome")
            .isEqualTo(7);
    }

    @Test
    void processEvent_luckTest_emits_LuckTestShown_when_roll_fails() {
        gameState.setPlayer(playerWith(10, 8, 20));
        HookDispatcher d = dispatcherWith(new FixedDice(6)); // roll2d6 = 12, luck = 8 → failed

        d.processEvent(new LuckTestEvent(10, 20));

        assertThat(output.events())
            .as("LuckTestShown(roll=12, luck=8, passed=false) must be recorded when roll > luck")
            .contains(new OutputEvent.LuckTestShown(12, 8, false));
    }

    // -----------------------------------------------------------------------
    // SkillTestEvent output
    // -----------------------------------------------------------------------

    @Test
    void processEvent_skillTest_emits_SkillTestShown_and_leaves_skill_unchanged_when_roll_passes() {
        gameState.setPlayer(playerWith(10, 8, 20));
        HookDispatcher d = dispatcherWith(new FixedDice(3)); // roll2d6 = 6, skill = 10 → passed

        d.processEvent(new SkillTestEvent(10, 20));

        assertThat(output.events())
            .as("SkillTestShown(roll=6, skill=10, passed=true) must be recorded when roll ≤ skill")
            .contains(new OutputEvent.SkillTestShown(6, 10, true));
        assertThat(gameState.player().getSkill())
            .as("SKILL must not be modified after a skill test")
            .isEqualTo(10);
    }

    @Test
    void processEvent_skillTest_emits_SkillTestShown_when_roll_fails() {
        gameState.setPlayer(playerWith(10, 8, 20));
        HookDispatcher d = dispatcherWith(new FixedDice(6)); // roll2d6 = 12, skill = 10 → failed

        d.processEvent(new SkillTestEvent(10, 20));

        assertThat(output.events())
            .as("SkillTestShown(roll=12, skill=10, passed=false) must be recorded when roll > skill")
            .contains(new OutputEvent.SkillTestShown(12, 10, false));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private HookDispatcher dispatcherWith(Dice testDice) {
        CombatSystemRegistry combatRegistry = new CombatSystemRegistry() {
            @Override
            public com.tas.neo.combat.CombatSystem get(String id) {
                throw new IllegalArgumentException("No combat systems in test");
            }

            @Override
            public boolean has(String id) {
                return false;
            }
        };
        return new HookDispatcher(scriptEngine, input, output, gameState, scriptState,
                                  combatRegistry, testDice);
    }

    private Player playerWith(int skill, int luck, int stamina) {
        Map<AttributeType, Attribute> attrs = new EnumMap<>(AttributeType.class);
        attrs.put(AttributeType.SKILL,   new Attribute(AttributeType.SKILL,   skill,   skill));
        attrs.put(AttributeType.LUCK,    new Attribute(AttributeType.LUCK,    luck,    luck));
        attrs.put(AttributeType.STAMINA, new Attribute(AttributeType.STAMINA, stamina, stamina));
        return new Player(attrs, new Inventory(), 0, 0);
    }
}
