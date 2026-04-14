package com.tas.neo.domain.adventure;

public record PartyStatCondition(String memberId, String statName,
                                  ComparisonType comparison, int threshold) implements Condition {}
