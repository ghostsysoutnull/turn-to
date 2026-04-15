package com.tas.neo.combat;

import com.tas.neo.combat.personal.PersonalCombatSystem;
import com.tas.neo.domain.combat.CombatOutcome;
import com.tas.neo.domain.combat.Creature;
import com.tas.neo.domain.party.PartyMember;
import com.tas.neo.domain.player.Player;
import com.tas.neo.engine.HookDispatcher;
import com.tas.neo.io.GameInput;
import com.tas.neo.io.GameOutput;
import com.tas.neo.mechanics.CombatEngine;
import com.tas.neo.mechanics.Dice;
import com.tas.neo.mechanics.FixedDice;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DefaultCombatSystemRegistry}.
 *
 * <p>Covers the test strategy rows from docs/design/10-combat-systems.md:
 * <ul>
 *   <li>get known id returns the registered system</li>
 *   <li>get unknown id throws an exception</li>
 *   <li>has() returns true for registered id, false for unknown</li>
 * </ul>
 */
class DefaultCombatSystemRegistryTest {

    // -------------------------------------------------------------------------
    // Minimal stub CombatSystem for registry tests — not a real implementation
    // -------------------------------------------------------------------------

    private static CombatSystem stubSystem(String id) {
        return new CombatSystem() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public CombatOutcome run(Player player, List<PartyMember> participants,
                                     List<Creature> opponents, Map<String, Object> params,
                                     CombatSystemRegistry registry, HookDispatcher hooks,
                                     GameInput input, GameOutput output, Dice dice) {
                throw new UnsupportedOperationException("stub");
            }
        };
    }

    // -------------------------------------------------------------------------
    // Row: get known id returns system
    // -------------------------------------------------------------------------

    @Test
    void get_registered_id_returns_the_system() {
        CombatSystem personal = stubSystem("personal");
        DefaultCombatSystemRegistry registry = new DefaultCombatSystemRegistry(personal);

        CombatSystem result = registry.get("personal");

        assertThat(result)
            .as("get('personal') should return the registered personal system")
            .isSameAs(personal);
    }

    @Test
    void get_one_of_multiple_registered_systems_returns_correct_instance() {
        CombatSystem personal = stubSystem("personal");
        CombatSystem naval = stubSystem("naval");
        DefaultCombatSystemRegistry registry = new DefaultCombatSystemRegistry(personal, naval);

        assertThat(registry.get("personal"))
            .as("get('personal') should return the personal system")
            .isSameAs(personal);
        assertThat(registry.get("naval"))
            .as("get('naval') should return the naval system")
            .isSameAs(naval);
    }

    // -------------------------------------------------------------------------
    // Row: get unknown id throws
    // -------------------------------------------------------------------------

    @Test
    void get_unknown_id_throws_illegal_argument_exception() {
        DefaultCombatSystemRegistry registry = new DefaultCombatSystemRegistry(stubSystem("personal"));

        assertThatThrownBy(() -> registry.get("unknown"))
            .as("get() with an unknown id must throw an exception identifying the unknown system")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown");
    }

    @Test
    void get_unknown_id_exception_names_the_missing_id() {
        DefaultCombatSystemRegistry registry = new DefaultCombatSystemRegistry(stubSystem("personal"));

        assertThatThrownBy(() -> registry.get("arena"))
            .as("exception message should contain the id that was not found")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("arena");
    }

    // -------------------------------------------------------------------------
    // has() contract
    // -------------------------------------------------------------------------

    @Test
    void has_returns_true_for_registered_system() {
        DefaultCombatSystemRegistry registry = new DefaultCombatSystemRegistry(stubSystem("personal"));

        assertThat(registry.has("personal"))
            .as("has('personal') should return true after registration")
            .isTrue();
    }

    @Test
    void has_returns_false_for_unregistered_id() {
        DefaultCombatSystemRegistry registry = new DefaultCombatSystemRegistry(stubSystem("personal"));

        assertThat(registry.has("naval"))
            .as("has('naval') should return false when naval is not registered")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // PersonalCombatSystem always registered via constructor
    // -------------------------------------------------------------------------

    @Test
    void registry_built_with_personal_combat_system_has_personal_id() {
        FixedDice dice = new FixedDice(6);
        CombatEngine engine = new CombatEngine(dice, new com.tas.neo.io.ScriptedInput(), new com.tas.neo.io.RecordingOutput());
        PersonalCombatSystem personal = new PersonalCombatSystem(engine);
        DefaultCombatSystemRegistry registry = new DefaultCombatSystemRegistry(personal);

        assertThat(registry.has("personal"))
            .as("registry built with PersonalCombatSystem should expose 'personal' id")
            .isTrue();
        assertThat(registry.get("personal"))
            .as("get('personal') should return the PersonalCombatSystem instance")
            .isSameAs(personal);
    }
}
