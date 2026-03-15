package pulseman

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Texture
import no.njoh.pulseengine.core.graphics.postprocessing.effects.ColorGradingEffect
import no.njoh.pulseengine.core.graphics.postprocessing.effects.ColorGradingEffect.ToneMapper.ACES
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.lighting.global.GiOccluder
import no.njoh.pulseengine.modules.lighting.global.GiSceneRenderer
import no.njoh.pulseengine.modules.lighting.global.GlobalIlluminationSystem
import no.njoh.pulseengine.modules.scene.entities.CommonSceneEntity
import no.njoh.pulseengine.modules.scene.entities.Lamp
import no.njoh.pulseengine.modules.scene.systems.EntityRendererImpl
import kotlin.math.max
import kotlin.math.sin

/**
 * Immutable snapshot of game state passed to [LightingManager.syncSceneLights] each frame.
 * Decouples lighting updates from direct game state access so the lighting system
 * only reads a stable, consistent view of positions, modes, and timers.
 */
data class LightingSnapshot(
    val phase: GamePhase,
    val pulseX: Float,
    val pulseY: Float,
    val ghosts: List<GhostLightingState>,
    val fruit: FruitState?,
    val deathAnimTimer: Float,
    val uiPulseTime: Float,
)

data class GhostLightingState(
    val type: GhostType,
    val mode: GhostMode,
    val released: Boolean,
    val x: Float,
    val y: Float,
)

/**
 * Manages PulseEngine lighting systems for Pulse-Man.
 *
 * Creates and orchestrates multiple light layers to give the maze atmosphere in
 * [GlobalIlluminationSystem].
 *
 * - **Board backlight** — a large radial light behind the entire maze providing base illumination.
 * - **Entity aura lights** — radial lights that follow Pulse-Man, active fruit, and
 *   ghosts and power pellets, casting soft shadows through maze wall [occluders][GiMazeOccluder].
 * ### Ambient color priority
 * The scene ambient color is determined by a priority chain:
 * 1. **Fog of war** — near-black ambient for maximum contrast against entity auras
 * 2. **Scene brightness** — user-selectable LOW / MEDIUM / HIGH base ambient
 *
 * ### Lifecycle
 * Call [setupSceneLighting] once during game init to create the PulseEngine scene, lights, and
 * GI occluders/system. Then call [syncSceneLights] every frame with a
 * [LightingSnapshot] to update all light positions, intensities, and colors based on current game
 * state.
 *
 * All lighting features are individually toggleable via the service menu boolean flags.
 */
class LightingManager(private val engine: PulseEngine) {

    var lightingEnabled = true
    var entityHaloEnabled = true
    var boardBacklightEnabled = true
    var auraLightsEnabled = true
    var lightingTargetMainEnabled = true
    var sceneBrightness = SceneBrightness.MEDIUM
    var enhancedPacAuraEnabled = true
    var fogOfWarEnabled = false
    var baseLightIntensity = 0.5f
        set(value) {
            field = value.coerceIn(0f, 3f)
        }

    private var giSystem: GlobalIlluminationSystem? = null
    private var boardBacklight: Lamp? = null
    private var pulseAuraLight: Lamp? = null
    private var pulseAuraRimLight: Lamp? = null
    private val ghostAuraLights = mutableMapOf<GhostType, Lamp>()
    private val ghostRimLights = mutableMapOf<GhostType, Lamp>()
    private var fruitAuraLight: Lamp? = null
    private val powerPelletAuraLights = mutableMapOf<Pair<Int, Int>, Lamp>()

    private val giMazeOccluders = mutableListOf<GiMazeOccluder>()
    private var sceneLightingInitialized = false

