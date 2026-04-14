package com.tas.neo.domain.adventure;

import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.adventure.event.StatChangeEvent;
import com.tas.neo.domain.player.AttributeType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SectionTest {

    @Test
    void exposes_number_narrative_events_choices_type_and_scripts() {
        SectionEvent event = new StatChangeEvent(AttributeType.STAMINA, -1);
        Choice choice = new Choice("Onwards", new SectionTarget(2),
            Optional.empty(), Optional.empty());
        ScriptBlock scripts = new ScriptBlock(java.util.Map.of("onEnter", "print('hi')"));

        Section section = new Section(
            1,
            "The entrance.",
            List.of(event),
            List.of(choice),
            SectionType.NORMAL,
            scripts
        );

        assertThat(section.number()).isEqualTo(1);
        assertThat(section.narrative()).isEqualTo("The entrance.");
        assertThat(section.events()).containsExactly(event);
        assertThat(section.choices()).containsExactly(choice);
        assertThat(section.type()).isEqualTo(SectionType.NORMAL);
        assertThat(section.scripts()).isEqualTo(scripts);
    }

    @Test
    void empty_events_and_choices_allowed() {
        Section section = new Section(
            99,
            "Victory!",
            List.of(),
            List.of(),
            SectionType.VICTORY,
            ScriptBlock.empty()
        );

        assertThat(section.events()).isEmpty();
        assertThat(section.choices()).isEmpty();
        assertThat(section.type()).isEqualTo(SectionType.VICTORY);
    }
}
