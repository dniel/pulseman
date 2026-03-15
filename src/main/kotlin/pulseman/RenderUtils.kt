package pulseman

import no.njoh.pulseengine.core.graphics.surface.Surface
import kotlin.math.sqrt

/**
 * Sets the drawing color for a ghost based on its type and current state.
 * Handles the flashing blue/white transition when the ghost is in FRIGHTENED mode.
 */
fun setGhostColor(s: Surface, ghost: GhostState, frightenedTimer: Float, frightenedFlashes: Int) {
    if (ghost.mode == GhostMode.FRIGHTENED) {
        val flashDuration = frightenedFlashes * 0.233f
        val flashWhite = frightenedTimer < flashDuration && ((frightenedTimer * 7f).toInt() % 2 == 0)
        if (flashWhite) s.setDrawColor(1f, 1f, 1f, 1f)
        else s.setDrawColor(0.1f, 0.24f, 0.88f, 1f)
        return
    }

    when (ghost.type) {
        GhostType.BLINKY -> s.setDrawColor(0.94f, 0.18f, 0.2f, 1f)
        GhostType.PINKY -> s.setDrawColor(1f, 0.66f, 0.82f, 1f)
        GhostType.INKY -> s.setDrawColor(0.16f, 0.86f, 0.95f, 1f)
        GhostType.CLYDE -> s.setDrawColor(0.98f, 0.6f, 0.16f, 1f)
    }
}

/**
 * Renders the main body of a ghost, including the rounded top and wavy bottom bumps.
 */
fun drawGhostBody(s: Surface, cx: Float, cy: Float, size: Float) {
    val radius = size * 0.5f
    val bodyTop = cy - size * 0.08f
    val bodyBottom = cy + size * 0.42f

    s.drawWithin(cx - radius, cy - radius, radius * 2f, radius) {
        drawFilledCircle(s, cx, cy - size * 0.12f, radius, 16)
    }
    s.drawQuad(cx - radius, bodyTop, radius * 2f, bodyBottom - bodyTop)

    val bumpRadius = size * 0.15f
    val baseY = bodyBottom - bumpRadius * 0.6f
    drawFilledCircle(s, cx - size * 0.3f, baseY, bumpRadius, 8)
    drawFilledCircle(s, cx, baseY + 0.25f, bumpRadius, 8)
    drawFilledCircle(s, cx + size * 0.3f, baseY, bumpRadius, 8)
}

/**
 * Renders the white eyes and blue pupils of a ghost, offset based on its movement direction.
 */
fun drawGhostEyes(s: Surface, cx: Float, cy: Float, dir: Direction, eyeScale: Float) {
    val eyeOffset = 2.2f * eyeScale
    val eyeRadius = 1.4f * eyeScale
    val pupilRadius = 0.725f * eyeScale
    val pdx = dir.dx * 0.7f
    val pdy = dir.dy * 0.6f

    s.setDrawColor(1f, 1f, 1f, 1f)
    drawFilledCircle(s, cx - eyeOffset, cy - 1f, eyeRadius, 10)
    drawFilledCircle(s, cx + eyeOffset, cy - 1f, eyeRadius, 10)

    s.setDrawColor(0.07f, 0.07f, 0.35f, 1f)
    drawFilledCircle(s, cx - eyeOffset + pdx, cy - 1f + pdy, pupilRadius, 8)
    drawFilledCircle(s, cx + eyeOffset + pdx, cy - 1f + pdy, pupilRadius, 8)
}

/**
 * Renders the "scared" facial expression (eyes and mouth) for a ghost in frightened mode.
 */
fun drawFrightenedFace(s: Surface, cx: Float, cy: Float) {
    s.setDrawColor(1f, 1f, 1f, 1f)
    drawFilledCircle(s, cx - 2f, cy - 1f, 0.9f, 6)
    drawFilledCircle(s, cx + 2f, cy - 1f, 0.9f, 6)
    val y = cy + 2f
    s.drawLine(cx - 3f, y, cx - 1.5f, y - 0.5f)
    s.drawLine(cx - 1.5f, y - 0.5f, cx, y)
    s.drawLine(cx, y, cx + 1.5f, y - 0.5f)
    s.drawLine(cx + 1.5f, y - 0.5f, cx + 3f, y)
}