    /**
     * Initializes the PulseEngine lighting scene: creates the GI system,
     * board backlight, all entity aura lights, and maze wall occluders.
     * Must be called once during game initialization after the maze grid is ready.
     */
    fun setupSceneLighting() {
        ensureSceneLightingInitialized()
        teardownGiLighting()
        setupGiLighting()
        syncSceneLights(LightingSnapshot(
            phase = GamePhase.BOOT,
            pulseX = Maze.centerX(Maze.PULSE_START_X),
            pulseY = Maze.centerY(Maze.PULSE_START_Y),
            ghosts = emptyList(),
            fruit = null,
            deathAnimTimer = 0f,
            uiPulseTime = 0f,
        ))
        engine.scene.start()
    }

    private fun ensureSceneLightingInitialized() {
        if (sceneLightingInitialized) return
        engine.scene.createEmptyAndSetActive("pulseman-lighting.scn")
        engine.scene.addSystem(EntityRendererImpl())
        addBoardBacklight()
        createAuraLights()
        sceneLightingInitialized = true
    }

    private fun setupGiLighting() {
        giSystem = GlobalIlluminationSystem().apply {
            ambientLight = giSceneBrightnessAmbient()
            ambientInteriorLight = Color(0.01f, 0.01f, 0.03f, 1f)
            skyLight = false
            lightTexScale = 0.1f
            localSceneTexScale = 0.25f
            globalSceneTexScale = 0.25f
            globalWorldScale = 4f
            upscaleSmaleSources = false
            dithering = 1.0f
            normalMapScale = 0f
            aoRadius = 18f
            aoStrength = 1.0f
            intervalLength = 1.8f
            bounceAccumulation = 0.55f
            sourceIntensity = 1.35f
            targetSurface = if (lightingTargetMainEnabled) "main" else ""
            maxCascades = 8
            maxSteps = 30
            enabled = lightingEnabled
        }
        engine.scene.addSystem(giSystem!!)
        createGiMazeOccluders()
        ensureGiColorGrading()
    }

    /** Creates a large radial light centered behind the maze to provide base illumination. */
    private fun addBoardBacklight() {
        val boardWidth = Maze.COLS * Maze.TILE.toFloat()
        val boardHeight = Maze.ROWS * Maze.TILE.toFloat()
        val boardCenterX = Maze.tileX(0) + boardWidth * 0.5f
        val boardCenterY = Maze.tileY(0) + boardHeight * 0.5f
        val radius = max(boardWidth, boardHeight) * 0.92f

        boardBacklight = RoundLamp().apply {
                trackParent = false
                x = boardCenterX
                y = boardCenterY - boardHeight * 0.03f
                z = -8f
                width = GI_SOURCE_SIZE_LARGE
                height = GI_SOURCE_SIZE_LARGE
                lightColor = Color(0.44f, 0.62f, 0.9f, 1f)
                intensity = 0.55f
                this.radius = radius
                size = 220f
                coneAngle = 360f
                spill = 1f
            }
        engine.scene.addEntity(boardBacklight!!)
    }

    /**
     * Creates all entity-tracking aura lights (Pulse-Man, ghosts, fruit, power pellets).
     */
    private fun createAuraLights() {
        pulseAuraLight = createAuraLamp(
            color = Color(1f, 0.9f, 0.2f, 1f),
            radius = 220f,
            size = 34f,
            intensity = 0.9f,
        )
        pulseAuraRimLight = createAuraLamp(
            color = Color(1f, 0.9f, 0.2f, 0.6f),
            radius = 320f,
            size = 46f,
            intensity = 0.35f,
        )

        createGhostLights()

        fruitAuraLight = createAuraLamp(
            color = Color(1f, 0.45f, 0.22f, 1f),
            radius = 180f,
            size = 28f,
            intensity = 0f,
        )

        createPowerPelletLights()

    }



    private fun createGhostLights() {
        ghostAuraLights.clear()
        ghostRimLights.clear()
        for (type in GhostType.entries) {
            ghostAuraLights[type] = createAuraLamp(
                color = ghostAuraColor(type),
                radius = 240f,
                size = 30f,
                intensity = 0f,
            )
            ghostRimLights[type] = createAuraLamp(
                color = ghostRimColor(type),
                radius = 320f,
                size = 40f,
                intensity = 0f,
            )
        }
    }

