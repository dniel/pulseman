package pacman

class FruitManager(
    private val scoreManager: ScoreManager,
    private val particleSystem: ParticleSystem,
    private val soundManager: SoundManager,
) {
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

    fun reset() {
        activeFruit = null
        fruitSpawn70Done = false
        fruitSpawn170Done = false
    }

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

    fun updateFruit(dt: Float) {
        val fruit = activeFruit ?: return
        fruit.timer -= dt
        if (fruit.timer <= 0f) {
            activeFruit = null
        }
    }

    fun checkFruitCollision(pacGridX: Int, pacGridY: Int): Boolean {
        val fruit = activeFruit ?: return false
        if (pacGridX == fruit.col && pacGridY == fruit.row) {
            scoreManager.addScore(fruit.type.score)
            particleSystem.emitFruitParticles(Maze.centerX(fruit.col), Maze.centerY(fruit.row), fruit.type)
            scoreManager.addPopup(Maze.centerX(fruit.col), Maze.centerY(fruit.row), fruit.type.score.toString())
            activeFruit = null
            soundManager.play("pacman_eatfruit")
            return true
        }
        return false
    }
}
