package com.tas.neo.domain.adventure;

import com.tas.neo.domain.adventure.event.SectionEvent;
import java.util.List;

public class Section {

    private final int number;
    private final String narrative;
    private final List<SectionEvent> events;
    private final List<Choice> choices;
    private final SectionType type;
    private final ScriptBlock scripts;

    public Section(int number, String narrative, List<SectionEvent> events,
                   List<Choice> choices, SectionType type, ScriptBlock scripts) {
        this.number = number;
        this.narrative = narrative;
        this.events = events;
        this.choices = choices;
        this.type = type;
        this.scripts = scripts;
    }

    public int number() { return number; }
    public String narrative() { return narrative; }
    public List<SectionEvent> events() { return events; }
    public List<Choice> choices() { return choices; }
    public SectionType type() { return type; }
    public ScriptBlock scripts() { return scripts; }
}
