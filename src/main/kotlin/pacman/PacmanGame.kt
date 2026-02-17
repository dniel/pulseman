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

    private val gameSpeedScale = 0.5f
    private val pacSpeed = 7f * gameSpeedScale
    private val pacman = PacmanController(pacSpeed, gameSpeedScale)
    private val ghostAI = GhostAISystem(pacman, gameSpeedScale)

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
     private var deathAnimTimer = 0f
     private var uiPulseTime = 0f
    private var lightingEnabled = true
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
     private var frightenedAmbientShiftEnabled = true
     private var enhancedPacAuraEnabled = true
     private var enhancedGhostLightsEnabled = true
     
     private val particleSystem = ParticleSystem()
     private var nativeFogEnabled = false
     private var nativeFogIntensity = 0.5f
     private var fogOfWarEnabled = false
     private var dynamicFrightenedBloomEnabled = true
     private var serviceMenuOpen = false
     private var serviceMenuCursorIndex = 1
     private var bootTestHold = false
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
     private lateinit var soundManager: SoundManager
     private lateinit var scoreManager: ScoreManager
     private lateinit var postProcessing: PostProcessingManager
     private lateinit var fruitManager: FruitManager

     private var dotsEatenThisLevel = 0

    private val bootDuration = 5f
    private val startScreenDelay = 8f
    private val attractDemoDuration = 10f


     override fun onCreate() {
         engine.config.gameName = "PulsDniel Pacman"
         engine.config.fixedTickRate = 60f
         engine.config.targetFps = 60
         engine.console.runScript("init.pes")
         engine.console.runScript("init-dev.pes")
          setupUiSurface()
         postProcessing = PostProcessingManager(engine)
         postProcessing.configurePostEffects()
         soundManager = SoundManager(engine)
         soundManager.loadAll()
         scoreManager = ScoreManager()
         scoreManager.highScore = engine.data.loadObject<HighScoreData>("highscore.json")?.score ?: 0
         fruitManager = FruitManager(scoreManager, particleSystem, soundManager)
         resetGame()
         setupSceneLighting()
     }

     override fun onDestroy() {
         engine.data.saveObject(HighScoreData(scoreManager.highScore), "highscore.json")
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

        if (engine.input.wasClicked(Key.UP) || engine.input.wasClicked(Key.W)) pacman.nextDir = Direction.UP
        if (engine.input.wasClicked(Key.DOWN)) pacman.nextDir = Direction.DOWN
        if (engine.input.wasClicked(Key.LEFT) || engine.input.wasClicked(Key.A)) pacman.nextDir = Direction.LEFT
         if (engine.input.wasClicked(Key.RIGHT) || engine.input.wasClicked(Key.D)) pacman.nextDir = Direction.RIGHT
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

    private fun setupUiSurface() {
        engine.gfx.createSurface(UI_SURFACE_NAME)
            .setBackgroundColor(0f, 0f, 0f, 0f)
            .setBlendFunction(BlendFunction.NORMAL)
            .setIsVisible(true)
    }

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
            getter = { postProcessing.crtEnabled },
            setter = { value ->
                postProcessing.crtEnabled = value
                if (postProcessing.crtEnabled) {
                    postProcessing.ensureCRTEffects()
                    postProcessing.updateCRTEffectSettings()
                } else {
                    postProcessing.deleteCRTEffects()
                }
            },
        ),
        MenuItem(
            label = "CRT Strength",
            type = MenuItemType.Slider,
            getter = { postProcessing.crtStrength },
            sliderSetter = { delta ->
                postProcessing.crtStrength = (postProcessing.crtStrength + delta).coerceIn(0f, 2f)
                postProcessing.updateCRTEffectSettings()
            },
        ),
        MenuItem(
            label = "Scanline",
            type = MenuItemType.Toggle,
            getter = { postProcessing.scanlineEnabled },
            setter = { value ->
                postProcessing.scanlineEnabled = value
                if (postProcessing.scanlineEnabled) {
                    postProcessing.ensureScanlineEffects()
                    postProcessing.updateScanlineEffectSettings()
                } else {
                    postProcessing.deleteScanlineEffects()
                }
            },
        ),
        MenuItem(
            label = "Scanline Strength",
            type = MenuItemType.Slider,
            getter = { postProcessing.scanlineStrength },
            sliderSetter = { delta ->
                postProcessing.scanlineStrength = (postProcessing.scanlineStrength + delta).coerceIn(0f, 2f)
                postProcessing.updateScanlineEffectSettings()
            },
        ),
        MenuItem(
            label = "Bloom",
            type = MenuItemType.Toggle,
            getter = { postProcessing.bloomEnabled },
            setter = { value ->
                postProcessing.bloomEnabled = value
                if (postProcessing.bloomEnabled) {
                    postProcessing.ensureBloomEffects()
                    postProcessing.updateBloomEffectSettings(ghostAI.frightenedTimer, dynamicFrightenedBloomEnabled, isGameplayVisualPhase())
                } else {
                    postProcessing.deleteBloomEffects()
                }
            },
        ),
        MenuItem(
            label = "Bloom Strength",
            type = MenuItemType.Slider,
            getter = { postProcessing.bloomStrength },
            sliderSetter = { delta ->
                postProcessing.bloomStrength = (postProcessing.bloomStrength + delta).coerceIn(0f, 2f)
                postProcessing.updateBloomEffectSettings(ghostAI.frightenedTimer, dynamicFrightenedBloomEnabled, isGameplayVisualPhase())
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
         MenuItem(
             label = "── Visual Effects ──",
             type = MenuItemType.Header,
         ),
         MenuItem(
             label = "Frightened Ambient Shift",
             type = MenuItemType.Toggle,
             getter = { frightenedAmbientShiftEnabled },
             setter = { value -> frightenedAmbientShiftEnabled = value },
         ),
         MenuItem(
             label = "Enhanced Pac Aura",
             type = MenuItemType.Toggle,
             getter = { enhancedPacAuraEnabled },
             setter = { value -> enhancedPacAuraEnabled = value },
         ),
         MenuItem(
             label = "Enhanced Ghost Lights",
             type = MenuItemType.Toggle,
             getter = { enhancedGhostLightsEnabled },
             setter = { value -> enhancedGhostLightsEnabled = value },
         ),
         MenuItem(
             label = "Frightened Particle Trail",
             type = MenuItemType.Toggle,
             getter = { particleSystem.frightenedParticleTrailEnabled },
             setter = { value -> particleSystem.frightenedParticleTrailEnabled = value },
         ),
         MenuItem(
             label = "Ambient Dust",
             type = MenuItemType.Toggle,
             getter = { particleSystem.ambientDustEnabled },
             setter = { value -> particleSystem.ambientDustEnabled = value },
         ),
         MenuItem(
             label = "Ghost Color Explosions",
             type = MenuItemType.Toggle,
             getter = { particleSystem.enhancedGhostExplosionsEnabled },
             setter = { value -> particleSystem.enhancedGhostExplosionsEnabled = value },
         ),
         MenuItem(
             label = "Level Win Confetti",
             type = MenuItemType.Toggle,
             getter = { particleSystem.levelWinConfettiEnabled },
             setter = { value -> particleSystem.levelWinConfettiEnabled = value },
         ),
         MenuItem(
             label = "Fog of War",
             type = MenuItemType.Toggle,
             getter = { fogOfWarEnabled },
             setter = { value -> fogOfWarEnabled = value },
         ),
         MenuItem(
             label = "Frightened Bloom Boost",
             type = MenuItemType.Toggle,
             getter = { dynamicFrightenedBloomEnabled },
             setter = { value -> dynamicFrightenedBloomEnabled = value },
         ),
         MenuItem(
             label = "Native Fog",
             type = MenuItemType.Slider,
             getter = { nativeFogIntensity },
             sliderSetter = { delta ->
                 nativeFogIntensity = (nativeFogIntensity + delta * 0.1f).coerceIn(0f, 1f)
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
                 pacman.updatePacman(dt, ::eatDotAt) { col, row -> fruitManager.checkFruitCollision(col, row) }
                 particleSystem.emitPacTrail(pacman.pixelX(), pacman.pixelY(), phase, ghostAI.frightenedTimer)
                 particleSystem.emitFrightenedTrail(pacman.pixelX(), pacman.pixelY(), phase, ghostAI.frightenedTimer)
                 particleSystem.emitAmbientDust(dt, phase)
                 ghostAI.update(dt, level)
                 fruitManager.updateFruit(dt)
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
                 pacman.updatePacman(dt, ::eatDotAt) { col, row -> fruitManager.checkFruitCollision(col, row) }
                 particleSystem.emitPacTrail(pacman.pixelX(), pacman.pixelY(), phase, ghostAI.frightenedTimer)
                 particleSystem.emitFrightenedTrail(pacman.pixelX(), pacman.pixelY(), phase, ghostAI.frightenedTimer)
                 particleSystem.emitAmbientDust(dt, phase)
                 ghostAI.update(dt, level)
                 fruitManager.updateFruit(dt)
                 checkCollisions()
                  if (Maze.dotsRemaining() == 0) {
                      phase = GamePhase.WON
                     wonTimer = 1.5f
                     particleSystem.emitLevelWinConfetti(pacman.pixelX(), pacman.pixelY())
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
                 fruitManager.updateFruit(dt)
                 gameOverTimer -= dt
                if (gameOverTimer <= 0f) enterStartScreen()
            }
        }

        pacman.updateMouthAnimation(dt)
        if (attractDemoGameOverTimer > 0f) {
            attractDemoGameOverTimer = (attractDemoGameOverTimer - dt).coerceAtLeast(0f)
        }
         if (isNonGameplayLightsOffPhase()) {
             setLightingEnabledState(false)
         }
         scoreManager.update(dt)
         particleSystem.updateParticles(dt)
        syncSceneLights()
    }

     override fun onRender() {
         val s = engine.gfx.mainSurface
         val uiSurface = engine.gfx.getSurfaceOrDefault(UI_SURFACE_NAME)
         val gameplayPhase = isGameplayVisualPhase()
        if (postProcessing.crtEnabled && !postProcessing.hasMainCrtEffect()) {
            postProcessing.ensureCRTEffects()
        } else if (!postProcessing.crtEnabled && postProcessing.hasMainCrtEffect()) {
            postProcessing.deleteCRTEffects()
        }
        if (postProcessing.scanlineEnabled && !postProcessing.hasMainScanlineEffect()) {
            postProcessing.ensureScanlineEffects()
        } else if (!postProcessing.scanlineEnabled && postProcessing.hasMainScanlineEffect()) {
            postProcessing.deleteScanlineEffects()
        }
        if (postProcessing.bloomEnabled && !postProcessing.hasMainBloomEffect()) {
            postProcessing.ensureBloomEffects()
        } else if (!postProcessing.bloomEnabled && postProcessing.hasMainBloomEffect()) {
            postProcessing.deleteBloomEffects()
        }
        postProcessing.updateCRTEffectSettings()
        postProcessing.updateScanlineEffectSettings()
        postProcessing.updateBloomEffectSettings(ghostAI.frightenedTimer, dynamicFrightenedBloomEnabled, gameplayPhase)
        s.setBackgroundColor(0f, 0f, 0f, 1f)
        if (gameplayPhase) {
            uiSurface.setBackgroundColor(0f, 0f, 0f, 0f)
            renderMaze(s)
            if (entityHaloEnabled) renderEntityBloomHalos(s)
            renderFruit(s)
            if (phase != GamePhase.DYING) renderGhosts(s)
             renderPacman(s)
             particleSystem.renderParticles(s)
              scoreManager.render(s)
              if (geometryTestOverlayEnabled) renderGeometryTestOverlay(s)
             renderUI(uiSurface)
         } else {
             uiSurface.setBackgroundColor(0f, 0f, 0f, 0f)
             renderStartupScreen(s)
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
          scoreManager.reset()
          lives = 3
          level = 1
          particleSystem.reset()
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
         scoreManager.reset()
         lives = 3
         level = 1
         particleSystem.reset()
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
         scoreManager.reset()
         lives = 3
         level = 1
         particleSystem.reset()
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
         soundManager.play("pacman_beginning")
    }

     private fun startLevelState(resetDots: Boolean) {
         if (resetDots) Maze.reset()
         ghostAI.startLevel(level)
         dotsEatenThisLevel = 0
         fruitManager.reset()
         particleSystem.reset()
        resetPositions()
    }

    private fun startNextLevelTransition() {
         level++
         startLevelState(resetDots = true)
         phase = GamePhase.LEVEL_TRANSITION
         levelTransitionTimer = 1.5f
         soundManager.play("pacman_intermission")
    }

    private fun resetPositions() {
        pacman.reset()
        ghostAI.resetPositions()
    }

    private fun updateAttractPacmanControl() {
        pacman.updateAttractPacmanControl(ghostAI.ghosts)
    }

    private fun eatDotAt(col: Int, row: Int) {
        if (col !in 0 until Maze.COLS || row !in 0 until Maze.ROWS) return
        when (Maze.grid[row][col]) {
             Maze.DOT -> {
                 Maze.grid[row][col] = Maze.EMPTY
                  dotsEatenThisLevel++
                  scoreManager.addScore(10)
                  fruitManager.maybeSpawnFruit(dotsEatenThisLevel, level)
                 particleSystem.emitDotParticles(Maze.centerX(col), Maze.centerY(row))
                 soundManager.play("pacman_chomp")
            }

             Maze.POWER -> {
                 Maze.grid[row][col] = Maze.EMPTY
                 dotsEatenThisLevel++
                  scoreManager.addScore(50)
                  fruitManager.maybeSpawnFruit(dotsEatenThisLevel, level)
                 particleSystem.emitPowerPelletParticles(Maze.centerX(col), Maze.centerY(row))
                 ghostAI.activateFrightened(level)
                 soundManager.play("pacman_beginning")
            }
        }
    }

    private fun checkCollisions() {
        for (ghost in ghostAI.ghosts) {
            if (!ghost.released) continue
            val sameCell = ghost.gridX == pacman.gridX && ghost.gridY == pacman.gridY
            val adjacent = abs(ghost.gridX - pacman.gridX) + abs(ghost.gridY - pacman.gridY) <= 1 && ghost.progress > 0.5f

            if (sameCell || (adjacent && pacman.progress > 0.5f && ghost.direction == pacman.dir.opposite())) {
                when (ghost.mode) {
                     GhostMode.FRIGHTENED -> {
                         ghost.mode = GhostMode.EATEN
                         ghostAI.pelletsEatenForGhostScore++
                          val ghostScore = 200 * (1 shl (ghostAI.pelletsEatenForGhostScore - 1).coerceAtMost(3))
                          scoreManager.addScore(ghostScore)
                          particleSystem.emitGhostEatenParticles(ghostAI.ghostPixelX(ghost), ghostAI.ghostPixelY(ghost), ghost.type)
                          scoreManager.addPopup(ghostAI.ghostPixelX(ghost), ghostAI.ghostPixelY(ghost) - 8f, ghostScore.toString())
                          soundManager.play("pacman_eatghost")
                     }

                     GhostMode.EATEN -> {}
                     else -> {
                         particleSystem.emitDeathParticles(pacman.pixelX(), pacman.pixelY())
                         soundManager.play("pacman_death")
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
        val px = pacman.pixelX()
        val py = pacman.pixelY()
        val radius = (Maze.TILE - 4f) * 0.5f

        if (phase == GamePhase.DYING) {
            val life = (deathAnimTimer / 1.5f).coerceIn(0f, 1f)
            val gone = 1f - life
            val shrink = (1f - gone).coerceAtLeast(0f)
            val dyingMouth = (0.35f + gone * 0.65f).coerceAtMost(1f)
            s.setDrawColor(1f, 0.95f, 0f, 1f)
            drawFilledCircle(s, px, py, radius * shrink, 20)
            drawPacmanMouthCutout(s, px, py, radius * shrink, if (pacman.dir == Direction.NONE) Direction.RIGHT else pacman.dir, dyingMouth)
            return
        }

        s.setDrawColor(1f, 0.95f, 0f, 1f)
        drawFilledCircle(s, px, py, radius, 20)
        if (pacman.dir != Direction.NONE) {
            drawPacmanMouthCutout(s, px, py, radius, pacman.dir, pacman.mouthAngle)
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
        val pacX = pacman.pixelX()
        val pacY = pacman.pixelY()
        s.setDrawColor(1f, 0.95f, 0.24f, 0.16f + pulse * 0.12f)
        drawFilledCircle(s, pacX, pacY, 16f + pulse * 3f, 18)

        fruitManager.activeFruit?.let {
            val fx = Maze.centerX(it.col)
            val fy = Maze.centerY(it.row)
            s.setDrawColor(1f, 0.55f, 0.2f, 0.16f + pulse * 0.12f)
            drawFilledCircle(s, fx, fy, 13f + pulse * 2.5f, 16)
        }

        for (ghost in ghostAI.ghosts) {
            if (ghost.mode == GhostMode.EATEN) continue
            val gx = if (!ghost.released) Maze.centerX(ghost.gridX) else ghostAI.ghostPixelX(ghost)
            val gy = if (!ghost.released) Maze.centerY(ghost.gridY) else ghostAI.ghostPixelY(ghost)
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
        for (ghost in ghostAI.ghosts) {
            val gx = if (!ghost.released && ghost.mode != GhostMode.EATEN) Maze.centerX(ghost.gridX) else ghostAI.ghostPixelX(ghost)
            val gy = if (!ghost.released && ghost.mode != GhostMode.EATEN) Maze.centerY(ghost.gridY) else ghostAI.ghostPixelY(ghost)
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
            val flash = ghostAI.frightenedTimer < 2f && ((ghostAI.frightenedTimer * 6f).toInt() % 2 == 0)
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
        val fruit = fruitManager.activeFruit ?: return
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

    private fun syncSceneLights() {
        val pulse = 0.5f + 0.5f * sin(uiPulseTime * 3.8f)
        val spin = (uiPulseTime * 220f) % 360f
         val playfieldLightsEnabled = phase in setOf(
             GamePhase.PLAYING,
             GamePhase.ATTRACT_DEMO,
             GamePhase.DYING,
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

        // Ambient color priority chain: fogOfWar > frightenedAmbient > sceneBrightness
        val anyGhostFrightened = ghostAI.ghosts.any { it.mode == GhostMode.FRIGHTENED }
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

        pacAuraLight?.apply {
            x = pacman.pixelX()
            y = pacman.pixelY()
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
         if (phase == GamePhase.DYING) {
             val deathProgress = 1f - (deathAnimTimer / 1.5f).coerceIn(0f, 1f)
             val flicker = if ((deathAnimTimer * 12f).toInt() % 2 == 0) 0.3f else 1.0f
             val fade = (1f - deathProgress).coerceAtLeast(0f)
             
             pacAuraLight?.intensity = (pacAuraLight?.intensity ?: 0f) * flicker * fade
             ghostAuraLights.values.forEach { it.intensity = it.intensity * flicker * fade * 0.5f }
             boardBacklight?.intensity = (boardBacklight?.intensity ?: 0f) * fade
         }

         fruitAuraLight?.apply {
            val fruit = fruitManager.activeFruit
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

        for (ghost in ghostAI.ghosts) {
            val light = ghostAuraLights[ghost.type] ?: continue
            val conePair = eatenGhostConeLights[ghost.type]
            light.x = if (!ghost.released && ghost.mode != GhostMode.EATEN) Maze.centerX(ghost.gridX) else ghostAI.ghostPixelX(ghost)
            light.y = if (!ghost.released && ghost.mode != GhostMode.EATEN) Maze.centerY(ghost.gridY) else ghostAI.ghostPixelY(ghost)

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

     private fun renderUI(s: Surface) {
         val scoreText = scoreManager.score.toString().padStart(6, '0')
         val highScoreText = scoreManager.highScore.toString().padStart(6, '0')

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
        s.drawText("SERVICE MENU", centerX, titleY, fontSize = 38f, xOrigin = 0.5f, yOrigin = 0.5f)

         val startY = 100f
         val lineHeight = 18f
         var yOffset = startY

        for (i in menuItems.indices) {
            val item = menuItems[i]
            val isSelected = i == serviceMenuCursorIndex

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
            engine.window.height - 40f,
            fontSize = 14f,
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
             "1ST  PLAYER   ${scoreManager.highScore.toString().padStart(6, '0')}",
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
          private const val UI_SURFACE_NAME = "pacman_ui"
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
