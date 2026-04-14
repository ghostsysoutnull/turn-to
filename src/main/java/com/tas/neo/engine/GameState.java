package com.tas.neo.engine;

import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.location.Grid;
import com.tas.neo.domain.party.PartyMember;
import java.util.Optional;

public class GameState {

    // Stub — to be fully implemented in engine layer

    public boolean isVictory() { return false; }
    public boolean isGameOver() { return false; }
    public boolean isInGrid() { return false; }
    public Section currentSection() { return null; }
    public Optional<Grid> currentGrid() { return Optional.empty(); }
    public PartyMember getPartyMember(String id) { return null; }
}
