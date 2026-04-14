package com.tas.neo.domain.log;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionLogTest {

    @Test
    void records_adventureId_path_events_errors_result_and_stepsCount() {
        NavigationEntry entry = new NavigationEntry("section:1", "section:2", "Onwards");

        SessionLog log = new SessionLog(
            "the-warlock",
            List.of(entry),
            List.of(),
            List.of(),
            "VICTORY",
            1
        );

        assertThat(log.adventureId()).isEqualTo("the-warlock");
        assertThat(log.path()).containsExactly(entry);
        assertThat(log.events()).isEmpty();
        assertThat(log.errors()).isEmpty();
        assertThat(log.result()).isEqualTo("VICTORY");
        assertThat(log.stepsCount()).isEqualTo(1);
    }

    @Test
    void allows_game_over_and_abandoned_results() {
        SessionLog over = new SessionLog("a", List.of(), List.of(), List.of(), "GAME_OVER", 0);
        SessionLog abandoned = new SessionLog("a", List.of(), List.of(), List.of(), "ABANDONED", 0);

        assertThat(over.result()).isEqualTo("GAME_OVER");
        assertThat(abandoned.result()).isEqualTo("ABANDONED");
    }
}
