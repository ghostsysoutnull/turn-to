package com.tas.neo.domain.log;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationEntryTest {

    @Test
    void records_from_to_and_via() {
        NavigationEntry entry = new NavigationEntry("section:1", "section:2", "Enter the mountain");

        assertThat(entry.from()).isEqualTo("section:1");
        assertThat(entry.to()).isEqualTo("section:2");
        assertThat(entry.via()).isEqualTo("Enter the mountain");
    }

    @Test
    void accepts_grid_cell_location_format() {
        NavigationEntry entry = new NavigationEntry(
            "grid:dungeon-level-1:0,0,0",
            "grid:dungeon-level-1:1,0,0",
            "Go east"
        );

        assertThat(entry.from()).isEqualTo("grid:dungeon-level-1:0,0,0");
        assertThat(entry.to()).isEqualTo("grid:dungeon-level-1:1,0,0");
    }
}
