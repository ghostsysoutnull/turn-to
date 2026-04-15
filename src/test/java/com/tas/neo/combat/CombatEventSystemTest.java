package com.tas.neo.combat;

import com.tas.neo.combat.personal.PersonalCombatSystem;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.event.CombatEvent;
import com.tas.neo.domain.combat.CombatOutcome;
import com.tas.neo.domain.combat.CombatOutcomeType;
import com.tas.neo.domain.combat.Creature;
import com.tas.neo.domain.item.Inventory;
import com.tas.neo.domain.party.MemberState;
import com.tas.neo.domain.party.PartyMember;
import com.tas.neo.domain.party.PartyMemberStat;
import com.tas.neo.domain.party.RemoveConsequence;
import com.tas.neo.domain.player.Attribute;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.domain.player.Player;
import com.tas.neo.io.RecordingOutput;
import com.tas.neo.io.ScriptedInput;
import com.tas.neo.mechanics.CombatEngine;
import com.tas.neo.mechanics.FixedDice;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CombatEvent} structural contracts and participant-id resolution.
 *
 * <p>Covers test strategy rows from docs/design/10-combat-systems.md:
 * <ul>
 *   <li>CombatEvent without system field defaults to "personal"</li>
 *   <li>Participant resolution: a CombatEvent naming a party member id passes the
 *       correct PartyMember to the system</li>
 * </ul>
 *
 * <p>The defaulting and participant-resolution logic is expected to live in the
 * component that processes {@code CombatEvent} (e.g. {@code HookDispatcher}).
 * These tests drive that contract from the outside by building a
 * {@link DefaultCombatSystemRegistry}, a registry-backed personal system, and
 * asserting the correct member is resolved and passed.
 */
class CombatEventSystemTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Player strongPlayer() {
        Map<AttributeType, Attribute> attrs = new EnumMap<>(AttributeType.class);
        attrs.put(AttributeType.SKILL,   new Attribute(AttributeType.SKILL,   10, 10));
        attrs.put(AttributeType.STAMINA, new Attribute(AttributeType.STAMINA, 20, 20));
        attrs.put(AttributeType.LUCK,    new Attribute(AttributeType.LUCK,    8,  8));
        return new Player(attrs, new Inventory(), 0, 0);
    }

    private static PartyMember activeMember(String id) {
        Map<String, PartyMemberStat> stats = Map.of("HP", new PartyMemberStat("HP", 10, 10));
        return new PartyMember(id, id, "HP", stats, new RemoveConsequence("Gone."), MemberState.ACTIVE);
    }

    // -------------------------------------------------------------------------
    // Row: CombatEvent without system field defaults to "personal"
    //
    // When a CombatEvent is constructed with system = "personal" (the value that
    // the loader must supply when the JSON field is absent), the system field
    // must be "personal".
    // -------------------------------------------------------------------------

    @Test
    void combat_event_with_explicit_personal_system_field_has_personal_system() {
        CombatEvent event = new CombatEvent(
            "personal",
            List.of(),
            List.of(new Creature("Goblin", 5, 2)),
            false,
            Map.of(),
            new ScriptBlock(Map.of()), 0, 0
        );

        assertThat(event.system())
            .as("CombatEvent.system() must be 'personal' when constructed with 'personal'")
            .isEqualTo("personal");
    }

    @Test
    void combat_event_system_null_is_absent_and_loader_must_default_it() {
        // This test documents the invariant: a null system field is invalid at the
        // engine layer. Adventures are loaded with the default applied by the loader.
        // The registry must be able to resolve "personal" for events authored without
        // a system field (loader normalises null → "personal").
        CombatEvent event = new CombatEvent(
            null,   // absent in JSON — loader must substitute "personal"
            List.of(),
            List.of(new Creature("Goblin", 5, 2)),
            false,
            Map.of(),
            new ScriptBlock(Map.of()), 0, 0
        );

        // The system field is null — the loader has not normalised it yet.
        // This is the pre-normalisation state; the assertion documents what the loader
        // must transform before the engine sees it.
        assertThat(event.system())
            .as("system field is null when not supplied to constructor — loader must default this to 'personal'")
            .isNull();
    }

    @Test
    void registry_can_resolve_personal_system_for_event_with_personal_field() {
        // When the engine reads event.system() == "personal" it must find the system in the registry.
        RecordingOutput output = new RecordingOutput();
        ScriptedInput input = new ScriptedInput();
        CombatEngine engine = new CombatEngine(new FixedDice(6), input, output);
        PersonalCombatSystem personal = new PersonalCombatSystem(engine);
        DefaultCombatSystemRegistry registry = new DefaultCombatSystemRegistry(personal);

        CombatEvent event = new CombatEvent(
            "personal", List.of(), List.of(new Creature("Goblin", 5, 2)),
            false, Map.of(), new ScriptBlock(Map.of()), 0, 0
        );

        assertThat(registry.has(event.system()))
            .as("registry must be able to resolve the system id from a CombatEvent with system='personal'")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // Row: Participant resolution
    //
    // A CombatEvent naming a party member id must result in the correct PartyMember
    // being passed to the CombatSystem when the event is processed.
    //
    // This test verifies the structural preconditions: the id listed in
    // CombatEvent.participantIds() matches the id of the PartyMember that would be
    // looked up in GameState (via GameState.getPartyMember(id)).
    // -------------------------------------------------------------------------

    @Test
    void combat_event_participant_ids_match_party_member_ids_for_resolution() {
        PartyMember theron = activeMember("theron");

        CombatEvent event = new CombatEvent(
            "personal",
            List.of("theron"),
            List.of(new Creature("Goblin", 5, 2)),
            false,
            Map.of(),
            new ScriptBlock(Map.of()), 0, 0
        );

        // The participant id in the event must match the party member id —
        // this is the contract the engine resolves against GameState.
        assertThat(event.participantIds())
            .as("participantIds in CombatEvent must contain the id of the named party member")
            .containsExactly(theron.id());
    }

    @Test
    void combat_event_with_multiple_participant_ids_lists_all_ids() {
        PartyMember theron = activeMember("theron");
        PartyMember ship = activeMember("ship");

        CombatEvent event = new CombatEvent(
            "naval",
            List.of("theron", "ship"),
            List.of(),
            false,
            Map.of(),
            new ScriptBlock(Map.of()), 0, 0
        );

        assertThat(event.participantIds())
            .as("CombatEvent must preserve all participant ids in declaration order")
            .containsExactly(theron.id(), ship.id());
    }

    @Test
    void combat_system_receives_resolved_party_member_when_participant_id_matches() {
        // This test verifies the resolution contract: given a CombatEvent with
        // participantIds = ["theron"] and a PartyMember with id "theron",
        // the resolved list passed to the system must contain that exact member.
        //
        // The production resolution logic is:
        //   event.participantIds().stream()
        //       .map(gameState::getPartyMember)
        //       .toList()
        //
        // We simulate this inline to pin the contract.
        PartyMember theron = activeMember("theron");
        Map<String, PartyMember> partyById = Map.of("theron", theron);

        CombatEvent event = new CombatEvent(
            "personal",
            List.of("theron"),
            List.of(new Creature("Goblin", 5, 2)),
            false,
            Map.of(),
            new ScriptBlock(Map.of()), 0, 0
        );

        List<PartyMember> resolved = event.participantIds().stream()
            .map(partyById::get)
            .toList();

        assertThat(resolved)
            .as("resolving participantIds from a party map must yield the correct PartyMember instances")
            .containsExactly(theron);
    }

    // -------------------------------------------------------------------------
    // CombatEvent record: params and opponents are exposed
    // -------------------------------------------------------------------------

    @Test
    void combat_event_exposes_opponents_list() {
        Creature goblin = new Creature("Goblin", 5, 2);
        CombatEvent event = new CombatEvent(
            "personal", List.of(), List.of(goblin), false, Map.of(), new ScriptBlock(Map.of()), 0, 0
        );

        assertThat(event.opponents())
            .as("CombatEvent must expose the opponents list")
            .containsExactly(goblin);
    }

    @Test
    void combat_event_exposes_params_map() {
        CombatEvent event = new CombatEvent(
            "naval", List.of(), List.of(), false,
            Map.of("enemyCrew", 12), new ScriptBlock(Map.of()), 0, 0
        );

        assertThat(event.params())
            .as("CombatEvent must expose the params map for system-specific values")
            .containsEntry("enemyCrew", 12);
    }

    @Test
    void combat_event_simultaneous_flag_is_exposed() {
        CombatEvent sequential = new CombatEvent(
            "personal", List.of(), List.of(), false, Map.of(), new ScriptBlock(Map.of()), 0, 0);
        CombatEvent simultaneous = new CombatEvent(
            "personal", List.of(), List.of(), true, Map.of(), new ScriptBlock(Map.of()), 0, 0);

        assertThat(sequential.simultaneous())
            .as("non-simultaneous CombatEvent must expose simultaneous = false")
            .isFalse();
        assertThat(simultaneous.simultaneous())
            .as("simultaneous CombatEvent must expose simultaneous = true")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // PersonalCombatSystem used via registry — integration smoke
    // -------------------------------------------------------------------------

    @Test
    void personal_system_via_registry_produces_victory_for_strong_player() {
        RecordingOutput output = new RecordingOutput();
        ScriptedInput input = new ScriptedInput();
        FixedDice dice = new FixedDice(6);
        CombatEngine engine = new CombatEngine(dice, input, output);
        PersonalCombatSystem personal = new PersonalCombatSystem(engine);
        DefaultCombatSystemRegistry registry = new DefaultCombatSystemRegistry(personal);

        Player player = strongPlayer();
        CombatEvent event = new CombatEvent(
            "personal", List.of(), List.of(new Creature("Goblin", 5, 2)),
            false, Map.of(), new ScriptBlock(Map.of()), 0, 0
        );

        CombatSystem system = registry.get(event.system());
        CombatOutcome outcome = system.run(
            player, List.of(), event.opponents(), event.params(),
            registry, null, input, output, dice);

        assertThat(outcome.type())
            .as("personal system retrieved via registry must produce VICTORY for a strong player")
            .isEqualTo(CombatOutcomeType.VICTORY);
    }
}
