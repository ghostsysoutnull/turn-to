package com.tas.neo.domain.location;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.adventure.event.StatChangeEvent;
import com.tas.neo.domain.player.AttributeType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CellTest {

    @Test
    void exposes_id_coordinates_narrative_events_scripts_passages_and_choices() {
        SectionEvent event = new StatChangeEvent(AttributeType.STAMINA, -1);
        Choice choice = new Choice("Search the room", new SectionTarget(5),
            Optional.empty(), Optional.empty());
        Passage passage = new Passage(Optional.empty(), Optional.empty(), Optional.empty());

        Cell cell = new Cell(
            Optional.of("entrance"),
            1, 2, 0,
            "Cold air rushes past.",
            List.of(event),
            ScriptBlock.empty(),
            Map.of(Direction.EAST, passage),
            List.of(choice)
        );

        assertThat(cell.id()).contains("entrance");
        assertThat(cell.x()).isEqualTo(1);
        assertThat(cell.y()).isEqualTo(2);
        assertThat(cell.z()).isZero();
        assertThat(cell.narrative()).isEqualTo("Cold air rushes past.");
        assertThat(cell.events()).containsExactly(event);
        assertThat(cell.passages()).containsEntry(Direction.EAST, passage);
        assertThat(cell.choices()).containsExactly(choice);
    }

    @Test
    void cell_with_empty_id_is_unnamed() {
        Cell cell = new Cell(
            Optional.empty(),
            0, 0, 0,
            "A nameless corridor.",
            List.of(),
            ScriptBlock.empty(),
            Map.of(),
            List.of()
        );

        assertThat(cell.id()).isEmpty();
    }
}
