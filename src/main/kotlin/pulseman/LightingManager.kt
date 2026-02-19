package pulseman

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.lighting.direct.DirectLightOccluder
import no.njoh.pulseengine.modules.lighting.direct.DirectLightType
import no.njoh.pulseengine.modules.lighting.direct.DirectShadowType
import no.njoh.pulseengine.modules.lighting.direct.DirectLightingSystem
import no.njoh.pulseengine.modules.scene.systems.EntityRendererImpl
import no.njoh.pulseengine.modules.scene.entities.Lamp
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.modules.physics.entities.Box
import kotlin.math.max
import kotlin.math.sin

/** A pair of opposing cone [Lamp]s used to create spinning directional light effects around entities. */
data class LightPair(
    val first: Lamp,
    val second: Lamp,
)

/**
 * Immutable snapshot of game state passed to [LightingManager.syncSceneLights] each frame.
 * Decouples lighting updates from direct game state access so the lighting system
 * only reads a stable, consistent view of positions, modes, and timers.
 */
data class LightingSnapshot(
    val phase: GamePhase,
    val pulseX: Float,
    val pulseY: Float,
    val ghosts: List<GhostState>,
    val fruit: FruitState?,
    val frightenedTimer: Float,
    val deathAnimTimer: Float,
    val uiPulseTime: Float,
)

/**
 * Manages the dynamic lighting system using PulseEngine's [DirectLightingSystem].
 *
 * Creates and orchestrates multiple light layers to give the maze atmosphere:
 *
 * - **Board backlight** — a large radial light behind the entire maze providing base illumination.
 * - **Entity aura lights** — radial lights that follow Pulse-Man, each ghost, active fruit, and
 *   power pellets, casting soft shadows through maze wall [occluders][LightingMazeOccluder].
 * - **Cone light pairs** — spinning directional [LightPair]s on eaten ghosts, fruit, and power
 *   pellets for dramatic visual emphasis.
 *
 * ### Ambient color priority
 * The scene ambient color is determined by a priority chain:
 * 1. **Fog of war** — near-black ambient for maximum contrast against entity auras
 * 2. **Frightened mode** — cool blue ambient shift when ghosts are vulnerable
 * 3. **Scene brightness** — user-selectable LOW / MEDIUM / HIGH base ambient
 *
 * ### Lifecycle
 * Call [setupSceneLighting] once during game init to create the PulseEngine scene, lights, and
 * occluders. Then call [syncSceneLights] every frame with a [LightingSnapshot] to update all
 * light positions, intensities, and colors based on current game state.
 *
 * All lighting features are individually toggleable via the service menu boolean flags.
 */
class LightingManager(private val engine: PulseEngine) {

    var lightingEnabled = true
    var entityHaloEnabled = true
    var boardBacklightEnabled = true
    var auraLightsEnabled = true
    var lightingTargetMainEnabled = true
    var sceneBrightness = SceneBrightness.HIGH
    var frightenedAmbientShiftEnabled = true
    var enhancedPacAuraEnabled = true
    var enhancedGhostLightsEnabled = true
    var nativeFogEnabled = false
    var nativeFogIntensity = 0.5f
    var fogOfWarEnabled = false

    private var lightingSystem: DirectLightingSystem? = null
    private var boardBacklight: Lamp? = null
    private var pulseAuraLight: Lamp? = null
    private var fruitAuraLight: Lamp? = null
    private var fruitConeLights: LightPair? = null
    private val ghostAuraLights = mutableMapOf<GhostType, Lamp>()
    private val powerPelletAuraLights = mutableMapOf<Pair<Int, Int>, Lamp>()
    private val eatenGhostConeLights = mutableMapOf<GhostType, LightPair>()
    private val powerPelletConeLights = mutableMapOf<Pair<Int, Int>, LightPair>()
    private val mazeOccluders = mutableListOf<LightingMazeOccluder>()

