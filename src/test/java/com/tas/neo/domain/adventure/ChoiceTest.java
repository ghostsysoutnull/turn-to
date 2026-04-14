package com.tas.neo.domain.adventure;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChoiceTest {

    @Test
    void records_text_and_section_target() {
        Choice c = new Choice(
            "Enter the mountain",
            new SectionTarget(2),
            Optional.empty(),
            Optional.empty()
        );

        assertThat(c.text()).isEqualTo("Enter the mountain");
        assertThat(c.target()).isInstanceOf(SectionTarget.class);
        assertThat(((SectionTarget) c.target()).sectionNumber()).isEqualTo(2);
    }

    @Test
    void records_text_and_grid_target() {
        Choice c = new Choice(
            "Explore caves",
            new GridTarget("cave-network", "entrance"),
            Optional.empty(),
            Optional.empty()
        );

        assertThat(c.target()).isInstanceOf(GridTarget.class);
        assertThat(((GridTarget) c.target()).gridId()).isEqualTo("cave-network");
        assertThat(((GridTarget) c.target()).cellId()).isEqualTo("entrance");
    }

    @Test
    void carries_optional_condition() {
        Condition condition = new HasItemCondition("Key");
        Choice c = new Choice(
            "Unlock", new SectionTarget(3),
            Optional.of(condition), Optional.empty()
        );

        assertThat(c.condition()).contains(condition);
    }

    @Test
    void carries_optional_id_for_onChoices_hide() {
        Choice c = new Choice(
            "Flee", new SectionTarget(3),
            Optional.empty(), Optional.of("flee")
        );

        assertThat(c.id()).contains("flee");
    }
}
