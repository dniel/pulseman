package pulseman

import no.njoh.pulseengine.core.graphics.surface.Surface
import kotlin.math.sin

/**
 * Renders the primary gameplay elements including the maze, Pulse-Man, ghosts, and fruit.
 *
 * This renderer handles the visual representation of the game world, including
 * wall outlines, pellet pulsing, and entity animations.
 */
class GameplayRenderer(
    private val pulseMan: PulseManController,
    private val ghostAI: GhostAISystem,
    private val fruitManager: FruitManager,
) {
    var wallBevelEnabled = true
    var wallOutlineEnabled = true
    var wallThinOutlineMode = false
    var wallBevelDebug = false

    /**
     * Renders the maze structure, dots, power pellets, and ghost house door.
     */
    fun renderMaze(s: Surface, uiPulseTime: Float) {
        val wallColor = Maze.currentLayout.wallColor
        val thickness = if (wallThinOutlineMode) 1f else 3f
        val wallBase = when {
            !wallBevelEnabled -> wallColor
            wallBevelDebug -> floatArrayOf(0.1f, 0.2f, 0.4f)
            else -> floatArrayOf(0.14f, 0.26f, 0.5f)
        }
        val pelletPulse = 0.5f + 0.5f * sin(uiPulseTime * 4.2f)

        val verticalOpen = Array(Maze.ROWS) { BooleanArray(Maze.COLS + 1) }
        val horizontalOpen = Array(Maze.ROWS + 1) { BooleanArray(Maze.COLS) }
        for (row in 0 until Maze.ROWS) {
            for (col in 0 until Maze.COLS) {
                if (!Maze.isWallForOutline(col, row)) continue
                if (!Maze.isWallForOutline(col - 1, row)) verticalOpen[row][col] = true
                if (!Maze.isWallForOutline(col + 1, row)) verticalOpen[row][col + 1] = true
                if (!Maze.isWallForOutline(col, row - 1)) horizontalOpen[row][col] = true
                if (!Maze.isWallForOutline(col, row + 1)) horizontalOpen[row + 1][col] = true
            }
        }

        for (row in 0 until Maze.ROWS) {
            for (col in 0 until Maze.COLS) {
                val x = Maze.tileX(col)
                val y = Maze.tileY(row)
                val tile = Maze.grid[row][col]

                if (Maze.isWalkable(col, row)) {
                    s.setDrawColor(0f, 0f, 0f, 1f)
                    s.drawQuad(x, y, Maze.TILE.toFloat(), Maze.TILE.toFloat())
                }

                when (tile) {
                    Maze.WALL -> {
                        val tileSize = Maze.TILE.toFloat()
                        s.setDrawColor(wallBase[0], wallBase[1], wallBase[2], 1f)
                        s.drawQuad(x, y, tileSize, tileSize)
                    }

                    Maze.DOT -> {
                        s.setDrawColor(1f, 0.95f, 0.78f, 0.18f + pelletPulse * 0.16f)
                        drawFilledCircle(s, Maze.centerX(col), Maze.centerY(row), 4.8f, 12)
                        s.setDrawColor(1f, 0.93f, 0.75f, 1f)
                        drawFilledCircle(s, Maze.centerX(col), Maze.centerY(row), 2.1f, 8)
                    }

                    Maze.POWER -> {
                        val cx = Maze.centerX(col)
                        val cy = Maze.centerY(row)
                        s.setDrawColor(1f, 0.98f, 0.86f, 0.28f + pelletPulse * 0.22f)
                        drawFilledCircle(s, cx, cy, 8.8f, 14)
                        s.setDrawColor(1f, 1f, 1f, 1f)
                        drawFilledCircle(s, cx, cy, 5f, 14)
                    }

                    Maze.GHOST_DOOR -> {
                        s.setDrawColor(1f, 0.7f, 0.8f, 1f)
                        s.drawQuad(x, y + Maze.TILE / 2f - 2f, Maze.TILE.toFloat(), 4f)
                    }
                }
            }
        }

        if (wallOutlineEnabled) {
            s.setDrawColor(wallColor[0], wallColor[1], wallColor[2], 1f)
            for (row in 0 until Maze.ROWS) {
                for (xEdge in 0..Maze.COLS) {
                    if (!verticalOpen[row][xEdge]) continue
                    val x0 = if (xEdge == 0) Maze.tileX(0) else Maze.tileX(xEdge) - thickness
                    s.drawQuad(x0, Maze.tileY(row), thickness, Maze.TILE.toFloat())
                }
            }

            for (rowEdge in 0..Maze.ROWS) {
                for (col in 0 until Maze.COLS) {
                    if (!horizontalOpen[rowEdge][col]) continue
                    val y0 = if (rowEdge == 0) Maze.tileY(0) else Maze.tileY(rowEdge) - thickness
                    s.drawQuad(Maze.tileX(col), y0, Maze.TILE.toFloat(), thickness)
                }
            }

            if (!wallThinOutlineMode) {
                for (rowEdge in 0..Maze.ROWS) {
                    for (xEdge in 0..Maze.COLS) {
                        val hasVertical = (rowEdge > 0 && verticalOpen[rowEdge - 1][xEdge]) || (rowEdge < Maze.ROWS && verticalOpen[rowEdge][xEdge])
                        val hasHorizontal = (xEdge > 0 && horizontalOpen[rowEdge][xEdge - 1]) || (xEdge < Maze.COLS && horizontalOpen[rowEdge][xEdge])
                        if (!hasVertical || !hasHorizontal) continue

                        val px = if (xEdge == 0) Maze.tileX(0) else Maze.tileX(xEdge) - thickness
                        val py = if (rowEdge == 0) Maze.tileY(0) else Maze.tileY(rowEdge) - thickness
                        s.drawQuad(px, py, thickness, thickness)
                    }
                }
            }
        }
    }

    /**
     * Renders Pulse-Man at his current position with appropriate mouth animation or death sequence.
     */
    fun renderPulseMan(s: Surface, phase: GamePhase, deathAnimTimer: Float) {
        val px = pulseMan.pixelX()
        val py = pulseMan.pixelY()
        val radius = (Maze.TILE - 4f) * 0.5f

        if (phase == GamePhase.DYING) {
            val life = (deathAnimTimer / 1.5f).coerceIn(0f, 1f)
            val gone = 1f - life
            val shrink = (1f - gone).coerceAtLeast(0f)
            val dyingMouth = (0.35f + gone * 0.65f).coerceAtMost(1f)
            s.setDrawColor(1f, 0.95f, 0f, 1f)
            drawFilledCircle(s, px, py, radius * shrink, 20)
            drawPulseManMouthCutout(s, px, py, radius * shrink, if (pulseMan.dir == Direction.NONE) Direction.RIGHT else pulseMan.dir, dyingMouth)
            return
        }

        s.setDrawColor(1f, 0.95f, 0f, 1f)
        drawFilledCircle(s, px, py, radius, 20)
        if (pulseMan.dir != Direction.NONE) {
            drawPulseManMouthCutout(s, px, py, radius, pulseMan.dir, pulseMan.mouthAngle)
        }
    }

    /**
     * Renders all ghosts at their current positions with appropriate colors and eyes.
     */
    fun renderGhosts(s: Surface) {
        for (ghost in ghostAI.ghosts) {
            val gx = if (!ghost.released && ghost.mode != GhostMode.EATEN) Maze.centerX(ghost.gridX) else ghostAI.ghostPixelX(ghost)
            val gy = if (!ghost.released && ghost.mode != GhostMode.EATEN) Maze.centerY(ghost.gridY) else ghostAI.ghostPixelY(ghost)
            val size = Maze.TILE - 4f

            if (ghost.mode == GhostMode.EATEN) {
                drawGhostEyes(s, gx, gy - 1f, ghost.direction, eyeScale = 1.15f)
                continue
            }

            setGhostColor(s, ghost, ghostAI.frightenedTimer, ghostAI.frightenedFlashes)
            drawGhostBody(s, gx, gy, size)

            if (ghost.mode == GhostMode.FRIGHTENED) {
                drawFrightenedFace(s, gx, gy)
            } else {
                drawGhostEyes(s, gx, gy - 1f, ghost.direction, eyeScale = 1f)
            }
        }
    }

    /**
     * Renders the active fruit if one is present.
     */
    fun renderFruit(s: Surface) {
        val fruit = fruitManager.activeFruit ?: return
        val cx = Maze.centerX(fruit.col)
        val cy = Maze.centerY(fruit.row)

        when (fruit.type) {
            FruitType.CHERRY -> {
                s.setDrawColor(0.95f, 0.12f, 0.15f, 1f)
                drawFilledCircle(s, cx - 2.3f, cy + 1f, 4f, 10)
                drawFilledCircle(s, cx + 2.3f, cy - 0.3f, 4f, 10)
                s.setDrawColor(0.2f, 0.9f, 0.3f, 1f)
                s.drawQuad(cx - 0.8f, cy - 7f, 1.6f, 4.5f)
            }

            FruitType.STRAWBERRY -> {
                s.setDrawColor(0.95f, 0.15f, 0.16f, 1f)
                s.drawQuad(cx - 5f, cy - 2f, 10f, 8f)
                s.setDrawColor(0.1f, 0.8f, 0.22f, 1f)
                s.drawQuad(cx - 4f, cy - 6f, 8f, 3f)
            }

            FruitType.ORANGE -> {
                s.setDrawColor(1f, 0.55f, 0.1f, 1f)
                drawFilledCircle(s, cx, cy, 5f, 12)
            }

            FruitType.APPLE -> {
                s.setDrawColor(0.9f, 0.12f, 0.16f, 1f)
                drawFilledCircle(s, cx, cy, 5f, 12)
                s.setDrawColor(0.22f, 0.95f, 0.3f, 1f)
                drawFilledCircle(s, cx + 2f, cy - 5.5f, 1.6f, 6)
            }

            FruitType.MELON -> {
                s.setDrawColor(0.28f, 0.86f, 0.45f, 1f)
                drawFilledCircle(s, cx, cy, 5f, 12)
            }

            FruitType.GALAXIAN -> {
                s.setDrawColor(0.95f, 0.95f, 0.22f, 1f)
                drawFilledCircle(s, cx, cy, 5f, 12)
            }

            FruitType.BELL -> {
                s.setDrawColor(1f, 0.85f, 0.2f, 1f)
                drawFilledCircle(s, cx, cy, 5f, 12)
            }

            FruitType.KEY -> {
                s.setDrawColor(0.98f, 0.92f, 0.45f, 1f)
                drawFilledCircle(s, cx, cy, 5f, 12)
            }
        }
    }

    /**
     * Renders subtle bloom halos around Pulse-Man, ghosts, and fruit.
     */
    fun renderEntityBloomHalos(s: Surface, uiPulseTime: Float) {
        val pulse = 0.5f + 0.5f * sin(uiPulseTime * 4.3f)
        val pulseX = pulseMan.pixelX()
        val pulseY = pulseMan.pixelY()
        s.setDrawColor(1f, 0.95f, 0.24f, 0.16f + pulse * 0.12f)
        drawFilledCircle(s, pulseX, pulseY, 16f + pulse * 3f, 18)

        fruitManager.activeFruit?.let {
            val fx = Maze.centerX(it.col)
            val fy = Maze.centerY(it.row)
            s.setDrawColor(1f, 0.55f, 0.2f, 0.16f + pulse * 0.12f)
            drawFilledCircle(s, fx, fy, 13f + pulse * 2.5f, 16)
        }

        for (ghost in ghostAI.ghosts) {
            if (ghost.mode == GhostMode.EATEN) continue
            val gx = if (!ghost.released) Maze.centerX(ghost.gridX) else ghostAI.ghostPixelX(ghost)
            val gy = if (!ghost.released) Maze.centerY(ghost.gridY) else ghostAI.ghostPixelY(ghost)
            val c = when {
                ghost.mode == GhostMode.FRIGHTENED -> floatArrayOf(0.45f, 0.58f, 1f)
                ghost.type == GhostType.BLINKY -> floatArrayOf(1f, 0.24f, 0.24f)
                ghost.type == GhostType.PINKY -> floatArrayOf(1f, 0.7f, 0.86f)
                ghost.type == GhostType.INKY -> floatArrayOf(0.24f, 1f, 1f)
                else -> floatArrayOf(1f, 0.72f, 0.25f)
            }
            s.setDrawColor(c[0], c[1], c[2], 0.14f + pulse * 0.1f)
            drawFilledCircle(s, gx, gy, 14f + pulse * 2f, 16)
        }
    }

    /**
     * Renders a debug overlay for testing maze geometry and window alignment.
     */
    fun renderGeometryTestOverlay(s: Surface, windowWidth: Float, windowHeight: Float) {
        val left = Maze.tileX(0)
        val top = Maze.tileY(0)
        val width = Maze.COLS * Maze.TILE.toFloat()
        val height = Maze.ROWS * Maze.TILE.toFloat()
        val right = left + width
        val bottom = top + height
        val centerX = left + width * 0.5f
        val centerY = top + height * 0.5f

        s.setDrawColor(1f, 0.35f, 0.35f, 0.9f)
        s.drawLine(left, top, right, top)
        s.drawLine(left, bottom, right, bottom)
        s.drawLine(left, top, left, bottom)
        s.drawLine(right, top, right, bottom)

        s.setDrawColor(0.35f, 1f, 0.4f, 0.9f)
        s.drawLine(centerX, top, centerX, bottom)
        s.drawLine(left, centerY, right, centerY)

        s.setDrawColor(0.35f, 0.75f, 1f, 0.85f)
        val sx0 = 20f
        val sy0 = 20f
        val sx1 = windowWidth - 20f
        val sy1 = windowHeight - 20f
        s.drawLine(sx0, sy0, sx1, sy0)
        s.drawLine(sx0, sy1, sx1, sy1)
        s.drawLine(sx0, sy0, sx0, sy1)
        s.drawLine(sx1, sy0, sx1, sy1)
    }
}