    /**
     * Creates aura lights for every power pellet in the current [Maze] grid.
     * Scans the grid for [Maze.POWER] tiles and places a radial aura light at each position.
     * Must be called again whenever the maze layout changes.
     */
    private fun createPowerPelletLights() {
        powerPelletAuraLights.clear()
        for (row in 0 until Maze.ROWS) {
            for (col in 0 until Maze.COLS) {
                if (Maze.grid[row][col] != Maze.POWER) continue
                val key = col to row
                powerPelletAuraLights[key] = createAuraLamp(
                    color = Color(1f, 1f, 1f, 1f),
                    radius = 320f,
                    size = 44f,
                    intensity = 0.9f,
                )
            }
        }
    }

    /** Factory for a radial aura [Lamp] with soft shadows, added to the scene immediately. */
    private fun createAuraLamp(color: Color, radius: Float, size: Float, intensity: Float): Lamp {
        val lamp = RoundLamp().apply {
            trackParent = false
            x = Maze.centerX(Maze.PULSE_START_X)
            y = Maze.centerY(Maze.PULSE_START_Y)
            z = -2f
            width = GI_SOURCE_SIZE_SMALL
            height = GI_SOURCE_SIZE_SMALL
            lightColor = color
            this.intensity = intensity
            this.radius = radius
            this.size = size
            coneAngle = 360f
            spill = 0.95f
        }
        engine.scene.addEntity(lamp)
        return lamp
    }

    /** Cycles through LOW → MEDIUM → HIGH scene brightness and updates the ambient color. */
    fun cycleSceneBrightness() {
        sceneBrightness = when (sceneBrightness) {
            SceneBrightness.LOW -> SceneBrightness.MEDIUM
            SceneBrightness.MEDIUM -> SceneBrightness.HIGH
            SceneBrightness.HIGH -> SceneBrightness.LOW
        }
        giSystem?.ambientLight = giSceneBrightnessAmbient()
    }

    private fun giSceneBrightnessAmbient(): Color = when (sceneBrightness) {
        SceneBrightness.LOW -> Color(0.12f, 0.12f, 0.16f, 1f)
        SceneBrightness.MEDIUM -> Color(0.18f, 0.18f, 0.24f, 1f)
        SceneBrightness.HIGH -> Color(0.25f, 0.25f, 0.32f, 1f)
    }

    private fun createGiMazeOccluders() {
        for (row in 0 until Maze.ROWS) {
            for (col in 0 until Maze.COLS) {
                if (Maze.grid[row][col] != Maze.WALL) continue
                val occluder = GiMazeOccluder().apply {
                    x = Maze.centerX(col)
                    y = Maze.centerY(row)
                    width = Maze.TILE.toFloat()
                    height = Maze.TILE.toFloat()
                    castShadows = true
                }
                engine.scene.addEntity(occluder)
                giMazeOccluders.add(occluder)
            }
        }
    }

    /**
     * Rebuilds maze wall occluders and power pellet lights to match the current [Maze] grid.
     *
     * Marks all existing [GiMazeOccluder] entities and power pellet [Lamp] entities
     * as [DEAD] so the engine removes them on the next update cycle, then creates fresh
     * entities at the positions defined by the currently loaded [MazeLayout].
     *
     * Must be called after [Maze.loadLayout] whenever the maze layout changes (e.g. level
     * transitions in Ms. Pulse-Man mode) so that shadows and pellet lights match the
     * visible maze walls.
     */
    fun refreshMazeGeometry() {
        giMazeOccluders.forEach { it.set(DEAD) }
        giMazeOccluders.clear()
        createGiMazeOccluders()

        powerPelletAuraLights.values.forEach { it.set(DEAD) }


        createPowerPelletLights()

    }

