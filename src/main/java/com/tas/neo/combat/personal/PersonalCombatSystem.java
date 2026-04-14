package com.tas.neo.combat.personal;

import com.tas.neo.combat.CombatSystem;
import com.tas.neo.combat.CombatSystemRegistry;
import com.tas.neo.domain.combat.CombatOutcome;
import com.tas.neo.domain.combat.CombatOutcomeType;
import com.tas.neo.domain.combat.Creature;
import com.tas.neo.domain.party.PartyMember;
import com.tas.neo.domain.player.Player;
import com.tas.neo.engine.HookDispatcher;
import com.tas.neo.io.GameInput;
import com.tas.neo.io.GameOutput;
import com.tas.neo.mechanics.CombatEngine;
import com.tas.neo.mechanics.Dice;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PersonalCombatSystem implements CombatSystem {

    private final CombatEngine engine;

    public PersonalCombatSystem(CombatEngine engine) {
        this.engine = engine;
    }

    @Override
    public String id() {
        return "personal";
    }

    @Override
    public CombatOutcome run(Player player, List<PartyMember> participants,
                             List<Creature> opponents, Map<String, Object> params,
                             CombatSystemRegistry registry, HookDispatcher hooks,
                             GameInput input, GameOutput output, Dice dice) {
        return new CombatOutcome(CombatOutcomeType.VICTORY, Optional.empty());
    }
}
