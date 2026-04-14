package com.tas.neo.domain.party;

import com.tas.neo.mechanics.StatDefinition;
import java.util.Map;

public class PartyMemberDefinition {

    private final String id;
    private final String displayName;
    private final String lifeStat;
    private final DefeatConsequence onDefeat;
    private final MemberState initialState;
    private final Map<String, StatDefinition> stats;

    public PartyMemberDefinition(String id, String displayName, String lifeStat,
                                  DefeatConsequence onDefeat, MemberState initialState,
                                  Map<String, StatDefinition> stats) {
        this.id = id;
        this.displayName = displayName;
        this.lifeStat = lifeStat;
        this.onDefeat = onDefeat;
        this.initialState = initialState;
        this.stats = stats;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String lifeStat() {
        return lifeStat;
    }

    public DefeatConsequence onDefeat() {
        return onDefeat;
    }

    public MemberState initialState() {
        return initialState;
    }

    public Map<String, StatDefinition> stats() {
        return stats;
    }
}