    private fun teardownGiLighting() {
        giSystem?.enabled = false
        giSystem?.let { engine.scene.removeSystem(it) }
        giSystem = null
        giMazeOccluders.forEach { it.set(DEAD) }
        giMazeOccluders.clear()
        removeGiPostProcessing()
    }

    private fun ensureGiColorGrading() {
        val surface = engine.gfx.getSurface("main") ?: return
        if (surface.getPostProcessingEffect<ColorGradingEffect>() != null) return
        surface.addPostProcessingEffect(ColorGradingEffect(
            name = GI_COLOR_GRADING_EFFECT,
            order = 100,
            toneMapper = ACES,
            exposure = 1.75f,
            contrast = 1.05f,
            saturation = 1.22f,
        ))
    }

    private fun removeGiPostProcessing() {
        val surface = engine.gfx.getSurface("main") ?: return
        surface.deletePostProcessingEffect(GI_COLOR_GRADING_EFFECT)
    }

    fun setLightingEnabledState(enabled: Boolean) {
        lightingEnabled = enabled
        giSystem?.enabled = enabled
    }

    fun setLightingTargetMain(value: Boolean) {
        lightingTargetMainEnabled = value
        giSystem?.targetSurface = if (lightingTargetMainEnabled) "main" else ""
    }

