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
    boolean isPartyMemberActive(String id);
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

`DefaultScriptContext` is constructed via static factory methods — never directly:

```java
public class DefaultScriptContext implements ScriptContext {
    public static DefaultScriptContext forSection(Player player, GameState state,
                                                   GameOutput output, List<Choice> choices);
    public static DefaultScriptContext forChoices(Player player, GameState state,
                                                   GameOutput output, List<Choice> choices);
    public static DefaultScriptContext forCell(Player player, GameState state,
                                               GameOutput output, List<Choice> choices);
}
```

`forChoices` produces a context where `navigateTo` throws `UnsupportedOperationException` and `addChoice`/`hideChoice` are permitted. `forSection` and `forCell` permit `navigateTo` but block `addChoice`. `forCell` sets `currentSection()` to return `-1`.

---

## AdventureScriptState

A mutable key/value store scoped to one adventure run. Survives section transitions. Permits `String`, `Integer`, and `Boolean` values only — enforced by typed overloads, not runtime checks.

```java
public class AdventureScriptState {
    public void set(String key, String value);
    public void set(String key, int value);
    public void set(String key, boolean value);
    public Object get(String key);    // returns String, Integer, or Boolean; null if absent
    public boolean has(String key);
}
```

No `set(String, Object)` overload. Scripts call the typed overload matching the value type. `get` returns `Object` because `ConditionEvaluator` compares with `Objects.equals` against a condition value of the same type.

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

## Hook Enums

```java
public enum AdventureHook { ON_LOAD, ON_START, ON_VICTORY }
public enum SectionHook   { ON_ENTER, ON_CHOICES, ON_EXIT }
public enum CombatHook    { ON_COMBAT_START, ON_COMBAT_END }
```

These live in `com.tas.neo.engine`. The string key stored in `ScriptBlock.hooks` matches the enum name in lower-snake-case: `ON_ENTER` → `"onEnter"`, `ON_COMBAT_START` → `"onCombatStart"`. `HookDispatcher` converts enum to key via a small private helper.

---

## HookDispatcher

Single point of contact between the engine and the scripting layer. Builds the correct `ScriptContext` for each hook point and calls `ScriptEngine.execute`.

```java
public class HookDispatcher {
    public HookDispatcher(ScriptEngine scriptEngine, GameInput input, GameOutput output,
                          GameState state, AdventureScriptState scriptState,
                          CombatSystemRegistry combatRegistry, Dice dice,
                          GameLogger logger);

    public void fireAdventureHook(AdventureHook hook, Adventure adventure);
    public void fireSectionHook(SectionHook hook, Section section, List<Choice> mutableChoices);
    public void fireCellHook(SectionHook hook, Cell cell, List<Choice> mutableChoices);
    public void fireCombatHook(CombatHook hook, CombatEvent event, CombatRound round);
    public void fireItemHook(ItemScriptHook hook, Item item);
    public CombatOutcome processCombatEvent(CombatEvent event);
    public void processEvent(SectionEvent event);
}
```

Defeat checking after party member stat changes is also the responsibility of `HookDispatcher`, since it has access to both `GameState` and `GameOutput`.

---

## PartyMemberProxy

A thin wrapper returned to scripts via `ctx.getPartyMember(id)`. Exposes a Lua-friendly API over `PartyMember`.

```java
public class PartyMemberProxy {
    public static PartyMemberProxy of(PartyMember member);   // wraps a known member
    public static PartyMemberProxy unknown(String id);       // no-op proxy for absent member

    public void modifyStat(String name, int delta);
    public int getStat(String name);
    public int getMaxStat(String name);
    public boolean isActive();
    public boolean isDefeated();
}
```

`PartyMemberProxy.unknown(id)` silently no-ops all mutation calls, returns `false` for boolean queries, and returns `0` for numeric queries. `DefaultScriptContext.getPartyMember(id)` returns `PartyMemberProxy.unknown(id)` when the id is not in `GameState`.

---

## Test Strategy

| Layer | What | Approach |
|-------|------|----------|
| unit | `LuaScriptEngine` executes script | `RecordingScriptContext` → assert calls recorded |
| unit | Failed script does not crash | Script with syntax error → assert `ScriptException` caught, game continues |
| unit | Sandboxing | Script attempting `io.open(...)` → assert error, no file access |
| unit | `HookDispatcher` no-op on empty hook | `ScriptBlock.empty()` → assert `ScriptEngine.execute` never called |
| unit | `HookDispatcher` fires correct hook | Fixture section with `onEnter` script → assert it executes on entry |
| unit | `navigateTo` blocked in `onChoices` | `DefaultScriptContext` in choices mode → assert `UnsupportedOperationException` |
| unit | Party member proxy no-op on unknown id | `ctx.getPartyMember('unknown')` → assert no exception, warning logged |
