package pacman

import no.njoh.pulseengine.core.graphics.surface.Surface
import kotlin.math.max

class ScoreManager {
    var score: Int = 0
        private set
    var highScore: Int = 0
    private val popups = mutableListOf<ScorePopup>()

    fun addScore(amount: Int) {
        score += amount
        if (score > highScore) highScore = score
    }

    fun addPopup(x: Float, y: Float, text: String) {
        popups += ScorePopup(x = x, y = y, text = text, timer = 1f)
    }

    fun update(dt: Float) {
        val iterator = popups.iterator()
        while (iterator.hasNext()) {
            val popup = iterator.next()
            popup.timer -= dt
            popup.y -= dt * 28f
            if (popup.timer <= 0f) iterator.remove()
        }
    }

    fun render(surface: Surface) {
        for (popup in popups) {
            val alpha = popup.timer.coerceIn(0f, 1f)
            surface.setDrawColor(1f, 1f, 1f, alpha)
            surface.drawText(popup.text, popup.x, popup.y, fontSize = 18f, xOrigin = 0.5f, yOrigin = 0.5f)
        }
    }

    fun reset() {
        score = 0
        popups.clear()
    }
}
