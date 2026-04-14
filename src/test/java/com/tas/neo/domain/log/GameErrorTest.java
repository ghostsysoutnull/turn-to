package com.tas.neo.domain.log;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameErrorTest {

    @Test
    void records_source_type_message_and_snapshot() {
        PlayerSnapshot snap = new PlayerSnapshot("section:42", 8, 9, 5, 3, List.of("Torch"));
        GameError err = new GameError(
            "section-42-onEnter",
            "ScriptError",
            "attempt to index a nil value (global 'doorState')",
            snap
        );

        assertThat(err.source()).isEqualTo("section-42-onEnter");
        assertThat(err.type()).isEqualTo("ScriptError");
        assertThat(err.message()).contains("nil value");
        assertThat(err.snapshot()).isEqualTo(snap);
    }
}
