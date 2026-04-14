package com.tas.neo.scripting;

/**
 * Test double implementing {@link ScriptEngine} that executes nothing. Used in engine
 * and combat tests to avoid LuaJ overhead.
 *
 * <p>Defined in {@code docs/design/06-testability.md}.
 */
public class NoOpScriptEngine implements ScriptEngine {

    @Override
    public void execute(String script, ScriptContext context) {
        // no-op
    }
}
