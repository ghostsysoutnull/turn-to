package com.tas.neo.domain.adventure.event;

import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.combat.Creature;
import com.tas.neo.domain.player.AttributeType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SectionEventTest {

    @Test
    void statChange_records_attribute_and_delta() {
        StatChangeEvent e = new StatChangeEvent(AttributeType.STAMINA, -2);

        assertThat(e.attribute()).isEqualTo(AttributeType.STAMINA);
        assertThat(e.delta()).isEqualTo(-2);
        assertThat((SectionEvent) e).isInstanceOf(StatChangeEvent.class);
    }

    @Test
    void itemEvent_records_name_action_and_quantity() {
        ItemEvent e = new ItemEvent("Arrow", ItemAction.GAIN, 10);

        assertThat(e.itemName()).isEqualTo("Arrow");
        assertThat(e.action()).isEqualTo(ItemAction.GAIN);
        assertThat(e.quantity()).isEqualTo(10);
    }

    @Test
    void luckTest_records_success_and_failure_sections() {
        LuckTestEvent e = new LuckTestEvent(10, 20);

        assertThat(e.successSection()).isEqualTo(10);
        assertThat(e.failSection()).isEqualTo(20);
    }

    @Test
    void skillTest_records_success_and_failure_sections() {
        SkillTestEvent e = new SkillTestEvent(10, 20);

        assertThat(e.successSection()).isEqualTo(10);
        assertThat(e.failSection()).isEqualTo(20);
    }

    @Test
    void navigate_records_target_section() {
        NavigateEvent e = new NavigateEvent(42);

        assertThat(e.targetSection()).isEqualTo(42);
    }

    @Test
    void goldChange_records_delta() {
        GoldChangeEvent e = new GoldChangeEvent(-5);

        assertThat(e.delta()).isEqualTo(-5);
    }

    @Test
    void combatEvent_records_all_fields_including_params_and_scripts() {
        Creature goblin = new Creature("Goblin", 5, 6);
        ScriptBlock scripts = ScriptBlock.empty();
        Map<String, Object> params = Map.of("enemyCrew", 12);

        CombatEvent e = new CombatEvent(
            "personal",
            List.of("theron"),
            List.of(goblin),
            false,
            params,
            scripts
        );

        assertThat(e.system()).isEqualTo("personal");
        assertThat(e.participantIds()).containsExactly("theron");
        assertThat(e.opponents()).containsExactly(goblin);
        assertThat(e.simultaneous()).isFalse();
        assertThat(e.params()).containsEntry("enemyCrew", 12);
        assertThat(e.scripts()).isEqualTo(scripts);
    }

    @Test
    void all_event_types_are_SectionEvent() {
        SectionEvent[] events = new SectionEvent[] {
            new StatChangeEvent(AttributeType.LUCK, 1),
            new ItemEvent("Arrow", ItemAction.LOSS, 1),
            new LuckTestEvent(1, 2),
            new SkillTestEvent(1, 2),
            new NavigateEvent(3),
            new GoldChangeEvent(10),
            new CombatEvent("personal", List.of(), List.of(), false, Map.of(), ScriptBlock.empty())
        };

        assertThat(events).hasSize(7);
    }
}
