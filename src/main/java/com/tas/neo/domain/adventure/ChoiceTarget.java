package com.tas.neo.domain.adventure;

public sealed interface ChoiceTarget
    permits SectionTarget, GridTarget {}
