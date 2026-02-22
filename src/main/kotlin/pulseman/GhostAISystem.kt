package pulseman

import kotlin.math.*

/**
 * Core ghost AI system implementing classic Pulse-Man mechanics.
 *
 * This system manages ghost movement, targeting logic, and behavioral modes:
 * - SCATTER: Ghosts head toward their respective home corners.
 * - CHASE: Ghosts target Pulse-Man with unique personalities (Blinky, Pinky, Inky, Clyde).
 * - FRIGHTENED: Ghosts move randomly at a slower speed.
 * - EATEN: Ghost eyes return to the ghost house for revival using BFS pathfinding.
 *
 * Game rules implemented:
 * - Wave timing: SCATTER and CHASE modes alternate per the ROM-accurate [LevelProgression.SCATTER_CHASE_PATTERNS].
 * - Ghost release: Ghosts are released based on per-level dot counters from [LevelProgression].
 * - Elroy mode: Blinky's speed increases as dots remaining decrease, using per-level thresholds from [LevelSpec].
 * - Tunnel slowdown: Ghosts move at the per-level tunnel speed fraction while inside tunnel corridors.
 * - Path caching: BFS results for EATEN ghosts are cached to optimize performance.
 */
class GhostAISystem(
    private val pulseMan: PulseManController,
    private val gameSpeedScale: Float,
) {
    val ghosts = mutableListOf<GhostState>()

    /** Remaining duration of the current FRIGHTENED mode. */
    var frightenedTimer = 0f
        private set

    /** Number of blue/white flashes before frightened mode ends on the current level. */
    var frightenedFlashes = 5
        private set

    /** Counter for ghosts eaten during a single power pellet effect, used to calculate score multipliers. */
    var pelletsEatenForGhostScore = 0
    var dotsRemaining: Int = Int.MAX_VALUE

    private val maxSpeed = LevelProgression.MAX_SPEED * gameSpeedScale
    private var ghostModeTimer = 0f
    private var ghostModeIndex = 0
    private var currentGhostMode = GhostMode.SCATTER
    /** Timers used as a fallback to release ghosts from the house if dots aren't being eaten. */
    private var ghostReleaseTimers = floatArrayOf(0f, 0f, 0f, 0f)
    /**
     * Per-level dot count thresholds for releasing ghosts (Blinky, Pinky, Inky, Clyde).
     * Blinky always starts outside (threshold 0). Values are loaded from [LevelProgression] in [startLevel].
     */
    private var dotReleaseThresholds = intArrayOf(0, 0, 0, 0)
    private val cachedEatenDir = arrayOfNulls<Direction>(4)
    private val cachedEatenFrom = Array(4) { intArrayOf(-1, -1) }

    private val eatenGhostSpeed = 12f * gameSpeedScale

    /**
     * Scatter/chase wave sequence for the current level, loaded from [LevelProgression.SCATTER_CHASE_PATTERNS].
     * Alternates: scatter, chase, scatter, chase, ... with the final chase being infinite.
     */
    private var modeSequence: List<Pair<GhostMode, Float>> = emptyList()

    /**
     * Initializes the AI system for a new level.
     * Loads ROM-accurate scatter/chase timing and ghost release thresholds from [LevelProgression].
     */
    fun startLevel(level: Int) {
        ghostModeIndex = 0
        frightenedTimer = 0f
        pelletsEatenForGhostScore = 0
        clearEatenPathCache()

        val spec = LevelProgression.forLevel(level)
        frightenedFlashes = spec.frightFlashes
        val pattern = LevelProgression.SCATTER_CHASE_PATTERNS[spec.scatterChasePattern]
        modeSequence = buildModeSequence(pattern)
        currentGhostMode = modeSequence[0].first
        ghostModeTimer = modeSequence[0].second

        val limits = dotLimitsForLevel(level)
        dotReleaseThresholds = intArrayOf(0, limits[0], limits[1], limits[2])

        val forceOutBase = if (level <= 4) 4f else 3f
        ghostReleaseTimers = floatArrayOf(0f, forceOutBase, forceOutBase * 2f, forceOutBase * 3f)
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
     * Ghosts immediately reverse direction and move at the per-level reduced speed.
     * If [LevelSpec.frightTime] is 0 for this level, ghosts reverse but do not turn blue.
     */
    fun activateFrightened(level: Int) {
        val spec = LevelProgression.forLevel(level)
        frightenedTimer = spec.frightTime
        frightenedFlashes = spec.frightFlashes
        pelletsEatenForGhostScore = 0
        for (ghost in ghosts) {
            if (ghost.mode != GhostMode.EATEN && ghost.released) {
                if (spec.frightTime > 0f) {
                    ghost.mode = GhostMode.FRIGHTENED
                }
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
        val spec = LevelProgression.forLevel(level)
        val dotsEaten = (Maze.totalDots() - dotsRemaining).coerceAtLeast(0)
        for (i in ghosts.indices) {
            val ghost = ghosts[i]
            if (!ghost.released) {
                ghost.releaseTimer -= dt
                val dotThreshold = dotReleaseThresholds[i]
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

            val baseSpeed = spec.ghostSpeed * maxSpeed
            val speed = when (ghost.mode) {
                GhostMode.FRIGHTENED -> spec.ghostFrightSpeed * maxSpeed
                GhostMode.EATEN     -> eatenGhostSpeed
                else                -> elroySpeed(ghost.type, baseSpeed, spec)
            }

            val inTunnel = ghost.mode != GhostMode.EATEN &&
                ghost.gridY == 13 && (ghost.gridX <= 5 || ghost.gridX >= 22)
            val tunnelFactor = if (inTunnel) (spec.ghostTunnelSpeed / spec.ghostSpeed) else 1f

            ghost.progress += speed * tunnelFactor * dt
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

    /**
     * Returns Blinky's speed accounting for Elroy 1 and Elroy 2 thresholds from [spec].
     * Non-Blinky ghosts always return [baseSpeed].
     */
    private fun elroySpeed(type: GhostType, baseSpeed: Float, spec: LevelSpec): Float {
        if (type != GhostType.BLINKY) return baseSpeed
        return when {
            dotsRemaining <= spec.elroy2Dots -> spec.elroy2Speed * maxSpeed
            dotsRemaining <= spec.elroy1Dots -> spec.elroy1Speed * maxSpeed
            else                             -> baseSpeed
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
            GhostType.BLINKY -> intArrayOf(pulseMan.gridX, pulseMan.gridY)
            GhostType.PINKY -> intArrayOf(pulseMan.gridX + pulseMan.dir.dx * 4, pulseMan.gridY + pulseMan.dir.dy * 4)
            GhostType.INKY -> {
                val blinky = ghosts.firstOrNull { it.type == GhostType.BLINKY }
                if (blinky != null) {
                    val ax = pulseMan.gridX + pulseMan.dir.dx * 2
                    val ay = pulseMan.gridY + pulseMan.dir.dy * 2
                    intArrayOf(ax + (ax - blinky.gridX), ay + (ay - blinky.gridY))
                } else intArrayOf(pulseMan.gridX, pulseMan.gridY)
            }

            GhostType.CLYDE -> {
                val dist = distSq(ghost.gridX, ghost.gridY, pulseMan.gridX, pulseMan.gridY)
                if (dist > 64) intArrayOf(pulseMan.gridX, pulseMan.gridY) else Maze.SCATTER_TARGETS[ghost.type.ordinal]
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

    /**
     * Builds a [GhostMode]/duration sequence from a flat [pattern] array
     * (alternating scatter/chase durations from [LevelProgression.SCATTER_CHASE_PATTERNS]).
     */
    private fun buildModeSequence(pattern: FloatArray): List<Pair<GhostMode, Float>> {
        val seq = mutableListOf<Pair<GhostMode, Float>>()
        for (i in pattern.indices) {
            val mode = if (i % 2 == 0) GhostMode.SCATTER else GhostMode.CHASE
            seq.add(mode to pattern[i])
        }
        return seq
    }

    /**
     * Returns the per-level ghost dot-release limits as [pinky, inky, clyde].
     * Sourced from the arcade dossier: level 1 = [0, 30, 60], level 2 = [0, 0, 50], level 3+ = [0, 0, 0].
     */
    private fun dotLimitsForLevel(level: Int): IntArray = when (level) {
        1    -> intArrayOf(0, 30, 60)
        2    -> intArrayOf(0, 0, 50)
        else -> intArrayOf(0, 0, 0)
    }
}
