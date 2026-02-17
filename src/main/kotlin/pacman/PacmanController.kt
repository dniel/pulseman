package pacman

import kotlin.math.*

class PacmanController(
    private val pacSpeed: Float,
    private val gameSpeedScale: Float,
) {
    var gridX = Maze.PAC_START_X
        private set
    var gridY = Maze.PAC_START_Y
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

    fun reset(startX: Int = Maze.PAC_START_X, startY: Int = Maze.PAC_START_Y, startDir: Direction = Direction.NONE) {
        gridX = startX
        gridY = startY
        dir = startDir
        nextDir = Direction.NONE
        progress = 0f
    }

    fun updatePacman(
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

        progress += pacSpeed * dt
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

    fun updateAttractPacmanControl(ghosts: List<GhostState>) {
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

    private fun scoreAttractDirection(direction: Direction, choiceCount: Int, ghosts: List<GhostState>): Float {
        val nxRaw = gridX + direction.dx
        val ny = gridY + direction.dy
        if (ny !in 0 until Maze.ROWS) return Float.MAX_VALUE
        val nx = if (nxRaw < 0 || nxRaw >= Maze.COLS) Maze.wrapCol(nxRaw) else nxRaw

        var score = 0f
        when (Maze.grid[ny][nx]) {
            Maze.POWER -> score -= 220f
            Maze.DOT -> score -= 160f
            else -> score += 22f
        }

        score += nearestDotDistance(nx, ny) * 7f

        for (ghost in ghosts) {
            if (!ghost.released) continue
            val gx = ghost.gridX
            val gy = ghost.gridY
            val dxRaw = abs(nx - gx)
            val dx = min(dxRaw, Maze.COLS - dxRaw)
            val dist = dx + abs(ny - gy)

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

        if (choiceCount > 1 && direction == dir.opposite()) {
            score += 18f
        }
        return score
    }

    private fun nearestDotDistance(startCol: Int, startRow: Int): Int {
        var nearest = Int.MAX_VALUE
        for (row in 0 until Maze.ROWS) {
            for (col in 0 until Maze.COLS) {
                val tile = Maze.grid[row][col]
                if (tile != Maze.DOT && tile != Maze.POWER) continue
                val dxRaw = abs(col - startCol)
                val dx = min(dxRaw, Maze.COLS - dxRaw)
                val distance = dx + abs(row - startRow)
                if (distance < nearest) nearest = distance
            }
        }
        return if (nearest == Int.MAX_VALUE) 0 else nearest
    }

    fun updateMouthAnimation(dt: Float) {
        val speed = 12.0f * gameSpeedScale
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

    fun pixelX(): Float = Maze.centerX(gridX) + dir.dx * progress * Maze.TILE
    fun pixelY(): Float = Maze.centerY(gridY) + dir.dy * progress * Maze.TILE
}