    /**
     * Initializes the PulseEngine lighting scene: creates the [DirectLightingSystem],
     * board backlight, all entity aura lights, and maze wall occluders.
     * Must be called once during game initialization after the maze grid is ready.
     */
    fun setupSceneLighting() {
        engine.scene.createEmptyAndSetActive("pulseman-lighting.scn")
        engine.scene.addSystem(EntityRendererImpl())

        lightingSystem = DirectLightingSystem().apply {
            ambientColor = sceneBrightnessAmbient()
            dithering = 0f
            textureScale = 1f
            enableFXAA = false
            useNormalMap = false
            enableLightSpill = true
            targetSurfaces = if (lightingTargetMainEnabled) "main" else ""
            drawDebug = false
            enabled = lightingEnabled
        }
        engine.scene.addSystem(lightingSystem!!)

        addBoardBacklight()
        createAuraLights()
        createMazeOccluders()
        syncSceneLights(LightingSnapshot(
            phase = GamePhase.BOOT,
            pulseX = Maze.centerX(Maze.PULSE_START_X),
            pulseY = Maze.centerY(Maze.PULSE_START_Y),
            ghosts = emptyList(),
            fruit = null,
            frightenedTimer = 0f,
            deathAnimTimer = 0f,
            uiPulseTime = 0f,
        ))
        engine.scene.start()
    }

    /** Creates a large radial light centered behind the maze to provide base illumination. */
    private fun addBoardBacklight() {
        val boardWidth = Maze.COLS * Maze.TILE.toFloat()
        val boardHeight = Maze.ROWS * Maze.TILE.toFloat()
        val boardCenterX = Maze.tileX(0) + boardWidth * 0.5f
        val boardCenterY = Maze.tileY(0) + boardHeight * 0.5f
        val radius = max(boardWidth, boardHeight) * 0.92f

        boardBacklight = Lamp().apply {
                trackParent = false
                x = boardCenterX
                y = boardCenterY - boardHeight * 0.03f
                z = -8f
                lightColor = Color(0.44f, 0.62f, 0.9f, 1f)
                intensity = 0.55f
                this.radius = radius
                size = 220f
                coneAngle = 360f
                spill = 1f
                shadowType = DirectShadowType.NONE
            }
        engine.scene.addEntity(boardBacklight!!)
    }

    /**
     * Creates all entity-tracking aura lights (Pulse-Man, ghosts, fruit, power pellets)
     * and their associated cone light pairs for spinning visual emphasis.
     */
    private fun createAuraLights() {
        pulseAuraLight = createAuraLamp(
            color = Color(1f, 0.92f, 0.3f, 1f),
            radius = 220f,
            size = 34f,
            intensity = 0.9f,
        )

        fruitAuraLight = createAuraLamp(
            color = Color(1f, 0.45f, 0.22f, 1f),
            radius = 180f,
            size = 28f,
            intensity = 0f,
        )
        fruitConeLights = createConePair(
            color = Color(1f, 0.55f, 0.24f, 1f),
            radius = 170f,
            size = 30f,
            coneAngle = 34f,
            intensity = 0f,
        )

        ghostAuraLights.clear()
        eatenGhostConeLights.clear()
        for (type in GhostType.entries) {
            ghostAuraLights[type] = createAuraLamp(
                color = ghostAuraColor(type),
                radius = 170f,
                size = 26f,
                intensity = 0.65f,
            )
            eatenGhostConeLights[type] = createConePair(
                color = Color(1f, 1f, 1f, 1f),
                radius = 250f,
                size = 44f,
                coneAngle = 36f,
                intensity = 0f,
            )
        }

        createPowerPelletLights()
    }

    /**
     * Creates aura and cone light pairs for every power pellet in the current [Maze] grid.
     * Scans the grid for [Maze.POWER] tiles and places a radial aura light plus a spinning
     * cone [LightPair] at each position. Must be called again whenever the maze layout changes.
     */
    private fun createPowerPelletLights() {
        powerPelletAuraLights.clear()
        powerPelletConeLights.clear()
        for (row in 0 until Maze.ROWS) {
            for (col in 0 until Maze.COLS) {
                if (Maze.grid[row][col] != Maze.POWER) continue
                val key = col to row
                powerPelletAuraLights[key] = createAuraLamp(
                    color = Color(1f, 0.97f, 0.78f, 1f),
                    radius = 125f,
                    size = 18f,
                    intensity = 0.5f,
                )
                powerPelletConeLights[key] = createConePair(
                    color = Color(0.9f, 0.98f, 1f, 1f),
                    radius = 145f,
                    size = 26f,
                    coneAngle = 34f,
                    intensity = 0.22f,
                )
            }
        }
    }

    /** Factory for a radial aura [Lamp] with soft shadows, added to the scene immediately. */
    private fun createAuraLamp(color: Color, radius: Float, size: Float, intensity: Float): Lamp {
        val lamp = Lamp().apply {
            trackParent = false
            x = Maze.centerX(Maze.PULSE_START_X)
            y = Maze.centerY(Maze.PULSE_START_Y)
            z = -2f
            lightColor = color
            this.intensity = intensity
            this.radius = radius
            this.size = size
            coneAngle = 360f
            spill = 0.95f
            type = DirectLightType.RADIAL
            shadowType = DirectShadowType.SOFT
        }
        engine.scene.addEntity(lamp)
        return lamp
    }

