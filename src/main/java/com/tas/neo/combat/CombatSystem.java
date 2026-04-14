package com.tas.neo.combat;

import com.tas.neo.domain.combat.CombatOutcome;
import com.tas.neo.domain.combat.Creature;
import com.tas.neo.domain.party.PartyMember;
import com.tas.neo.domain.player.Player;
import com.tas.neo.engine.HookDispatcher;
import com.tas.neo.io.GameInput;
import com.tas.neo.io.GameOutput;
import com.tas.neo.mechanics.Dice;
import java.util.List;
import java.util.Map;

public interface CombatSystem {
    String id();
    CombatOutcome run(Player player, List<PartyMember> participants,
                     List<Creature> opponents, Map<String, Object> params,
                     CombatSystemRegistry registry, HookDispatcher hooks,
                     GameInput input, GameOutput output, Dice dice);
}
