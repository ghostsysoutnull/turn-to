package com.tas.neo.domain.location;

public enum Direction {
    NORTH, SOUTH, EAST, WEST, UP, DOWN;

    public int dx() {
        return switch (this) {
            case EAST -> 1;
            case WEST -> -1;
            default -> 0;
        };
    }

    public int dy() {
        return switch (this) {
            case NORTH -> -1;
            case SOUTH -> 1;
            default -> 0;
        };
    }

    public int dz() {
        return switch (this) {
            case UP -> 1;
            case DOWN -> -1;
            default -> 0;
        };
    }

    public String defaultLabel() {
        return "Go " + name().toLowerCase();
    }

    public Direction opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
            case UP -> DOWN;
            case DOWN -> UP;
        };
    }
}
