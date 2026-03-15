package pulseman

/**
 * The game maze — a 28×28 tile grid defining the playfield layout, walkability, and coordinate mapping.
 *
 * The maze is parsed from a compact ASCII layout string (provided by a [MazeLayout]) where each
 * character maps to a tile type (wall, dot, power pellet, empty, ghost house, ghost door, or void).
 *
 * ### Tile types
 * | Constant     | Value | Description                                      |
 * |--------------|-------|--------------------------------------------------|
 * | [WALL]       | 0     | Impassable wall segment                          |
 * | [DOT]        | 1     | Standard dot (10 pts)                            |
 * | [POWER]      | 2     | Power pellet (50 pts, triggers frightened mode)   |
 * | [EMPTY]      | 3     | Walkable but contains nothing                    |
 * | [GHOST_HOUSE] | 4    | Interior of the ghost pen                        |
 * | [GHOST_DOOR] | 5     | One-way door ghosts pass through when leaving    |
 * | [VOID]       | 6     | Outside the playfield (not rendered or walkable)  |
 *
 * ### Coordinate system
 * Grid positions are (col, row) with (0,0) at top-left. Pixel positions are calculated via
 * [tileX]/[tileY] (top-left corner) or [centerX]/[centerY] (tile center), offset by [OFFSET_X]
 * and [OFFSET_Y] to center the board on screen.
 *
 * ### Initialization
 * On first access, the [MazeLayouts.CLASSIC] layout is loaded. [loadLayout] can be called to
 * switch to a different layout. After parsing, [sealUnreachableTiles] runs a BFS flood-fill
 * from Pulse-Man's start position to convert any unreachable walkable tiles to walls.
 * [reset] copies the original grid so dots can be restored between levels.
 */
object Maze {

    /** Number of columns in the maze grid. */
    const val COLS = 28
    /** Number of rows in the maze grid. */
    const val ROWS = 28
    /** Side length of each tile in pixels. */
    const val TILE = 12
    /** Horizontal pixel offset to position the maze on screen. */
    const val OFFSET_X = 32f
    /** Vertical pixel offset to position the maze on screen. */
    const val OFFSET_Y = 30f

    const val WALL = 0
    const val DOT = 1
    const val POWER = 2
    const val EMPTY = 3
    const val GHOST_HOUSE = 4
    const val GHOST_DOOR = 5
    const val VOID = 6

    /** Pulse-Man's starting grid column. */
    const val PULSE_START_X = 14
    /** Pulse-Man's starting grid row. */
    const val PULSE_START_Y = 21

    /**
     * Starting grid positions for each ghost, indexed by [GhostType] ordinal.
     * Blinky starts above the ghost house; the other three start inside it.
     */
    val GHOST_STARTS = arrayOf(
        intArrayOf(14, 10),
        intArrayOf(13, 14),
        intArrayOf(11, 14),
        intArrayOf(16, 14),
    )

    /**
     * Corner scatter targets for each ghost, indexed by [GhostType] ordinal.
     * Ghosts retreat toward these corners during scatter mode.
     */
    val SCATTER_TARGETS = arrayOf(
        intArrayOf(25, 0),
        intArrayOf(2, 0),
        intArrayOf(27, 27),
        intArrayOf(0, 27),
    )

    /** The currently loaded maze layout, controlling grid structure and wall colors. */
    var currentLayout: MazeLayout = MazeLayouts.CLASSIC
        private set

    private var tunnelRows: Set<Int> = MazeLayouts.CLASSIC.tunnelRows

    var grid: Array<IntArray> = emptyArray()
        private set

    private var originalGrid: Array<IntArray> = emptyArray()
    private var _totalDots = 0

    init {
        loadLayout(MazeLayouts.CLASSIC)
    }

    /**
     * Loads a new maze layout, replacing the current grid.
     * Parses the ASCII layout, seals unreachable tiles via BFS, and resets the grid with fresh dots.
     */
    fun loadLayout(layout: MazeLayout) {
        currentLayout = layout
        tunnelRows = layout.tunnelRows
        originalGrid = buildGrid(layout.ascii)
        sealUnreachableTiles(originalGrid)
        reset()
    }

    /**
     * Parses a 28-row ASCII layout into a 2D integer grid.
     * All 28 rows are read directly — tunnel rows and ghost house rows are encoded inline.
     */
    private fun buildGrid(ascii: Array<String>): Array<IntArray> {
        val g = Array(ROWS) { IntArray(COLS) { VOID } }

        for (row in ascii.indices) {
            val line = ascii[row]
            for (col in 0 until COLS) {
                if (col >= line.length) {
                    g[row][col] = VOID
                    continue
                }
                g[row][col] = when (line[col]) {
                    '#' -> WALL
                    '.' -> DOT
                    'o' -> POWER
                    '_' -> EMPTY
                    'H' -> GHOST_HOUSE
                    '-' -> GHOST_DOOR
                    ' ' -> VOID
                    else -> VOID
                }
            }
        }

        return g
    }

