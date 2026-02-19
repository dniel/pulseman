package pacman

import kotlin.math.*

/**
 * Core ghost AI system implementing classic Pac-Man mechanics.
 *
 * This system manages ghost movement, targeting logic, and behavioral modes:
 * - SCATTER: Ghosts head toward their respective home corners.
 * - CHASE: Ghosts target Pac-Man with unique personalities (Blinky, Pinky, Inky, Clyde).
 * - FRIGHTENED: Ghosts move randomly at a slower speed.
 * - EATEN: Ghost eyes return to the ghost house for revival using BFS pathfinding.
 *
 * Game rules implemented:
 * - Wave timing: SCATTER and CHASE modes alternate in 7 predefined waves per level.
 * - Ghost release: Ghosts are released based on dot counters (Blinky=0, Pinky=7, Inky=17, Clyde=32)
 *   or a time fallback mechanism.
 * - Elroy mode: Blinky's speed increases as dots remaining decrease (≤20 dots: +5%, ≤10 dots: +15%).
 * - Tunnel slowdown: Ghosts move at 60% speed while inside tunnel corridors, unless in EATEN mode.
 * - Path caching: BFS results for EATEN ghosts are cached to optimize performance.
 */
class GhostAISystem(
    private val pacman: PacmanController,
    private val gameSpeedScale: Float,
) {
    val ghosts = mutableListOf<GhostState>()

    /** Remaining duration of the current FRIGHTENED mode. */
    var frightenedTimer = 0f
        private set

    /** Counter for ghosts eaten during a single power pellet effect, used to calculate score multipliers. */
    var pelletsEatenForGhostScore = 0
    var dotsRemaining: Int = Int.MAX_VALUE

    private var ghostModeTimer = 0f
    private var ghostModeIndex = 0
    private var currentGhostMode = GhostMode.SCATTER
    /** Timers used as a fallback to release ghosts from the house if dots aren't being eaten. */
    private var ghostReleaseTimers = floatArrayOf(0f, 3f, 6f, 9f)
    /** Dot count thresholds for releasing ghosts (Blinky, Pinky, Inky, Clyde). */
    private val dotReleaseThresholds = intArrayOf(0, 7, 17, 32)
    private val cachedEatenDir = arrayOfNulls<Direction>(4)
    private val cachedEatenFrom = Array(4) { intArrayOf(-1, -1) }

    private val eatenGhostSpeed = 12f * gameSpeedScale
    /** Sequence of SCATTER/CHASE waves and their durations in seconds. */
    private val modeSequence = listOf(
        GhostMode.SCATTER to 7f,
        GhostMode.CHASE to 20f,
        GhostMode.SCATTER to 7f,
        GhostMode.CHASE to 20f,
        GhostMode.SCATTER to 5f,
        GhostMode.CHASE to Float.MAX_VALUE,
    )

    /**
     * Initializes the AI system for a new level.
     * Resets wave timers and calculates ghost release delays based on the current level.
     */
    fun startLevel(level: Int) {
        ghostModeIndex = 0
        currentGhostMode = modeSequence[0].first
        ghostModeTimer = modeSequence[0].second
        frightenedTimer = 0f
        pelletsEatenForGhostScore = 0
        clearEatenPathCache()
        ghostReleaseTimers = floatArrayOf(0f, 3f, 6f, 9f)
            .map { max(0f, it - (level - 1) * 0.25f) }
            .toFloatArray()
    }

    /**
     * Resets all ghosts to their starting positions and states.
     */
    fun resetPositions() {
        clearEatenPathCache()
        ghosts.clear()
        for (i in 0 until 4) {
            val start = Maze.GHOST_STARTS[i]
            ghosts.add(
                GhostState(
                    type = GhostType.entries[i],
                    gridX = start[0],
                    gridY = start[1],
                    direction = Direction.LEFT,
                    mode = GhostMode.SCATTER,
                    progress = 0f,
                    released = i == 0,
                    releaseTimer = ghostReleaseTimers[i],
                )
            )
        }
    }

    /**
     * Activates FRIGHTENED mode for all released ghosts.
     * Ghosts immediately reverse direction and move at a reduced speed.
     */
    fun activateFrightened(level: Int) {
        frightenedTimer = frightenedDurationForLevel(level)
        pelletsEatenForGhostScore = 0
        for (ghost in ghosts) {
            if (ghost.mode != GhostMode.EATEN && ghost.released) {
                ghost.mode = GhostMode.FRIGHTENED
                ghost.direction = ghost.direction.opposite()
                ghost.progress = 1f - ghost.progress
            }
        }
    }

    /**
     * Updates the ghost AI state, including mode transitions, release mechanics, and movement logic.
     */
    fun update(dt: Float, level: Int) {
        updateGhostModes(dt, level)
        updateGhosts(dt, level)
    }

    private fun updateGhostModes(dt: Float, level: Int) {
        if (frightenedTimer > 0f) {
            frightenedTimer -= dt
            if (frightenedTimer <= 0f) {
                frightenedTimer = 0f
                for (ghost in ghosts) {
                    if (ghost.mode == GhostMode.FRIGHTENED) ghost.mode = currentGhostMode
                }
            }
            return
        }

        ghostModeTimer -= dt
        if (ghostModeTimer <= 0f && ghostModeIndex < modeSequence.size - 1) {
            ghostModeIndex++
            currentGhostMode = modeSequence[ghostModeIndex].first
            ghostModeTimer = modeSequence[ghostModeIndex].second
            for (ghost in ghosts) {
                if (ghost.mode != GhostMode.EATEN && ghost.mode != GhostMode.FRIGHTENED) {
                    ghost.direction = ghost.direction.opposite()
                    ghost.progress = 1f - ghost.progress
                    ghost.mode = currentGhostMode
                }
            }
        }
    }

    private fun updateGhosts(dt: Float, level: Int) {
        val dotsEaten = (Maze.totalDots() - dotsRemaining).coerceAtLeast(0)
        for (i in ghosts.indices) {
            val ghost = ghosts[i]
            if (!ghost.released) {
                ghost.releaseTimer -= dt
                val dotThreshold = max(0, dotReleaseThresholds[i] - (level - 1) * 2)
                if (ghost.releaseTimer <= 0f || dotsEaten >= dotThreshold) {
                    ghost.released = true
                    ghost.gridX = 14
                    ghost.gridY = 10
                    ghost.progress = 0f
                    ghost.direction = Direction.LEFT
                    ghost.mode = currentGhostMode
                }
                continue
            }

            val speed = when (ghost.mode) {
                GhostMode.FRIGHTENED -> frightenedGhostSpeedForLevel(level)
                GhostMode.EATEN -> eatenGhostSpeed
                else -> ghostSpeedForLevel(level, ghost.type)
            }
            val tunnelSlowdown = if (ghost.mode != GhostMode.EATEN && ghost.gridY == 13 && (ghost.gridX <= 5 || ghost.gridX >= 22)) 0.6f else 1f

            ghost.progress += speed * tunnelSlowdown * dt
            if (ghost.progress < 1f) continue

            val newCol = Maze.wrapCol(ghost.gridX + ghost.direction.dx)
            val newRow = ghost.gridY + ghost.direction.dy
            ghost.gridX = newCol
            ghost.gridY = newRow
            ghost.progress = 0f

            if (ghost.mode == GhostMode.EATEN && ghost.gridX in 11..16 && ghost.gridY in 12..14) {
                ghost.mode = currentGhostMode
                ghost.gridX = 14
                ghost.gridY = 14
            }

            chooseGhostDirection(i, ghost)
        }
    }

    private fun chooseGhostDirection(idx: Int, ghost: GhostState) {
        val canUseDoor = ghost.mode == GhostMode.EATEN || (ghost.gridY in 12..14 && ghost.gridX in 10..17)
        val available = Maze.ghostAvailableDirections(ghost.gridX, ghost.gridY, canUseDoor)
            .let { dirs -> if (ghost.mode == GhostMode.EATEN) dirs else dirs.filter { it != ghost.direction.opposite() } }

        if (available.isEmpty()) {
            ghost.direction = ghost.direction.opposite()
            return
        }

        if (ghost.mode == GhostMode.FRIGHTENED) {
            ghost.direction = available.random()
            return
        }

        val target = getGhostTarget(ghost)
        if (ghost.mode == GhostMode.EATEN) {
            val from = cachedEatenFrom[idx]
            val pathDir = if (from[0] == ghost.gridX && from[1] == ghost.gridY) {
                cachedEatenDir[idx]
            } else {
                nextGhostStepTowards(ghost.gridX, ghost.gridY, target[0], target[1], canUseDoor = true).also { dir ->
                    cachedEatenDir[idx] = dir
                    from[0] = ghost.gridX
                    from[1] = ghost.gridY
                }
            }
            if (pathDir != null) {
                ghost.direction = pathDir
                return
            }
        }

        ghost.direction = available.minByOrNull { dir ->
            val tx = ghost.gridX + dir.dx
            val ty = ghost.gridY + dir.dy
            distSq(tx, ty, target[0], target[1])
        } ?: available.first()
    }

    private fun nextGhostStepTowards(startX: Int, startY: Int, targetX: Int, targetY: Int, canUseDoor: Boolean): Direction? {
        if (startX == targetX && startY == targetY) return null

        val visited = Array(Maze.ROWS) { BooleanArray(Maze.COLS) }
        val firstDir = Array(Maze.ROWS) { arrayOfNulls<Direction>(Maze.COLS) }
        val queue = ArrayDeque<Pair<Int, Int>>()

        visited[startY][startX] = true
        queue.add(startX to startY)

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            for (dir in Direction.entries) {
                if (dir == Direction.NONE) continue

                var nx = cx + dir.dx
                val ny = cy + dir.dy
                if (!Maze.isGhostWalkable(nx, ny, canUseDoor)) continue
                if (nx < 0 || nx >= Maze.COLS) nx = Maze.wrapCol(nx)
                if (ny !in 0 until Maze.ROWS || visited[ny][nx]) continue

                val first = if (cx == startX && cy == startY) dir else firstDir[cy][cx] ?: dir
                firstDir[ny][nx] = first
                visited[ny][nx] = true

                if (nx == targetX && ny == targetY) return first
                queue.add(nx to ny)
            }
        }

        return null
    }

    private fun getGhostTarget(ghost: GhostState): IntArray {
        if (ghost.mode == GhostMode.SCATTER) return Maze.SCATTER_TARGETS[ghost.type.ordinal]
        if (ghost.mode == GhostMode.EATEN) return intArrayOf(14, 13)

        return when (ghost.type) {
            GhostType.BLINKY -> intArrayOf(pacman.gridX, pacman.gridY)
            GhostType.PINKY -> intArrayOf(pacman.gridX + pacman.dir.dx * 4, pacman.gridY + pacman.dir.dy * 4)
            GhostType.INKY -> {
                val blinky = ghosts.firstOrNull { it.type == GhostType.BLINKY }
                if (blinky != null) {
                    val ax = pacman.gridX + pacman.dir.dx * 2
                    val ay = pacman.gridY + pacman.dir.dy * 2
                    intArrayOf(ax + (ax - blinky.gridX), ay + (ay - blinky.gridY))
                } else intArrayOf(pacman.gridX, pacman.gridY)
            }

            GhostType.CLYDE -> {
                val dist = distSq(ghost.gridX, ghost.gridY, pacman.gridX, pacman.gridY)
                if (dist > 64) intArrayOf(pacman.gridX, pacman.gridY) else Maze.SCATTER_TARGETS[ghost.type.ordinal]
            }
        }
    }

    private fun distSq(x1: Int, y1: Int, x2: Int, y2: Int): Float {
        val dx = (x1 - x2).toFloat()
        val dy = (y1 - y2).toFloat()
        return dx * dx + dy * dy
    }

    private fun clearEatenPathCache() {
        for (i in cachedEatenDir.indices) {
            cachedEatenDir[i] = null
            cachedEatenFrom[i][0] = -1
            cachedEatenFrom[i][1] = -1
        }
    }

    fun ghostPixelX(g: GhostState): Float = Maze.centerX(g.gridX) + g.direction.dx * g.progress * Maze.TILE
    fun ghostPixelY(g: GhostState): Float = Maze.centerY(g.gridY) + g.direction.dy * g.progress * Maze.TILE

    private fun baseGhostSpeedForLevel(level: Int): Float = (6f + (level - 1) * 0.3f).coerceAtMost(9f) * gameSpeedScale
    private fun ghostSpeedForLevel(level: Int, ghostType: GhostType): Float {
        val baseSpeed = baseGhostSpeedForLevel(level)
        if (ghostType != GhostType.BLINKY) return baseSpeed
        return when {
            dotsRemaining <= 10 -> baseSpeed * 1.15f
            dotsRemaining <= 20 -> baseSpeed * 1.05f
            else -> baseSpeed
        }
    }
    private fun frightenedDurationForLevel(level: Int): Float = max(3f, 8f - (level - 1) * 0.5f)
    private fun frightenedGhostSpeedForLevel(level: Int): Float = baseGhostSpeedForLevel(level) * 0.58f
}
