package com.tas.neo.scripting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@code AdventureScriptState}.
 *
 * <p>Covers the test strategy rows from {@code docs/design/07-scripting-engine.md}:
 * <ul>
 *   <li>set/get round-trips for String, int, boolean</li>
 *   <li>get returns null for absent key</li>
 *   <li>has() works correctly</li>
 * </ul>
 */
class AdventureScriptStateTest {

    private AdventureScriptState state;

    @BeforeEach
    void setUp() {
        state = new AdventureScriptState();
    }

    @Test
    void string_value_round_trips() {
        state.set("name", "Theron");

        assertThat(state.get("name"))
            .as("String value stored via set(String,String) must be retrievable via get")
            .isEqualTo("Theron");
    }

    @Test
    void int_value_round_trips() {
        state.set("score", 42);

        assertThat(state.get("score"))
            .as("int value stored via set(String,int) must be retrievable as Integer via get")
            .isEqualTo(42);
    }

    @Test
    void boolean_value_true_round_trips() {
        state.set("visited", true);

        assertThat(state.get("visited"))
            .as("boolean true stored via set(String,boolean) must be retrievable via get")
            .isEqualTo(true);
    }

    @Test
    void boolean_value_false_round_trips() {
        state.set("completed", false);

        assertThat(state.get("completed"))
            .as("boolean false stored via set(String,boolean) must be retrievable via get")
            .isEqualTo(false);
    }

    @Test
    void get_returns_null_for_absent_key() {
        assertThat(state.get("nonexistent"))
            .as("get must return null when the key has never been set")
            .isNull();
    }

    @Test
    void has_returns_true_after_set() {
        state.set("flag", true);

        assertThat(state.has("flag"))
            .as("has() must return true after a key has been set")
            .isTrue();
    }

    @Test
    void has_returns_false_for_absent_key() {
        assertThat(state.has("missing"))
            .as("has() must return false when the key has never been set")
            .isFalse();
    }

    @Test
    void set_overwrites_previous_value_for_same_key() {
        state.set("count", 1);
        state.set("count", 99);

        assertThat(state.get("count"))
            .as("second set on same key must overwrite the previous value")
            .isEqualTo(99);
    }

    @Test
    void overwrite_string_with_int_replaces_value() {
        state.set("key", "old");
        state.set("key", 7);

        assertThat(state.get("key"))
            .as("a key originally set as String can be overwritten with an int")
            .isEqualTo(7);
    }

    @Test
    void independent_keys_do_not_interfere() {
        state.set("a", "alpha");
        state.set("b", 2);
        state.set("c", true);

        assertThat(state.get("a")).isEqualTo("alpha");
        assertThat(state.get("b")).isEqualTo(2);
        assertThat(state.get("c")).isEqualTo(true);
    }

    @Test
    void get_returns_integer_not_long_for_int_value() {
        state.set("num", 5);

        Object value = state.get("num");
        assertThat(value)
            .as("int set value must be stored as Integer, not Long or other numeric type")
            .isInstanceOf(Integer.class);
    }

    @Test
    void get_returns_boolean_type_for_boolean_value() {
        state.set("flag", false);

        Object value = state.get("flag");
        assertThat(value)
            .as("boolean set value must be stored as Boolean")
            .isInstanceOf(Boolean.class);
    }
}
