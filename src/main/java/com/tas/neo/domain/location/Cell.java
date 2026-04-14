package com.tas.neo.domain.location;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.event.SectionEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Cell {

    private final Optional<String> id;
    private final int x;
    private final int y;
    private final int z;
    private final String narrative;
    private final List<SectionEvent> events;
    private final ScriptBlock scripts;
    private final Map<Direction, Passage> passages;
    private final List<Choice> choices;

    public Cell(Optional<String> id, int x, int y, int z, String narrative,
                List<SectionEvent> events, ScriptBlock scripts,
                Map<Direction, Passage> passages, List<Choice> choices) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.narrative = narrative;
        this.events = events;
        this.scripts = scripts;
        this.passages = passages;
        this.choices = choices;
    }

    public Optional<String> id() { return id; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public String narrative() { return narrative; }
    public List<SectionEvent> events() { return events; }
    public ScriptBlock scripts() { return scripts; }
    public Map<Direction, Passage> passages() { return passages; }
    public List<Choice> choices() { return choices; }
}
