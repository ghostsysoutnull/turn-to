package com.tas.neo.scripting;

import com.tas.neo.combat.CombatSystemRegistry;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.engine.HookDispatcher;
import com.tas.neo.engine.GameState;
import com.tas.neo.engine.SectionHook;
import com.tas.neo.io.RecordingOutput;
import com.tas.neo.io.ScriptedInput;
import com.tas.neo.mechanics.Dice;
import com.tas.neo.mechanics.FixedDice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
}