    /** Restores the grid to its original state (all dots and power pellets replaced) and recounts total dots. */
    fun reset() {
        grid = Array(ROWS) { row -> originalGrid[row].copyOf() }
        _totalDots = 0
        for (row in grid) for (tile in row) {
            if (tile == DOT || tile == POWER) _totalDots++
        }
    }

    /** Returns the total number of dots and power pellets when the level started. */
    fun totalDots(): Int = _totalDots

    /** Counts the dots and power pellets still remaining on the grid (live scan). */
    fun dotsRemaining(): Int {
        var count = 0
        for (row in grid) for (tile in row) {
            if (tile == DOT || tile == POWER) count++
        }
        return count
    }

    /**
     * Whether Pulse-Man can walk on the given tile.
     * Out-of-bounds columns on tunnel rows are walkable (wrap-around); all other
     * out-of-bounds positions are not. Dots, power pellets, and empty tiles are walkable.
     */
    fun isWalkable(col: Int, row: Int): Boolean {
        if (row in 0 until ROWS && (col < 0 || col >= COLS)) return isTunnelRow(row)
        if (col !in 0 until COLS || row !in 0 until ROWS) return false
        return when (grid[row][col]) {
            DOT, POWER, EMPTY -> true
            else -> false
        }
    }

    /**
     * Whether a ghost can walk on the given tile.
     * Same as [isWalkable] but ghosts can also traverse [GHOST_HOUSE] tiles, and
     * may pass through the [GHOST_DOOR] when [canUseDoor] is true (leaving or returning to the pen).
     */
    fun isGhostWalkable(col: Int, row: Int, canUseDoor: Boolean = false): Boolean {
        if (row in 0 until ROWS && (col < 0 || col >= COLS)) return isTunnelRow(row)
        if (col !in 0 until COLS || row !in 0 until ROWS) return false
        return when (grid[row][col]) {
            DOT, POWER, EMPTY -> true
            GHOST_HOUSE -> true
            GHOST_DOOR -> canUseDoor
            else -> false
        }
    }

    private fun isTunnelRow(row: Int): Boolean = row in tunnelRows

    /** Wraps a column index for tunnel traversal (left edge → right, right edge → left). */
    fun wrapCol(col: Int): Int = when {
        col < 0 -> COLS - 1
        col >= COLS -> 0
        else -> col
    }

    /** Pixel X of a tile's left edge. */
    fun tileX(col: Int): Float = OFFSET_X + col * TILE
    /** Pixel Y of a tile's top edge. */
    fun tileY(row: Int): Float = OFFSET_Y + row * TILE
    /** Pixel X of a tile's center. */
    fun centerX(col: Int): Float = OFFSET_X + col * TILE + TILE / 2f
    /** Pixel Y of a tile's center. */
    fun centerY(row: Int): Float = OFFSET_Y + row * TILE + TILE / 2f

    /** Returns walkable directions for Pulse-Man from the given grid position. */
    fun availableDirections(col: Int, row: Int): List<Direction> =
        Direction.entries.filter { it != Direction.NONE && isWalkable(col + it.dx, row + it.dy) }

    /** Returns walkable directions for a ghost, optionally allowing passage through the ghost door. */
    fun ghostAvailableDirections(col: Int, row: Int, canUseDoor: Boolean = false): List<Direction> =
        Direction.entries.filter { it != Direction.NONE && isGhostWalkable(col + it.dx, row + it.dy, canUseDoor) }

    /** Whether the tile should be treated as a wall for maze outline rendering (walls and ghost house interior). */
    fun isWallForOutline(col: Int, row: Int): Boolean {
        if (col !in 0 until COLS || row !in 0 until ROWS) return false
        return when (grid[row][col]) {
            WALL, GHOST_HOUSE -> true
            else -> false
        }
    }

    private fun isPlayerTile(tile: Int) = tile == DOT || tile == POWER || tile == EMPTY

    /**
     * BFS flood-fill from Pulse-Man's start position. Any walkable tile not reachable
     * from the start is converted to [WALL] to prevent entities from entering dead zones
     * caused by the ASCII layout encoding.
     */
    private fun sealUnreachableTiles(g: Array<IntArray>) {
        val visited = Array(ROWS) { BooleanArray(COLS) }
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(PULSE_START_X to PULSE_START_Y)
        visited[PULSE_START_Y][PULSE_START_X] = true

        val dx = intArrayOf(0, 0, -1, 1)
        val dy = intArrayOf(-1, 1, 0, 0)

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            for (i in dx.indices) {
                var nx = cx + dx[i]
                val ny = cy + dy[i]
                if (ny !in 0 until ROWS) continue
                if (nx < 0 || nx >= COLS) {
                    if (!isTunnelRow(ny)) continue
                    nx = if (nx < 0) COLS - 1 else 0
                }
                if (visited[ny][nx]) continue
                if (!isPlayerTile(g[ny][nx])) continue
                visited[ny][nx] = true
                queue.add(nx to ny)
            }
        }

        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                if (isPlayerTile(g[row][col]) && !visited[row][col]) {
                    g[row][col] = WALL
                }
            }
        }
    }
}
