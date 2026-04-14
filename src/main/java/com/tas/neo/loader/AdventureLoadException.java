package com.tas.neo.loader;

public class AdventureLoadException extends Exception {
    public AdventureLoadException(String message) {
        super(message);
    }

    public AdventureLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
