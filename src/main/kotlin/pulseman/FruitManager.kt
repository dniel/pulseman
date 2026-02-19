package pulseman

/**
 * Manages the spawning, timing, and consumption of bonus fruits.
 * Fruits spawn twice per level based on the number of dots eaten (70 and 170).
 */
class FruitManager(
    private val scoreManager: ScoreManager,
    private val particleSystem: ParticleSystem,
    private val soundManager: SoundManager,
) {
    /** The currently active fruit on the board, or null if no fruit is present. */
    var activeFruit: FruitState? = null
        private set

    private var fruitSpawn70Done = false
    private var fruitSpawn170Done = false

    private val fruitTypeCycle = listOf(
        FruitType.CHERRY,
        FruitType.STRAWBERRY,
        FruitType.ORANGE,
        FruitType.APPLE,
        FruitType.MELON,
        FruitType.GALAXIAN,
        FruitType.BELL,
        FruitType.KEY,
    )

    /**
     * Resets the spawn flags and removes any active fruit.
     */
    fun reset() {
        activeFruit = null
        fruitSpawn70Done = false
        fruitSpawn170Done = false
    }

    /**
     * Checks if the number of [dotsEaten] has reached the thresholds for spawning a fruit.
     */
    fun maybeSpawnFruit(dotsEaten: Int, level: Int) {
        if (!fruitSpawn70Done && dotsEaten >= 70) {
            spawnFruit(level)
            fruitSpawn70Done = true
            return
        }
        if (!fruitSpawn170Done && dotsEaten >= 170) {
            spawnFruit(level)
            fruitSpawn170Done = true
        }
    }

    /**
     * Spawns a fruit below the ghost house. The type of fruit depends on the current [level].
     */
    private fun spawnFruit(level: Int) {
        if (activeFruit != null) return
        val fruitType = fruitTypeCycle[(level - 1).mod(fruitTypeCycle.size)]
        activeFruit = FruitState(
            type = fruitType,
            col = 14,
            row = 16,
            timer = 10f,
        )
    }

    /**
     * Updates the active fruit's timer and removes it if its lifetime (10s) has expired.
     */
    fun updateFruit(dt: Float) {
        val fruit = activeFruit ?: return
        fruit.timer -= dt
        if (fruit.timer <= 0f) {
            activeFruit = null
        }
    }

    /**
     * Checks if Pulse-Man is at the same grid position as the active fruit.
     * If so, awards points, triggers effects, and removes the fruit.
     */
    fun checkFruitCollision(pacGridX: Int, pacGridY: Int): Boolean {
        val fruit = activeFruit ?: return false
        if (pacGridX == fruit.col && pacGridY == fruit.row) {
            scoreManager.addScore(fruit.type.score)
            particleSystem.emitFruitParticles(Maze.centerX(fruit.col), Maze.centerY(fruit.row), fruit.type)
            scoreManager.addPopup(Maze.centerX(fruit.col), Maze.centerY(fruit.row), fruit.type.score.toString())
            activeFruit = null
            soundManager.play("pulseman_eatfruit")
            return true
        }
        return false
    }
}