    /**
     * Updates all light positions, intensities, colors, and ambient based on the current
     * [snapshot] of game state. Called every frame from [PulseManGame.onFixedUpdate].
     *
     * During non-gameplay phases all lights are dimmed to zero. During gameplay the method
     * applies the ambient color priority chain, positions entity auras, and handles
     * death-sequence flickering.
     */
    fun syncSceneLights(snapshot: LightingSnapshot) {
        val pulse = 0.5f + 0.5f * sin(snapshot.uiPulseTime * 3.8f)
        val auraBreathe = 0.5f + 0.5f * sin(snapshot.uiPulseTime * 1.8f)
        val eatenPulse = 0.5f + 0.5f * sin(snapshot.uiPulseTime * 12f)
        val eatenBreathe = 0.5f + 0.5f * sin(snapshot.uiPulseTime * 9f)
        val playfieldLightsEnabled = snapshot.phase in setOf(
            GamePhase.PLAYING,
            GamePhase.ATTRACT_DEMO,
            GamePhase.DYING,
        )

        if (!playfieldLightsEnabled) {
            boardBacklight?.intensity = 0f
            pulseAuraLight?.intensity = 0f
            pulseAuraRimLight?.intensity = 0f
            ghostAuraLights.values.forEach { it.intensity = 0f }
            ghostRimLights.values.forEach { it.intensity = 0f }
            fruitAuraLight?.intensity = 0f
            powerPelletAuraLights.values.forEach { it.intensity = 0f }

            return
        }

        syncGiLights(playfieldLightsEnabled)

         boardBacklight?.intensity = if (fogOfWarEnabled) 0.1f else if (boardBacklightEnabled) 0.95f + pulse * 0.18f else 0f

        pulseAuraLight?.apply {
            x = snapshot.pulseX
            y = snapshot.pulseY
            spill = 1f
            width = GI_PAC_SOURCE_SIZE_MAIN
            height = GI_PAC_SOURCE_SIZE_MAIN
            if (enhancedPacAuraEnabled) {
                radius = 320f * (0.85f + auraBreathe * 0.3f)
                size = 44f
                intensity = if (auraLightsEnabled) 0.78f + pulse * 0.22f else 0f
            } else {
                radius = 220f * (0.85f + auraBreathe * 0.3f)
                size = 34f
                intensity = if (auraLightsEnabled) 0.58f + pulse * 0.32f else 0f
            }
         }
        pulseAuraRimLight?.apply {
            x = snapshot.pulseX
            y = snapshot.pulseY
            spill = 1f
            width = GI_PAC_SOURCE_SIZE_RIM
            height = GI_PAC_SOURCE_SIZE_RIM
            val baseRadius = if (enhancedPacAuraEnabled) 380f else 270f
            radius = baseRadius * (0.88f + auraBreathe * 0.24f)
            size = if (enhancedPacAuraEnabled) 52f else 40f
            val mainIntensity = pulseAuraLight?.intensity ?: 0f
            intensity = if (auraLightsEnabled) mainIntensity * 0.3f else 0f
        }

        val ghostByType = snapshot.ghosts.associateBy { it.type }
        for (type in GhostType.entries) {
            val ghost = ghostByType[type]
            val auraLight = ghostAuraLights[type]
            val rimLight = ghostRimLights[type]

            if (ghost == null || !ghost.released) {
                auraLight?.intensity = 0f
                rimLight?.intensity = 0f
                continue
            }

            if (ghost.mode == GhostMode.EATEN) {
                auraLight?.apply {
                    x = ghost.x
                    y = ghost.y
                    lightColor = Color(1f, 1f, 1f, 1f)
                    spill = 1f
                    width = GI_GHOST_SOURCE_SIZE_MAIN
                    height = GI_GHOST_SOURCE_SIZE_MAIN
                    radius = 320f * (0.85f + eatenBreathe * 0.3f)
                    size = 44f
                    intensity = if (auraLightsEnabled) 0.78f + eatenPulse * 0.22f else 0f
                }
                rimLight?.intensity = 0f
                continue
            }

            val auraColor = ghostAuraColor(type, ghost.mode, snapshot.uiPulseTime)
            val rimColor = ghostRimColor(type, ghost.mode, snapshot.uiPulseTime)

            auraLight?.apply {
                x = ghost.x
                y = ghost.y
                lightColor = auraColor
                spill = 1f
                width = GI_GHOST_SOURCE_SIZE_MAIN
                height = GI_GHOST_SOURCE_SIZE_MAIN
                radius = if (enhancedPacAuraEnabled) {
                    320f * (0.85f + auraBreathe * 0.3f)
                } else {
                    220f * (0.85f + auraBreathe * 0.3f)
                }
                size = if (enhancedPacAuraEnabled) 44f else 34f
                val intensityBoost = if (ghost.mode == GhostMode.FRIGHTENED) 1.18f else 1f
                val baseIntensity = if (enhancedPacAuraEnabled) {
                    0.78f + pulse * 0.22f
                } else {
                    0.58f + pulse * 0.32f
                }
                intensity = if (auraLightsEnabled) baseIntensity * intensityBoost else 0f
            }

            rimLight?.apply {
                x = ghost.x
                y = ghost.y
                lightColor = rimColor
                spill = 1f
                width = GI_GHOST_SOURCE_SIZE_RIM
                height = GI_GHOST_SOURCE_SIZE_RIM
                val baseRadius = if (enhancedPacAuraEnabled) 380f else 270f
                radius = baseRadius * (0.88f + auraBreathe * 0.24f)
                size = if (enhancedPacAuraEnabled) 52f else 40f
                val mainIntensity = auraLight?.intensity ?: 0f
                intensity = if (auraLightsEnabled) mainIntensity * 0.3f else 0f
            }
        }

          // Death sequence light flicker
          if (snapshot.phase == GamePhase.DYING) {
             val deathProgress = 1f - (snapshot.deathAnimTimer / 1.5f).coerceIn(0f, 1f)
             val flicker = if ((snapshot.deathAnimTimer * 12f).toInt() % 2 == 0) 0.3f else 1.0f
             val jitter = 0.5f + 0.5f * sin(snapshot.deathAnimTimer * 18f)
             val fade = (1f - deathProgress).coerceAtLeast(0f)

            pulseAuraLight?.apply {
                intensity = intensity * flicker * fade * (0.6f + jitter * 0.4f)
                radius *= (0.8f + jitter * 0.4f) * fade
            }
            pulseAuraRimLight?.apply {
                intensity = intensity * flicker * fade * (0.6f + jitter * 0.4f)
                radius *= (0.85f + jitter * 0.3f) * fade
            }
            boardBacklight?.intensity = (boardBacklight?.intensity ?: 0f) * fade
        }

         fruitAuraLight?.apply {
            val fruit = snapshot.fruit
            if (fruit == null) {
                intensity = 0f
            } else {
                val x = Maze.centerX(fruit.col)
                val y = Maze.centerY(fruit.row)
                this.x = x
                this.y = y
                intensity = if (auraLightsEnabled) 0.45f + pulse * 0.35f else 0f
            }
         }

        for ((key, light) in powerPelletAuraLights) {
            val (col, row) = key
            if (Maze.grid[row][col] == Maze.POWER) {
                val x = Maze.centerX(col)
                val y = Maze.centerY(row)
                light.x = x
                light.y = y
                light.lightColor = Color(1f, 1f, 1f, 1f)
                light.spill = 1f
                light.width = GI_PAC_SOURCE_SIZE_MAIN
                light.height = GI_PAC_SOURCE_SIZE_MAIN
                if (enhancedPacAuraEnabled) {
                    light.radius = 320f * (0.85f + auraBreathe * 0.3f)
                    light.size = 44f
                    light.intensity = if (auraLightsEnabled) 0.78f + pulse * 0.22f else 0f
                } else {
                    light.radius = 220f * (0.85f + auraBreathe * 0.3f)
                    light.size = 34f
                    light.intensity = if (auraLightsEnabled) 0.58f + pulse * 0.32f else 0f
                }
            } else {
                light.intensity = 0f
            }
        }



        applyGiIntensityScaling()
    }

