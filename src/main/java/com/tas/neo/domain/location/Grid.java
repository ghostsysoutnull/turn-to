package com.tas.neo.domain.location;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Grid {

    private final String id;
    private final int width;
    private final int height;
    private final int floors;
    private final Map<String, Cell> cellMap;

    public Grid(String id, int width, int height, int floors, Map<String, Cell> cellMap) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.floors = floors;
        this.cellMap = cellMap;
    }

    public String id() { return id; }
    public int width() { return width; }
    public int height() { return height; }
    public int floors() { return floors; }

    public Optional<Cell> getCell(int x, int y, int z) {
        for (Cell cell : cellMap.values()) {
            if (cell.x() == x && cell.y() == y && cell.z() == z) {
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }

    public Optional<Cell> getCellById(String id) {
        for (Cell cell : cellMap.values()) {
            if (cell.id().isPresent() && cell.id().get().equals(id)) {
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }

    public List<Cell> cells() {
        return new ArrayList<>(cellMap.values());
    }
}
