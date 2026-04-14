# Design: Combat Systems

## Overview

The engine supports pluggable combat systems. Each system encapsulates its own round structure, stat usage, and outcome logic. The standard Fighting Fantasy personal combat is one such system. Adventures can declare additional systems (naval, companion-based, arena, etc.) by registering them and specifying them in `CombatEvent`.

The engine has no knowledge of specific combat systems beyond `"personal"`, which is always available.

---

## CombatSystem Interface

```java
public interface CombatSystem {
    String id();  // e.g. "personal", "naval"

    CombatOutcome run(
        Player player,
        List<PartyMember> participants,    // party members declared in the CombatEvent
        List<Creature> opponents,
        Map<String, Object> params,        // system-specific params from JSON
        CombatSystemRegistry registry,     // access other systems (e.g. for boarding)
        HookDispatcher hooks,
        GameInput input,
        GameOutput output,
        Dice dice
    );
}
```

---

## CombatOutcome

Replaces `CombatResult` for the generalised system:

```java
public record CombatOutcome(
    CombatOutcomeType type,
    Optional<Integer> navigateTo    // if the system wants to navigate post-combat
) {}

public enum CombatOutcomeType {
    VICTORY,   // player/party won
    DEFEAT,    // player died or party defeated
    FLED,      // player successfully fled
    DELEGATED  // system handed off to another system (e.g. boarding)
}
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

`DefaultCombatSystemRegistry` is built at startup and holds all registered systems. `PersonalCombatSystem` is always registered. Additional systems are registered in `Main` alongside other wiring.

---

## Updated CombatEvent

```java
public record CombatEvent(
    String system,                  // default "personal"
    List<String> participantIds,    // party member ids that join this combat
    List<Creature> opponents,
    boolean simultaneous,           // personal combat only
    Map<String, Object> params,     // system-specific config
    ScriptBlock scripts             // onCombatStart, onRoundStart, onRoundEnd, onCombatEnd
) implements SectionEvent {

    public CombatEvent(List<Creature> opponents, boolean simultaneous) {
        this("personal", List.of(), opponents, simultaneous, Map.of(), ScriptBlock.empty());
    }
}
```

Backwards-compatible: existing adventures with no `system` field default to `"personal"` with no participants.

---

## PersonalCombatSystem

Wraps the existing `CombatEngine`:

```java
public class PersonalCombatSystem implements CombatSystem {

    @Override
    public String id() { return "personal"; }

    @Override
    public CombatOutcome run(Player player, List<PartyMember> participants,
                             List<Creature> opponents, Map<String, Object> params,
                             CombatSystemRegistry registry, HookDispatcher hooks,
                             GameInput input, GameOutput output, Dice dice) {

        CombatResult result = combatEngine.fight(player, opponents,
            (boolean) params.getOrDefault("simultaneous", false));

        if (result.playerWon())
            return new CombatOutcome(CombatOutcomeType.VICTORY, Optional.empty());
        else
            return new CombatOutcome(CombatOutcomeType.DEFEAT, Optional.empty());
    }
}
```

---

## Example: Naval Combat System (illustrative)

This is not a built-in system — it demonstrates how an adventure registers a custom system:

```java
public class NavalCombatSystem implements CombatSystem {

    @Override
    public String id() { return "naval"; }

    @Override
    public CombatOutcome run(Player player, List<PartyMember> participants, ...) {

        PartyMember ship = participants.stream()
            .filter(m -> m.id().equals("ship"))
            .findFirst()
            .orElseThrow(() -> new CombatException("Naval combat requires 'ship' participant"));

        int enemyCrew  = (int) params.get("enemyCrew");
        int enemyHull  = (int) params.get("enemyHull");
        boolean allowBoarding = (boolean) params.getOrDefault("allowBoarding", false);

        // ... naval rounds: reduce enemyCrew/ship.CREW, enemyHull/ship.HULL ...

        if (allowBoarding && playerChoosesToBoard(input)) {
            // Transition to personal combat
            List<Creature> boarders = buildBoardingParty(params);
            return registry.get("personal").run(player, List.of(), boarders,
                Map.of(), registry, hooks, input, output, dice);
        }

        if (ship.isDefeated())
            return new CombatOutcome(CombatOutcomeType.DEFEAT, Optional.empty());

        return new CombatOutcome(CombatOutcomeType.VICTORY, Optional.empty());
    }
}
```

The naval system calls `registry.get("personal")` for boarding — systems compose cleanly without any special engine support.

---

## CombatEvent JSON

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

## Engine Integration

`HookDispatcher.processCombatEvent` resolves the system and participants before calling `run`:

```java
public CombatOutcome processCombatEvent(CombatEvent event, GameState state) {
    CombatSystem system = registry.get(event.system());

    List<PartyMember> participants = event.participantIds().stream()
        .map(state::getPartyMember)
        .toList();

    hooks.fireCombatHook(CombatHook.ON_COMBAT_START, event);

    CombatOutcome outcome = system.run(
        state.player(), participants, event.opponents(),
        event.params(), registry, hooks, input, output, dice
    );

    hooks.fireCombatHook(CombatHook.ON_COMBAT_END, event);

    return outcome;
}
```

---

## Wiring (Main)

```java
CombatSystemRegistry combatRegistry = new DefaultCombatSystemRegistry(
    new PersonalCombatSystem(combatEngine),
    new NavalCombatSystem()     // only needed if the adventure uses it
);
```

Adventures that only use personal combat require no registry changes.

---

## Test Strategy

| What | Approach |
|------|----------|
| `PersonalCombatSystem` | `FixedDice` + assert `CombatOutcome.type()` |
| System delegation (boarding) | Mock registry returning a `FixedOutcomeSystem`; assert delegation occurred |
| `CombatEvent` defaults | Load JSON with no `system` field → assert `"personal"` used |
| Participant resolution | `GameState` with party member; `CombatEvent` names it → assert correct `PartyMember` passed |
| Unknown system id | `registry.get("unknown")` → assert `CombatException` |
| Naval system defeat consequence | Ship CREW reaches 0 → assert `DEFEAT` outcome |
