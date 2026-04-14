# Design: Location Networks

## Responsibilities

The location network model represents spatial grids of traversable cells. It provides the domain types for `Grid`, `Cell`, `Passage`, and `Direction`. The loader populates these types from JSON. The engine navigates between cells via `GameState.navigateToCell` and fires cell lifecycle hooks via `HookDispatcher.fireCellHook`.

---

## Direction

```java
public enum Direction {
    NORTH, SOUTH, EAST, WEST, UP, DOWN;

    public int dx();
    public int dy();
    public int dz();
    public Direction opposite();
}
```

Coordinate deltas:

| Direction | dx | dy | dz |
|-----------|----|----|-----|
| NORTH     | 0  | −1 | 0   |
| SOUTH     | 0  | +1 | 0   |
| EAST      | +1 | 0  | 0   |
| WEST      | −1 | 0  | 0   |
| UP        | 0  | 0  | +1  |
| DOWN      | 0  | 0  | −1  |

`opposite()` returns the inverse direction (NORTH ↔ SOUTH, EAST ↔ WEST, UP ↔ DOWN).

---

## Passage

```java
public record Passage(
    Optional<String> label,
    Optional<Condition> condition,
    Optional<Integer> toSection
) {}
```

A passage without `toSection` targets the adjacent cell implied by its direction and the current cell's coordinates. A passage with `toSection` exits the grid to the specified section.

The `label` is the text shown to the player as a choice. Default labels (used when `label` is empty) are defined per direction: "Go north", "Go south", "Go east", "Go west", "Go up", "Go down".

---

## Cell

```java
public class Cell {
    public Optional<String> id();
    public int x();
    public int y();
    public int z();
    public String narrative();
    public List<SectionEvent> events();
    public ScriptBlock scripts();
    public Map<Direction, Passage> passages();
    public List<Choice> choices();
}
```

`id` is present only for cells that serve as named entry points. `passages` contains at most one entry per direction. `choices` holds non-navigation choices (e.g. "Search the room") that are presented alongside passage-derived choices.

Cell lifecycle hooks use the same `SectionHook` enum as sections: `ON_ENTER`, `ON_DISPLAY`, `ON_CHOICES`, `ON_EXIT`.

`Cell` exposes a static inner `Builder`. Required fields: `x`, `y`, `z`, `narrative`. Optional fields default to empty / `ScriptBlock.empty()`.

```java
Cell cell = Cell.builder().x(0).y(0).z(0).narrative("Cold stone floor.").passage(Direction.EAST, passage).build();
```

---

## Grid

```java
public class Grid {
    public String id();
    public int width();
    public int height();
    public int floors();
    public Optional<Cell> getCell(int x, int y, int z);
    public Optional<Cell> getCellById(String id);
    public List<Cell> cells();
}
```

`getCell` returns the cell at the given coordinates, or empty if the position is unoccupied (wall). `getCellById` resolves named entry points. Both return `Optional.empty()` rather than throwing on a miss.

`Grid` exposes a static inner `Builder`. Required fields: `id`. Dimensions (`width`, `height`, `floors`) default to the bounding box of the added cells.

```java
Grid grid = Grid.builder().id("dungeon-level-1").cell(cell).cell(cell2).build();
```

---

## Passage Resolution

When the player selects a passage-derived choice, the engine resolves the target as follows:

1. If `passage.toSection()` is present → call `GameState.navigateTo(section)`, exiting the grid.
2. Otherwise → compute the target coordinates `(x + direction.dx(), y + direction.dy(), z + direction.dz())` and call `GameState.navigateToCell(grid, targetCell)`.

The engine builds the choice list for a cell by:

1. Evaluating declarative conditions on each passage — hidden if not met.
2. Converting each visible passage to a `Choice` (using label or default, `SectionTarget` or `GridTarget` is not applicable here — passages produce internal navigation handled by the engine directly, not via `ChoiceTarget`).
3. Appending the cell's explicit `choices` list.
4. Firing `ON_CHOICES` hook on the combined list.

---

## Test Strategy

| What | Approach |
|------|----------|
| `Direction.dx/dy/dz` | Assert each direction returns correct deltas |
| `Direction.opposite` | Assert all six inverse pairs |
| `Grid.getCell` | Grid with known cells → assert correct cell returned, empty for unoccupied position |
| `Grid.getCellById` | Named cell lookup → assert correct cell; unknown id → empty |
| `Passage` default label | Passage with no label → engine uses direction default |
| `Passage` with condition | Condition not met → passage hidden from choice list |
| `Passage` with `toSection` | Player selects → `state.isInGrid() == false`, correct section |
| Cell with events | `InMemoryAdventureLoader` + cell with `StatChangeEvent` → stat modified on entry |
| Cell `onEnter` script | Cell with `onEnter` → script fires before narrative |
| Cell `onChoices` script | Cell with `onChoices` adding a choice → choice appears in list |
| Cell explicit choices | Cell with `choices` list → appear alongside passage choices |
