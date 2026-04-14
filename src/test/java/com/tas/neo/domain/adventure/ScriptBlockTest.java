package com.tas.neo.domain.adventure;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptBlockTest {

    @Test
    void empty_returns_block_with_no_hooks() {
        ScriptBlock block = ScriptBlock.empty();

        assertThat(block.get("onEnter")).isEmpty();
        assertThat(block.get("onChoices")).isEmpty();
    }

    @Test
    void get_returns_script_when_hook_present() {
        ScriptBlock block = new ScriptBlock(Map.of("onEnter", "ctx.showMessage('Hi')"));

        Optional<String> script = block.get("onEnter");

        assertThat(script).contains("ctx.showMessage('Hi')");
    }

    @Test
    void get_returns_empty_for_missing_hook() {
        ScriptBlock block = new ScriptBlock(Map.of("onEnter", "a"));

        assertThat(block.get("onExit")).isEmpty();
    }

    @Test
    void carries_all_provided_hooks() {
        ScriptBlock block = new ScriptBlock(Map.of(
            "onEnter", "a",
            "onExit", "b",
            "onChoices", "c"
        ));

        assertThat(block.hooks()).hasSize(3);
        assertThat(block.get("onEnter")).contains("a");
        assertThat(block.get("onExit")).contains("b");
        assertThat(block.get("onChoices")).contains("c");
    }
}
