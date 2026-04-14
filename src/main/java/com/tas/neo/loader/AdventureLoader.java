package com.tas.neo.loader;

import com.tas.neo.domain.adventure.Adventure;

public interface AdventureLoader {
    Adventure load(String adventureId) throws AdventureLoadException;
}
