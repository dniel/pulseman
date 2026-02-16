package pacman

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.PulseEngineGame
import no.njoh.pulseengine.core.asset.types.Sound
import no.njoh.pulseengine.core.graphics.api.BlendFunction
import no.njoh.pulseengine.core.graphics.postprocessing.effects.BloomEffect
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.core.shared.primitives.Color
import no.njoh.pulseengine.modules.lighting.direct.DirectLightOccluder
import no.njoh.pulseengine.modules.lighting.direct.DirectLightType
import no.njoh.pulseengine.modules.lighting.direct.DirectShadowType
import no.njoh.pulseengine.modules.lighting.direct.DirectLightingSystem
import no.njoh.pulseengine.modules.scene.systems.EntityRendererImpl
import no.njoh.pulseengine.modules.scene.entities.Lamp
import no.njoh.pulseengine.modules.physics.entities.Box
import java.io.File
import kotlin.math.*
import kotlin.random.Random

fun main() = PulseEngine.run<PacmanGame>()

class PacmanGame : PulseEngineGame() {

    private var pacGridX = Maze.PAC_START_X
    private var pacGridY = Maze.PAC_START_Y
    private var pacDir = Direction.NONE
    private var pacNextDir = Direction.NONE
    private var pacProgress = 0f
    private var mouthAngle = 0.25f
    private var mouthOpening = true

    private val ghosts = mutableListOf<GhostState>()
    private val scorePopups = mutableListOf<ScorePopup>()
    private val particles = mutableListOf<Particle>()

    private var score = 0
    private var highScore = 0
    private var lives = 3
    private var level = 1

    private var phase = GamePhase.READY
    private var bootTimer = 0f
    private var attractTimer = 0f
    private var titlePointsTimer = 0f
    private var hiScoreTimer = 0f
    private var attractDemoTimer = 0f
    private var attractDemoGameOverTimer = 0f
    private var gameOverTimer = 0f
    private var readyTimer = 2f
    private var wonTimer = 0f
    private var levelTransitionTimer = 0f
    private var frightenedTimer = 0f
    private var ghostModeTimer = 0f
    private var ghostModeIndex = 0
    private var currentGhostMode = GhostMode.SCATTER
    private var deathAnimTimer = 0f
    private var uiPulseTime = 0f
    private var lightingEnabled = true
    private var crtEnabled = true
    private var scanlineEnabled = true
    private var bloomEnabled = true
    private var crtStrength = 1f
    private var scanlineStrength = 1f
    private var bloomStrength = 0.5f
    private var entityHaloEnabled = true
    private var boardBacklightEnabled = true
    private var auraLightsEnabled = true
    private var wallBevelEnabled = true
    private var wallOutlineEnabled = true
    private var wallThinOutlineMode = false
    private var geometryTestOverlayEnabled = false
    private var lightingTargetMainEnabled = true
    private var wallBevelDebug = false
    private var sceneBrightness = SceneBrightness.HIGH
    private var serviceMenuOpen = false
    private var serviceMenuCursorIndex = 1
    private var bootTestHold = false
    private var debugLayerVisible = false
    private var lightingSystem: DirectLightingSystem? = null
    private var boardBacklight: Lamp? = null
    private var pacAuraLight: Lamp? = null
    private var fruitAuraLight: Lamp? = null
    private var fruitConeLights: LightPair? = null
    private val ghostAuraLights = mutableMapOf<GhostType, Lamp>()
    private val powerPelletAuraLights = mutableMapOf<Pair<Int, Int>, Lamp>()
    private val eatenGhostConeLights = mutableMapOf<GhostType, LightPair>()
    private val powerPelletConeLights = mutableMapOf<Pair<Int, Int>, LightPair>()

    private val menuItems: List<MenuItem> by lazy { buildMenuItems() }

    private var dotsEatenThisLevel = 0
    private var fruitSpawn70Done = false
    private var fruitSpawn170Done = false
    private var activeFruit: FruitState? = null

    private val bootDuration = 5f
    private val startScreenDelay = 8f
    private val attractDemoDuration = 10f

    private var ghostReleaseTimers = floatArrayOf(0f, 3f, 6f, 9f)
    private var pelletsEatenForGhostScore = 0

    private val gameSpeedScale = 0.5f
    private val pacSpeed = 7f * gameSpeedScale
    private val eatenGhostSpeed = 12f * gameSpeedScale
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

    private val modeSequence = listOf(
        GhostMode.SCATTER to 7f,
        GhostMode.CHASE to 20f,
        GhostMode.SCATTER to 7f,
        GhostMode.CHASE to 20f,
        GhostMode.SCATTER to 5f,
        GhostMode.CHASE to Float.MAX_VALUE,
    )

    override fun onCreate() {
        engine.config.gameName = "PulsDniel Pacman"
        engine.config.fixedTickRate = 60f
        engine.config.targetFps = 60
        engine.console.runScript("init.pes")
        engine.console.runScript("init-dev.pes")
        setupUiSurface()
        setupDebugSurface()
        configurePostEffects()
        loadGameSounds()
        highScore = engine.data.loadObject<HighScoreData>("highscore.json")?.score ?: 0
        resetGame()
        setupSceneLighting()
    }

    override fun onDestroy() {
        engine.data.saveObject(HighScoreData(highScore), "highscore.json")
    }

    override fun onUpdate() {
        if (engine.input.wasClicked(Key.S)) {
            serviceMenuOpen = !serviceMenuOpen
            if (serviceMenuOpen) {
                if (menuItems[serviceMenuCursorIndex].type is MenuItemType.Header) {
                    serviceMenuCursorIndex = 1
                }
            }
        }

        if (serviceMenuOpen) {
            handleServiceMenuInput()
            return
        }

        if (engine.input.wasClicked(Key.ENTER) && phase in setOf(GamePhase.BOOT, GamePhase.ATTRACT, GamePhase.ATTRACT_DEMO, GamePhase.TITLE_POINTS, GamePhase.HI_SCORE)) {
            startNewGameFromStartup()
            return
        }

        if (engine.input.wasClicked(Key.UP) || engine.input.wasClicked(Key.W)) pacNextDir = Direction.UP
        if (engine.input.wasClicked(Key.DOWN)) pacNextDir = Direction.DOWN
        if (engine.input.wasClicked(Key.LEFT) || engine.input.wasClicked(Key.A)) pacNextDir = Direction.LEFT
        if (engine.input.wasClicked(Key.RIGHT)) pacNextDir = Direction.RIGHT
        if (engine.input.wasClicked(Key.D)) debugLayerVisible = !debugLayerVisible
        if (engine.input.wasClicked(Key.R)) resetGame()
        if (engine.input.wasClicked(Key.T)) {
            if (bootTestHold) {
                bootTestHold = false
            } else {
                phase = GamePhase.BOOT
                bootTimer = bootDuration - 2.9f
                bootTestHold = true
                setLightingEnabledState(false)
            }
        }
        if (engine.input.wasClicked(Key.ENTER) && phase == GamePhase.GAME_OVER) {
            enterStartScreen()
        }
    }

    private fun configurePostEffects() {
        if (crtEnabled) ensureCRTEffects()
        updateCRTEffectSettings()
        if (scanlineEnabled) {
            ensureScanlineEffects()
            updateScanlineEffectSettings()
        }
        if (bloomEnabled) {
            ensureBloomEffects()
            updateBloomEffectSettings()
        }
    }

    private fun ensureCRTEffects() {
        val mainSurface = engine.gfx.mainSurface
        if (mainSurface.getPostProcessingEffect(CRT_EFFECT_NAME) == null) {
            mainSurface.addPostProcessingEffect(CRTEffect(name = CRT_EFFECT_NAME))
        }
    }

    private fun deleteCRTEffects() {
        engine.gfx.mainSurface.deletePostProcessingEffect(CRT_EFFECT_NAME)
    }

    private fun ensureScanlineEffects() {
        val mainSurface = engine.gfx.mainSurface
        if (mainSurface.getPostProcessingEffect(SCANLINE_EFFECT_NAME) == null) {
            mainSurface.addPostProcessingEffect(ScanlineEffect(name = SCANLINE_EFFECT_NAME))
        }
    }

    private fun deleteScanlineEffects() {
        engine.gfx.mainSurface.deletePostProcessingEffect(SCANLINE_EFFECT_NAME)
    }

    private fun ensureBloomEffects() {
        val mainSurface = engine.gfx.mainSurface
        if (mainSurface.getPostProcessingEffect(BLOOM_EFFECT_NAME) == null) {
            mainSurface.addPostProcessingEffect(
                BloomEffect(name = BLOOM_EFFECT_NAME).apply {
                    intensity = 0.85f
                    threshold = 1.05f
                    thresholdSoftness = 0.7f
                    radius = 0.0038f
                }
            )
        }
    }

    private fun updateBloomEffectSettings() {
        val gameplayPhase = isGameplayVisualPhase()
        val mainBloom = engine.gfx.mainSurface.getPostProcessingEffect(BLOOM_EFFECT_NAME) as? BloomEffect
        mainBloom?.apply {
            intensity = if (gameplayPhase) 1.35f * bloomStrength else 0.95f * bloomStrength
            threshold = if (gameplayPhase) 0.78f else 0.9f
            thresholdSoftness = if (gameplayPhase) 0.86f else 0.78f
            radius = if (gameplayPhase) {
                0.0062f + (bloomStrength - 1f) * 0.0018f
            } else {
                0.0042f + (bloomStrength - 1f) * 0.0012f
            }
        }
    }

    private fun deleteBloomEffects() {
        engine.gfx.mainSurface.deletePostProcessingEffect(BLOOM_EFFECT_NAME)
    }

    private fun setupUiSurface() {
        engine.gfx.createSurface(UI_SURFACE_NAME)
            .setBackgroundColor(0f, 0f, 0f, 0f)
            .setBlendFunction(BlendFunction.NORMAL)
            .setIsVisible(true)
    }

    private fun setupDebugSurface() {
        engine.gfx.createSurface(DEBUG_SURFACE_NAME)
            .setBackgroundColor(0f, 0f, 0f, 0f)
            .setBlendFunction(BlendFunction.NORMAL)
            .setIsVisible(true)
    }

    private fun updateCRTEffectSettings() {
        val mainEffect = engine.gfx.mainSurface.getPostProcessingEffect(CRT_EFFECT_NAME) as? CRTEffect
        if (!crtEnabled) {
            mainEffect?.apply {
                vignetteStrength = 0f
                curvature = 0f
            }
            return
        }

        val vignetteBase = 0.2f
        val curvatureBase = 0.035f
        mainEffect?.apply {
            vignetteStrength = vignetteBase * crtStrength
            curvature = curvatureBase * crtStrength
        }
    }

    private fun updateScanlineEffectSettings() {
        val mainEffect = engine.gfx.mainSurface.getPostProcessingEffect(SCANLINE_EFFECT_NAME) as? ScanlineEffect
        val strength = if (scanlineEnabled) scanlineStrength else 0f
        mainEffect?.strength = strength
    }

    private fun hasMainCrtEffect(): Boolean = engine.gfx.mainSurface.getPostProcessingEffect(CRT_EFFECT_NAME) != null

