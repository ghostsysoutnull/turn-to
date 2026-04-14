package com.tas.neo.domain.party;

import com.tas.neo.mechanics.FixedStatDefinition;
import com.tas.neo.mechanics.StatDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PartyMemberDefinitionTest {

    @Test
    void exposes_id_displayName_lifeStat_and_onDefeat() {
        Map<String, StatDefinition> stats = new LinkedHashMap<>();
        stats.put("HP", new FixedStatDefinition(10, 10));
        DefeatConsequence defeat = new RemoveConsequence("Theron falls.");

        PartyMemberDefinition def = new PartyMemberDefinition(
            "theron", "Theron", "HP", defeat, MemberState.ACTIVE, stats);

        assertThat(def.id()).isEqualTo("theron");
        assertThat(def.displayName()).isEqualTo("Theron");
        assertThat(def.lifeStat()).isEqualTo("HP");
        assertThat(def.onDefeat()).isEqualTo(defeat);
        assertThat(def.initialState()).isEqualTo(MemberState.ACTIVE);
        assertThat(def.stats()).containsKey("HP");
    }

    @Test
    void initialState_can_be_WAITING() {
        Map<String, StatDefinition> stats = new LinkedHashMap<>();
        stats.put("HP", new FixedStatDefinition(10, 10));

        PartyMemberDefinition def = new PartyMemberDefinition(
            "theron", "Theron", "HP",
            new RemoveConsequence("-"), MemberState.WAITING, stats);

        assertThat(def.initialState()).isEqualTo(MemberState.WAITING);
    }
}
