package com.tas.neo.scripting;

public interface PartyMemberProxy {
    String id();
    int getStat(String name);
    void modifyStat(String name, int delta);
    boolean isActive();
}
