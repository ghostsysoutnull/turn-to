# Design: Scripting Engine

## Responsibilities

The scripting layer executes Lua scripts at engine-defined lifecycle hook points. It exposes a controlled `ScriptContext` API to scripts and maintains an `AdventureScriptState` store scoped to each adventure run.

---

## ScriptEngine

```java
public interface ScriptEngine {
    void execute(String script, ScriptContext context) throws ScriptException;
}
```

`ScriptException` is a checked wrapper around Lua runtime errors. The engine catches it, logs it, and continues — a failing script never crashes the game.

`LuaScriptEngine` implements this using **LuaJ** (pure-Java Lua 5.2 interpreter). A new Lua environment is created per execution — scripts share no Lua state across invocations. Shared state is handled exclusively through `AdventureScriptState`.

---

## Sandboxing

`LuaScriptEngine` removes access to filesystem and OS modules before executing any script. Scripts retain access to `math`, `string`, and `table`. `print` is redirected to `GameOutput.showMessage`.

The modules removed are: `io`, `os`, `package`, `require`, `dofile`, `load`, `loadfile`.

---

## ScriptContext

```java
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
    void addPartyMember(String id);
    void removePartyMember(String id);
    void navigateTo(int section);
    int currentSection();
    void showMessage(String message);
    void addChoice(String text, int targetSection);
    void hideChoice(String id);
}
```

`DefaultScriptContext` holds references to `Player`, `GameState`, `GameOutput`, and the mutable choice list. Methods invalid for a given hook (e.g. `navigateTo` inside `onChoices`) throw `UnsupportedOperationException`.

`ctx.currentSection()` returns `-1` when called from a cell script (the player is in a grid, not a section). Cell scripts should not call this method.

`ctx.hideChoice(id)` removes the choice with the matching `id` from the mutable list. No-op if the id is not found or was already removed by a declarative condition.

---

## AdventureScriptState

A mutable key/value store scoped to one adventure run. Survives section transitions. Permits `String`, `Integer`, and `Boolean` values only.

```java
public class AdventureScriptState {
    public void set(String key, Object value);
    public Object get(String key);
    public boolean has(String key);
}
```

---

## ScriptBlock

```java
public record ScriptBlock(Map<String, String> hooks) {
    public Optional<String> get(String hookName);
    public static ScriptBlock empty();
}
```

Carried by `Adventure`, `Section`, `Item`, and `CombatEvent`. A missing hook entry is treated as a no-op by `HookDispatcher`.

---

## HookDispatcher

Single point of contact between the engine and the scripting layer. Builds the correct `ScriptContext` for each hook point and calls `ScriptEngine.execute`.

```java
public class HookDispatcher {
    public HookDispatcher(ScriptEngine scriptEngine, GameOutput output,
                          GameState state, AdventureScriptState scriptState,
                          CombatSystemRegistry combatRegistry);

    public void fireAdventureHook(AdventureHook hook, Adventure adventure);
    public void fireSectionHook(SectionHook hook, Section section, List<Choice> mutableChoices);
    public void fireCellHook(SectionHook hook, Cell cell, List<Choice> mutableChoices);
    public void fireCombatHook(CombatHook hook, CombatEvent event, CombatRound round);
    public void fireItemHook(ItemScriptHook hook, Item item);
    public CombatOutcome processCombatEvent(CombatEvent event);
}
```

Defeat checking after party member stat changes is also the responsibility of `HookDispatcher`, since it has access to both `GameState` and `GameOutput`.

---

## PartyMemberProxy

A thin wrapper returned to scripts via `ctx.getPartyMember(id)`. Exposes a Lua-friendly API over `PartyMember`.

```java
public class PartyMemberProxy {
    public void modifyStat(String name, int delta);
    public int getStat(String name);
    public int getMaxStat(String name);
    public boolean isActive();
    public boolean isDefeated();
}
```

If the requested id is unknown, the proxy silently no-ops all mutation calls, returns `false` for boolean queries, and returns `0` for numeric queries.

---

## Test Strategy

| What | Approach |
|------|----------|
| `LuaScriptEngine` executes script | `RecordingScriptContext` → assert calls recorded |
| Failed script does not crash | Script with syntax error → assert `ScriptException` caught, game continues |
| Sandboxing | Script attempting `io.open(...)` → assert error, no file access |
| `HookDispatcher` no-op on empty hook | `ScriptBlock.empty()` → assert `ScriptEngine.execute` never called |
| `HookDispatcher` fires correct hook | Fixture section with `onEnter` script → assert it executes on entry |
| `navigateTo` blocked in `onChoices` | `DefaultScriptContext` in choices mode → assert `UnsupportedOperationException` |
| Party member proxy no-op on unknown id | `ctx.getPartyMember('unknown')` → assert no exception, warning logged |
