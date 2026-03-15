package pulseman

import kotlin.math.*

/**
 * Controls Pulse-Man's movement, animation, and AI behavior during attract mode.
 *
 * This controller manages grid-based movement, mouth animation cycles,
 * and includes a 3-step lookahead AI used when the game is in attract mode.
 *
 * Movement speed is set externally via [currentSpeed] so that [PulseManGame] can apply
 * the correct per-level speed from [LevelProgression] (normal, frightened, or dots speed).
 */
class PulseManController(
    normalSpeed: Float,
    private val gameSpeedScale: Float,
) {
    private var cachedNearestDotGrid: Array<IntArray>? = null

    var gridX = Maze.PULSE_START_X
        private set
    var gridY = Maze.PULSE_START_Y
        private set
    var dir = Direction.NONE
        private set
    var nextDir = Direction.NONE
    var progress = 0f
        private set
    var mouthAngle = 0.25f
        private set
    var mouthOpening = true
        private set

    /**
     * The speed currently applied to Pulse-Man's movement, in engine tiles per second
     * (before [gameSpeedScale] is applied internally — this value already includes scale).
     * Updated by [PulseManGame] each tick based on the current level and game state.
     */
    var currentSpeed: Float = normalSpeed

    /**
     * Resets Pulse-Man to a specified starting position and direction.
     */
    fun reset(startX: Int = Maze.PULSE_START_X, startY: Int = Maze.PULSE_START_Y, startDir: Direction = Direction.NONE) {
        gridX = startX
        gridY = startY
        dir = startDir
        nextDir = Direction.NONE
        progress = 0f
    }

    /**
     * Updates Pulse-Man's position based on [currentSpeed].
     * Handles wrapping at tunnel exits and triggers dot consumption and fruit checks.
     */
    fun updatePulseMan(
        dt: Float,
        onDotEaten: (col: Int, row: Int) -> Unit,
        onFruitCheck: (col: Int, row: Int) -> Unit,
    ) {
        if (dir == Direction.NONE) {
            if (nextDir != Direction.NONE && Maze.isWalkable(gridX + nextDir.dx, gridY + nextDir.dy)) {
                dir = nextDir
            }
            return
        }

        progress += currentSpeed * dt
        if (progress < 1f) return

        val newCol = Maze.wrapCol(gridX + dir.dx)
        val newRow = gridY + dir.dy
        gridX = newCol
        gridY = newRow
        progress = 0f

        onDotEaten(gridX, gridY)
        onFruitCheck(gridX, gridY)

        val canTurn = nextDir != Direction.NONE &&
            nextDir != dir &&
            Maze.isWalkable(gridX + nextDir.dx, gridY + nextDir.dy)
        if (canTurn) {
            dir = nextDir
        } else if (!Maze.isWalkable(Maze.wrapCol(gridX + dir.dx), gridY + dir.dy)) {
            dir = Direction.NONE
        }
    }

    /**
     * AI logic for attract mode that looks ahead up to 3 tiles to decide the best direction.
     * Evaluates positions based on proximity to dots, power pellets, and ghosts.
     */
    fun updateAttractPulseManControl(ghosts: List<GhostState>) {
        val choices = Maze.availableDirections(gridX, gridY)
        if (choices.isEmpty()) {
            nextDir = Direction.NONE
            return
        }

        val best = choices.minByOrNull { direction -> scoreAttractDirection(direction, choices.size, ghosts) }
        if (best != null) {
            nextDir = best
        }
    }

    /**
     * Invalidates the cached BFS grid used for finding the nearest dot.
     */
    fun invalidateDotCache() {
        cachedNearestDotGrid = null
    }

    private fun scoreAttractDirection(direction: Direction, choiceCount: Int, ghosts: List<GhostState>): Float {
        val nxRaw = gridX + direction.dx
        val ny = gridY + direction.dy
        if (ny !in 0 until Maze.ROWS) return Float.MAX_VALUE
        val nx = if (nxRaw < 0 || nxRaw >= Maze.COLS) Maze.wrapCol(nxRaw) else nxRaw

        var score = scorePosition(nx, ny, ghosts)

        val step2 = bestNextStep(nx, ny, direction, ghosts)
        if (step2 != null) {
            score += step2.score * 0.5f
            val step3 = bestNextStep(step2.col, step2.row, step2.direction, ghosts)
            if (step3 != null) {
                score += step3.score * 0.25f
            }
        }

        if (choiceCount > 1 && direction == dir.opposite()) {
            score += 18f
        }
        return score
    }

    private fun scorePosition(col: Int, row: Int, ghosts: List<GhostState>): Float {
        var score = 0f
        when (Maze.grid[row][col]) {
            Maze.POWER -> score -= 220f
            Maze.DOT -> score -= 160f
            else -> score += 22f
        }

        score += nearestDotDistance(col, row) * 7f

        for (ghost in ghosts) {
            if (!ghost.released) continue
            val gx = ghost.gridX
            val gy = ghost.gridY
            val dxRaw = abs(col - gx)
            val dx = min(dxRaw, Maze.COLS - dxRaw)
            val dist = dx + abs(row - gy)

            when (ghost.mode) {
                GhostMode.EATEN -> score += 2f
                GhostMode.FRIGHTENED -> score -= (12f / (dist + 1f))
                else -> {
                    if (dist <= 1) score += 3000f
                    else if (dist == 2) score += 700f
                    else if (dist == 3) score += 200f
                    else score += 30f / dist
                }
            }
        }

        return score
    }

    private fun bestNextStep(col: Int, row: Int, prevDirection: Direction, ghosts: List<GhostState>): StepScore? {
        val dirs = Maze.availableDirections(col, row).filter { it != prevDirection.opposite() }
        if (dirs.isEmpty()) return null

        var best: StepScore? = null
        for (direction in dirs) {
            val nxRaw = col + direction.dx
            val ny = row + direction.dy
            if (ny !in 0 until Maze.ROWS) continue
            val nx = if (nxRaw < 0 || nxRaw >= Maze.COLS) Maze.wrapCol(nxRaw) else nxRaw
            val tileScore = scorePosition(nx, ny, ghosts)
            if (best == null || tileScore < best.score) {
                best = StepScore(nx, ny, tileScore, direction)
            }
        }
        return best
    }

    private fun nearestDotDistance(startCol: Int, startRow: Int): Int {
        val distances = cachedNearestDotGrid ?: buildNearestDotGrid().also { cachedNearestDotGrid = it }
        val nearest = distances[startRow][startCol]
        return if (nearest == Int.MAX_VALUE) 0 else nearest
    }

    private fun buildNearestDotGrid(): Array<IntArray> {
        val distances = Array(Maze.ROWS) { IntArray(Maze.COLS) { Int.MAX_VALUE } }
        val queue = ArrayDeque<Pair<Int, Int>>()

        for (row in 0 until Maze.ROWS) {
            for (col in 0 until Maze.COLS) {
                val tile = Maze.grid[row][col]
                if (tile == Maze.DOT || tile == Maze.POWER) {
                    distances[row][col] = 0
                    queue.add(col to row)
                }
            }
        }

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            val nextDistance = distances[cy][cx] + 1

            for (direction in Direction.entries) {
                if (direction == Direction.NONE) continue
                var nx = cx + direction.dx
                val ny = cy + direction.dy
                if (!Maze.isWalkable(nx, ny)) continue
                if (nx < 0 || nx >= Maze.COLS) nx = Maze.wrapCol(nx)
                if (ny !in 0 until Maze.ROWS) continue
                if (nextDistance >= distances[ny][nx]) continue
                distances[ny][nx] = nextDistance
                queue.add(nx to ny)
            }
        }

        return distances
    }

    private data class StepScore(val col: Int, val row: Int, val score: Float, val direction: Direction)

    /**
     * Updates the mouth opening/closing animation based on delta time.
     */
    fun updateMouthAnimation(dt: Float) {
        val speed = 6.0f * gameSpeedScale
        if (mouthOpening) {
            mouthAngle += speed * dt
            if (mouthAngle >= 1f) {
                mouthAngle = 1f
                mouthOpening = false
            }
        } else {
            mouthAngle -= speed * dt
            if (mouthAngle <= 0.15f) {
                mouthAngle = 0.15f
                mouthOpening = true
            }
        }
    }

    /** Returns Pulse-Man's current X coordinate in world pixels. */
    fun pixelX(): Float = Maze.centerX(gridX) + dir.dx * progress * Maze.TILE
    /** Returns Pulse-Man's current Y coordinate in world pixels. */
    fun pixelY(): Float = Maze.centerY(gridY) + dir.dy * progress * Maze.TILE
}
