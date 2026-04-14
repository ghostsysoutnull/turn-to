package com.tas.neo.io;

import com.tas.neo.domain.log.GameError;
import com.tas.neo.domain.log.NavigationEntry;

public interface GameLogger {
    void logNavigation(NavigationEntry entry);
    void logEvent(OutputEvent event);
    void logError(GameError error);
    void close();
}
