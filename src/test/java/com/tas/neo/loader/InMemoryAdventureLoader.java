package com.tas.neo.loader;

import com.tas.neo.domain.adventure.Adventure;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test double implementing {@link AdventureLoader} that accepts {@link Adventure}
 * objects directly. Used in engine-level tests, bypassing JSON parsing and filesystem
 * I/O.
 *
 * <p>Defined in {@code docs/design/06-testability.md}.
 */
public class InMemoryAdventureLoader implements AdventureLoader {

    private final Map<String, Adventure> adventures = new LinkedHashMap<>();

    public InMemoryAdventureLoader(Adventure... adventures) {
        for (Adventure a : adventures) {
            this.adventures.put(a.id(), a);
        }
    }

    @Override
    public Adventure load(String adventureId) throws AdventureLoadException {
        Adventure adventure = adventures.get(adventureId);
        if (adventure == null) {
            throw new AdventureLoadException("Unknown adventure id: " + adventureId);
        }
        return adventure;
    }
}
