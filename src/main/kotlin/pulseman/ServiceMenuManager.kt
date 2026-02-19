package pulseman

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.input.Key

/**
 * Represents the type of a menu item in the service menu.
 */
sealed interface MenuItemType {
    data object Toggle : MenuItemType
    data object Slider : MenuItemType
    data object Cycle : MenuItemType
    data object Header : MenuItemType
}

/**
 * Data class representing a single item in the service menu.
 *
 * @param label The text displayed for the menu item.
 * @param type The type of the menu item (Toggle, Slider, Cycle, or Header).
 * @param getter Function to retrieve the current value of the item.
 * @param setter Function to update a boolean value (for Toggle items).
 * @param sliderSetter Function to update a float value (for Slider items).
 * @param cycleSetter Function to cycle through values (for Cycle items).
 */
data class MenuItem(
    val label: String,
    val type: MenuItemType,
    val getter: (() -> Any)? = null,
    val setter: ((Boolean) -> Unit)? = null,
    val sliderSetter: ((Float) -> Unit)? = null,
    val cycleSetter: (() -> Unit)? = null,
)

/**
 * Manages the service menu, providing an interface for adjusting game settings and debugging.
 */
class ServiceMenuManager(
    private val menuItems: List<MenuItem>,
) {
    /** Whether the service menu is currently visible. */
    var isOpen = false

    /** The index of the currently selected menu item. */
    var cursorIndex = 1

    /**
     * Toggles the visibility of the service menu.
     */
    fun toggle() {
        isOpen = !isOpen
        if (isOpen) {
            if (menuItems[cursorIndex].type is MenuItemType.Header) {
                cursorIndex = 1
            }
        }
    }

    /**
     * Processes input for navigating and interacting with the service menu.
     */
    fun handleInput(engine: PulseEngine) {
        if (engine.input.wasClicked(Key.UP)) {
            var nextIndex = cursorIndex - 1
            if (nextIndex < 0) nextIndex = menuItems.size - 1
            while (menuItems[nextIndex].type == MenuItemType.Header) {
                nextIndex--
                if (nextIndex < 0) nextIndex = menuItems.size - 1
            }
            cursorIndex = nextIndex
        }

        if (engine.input.wasClicked(Key.DOWN)) {
            var nextIndex = cursorIndex + 1
            if (nextIndex >= menuItems.size) nextIndex = 0
            while (menuItems[nextIndex].type == MenuItemType.Header) {
                nextIndex++
                if (nextIndex >= menuItems.size) nextIndex = 0
            }
            cursorIndex = nextIndex
        }

        val currentItem = menuItems[cursorIndex]

        if (engine.input.wasClicked(Key.SPACE)) {
            when (currentItem.type) {
                MenuItemType.Toggle -> {
                    val currentValue = currentItem.getter?.invoke() as? Boolean ?: false
                    currentItem.setter?.invoke(!currentValue)
                }

                MenuItemType.Cycle -> {
                    currentItem.cycleSetter?.invoke()
                }

                else -> {}
            }
        }

        if (engine.input.wasClicked(Key.LEFT)) {
            if (currentItem.type == MenuItemType.Slider) {
                currentItem.sliderSetter?.invoke(-0.1f)
            }
        }

        if (engine.input.wasClicked(Key.RIGHT)) {
            if (currentItem.type == MenuItemType.Slider) {
                currentItem.sliderSetter?.invoke(0.1f)
            }
        }
    }

    /**
     * Renders the service menu overlay.
     */
    fun render(s: Surface, windowWidth: Float, windowHeight: Float) {
        s.setDrawColor(0f, 0f, 0f, 0.85f)
        s.drawQuad(0f, 0f, windowWidth, windowHeight)

        val centerX = windowWidth / 2f
        val titleY = 60f

        s.setDrawColor(0f, 1f, 1f, 1f)
        s.drawText("SERVICE MENU", centerX, titleY, fontSize = 38f, xOrigin = 0.5f, yOrigin = 0.5f)

        val startY = 100f
        val lineHeight = 18f
        var yOffset = startY

        for (i in menuItems.indices) {
            val item = menuItems[i]
            val isSelected = i == cursorIndex

            when (item.type) {
                MenuItemType.Header -> {
                    s.setDrawColor(1f, 0.8f, 0f, 1f)
                    s.drawText(item.label, centerX - 200f, yOffset, fontSize = 18f)
                }

                MenuItemType.Toggle -> {
                    val value = item.getter?.invoke() as? Boolean ?: false
                    val valueText = if (value) "ON" else "OFF"
                    val prefix = if (isSelected) "> " else "  "

                    s.setDrawColor(0.9f, 0.9f, 0.9f, 1f)
                    s.drawText("$prefix${item.label}", centerX - 200f, yOffset, fontSize = 16f)

                    s.setDrawColor(if (value) 0f else 0.5f, if (value) 1f else 0.5f, 0f, 1f)
                    s.drawText(valueText, centerX + 200f, yOffset, fontSize = 16f)
                }

                MenuItemType.Slider -> {
                    val value = item.getter?.invoke() as? Float ?: 0f
                    val valueText = "%.1f".format(value)
                    val prefix = if (isSelected) "> " else "  "

                    s.setDrawColor(0.9f, 0.9f, 0.9f, 1f)
                    s.drawText("$prefix${item.label}", centerX - 200f, yOffset, fontSize = 16f)

                    s.setDrawColor(0f, 0.8f, 1f, 1f)
                    s.drawText(valueText, centerX + 200f, yOffset, fontSize = 16f)
                }

                MenuItemType.Cycle -> {
                    val value = item.getter?.invoke()
                    val valueText = when (value) {
                        is SceneBrightness -> value.name
                        else -> value.toString()
                    }
                    val prefix = if (isSelected) "> " else "  "

                    s.setDrawColor(0.9f, 0.9f, 0.9f, 1f)
                    s.drawText("$prefix${item.label}", centerX - 200f, yOffset, fontSize = 16f)

                    s.setDrawColor(1f, 0.7f, 0f, 1f)
                    s.drawText(valueText, centerX + 200f, yOffset, fontSize = 16f)
                }
            }

            yOffset += lineHeight
        }

        s.setDrawColor(0.7f, 0.7f, 0.7f, 1f)
        s.drawText(
            "UP/DOWN: Navigate   SPACE: Toggle   LEFT/RIGHT: Adjust   S: Close",
            centerX,
            windowHeight - 40f,
            fontSize = 14f,
            xOrigin = 0.5f,
            yOrigin = 0.5f,
        )
    }
}
