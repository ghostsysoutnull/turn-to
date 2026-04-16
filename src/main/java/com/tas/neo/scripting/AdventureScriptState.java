package com.tas.neo.scripting;

import java.util.HashMap;
import java.util.Map;

public class AdventureScriptState {

    private final Map<String, Object> store = new HashMap<>();

    public void set(String key, String value) {
        store.put(key, value);
    }

    public void set(String key, int value) {
        store.put(key, value);
    }

    public void set(String key, boolean value) {
        store.put(key, value);
    }

    /** Returns the stored value, or null if absent. */
    public Object get(String key) {
        return store.get(key);
    }

    public boolean has(String key) {
        return store.containsKey(key);
    }

    /** Returns an immutable snapshot of the current state variables. */
    public Map<String, Object> snapshot() {
        return Map.copyOf(store);
    }
}