    /** Factory for a [LightPair] of opposing linear cone lights (no shadows), used for spinning accents. */
    private fun createConePair(color: Color, radius: Float, size: Float, coneAngle: Float, intensity: Float): LightPair {
        val first = createAuraLamp(color, radius, size, intensity).apply {
            type = DirectLightType.LINEAR
            this.coneAngle = coneAngle
            shadowType = DirectShadowType.NONE
            spill = 1f
        }
        val second = createAuraLamp(color, radius, size, intensity).apply {
            type = DirectLightType.LINEAR
            this.coneAngle = coneAngle
            shadowType = DirectShadowType.NONE
            spill = 1f
        }
        return LightPair(first, second)
    }

    /** Returns the signature aura color for a ghost type (red, pink, cyan, orange). */
    fun ghostAuraColor(type: GhostType): Color = when (type) {
        GhostType.BLINKY -> Color(1f, 0.22f, 0.22f, 1f)
        GhostType.PINKY -> Color(1f, 0.7f, 0.86f, 1f)
        GhostType.INKY -> Color(0.25f, 0.95f, 1f, 1f)
        GhostType.CLYDE -> Color(1f, 0.72f, 0.22f, 1f)
    }

    /** Cycles through LOW → MEDIUM → HIGH scene brightness and updates the ambient color. */
    fun cycleSceneBrightness() {
        sceneBrightness = when (sceneBrightness) {
            SceneBrightness.LOW -> SceneBrightness.MEDIUM
            SceneBrightness.MEDIUM -> SceneBrightness.HIGH
            SceneBrightness.HIGH -> SceneBrightness.LOW
        }
        lightingSystem?.ambientColor = sceneBrightnessAmbient()
    }

    private fun sceneBrightnessAmbient(): Color = when (sceneBrightness) {
        SceneBrightness.LOW -> Color(0.13f, 0.13f, 0.16f, 0.98f)
        SceneBrightness.MEDIUM -> Color(0.18f, 0.18f, 0.22f, 0.98f)
        SceneBrightness.HIGH -> Color(0.24f, 0.24f, 0.3f, 0.98f)
    }

    /** Creates a [LightingMazeOccluder] entity for every wall and ghost-house tile so lights cast shadows. */
    private fun createMazeOccluders() {
        for (row in 0 until Maze.ROWS) {
            for (col in 0 until Maze.COLS) {
                if (!Maze.isWallForOutline(col, row)) continue
                val occluder = LightingMazeOccluder().apply {
                    x = Maze.centerX(col)
                    y = Maze.centerY(row)
                    width = Maze.TILE.toFloat()
                    height = Maze.TILE.toFloat()
                    castShadows = true
                }
                engine.scene.addEntity(occluder)
                mazeOccluders.add(occluder)
            }
        }
    }

    /**
     * Rebuilds maze wall occluders and power pellet lights to match the current [Maze] grid.
     *
     * Marks all existing [LightingMazeOccluder] entities and power pellet [Lamp] entities
     * as [DEAD] so the engine removes them on the next update cycle, then creates fresh
     * entities at the positions defined by the currently loaded [MazeLayout].
     *
     * Must be called after [Maze.loadLayout] whenever the maze layout changes (e.g. level
     * transitions in Ms. Pulse-Man mode) so that shadows and pellet lights match the
     * visible maze walls.
     */
    fun refreshMazeGeometry() {
        mazeOccluders.forEach { it.set(DEAD) }
        mazeOccluders.clear()

        powerPelletAuraLights.values.forEach { it.set(DEAD) }
        powerPelletConeLights.values.forEach { pair ->
            pair.first.set(DEAD)
            pair.second.set(DEAD)
        }

        createMazeOccluders()
        createPowerPelletLights()
    }

    fun setLightingEnabledState(enabled: Boolean) {
        lightingEnabled = enabled
        lightingSystem?.enabled = enabled
    }

    fun setLightingTargetMain(value: Boolean) {
        lightingTargetMainEnabled = value
        lightingSystem?.targetSurfaces = if (lightingTargetMainEnabled) "main" else ""
    }

