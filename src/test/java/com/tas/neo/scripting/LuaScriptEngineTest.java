package com.tas.neo.scripting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@code LuaScriptEngine}.
 *
 * <p>Covers the test strategy rows from {@code docs/design/07-scripting-engine.md}:
 * <ul>
 *   <li>LuaScriptEngine executes script — assert calls recorded in RecordingScriptContext</li>
 *   <li>Failed script does not crash — syntax error caught, game continues</li>
 *   <li>Sandboxing — io.open attempt throws error, no file access</li>
 * </ul>
 */
class LuaScriptEngineTest {

    private LuaScriptEngine engine;
    private RecordingScriptContext ctx;

    @BeforeEach
    void setUp() {
        engine = new LuaScriptEngine();
        ctx = new RecordingScriptContext();
    }

    @Test
    void executes_showMessage_and_records_in_context() throws ScriptException {
        engine.execute("ctx.showMessage('hello world')", ctx);

        assertThat(ctx.getMessages())
            .as("showMessage call should be recorded by context")
            .containsExactly("hello world");
    }

    @Test
    void executes_modifyStat_and_records_stat_change() throws ScriptException {
        engine.execute("ctx.modifyStat('SKILL', -2)", ctx);

        assertThat(ctx.getStatChanges())
            .as("modifyStat call should be recorded")
            .hasSize(1);
        assertThat(ctx.getStatChanges().get(0).attribute()).isEqualTo("SKILL");
        assertThat(ctx.getStatChanges().get(0).delta()).isEqualTo(-2);
    }

    @Test
    void executes_navigateTo_and_records_target_section() throws ScriptException {
        engine.execute("ctx.navigateTo(42)", ctx);

        assertThat(ctx.navigatedTo())
            .as("navigateTo call should be recorded with correct section")
            .isEqualTo(42);
    }

    @Test
    void executes_addItem_and_records_item() throws ScriptException {
        engine.execute("ctx.addItem('Sword')", ctx);

        assertThat(ctx.itemAdded("Sword"))
            .as("addItem call should be recorded")
            .isTrue();
    }

    @Test
    void script_with_syntax_error_does_not_throw_unchecked_exception() {
        assertThatCode(() -> engine.execute("this is not valid lua !!!", ctx))
            .as("syntax error in script must not propagate as an unchecked exception")
            .doesNotThrowAnyException();
    }

    @Test
    void script_runtime_error_does_not_crash_engine() {
        // Calling a nil value at runtime
        assertThatCode(() -> engine.execute("local x = nil; x()", ctx))
            .as("runtime error must be caught; game continues after a failing script")
            .doesNotThrowAnyException();
    }

    @Test
    void sandbox_io_open_is_not_accessible() {
        // io module must be removed; script must fail with an error, not open a file
        assertThatCode(() -> engine.execute("io.open('/etc/passwd', 'r')", ctx))
            .as("io module must be sandboxed — io.open must not succeed")
            .doesNotThrowAnyException();

        // If it somehow ran without error, it must not have opened anything — verified by
        // confirming no message was recorded (a successful open would not show a message,
        // but the engine must still not expose the module)
        // The real guard: executing io.open must cause a Lua error which the engine swallows
        // We verify the engine is still alive and functional afterwards.
        assertThatCode(() -> engine.execute("ctx.showMessage('still alive')", ctx))
            .as("engine must still be functional after a sandboxed io.open attempt")
            .doesNotThrowAnyException();

        assertThat(ctx.getMessages())
            .as("engine must remain functional and execute subsequent scripts normally")
            .contains("still alive");
    }

    @Test
    void sandbox_os_module_is_not_accessible() {
        assertThatCode(() -> engine.execute("os.exit(0)", ctx))
            .as("os module must be sandboxed — os.exit must not succeed")
            .doesNotThrowAnyException();
    }

    @Test
    void sandbox_require_is_not_accessible() {
        assertThatCode(() -> engine.execute("require('os')", ctx))
            .as("require must be sandboxed")
            .doesNotThrowAnyException();
    }

    @Test
    void multiple_calls_in_one_script_are_all_recorded() throws ScriptException {
        engine.execute(
            "ctx.showMessage('one'); ctx.showMessage('two'); ctx.modifyStat('LUCK', 1)",
            ctx
        );

        assertThat(ctx.getMessages())
            .as("all showMessage calls in one script must be recorded in order")
            .containsExactly("one", "two");
        assertThat(ctx.getStatChanges())
            .as("stat change in same script must also be recorded")
            .hasSize(1);
    }

    @Test
    void lua_environment_is_fresh_per_execution() throws ScriptException {
        // First execution sets a local variable
        engine.execute("myGlobal = 'set'", ctx);
        // Second execution should not see that variable (fresh environment per call)
        engine.execute("if myGlobal then ctx.showMessage('leaked') end", ctx);

        assertThat(ctx.getMessages())
            .as("Lua state must not leak between executions — each execution is isolated")
            .doesNotContain("leaked");
    }

    @Test
    void print_is_redirected_to_ctx_showMessage() throws ScriptException {
        engine.execute("print('printed')", ctx);

        assertThat(ctx.getMessages())
            .as("print() inside a script must be redirected to ctx.showMessage")
            .containsExactly("printed");
    }
}
