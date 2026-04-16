package com.tas.neo.domain.log;

import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.location.Cell;
import com.tas.neo.domain.location.Grid;
import com.tas.neo.domain.player.Attribute;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.domain.player.Player;
import com.tas.neo.domain.item.Inventory;
import com.tas.neo.engine.GameState;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Test
    void location_includes_grid_id_and_coordinates_when_in_grid() {
        GameState state = stateInGrid("vault-dungeon", 2, 3, 0);

        PlayerSnapshot snap = PlayerSnapshot.of(state);

        assertThat(snap.location())
            .as("location must include grid id and x,y,z coordinates when player is in a grid")
            .isEqualTo("grid:vault-dungeon:2,3,0");
    }

    @Test
    void location_uses_section_format_when_not_in_grid() {
        GameState state = new GameState();
        state.setPlayer(defaultPlayer());
        com.tas.neo.domain.adventure.Section section = new com.tas.neo.domain.adventure.Section(
            7, "Room.", List.of(), List.of(),
            com.tas.neo.domain.adventure.SectionType.NORMAL,
            ScriptBlock.empty());
        state.navigateTo(section);

        PlayerSnapshot snap = PlayerSnapshot.of(state);

        assertThat(snap.location())
            .as("location must use 'section:<n>' format when player is at a section")
            .isEqualTo("section:7");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static GameState stateInGrid(String gridId, int x, int y, int z) {
        Cell cell = new Cell(Optional.empty(), x, y, z, "",
            List.of(), ScriptBlock.empty(), Map.of(), List.of());
        Grid grid = new Grid(gridId, 10, 10, 1, Map.of(x + "," + y + "," + z, cell));
        GameState state = new GameState();
        state.setPlayer(defaultPlayer());
        state.navigateToCell(grid, cell);
        return state;
    }

    private static Player defaultPlayer() {
        EnumMap<AttributeType, Attribute> attrs = new EnumMap<>(AttributeType.class);
        attrs.put(AttributeType.SKILL,   new Attribute(AttributeType.SKILL,   10, 10));
        attrs.put(AttributeType.STAMINA, new Attribute(AttributeType.STAMINA, 20, 20));
        attrs.put(AttributeType.LUCK,    new Attribute(AttributeType.LUCK,    10, 10));
        return new Player(attrs, new Inventory(), 0, 0);
    }
}
