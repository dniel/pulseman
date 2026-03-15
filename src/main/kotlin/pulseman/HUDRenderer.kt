package pulseman

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
        credits: Int,
        uiPulseTime: Float,
        attractDemoGameOverTimer: Float,
        windowWidth: Int,
        windowHeight: Int,
        scale: Float,
        marginX: Float,
        marginY: Float,
    ) {
        val scoreText = scoreManager.score.toString().padStart(6, '0')
        val highScoreText = scoreManager.highScore.toString().padStart(6, '0')

        s.setDrawColor(0.95f, 0.95f, 0.95f, 1f)
        s.drawText("1UP", marginX + 10f * scale, marginY + 7.5f * scale, fontSize = 20f)
        s.drawText(scoreText, marginX + 10f * scale, marginY + 18f * scale, fontSize = 24f)

        s.drawText("HIGH SCORE", windowWidth / 2f, marginY + 7.5f * scale, fontSize = 20f, xOrigin = 0.5f)
        s.drawText(highScoreText, windowWidth / 2f, marginY + 18f * scale, fontSize = 24f, xOrigin = 0.5f)

        s.drawText("LEVEL ${level.toString().padStart(2, '0')}", marginX + 320f * scale, marginY + 7.5f * scale, fontSize = 20f)
        s.drawText("LIVES", marginX + 320f * scale, marginY + 18f * scale, fontSize = 16f)
        for (i in 0 until lives) {
            drawLifeIcon(s, marginX + (351f + i * 12f) * scale, marginY + 22.5f * scale, 4f * scale)
        }

        val pulseAlpha = (0.65f + 0.35f * (0.5f + 0.5f * sin(uiPulseTime * 4f))).coerceIn(0.45f, 1f)
        when (phase) {
            GamePhase.READY -> {
                s.setDrawColor(1f, 1f, 0f, pulseAlpha)
                s.drawText(
                    "READY!",
                    windowWidth / 2f,
                    marginY + Maze.centerY(17) * scale,
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
                    marginY + Maze.centerY(17) * scale,
                    fontSize = 36f,
                    xOrigin = 0.5f,
                    yOrigin = 0.5f,
                )
                val continueText = if (credits > 0) "PUSH 1P START" else "INSERT COIN"
                s.setDrawColor(1f, 1f, 1f, 0.7f)
                s.drawText(
                    continueText,
                    windowWidth / 2f,
                    marginY + (Maze.centerY(17) + 20f) * scale,
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
                    marginY + Maze.centerY(17) * scale,
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
                    marginY + Maze.centerY(17) * scale,
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
                        marginY + Maze.centerY(17) * scale,
                        fontSize = 34f,
                        xOrigin = 0.5f,
                        yOrigin = 0.5f,
                    )
                    s.setDrawColor(1f, 0.96f, 0.74f, 0.9f)
                    s.drawText(
                        "DEMO PLAY",
                        windowWidth / 2f,
                        marginY + (Maze.centerY(17) + 17f) * scale,
                        fontSize = 16f,
                        xOrigin = 0.5f,
                        yOrigin = 0.5f,
                    )
                }
            }

            else -> {}
        }

        s.setDrawColor(0.9f, 0.9f, 0.2f, 1f)
        s.drawText(
            "CREDIT  $credits",
            windowWidth / 2f,
            windowHeight - 7.5f,
            fontSize = 14f,
            xOrigin = 0.5f,
            yOrigin = 0.5f,
        )
    }

    private fun drawLifeIcon(s: Surface, x: Float, y: Float, radius: Float) {
        s.setDrawColor(1f, 0.95f, 0f, 1f)
        drawFilledCircle(s, x, y, radius, 14)
        drawPulseManMouthCutout(s, x, y, radius, Direction.RIGHT, 0.45f)
    }
}