    /**
     * Updates all light positions, intensities, colors, and ambient based on the current
     * [snapshot] of game state. Called every frame from [PulseManGame.onFixedUpdate].
     *
     * During non-gameplay phases all lights are dimmed to zero. During gameplay the method
     * applies the ambient color priority chain, positions entity auras, handles death-sequence
     * flickering, and animates spinning cone lights.
     */
    fun syncSceneLights(snapshot: LightingSnapshot) {
        val pulse = 0.5f + 0.5f * sin(snapshot.uiPulseTime * 3.8f)
        val spin = (snapshot.uiPulseTime * 220f) % 360f
        val playfieldLightsEnabled = snapshot.phase in setOf(
            GamePhase.PLAYING,
            GamePhase.ATTRACT_DEMO,
            GamePhase.DYING,
        )

        if (!playfieldLightsEnabled) {
            boardBacklight?.intensity = 0f
            pulseAuraLight?.intensity = 0f
            fruitAuraLight?.intensity = 0f
            fruitConeLights?.first?.intensity = 0f
            fruitConeLights?.second?.intensity = 0f
            ghostAuraLights.values.forEach { it.intensity = 0f }
            eatenGhostConeLights.values.forEach {
                it.first.intensity = 0f
                it.second.intensity = 0f
            }
            powerPelletAuraLights.values.forEach { it.intensity = 0f }
            powerPelletConeLights.values.forEach {
                it.first.intensity = 0f
                it.second.intensity = 0f
            }
            return
        }

        // Ambient color priority chain: fogOfWar > frightenedAmbient > sceneBrightness
        val anyGhostFrightened = snapshot.ghosts.any { it.mode == GhostMode.FRIGHTENED }
        if (fogOfWarEnabled && playfieldLightsEnabled) {
            lightingSystem?.ambientColor = Color(0.005f, 0.005f, 0.015f, 0.95f)  // Near-black
        } else if (frightenedAmbientShiftEnabled && anyGhostFrightened) {
            lightingSystem?.ambientColor = Color(0.02f, 0.03f, 0.12f, 0.85f)  // Cool blue
        } else {
            lightingSystem?.ambientColor = sceneBrightnessAmbient()  // Normal
        }

         // Native fog control
         lightingSystem?.apply {
              if (nativeFogIntensity > 0f && playfieldLightsEnabled) {
                 fogIntensity = nativeFogIntensity
                 fogTurbulence = 1.5f
                 fogScale = 0.3f
             } else {
                 fogIntensity = 0f
             }
         }

         boardBacklight?.intensity = if (fogOfWarEnabled) 0.05f else if (boardBacklightEnabled) 0.5f + pulse * 0.1f else 0f

        pulseAuraLight?.apply {
            x = snapshot.pulseX
            y = snapshot.pulseY
            if (enhancedPacAuraEnabled) {
                radius = 320f
                size = 44f
                intensity = if (auraLightsEnabled) 0.78f + pulse * 0.22f else 0f
            } else {
                radius = 220f
                size = 34f
                intensity = if (auraLightsEnabled) 0.58f + pulse * 0.32f else 0f
            }
         }

         // Death sequence light flicker
         if (snapshot.phase == GamePhase.DYING) {
             val deathProgress = 1f - (snapshot.deathAnimTimer / 1.5f).coerceIn(0f, 1f)
             val flicker = if ((snapshot.deathAnimTimer * 12f).toInt() % 2 == 0) 0.3f else 1.0f
             val fade = (1f - deathProgress).coerceAtLeast(0f)

             pulseAuraLight?.intensity = (pulseAuraLight?.intensity ?: 0f) * flicker * fade
             ghostAuraLights.values.forEach { it.intensity = it.intensity * flicker * fade * 0.5f }
             boardBacklight?.intensity = (boardBacklight?.intensity ?: 0f) * fade
         }

         fruitAuraLight?.apply {
            val fruit = snapshot.fruit
            if (fruit == null) {
                intensity = 0f
                fruitConeLights?.let {
                    it.first.intensity = 0f
                    it.second.intensity = 0f
                }
            } else {
                val x = Maze.centerX(fruit.col)
                val y = Maze.centerY(fruit.row)
                this.x = x
                this.y = y
                intensity = if (auraLightsEnabled) 0.45f + pulse * 0.35f else 0f
                fruitConeLights?.let {
                    val base = (spin + 73f) % 360f
                    it.first.x = x
                    it.first.y = y
                    it.first.rotation = base
                    it.first.intensity = if (auraLightsEnabled) 0.5f + pulse * 0.3f else 0f
                     it.second.x = x
                     it.second.y = y
                      it.second.rotation = (base + 180f) % 360f
                      it.second.intensity = if (auraLightsEnabled) 0.5f + pulse * 0.3f else 0f
                }
            }
        }

        for (ghost in snapshot.ghosts) {
            val light = ghostAuraLights[ghost.type] ?: continue
            val conePair = eatenGhostConeLights[ghost.type]
            light.x = if (!ghost.released && ghost.mode != GhostMode.EATEN) Maze.centerX(ghost.gridX) else ghostPixelX(ghost)
            light.y = if (!ghost.released && ghost.mode != GhostMode.EATEN) Maze.centerY(ghost.gridY) else ghostPixelY(ghost)

            when (ghost.mode) {
                GhostMode.FRIGHTENED -> {
                    light.lightColor = Color(0.42f, 0.58f, 1f, 1f)
                    light.intensity = if (auraLightsEnabled) 0.42f + pulse * 0.2f else 0f
                    conePair?.let {
                        it.first.intensity = 0f
                        it.second.intensity = 0f
                    }
                }

                GhostMode.EATEN -> {
                    light.lightColor = Color(1f, 1f, 1f, 1f)
                    light.intensity = if (auraLightsEnabled) 0.22f + pulse * 0.08f else 0f
                    conePair?.let {
                        it.first.x = light.x
                        it.first.y = light.y
                        it.first.rotation = spin
                        it.first.intensity = if (auraLightsEnabled) 0.5f + pulse * 0.3f else 0f
                         it.second.x = light.x
                         it.second.y = light.y
                          it.second.rotation = (spin + 180f) % 360f
                          it.second.intensity = if (auraLightsEnabled) 0.5f + pulse * 0.3f else 0f
                    }
                }

                else -> {
                    light.lightColor = ghostAuraColor(ghost.type)
                    if (enhancedGhostLightsEnabled) {
                        light.radius = 220f
                        light.size = 32f
                        light.intensity = if (auraLightsEnabled) 0.85f + pulse * 0.15f else 0f
                    } else {
                        light.radius = 170f
                        light.size = 26f
                        light.intensity = if (auraLightsEnabled) 0.38f + pulse * 0.24f else 0f
                    }
                    conePair?.let {
                        it.first.intensity = 0f
                        it.second.intensity = 0f
                    }
                }
            }
        }

        for ((key, light) in powerPelletAuraLights) {
            val (col, row) = key
            val conePair = powerPelletConeLights[key]
            if (Maze.grid[row][col] == Maze.POWER) {
                val x = Maze.centerX(col)
                val y = Maze.centerY(row)
                light.x = x
                light.y = y
                light.intensity = if (auraLightsEnabled) 0.28f + pulse * 0.24f else 0f
                conePair?.let {
                    val base = (spin + (col * 11f + row * 7f)) % 360f
                    it.first.x = x
                    it.first.y = y
                    it.first.rotation = base
                    it.first.intensity = if (auraLightsEnabled) 0.5f + pulse * 0.3f else 0f
                     it.second.x = x
                     it.second.y = y
                      it.second.rotation = (base + 180f) % 360f
                      it.second.intensity = if (auraLightsEnabled) 0.5f + pulse * 0.3f else 0f
                }
            } else {
                light.intensity = 0f
                conePair?.let {
                    it.first.intensity = 0f
                    it.second.intensity = 0f
                }
            }
        }
    }

    private fun ghostPixelX(g: GhostState): Float = Maze.centerX(g.gridX) + g.direction.dx * g.progress * Maze.TILE
    private fun ghostPixelY(g: GhostState): Float = Maze.centerY(g.gridY) + g.direction.dy * g.progress * Maze.TILE
}

/**
 * A tile-sized box entity that blocks light, creating shadows behind maze walls.
 * Implements [DirectLightOccluder] so the [DirectLightingSystem] recognizes it as a shadow caster.
 * Only renders on the occluder surface — invisible on the main game surface.
 */
private class LightingMazeOccluder : Box(), DirectLightOccluder {
    override var castShadows = true

    override fun onRender(engine: PulseEngine, surface: Surface) {
        if (surface.config.name != DirectLightingSystem.OCCLUDER_SURFACE_NAME) return
        surface.setDrawColor(1f, 1f, 1f, 1f)
        surface.drawQuad(
            x = xInterpolated() - width * 0.5f,
            y = yInterpolated() - height * 0.5f,
            width = width,
            height = height,
        )
    }
}
