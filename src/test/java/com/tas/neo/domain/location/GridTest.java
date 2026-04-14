package com.tas.neo.domain.location;

import com.tas.neo.domain.adventure.ScriptBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GridTest {

    private static Cell cell(Optional<String> id, int x, int y, int z) {
        return new Cell(
            id, x, y, z,
            "Cell at " + x + "," + y + "," + z,
            List.of(),
            ScriptBlock.empty(),
            Map.of(),
            List.of()
        );
    }

    private static Grid newGrid(Cell... cells) {
        Map<String, Cell> map = new LinkedHashMap<>();
        for (int i = 0; i < cells.length; i++) {
            map.put("c" + i, cells[i]);
        }
        return new Grid("cave", 3, 3, 1, map);
    }

    @Test
    void exposes_id_and_dimensions() {
        Grid grid = newGrid();

        assertThat(grid.id()).isEqualTo("cave");
        assertThat(grid.width()).isEqualTo(3);
        assertThat(grid.height()).isEqualTo(3);
        assertThat(grid.floors()).isEqualTo(1);
    }

    @Test
    void getCell_returns_cell_at_given_coordinates() {
        Cell entrance = cell(Optional.of("entrance"), 0, 0, 0);
        Cell inner = cell(Optional.empty(), 1, 0, 0);
        Grid grid = newGrid(entrance, inner);

        assertThat(grid.getCell(0, 0, 0)).contains(entrance);
        assertThat(grid.getCell(1, 0, 0)).contains(inner);
    }

    @Test
    void getCell_returns_empty_for_unoccupied_coordinates() {
        Cell entrance = cell(Optional.of("entrance"), 0, 0, 0);
        Grid grid = newGrid(entrance);

        assertThat(grid.getCell(2, 2, 0)).isEmpty();
    }

    @Test
    void getCellById_resolves_named_cells() {
        Cell entrance = cell(Optional.of("entrance"), 0, 0, 0);
        Grid grid = newGrid(entrance);

        assertThat(grid.getCellById("entrance")).contains(entrance);
    }

    @Test
    void getCellById_returns_empty_for_unknown_id() {
        Cell entrance = cell(Optional.of("entrance"), 0, 0, 0);
        Grid grid = newGrid(entrance);

        assertThat(grid.getCellById("unknown")).isEmpty();
    }

    @Test
    void cells_returns_all_cells() {
        Cell a = cell(Optional.of("a"), 0, 0, 0);
        Cell b = cell(Optional.empty(), 1, 0, 0);
        Grid grid = newGrid(a, b);

        assertThat(grid.cells()).containsExactlyInAnyOrder(a, b);
    }
}
