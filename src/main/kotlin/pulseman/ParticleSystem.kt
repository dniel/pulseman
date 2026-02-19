package pulseman

import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.shared.primitives.Color
import kotlin.math.*
import kotlin.random.Random

/**
 * Manages the creation, update, and rendering of visual particle effects.
 *
 * Includes effects for dots/power pellets being eaten, ghost deaths,
 * Pulse-Man's death, fruit consumption, and ambient environment effects.
 */
class ParticleSystem {
    private val particles = mutableListOf<Particle>()
    private var dustEmitAccumulator = 0f
    private var confettiAccumulator = 0f
    
    var frightenedParticleTrailEnabled = true
    var ambientDustEnabled = true
    var enhancedGhostExplosionsEnabled = true
    var levelWinConfettiEnabled = true

    /**
     * Clears all active particles and resets emission accumulators.
     */
    fun reset() {
        particles.clear()
        dustEmitAccumulator = 0f
        confettiAccumulator = 0f
    }

    /**
     * Updates all active particles, applying velocity, friction, gravity, and life decay.
     */
    fun updateParticles(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) {
                it.remove()
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= 0.985f
            p.vy = p.vy * 0.985f + 5f * dt
            p.size *= 0.992f
        }
        while (particles.size > MAX_PARTICLES) particles.removeFirst()
    }

    private fun emitBurst(
        x: Float,
        y: Float,
        count: Int,
        speedMin: Float,
        speedMax: Float,
        lifeMin: Float,
        lifeMax: Float,
        sizeMin: Float,
        sizeMax: Float,
        red: Float,
        green: Float,
        blue: Float,
    ) {
        repeat(count) {
            val angle = Random.nextFloat() * (PI * 2f).toFloat()
            val speed = speedMin + Random.nextFloat() * (speedMax - speedMin)
            val life = lifeMin + Random.nextFloat() * (lifeMax - lifeMin)
            val size = sizeMin + Random.nextFloat() * (sizeMax - sizeMin)
            particles += Particle(
                x = x,
                y = y,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                size = size,
                life = life,
                maxLife = life,
                red = red,
                green = green,
                blue = blue,
            )
        }
    }

    /** Emits a burst of particles when a dot is eaten. */
    fun emitDotParticles(x: Float, y: Float) {
        emitBurst(x, y, count = 14, speedMin = 26f, speedMax = 88f, lifeMin = 0.24f, lifeMax = 0.58f, sizeMin = 1.6f, sizeMax = 3.6f, red = 1f, green = 0.94f, blue = 0.68f)
    }

    /** Emits a burst of particles when a power pellet is eaten. */
    fun emitPowerPelletParticles(x: Float, y: Float) {
        emitBurst(x, y, count = 18, speedMin = 30f, speedMax = 92f, lifeMin = 0.32f, lifeMax = 0.62f, sizeMin = 1.8f, sizeMax = 4.2f, red = 1f, green = 0.98f, blue = 0.7f)
    }

    /** Emits color-coded particles when a ghost is eaten. */
    fun emitGhostEatenParticles(x: Float, y: Float, ghostType: GhostType) {
        if (!enhancedGhostExplosionsEnabled) {
            emitBurst(x, y, count = 22, speedMin = 48f, speedMax = 130f, lifeMin = 0.35f, lifeMax = 0.85f, sizeMin = 2f, sizeMax = 4.8f, red = 1f, green = 1f, blue = 1f)
            return
        }
        val color = ghostAuraColor(ghostType)
        emitBurst(x, y, count = 28, speedMin = 48f, speedMax = 140f, lifeMin = 0.35f, lifeMax = 0.9f, sizeMin = 2f, sizeMax = 5.2f, red = color.red, green = color.green, blue = color.blue)
    }

    /** Emits a large burst of particles when Pulse-Man dies. */
    fun emitDeathParticles(x: Float, y: Float) {
        emitBurst(x, y, count = 56, speedMin = 48f, speedMax = 210f, lifeMin = 0.45f, lifeMax = 1.15f, sizeMin = 2.6f, sizeMax = 7.2f, red = 1f, green = 0.9f, blue = 0.24f)
        emitBurst(x, y, count = 40, speedMin = 30f, speedMax = 145f, lifeMin = 0.55f, lifeMax = 1.35f, sizeMin = 2.4f, sizeMax = 6.4f, red = 1f, green = 0.55f, blue = 0.12f)
        emitBurst(x, y, count = 28, speedMin = 80f, speedMax = 250f, lifeMin = 0.22f, lifeMax = 0.62f, sizeMin = 1.4f, sizeMax = 3.8f, red = 1f, green = 1f, blue = 0.95f)

        val shards = 20
        repeat(shards) { i ->
            val angle = ((i.toFloat() / shards) * (PI * 2f)).toFloat() + Random.nextFloat() * 0.08f
            val speed = 120f + Random.nextFloat() * 170f
            val life = 0.35f + Random.nextFloat() * 0.45f
            val size = 2.2f + Random.nextFloat() * 2.6f
            particles += Particle(
                x = x,
                y = y,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                size = size,
                life = life,
                maxLife = life,
                red = 1f,
                green = 0.98f,
                blue = 0.7f,
            )
        }
    }

    /** Emits particles matching the color of the consumed fruit. */
    fun emitFruitParticles(x: Float, y: Float, type: FruitType) {
        val color = when (type) {
            FruitType.CHERRY, FruitType.STRAWBERRY, FruitType.APPLE -> floatArrayOf(0.96f, 0.2f, 0.2f)
            FruitType.ORANGE -> floatArrayOf(1f, 0.64f, 0.16f)
            FruitType.MELON -> floatArrayOf(0.35f, 0.88f, 0.45f)
            FruitType.GALAXIAN, FruitType.BELL, FruitType.KEY -> floatArrayOf(1f, 0.9f, 0.35f)
        }
        emitBurst(
            x,
            y,
            count = 26,
            speedMin = 30f,
            speedMax = 115f,
            lifeMin = 0.32f,
            lifeMax = 0.78f,
            sizeMin = 1.8f,
            sizeMax = 4.6f,
            red = color[0],
            green = color[1],
            blue = color[2],
        )
    }

    /** Emits a trail of particles behind Pulse-Man during normal play. */
    fun emitPulseManTrail(x: Float, y: Float, phase: GamePhase, frightenedTimer: Float) {
        if (phase != GamePhase.PLAYING && phase != GamePhase.ATTRACT_DEMO) return
        if (frightenedTimer > 0f) return // Frightened trail takes over
        
        emitBurst(
            x = x,
            y = y,
            count = 1,
            speedMin = 8f, speedMax = 20f,
            lifeMin = 0.2f, lifeMax = 0.5f,
            sizeMin = 1.4f, sizeMax = 2.8f,
            red = 1f, green = 0.9f, blue = 0.1f,
        )
    }

    /** Emits a blue trail behind Pulse-Man while ghosts are frightened. */
    fun emitFrightenedTrail(x: Float, y: Float, phase: GamePhase, frightenedTimer: Float) {
        if (!frightenedParticleTrailEnabled) return
        if (frightenedTimer <= 0f) return
        if (phase != GamePhase.PLAYING && phase != GamePhase.ATTRACT_DEMO) return
        
        val count = if (Random.nextFloat() > 0.4f) 3 else 2
        emitBurst(
            x = x,
            y = y,
            count = count,
            speedMin = 15f, speedMax = 45f,
            lifeMin = 0.4f, lifeMax = 0.8f,
            sizeMin = 2.0f, sizeMax = 4.0f,
            red = 0.3f, green = 0.5f, blue = 1f,
        )
    }

    /** Periodically emits ambient dust particles within the maze boundaries. */
    fun emitAmbientDust(dt: Float, phase: GamePhase) {
        if (!ambientDustEnabled) return
        if (phase != GamePhase.PLAYING && phase != GamePhase.ATTRACT_DEMO) return
        
        dustEmitAccumulator += dt
        val interval = 0.15f
        if (dustEmitAccumulator < interval) return
        dustEmitAccumulator -= interval
        
        val mazePixelWidth = Maze.COLS * Maze.TILE.toFloat()
        val mazePixelHeight = Maze.ROWS * Maze.TILE.toFloat()
        val rx = Maze.OFFSET_X + Random.nextFloat() * mazePixelWidth
        val ry = Maze.OFFSET_Y + Random.nextFloat() * mazePixelHeight
        
        emitBurst(
            x = rx, y = ry,
            count = 1,
            speedMin = 2f, speedMax = 8f,
            lifeMin = 1.5f, lifeMax = 3.0f,
            sizeMin = 0.8f, sizeMax = 1.4f,
            red = 0.6f, green = 0.6f, blue = 0.7f,
        )
    }

    /** One-time burst at Pulse-Man's position when level is won. */
    fun emitLevelWinConfetti(x: Float, y: Float) {
        if (!levelWinConfettiEnabled) return
 
        for ((r, g, b) in CONFETTI_COLORS) {
            emitBurst(x, y, count = 12, speedMin = 80f, speedMax = 260f,
                lifeMin = 0.8f, lifeMax = 1.5f, sizeMin = 3f, sizeMax = 6f,
                red = r, green = g, blue = b)
        }
    }

    /** Emits a continuous stream of confetti from the top of the maze. */
    fun emitContinuousConfetti(dt: Float) {
        if (!levelWinConfettiEnabled) return

        confettiAccumulator += dt
        val emitInterval = 0.03f
        val mazeLeft = Maze.OFFSET_X
        val mazeWidth = Maze.COLS * Maze.TILE.toFloat()
        while (confettiAccumulator >= emitInterval) {
            confettiAccumulator -= emitInterval
            val rx = mazeLeft + Random.nextFloat() * mazeWidth
            val ry = Maze.OFFSET_Y + Random.nextFloat() * 40f
            val (r, g, b) = CONFETTI_COLORS[Random.nextInt(CONFETTI_COLORS.size)]
            val angle = (PI * 0.25f + Random.nextFloat() * PI * 0.5f).toFloat()
            val speed = 40f + Random.nextFloat() * 100f
            val life = 1.0f + Random.nextFloat() * 0.8f
            val size = 2.5f + Random.nextFloat() * 4f
            particles += Particle(
                x = rx, y = ry,
                vx = cos(angle) * speed * (if (Random.nextBoolean()) 1f else -1f),
                vy = sin(angle) * speed,
                size = size,
                life = life,
                maxLife = life,
                red = r, green = g, blue = b,
            )
        }
    }

    /**
     * Renders all active particles to the specified surface.
     */
    fun renderParticles(s: Surface) {
        if (particles.isEmpty()) return

        for (p in particles) {
            val life = (p.life / p.maxLife).coerceIn(0f, 1f)
            val size = p.size * (0.72f + life * 0.45f)
            s.setDrawColor(p.red, p.green, p.blue, life)
            s.drawQuad(p.x - size * 0.5f, p.y - size * 0.5f, size, size)
        }
    }

    private fun ghostAuraColor(type: GhostType): Color = when (type) {
        GhostType.BLINKY -> Color(1f, 0.22f, 0.22f, 1f)
        GhostType.PINKY -> Color(1f, 0.7f, 0.86f, 1f)
        GhostType.INKY -> Color(0.25f, 0.95f, 1f, 1f)
        GhostType.CLYDE -> Color(1f, 0.72f, 0.22f, 1f)
    }

    companion object {
        private const val MAX_PARTICLES = 400
        private val CONFETTI_COLORS = listOf(
            Triple(1f, 0.2f, 0.2f),
            Triple(0.2f, 1f, 0.3f),
            Triple(0.3f, 0.5f, 1f),
            Triple(1f, 1f, 0.2f),
            Triple(1f, 0.5f, 1f),
        )
    }
}