    /**
     * Scales all lamp intensities and radii for [GlobalIlluminationSystem] compatibility.
     *
     * The GI system renders light sources as textured quads on a scene texture and traces
     * rays via radiance cascades. This requires much higher intensity values (100–500×)
     * to propagate light through the cascade, and infinite radius
     * (0) so that maze wall [GiMazeOccluder]s handle occlusion via SDF ray marching instead
     * of a hard distance cutoff.
     */
    private fun applyGiIntensityScaling() {
        fun scaleAura(lamp: Lamp?) {
            lamp ?: return
            lamp.intensity = lamp.intensity * GI_INTENSITY_BOOST * baseLightIntensity
            lamp.radius = 0f
        }

        scaleAura(boardBacklight)
        pulseAuraLight?.let {
            scaleAura(it)
            it.intensity *= GI_ENTITY_AURA_INTENSITY_BOOST
        }
        pulseAuraRimLight?.let {
            scaleAura(it)
            it.intensity *= GI_ENTITY_AURA_INTENSITY_BOOST
        }
        ghostAuraLights.values.forEach {
            scaleAura(it)
            it.intensity *= GI_ENTITY_AURA_INTENSITY_BOOST
        }
        ghostRimLights.values.forEach {
            scaleAura(it)
            it.intensity *= GI_ENTITY_AURA_INTENSITY_BOOST
        }
        fruitAuraLight?.let {
            scaleAura(it)
            it.intensity *= GI_ENTITY_AURA_INTENSITY_BOOST
        }
        powerPelletAuraLights.values.forEach {
            scaleAura(it)
            it.intensity *= GI_ENTITY_AURA_INTENSITY_BOOST
        }

    }

    companion object {
        private const val GI_INTENSITY_BOOST = 3f
        private const val GI_SOURCE_SIZE_SMALL = 12f
        private const val GI_SOURCE_SIZE_LARGE = 30f
        private const val GI_ENTITY_AURA_INTENSITY_BOOST = 1.8f
        private const val GI_PAC_SOURCE_SIZE_MAIN = 24f
        private const val GI_PAC_SOURCE_SIZE_RIM = 24f
        private const val GI_GHOST_SOURCE_SIZE_MAIN = 24f
        private const val GI_GHOST_SOURCE_SIZE_RIM = 24f
        private const val GI_COLOR_GRADING_EFFECT = "gi_color_grading"
    }

