package com.tas.neo.domain.adventure;

import java.util.Optional;

public record Choice(String text, ChoiceTarget target, Optional<Condition> condition, Optional<String> id) {

    public static Choice to(String text, ChoiceTarget target) {
        return new Choice(text, target, Optional.empty(), Optional.empty());
    }

    public static Choice to(String text, ChoiceTarget target, Condition condition) {
        return new Choice(text, target, Optional.of(condition), Optional.empty());
    }

    public static Choice to(String text, ChoiceTarget target, Condition condition, String id) {
        return new Choice(text, target, Optional.of(condition), Optional.of(id));
    }
}
