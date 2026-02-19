package pacman

import no.njoh.pulseengine.core.graphics.surface.Surface
import kotlin.math.sin

/**
 * Renders the Heads-Up Display (HUD) including score, lives, and level status.
 *
 * This renderer overlay shows real-time game information and transient
 * state messages like "READY!" or "GAME OVER".
 */
class HUDRenderer(
    private val scoreManager: ScoreManager,
) {
    /**
     * Renders the complete HUD overlay based on the current game state.
     */
    fun renderUI(
        s: Surface,
        phase: GamePhase,
        level: Int,
        lives: Int,
        uiPulseTime: Float,
        attractDemoGameOverTimer: Float,
        windowWidth: Int,
        windowHeight: Int,
    ) {
        val scoreText = scoreManager.score.toString().padStart(6, '0')
        val highScoreText = scoreManager.highScore.toString().padStart(6, '0')

        s.setDrawColor(0.95f, 0.95f, 0.95f, 1f)
        s.drawText("1UP", 20f, 15f, fontSize = 20f)
        s.drawText(scoreText, 20f, 36f, fontSize = 24f)

        s.drawText("HIGH SCORE", windowWidth / 2f, 15f, fontSize = 20f, xOrigin = 0.5f)
        s.drawText(highScoreText, windowWidth / 2f, 36f, fontSize = 24f, xOrigin = 0.5f)

        s.drawText("LEVEL ${level.toString().padStart(2, '0')}", 640f, 15f, fontSize = 20f)
        s.drawText("LIVES", 640f, 36f, fontSize = 16f)
        for (i in 0 until lives) {
            drawLifeIcon(s, 702f + i * 24f, 45f, 8f)
        }

        val pulseAlpha = (0.65f + 0.35f * (0.5f + 0.5f * sin(uiPulseTime * 4f))).coerceIn(0.45f, 1f)
        when (phase) {
            GamePhase.READY -> {
                s.setDrawColor(1f, 1f, 0f, pulseAlpha)
                s.drawText(
                    "READY!",
                    windowWidth / 2f,
                    Maze.centerY(17),
                    fontSize = 28f,
                    xOrigin = 0.5f,
                    yOrigin = 0.5f,
                )
            }

            GamePhase.GAME_OVER -> {
                s.setDrawColor(1f, 0f, 0f, pulseAlpha)
                s.drawText(
                    "GAME OVER",
                    windowWidth / 2f,
                    Maze.centerY(17),
                    fontSize = 36f,
                    xOrigin = 0.5f,
                    yOrigin = 0.5f,
                )
                s.setDrawColor(1f, 1f, 1f, 0.7f)
                s.drawText(
                    "Press ENTER to restart",
                    windowWidth / 2f,
                    Maze.centerY(17) + 40f,
                    fontSize = 18f,
                    xOrigin = 0.5f,
                    yOrigin = 0.5f,
                )
            }

            GamePhase.WON -> {
                s.setDrawColor(0f, 1f, 0f, pulseAlpha)
                s.drawText(
                    "YOU WIN!",
                    windowWidth / 2f,
                    Maze.centerY(17),
                    fontSize = 36f,
                    xOrigin = 0.5f,
                    yOrigin = 0.5f,
                )
            }

            GamePhase.LEVEL_TRANSITION -> {
                s.setDrawColor(0.95f, 0.95f, 0.2f, pulseAlpha)
                s.drawText(
                    "LEVEL $level",
                    windowWidth / 2f,
                    Maze.centerY(17),
                    fontSize = 34f,
                    xOrigin = 0.5f,
                    yOrigin = 0.5f,
                )
            }

            GamePhase.BOOT, GamePhase.ATTRACT, GamePhase.TITLE_POINTS, GamePhase.HI_SCORE -> return

            GamePhase.ATTRACT_DEMO -> {
                if (attractDemoGameOverTimer > 0f) {
                    s.setDrawColor(1f, 0.2f, 0.2f, pulseAlpha)
                    s.drawText(
                        "GAME OVER",
                        windowWidth / 2f,
                        Maze.centerY(17),
                        fontSize = 34f,
                        xOrigin = 0.5f,
                        yOrigin = 0.5f,
                    )
                    s.setDrawColor(1f, 0.96f, 0.74f, 0.9f)
                    s.drawText(
                        "DEMO PLAY",
                        windowWidth / 2f,
                        Maze.centerY(17) + 34f,
                        fontSize = 16f,
                        xOrigin = 0.5f,
                        yOrigin = 0.5f,
                    )
                }
            }

            else -> {}
        }

        s.setDrawColor(0.55f, 0.55f, 0.55f, 1f)
        s.drawText(
            "S: Service Menu",
            windowWidth / 2f,
            windowHeight - 15f,
            fontSize = 14f,
            xOrigin = 0.5f,
            yOrigin = 0.5f,
        )
    }

    private fun drawLifeIcon(s: Surface, x: Float, y: Float, radius: Float) {
        s.setDrawColor(1f, 0.95f, 0f, 1f)
        drawFilledCircle(s, x, y, radius, 14)
        drawPacmanMouthCutout(s, x, y, radius, Direction.RIGHT, 0.45f)
    }
}
