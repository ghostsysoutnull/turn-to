package com.tas.neo.domain.adventure;

public record GridTarget(String gridId, String cellId) implements ChoiceTarget {}
