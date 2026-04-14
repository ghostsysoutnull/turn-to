package com.tas.neo.domain.party;

public sealed interface DefeatConsequence
    permits GameOverConsequence, RemoveConsequence, NavigateConsequence {}
