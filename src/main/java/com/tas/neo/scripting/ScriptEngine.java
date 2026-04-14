package com.tas.neo.scripting;

public interface ScriptEngine {
    void execute(String script, ScriptContext context) throws ScriptException;
}
