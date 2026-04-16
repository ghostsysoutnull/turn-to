package com.tas.neo.scripting;

public interface ScriptEngine {
    void execute(String script, ScriptContext context) throws ScriptException;

    default void execute(String script, ScriptContext context, AdventureScriptState scriptState)
            throws ScriptException {
        execute(script, context);
    }
}
