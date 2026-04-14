package com.tas.neo.domain.log;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerSnapshotTest {

    @Test
    void records_all_fields() {
        PlayerSnapshot snap = new PlayerSnapshot(
            "section:42",
            8,
            9,
            5,
            3,
            List.of("Iron Key", "Torch")
        );

        assertThat(snap.location()).isEqualTo("section:42");
        assertThat(snap.stamina()).isEqualTo(8);
        assertThat(snap.skill()).isEqualTo(9);
        assertThat(snap.luck()).isEqualTo(5);
        assertThat(snap.gold()).isEqualTo(3);
        assertThat(snap.inventory()).containsExactly("Iron Key", "Torch");
    }

    @Test
    void allows_empty_inventory() {
        PlayerSnapshot snap = new PlayerSnapshot("section:1", 10, 10, 10, 0, List.of());

        assertThat(snap.inventory()).isEmpty();
    }
}
