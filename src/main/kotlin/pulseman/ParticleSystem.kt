package pulseman

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.core.shared.primitives.Color
import kotlin.math.*
import kotlin.random.Random

/**
 * Centralized particle FX subsystem for gameplay feedback and atmosphere.
 *
 * [ParticleSystem] owns a single in-memory list of short-lived [Particle] instances and exposes
 * focused emitter methods used by gameplay systems. Emitters are grouped by gameplay intent:
 *
 * - **Interaction feedback**: dot, power-pellet, fruit, and ghost-eaten bursts.
 * - **State feedback**: Pulse-Man trail, frightened trail, and ghost trails with mode-aware color.
 * - **Ambient dressing**: dust motes, power-pellet halo sparkles, and win confetti.
 *
 * ## Lifecycle and ownership
 * - Emitters are called from [PulseManGame] during fixed updates.
 * - [updateParticles] advances simulation and culls dead particles.
 * - [renderParticles] draws all active particles each frame.
 * - [reset] clears all runtime particle state between level/session transitions.
 *
 * ## Trail behavior model
 * Trail emitters are movement-gated so particles only spawn when a sprite has changed position.
 * Ghost trails additionally:
 * - spawn from one tile behind movement direction,
 * - inherit directional velocity bias for smoother corner transitions,
 * - switch color by ghost mode (normal type color, frightened blue/white, eaten white).
 *
 * ## Performance model
 * - Hard cap via [MAX_PARTICLES] prevents runaway growth.
 * - Emitters use small burst counts with short lifetimes.
 * - [updateParticles] applies lightweight friction/gravity and removes expired particles eagerly.
 */
