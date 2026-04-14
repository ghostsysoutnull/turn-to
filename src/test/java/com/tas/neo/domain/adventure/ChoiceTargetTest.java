package com.tas.neo.domain.adventure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChoiceTargetTest {

    @Test
    void sectionTarget_records_section_number() {
        SectionTarget t = new SectionTarget(42);

        assertThat(t.sectionNumber()).isEqualTo(42);
        assertThat((ChoiceTarget) t).isInstanceOf(SectionTarget.class);
    }

    @Test
    void gridTarget_records_grid_and_cell_ids() {
        GridTarget t = new GridTarget("cave", "entry");

        assertThat(t.gridId()).isEqualTo("cave");
        assertThat(t.cellId()).isEqualTo("entry");
        assertThat((ChoiceTarget) t).isInstanceOf(GridTarget.class);
    }
}