    private fun hasMainScanlineEffect(): Boolean = engine.gfx.mainSurface.getPostProcessingEffect(SCANLINE_EFFECT_NAME) != null

    private fun hasMainBloomEffect(): Boolean = engine.gfx.mainSurface.getPostProcessingEffect(BLOOM_EFFECT_NAME) != null

    private fun setupSceneLighting() {
        engine.scene.createEmptyAndSetActive("pacman-lighting.scn")
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
        syncSceneLights()
        engine.scene.start()
    }

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

    private fun createAuraLights() {
        pacAuraLight = createAuraLamp(
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

    private fun createAuraLamp(color: Color, radius: Float, size: Float, intensity: Float): Lamp {
        val lamp = Lamp().apply {
            trackParent = false
            x = Maze.centerX(Maze.PAC_START_X)
            y = Maze.centerY(Maze.PAC_START_Y)
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

    private fun ghostAuraColor(type: GhostType): Color = when (type) {
        GhostType.BLINKY -> Color(1f, 0.22f, 0.22f, 1f)
        GhostType.PINKY -> Color(1f, 0.7f, 0.86f, 1f)
        GhostType.INKY -> Color(0.25f, 0.95f, 1f, 1f)
        GhostType.CLYDE -> Color(1f, 0.72f, 0.22f, 1f)
    }

    private fun cycleSceneBrightness() {
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

    private fun buildMenuItems(): List<MenuItem> = listOf(
        MenuItem(
            label = "POST-PROCESSING",
            type = MenuItemType.Header,
        ),
        MenuItem(
            label = "CRT",
            type = MenuItemType.Toggle,
            getter = { crtEnabled },
            setter = { value ->
                crtEnabled = value
                if (crtEnabled) {
                    ensureCRTEffects()
                    updateCRTEffectSettings()
                } else {
                    deleteCRTEffects()
                }
            },
        ),
        MenuItem(
            label = "CRT Strength",
            type = MenuItemType.Slider,
            getter = { crtStrength },
            sliderSetter = { delta ->
                crtStrength = (crtStrength + delta).coerceIn(0f, 2f)
                updateCRTEffectSettings()
            },
        ),
        MenuItem(
            label = "Scanline",
            type = MenuItemType.Toggle,
            getter = { scanlineEnabled },
            setter = { value ->
                scanlineEnabled = value
                if (scanlineEnabled) {
                    ensureScanlineEffects()
                    updateScanlineEffectSettings()
                } else {
                    deleteScanlineEffects()
                }
            },
        ),
        MenuItem(
            label = "Scanline Strength",
            type = MenuItemType.Slider,
            getter = { scanlineStrength },
            sliderSetter = { delta ->
                scanlineStrength = (scanlineStrength + delta).coerceIn(0f, 2f)
                updateScanlineEffectSettings()
            },
        ),
        MenuItem(
            label = "Bloom",
            type = MenuItemType.Toggle,
            getter = { bloomEnabled },
            setter = { value ->
                bloomEnabled = value
                if (bloomEnabled) {
                    ensureBloomEffects()
                    updateBloomEffectSettings()
                } else {
                    deleteBloomEffects()
                }
            },
        ),
        MenuItem(
            label = "Bloom Strength",
            type = MenuItemType.Slider,
            getter = { bloomStrength },
            sliderSetter = { delta ->
                bloomStrength = (bloomStrength + delta).coerceIn(0f, 2f)
                updateBloomEffectSettings()
            },
        ),
        MenuItem(
            label = "LIGHTING",
            type = MenuItemType.Header,
        ),
        MenuItem(
            label = "Lighting",
            type = MenuItemType.Toggle,
            getter = { lightingEnabled },
            setter = { value ->
                if (value || !isNonGameplayLightsOffPhase()) {
                    lightingEnabled = value
                    setLightingEnabledState(lightingEnabled)
                }
            },
        ),
        MenuItem(
            label = "Entity Halos",
            type = MenuItemType.Toggle,
            getter = { entityHaloEnabled },
            setter = { value -> entityHaloEnabled = value },
        ),
        MenuItem(
            label = "Board Backlight",
            type = MenuItemType.Toggle,
            getter = { boardBacklightEnabled },
            setter = { value -> boardBacklightEnabled = value },
        ),
        MenuItem(
            label = "Aura Lights",
            type = MenuItemType.Toggle,
            getter = { auraLightsEnabled },
            setter = { value -> auraLightsEnabled = value },
        ),
        MenuItem(
            label = "Brightness",
            type = MenuItemType.Cycle,
            getter = { sceneBrightness },
            cycleSetter = { cycleSceneBrightness() },
        ),
        MenuItem(
            label = "WALLS",
            type = MenuItemType.Header,
        ),
        MenuItem(
            label = "Wall Bevel",
            type = MenuItemType.Toggle,
            getter = { wallBevelEnabled },
            setter = { value -> wallBevelEnabled = value },
        ),
        MenuItem(
            label = "Wall Bevel Debug",
            type = MenuItemType.Toggle,
            getter = { wallBevelDebug },
            setter = { value -> wallBevelDebug = value },
        ),
        MenuItem(
            label = "Wall Outline",
            type = MenuItemType.Toggle,
            getter = { wallOutlineEnabled },
            setter = { value -> wallOutlineEnabled = value },
        ),
        MenuItem(
            label = "Wall Thin Outline",
            type = MenuItemType.Toggle,
            getter = { wallThinOutlineMode },
            setter = { value -> wallThinOutlineMode = value },
        ),
        MenuItem(
            label = "DEBUG",
            type = MenuItemType.Header,
        ),
        MenuItem(
            label = "Geometry Test",
            type = MenuItemType.Toggle,
            getter = { geometryTestOverlayEnabled },
            setter = { value -> geometryTestOverlayEnabled = value },
        ),
        MenuItem(
            label = "Light Target Main",
            type = MenuItemType.Toggle,
            getter = { lightingTargetMainEnabled },
            setter = { value ->
                lightingTargetMainEnabled = value
                lightingSystem?.targetSurfaces = if (lightingTargetMainEnabled) "main" else ""
            },
        ),
    )

    private fun handleServiceMenuInput() {
        if (engine.input.wasClicked(Key.UP)) {
            var nextIndex = serviceMenuCursorIndex - 1
            if (nextIndex < 0) nextIndex = menuItems.size - 1
            while (menuItems[nextIndex].type == MenuItemType.Header) {
                nextIndex--
                if (nextIndex < 0) nextIndex = menuItems.size - 1
            }
            serviceMenuCursorIndex = nextIndex
        }

        if (engine.input.wasClicked(Key.DOWN)) {
            var nextIndex = serviceMenuCursorIndex + 1
            if (nextIndex >= menuItems.size) nextIndex = 0
            while (menuItems[nextIndex].type == MenuItemType.Header) {
                nextIndex++
                if (nextIndex >= menuItems.size) nextIndex = 0
            }
            serviceMenuCursorIndex = nextIndex
        }

        val currentItem = menuItems[serviceMenuCursorIndex]

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

    private fun createMazeOccluders() {
        for (row in 0 until Maze.ROWS) {
            for (col in 0 until Maze.COLS) {
                if (!Maze.isWallForOutline(col, row)) continue
                engine.scene.addEntity(
                    MazeOccluder().apply {
                        x = Maze.centerX(col)
                        y = Maze.centerY(row)
                        width = Maze.TILE.toFloat()
                        height = Maze.TILE.toFloat()
                        castShadows = true
                    }
                )
            }
        }
    }

    override fun onFixedUpdate() {
        val dt = engine.data.fixedDeltaTime
        uiPulseTime += dt
        if (serviceMenuOpen) return

        when (phase) {
            GamePhase.BOOT -> {
                if (!bootTestHold) {
                    bootTimer -= dt
                    if (bootTimer <= 0f) {
                        phase = GamePhase.ATTRACT
                        attractTimer = 6f
                    }
                }
            }

            GamePhase.ATTRACT -> {
                attractTimer -= dt
                if (attractTimer <= 0f) {
                    phase = GamePhase.HI_SCORE
                    hiScoreTimer = 4f
                }
            }

            GamePhase.ATTRACT_DEMO -> {
                attractDemoTimer -= dt
                updateAttractPacmanControl()
                updatePacman(dt)
                updateGhosts(dt)
                updateGhostModes(dt)
                updateFruit(dt)
                checkCollisions()
                if (Maze.dotsRemaining() == 0) {
                    startLevelState(resetDots = true)
                }
                if (attractDemoTimer <= 0f) {
                    enterStartScreen()
                }
            }

            GamePhase.TITLE_POINTS -> {
                titlePointsTimer -= dt
                if (titlePointsTimer <= 0f) enterAttractMode()
            }

            GamePhase.HI_SCORE -> {
                hiScoreTimer -= dt
                if (hiScoreTimer <= 0f) startAttractDemo()
            }

            GamePhase.READY -> {
                readyTimer -= dt
                if (readyTimer <= 0f) phase = GamePhase.PLAYING
            }

            GamePhase.PLAYING -> {
                updatePacman(dt)
                updateGhosts(dt)
                updateGhostModes(dt)
                updateFruit(dt)
                checkCollisions()
                if (Maze.dotsRemaining() == 0) {
                    phase = GamePhase.WON
                    wonTimer = 1.5f
                }
            }

            GamePhase.DYING -> {
                deathAnimTimer -= dt
                if (deathAnimTimer <= 0f) {
                    lives--
                    if (lives <= 0) {
                        phase = GamePhase.GAME_OVER
                        gameOverTimer = 2.5f
                    } else {
                        resetPositions()
                        phase = GamePhase.READY
                        readyTimer = 1.5f
                    }
                }
            }

            GamePhase.WON -> {
                wonTimer -= dt
                if (wonTimer <= 0f) startNextLevelTransition()
            }

            GamePhase.LEVEL_TRANSITION -> {
                levelTransitionTimer -= dt
                if (levelTransitionTimer <= 0f) {
                    phase = GamePhase.PLAYING
                }
            }

            GamePhase.GAME_OVER -> {
                updateFruit(dt)
                gameOverTimer -= dt
                if (gameOverTimer <= 0f) enterStartScreen()
            }
        }

        updateMouthAnimation(dt)
        if (attractDemoGameOverTimer > 0f) {
            attractDemoGameOverTimer = (attractDemoGameOverTimer - dt).coerceAtLeast(0f)
        }
        if (isNonGameplayLightsOffPhase()) {
            setLightingEnabledState(false)
        }
        updateScorePopups(dt)
        updateParticles(dt)
        syncSceneLights()
    }

    override fun onRender() {
        val s = engine.gfx.mainSurface
        val uiSurface = engine.gfx.getSurfaceOrDefault(UI_SURFACE_NAME)
        val debugSurface = engine.gfx.getSurfaceOrDefault(DEBUG_SURFACE_NAME)
        val gameplayPhase = isGameplayVisualPhase()
        if (crtEnabled && !hasMainCrtEffect()) {
            ensureCRTEffects()
        } else if (!crtEnabled && hasMainCrtEffect()) {
            deleteCRTEffects()
        }
        if (scanlineEnabled && !hasMainScanlineEffect()) {
            ensureScanlineEffects()
        } else if (!scanlineEnabled && hasMainScanlineEffect()) {
            deleteScanlineEffects()
        }
        if (bloomEnabled && !hasMainBloomEffect()) {
            ensureBloomEffects()
        } else if (!bloomEnabled && hasMainBloomEffect()) {
            deleteBloomEffects()
        }
        updateCRTEffectSettings()
        updateScanlineEffectSettings()
        updateBloomEffectSettings()
        s.setBackgroundColor(0f, 0f, 0f, 1f)
        if (gameplayPhase) {
            uiSurface.setBackgroundColor(0f, 0f, 0f, 0f)
            renderMaze(s)
            if (entityHaloEnabled) renderEntityBloomHalos(s)
            renderFruit(s)
            if (phase != GamePhase.DYING) renderGhosts(s)
            renderPacman(s)
            renderParticles(s)
            renderScorePopups(s)
            if (geometryTestOverlayEnabled) renderGeometryTestOverlay(s)
            renderUI(uiSurface)
            if (debugLayerVisible) renderCrtDebugOverlay(debugSurface)
        } else {
            uiSurface.setBackgroundColor(0f, 0f, 0f, 0f)
            renderStartupScreen(s)
            if (debugLayerVisible) renderCrtDebugOverlay(debugSurface)
        }
        if (serviceMenuOpen) {
            renderServiceMenu(uiSurface)
        }
    }

    private fun isGameplayVisualPhase(): Boolean = phase in setOf(
        GamePhase.READY,
        GamePhase.PLAYING,
        GamePhase.ATTRACT_DEMO,
        GamePhase.DYING,
        GamePhase.LEVEL_TRANSITION,
        GamePhase.WON,
        GamePhase.GAME_OVER,
    )

    private fun isNonGameplayLightsOffPhase(): Boolean = phase in setOf(
        GamePhase.BOOT,
        GamePhase.ATTRACT,
        GamePhase.TITLE_POINTS,
        GamePhase.HI_SCORE,
    )

    private fun setLightingEnabledState(enabled: Boolean) {
        lightingEnabled = enabled
        lightingSystem?.enabled = enabled
    }

    private fun resetGame() {
        serviceMenuOpen = false
        bootTestHold = false
        Maze.reset()
        score = 0
        lives = 3
        level = 1
        particles.clear()
        highScore = max(highScore, score)
        startLevelState(resetDots = true)
        phase = GamePhase.BOOT
        bootTimer = bootDuration
        attractTimer = 6f
        titlePointsTimer = startScreenDelay
        hiScoreTimer = 4f
        attractDemoGameOverTimer = 0f
        gameOverTimer = 0f
        attractDemoTimer = 0f
        setLightingEnabledState(false)
    }

    private fun startNewGameFromStartup() {
        serviceMenuOpen = false
        bootTestHold = false
        Maze.reset()
        score = 0
        lives = 3
        level = 1
        particles.clear()
        highScore = max(highScore, score)
        startLevelState(resetDots = true)
        beginReadyPhase()
    }

    private fun enterAttractMode() {
        phase = GamePhase.ATTRACT
        attractTimer = 6f
        setLightingEnabledState(false)
    }

    private fun startAttractDemo() {
        Maze.reset()
        score = 0
        lives = 3
        level = 1
        particles.clear()
        attractDemoGameOverTimer = 0f
        setLightingEnabledState(true)
        startLevelState(resetDots = true)
        phase = GamePhase.ATTRACT_DEMO
        attractDemoTimer = attractDemoDuration
    }

    private fun enterStartScreen() {
        serviceMenuOpen = false
        phase = GamePhase.TITLE_POINTS
        titlePointsTimer = startScreenDelay
        setLightingEnabledState(false)
    }

    private fun beginReadyPhase() {
        setLightingEnabledState(true)
        phase = GamePhase.READY
        readyTimer = 2f
        tryPlaySound("pacman_beginning")
    }

    private fun startLevelState(resetDots: Boolean) {
        if (resetDots) Maze.reset()
        ghostModeIndex = 0
        currentGhostMode = modeSequence[0].first
        ghostModeTimer = modeSequence[0].second
        frightenedTimer = 0f
        pelletsEatenForGhostScore = 0
        dotsEatenThisLevel = 0
        fruitSpawn70Done = false
        fruitSpawn170Done = false
        activeFruit = null
        particles.clear()
        ghostReleaseTimers = floatArrayOf(0f, 3f, 6f, 9f).map { max(0f, it - (level - 1) * 0.25f) }.toFloatArray()
        resetPositions()
    }

    private fun startNextLevelTransition() {
        level++
        startLevelState(resetDots = true)
        phase = GamePhase.LEVEL_TRANSITION
        levelTransitionTimer = 1.5f
        tryPlaySound("pacman_intermission")
    }

    private fun resetPositions() {
        pacGridX = Maze.PAC_START_X
        pacGridY = Maze.PAC_START_Y
        pacDir = Direction.NONE
        pacNextDir = Direction.NONE
        pacProgress = 0f

        ghosts.clear()
        for (i in 0 until 4) {
            val start = Maze.GHOST_STARTS[i]
            ghosts.add(
                GhostState(
                    type = GhostType.entries[i],
                    gridX = start[0],
                    gridY = start[1],
                    direction = Direction.LEFT,
                    mode = GhostMode.SCATTER,
                    progress = 0f,
                    released = i == 0,
                    releaseTimer = ghostReleaseTimers[i],
                )
            )
        }
    }

    private fun updatePacman(dt: Float) {
        if (pacDir == Direction.NONE) {
            if (pacNextDir != Direction.NONE && Maze.isWalkable(pacGridX + pacNextDir.dx, pacGridY + pacNextDir.dy)) {
                pacDir = pacNextDir
            }
            return
        }

        pacProgress += pacSpeed * dt
        if (pacProgress < 1f) return

        val newCol = Maze.wrapCol(pacGridX + pacDir.dx)
        val newRow = pacGridY + pacDir.dy
        pacGridX = newCol
        pacGridY = newRow
        pacProgress = 0f

        eatDotAt(pacGridX, pacGridY)
        checkFruitCollision()

        val canTurn = pacNextDir != Direction.NONE &&
            pacNextDir != pacDir &&
            Maze.isWalkable(pacGridX + pacNextDir.dx, pacGridY + pacNextDir.dy)
        if (canTurn) {
            pacDir = pacNextDir
        } else if (!Maze.isWalkable(Maze.wrapCol(pacGridX + pacDir.dx), pacGridY + pacDir.dy)) {
            pacDir = Direction.NONE
        }
    }

    private fun updateAttractPacmanControl() {
        val choices = Maze.availableDirections(pacGridX, pacGridY)
        if (choices.isEmpty()) {
            pacNextDir = Direction.NONE
            return
        }

        val best = choices.minByOrNull { direction -> scoreAttractDirection(direction, choices.size) }
        if (best != null) {
            pacNextDir = best
        }
    }

    private fun scoreAttractDirection(direction: Direction, choiceCount: Int): Float {
        val nxRaw = pacGridX + direction.dx
        val ny = pacGridY + direction.dy
        if (ny !in 0 until Maze.ROWS) return Float.MAX_VALUE
        val nx = if (nxRaw < 0 || nxRaw >= Maze.COLS) Maze.wrapCol(nxRaw) else nxRaw

        var score = 0f
        when (Maze.grid[ny][nx]) {
            Maze.POWER -> score -= 220f
            Maze.DOT -> score -= 160f
            else -> score += 22f
        }

        score += nearestDotDistance(nx, ny) * 7f

        for (ghost in ghosts) {
            if (!ghost.released) continue
            val gx = ghost.gridX
            val gy = ghost.gridY
            val dxRaw = abs(nx - gx)
            val dx = min(dxRaw, Maze.COLS - dxRaw)
            val dist = dx + abs(ny - gy)

            when (ghost.mode) {
                GhostMode.EATEN -> score += 2f
                GhostMode.FRIGHTENED -> score -= (12f / (dist + 1f))
                else -> {
                    if (dist <= 1) score += 3000f
                    else if (dist == 2) score += 700f
                    else if (dist == 3) score += 200f
                    else score += 30f / dist
                }
            }
        }

        if (choiceCount > 1 && direction == pacDir.opposite()) {
            score += 18f
        }
        return score
    }

    private fun nearestDotDistance(startCol: Int, startRow: Int): Int {
        var nearest = Int.MAX_VALUE
        for (row in 0 until Maze.ROWS) {
            for (col in 0 until Maze.COLS) {
                val tile = Maze.grid[row][col]
                if (tile != Maze.DOT && tile != Maze.POWER) continue
                val dxRaw = abs(col - startCol)
                val dx = min(dxRaw, Maze.COLS - dxRaw)
                val distance = dx + abs(row - startRow)
                if (distance < nearest) nearest = distance
            }
        }
        return if (nearest == Int.MAX_VALUE) 0 else nearest
    }

    private fun eatDotAt(col: Int, row: Int) {
        if (col !in 0 until Maze.COLS || row !in 0 until Maze.ROWS) return
        when (Maze.grid[row][col]) {
            Maze.DOT -> {
                Maze.grid[row][col] = Maze.EMPTY
                dotsEatenThisLevel++
                addScore(10)
                maybeSpawnFruit()
                emitDotParticles(Maze.centerX(col), Maze.centerY(row))
                tryPlaySound("pacman_chomp")
            }

            Maze.POWER -> {
                Maze.grid[row][col] = Maze.EMPTY
                dotsEatenThisLevel++
                addScore(50)
                maybeSpawnFruit()
                emitPowerPelletParticles(Maze.centerX(col), Maze.centerY(row))
                activateFrightened()
                tryPlaySound("pacman_beginning")
            }
        }
    }

    private fun maybeSpawnFruit() {
        if (!fruitSpawn70Done && dotsEatenThisLevel >= 70) {
            spawnFruit()
            fruitSpawn70Done = true
            return
        }
        if (!fruitSpawn170Done && dotsEatenThisLevel >= 170) {
            spawnFruit()
            fruitSpawn170Done = true
        }
    }

    private fun spawnFruit() {
        if (activeFruit != null) return
        val fruitType = fruitTypeCycle[(level - 1).mod(fruitTypeCycle.size)]
        activeFruit = FruitState(
            type = fruitType,
            col = 14,
            row = 16,
            timer = 10f,
        )
    }

    private fun updateFruit(dt: Float) {
        val fruit = activeFruit ?: return
        fruit.timer -= dt
        if (fruit.timer <= 0f) {
            activeFruit = null
        }
    }

    private fun checkFruitCollision() {
        val fruit = activeFruit ?: return
        if (pacGridX == fruit.col && pacGridY == fruit.row) {
            addScore(fruit.type.score)
            emitFruitParticles(Maze.centerX(fruit.col), Maze.centerY(fruit.row), fruit.type)
            addScorePopup(Maze.centerX(fruit.col), Maze.centerY(fruit.row), fruit.type.score.toString())
            activeFruit = null
            tryPlaySound("pacman_eatfruit")
        }
    }

    private fun activateFrightened() {
        frightenedTimer = frightenedDurationForLevel()
        pelletsEatenForGhostScore = 0
        for (ghost in ghosts) {
            if (ghost.mode != GhostMode.EATEN && ghost.released) {
                ghost.mode = GhostMode.FRIGHTENED
                ghost.direction = ghost.direction.opposite()
                ghost.progress = 1f - ghost.progress
            }
        }
    }

    private fun updateGhostModes(dt: Float) {
        if (frightenedTimer > 0f) {
            frightenedTimer -= dt
            if (frightenedTimer <= 0f) {
                frightenedTimer = 0f
                for (ghost in ghosts) {
                    if (ghost.mode == GhostMode.FRIGHTENED) ghost.mode = currentGhostMode
                }
            }
            return
        }

        ghostModeTimer -= dt
        if (ghostModeTimer <= 0f && ghostModeIndex < modeSequence.size - 1) {
            ghostModeIndex++
            currentGhostMode = modeSequence[ghostModeIndex].first
            ghostModeTimer = modeSequence[ghostModeIndex].second
            for (ghost in ghosts) {
                if (ghost.mode != GhostMode.EATEN && ghost.mode != GhostMode.FRIGHTENED) {
                    ghost.direction = ghost.direction.opposite()
                    ghost.progress = 1f - ghost.progress
                    ghost.mode = currentGhostMode
                }
            }
        }
    }

    private fun updateGhosts(dt: Float) {
        for (ghost in ghosts) {
            if (!ghost.released) {
                ghost.releaseTimer -= dt
                if (ghost.releaseTimer <= 0f) {
                    ghost.released = true
                    ghost.gridX = 14
                    ghost.gridY = 10
                    ghost.progress = 0f
                    ghost.direction = Direction.LEFT
                    ghost.mode = currentGhostMode
                }
                continue
            }

            val speed = when (ghost.mode) {
                GhostMode.FRIGHTENED -> frightenedGhostSpeedForLevel()
                GhostMode.EATEN -> eatenGhostSpeed
                else -> ghostSpeedForLevel()
            }

            ghost.progress += speed * dt
            if (ghost.progress < 1f) continue

            val newCol = Maze.wrapCol(ghost.gridX + ghost.direction.dx)
            val newRow = ghost.gridY + ghost.direction.dy
            ghost.gridX = newCol
            ghost.gridY = newRow
            ghost.progress = 0f

            if (ghost.mode == GhostMode.EATEN && ghost.gridX in 11..16 && ghost.gridY in 12..14) {
                ghost.mode = currentGhostMode
                ghost.gridX = 14
                ghost.gridY = 14
            }

            chooseGhostDirection(ghost)
        }
    }

    private fun chooseGhostDirection(ghost: GhostState) {
        val canUseDoor = ghost.mode == GhostMode.EATEN || (ghost.gridY in 12..14 && ghost.gridX in 10..17)
        val available = Maze.ghostAvailableDirections(ghost.gridX, ghost.gridY, canUseDoor)
            .let { dirs -> if (ghost.mode == GhostMode.EATEN) dirs else dirs.filter { it != ghost.direction.opposite() } }

        if (available.isEmpty()) {
            ghost.direction = ghost.direction.opposite()
            return
        }

        if (ghost.mode == GhostMode.FRIGHTENED) {
            ghost.direction = available.random()
            return
        }

        val target = getGhostTarget(ghost)
        if (ghost.mode == GhostMode.EATEN) {
            val pathDir = nextGhostStepTowards(ghost.gridX, ghost.gridY, target[0], target[1], canUseDoor = true)
            if (pathDir != null) {
                ghost.direction = pathDir
                return
            }
        }

        ghost.direction = available.minByOrNull { dir ->
            val tx = ghost.gridX + dir.dx
            val ty = ghost.gridY + dir.dy
            distSq(tx, ty, target[0], target[1])
        } ?: available.first()
    }

    private fun nextGhostStepTowards(startX: Int, startY: Int, targetX: Int, targetY: Int, canUseDoor: Boolean): Direction? {
        if (startX == targetX && startY == targetY) return null

        val visited = Array(Maze.ROWS) { BooleanArray(Maze.COLS) }
        val firstDir = Array(Maze.ROWS) { arrayOfNulls<Direction>(Maze.COLS) }
        val queue = ArrayDeque<Pair<Int, Int>>()

        visited[startY][startX] = true
        queue.add(startX to startY)

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            for (dir in Direction.entries) {
                if (dir == Direction.NONE) continue

                var nx = cx + dir.dx
                val ny = cy + dir.dy
                if (!Maze.isGhostWalkable(nx, ny, canUseDoor)) continue
                if (nx < 0 || nx >= Maze.COLS) nx = Maze.wrapCol(nx)
                if (ny !in 0 until Maze.ROWS || visited[ny][nx]) continue

                val first = if (cx == startX && cy == startY) dir else firstDir[cy][cx] ?: dir
                firstDir[ny][nx] = first
                visited[ny][nx] = true

                if (nx == targetX && ny == targetY) return first
                queue.add(nx to ny)
            }
        }

        return null
    }

    private fun getGhostTarget(ghost: GhostState): IntArray {
        if (ghost.mode == GhostMode.SCATTER) return Maze.SCATTER_TARGETS[ghost.type.ordinal]
        if (ghost.mode == GhostMode.EATEN) return intArrayOf(14, 13)

        return when (ghost.type) {
            GhostType.BLINKY -> intArrayOf(pacGridX, pacGridY)
            GhostType.PINKY -> intArrayOf(pacGridX + pacDir.dx * 4, pacGridY + pacDir.dy * 4)
            GhostType.INKY -> {
                val blinky = ghosts.firstOrNull { it.type == GhostType.BLINKY }
                if (blinky != null) {
                    val ax = pacGridX + pacDir.dx * 2
                    val ay = pacGridY + pacDir.dy * 2
                    intArrayOf(ax + (ax - blinky.gridX), ay + (ay - blinky.gridY))
                } else intArrayOf(pacGridX, pacGridY)
            }

            GhostType.CLYDE -> {
                val dist = distSq(ghost.gridX, ghost.gridY, pacGridX, pacGridY)
                if (dist > 64) intArrayOf(pacGridX, pacGridY) else Maze.SCATTER_TARGETS[ghost.type.ordinal]
            }
        }
    }

    private fun distSq(x1: Int, y1: Int, x2: Int, y2: Int): Float {
        val dx = (x1 - x2).toFloat()
        val dy = (y1 - y2).toFloat()
        return dx * dx + dy * dy
    }

    private fun checkCollisions() {
        for (ghost in ghosts) {
            if (!ghost.released) continue
            val sameCell = ghost.gridX == pacGridX && ghost.gridY == pacGridY
            val adjacent = abs(ghost.gridX - pacGridX) + abs(ghost.gridY - pacGridY) <= 1 && ghost.progress > 0.5f

            if (sameCell || (adjacent && pacProgress > 0.5f && ghost.direction == pacDir.opposite())) {
                when (ghost.mode) {
                    GhostMode.FRIGHTENED -> {
                        ghost.mode = GhostMode.EATEN
                        pelletsEatenForGhostScore++
                        val ghostScore = 200 * (1 shl (pelletsEatenForGhostScore - 1).coerceAtMost(3))
                        addScore(ghostScore)
                        emitGhostEatenParticles(ghostPixelX(ghost), ghostPixelY(ghost))
                        addScorePopup(ghostPixelX(ghost), ghostPixelY(ghost) - 8f, ghostScore.toString())
                        tryPlaySound("pacman_eatghost")
                    }

                    GhostMode.EATEN -> {}
                    else -> {
                        emitDeathParticles(pacPixelX(), pacPixelY())
                        tryPlaySound("pacman_death")
                        if (phase == GamePhase.ATTRACT_DEMO) {
                            attractDemoGameOverTimer = 1.25f
                            resetPositions()
                        } else {
                            phase = GamePhase.DYING
                            deathAnimTimer = 1.5f
                        }
                        return
                    }
                }
            }
        }
    }

    private fun loadGameSounds() {
        listOf(
            "pacman_beginning",
            "pacman_chomp",
            "pacman_death",
            "pacman_eatfruit",
            "pacman_eatghost",
            "pacman_extrapac",
            "pacman_intermission",
        ).forEach(::loadSoundAsset)
    }

    private fun loadSoundAsset(name: String) {
        val filename = "$name.ogg"
        val path = resolveSoundPath(filename) ?: return
        engine.asset.load(Sound(path, name))
    }

    private fun resolveSoundPath(filename: String): String? {
        listOf(
            "src/main/resources/$filename",
            "sounds/$filename",
            filename,
        ).map(::File)
            .firstOrNull { it.isFile }
            ?.absolutePath
            ?.let { return it }

        val resource = javaClass.classLoader.getResource(filename) ?: return null
        return if (resource.protocol == "file") {
            File(resource.toURI()).absolutePath
        } else {
            val tempFile = File.createTempFile("pulsdniel-sound-", "-$filename")
            tempFile.deleteOnExit()
            resource.openStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.absolutePath
        }
    }

    private fun updateMouthAnimation(dt: Float) {
        val speed = 12.0f * gameSpeedScale
        if (mouthOpening) {
            mouthAngle += speed * dt
            if (mouthAngle >= 1f) {
                mouthAngle = 1f
                mouthOpening = false
            }
        } else {
            mouthAngle -= speed * dt
            if (mouthAngle <= 0.15f) {
                mouthAngle = 0.15f
                mouthOpening = true
            }
        }
    }

    private fun updateScorePopups(dt: Float) {
        val iterator = scorePopups.iterator()
        while (iterator.hasNext()) {
            val popup = iterator.next()
            popup.timer -= dt
            popup.y -= dt * 28f
            if (popup.timer <= 0f) iterator.remove()
        }
    }

    private fun updateParticles(dt: Float) {
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

    private fun emitDotParticles(x: Float, y: Float) {
        emitBurst(x, y, count = 14, speedMin = 26f, speedMax = 88f, lifeMin = 0.24f, lifeMax = 0.58f, sizeMin = 1.6f, sizeMax = 3.6f, red = 1f, green = 0.94f, blue = 0.68f)
    }

    private fun emitPowerPelletParticles(x: Float, y: Float) {
        emitBurst(x, y, count = 18, speedMin = 30f, speedMax = 92f, lifeMin = 0.32f, lifeMax = 0.62f, sizeMin = 1.8f, sizeMax = 4.2f, red = 1f, green = 0.98f, blue = 0.7f)
    }

    private fun emitGhostEatenParticles(x: Float, y: Float) {
        emitBurst(x, y, count = 22, speedMin = 48f, speedMax = 130f, lifeMin = 0.35f, lifeMax = 0.85f, sizeMin = 2f, sizeMax = 4.8f, red = 1f, green = 1f, blue = 1f)
    }

    private fun emitDeathParticles(x: Float, y: Float) {
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

    private fun emitFruitParticles(x: Float, y: Float, type: FruitType) {
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

    private fun pacPixelX(): Float = Maze.centerX(pacGridX) + pacDir.dx * pacProgress * Maze.TILE
    private fun pacPixelY(): Float = Maze.centerY(pacGridY) + pacDir.dy * pacProgress * Maze.TILE
    private fun ghostPixelX(g: GhostState): Float = Maze.centerX(g.gridX) + g.direction.dx * g.progress * Maze.TILE
    private fun ghostPixelY(g: GhostState): Float = Maze.centerY(g.gridY) + g.direction.dy * g.progress * Maze.TILE

    private fun ghostSpeedForLevel(): Float = (6f + (level - 1) * 0.3f).coerceAtMost(9f) * gameSpeedScale
    private fun frightenedDurationForLevel(): Float = max(3f, 8f - (level - 1) * 0.5f)
    private fun frightenedGhostSpeedForLevel(): Float = ghostSpeedForLevel() * 0.58f

    private fun addScore(amount: Int) {
        score += amount
        if (score > highScore) highScore = score
    }

    private fun addScorePopup(x: Float, y: Float, text: String) {
        scorePopups += ScorePopup(x = x, y = y, text = text, timer = 1f)
    }

    private fun tryPlaySound(name: String) {
        engine.audio.playSound(name)
    }

    private fun renderMaze(s: Surface) {
        val wallColor = floatArrayOf(0.62f, 0.88f, 1f)
        val thickness = if (wallThinOutlineMode) 1f else 3f
        val wallBase = when {
            !wallBevelEnabled -> wallColor
            wallBevelDebug -> floatArrayOf(0.1f, 0.2f, 0.4f)
            else -> floatArrayOf(0.14f, 0.26f, 0.5f)
        }
        val pelletPulse = 0.5f + 0.5f * sin(uiPulseTime * 4.2f)

        val verticalOpen = Array(Maze.ROWS) { BooleanArray(Maze.COLS + 1) }
        val horizontalOpen = Array(Maze.ROWS + 1) { BooleanArray(Maze.COLS) }
        for (row in 0 until Maze.ROWS) {
            for (col in 0 until Maze.COLS) {
                if (!Maze.isWallForOutline(col, row)) continue
                if (!Maze.isWallForOutline(col - 1, row)) verticalOpen[row][col] = true
                if (!Maze.isWallForOutline(col + 1, row)) verticalOpen[row][col + 1] = true
                if (!Maze.isWallForOutline(col, row - 1)) horizontalOpen[row][col] = true
                if (!Maze.isWallForOutline(col, row + 1)) horizontalOpen[row + 1][col] = true
            }
        }

        for (row in 0 until Maze.ROWS) {
            for (col in 0 until Maze.COLS) {
                val x = Maze.tileX(col)
                val y = Maze.tileY(row)
                val tile = Maze.grid[row][col]

                if (Maze.isWalkable(col, row)) {
                    s.setDrawColor(0f, 0f, 0f, 1f)
                    s.drawQuad(x, y, Maze.TILE.toFloat(), Maze.TILE.toFloat())
                }

                when (tile) {
                    Maze.WALL -> {
                        val tileSize = Maze.TILE.toFloat()
                        s.setDrawColor(wallBase[0], wallBase[1], wallBase[2], 1f)
                        s.drawQuad(x, y, tileSize, tileSize)
                    }

                    Maze.DOT -> {
                        s.setDrawColor(1f, 0.95f, 0.78f, 0.18f + pelletPulse * 0.16f)
                        drawFilledCircle(s, Maze.centerX(col), Maze.centerY(row), 4.8f, 12)
                        s.setDrawColor(1f, 0.93f, 0.75f, 1f)
                        drawFilledCircle(s, Maze.centerX(col), Maze.centerY(row), 2.1f, 8)
                    }

                    Maze.POWER -> {
                        val cx = Maze.centerX(col)
                        val cy = Maze.centerY(row)
                        s.setDrawColor(1f, 0.98f, 0.86f, 0.28f + pelletPulse * 0.22f)
                        drawFilledCircle(s, cx, cy, 8.8f, 14)
                        s.setDrawColor(1f, 1f, 1f, 1f)
                        drawFilledCircle(s, cx, cy, 5f, 14)
                    }

                    Maze.GHOST_DOOR -> {
                        s.setDrawColor(1f, 0.7f, 0.8f, 1f)
                        s.drawQuad(x, y + Maze.TILE / 2f - 2f, Maze.TILE.toFloat(), 4f)
                    }
                }
            }
        }

        if (wallOutlineEnabled) {
            s.setDrawColor(wallColor[0], wallColor[1], wallColor[2], 1f)
            for (row in 0 until Maze.ROWS) {
                for (xEdge in 0..Maze.COLS) {
                    if (!verticalOpen[row][xEdge]) continue
                    val x0 = if (xEdge == 0) Maze.tileX(0) else Maze.tileX(xEdge) - thickness
                    s.drawQuad(x0, Maze.tileY(row), thickness, Maze.TILE.toFloat())
                }
            }

            for (rowEdge in 0..Maze.ROWS) {
                for (col in 0 until Maze.COLS) {
                    if (!horizontalOpen[rowEdge][col]) continue
                    val y0 = if (rowEdge == 0) Maze.tileY(0) else Maze.tileY(rowEdge) - thickness
                    s.drawQuad(Maze.tileX(col), y0, Maze.TILE.toFloat(), thickness)
                }
            }

            if (!wallThinOutlineMode) {
                for (rowEdge in 0..Maze.ROWS) {
                    for (xEdge in 0..Maze.COLS) {
                        val hasVertical = (rowEdge > 0 && verticalOpen[rowEdge - 1][xEdge]) || (rowEdge < Maze.ROWS && verticalOpen[rowEdge][xEdge])
                        val hasHorizontal = (xEdge > 0 && horizontalOpen[rowEdge][xEdge - 1]) || (xEdge < Maze.COLS && horizontalOpen[rowEdge][xEdge])
                        if (!hasVertical || !hasHorizontal) continue

                        val px = if (xEdge == 0) Maze.tileX(0) else Maze.tileX(xEdge) - thickness
                        val py = if (rowEdge == 0) Maze.tileY(0) else Maze.tileY(rowEdge) - thickness
                        s.drawQuad(px, py, thickness, thickness)
                    }
                }
            }
        }
    }

    private fun renderPacman(s: Surface) {
        val px = pacPixelX()
        val py = pacPixelY()
        val radius = (Maze.TILE - 4f) * 0.5f

        if (phase == GamePhase.DYING) {
            val life = (deathAnimTimer / 1.5f).coerceIn(0f, 1f)
            val gone = 1f - life
            val shrink = (1f - gone).coerceAtLeast(0f)
            val dyingMouth = (0.35f + gone * 0.65f).coerceAtMost(1f)
            s.setDrawColor(1f, 0.95f, 0f, 1f)
            drawFilledCircle(s, px, py, radius * shrink, 20)
            drawPacmanMouthCutout(s, px, py, radius * shrink, if (pacDir == Direction.NONE) Direction.RIGHT else pacDir, dyingMouth)
            return
        }

        s.setDrawColor(1f, 0.95f, 0f, 1f)
        drawFilledCircle(s, px, py, radius, 20)
        if (pacDir != Direction.NONE) {
            drawPacmanMouthCutout(s, px, py, radius, pacDir, mouthAngle)
        }
    }

    private fun renderGeometryTestOverlay(s: Surface) {
        val left = Maze.tileX(0)
        val top = Maze.tileY(0)
        val width = Maze.COLS * Maze.TILE.toFloat()
        val height = Maze.ROWS * Maze.TILE.toFloat()
        val right = left + width
        val bottom = top + height
        val centerX = left + width * 0.5f
        val centerY = top + height * 0.5f

        s.setDrawColor(1f, 0.35f, 0.35f, 0.9f)
        s.drawLine(left, top, right, top)
        s.drawLine(left, bottom, right, bottom)
        s.drawLine(left, top, left, bottom)
        s.drawLine(right, top, right, bottom)

        s.setDrawColor(0.35f, 1f, 0.4f, 0.9f)
        s.drawLine(centerX, top, centerX, bottom)
        s.drawLine(left, centerY, right, centerY)

        s.setDrawColor(0.35f, 0.75f, 1f, 0.85f)
        val sx0 = 20f
        val sy0 = 20f
        val sx1 = engine.window.width - 20f
        val sy1 = engine.window.height - 20f
        s.drawLine(sx0, sy0, sx1, sy0)
        s.drawLine(sx0, sy1, sx1, sy1)
        s.drawLine(sx0, sy0, sx0, sy1)
        s.drawLine(sx1, sy0, sx1, sy1)
    }

    private fun renderEntityBloomHalos(s: Surface) {
        val pulse = 0.5f + 0.5f * sin(uiPulseTime * 4.3f)
        val pacX = pacPixelX()
        val pacY = pacPixelY()
        s.setDrawColor(1f, 0.95f, 0.24f, 0.16f + pulse * 0.12f)
        drawFilledCircle(s, pacX, pacY, 16f + pulse * 3f, 18)

        activeFruit?.let {
            val fx = Maze.centerX(it.col)
            val fy = Maze.centerY(it.row)
            s.setDrawColor(1f, 0.55f, 0.2f, 0.16f + pulse * 0.12f)
            drawFilledCircle(s, fx, fy, 13f + pulse * 2.5f, 16)
        }

        for (ghost in ghosts) {
            if (ghost.mode == GhostMode.EATEN) continue
            val gx = if (!ghost.released) Maze.centerX(ghost.gridX) else ghostPixelX(ghost)
            val gy = if (!ghost.released) Maze.centerY(ghost.gridY) else ghostPixelY(ghost)
            val c = when {
                ghost.mode == GhostMode.FRIGHTENED -> floatArrayOf(0.45f, 0.58f, 1f)
                ghost.type == GhostType.BLINKY -> floatArrayOf(1f, 0.24f, 0.24f)
                ghost.type == GhostType.PINKY -> floatArrayOf(1f, 0.7f, 0.86f)
                ghost.type == GhostType.INKY -> floatArrayOf(0.24f, 1f, 1f)
                else -> floatArrayOf(1f, 0.72f, 0.25f)
            }
            s.setDrawColor(c[0], c[1], c[2], 0.14f + pulse * 0.1f)
            drawFilledCircle(s, gx, gy, 14f + pulse * 2f, 16)
        }
    }

    private fun renderGhosts(s: Surface) {
        for (ghost in ghosts) {
            val gx = if (!ghost.released && ghost.mode != GhostMode.EATEN) Maze.centerX(ghost.gridX) else ghostPixelX(ghost)
            val gy = if (!ghost.released && ghost.mode != GhostMode.EATEN) Maze.centerY(ghost.gridY) else ghostPixelY(ghost)
            val size = Maze.TILE - 4f

            if (ghost.mode == GhostMode.EATEN) {
                drawGhostEyes(s, gx, gy - 1f, ghost.direction, eyeScale = 1.15f)
                continue
            }

            setGhostColor(s, ghost)
            drawGhostBody(s, gx, gy, size)

            if (ghost.mode == GhostMode.FRIGHTENED) {
                drawFrightenedFace(s, gx, gy)
            } else {
                drawGhostEyes(s, gx, gy - 1f, ghost.direction, eyeScale = 1f)
            }
        }
    }

    private fun setGhostColor(s: Surface, ghost: GhostState) {
        if (ghost.mode == GhostMode.FRIGHTENED) {
            val flash = frightenedTimer < 2f && ((frightenedTimer * 6f).toInt() % 2 == 0)
            if (flash) s.setDrawColor(1f, 1f, 1f, 1f)
            else s.setDrawColor(0.08f, 0.2f, 0.82f, 1f)
            return
        }

        when (ghost.type) {
            GhostType.BLINKY -> s.setDrawColor(0.96f, 0.12f, 0.12f, 1f)
            GhostType.PINKY -> s.setDrawColor(1f, 0.7f, 0.83f, 1f)
            GhostType.INKY -> s.setDrawColor(0.1f, 0.92f, 0.95f, 1f)
            GhostType.CLYDE -> s.setDrawColor(1f, 0.62f, 0.12f, 1f)
        }
    }

    private fun drawGhostBody(s: Surface, cx: Float, cy: Float, size: Float) {
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
        drawFilledCircle(s, cx, baseY + 0.5f, bumpRadius, 8)
        drawFilledCircle(s, cx + size * 0.3f, baseY, bumpRadius, 8)
    }

    private fun drawGhostEyes(s: Surface, cx: Float, cy: Float, dir: Direction, eyeScale: Float) {
        val eyeOffset = 4.4f * eyeScale
        val eyeRadius = 2.8f * eyeScale
        val pupilRadius = 1.45f * eyeScale
        val pdx = dir.dx * 1.4f
        val pdy = dir.dy * 1.2f

        s.setDrawColor(1f, 1f, 1f, 1f)
        drawFilledCircle(s, cx - eyeOffset, cy - 2f, eyeRadius, 10)
        drawFilledCircle(s, cx + eyeOffset, cy - 2f, eyeRadius, 10)

        s.setDrawColor(0.07f, 0.07f, 0.35f, 1f)
        drawFilledCircle(s, cx - eyeOffset + pdx, cy - 2f + pdy, pupilRadius, 8)
        drawFilledCircle(s, cx + eyeOffset + pdx, cy - 2f + pdy, pupilRadius, 8)
    }

    private fun drawFrightenedFace(s: Surface, cx: Float, cy: Float) {
        s.setDrawColor(1f, 1f, 1f, 1f)
        drawFilledCircle(s, cx - 4f, cy - 2f, 1.8f, 6)
        drawFilledCircle(s, cx + 4f, cy - 2f, 1.8f, 6)
        val y = cy + 4f
        s.drawLine(cx - 6f, y, cx - 3f, y - 1f)
        s.drawLine(cx - 3f, y - 1f, cx, y)
        s.drawLine(cx, y, cx + 3f, y - 1f)
        s.drawLine(cx + 3f, y - 1f, cx + 6f, y)
    }

    private fun renderFruit(s: Surface) {
        val fruit = activeFruit ?: return
        val cx = Maze.centerX(fruit.col)
        val cy = Maze.centerY(fruit.row)

        when (fruit.type) {
            FruitType.CHERRY -> {
                s.setDrawColor(0.95f, 0.12f, 0.15f, 1f)
                drawFilledCircle(s, cx - 2.3f, cy + 1f, 4f, 10)
                drawFilledCircle(s, cx + 2.3f, cy - 0.3f, 4f, 10)
                s.setDrawColor(0.2f, 0.9f, 0.3f, 1f)
                s.drawQuad(cx - 0.8f, cy - 7f, 1.6f, 4.5f)
            }

            FruitType.STRAWBERRY -> {
                s.setDrawColor(0.95f, 0.15f, 0.16f, 1f)
                s.drawQuad(cx - 5f, cy - 2f, 10f, 8f)
                s.setDrawColor(0.1f, 0.8f, 0.22f, 1f)
                s.drawQuad(cx - 4f, cy - 6f, 8f, 3f)
            }

            FruitType.ORANGE -> {
                s.setDrawColor(1f, 0.55f, 0.1f, 1f)
                drawFilledCircle(s, cx, cy, 5f, 12)
            }

            FruitType.APPLE -> {
                s.setDrawColor(0.9f, 0.12f, 0.16f, 1f)
                drawFilledCircle(s, cx, cy, 5f, 12)
                s.setDrawColor(0.22f, 0.95f, 0.3f, 1f)
                drawFilledCircle(s, cx + 2f, cy - 5.5f, 1.6f, 6)
            }

            FruitType.MELON -> {
                s.setDrawColor(0.28f, 0.86f, 0.45f, 1f)
                drawFilledCircle(s, cx, cy, 5f, 12)
            }

            FruitType.GALAXIAN -> {
                s.setDrawColor(0.95f, 0.95f, 0.22f, 1f)
                drawFilledCircle(s, cx, cy, 5f, 12)
            }

            FruitType.BELL -> {
                s.setDrawColor(1f, 0.85f, 0.2f, 1f)
                drawFilledCircle(s, cx, cy, 5f, 12)
            }

            FruitType.KEY -> {
                s.setDrawColor(0.98f, 0.92f, 0.45f, 1f)
                drawFilledCircle(s, cx, cy, 5f, 12)
            }
        }
    }

    private fun renderScorePopups(s: Surface) {
        for (popup in scorePopups) {
            val alpha = popup.timer.coerceIn(0f, 1f)
            s.setDrawColor(1f, 1f, 1f, alpha)
            s.drawText(popup.text, popup.x, popup.y, fontSize = 18f, xOrigin = 0.5f, yOrigin = 0.5f)
        }
    }

    private fun renderParticles(s: Surface) {
        if (particles.isEmpty()) return

        for (p in particles) {
            val life = (p.life / p.maxLife).coerceIn(0f, 1f)
            val size = p.size * (0.72f + life * 0.45f)
            s.setDrawColor(p.red, p.green, p.blue, life)
            s.drawQuad(p.x - size * 0.5f, p.y - size * 0.5f, size, size)
        }
    }

    private fun syncSceneLights() {
        val pulse = 0.5f + 0.5f * sin(uiPulseTime * 3.8f)
        val spin = (uiPulseTime * 220f) % 360f
        val playfieldLightsEnabled = phase in setOf(
            GamePhase.PLAYING,
            GamePhase.ATTRACT_DEMO,
        )

        if (!playfieldLightsEnabled) {
            boardBacklight?.intensity = 0f
            pacAuraLight?.intensity = 0f
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

        boardBacklight?.intensity = if (boardBacklightEnabled) 0.5f + pulse * 0.1f else 0f

        pacAuraLight?.apply {
            x = pacPixelX()
            y = pacPixelY()
            intensity = if (auraLightsEnabled) 0.58f + pulse * 0.32f else 0f
        }

        fruitAuraLight?.apply {
            val fruit = activeFruit
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

        for (ghost in ghosts) {
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
                    light.intensity = if (auraLightsEnabled) 0.38f + pulse * 0.24f else 0f
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

    private fun renderUI(s: Surface) {
        val scoreText = score.toString().padStart(6, '0')
        val highScoreText = highScore.toString().padStart(6, '0')

        s.setDrawColor(0.95f, 0.95f, 0.95f, 1f)
        s.drawText("1UP", 20f, 15f, fontSize = 20f)
        s.drawText(scoreText, 20f, 36f, fontSize = 24f)

        s.drawText("HIGH SCORE", engine.window.width / 2f, 15f, fontSize = 20f, xOrigin = 0.5f)
        s.drawText(highScoreText, engine.window.width / 2f, 36f, fontSize = 24f, xOrigin = 0.5f)

        s.drawText("LEVEL ${level.toString().padStart(2, '0')}", 640f, 15f, fontSize = 20f)
        s.drawText("LIVES", 640f, 36f, fontSize = 16f)
        for (i in 0 until lives) {
            drawLifeIcon(s, 702f + i * 24f, 45f, 8f)
        }

        val pulseAlpha = (0.65f + 0.35f * (0.5f + 0.5f * sin(uiPulseTime * 4f))).coerceIn(0.45f, 1f)
        when (phase) {
            GamePhase.READY -> {
                s.setDrawColor(1f, 1f, 0f, pulseAlpha)
                s.drawText(
                    "READY!",
                    engine.window.width / 2f,
                    Maze.centerY(17),
                    fontSize = 28f,
                    xOrigin = 0.5f,
                    yOrigin = 0.5f,
                )
            }

            GamePhase.GAME_OVER -> {
                s.setDrawColor(1f, 0f, 0f, pulseAlpha)
                s.drawText(
                    "GAME OVER",
                    engine.window.width / 2f,
                    Maze.centerY(17),
                    fontSize = 36f,
                    xOrigin = 0.5f,
                    yOrigin = 0.5f,
                )
                s.setDrawColor(1f, 1f, 1f, 0.7f)
                s.drawText(
                    "Press ENTER to restart",
                    engine.window.width / 2f,
                    Maze.centerY(17) + 40f,
                    fontSize = 18f,
                    xOrigin = 0.5f,
                    yOrigin = 0.5f,
                )
            }

            GamePhase.WON -> {
                s.setDrawColor(0f, 1f, 0f, pulseAlpha)
                s.drawText(
                    "YOU WIN!",
                    engine.window.width / 2f,
                    Maze.centerY(17),
                    fontSize = 36f,
                    xOrigin = 0.5f,
                    yOrigin = 0.5f,
                )
            }

            GamePhase.LEVEL_TRANSITION -> {
                s.setDrawColor(0.95f, 0.95f, 0.2f, pulseAlpha)
                s.drawText(
                    "LEVEL $level",
                    engine.window.width / 2f,
                    Maze.centerY(17),
                    fontSize = 34f,
                    xOrigin = 0.5f,
                    yOrigin = 0.5f,
                )
            }

            GamePhase.BOOT, GamePhase.ATTRACT, GamePhase.TITLE_POINTS, GamePhase.HI_SCORE -> return

            GamePhase.ATTRACT_DEMO -> {
                if (attractDemoGameOverTimer > 0f) {
                    s.setDrawColor(1f, 0.2f, 0.2f, pulseAlpha)
                    s.drawText(
                        "GAME OVER",
                        engine.window.width / 2f,
                        Maze.centerY(17),
                        fontSize = 34f,
                        xOrigin = 0.5f,
                        yOrigin = 0.5f,
                    )
                    s.setDrawColor(1f, 0.96f, 0.74f, 0.9f)
                    s.drawText(
                        "DEMO PLAY",
                        engine.window.width / 2f,
                        Maze.centerY(17) + 34f,
                        fontSize = 16f,
                        xOrigin = 0.5f,
                        yOrigin = 0.5f,
                    )
                }
            }

            else -> {}
        }

        s.setDrawColor(0.55f, 0.55f, 0.55f, 1f)
        s.drawText(
            "S: Service Menu",
            engine.window.width / 2f,
            engine.window.height - 15f,
            fontSize = 14f,
            xOrigin = 0.5f,
            yOrigin = 0.5f,
        )
    }

    private fun renderServiceMenu(s: Surface) {
        s.setDrawColor(0f, 0f, 0f, 0.85f)
        s.drawQuad(0f, 0f, engine.window.width.toFloat(), engine.window.height.toFloat())

        val centerX = engine.window.width / 2f
        val titleY = 60f

        s.setDrawColor(0f, 1f, 1f, 1f)
        s.drawText("SERVICE MENU", centerX, titleY, fontSize = 48f, xOrigin = 0.5f, yOrigin = 0.5f)

        val startY = 140f
        val lineHeight = 28f
        var yOffset = startY

        for (i in menuItems.indices) {
            val item = menuItems[i]
            val isSelected = i == serviceMenuCursorIndex

            when (item.type) {
                MenuItemType.Header -> {
                    s.setDrawColor(1f, 0.8f, 0f, 1f)
                    s.drawText(item.label, centerX - 200f, yOffset, fontSize = 24f)
                }
                MenuItemType.Toggle -> {
                    val value = item.getter?.invoke() as? Boolean ?: false
                    val valueText = if (value) "ON" else "OFF"
                    val prefix = if (isSelected) "> " else "  "

                    s.setDrawColor(0.9f, 0.9f, 0.9f, 1f)
                    s.drawText("$prefix${item.label}", centerX - 200f, yOffset, fontSize = 20f)

                    s.setDrawColor(if (value) 0f else 0.5f, if (value) 1f else 0.5f, 0f, 1f)
                    s.drawText(valueText, centerX + 200f, yOffset, fontSize = 20f)
                }
                MenuItemType.Slider -> {
                    val value = item.getter?.invoke() as? Float ?: 0f
                    val valueText = "%.1f".format(value)
                    val prefix = if (isSelected) "> " else "  "

                    s.setDrawColor(0.9f, 0.9f, 0.9f, 1f)
                    s.drawText("$prefix${item.label}", centerX - 200f, yOffset, fontSize = 20f)

                    s.setDrawColor(0f, 0.8f, 1f, 1f)
                    s.drawText(valueText, centerX + 200f, yOffset, fontSize = 20f)
                }
                MenuItemType.Cycle -> {
                    val value = item.getter?.invoke()
                    val valueText = when (value) {
                        is SceneBrightness -> value.name
                        else -> value.toString()
                    }
                    val prefix = if (isSelected) "> " else "  "

                    s.setDrawColor(0.9f, 0.9f, 0.9f, 1f)
                    s.drawText("$prefix${item.label}", centerX - 200f, yOffset, fontSize = 20f)

                    s.setDrawColor(1f, 0.7f, 0f, 1f)
                    s.drawText(valueText, centerX + 200f, yOffset, fontSize = 20f)
                }
            }

            yOffset += lineHeight
        }

        s.setDrawColor(0.7f, 0.7f, 0.7f, 1f)
        s.drawText(
            "UP/DOWN: Navigate   SPACE: Toggle   LEFT/RIGHT: Adjust   S: Close",
            centerX,
            engine.window.height - 40f,
            fontSize = 18f,
            xOrigin = 0.5f,
            yOrigin = 0.5f,
        )
    }

    private fun renderStartupScreen(s: Surface) {
        when (phase) {
            GamePhase.BOOT -> renderBootScreen(s)
            GamePhase.ATTRACT -> renderAttractScreen(s)
            GamePhase.TITLE_POINTS -> renderTitlePointsScreen(s)
            GamePhase.HI_SCORE -> renderHiScoreScreen(s)
            else -> {}
        }
    }

    private fun renderCrtDebugOverlay(s: Surface) {
        val crtMain = if (hasMainCrtEffect()) "Y" else "N"
        val scanMain = if (hasMainScanlineEffect()) "Y" else "N"
        val bloomMain = if (hasMainBloomEffect()) "Y" else "N"
        val state = if (crtEnabled) "ON" else "OFF"
        s.setDrawColor(0.72f, 0.92f, 1f, 0.9f)
        s.drawText("CRT DBG state=$state main=$crtMain", 20f, engine.window.height - 142f, fontSize = 14f)
        s.drawText("PP DBG scan=$scanMain bloom=$bloomMain", 20f, engine.window.height - 160f, fontSize = 14f)
    }

    private fun renderBootScreen(s: Surface) {
        val centerX = engine.window.width / 2f
        val centerY = engine.window.height / 2f
        val elapsed = (bootDuration - bootTimer).coerceIn(0f, bootDuration)
        val pct = ((elapsed / bootDuration) * 100f).toInt().coerceIn(0, 100)

        if (elapsed >= 2.35f && elapsed < 3.45f) {
            renderBootVideoTestScreen(s, elapsed)
            return
        }

        drawGlowText(s, "PULSE ENGINE SYSTEM I", centerX, centerY - 120f, fontSize = 26f, red = 0.72f, green = 0.9f, blue = 1f, xOrigin = 0.5f, yOrigin = 0.5f)
        s.setDrawColor(0.72f, 0.9f, 1f, 1f)
        s.drawText("PULSE ENGINE SYSTEM I", centerX, centerY - 120f, fontSize = 26f, xOrigin = 0.5f, yOrigin = 0.5f)

        s.setDrawColor(0.95f, 0.95f, 0.95f, 1f)
        s.drawText("POWER ON SELF TEST", centerX, centerY - 78f, fontSize = 18f, xOrigin = 0.5f, yOrigin = 0.5f)

        val left = centerX - 170f
        if (elapsed >= 0.7f) {
            s.setDrawColor(0.85f, 0.95f, 1f, 1f)
            s.drawText("RAM CHECK ....", left, centerY - 34f, fontSize = 16f)
            if (elapsed >= 1.3f) {
                s.setDrawColor(0.95f, 0.95f, 0.3f, 1f)
                s.drawText("OK", left + 175f, centerY - 34f, fontSize = 16f)
            }
        }

        if (elapsed >= 1.6f) {
            s.setDrawColor(0.85f, 0.95f, 1f, 1f)
            s.drawText("ROM CHECK ....", left, centerY - 8f, fontSize = 16f)
            if (elapsed >= 2.3f) {
                s.setDrawColor(0.95f, 0.95f, 0.3f, 1f)
                s.drawText("OK", left + 175f, centerY - 8f, fontSize = 16f)
            }
        }

        if (elapsed >= 2.6f) {
            s.setDrawColor(0.85f, 0.95f, 1f, 1f)
            s.drawText("VIDEO CHECK ..", left, centerY + 18f, fontSize = 16f)
            if (elapsed >= 3.2f) {
                s.setDrawColor(0.95f, 0.95f, 0.3f, 1f)
                s.drawText("OK", left + 175f, centerY + 18f, fontSize = 16f)
            }
        }

        if (elapsed >= 3.5f) {
            s.setDrawColor(0.85f, 0.95f, 1f, 1f)
            s.drawText("SOUND CHECK ..", left, centerY + 44f, fontSize = 16f)
            if (elapsed >= 4.1f) {
                s.setDrawColor(0.95f, 0.95f, 0.3f, 1f)
                s.drawText("OK", left + 175f, centerY + 44f, fontSize = 16f)
            }
        }

        s.setDrawColor(0.9f, 0.9f, 0.9f, 1f)
        s.drawText("PROGRESS ${pct.toString().padStart(3, '0')}%", centerX, centerY + 84f, fontSize = 16f, xOrigin = 0.5f, yOrigin = 0.5f)

        if (elapsed >= 4.4f) {
            s.setDrawColor(0.95f, 0.35f, 0.35f, 1f)
            s.drawText("2026 PULSE ENGINE LTD.", centerX, centerY + 116f, fontSize = 16f, xOrigin = 0.5f, yOrigin = 0.5f)
        }
    }

    private fun renderBootVideoTestScreen(s: Surface, elapsed: Float) {
        val width = engine.window.width.toFloat()
        val height = engine.window.height.toFloat()
        val left = 92f
        val top = 90f
        val right = width - 92f
        val bottom = height - 110f
        val areaW = right - left
        val areaH = bottom - top
        val line = 1.5f

        s.setDrawColor(0.02f, 0.03f, 0.05f, 1f)
        s.drawQuad(0f, 0f, width, height)

        s.setDrawColor(0.86f, 0.9f, 0.95f, 1f)
        s.drawQuad(left, top, areaW, line)
        s.drawQuad(left, bottom - line, areaW, line)
        s.drawQuad(left, top, line, areaH)
        s.drawQuad(right - line, top, line, areaH)

        val spacing = 28f
        var gx = left + spacing
        while (gx < right - spacing) {
            s.drawQuad(gx, top, line, areaH)
            gx += spacing
        }
        var gy = top + spacing
        while (gy < bottom - spacing) {
            s.drawQuad(left, gy, areaW, line)
            gy += spacing
        }

        val colors = arrayOf(
            floatArrayOf(1f, 0.15f, 0.15f),
            floatArrayOf(1f, 0.85f, 0.15f),
            floatArrayOf(0.35f, 1f, 0.35f),
            floatArrayOf(0.2f, 1f, 1f),
            floatArrayOf(0.3f, 0.5f, 1f),
            floatArrayOf(0.9f, 0.25f, 0.95f),
        )

        val horizontalBarsY = top + 14f
        val horizontalBarH = 32f
        val horizontalBarW = areaW / colors.size
        for (i in colors.indices) {
            val c = colors[i]
            s.setDrawColor(c[0], c[1], c[2], 1f)
            s.drawQuad(left + i * horizontalBarW, horizontalBarsY, horizontalBarW + 1f, horizontalBarH)
        }

        val barW = 18f
        val barX = width * 0.5f - barW * 0.5f
        val sectionH = areaH / 6f
        for (i in colors.indices) {
            val c = colors[i]
            s.setDrawColor(c[0], c[1], c[2], 1f)
            s.drawQuad(barX, top + i * sectionH, barW, sectionH + 1f)
        }

        val cx = (left + right) * 0.5f
        val cy = (top + bottom) * 0.5f + 18f
        s.setDrawColor(1f, 1f, 1f, 0.95f)
        s.drawLine(cx - 120f, cy, cx + 120f, cy)
        s.drawLine(cx, cy - 120f, cx, cy + 120f)
        drawFilledCircle(s, cx, cy, 90f, 40)
        s.setDrawColor(0.02f, 0.03f, 0.05f, 1f)
        drawFilledCircle(s, cx, cy, 88f, 40)
        s.setDrawColor(1f, 1f, 1f, 0.95f)
        drawFilledCircle(s, cx, cy, 56f, 36)
        s.setDrawColor(0.02f, 0.03f, 0.05f, 1f)
        drawFilledCircle(s, cx, cy, 54f, 36)

        s.setDrawColor(0.9f, 0.95f, 1f, 1f)
        s.drawText("VIDEO TEST: GEOMETRY + RGB", width * 0.5f, 42f, fontSize = 20f, xOrigin = 0.5f, yOrigin = 0.5f)
        val flash = 0.75f + 0.25f * sin(elapsed * 18f)
        s.setDrawColor(0.95f, 0.95f, 0.3f, flash)
        s.drawText("SIGNAL OK", width * 0.5f, height - 58f, fontSize = 18f, xOrigin = 0.5f, yOrigin = 0.5f)
    }

    private fun renderAttractScreen(s: Surface) {
        val centerX = engine.window.width / 2f
        val y0 = engine.window.height / 2f - 110f
        drawGlowText(s, "CHARACTER / NICKNAME", centerX, y0, fontSize = 28f, red = 1f, green = 0.3f, blue = 0.3f, xOrigin = 0.5f)
        s.setDrawColor(1f, 0.3f, 0.3f, 1f)
        s.drawText("CHARACTER / NICKNAME", centerX, y0, fontSize = 28f, xOrigin = 0.5f)

        val elapsed = (6f - attractTimer).coerceIn(0f, 6f)
        val cards = listOf(
            Triple("SHADOW", "-OIKAKE AKABEI", floatArrayOf(1f, 0.2f, 0.2f)),
            Triple("SPEEDY", "-MACHIBUSE PINKY", floatArrayOf(1f, 0.68f, 0.86f)),
            Triple("BASHFUL", "-KIMAGURE AOKU", floatArrayOf(0.2f, 1f, 1f)),
            Triple("POKEY", "-OTOBOKE GUZUTA", floatArrayOf(1f, 0.72f, 0.2f)),
        )
        for (i in cards.indices) {
            if (elapsed < i * 1.2f) break
            val (name, nick, c) = cards[i]
            val y = y0 + 46f + i * 40f
            val gx = centerX - 188f
            s.setDrawColor(c[0], c[1], c[2], 1f)
            drawGhostBody(s, gx, y + 4f, 20f)
            drawGhostEyes(s, gx, y + 2f, Direction.LEFT, eyeScale = 0.8f)
            s.setDrawColor(c[0], c[1], c[2], 1f)
            s.drawText(name, centerX - 155f, y, fontSize = 22f)
            s.setDrawColor(0.9f, 0.9f, 0.9f, 0.95f)
            s.drawText(nick, centerX - 20f, y, fontSize = 18f)
        }
    }

    private fun renderTitlePointsScreen(s: Surface) {
        s.setDrawColor(0f, 0f, 0f, 0.5f)
        s.drawQuad(0f, 0f, engine.window.width.toFloat(), engine.window.height.toFloat())

        val centerX = engine.window.width / 2f
        val centerY = engine.window.height / 2f

        drawGlowText(s, "PACMAN", centerX, centerY - 120f, fontSize = 72f, red = 1f, green = 0.95f, blue = 0f, xOrigin = 0.5f, yOrigin = 0.5f)
        s.setDrawColor(1f, 0.95f, 0f, 1f)
        s.drawText("PACMAN", centerX, centerY - 120f, fontSize = 72f, xOrigin = 0.5f, yOrigin = 0.5f)

        s.setDrawColor(0.2f, 0.8f, 1f, 1f)
        s.drawText("© 2026 PULSE ENGINE LTD.", centerX, centerY - 65f, fontSize = 20f, xOrigin = 0.5f, yOrigin = 0.5f)

        val ghostColors = listOf(
            floatArrayOf(1f, 0.2f, 0.2f),
            floatArrayOf(1f, 0.68f, 0.86f),
            floatArrayOf(0.2f, 1f, 1f),
            floatArrayOf(1f, 0.72f, 0.2f),
        )
        val ghostY = centerY - 22f
        val ghostSpacing = 62f
        val ghostStartX = centerX - ghostSpacing * 1.5f
        for (i in ghostColors.indices) {
            val color = ghostColors[i]
            val x = ghostStartX + ghostSpacing * i
            s.setDrawColor(color[0], color[1], color[2], 1f)
            drawGhostBody(s, x, ghostY + 4f, 22f)
            drawGhostEyes(s, x, ghostY + 2f, Direction.LEFT, eyeScale = 0.85f)
        }

        s.setDrawColor(0.9f, 0.9f, 0.9f, 0.9f)
        s.drawText("10 PTS", centerX - 70f, centerY + 26f, fontSize = 22f, xOrigin = 1f, yOrigin = 0.5f)
        s.drawText("DOT", centerX - 55f, centerY + 26f, fontSize = 22f, xOrigin = 0f, yOrigin = 0.5f)
        s.drawText("50 PTS", centerX - 70f, centerY + 57f, fontSize = 22f, xOrigin = 1f, yOrigin = 0.5f)
        s.drawText("POWER PELLET", centerX - 55f, centerY + 57f, fontSize = 22f, xOrigin = 0f, yOrigin = 0.5f)

        val blink = (0.35f + 0.65f * (0.5f + 0.5f * sin(uiPulseTime * 5f))).coerceIn(0.2f, 1f)
        s.setDrawColor(1f, 1f, 1f, blink)
        s.drawText("PRESS ENTER TO START", centerX, centerY + 122f, fontSize = 24f, xOrigin = 0.5f, yOrigin = 0.5f)
    }

    private fun renderHiScoreScreen(s: Surface) {
        val centerX = engine.window.width / 2f
        val centerY = engine.window.height / 2f

        s.setDrawColor(0f, 0f, 0f, 0.5f)
        s.drawQuad(0f, 0f, engine.window.width.toFloat(), engine.window.height.toFloat())

        drawGlowText(s, "HIGH SCORES", centerX, centerY - 120f, fontSize = 56f, red = 1f, green = 0.95f, blue = 0.1f, xOrigin = 0.5f, yOrigin = 0.5f)
        s.setDrawColor(1f, 0.95f, 0.1f, 1f)
        s.drawText("HIGH SCORES", centerX, centerY - 120f, fontSize = 56f, xOrigin = 0.5f, yOrigin = 0.5f)

        val rows = listOf(
            "1ST  PLAYER   ${highScore.toString().padStart(6, '0')}",
            "2ND  AAA      020000",
            "3RD  BBB      015000",
            "4TH  CCC      010000",
            "5TH  DDD      005000",
        )
        rows.forEachIndexed { i, row ->
            val alpha = 0.75f + 0.25f * sin(uiPulseTime * 2f + i * 0.4f)
            s.setDrawColor(0.9f, 0.95f, 1f, alpha)
            s.drawText(row, centerX, centerY - 30f + i * 34f, fontSize = 24f, xOrigin = 0.5f, yOrigin = 0.5f)
        }

        s.setDrawColor(1f, 1f, 1f, 0.8f)
        s.drawText("PRESS ENTER TO START", centerX, centerY + 150f, fontSize = 22f, xOrigin = 0.5f, yOrigin = 0.5f)
    }

    private fun drawGlowText(
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
        s.drawText(text, x - 3f, y, fontSize = fontSize, xOrigin = xOrigin, yOrigin = yOrigin)
        s.drawText(text, x + 3f, y, fontSize = fontSize, xOrigin = xOrigin, yOrigin = yOrigin)
        s.drawText(text, x, y - 3f, fontSize = fontSize, xOrigin = xOrigin, yOrigin = yOrigin)
        s.drawText(text, x, y + 3f, fontSize = fontSize, xOrigin = xOrigin, yOrigin = yOrigin)
        s.setDrawColor(red, green, blue, 0.1f)
        s.drawText(text, x - 6f, y, fontSize = fontSize, xOrigin = xOrigin, yOrigin = yOrigin)
        s.drawText(text, x + 6f, y, fontSize = fontSize, xOrigin = xOrigin, yOrigin = yOrigin)
        s.drawText(text, x, y - 6f, fontSize = fontSize, xOrigin = xOrigin, yOrigin = yOrigin)
        s.drawText(text, x, y + 6f, fontSize = fontSize, xOrigin = xOrigin, yOrigin = yOrigin)
    }

    private fun drawLifeIcon(s: Surface, x: Float, y: Float, radius: Float) {
        s.setDrawColor(1f, 0.95f, 0f, 1f)
        drawFilledCircle(s, x, y, radius, 14)
        drawPacmanMouthCutout(s, x, y, radius, Direction.RIGHT, 0.45f)
    }

    private fun drawFilledCircle(s: Surface, cx: Float, cy: Float, radius: Float, segments: Int = 20) {
        if (radius <= 0.5f) return
        val step = (radius * 2f) / segments
        for (i in 0..segments) {
            val y = cy - radius + i * step
            val dy = y - cy
            val halfWidth = sqrt((radius * radius - dy * dy).coerceAtLeast(0f))
            s.drawQuad(cx - halfWidth, y, halfWidth * 2f, step + 0.3f)
        }
    }

    private fun drawPacmanMouthCutout(s: Surface, cx: Float, cy: Float, radius: Float, dir: Direction, open: Float) {
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
                    s.drawQuad(cx + dx, cy - h, step + 0.8f, h * 2f + 0.8f)
                }
            }

            Direction.LEFT -> {
                for (i in 0..samples) {
                    val dx = i * step
                    val h = maxHalf * (dx / radius)
                    s.drawQuad(cx - dx - step, cy - h, step + 0.8f, h * 2f + 0.8f)
                }
            }

            Direction.UP -> {
                for (i in 0..samples) {
                    val dy = i * step
                    val w = maxHalf * (dy / radius)
                    s.drawQuad(cx - w, cy - dy - step, w * 2f + 0.8f, step + 0.8f)
                }
            }

            Direction.DOWN -> {
                for (i in 0..samples) {
                    val dy = i * step
                    val w = maxHalf * (dy / radius)
                    s.drawQuad(cx - w, cy + dy, w * 2f + 0.8f, step + 0.8f)
                }
            }

            Direction.NONE -> {}
        }
    }

    sealed interface MenuItemType {
        data object Toggle : MenuItemType
        data object Slider : MenuItemType
        data object Cycle : MenuItemType
        data object Header : MenuItemType
    }

    data class MenuItem(
        val label: String,
        val type: MenuItemType,
        val getter: (() -> Any)? = null,
        val setter: ((Boolean) -> Unit)? = null,
        val sliderSetter: ((Float) -> Unit)? = null,
        val cycleSetter: (() -> Unit)? = null,
    )

    companion object {
        private const val CRT_EFFECT_NAME = "pacman_crt"
        private const val UI_SURFACE_NAME = "pacman_ui"
        private const val SCANLINE_EFFECT_NAME = "pacman_scanline"
        private const val BLOOM_EFFECT_NAME = "pacman_bloom"
        private const val DEBUG_SURFACE_NAME = "pacman_debug"
    }
}

private class MazeOccluder : Box(), DirectLightOccluder {
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

enum class GamePhase { BOOT, ATTRACT, ATTRACT_DEMO, TITLE_POINTS, HI_SCORE, READY, PLAYING, DYING, LEVEL_TRANSITION, GAME_OVER, WON }
enum class GhostMode { SCATTER, CHASE, FRIGHTENED, EATEN }
enum class GhostType { BLINKY, PINKY, INKY, CLYDE }
enum class SceneBrightness { LOW, MEDIUM, HIGH }

enum class FruitType(val score: Int) {
    CHERRY(100),
    STRAWBERRY(300),
    ORANGE(500),
    APPLE(700),
    MELON(1000),
    GALAXIAN(2000),
    BELL(3000),
    KEY(5000),
}

data class GhostState(
    val type: GhostType,
    var gridX: Int,
    var gridY: Int,
    var direction: Direction,
    var mode: GhostMode,
    var progress: Float,
    var released: Boolean,
    var releaseTimer: Float,
)

data class FruitState(
    val type: FruitType,
    val col: Int,
    val row: Int,
    var timer: Float,
)

data class ScorePopup(
    val x: Float,
    var y: Float,
    val text: String,
    var timer: Float,
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var life: Float,
    val maxLife: Float,
    val red: Float,
    val green: Float,
    val blue: Float,
)

data class HighScoreData(val score: Int)

data class LightPair(
    val first: Lamp,
    val second: Lamp,
)
