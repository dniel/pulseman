package pulseman

import no.njoh.pulseengine.core.graphics.surface.Surface
import kotlin.math.max

/**
 * Manages the player's score, tracking the current score, high score,
 * and handling floating score popups.
 */
class ScoreManager {
    /** The player's current score. */
    var score: Int = 0
        private set

    /** The highest score achieved in the current session. */
    var highScore: Int = 0

    private val popups = mutableListOf<ScorePopup>()

    /**
     * Increases the current score by the specified [amount] and updates the [highScore] if exceeded.
     */
    fun addScore(amount: Int) {
        score += amount
        if (score > highScore) highScore = score
    }

    /**
     * Adds a new floating score popup (e.g., when eating a ghost or fruit) at the specified position.
     */
    fun addPopup(x: Float, y: Float, text: String) {
        popups += ScorePopup(x = x, y = y, text = text, timer = 1f)
    }

    /**
     * Updates the animation and lifetime of all active score popups.
     */
    fun update(dt: Float) {
        val iterator = popups.iterator()
        while (iterator.hasNext()) {
            val popup = iterator.next()
            popup.timer -= dt
            popup.y -= dt * 28f
            if (popup.timer <= 0f) iterator.remove()
        }
    }

    /**
     * Renders all active floating score popups to the screen.
     */
    fun render(surface: Surface) {
        for (popup in popups) {
            val alpha = popup.timer.coerceIn(0f, 1f)
            surface.setDrawColor(1f, 1f, 1f, alpha)
            surface.drawText(popup.text, popup.x, popup.y, fontSize = 18f, xOrigin = 0.5f, yOrigin = 0.5f)
        }
    }

    /**
     * Resets the current score and clears all active popups.
     */
    fun reset() {
        score = 0
        popups.clear()
    }
}
