package com.tas.neo.engine;

import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.location.Cell;
import com.tas.neo.domain.location.Grid;
import com.tas.neo.domain.party.MemberState;
import com.tas.neo.domain.party.PartyMember;
import com.tas.neo.domain.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GameState {

    private Player player;
    private Section currentSection;
    private Grid currentGrid;
    private Cell currentCell;
    private boolean gameOver = false;
    private boolean victory = false;
    private final Map<String, PartyMember> partyMembers = new LinkedHashMap<>();

    public Player player() { return player; }
    public void setPlayer(Player player) { this.player = player; }

    /** Alias for player() — retained for compatibility. */
    public Player getPlayer() { return player; }

    public Section currentSection() { return currentSection; }

    public void navigateTo(Section section) {
        this.currentSection = section;
        this.currentGrid = null;
        this.currentCell = null;
    }

    public Optional<Grid> currentGrid() { return Optional.ofNullable(currentGrid); }
    public Optional<Cell> currentCell() { return Optional.ofNullable(currentCell); }

    public void navigateToCell(Grid grid, Cell cell) {
        this.currentGrid = grid;
        this.currentCell = cell;
        this.currentSection = null;
    }

    public boolean isInGrid() { return currentGrid != null; }
    public boolean isTerminal() { return gameOver || victory; }
    public boolean isGameOver() { return gameOver; }
    public boolean isVictory() { return victory; }

    public void setGameOver() { this.gameOver = true; }
    public void setVictory() { this.victory = true; }

    public void addPartyMember(PartyMember member) {
        partyMembers.put(member.id(), member);
    }

    public PartyMember getPartyMember(String id) {
        return partyMembers.get(id);
    }

    public List<PartyMember> activePartyMembers() {
        List<PartyMember> active = new ArrayList<>();
        for (PartyMember m : partyMembers.values()) {
            if (m.state() == MemberState.ACTIVE) {
                active.add(m);
            }
        }
        return active;
    }

    public void setPartyMemberState(String id, MemberState state) {
        PartyMember m = partyMembers.get(id);
        if (m != null) {
            m.setState(state);
        }
    }
}
