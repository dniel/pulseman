package pulseman

import no.njoh.pulseengine.core.graphics.surface.Surface
import kotlin.math.sin

/**
 * Renders non-gameplay screens such as the boot sequence, attract mode, and high scores.
 *
 * This renderer handles the visual presentation of the game's various states
 * outside of the main gameplay loop.
 */
class ScreenRenderer(
    private val scoreManager: ScoreManager,
) {
    /**
     * Renders the appropriate startup or attract screen based on the current game phase.
     */
    fun renderStartupScreen(
        s: Surface,
        phase: GamePhase,
        bootTimer: Float,
        bootDuration: Float,
        attractTimer: Float,
        uiPulseTime: Float,
        windowWidth: Int,
        windowHeight: Int,
        credits: Int,
    ) {
        when (phase) {
            GamePhase.BOOT -> renderBootScreen(s, bootTimer, bootDuration, windowWidth, windowHeight)
            GamePhase.ATTRACT -> renderAttractScreen(s, attractTimer, windowWidth, windowHeight, credits)
            GamePhase.TITLE_POINTS -> renderTitlePointsScreen(s, uiPulseTime, windowWidth, windowHeight, credits)
            GamePhase.HI_SCORE -> renderHiScoreScreen(s, uiPulseTime, windowWidth, windowHeight, credits)
            else -> {}
        }
    }

    private fun renderBootScreen(s: Surface, bootTimer: Float, bootDuration: Float, windowWidth: Int, windowHeight: Int) {
        val centerX = windowWidth / 2f
        val centerY = windowHeight / 2f
        val elapsed = (bootDuration - bootTimer).coerceIn(0f, bootDuration)
        val pct = ((elapsed / bootDuration) * 100f).toInt().coerceIn(0, 100)

        if (elapsed >= 2.35f && elapsed < 3.45f) {
            renderBootVideoTestScreen(s, elapsed, windowWidth.toFloat(), windowHeight.toFloat())
            return
        }

        drawGlowText(s, "PULSE ENGINE SYSTEM I", centerX, centerY - 60f, fontSize = 26f, red = 0.72f, green = 0.9f, blue = 1f, xOrigin = 0.5f, yOrigin = 0.5f)
        s.setDrawColor(0.72f, 0.9f, 1f, 1f)
        s.drawText("PULSE ENGINE SYSTEM I", centerX, centerY - 60f, fontSize = 26f, xOrigin = 0.5f, yOrigin = 0.5f)

        s.setDrawColor(0.95f, 0.95f, 0.95f, 1f)
        s.drawText("POWER ON SELF TEST", centerX, centerY - 39f, fontSize = 18f, xOrigin = 0.5f, yOrigin = 0.5f)

        val left = centerX - 85f
        if (elapsed >= 0.7f) {
            s.setDrawColor(0.85f, 0.95f, 1f, 1f)
            s.drawText("RAM CHECK ....", left, centerY - 17f, fontSize = 16f)
            if (elapsed >= 1.3f) {
                s.setDrawColor(0.95f, 0.95f, 0.3f, 1f)
                s.drawText("OK", left + 87.5f, centerY - 17f, fontSize = 16f)
            }
        }

        if (elapsed >= 1.6f) {
            s.setDrawColor(0.85f, 0.95f, 1f, 1f)
            s.drawText("ROM CHECK ....", left, centerY - 4f, fontSize = 16f)
            if (elapsed >= 2.3f) {
                s.setDrawColor(0.95f, 0.95f, 0.3f, 1f)
                s.drawText("OK", left + 87.5f, centerY - 4f, fontSize = 16f)
            }
        }

        if (elapsed >= 2.6f) {
            s.setDrawColor(0.85f, 0.95f, 1f, 1f)
            s.drawText("VIDEO CHECK ..", left, centerY + 9f, fontSize = 16f)
            if (elapsed >= 3.2f) {
                s.setDrawColor(0.95f, 0.95f, 0.3f, 1f)
                s.drawText("OK", left + 87.5f, centerY + 9f, fontSize = 16f)
            }
        }

        if (elapsed >= 3.5f) {
            s.setDrawColor(0.85f, 0.95f, 1f, 1f)
            s.drawText("SOUND CHECK ..", left, centerY + 22f, fontSize = 16f)
            if (elapsed >= 4.1f) {
                s.setDrawColor(0.95f, 0.95f, 0.3f, 1f)
                s.drawText("OK", left + 87.5f, centerY + 22f, fontSize = 16f)
            }
        }

        s.setDrawColor(0.9f, 0.9f, 0.9f, 1f)
        s.drawText("PROGRESS ${pct.toString().padStart(3, '0')}%", centerX, centerY + 42f, fontSize = 16f, xOrigin = 0.5f, yOrigin = 0.5f)

        if (elapsed >= 4.4f) {
            s.setDrawColor(0.95f, 0.35f, 0.35f, 1f)
            s.drawText("2026 PULSE ENGINE LTD.", centerX, centerY + 58f, fontSize = 16f, xOrigin = 0.5f, yOrigin = 0.5f)
        }
    }

    private fun renderBootVideoTestScreen(s: Surface, elapsed: Float, windowWidth: Float, windowHeight: Float) {
        val width = windowWidth
        val height = windowHeight
        val left = 46f
        val top = 45f
        val right = width - 46f
        val bottom = height - 55f
        val areaW = right - left
        val areaH = bottom - top
        val line = 0.75f

        s.setDrawColor(0.02f, 0.03f, 0.05f, 1f)
        s.drawQuad(0f, 0f, width, height)

        s.setDrawColor(0.86f, 0.9f, 0.95f, 1f)
        s.drawQuad(left, top, areaW, line)
        s.drawQuad(left, bottom - line, areaW, line)
        s.drawQuad(left, top, line, areaH)
        s.drawQuad(right - line, top, line, areaH)

        val spacing = 14f
        var gx = left + spacing
        while (gx < right - spacing) {
            s.drawQuad(gx, top, line, areaH)
            gx += spacing
        }
        var gy = top + spacing
        while (gy < bottom - spacing) {
            s.drawQuad(left, gy, areaW, line)
            gy += spacing
        }

        val colors = arrayOf(
            floatArrayOf(1f, 0.15f, 0.15f),
            floatArrayOf(1f, 0.85f, 0.15f),
            floatArrayOf(0.35f, 1f, 0.35f),
            floatArrayOf(0.2f, 1f, 1f),
            floatArrayOf(0.3f, 0.5f, 1f),
            floatArrayOf(0.9f, 0.25f, 0.95f),
        )

        val horizontalBarsY = top + 7f
        val horizontalBarH = 16f
        val horizontalBarW = areaW / colors.size
        for (i in colors.indices) {
            val c = colors[i]
            s.setDrawColor(c[0], c[1], c[2], 1f)
            s.drawQuad(left + i * horizontalBarW, horizontalBarsY, horizontalBarW + 1f, horizontalBarH)
        }

        val barW = 9f
        val barX = width * 0.5f - barW * 0.5f
        val sectionH = areaH / 6f
        for (i in colors.indices) {
            val c = colors[i]
            s.setDrawColor(c[0], c[1], c[2], 1f)
            s.drawQuad(barX, top + i * sectionH, barW, sectionH + 1f)
        }

        val cx = (left + right) * 0.5f
        val cy = (top + bottom) * 0.5f + 9f
        s.setDrawColor(1f, 1f, 1f, 0.95f)
        s.drawLine(cx - 60f, cy, cx + 60f, cy)
        s.drawLine(cx, cy - 60f, cx, cy + 60f)
        drawFilledCircle(s, cx, cy, 45f, 40)
        s.setDrawColor(0.02f, 0.03f, 0.05f, 1f)
        drawFilledCircle(s, cx, cy, 44f, 40)
        s.setDrawColor(1f, 1f, 1f, 0.95f)
        drawFilledCircle(s, cx, cy, 28f, 36)
        s.setDrawColor(0.02f, 0.03f, 0.05f, 1f)
        drawFilledCircle(s, cx, cy, 27f, 36)

        s.setDrawColor(0.9f, 0.95f, 1f, 1f)
        s.drawText("VIDEO TEST: GEOMETRY + RGB", width * 0.5f, 21f, fontSize = 20f, xOrigin = 0.5f, yOrigin = 0.5f)
        val flash = 0.75f + 0.25f * sin(elapsed * 18f)
        s.setDrawColor(0.95f, 0.95f, 0.3f, flash)
        s.drawText("SIGNAL OK", width * 0.5f, height - 29f, fontSize = 18f, xOrigin = 0.5f, yOrigin = 0.5f)
    }

    private fun renderAttractScreen(s: Surface, attractTimer: Float, windowWidth: Int, windowHeight: Int, credits: Int) {
        val centerX = windowWidth / 2f
        val y0 = windowHeight / 2f - 55f
        drawGlowText(s, "CHARACTER / NICKNAME", centerX, y0, fontSize = 28f, red = 1f, green = 0.3f, blue = 0.3f, xOrigin = 0.5f)
        s.setDrawColor(1f, 0.3f, 0.3f, 1f)
        s.drawText("CHARACTER / NICKNAME", centerX, y0, fontSize = 28f, xOrigin = 0.5f)

        val elapsed = (6f - attractTimer).coerceIn(0f, 6f)
        val cards = listOf(
            Triple("SHADOW", "-OIKAKE AKABEI", floatArrayOf(1f, 0.2f, 0.2f)),
            Triple("SPEEDY", "-MACHIBUSE PINKY", floatArrayOf(1f, 0.68f, 0.86f)),
            Triple("BASHFUL", "-KIMAGURE AOKU", floatArrayOf(0.2f, 1f, 1f)),
            Triple("POKEY", "-OTOBOKE GUZUTA", floatArrayOf(1f, 0.72f, 0.2f)),
        )
        for (i in cards.indices) {
            if (elapsed < i * 1.2f) break
            val (name, nick, c) = cards[i]
            val y = y0 + 23f + i * 20f
            val gx = centerX - 94f
            s.setDrawColor(c[0], c[1], c[2], 1f)
            drawGhostBody(s, gx, y + 2f, 10f)
            drawGhostEyes(s, gx, y + 1f, Direction.LEFT, eyeScale = 0.8f)
            s.setDrawColor(c[0], c[1], c[2], 1f)
            s.drawText(name, centerX - 77.5f, y, fontSize = 22f)
            s.setDrawColor(0.9f, 0.9f, 0.9f, 0.95f)
            s.drawText(nick, centerX - 10f, y, fontSize = 18f)
        }

        renderCreditFooter(s, centerX, windowHeight.toFloat(), credits)
    }

    private fun renderTitlePointsScreen(s: Surface, uiPulseTime: Float, windowWidth: Int, windowHeight: Int, credits: Int) {
        s.setDrawColor(0f, 0f, 0f, 0.5f)
        s.drawQuad(0f, 0f, windowWidth.toFloat(), windowHeight.toFloat())

        val centerX = windowWidth / 2f
        val centerY = windowHeight / 2f

        drawGlowText(s, "PULSE-MAN", centerX, centerY - 60f, fontSize = 72f, red = 1f, green = 0.95f, blue = 0f, xOrigin = 0.5f, yOrigin = 0.5f)
        s.setDrawColor(1f, 0.95f, 0f, 1f)
        s.drawText("PULSE-MAN", centerX, centerY - 60f, fontSize = 72f, xOrigin = 0.5f, yOrigin = 0.5f)

        s.setDrawColor(0.2f, 0.8f, 1f, 1f)
        s.drawText("© 2026 PULSE ENGINE LTD.", centerX, centerY - 32.5f, fontSize = 20f, xOrigin = 0.5f, yOrigin = 0.5f)

        val ghostColors = listOf(
            floatArrayOf(1f, 0.2f, 0.2f),
            floatArrayOf(1f, 0.68f, 0.86f),
            floatArrayOf(0.2f, 1f, 1f),
            floatArrayOf(1f, 0.72f, 0.2f),
        )
        val ghostY = centerY - 11f
        val ghostSpacing = 31f
        val ghostStartX = centerX - ghostSpacing * 1.5f
        for (i in ghostColors.indices) {
            val color = ghostColors[i]
            val x = ghostStartX + ghostSpacing * i
            s.setDrawColor(color[0], color[1], color[2], 1f)
            drawGhostBody(s, x, ghostY + 2f, 11f)
            drawGhostEyes(s, x, ghostY + 1f, Direction.LEFT, eyeScale = 0.85f)
        }

        s.setDrawColor(0.9f, 0.9f, 0.9f, 0.9f)
        s.drawText("10 PTS", centerX - 35f, centerY + 13f, fontSize = 22f, xOrigin = 1f, yOrigin = 0.5f)
        s.drawText("DOT", centerX - 27.5f, centerY + 13f, fontSize = 22f, xOrigin = 0f, yOrigin = 0.5f)
        s.drawText("50 PTS", centerX - 35f, centerY + 28.5f, fontSize = 22f, xOrigin = 1f, yOrigin = 0.5f)
        s.drawText("POWER PELLET", centerX - 27.5f, centerY + 28.5f, fontSize = 22f, xOrigin = 0f, yOrigin = 0.5f)

        val blink = (0.35f + 0.65f * (0.5f + 0.5f * sin(uiPulseTime * 5f))).coerceIn(0.2f, 1f)
        val startText = if (credits > 0) "PUSH 1P START" else "INSERT COIN"
        s.setDrawColor(1f, 1f, 1f, blink)
        s.drawText(startText, centerX, centerY + 61f, fontSize = 24f, xOrigin = 0.5f, yOrigin = 0.5f)

        renderCreditFooter(s, centerX, windowHeight.toFloat(), credits)
    }

    private fun renderHiScoreScreen(s: Surface, uiPulseTime: Float, windowWidth: Int, windowHeight: Int, credits: Int) {
        val centerX = windowWidth / 2f
        val centerY = windowHeight / 2f

        s.setDrawColor(0f, 0f, 0f, 0.5f)
        s.drawQuad(0f, 0f, windowWidth.toFloat(), windowHeight.toFloat())

        drawGlowText(s, "HIGH SCORES", centerX, centerY - 60f, fontSize = 56f, red = 1f, green = 0.95f, blue = 0.1f, xOrigin = 0.5f, yOrigin = 0.5f)
        s.setDrawColor(1f, 0.95f, 0.1f, 1f)
        s.drawText("HIGH SCORES", centerX, centerY - 60f, fontSize = 56f, xOrigin = 0.5f, yOrigin = 0.5f)

        val rows = listOf(
            "1ST  PLAYER   ${scoreManager.highScore.toString().padStart(6, '0')}",
            "2ND  AAA      020000",
            "3RD  BBB      015000",
            "4TH  CCC      010000",
            "5TH  DDD      005000",
        )
        rows.forEachIndexed { i, row ->
            val alpha = 0.75f + 0.25f * sin(uiPulseTime * 2f + i * 0.4f)
            s.setDrawColor(0.9f, 0.95f, 1f, alpha)
            s.drawText(row, centerX, centerY - 15f + i * 17f, fontSize = 24f, xOrigin = 0.5f, yOrigin = 0.5f)
        }

        val startText = if (credits > 0) "PUSH 1P START" else "INSERT COIN"
        s.setDrawColor(1f, 1f, 1f, 0.8f)
        s.drawText(startText, centerX, centerY + 75f, fontSize = 22f, xOrigin = 0.5f, yOrigin = 0.5f)

        renderCreditFooter(s, centerX, windowHeight.toFloat(), credits)
    }

    /**
     * Renders the credit count footer at the bottom of startup/attract screens.
     */
    private fun renderCreditFooter(s: Surface, centerX: Float, windowHeight: Float, credits: Int) {
        s.setDrawColor(0.9f, 0.9f, 0.2f, 1f)
        s.drawText("CREDIT  $credits", centerX, windowHeight - 15f, fontSize = 20f, xOrigin = 0.5f, yOrigin = 0.5f)
    }
}
