package com.tas.neo.scripting;

import com.tas.neo.domain.party.PartyMember;

public class PartyMemberProxy {

    private final PartyMember member;

    private PartyMemberProxy(PartyMember member) {
        this.member = member;
    }

    public static PartyMemberProxy of(PartyMember member) {
        return new PartyMemberProxy(member);
    }

    /** Returns a no-op proxy for an id that is not in GameState. */
    public static PartyMemberProxy unknown(String id) {
        return new PartyMemberProxy(null);
    }

    public void modifyStat(String name, int delta) {
        if (member != null) {
            member.modifyStat(name, delta);
        }
    }

    public int getStat(String name) {
        return member == null ? 0 : member.getStat(name);
    }

    public int getMaxStat(String name) {
        return member == null ? 0 : member.getMaxStat(name);
    }

    public boolean isActive() {
        return member != null && member.isActive();
    }

    public boolean isDefeated() {
        return member != null && member.isDefeated();
    }
}
