# Design: Combat Systems

## Overview

The engine supports pluggable combat systems. Each system encapsulates its own round structure, stat usage, and outcome logic. The standard Fighting Fantasy personal combat is one system. Adventures can register additional systems (naval, companion-based, arena, etc.) via `CombatSystemRegistry`.

The engine has no knowledge of specific combat systems beyond `"personal"`, which is always available.

---

## CombatSystem

```java
public interface CombatSystem {
    String id();
    CombatOutcome run(
        Player player,
        List<PartyMember> participants,
        List<Creature> opponents,
        Map<String, Object> params,
        CombatSystemRegistry registry,
        HookDispatcher hooks,
        GameInput input,
        GameOutput output,
        Dice dice
    );
}
```

`participants` are the party members declared in the `CombatEvent`. `params` are system-specific values from the adventure JSON. `registry` allows a system to delegate to another system (e.g. boarding → personal combat).

---

## CombatOutcome

```java
public record CombatOutcome(CombatOutcomeType type, Optional<Integer> navigateTo) {}
```

```java
public enum CombatOutcomeType { VICTORY, DEFEAT, FLED, DELEGATED }
```

When `type` is `DELEGATED`, `navigateTo` is empty — the delegated system's outcome drives navigation.

---

## CombatSystemRegistry

```java
public interface CombatSystemRegistry {
    CombatSystem get(String id);
    boolean has(String id);
}
```

`DefaultCombatSystemRegistry` is built at startup. `PersonalCombatSystem` is always registered. Additional systems are registered in `Main` per adventure need. `get(String id)` throws `IllegalArgumentException` when the id is unknown — no separate `CombatException` class exists.

---

## PersonalCombatSystem

Wraps `CombatEngine`. Ignores `participants` — personal combat only involves the player.

```java
public class PersonalCombatSystem implements CombatSystem {
    public PersonalCombatSystem(CombatEngine engine);
    public String id();
    public CombatOutcome run(...);
}
```

`PersonalCombatSystem.run()` calls `engine.fight()`, then applies `CombatResult.playerStaminaLost()` to the player via `player.modifyAttribute(AttributeType.STAMINA, -staminaLost)` before returning the `CombatOutcome`. `CombatEngine` never modifies `Player` directly — it only produces the result.

---

## Updated CombatEvent

```java
public record CombatEvent(
    String system,
    List<String> participantIds,
    List<Creature> opponents,
    boolean simultaneous,
    Map<String, Object> params,
    ScriptBlock scripts
) implements SectionEvent {}
```

`system` defaults to `"personal"` if absent from JSON. All existing adventures without a `system` field continue to work unchanged.

---

## JSON Example (naval combat)

```json
{
  "type": "COMBAT",
  "system": "naval",
  "participants": ["ship"],
  "opponents": [],
  "params": {
    "enemyShipName": "The Serpent",
    "enemyCrew": 12,
    "enemyHull": 6,
    "allowBoarding": true
  },
  "scripts": {
    "onCombatStart": "ctx.showMessage('The enemy ship bears down on you!')",
    "onCombatEnd":   "if ctx.getPartyMember('ship').getStat('HULL') <= 3 then ctx.showMessage('Your ship is badly damaged.') end"
  }
}
```

---

## System Composition

A system can delegate to another via `registry.get("personal").run(...)`. No special engine support is required — systems compose through the registry alone. Example: a naval system transitions to personal combat for boarding actions by calling the personal system directly and returning its outcome.

---

## Test Strategy

| Layer | What | Approach |
|-------|------|----------|
| unit | `PersonalCombatSystem` victory | `FixedDice` favouring player → assert `VICTORY` outcome |
| unit | `PersonalCombatSystem` defeat | `FixedDice` favouring creature → assert `DEFEAT` outcome |
| unit | System delegation | Mock registry returning fixed-outcome system; assert `DELEGATED` not returned |
| unit | `CombatEvent` defaults to personal | JSON with no `system` field → assert `"personal"` used |
| unit | Participant resolution | `GameState` with party member; event names it → assert correct member passed |
| unit | Unknown system id | `registry.get("unknown")` → assert `CombatException` |
