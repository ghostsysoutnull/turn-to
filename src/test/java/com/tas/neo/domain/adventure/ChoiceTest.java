package com.tas.neo.domain.adventure;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChoiceTest {

    @Test
    void factory_to_text_and_target_sets_no_condition_or_id() {
        Choice c = Choice.to("Enter the mountain", new SectionTarget(2));

        assertThat(c.text()).isEqualTo("Enter the mountain");
        assertThat(c.target()).isInstanceOf(SectionTarget.class);
        assertThat(((SectionTarget) c.target()).sectionNumber()).isEqualTo(2);
        assertThat(c.condition()).isEmpty();
        assertThat(c.id()).isEmpty();
    }

    @Test
    void factory_to_with_grid_target() {
        Choice c = Choice.to("Explore caves", new GridTarget("cave-network", "entrance"));

        assertThat(c.target()).isInstanceOf(GridTarget.class);
        assertThat(((GridTarget) c.target()).gridId()).isEqualTo("cave-network");
        assertThat(((GridTarget) c.target()).cellId()).isEqualTo("entrance");
    }

    @Test
    void factory_to_with_condition_sets_condition_and_no_id() {
        Condition condition = new HasItemCondition("Key");
        Choice c = Choice.to("Unlock", new SectionTarget(3), condition);

        assertThat(c.condition()).contains(condition);
        assertThat(c.id()).isEmpty();
    }

    @Test
    void factory_to_with_condition_and_id_sets_all_fields() {
        Condition condition = new HasItemCondition("Key");
        Choice c = Choice.to("Flee", new SectionTarget(5), condition, "flee");

        assertThat(c.condition()).contains(condition);
        assertThat(c.id()).contains("flee");
    }

    @Test
    void canonical_constructor_equivalent_to_factory() {
        Choice via_factory = Choice.to("Go north", new SectionTarget(4));
        Choice via_constructor = new Choice(
            "Go north", new SectionTarget(4), Optional.empty(), Optional.empty()
        );

        assertThat(via_factory).isEqualTo(via_constructor);
    }
}