/**
 * Renders text with multiple semi-transparent layers to create a glowing effect.
 */
fun drawGlowText(
    s: Surface,
    text: String,
    x: Float,
    y: Float,
    fontSize: Float,
    red: Float,
    green: Float,
    blue: Float,
    xOrigin: Float = 0f,
    yOrigin: Float = 0f,
) {
    s.setDrawColor(red, green, blue, 0.16f)
    s.drawText(text, x - 1.5f, y, fontSize = fontSize, xOrigin = xOrigin, yOrigin = yOrigin)
    s.drawText(text, x + 1.5f, y, fontSize = fontSize, xOrigin = xOrigin, yOrigin = yOrigin)
    s.drawText(text, x, y - 1.5f, fontSize = fontSize, xOrigin = xOrigin, yOrigin = yOrigin)
    s.drawText(text, x, y + 1.5f, fontSize = fontSize, xOrigin = xOrigin, yOrigin = yOrigin)
    s.setDrawColor(red, green, blue, 0.1f)
    s.drawText(text, x - 3f, y, fontSize = fontSize, xOrigin = xOrigin, yOrigin = yOrigin)
    s.drawText(text, x + 3f, y, fontSize = fontSize, xOrigin = xOrigin, yOrigin = yOrigin)
    s.drawText(text, x, y - 3f, fontSize = fontSize, xOrigin = xOrigin, yOrigin = yOrigin)
    s.drawText(text, x, y + 3f, fontSize = fontSize, xOrigin = xOrigin, yOrigin = yOrigin)
}

/**
 * Utility to draw a filled circle by stacking horizontal quads.
 * Used when direct circle rendering is not available or suitable.
 */
fun drawFilledCircle(s: Surface, cx: Float, cy: Float, radius: Float, segments: Int = 20) {
    if (radius <= 0.25f) return
    val step = (radius * 2f) / segments
    for (i in 0..segments) {
        val y = cy - radius + i * step
        val dy = y - cy
        val halfWidth = sqrt((radius * radius - dy * dy).coerceAtLeast(0f))
        s.drawQuad(cx - halfWidth, y, halfWidth * 2f, step + 0.15f)
    }
}

/**
 * Renders a black cutout over Pulse-Man to simulate the opening and closing mouth.
 */
fun drawPulseManMouthCutout(s: Surface, cx: Float, cy: Float, radius: Float, dir: Direction, open: Float) {
    val openness = open.coerceIn(0.05f, 1f)
    val samples = 12
    val step = radius / samples
    val maxHalf = radius * (0.18f + 0.7f * openness)

    s.setDrawColor(0f, 0f, 0f, 1f)
    when (dir) {
        Direction.RIGHT -> {
            for (i in 0..samples) {
                val dx = i * step
                val h = maxHalf * (dx / radius)
                s.drawQuad(cx + dx, cy - h, step + 0.4f, h * 2f + 0.4f)
            }
        }

        Direction.LEFT -> {
            for (i in 0..samples) {
                val dx = i * step
                val h = maxHalf * (dx / radius)
                s.drawQuad(cx - dx - step, cy - h, step + 0.4f, h * 2f + 0.4f)
            }
        }

        Direction.UP -> {
            for (i in 0..samples) {
                val dy = i * step
                val w = maxHalf * (dy / radius)
                s.drawQuad(cx - w, cy - dy - step, w * 2f + 0.4f, step + 0.4f)
            }
        }

        Direction.DOWN -> {
            for (i in 0..samples) {
                val dy = i * step
                val w = maxHalf * (dy / radius)
                s.drawQuad(cx - w, cy + dy, w * 2f + 0.4f, step + 0.4f)
            }
        }

        Direction.NONE -> {}
    }
}