    private fun syncGiLights(playfieldLightsEnabled: Boolean) {
        giSystem?.ambientLight = when {
            fogOfWarEnabled && playfieldLightsEnabled -> Color(0.012f, 0.012f, 0.024f, 1f)
            else -> giSceneBrightnessAmbient()
        }
    }

    private fun ghostAuraColor(type: GhostType, mode: GhostMode = GhostMode.CHASE, uiPulseTime: Float = 0f): Color {
        if (mode == GhostMode.FRIGHTENED) {
            val flashWhite = ((uiPulseTime * 6f).toInt() % 2) == 0
            return if (flashWhite) Color(0.96f, 0.98f, 1f, 1f) else Color(0.22f, 0.42f, 1f, 1f)
        }
        return when (type) {
            GhostType.BLINKY -> Color(0.94f, 0.18f, 0.2f, 1f)
            GhostType.PINKY -> Color(1f, 0.66f, 0.82f, 1f)
            GhostType.INKY -> Color(0.16f, 0.86f, 0.95f, 1f)
            GhostType.CLYDE -> Color(0.98f, 0.6f, 0.16f, 1f)
        }
    }

    private fun ghostRimColor(type: GhostType, mode: GhostMode = GhostMode.CHASE, uiPulseTime: Float = 0f): Color {
        if (mode == GhostMode.FRIGHTENED) {
            val flashWhite = ((uiPulseTime * 6f).toInt() % 2) == 0
            return if (flashWhite) Color(0.96f, 0.98f, 1f, 0.72f) else Color(0.22f, 0.42f, 1f, 0.62f)
        }
        return when (type) {
            GhostType.BLINKY -> Color(0.96f, 0.34f, 0.36f, 0.7f)
            GhostType.PINKY -> Color(1f, 0.78f, 0.9f, 0.7f)
            GhostType.INKY -> Color(0.34f, 0.94f, 1f, 0.7f)
            GhostType.CLYDE -> Color(1f, 0.72f, 0.26f, 0.7f)
        }
    }

}

/**
 * A [Lamp] variant that renders as a circular light source in [GlobalIlluminationSystem].
 *
 * The default [Lamp.onRenderLightSource] draws a rectangular quad on the GI scene texture.
 * This override passes `cornerRadius = width * 0.5f` to [GiSceneRenderer.drawLight], producing
 * a circular source that eliminates square artifacts in GI light emitters.
 */
private class RoundLamp : Lamp() {
    override fun onRenderLightSource(engine: PulseEngine, surface: Surface) {
        if (intensity == 0f) return
        surface.setDrawColor(lightColor)
        surface.getRenderer<GiSceneRenderer>()?.drawLight(
            texture = Texture.BLANK,
            x = xInterpolated(),
            y = yInterpolated(),
            w = width,
            h = height,
            angle = rotationInterpolated(),
            intensity = intensity,
            coneAngle = coneAngle,
            radius = radius,
            cornerRadius = width * 0.5f,
        )
    }
}

private class GiMazeOccluder : CommonSceneEntity(), GiOccluder {
    override var occluderTexture = ""
    override var bounceColor = Color(0.15f, 0.15f, 0.25f)
    override var castShadows = true
    override var edgeLight = 0f

    override fun onRenderOccluder(engine: PulseEngine, surface: Surface) {
        if (!castShadows) return
        surface.setDrawColor(bounceColor)
        surface.getRenderer<GiSceneRenderer>()?.drawOccluder(
            texture = Texture.BLANK,
            x = x,
            y = y,
            w = width,
            h = height,
            angle = rotation,
            edgeLight = edgeLight,
        )
    }
}
