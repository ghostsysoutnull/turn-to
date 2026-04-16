package com.tas.neo.scripting;

public interface ScriptContext {
    void modifyStat(String attribute, int delta);
    int getStat(String attribute);
    void modifyGold(int delta);
    int getGold();
    void addItem(String itemName);
    void addItem(String itemName, int quantity);
    void removeItem(String itemName);
    void removeItem(String itemName, int quantity);
    boolean hasItem(String itemName);
    int getItemCount(String itemName);
    PartyMemberProxy getPartyMember(String id);
    boolean isPartyMemberActive(String id);
    void addPartyMember(String id);
    void removePartyMember(String id);
    void navigateTo(int section);
    int currentSection();
    void showMessage(String message);
    void addChoice(String text, int targetSection);
    void hideChoice(String id);

    /** Called by the scripting engine when a script fails. Default is a no-op. */
    default void onScriptError(String message) {}
}
