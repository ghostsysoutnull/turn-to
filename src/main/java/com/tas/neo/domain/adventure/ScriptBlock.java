package com.tas.neo.domain.adventure;

import java.util.Map;
import java.util.Optional;

public record ScriptBlock(Map<String, String> hooks) {

    private static final ScriptBlock EMPTY = new ScriptBlock(Map.of());

    public ScriptBlock {
        hooks = Map.copyOf(hooks);
    }

    public Optional<String> get(String hookName) {
        return Optional.ofNullable(hooks.get(hookName));
    }

    public static ScriptBlock empty() {
        return EMPTY;
    }
}
