# Design: Scripting Engine

## Responsibilities

The scripting layer executes Lua scripts at engine-defined lifecycle hook points. It exposes a controlled `ScriptContext` API to scripts and maintains an `AdventureScriptState` store scoped to each adventure run.

---

## ScriptEngine Interface

```java
public interface ScriptEngine {
    void execute(String script, ScriptContext context) throws ScriptException;
}
```

`ScriptException` is a checked wrapper around Lua runtime errors. The engine catches it, logs it, and continues — a failing script never crashes the game.

---

## LuaScriptEngine

Uses **LuaJ** (pure-Java Lua 5.2 interpreter):

```java
public class LuaScriptEngine implements ScriptEngine {

    @Override
    public void execute(String script, ScriptContext context) throws ScriptException {
        Globals globals = JsePlatform.standardGlobals();
        globals.set("ctx",   CoerceJavaToLua.coerce(context));
        globals.set("state", CoerceJavaToLua.coerce(context.getState()));
        try {
            globals.load(script).call();
        } catch (LuaError e) {
            throw new ScriptException(e.getMessage(), e);
        }
    }
}
```

A **new `Globals` environment is created per execution** — scripts share no Lua state across invocations. Shared state is handled exclusively through `AdventureScriptState` via `state.set/get`.

### Sandboxing

The standard LuaJ `JsePlatform.standardGlobals()` is trimmed before use:

```java
globals.set("io",      LuaValue.NIL);
globals.set("os",      LuaValue.NIL);
globals.set("package", LuaValue.NIL);
globals.set("require", LuaValue.NIL);
globals.set("dofile",  LuaValue.NIL);
globals.set("load",    LuaValue.NIL);
globals.set("loadfile",LuaValue.NIL);
```

Scripts retain: `math`, `string`, `table`, `print` (redirected to `GameOutput.showMessage`).

---

## ScriptContext

```java
public interface ScriptContext {
    void modifyStat(String attribute, int delta);
    int getStat(String attribute);
    void modifyGold(int delta);
    int getGold();
    void addItem(String itemName);
    void removeItem(String itemName);
    boolean hasItem(String itemName);
    void navigateTo(int section);
    int currentSection();
    void showMessage(String message);
    void addChoice(String text, int targetSection);
    void removeChoice(String text);
    AdventureScriptState getState();
}
```

`DefaultScriptContext` holds references to `Player`, `GameState`, `GameOutput`, and the mutable choice list (for `onChoices` hooks). Methods that are invalid for a given hook (e.g. `navigateTo` in `onChoices`) throw `UnsupportedOperationException`.

---

## AdventureScriptState

A simple mutable key/value store, scoped to one adventure run. Survives section transitions.

```java
public class AdventureScriptState {
    private final Map<String, Object> values = new HashMap<>();

    public void set(String key, Object value);  // String, Integer, Boolean only
    public Object get(String key);              // null if not set
    public boolean has(String key);
}
```

Only `String`, `Integer`, and `Boolean` values are permitted. The `set` method validates the type and throws `ScriptException` otherwise.

---

## HookDispatcher

`HookDispatcher` is the single point of contact between the engine and the scripting layer. It knows which hook to fire, builds the correct `ScriptContext`, and calls `ScriptEngine.execute`.

```java
public class HookDispatcher {
    public HookDispatcher(ScriptEngine scriptEngine, GameOutput output,
                          GameState state, AdventureScriptState scriptState) { ... }

    public void fireAdventureHook(AdventureHook hook, Adventure adventure);
    public void fireSectionHook(SectionHook hook, Section section, List<Choice> mutableChoices);
    public void fireCombatHook(CombatHook hook, CombatEvent event, CombatRound round);
    public void fireItemHook(ItemScriptHook hook, Item item, CombatRound round);
}
```

The engine calls `HookDispatcher` at each lifecycle point. If the relevant script block has no entry for the hook, the call is a no-op.

---

## Integration with Game Loop

```java
// Section entry
dispatcher.fireAdventureHook(AdventureHook.ON_ENTER_SECTION, adventure);  // not an adventure hook — section:
dispatcher.fireSectionHook(SectionHook.ON_ENTER, section, choices);

output.showNarrative(section.narrative());
dispatcher.fireSectionHook(SectionHook.ON_DISPLAY, section, choices);

// Before presenting choices
dispatcher.fireSectionHook(SectionHook.ON_CHOICES, section, mutableChoices);
output.showChoices(mutableChoices);

// After player selects
dispatcher.fireSectionHook(SectionHook.ON_EXIT, section, choices);
```

---

## ScriptBlock Domain Object

`ScriptBlock` is a record holding the optional script strings per hook:

```java
public record ScriptBlock(Map<String, String> hooks) {
    public Optional<String> get(String hookName) {
        return Optional.ofNullable(hooks.get(hookName));
    }

    public static ScriptBlock empty() {
        return new ScriptBlock(Map.of());
    }
}
```

`Section`, `Adventure`, and `Item` each carry a `ScriptBlock`.

---

## Test Strategy

### `RecordingScriptContext`

Captures every call made by a script for assertion:

```java
public class RecordingScriptContext implements ScriptContext {
    private final List<String> messages = new ArrayList<>();
    private final List<StatChange> statChanges = new ArrayList<>();
    private int navigatedTo = -1;

    // ... implements all methods, recording calls

    public List<String> getMessages() { ... }
    public int navigatedTo() { ... }
    public boolean itemAdded(String name) { ... }
}
```

### `NoOpScriptEngine`

Returns immediately without executing anything — useful for engine tests that don't involve scripts:

```java
public class NoOpScriptEngine implements ScriptEngine {
    @Override
    public void execute(String script, ScriptContext context) { /* no-op */ }
}
```

### Direct script testing

```java
@Test
void healingPotionRestoresStamina() {
    var ctx = new RecordingScriptContext(player);
    var engine = new LuaScriptEngine();
    engine.execute("""
        ctx.modifyStat('STAMINA', 6)
        ctx.showMessage('You feel restored.')
        ctx.removeItem('Healing Potion')
    """, ctx);

    assertThat(player.getStamina()).isEqualTo(initialStamina + 6);
    assertThat(ctx.getMessages()).contains("You feel restored.");
}
```