class ParticleSystem(private val engine: PulseEngine) {
    private val particles = mutableListOf<Particle>()
    private val physicsWallColliders = mutableListOf<PhysicsWallCollider>()
    private var dustEmitAccumulator = 0f
    private var confettiAccumulator = 0f
    private var lastPulseTrailPosition: Pair<Float, Float>? = null
    private val lastGhostTrailPositions = mutableMapOf<GhostType, Pair<Float, Float>>()

    
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
        lastPulseTrailPosition = null
        lastGhostTrailPositions.clear()
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
        biasVx: Float = 0f,
        biasVy: Float = 0f,
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
                vx = cos(angle) * speed + biasVx,
                vy = sin(angle) * speed + biasVy,
                size = size,
                life = life,
                maxLife = life,
                red = red,
                green = green,
                blue = blue,
            )
        }
    }

    private fun emitPhysicsTrail(
        x: Float,
        y: Float,
        count: Int,
        maxActive: Int = MAX_PHYSICS_TRAIL_PARTICLES,
        layerMask: Int = PHYSICS_LAYER_NONE,
        collisionMask: Int = PHYSICS_LAYER_NONE,
        restitution: Float = 0f,
        friction: Float = 0f,
        drag: Float = 0.02f,
        density: Float = 0.1f,
        speedMin: Float,
        speedMax: Float,
        biasVx: Float = 0f,
        biasVy: Float = 0f,
        lifeMin: Float,
        lifeMax: Float,
        sizeMin: Float,
        sizeMax: Float,
        red: Float,
        green: Float,
        blue: Float,
    ) {
        repeat(count) {
            if (PhysicsTrailParticle.activeCount >= maxActive) return
            val angle = Random.nextFloat() * (PI * 2f).toFloat()
            val speed = speedMin + Random.nextFloat() * (speedMax - speedMin)
            val life = lifeMin + Random.nextFloat() * (lifeMax - lifeMin)
            val size = sizeMin + Random.nextFloat() * (sizeMax - sizeMin)
            val vx = cos(angle) * speed + biasVx
            val vy = sin(angle) * speed + biasVy
            val particle = PhysicsTrailParticle()
            particle.initialize(
                x = x,
                y = y,
                vx = vx,
                vy = vy,
                life = life,
                size = size,
                red = red,
                green = green,
                blue = blue,
                layerMask = layerMask,
                collisionMask = collisionMask,
                restitution = restitution,
                friction = friction,
                drag = drag,
                density = density,
            )
            engine.scene.addEntity(particle)
        }
    }

    /** Emits a burst of particles when a dot is eaten. */
    fun emitDotParticles(x: Float, y: Float) {
        emitBurst(x, y, count = 14, speedMin = 26f, speedMax = 88f, lifeMin = 0.24f, lifeMax = 0.58f, sizeMin = 1.6f, sizeMax = 3.6f, red = 1f, green = 0.94f, blue = 0.68f)
    }

    /** Emits physics particles when a power pellet is eaten. */
    fun emitPowerPelletParticles(x: Float, y: Float) {
        emitPhysicsTrail(
            x = x, y = y, count = 56, maxActive = MAX_PHYSICS_BURST_PARTICLES,
            layerMask = PHYSICS_LAYER_BURST_PARTICLE, collisionMask = PHYSICS_LAYER_WALL,
            restitution = 0.35f, friction = 0.08f, drag = 0.025f, density = 0.1f,
            speedMin = 48f, speedMax = 210f, lifeMin = 0.8f, lifeMax = 1.6f,
            sizeMin = 2.6f, sizeMax = 7.2f, red = 1f, green = 0.98f, blue = 0.7f,
        )
        emitPhysicsTrail(
            x = x, y = y, count = 40, maxActive = MAX_PHYSICS_BURST_PARTICLES,
            layerMask = PHYSICS_LAYER_BURST_PARTICLE, collisionMask = PHYSICS_LAYER_WALL,
            restitution = 0.3f, friction = 0.06f, drag = 0.02f, density = 0.1f,
            speedMin = 30f, speedMax = 145f, lifeMin = 0.8f, lifeMax = 1.6f,
            sizeMin = 2.4f, sizeMax = 6.4f, red = 1f, green = 0.85f, blue = 0.5f,
        )
        emitPhysicsTrail(
            x = x, y = y, count = 28, maxActive = MAX_PHYSICS_BURST_PARTICLES,
            layerMask = PHYSICS_LAYER_BURST_PARTICLE, collisionMask = PHYSICS_LAYER_WALL,
            restitution = 0.25f, friction = 0.04f, drag = 0.015f, density = 0.1f,
            speedMin = 80f, speedMax = 250f, lifeMin = 0.4f, lifeMax = 1.0f,
            sizeMin = 1.4f, sizeMax = 3.8f, red = 1f, green = 1f, blue = 0.95f,
        )
        emitPhysicsTrail(
            x = x, y = y, count = 20, maxActive = MAX_PHYSICS_BURST_PARTICLES,
            layerMask = PHYSICS_LAYER_BURST_PARTICLE, collisionMask = PHYSICS_LAYER_WALL,
            restitution = 0.3f, friction = 0.06f, drag = 0.02f, density = 0.1f,
            speedMin = 120f, speedMax = 290f, lifeMin = 0.5f, lifeMax = 1.1f,
            sizeMin = 2.2f, sizeMax = 4.8f, red = 1f, green = 0.98f, blue = 0.7f,
        )
    }

    /** Emits slow-moving particles in a rotating ring around a power pellet. */
    fun emitPowerPelletHalo(x: Float, y: Float, time: Float) {
        val count = if (Random.nextFloat() > 0.5f) 2 else 1
        repeat(count) { i ->
            val ringRadius = 14f + Random.nextFloat() * 4f
            val ringAngle = (time * 2.5f + (i.toFloat() / count) * PI * 2f).toFloat()
            val px = x + cos(ringAngle) * ringRadius
            val py = y + sin(ringAngle) * ringRadius
            val outwardSpeed = 4f + Random.nextFloat() * 8f
            val life = 0.6f + Random.nextFloat() * 0.4f
            val size = 1.2f + Random.nextFloat() * 1.2f
            particles += Particle(
                x = px,
                y = py,
                vx = cos(ringAngle) * outwardSpeed,
                vy = sin(ringAngle) * outwardSpeed,
                size = size,
                life = life,
                maxLife = life,
                red = 1f,
                green = 0.96f,
                blue = 0.72f,
            )
        }
    }

    /** Emits color-coded physics particles when a ghost is eaten. */
    fun emitGhostEatenParticles(x: Float, y: Float, ghostType: GhostType) {
        val c = ghostAuraColor(ghostType)
        emitPhysicsTrail(
            x = x, y = y, count = 56, maxActive = MAX_PHYSICS_BURST_PARTICLES,
            layerMask = PHYSICS_LAYER_BURST_PARTICLE, collisionMask = PHYSICS_LAYER_WALL,
            restitution = 0.35f, friction = 0.08f, drag = 0.025f, density = 0.1f,
            speedMin = 48f, speedMax = 210f, lifeMin = 0.8f, lifeMax = 1.6f,
            sizeMin = 2.6f, sizeMax = 7.2f, red = c.red, green = c.green, blue = c.blue,
        )
        emitPhysicsTrail(
            x = x, y = y, count = 40, maxActive = MAX_PHYSICS_BURST_PARTICLES,
            layerMask = PHYSICS_LAYER_BURST_PARTICLE, collisionMask = PHYSICS_LAYER_WALL,
            restitution = 0.3f, friction = 0.06f, drag = 0.02f, density = 0.1f,
            speedMin = 30f, speedMax = 145f, lifeMin = 0.8f, lifeMax = 1.6f,
            sizeMin = 2.4f, sizeMax = 6.4f, red = c.red * 0.7f, green = c.green * 0.7f, blue = c.blue * 0.7f,
        )
        emitPhysicsTrail(
            x = x, y = y, count = 28, maxActive = MAX_PHYSICS_BURST_PARTICLES,
            layerMask = PHYSICS_LAYER_BURST_PARTICLE, collisionMask = PHYSICS_LAYER_WALL,
            restitution = 0.25f, friction = 0.04f, drag = 0.015f, density = 0.1f,
            speedMin = 80f, speedMax = 250f, lifeMin = 0.4f, lifeMax = 1.0f,
            sizeMin = 1.4f, sizeMax = 3.8f, red = 1f, green = 1f, blue = 0.95f,
        )
        emitPhysicsTrail(
            x = x, y = y, count = 20, maxActive = MAX_PHYSICS_BURST_PARTICLES,
            layerMask = PHYSICS_LAYER_BURST_PARTICLE, collisionMask = PHYSICS_LAYER_WALL,
            restitution = 0.3f, friction = 0.06f, drag = 0.02f, density = 0.1f,
            speedMin = 120f, speedMax = 290f, lifeMin = 0.5f, lifeMax = 1.1f,
            sizeMin = 2.2f, sizeMax = 4.8f, red = c.red, green = c.green, blue = c.blue,
        )
    }

    /**
     * Emits a large burst of physics-driven particles when Pulse-Man dies.
     *
     * Uses the physics engine for wall-bounce collision while matching the classic
     * particle rendering style (crisp quads, continuous shrink, alpha fade, no emissive glow).
     */
    fun emitDeathParticles(x: Float, y: Float) {
        // Layer 1 — warm yellow main burst
        emitPhysicsTrail(
            x = x, y = y,
            count = 56,
            maxActive = MAX_PHYSICS_DEATH_PARTICLES,
            layerMask = PHYSICS_LAYER_DEATH_PARTICLE,
            collisionMask = PHYSICS_LAYER_WALL,
            restitution = 0.35f, friction = 0.08f, drag = 0.025f, density = 0.1f,
            speedMin = 48f, speedMax = 210f,
            lifeMin = 0.8f, lifeMax = 1.6f,
            sizeMin = 2.6f, sizeMax = 7.2f,
            red = 1f, green = 0.9f, blue = 0.24f,
        )
        // Layer 2 — orange secondary
        emitPhysicsTrail(
            x = x, y = y,
            count = 40,
            maxActive = MAX_PHYSICS_DEATH_PARTICLES,
            layerMask = PHYSICS_LAYER_DEATH_PARTICLE,
            collisionMask = PHYSICS_LAYER_WALL,
            restitution = 0.3f, friction = 0.06f, drag = 0.02f, density = 0.1f,
            speedMin = 30f, speedMax = 145f,
            lifeMin = 0.8f, lifeMax = 1.6f,
            sizeMin = 2.4f, sizeMax = 6.4f,
            red = 1f, green = 0.55f, blue = 0.12f,
        )
        // Layer 3 — white-hot sparks
        emitPhysicsTrail(
            x = x, y = y,
            count = 28,
            maxActive = MAX_PHYSICS_DEATH_PARTICLES,
            layerMask = PHYSICS_LAYER_DEATH_PARTICLE,
            collisionMask = PHYSICS_LAYER_WALL,
            restitution = 0.25f, friction = 0.04f, drag = 0.015f, density = 0.1f,
            speedMin = 80f, speedMax = 250f,
            lifeMin = 0.4f, lifeMax = 1.0f,
            sizeMin = 1.4f, sizeMax = 3.8f,
            red = 1f, green = 1f, blue = 0.95f,
        )
        // Layer 4 — pale yellow shards
        emitPhysicsTrail(
            x = x, y = y,
            count = 20,
            maxActive = MAX_PHYSICS_DEATH_PARTICLES,
            layerMask = PHYSICS_LAYER_DEATH_PARTICLE,
            collisionMask = PHYSICS_LAYER_WALL,
            restitution = 0.3f, friction = 0.06f, drag = 0.02f, density = 0.1f,
            speedMin = 120f, speedMax = 290f,
            lifeMin = 0.5f, lifeMax = 1.1f,
            sizeMin = 2.2f, sizeMax = 4.8f,
            red = 1f, green = 0.98f, blue = 0.7f,
        )
    }

    fun refreshPhysicsWallColliders() {
        physicsWallColliders.forEach { it.set(DEAD) }
        physicsWallColliders.clear()

        for (row in 0 until Maze.ROWS) {
            for (col in 0 until Maze.COLS) {
                val tile = Maze.grid[row][col]
                if (tile != Maze.WALL && tile != Maze.GHOST_HOUSE) continue
                val collider = PhysicsWallCollider(
                    xCenter = Maze.centerX(col),
                    yCenter = Maze.centerY(row),
                    widthPx = Maze.TILE.toFloat(),
                    heightPx = Maze.TILE.toFloat(),
                    wallLayerMask = PHYSICS_LAYER_WALL,
                )
                physicsWallColliders += collider
                engine.scene.addEntity(collider)
            }
        }
    }

    fun clearDeathBurstPhysicsParticles() {
        engine.scene.forEachEntityOfType<PhysicsTrailParticle> { particle ->
            if (particle.layerMask == PHYSICS_LAYER_DEATH_PARTICLE) {
                particle.expireNow()
            }
        }
    }

    /**
     * Expires all non-death physics trail particles (ghost trails, Pulse-Man trail, etc.)
     * to prevent ghost trail color contamination of the death burst.
     *
     * Called immediately before [emitDeathParticles] so lingering ghost trail particles
     * (e.g. cyan from Inky) do not visually overwhelm the yellow death burst.
     */
    fun clearTrailPhysicsParticles() {
        engine.scene.forEachEntityOfType<PhysicsTrailParticle> { particle ->
            if (particle.layerMask != PHYSICS_LAYER_DEATH_PARTICLE) {
                particle.expireNow()
            }
        }
    }

    /** Emits physics particles matching the color of the consumed fruit. */
    fun emitFruitParticles(x: Float, y: Float, type: FruitType) {
        val c = when (type) {
            FruitType.CHERRY, FruitType.STRAWBERRY, FruitType.APPLE -> floatArrayOf(0.96f, 0.2f, 0.2f)
            FruitType.ORANGE -> floatArrayOf(1f, 0.64f, 0.16f)
            FruitType.MELON -> floatArrayOf(0.35f, 0.88f, 0.45f)
            FruitType.GALAXIAN, FruitType.BELL, FruitType.KEY -> floatArrayOf(1f, 0.9f, 0.35f)
        }
        emitPhysicsTrail(
            x = x, y = y, count = 56, maxActive = MAX_PHYSICS_BURST_PARTICLES,
            layerMask = PHYSICS_LAYER_BURST_PARTICLE, collisionMask = PHYSICS_LAYER_WALL,
            restitution = 0.35f, friction = 0.08f, drag = 0.025f, density = 0.1f,
            speedMin = 48f, speedMax = 210f, lifeMin = 0.8f, lifeMax = 1.6f,
            sizeMin = 2.6f, sizeMax = 7.2f, red = c[0], green = c[1], blue = c[2],
        )
        emitPhysicsTrail(
            x = x, y = y, count = 40, maxActive = MAX_PHYSICS_BURST_PARTICLES,
            layerMask = PHYSICS_LAYER_BURST_PARTICLE, collisionMask = PHYSICS_LAYER_WALL,
            restitution = 0.3f, friction = 0.06f, drag = 0.02f, density = 0.1f,
            speedMin = 30f, speedMax = 145f, lifeMin = 0.8f, lifeMax = 1.6f,
            sizeMin = 2.4f, sizeMax = 6.4f, red = c[0] * 0.7f, green = c[1] * 0.7f, blue = c[2] * 0.7f,
        )
        emitPhysicsTrail(
            x = x, y = y, count = 28, maxActive = MAX_PHYSICS_BURST_PARTICLES,
            layerMask = PHYSICS_LAYER_BURST_PARTICLE, collisionMask = PHYSICS_LAYER_WALL,
            restitution = 0.25f, friction = 0.04f, drag = 0.015f, density = 0.1f,
            speedMin = 80f, speedMax = 250f, lifeMin = 0.4f, lifeMax = 1.0f,
            sizeMin = 1.4f, sizeMax = 3.8f, red = 1f, green = 1f, blue = 0.95f,
        )
        emitPhysicsTrail(
            x = x, y = y, count = 20, maxActive = MAX_PHYSICS_BURST_PARTICLES,
            layerMask = PHYSICS_LAYER_BURST_PARTICLE, collisionMask = PHYSICS_LAYER_WALL,
            restitution = 0.3f, friction = 0.06f, drag = 0.02f, density = 0.1f,
            speedMin = 120f, speedMax = 290f, lifeMin = 0.5f, lifeMax = 1.1f,
            sizeMin = 2.2f, sizeMax = 4.8f, red = c[0], green = c[1], blue = c[2],
        )
    }

    /** Emits a trail of particles behind Pulse-Man during normal play. */
    fun emitPulseManTrail(x: Float, y: Float, direction: Direction, phase: GamePhase, frightenedTimer: Float) {
        if (phase != GamePhase.PLAYING && phase != GamePhase.ATTRACT_DEMO) return
        if (frightenedTimer > 0f) return
        if (direction == Direction.NONE) return
        if (!hasMovedSinceLastPulseTrail(x, y)) return

        emitPhysicsTrail(
            x = x,
            y = y,
            count = 2,
            speedMin = 18f, speedMax = 55f,
            lifeMin = 0.4f, lifeMax = 0.9f,
            sizeMin = 1.2f, sizeMax = 2.6f,
            red = 1f, green = 0.9f, blue = 0.1f,
        )
    }

    /** Emits a blue trail behind Pulse-Man while ghosts are frightened. */
    fun emitFrightenedTrail(x: Float, y: Float, direction: Direction, phase: GamePhase, frightenedTimer: Float) {
        if (!frightenedParticleTrailEnabled) return
        if (frightenedTimer <= 0f) return
        if (phase != GamePhase.PLAYING && phase != GamePhase.ATTRACT_DEMO) return
        if (direction == Direction.NONE) return
        if (!hasMovedSinceLastPulseTrail(x, y)) return

        val count = if (Random.nextFloat() > 0.4f) 3 else 2
        emitPhysicsTrail(
            x = x,
            y = y,
            count = count,
            speedMin = 22f, speedMax = 60f,
            lifeMin = 0.5f, lifeMax = 1.0f,
            sizeMin = 1.6f, sizeMax = 3.6f,
            red = 0.3f, green = 0.5f, blue = 1f,
        )
    }

    /**
     * Emits a movement trail for a ghost using mode-aware color.
     *
     * Particles spawn at the ghost center with random velocity so they scatter
     * independently as debris rather than rigidly following the ghost path.
     * Eaten ghosts emit more particles for a denser trail.
     */
    fun emitGhostTrail(ghost: GhostState, x: Float, y: Float, phase: GamePhase) {
        if (phase != GamePhase.PLAYING && phase != GamePhase.ATTRACT_DEMO) return
        if (!ghost.released) return
        if (ghost.direction == Direction.NONE) return

        val previous = lastGhostTrailPositions[ghost.type]
        lastGhostTrailPositions[ghost.type] = x to y
        if (previous == null) return

        val mazeWidth = Maze.COLS * Maze.TILE.toFloat()
        var deltaX = x - previous.first
        if (abs(deltaX) > mazeWidth * 0.5f) {
            deltaX -= sign(deltaX) * mazeWidth
        }
        val deltaY = y - previous.second
        if (abs(deltaX) < 0.001f && abs(deltaY) < 0.001f) return

        val deltaLength = sqrt(deltaX * deltaX + deltaY * deltaY)
        if (deltaLength < 0.001f) return
        if (deltaLength > MAX_TRAIL_STEP_DELTA) return

        val color = when (ghost.mode) {
            GhostMode.EATEN -> floatArrayOf(1f, 1f, 1f)
            GhostMode.FRIGHTENED -> {
                if (Random.nextFloat() > 0.5f) floatArrayOf(0.22f, 0.42f, 1f)
                else floatArrayOf(0.96f, 0.98f, 1f)
            }
            else -> {
                val ghostColor = ghostAuraColor(ghost.type)
                floatArrayOf(ghostColor.red, ghostColor.green, ghostColor.blue)
            }
        }

        val count = if (ghost.mode == GhostMode.EATEN) 3 else 1
        emitPhysicsTrail(
            x = x,
            y = y,
            count = count,
            speedMin = 18f,
            speedMax = 55f,
            lifeMin = 0.4f,
            lifeMax = 0.9f,
            sizeMin = 1.2f,
            sizeMax = 2.6f,
            red = color[0],
            green = color[1],
            blue = color[2],
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

    private fun hasMovedSinceLastPulseTrail(x: Float, y: Float): Boolean {
        val current = x to y
        val previous = lastPulseTrailPosition
        lastPulseTrailPosition = current
        if (previous == null) return false
        return previous != current
    }

    fun resetTrailTracking() {
        lastPulseTrailPosition = null
        lastGhostTrailPositions.clear()
    }

    companion object {
        private const val MAX_PARTICLES = 400
        private const val MAX_PHYSICS_TRAIL_PARTICLES = 260
        private const val MAX_TRAIL_STEP_DELTA = 22f
        private const val MAX_PHYSICS_DEATH_PARTICLES = 420
        private const val MAX_PHYSICS_BURST_PARTICLES = 350
        private const val PHYSICS_LAYER_NONE = 0
        private const val PHYSICS_LAYER_WALL = 1 shl 0
        private const val PHYSICS_LAYER_DEATH_PARTICLE = 1 shl 1
        private const val PHYSICS_LAYER_BURST_PARTICLE = 1 shl 2
        private val CONFETTI_COLORS = listOf(
            Triple(1f, 0.2f, 0.2f),
            Triple(0.2f, 1f, 0.3f),
            Triple(0.3f, 0.5f, 1f),
            Triple(1f, 1f, 0.2f),
            Triple(1f, 0.5f, 1f),
        )
    }
}
