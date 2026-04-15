package com.tas.neo.io;

import com.tas.neo.domain.log.GameError;
import com.tas.neo.domain.log.NavigationEntry;

public class NoOpGameLogger implements GameLogger {

    @Override
    public void logNavigation(NavigationEntry entry) {}

    @Override
    public void logEvent(OutputEvent event) {}

    @Override
    public void logError(GameError error) {}

    @Override
    public void close() {}
}
