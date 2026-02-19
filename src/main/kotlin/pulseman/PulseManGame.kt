package pulseman

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.PulseEngineGame
import no.njoh.pulseengine.core.graphics.api.BlendFunction
import no.njoh.pulseengine.core.input.Key
import no.njoh.pulseengine.modules.editor.SceneEditor
import no.njoh.pulseengine.modules.editor.UiElementFactory
import no.njoh.pulseengine.modules.metrics.MetricViewer
import no.njoh.pulseengine.modules.metrics.GpuMonitor
import kotlin.math.*

fun main() = PulseEngine.run<PulseManGame>()

/**
 * Main game orchestrator for the Pulse-Man application.
 *
 * Manages game state transitions, coordinates between subsystems (AI, Rendering, Sound, etc.),
 * and handles the main game loop through [onFixedUpdate] and [onRender].
 */
class PulseManGame : PulseEngineGame() {

    private val gameSpeedScale = 0.5f
    private val maxSpeed = LevelProgression.MAX_SPEED * gameSpeedScale
    private val pulseMan = PulseManController(maxSpeed * 0.80f, gameSpeedScale)
    private val ghostAI = GhostAISystem(pulseMan, gameSpeedScale)

     private var lives = 3
     private var level = 1
     var mazeMode = MazeMode.CLASSIC

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
    private var geometryTestOverlayEnabled = false
    private val particleSystem = ParticleSystem()
    private var dynamicFrightenedBloomEnabled = true
    private var bootTestHold = false
    private lateinit var lighting: LightingManager

    private val menuItems: List<MenuItem> by lazy { buildMenuItems() }
    private lateinit var soundManager: SoundManager
    private lateinit var scoreManager: ScoreManager
    private lateinit var postProcessing: PostProcessingManager
    private lateinit var fruitManager: FruitManager
    private lateinit var gameplayRenderer: GameplayRenderer
    private lateinit var screenRenderer: ScreenRenderer
    private lateinit var hudRenderer: HUDRenderer
    private lateinit var serviceMenu: ServiceMenuManager

    private var dotsEatenThisLevel = 0

    private val bootDuration = 5f
    private val startScreenDelay = 8f
    private val attractDemoDuration = 10f


    override fun onCreate() {
        engine.config.gameName = "PulsDniel PulseMan"
        engine.config.fixedTickRate = 60f
        engine.config.targetFps = 60
        engine.service.add(
            SceneEditor(UiElementFactory()),
            MetricViewer(),
            GpuMonitor(),
        )
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
        gameplayRenderer = GameplayRenderer(pulseMan, ghostAI, fruitManager)
        screenRenderer = ScreenRenderer(scoreManager)
        hudRenderer = HUDRenderer(scoreManager)
        serviceMenu = ServiceMenuManager(menuItems)
        lighting = LightingManager(engine)
        resetGame()
        lighting.setupSceneLighting()
    }

     override fun onDestroy() {
         engine.data.saveObject(HighScoreData(scoreManager.highScore), "highscore.json")
     }

    override fun onUpdate() {
        if (engine.input.wasClicked(Key.S)) {
            serviceMenu.toggle()
        }

        if (serviceMenu.isOpen) {
            serviceMenu.handleInput(engine)
            return
        }

        if (engine.input.wasClicked(Key.ENTER) && phase in setOf(GamePhase.BOOT, GamePhase.ATTRACT, GamePhase.ATTRACT_DEMO, GamePhase.TITLE_POINTS, GamePhase.HI_SCORE)) {
            startNewGameFromStartup()
            return
        }

        if (engine.input.wasClicked(Key.UP) || engine.input.wasClicked(Key.W)) pulseMan.nextDir = Direction.UP
        if (engine.input.wasClicked(Key.DOWN)) pulseMan.nextDir = Direction.DOWN
        if (engine.input.wasClicked(Key.LEFT) || engine.input.wasClicked(Key.A)) pulseMan.nextDir = Direction.LEFT
         if (engine.input.wasClicked(Key.RIGHT) || engine.input.wasClicked(Key.D)) pulseMan.nextDir = Direction.RIGHT
         if (engine.input.wasClicked(Key.R)) resetGame()
        if (engine.input.wasClicked(Key.T)) {
            if (bootTestHold) {
                bootTestHold = false
            } else {
                phase = GamePhase.BOOT
                bootTimer = bootDuration - 2.9f
                bootTestHold = true
                lighting.setLightingEnabledState(false)
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

    private fun buildMenuItems(): List<MenuItem> = listOf(
        MenuItem(
            label = "GAME",
            type = MenuItemType.Header,
        ),
        MenuItem(
            label = "Maze Mode",
            type = MenuItemType.Cycle,
            getter = { mazeMode },
            cycleSetter = {
                mazeMode = when (mazeMode) {
                    MazeMode.CLASSIC -> MazeMode.MS_PULSEMAN
                    MazeMode.MS_PULSEMAN -> MazeMode.CLASSIC
                }
            },
        ),
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
            getter = { lighting.lightingEnabled },
            setter = { value ->
                if (value || !isNonGameplayLightsOffPhase()) {
                    lighting.setLightingEnabledState(value)
                }
            },
        ),
        MenuItem(
            label = "Entity Halos",
            type = MenuItemType.Toggle,
            getter = { lighting.entityHaloEnabled },
            setter = { value -> lighting.entityHaloEnabled = value },
        ),
        MenuItem(
            label = "Board Backlight",
            type = MenuItemType.Toggle,
            getter = { lighting.boardBacklightEnabled },
            setter = { value -> lighting.boardBacklightEnabled = value },
        ),
        MenuItem(
            label = "Aura Lights",
            type = MenuItemType.Toggle,
            getter = { lighting.auraLightsEnabled },
            setter = { value -> lighting.auraLightsEnabled = value },
        ),
        MenuItem(
            label = "Brightness",
            type = MenuItemType.Cycle,
            getter = { lighting.sceneBrightness },
            cycleSetter = { lighting.cycleSceneBrightness() },
        ),
        MenuItem(
            label = "WALLS",
            type = MenuItemType.Header,
        ),
        MenuItem(
            label = "Wall Bevel",
            type = MenuItemType.Toggle,
            getter = { gameplayRenderer.wallBevelEnabled },
            setter = { value -> gameplayRenderer.wallBevelEnabled = value },
        ),
        MenuItem(
            label = "Wall Bevel Debug",
            type = MenuItemType.Toggle,
            getter = { gameplayRenderer.wallBevelDebug },
            setter = { value -> gameplayRenderer.wallBevelDebug = value },
        ),
        MenuItem(
            label = "Wall Outline",
            type = MenuItemType.Toggle,
            getter = { gameplayRenderer.wallOutlineEnabled },
            setter = { value -> gameplayRenderer.wallOutlineEnabled = value },
        ),
        MenuItem(
            label = "Wall Thin Outline",
            type = MenuItemType.Toggle,
            getter = { gameplayRenderer.wallThinOutlineMode },
            setter = { value -> gameplayRenderer.wallThinOutlineMode = value },
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
             getter = { lighting.lightingTargetMainEnabled },
             setter = { value -> lighting.setLightingTargetMain(value) },
         ),
         MenuItem(
             label = "── Visual Effects ──",
             type = MenuItemType.Header,
         ),
         MenuItem(
             label = "Frightened Ambient Shift",
             type = MenuItemType.Toggle,
             getter = { lighting.frightenedAmbientShiftEnabled },
             setter = { value -> lighting.frightenedAmbientShiftEnabled = value },
         ),
         MenuItem(
             label = "Enhanced Pac Aura",
             type = MenuItemType.Toggle,
             getter = { lighting.enhancedPacAuraEnabled },
             setter = { value -> lighting.enhancedPacAuraEnabled = value },
         ),
         MenuItem(
             label = "Enhanced Ghost Lights",
             type = MenuItemType.Toggle,
             getter = { lighting.enhancedGhostLightsEnabled },
             setter = { value -> lighting.enhancedGhostLightsEnabled = value },
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
             getter = { lighting.fogOfWarEnabled },
             setter = { value -> lighting.fogOfWarEnabled = value },
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
             getter = { lighting.nativeFogIntensity },
             sliderSetter = { delta ->
                 lighting.nativeFogIntensity = (lighting.nativeFogIntensity + delta * 0.1f).coerceIn(0f, 1f)
             },
         ),
     )

    /**
     * Main simulation loop for fixed-timestep logic.
     * Handles game state updates, AI, movement, and collisions.
     */
    override fun onFixedUpdate() {
        val dt = engine.data.fixedDeltaTime
        uiPulseTime += dt
        if (serviceMenu.isOpen) return

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
                 updateAttractPulseManControl()
                 applyLevelSpeed()
                 pulseMan.updatePulseMan(dt, ::eatDotAt) { col, row -> fruitManager.checkFruitCollision(col, row) }
                 particleSystem.emitPulseManTrail(pulseMan.pixelX(), pulseMan.pixelY(), phase, ghostAI.frightenedTimer)
                 particleSystem.emitFrightenedTrail(pulseMan.pixelX(), pulseMan.pixelY(), phase, ghostAI.frightenedTimer)
                 particleSystem.emitAmbientDust(dt, phase)
                 ghostAI.dotsRemaining = (Maze.totalDots() - dotsEatenThisLevel).coerceAtLeast(0)
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
                 applyLevelSpeed()
                 pulseMan.updatePulseMan(dt, ::eatDotAt) { col, row -> fruitManager.checkFruitCollision(col, row) }
                 particleSystem.emitPulseManTrail(pulseMan.pixelX(), pulseMan.pixelY(), phase, ghostAI.frightenedTimer)
                 particleSystem.emitFrightenedTrail(pulseMan.pixelX(), pulseMan.pixelY(), phase, ghostAI.frightenedTimer)
                 particleSystem.emitAmbientDust(dt, phase)
                 ghostAI.dotsRemaining = (Maze.totalDots() - dotsEatenThisLevel).coerceAtLeast(0)
                 ghostAI.update(dt, level)
                 fruitManager.updateFruit(dt)
                 checkCollisions()
                  if (Maze.dotsRemaining() == 0) {
                      phase = GamePhase.WON
                     wonTimer = 1.5f
                     particleSystem.emitLevelWinConfetti(pulseMan.pixelX(), pulseMan.pixelY())
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
                particleSystem.emitContinuousConfetti(dt)
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

        pulseMan.updateMouthAnimation(dt)
        if (attractDemoGameOverTimer > 0f) {
            attractDemoGameOverTimer = (attractDemoGameOverTimer - dt).coerceAtLeast(0f)
        }
         if (isNonGameplayLightsOffPhase()) {
             lighting.setLightingEnabledState(false)
         }
         scoreManager.update(dt)
         particleSystem.updateParticles(dt)
        lighting.syncSceneLights(LightingSnapshot(
            phase = phase,
            pulseX = pulseMan.pixelX(),
            pulseY = pulseMan.pixelY(),
            ghosts = ghostAI.ghosts,
            fruit = fruitManager.activeFruit,
            frightenedTimer = ghostAI.frightenedTimer,
            deathAnimTimer = deathAnimTimer,
            uiPulseTime = uiPulseTime,
        ))
    }

    /**
     * Renders the game graphics to the screen and UI surface.
     */
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
            gameplayRenderer.renderMaze(s, uiPulseTime)
            if (lighting.entityHaloEnabled) gameplayRenderer.renderEntityBloomHalos(s, uiPulseTime)
            gameplayRenderer.renderFruit(s)
            if (phase != GamePhase.DYING) gameplayRenderer.renderGhosts(s)
            gameplayRenderer.renderPulseMan(s, phase, deathAnimTimer)
            particleSystem.renderParticles(s)
            scoreManager.render(s)
            if (geometryTestOverlayEnabled) {
                gameplayRenderer.renderGeometryTestOverlay(s, engine.window.width.toFloat(), engine.window.height.toFloat())
            }
            hudRenderer.renderUI(uiSurface, phase, level, lives, uiPulseTime, attractDemoGameOverTimer, engine.window.width, engine.window.height)
        } else {
            uiSurface.setBackgroundColor(0f, 0f, 0f, 0f)
            screenRenderer.renderStartupScreen(s, phase, bootTimer, bootDuration, attractTimer, uiPulseTime, engine.window.width, engine.window.height)
        }
        if (serviceMenu.isOpen) {
            serviceMenu.render(uiSurface, engine.window.width.toFloat(), engine.window.height.toFloat())
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

    private fun resetGame() {
        serviceMenu.isOpen = false
        bootTestHold = false
        Maze.loadLayout(MazeLayouts.forLevel(1, mazeMode))
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
        lighting.setLightingEnabledState(false)
    }

    private fun startNewGameFromStartup() {
        serviceMenu.isOpen = false
        bootTestHold = false
        Maze.loadLayout(MazeLayouts.forLevel(1, mazeMode))
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
        lighting.setLightingEnabledState(false)
    }

     private fun startAttractDemo() {
         Maze.loadLayout(MazeLayouts.forLevel(1, mazeMode))
         scoreManager.reset()
         lives = 3
         level = 1
         particleSystem.reset()
         attractDemoGameOverTimer = 0f
         lighting.setLightingEnabledState(true)
         startLevelState(resetDots = true)
         phase = GamePhase.ATTRACT_DEMO
         attractDemoTimer = attractDemoDuration
     }

    private fun enterStartScreen() {
        serviceMenu.isOpen = false
        phase = GamePhase.TITLE_POINTS
        titlePointsTimer = startScreenDelay
        lighting.setLightingEnabledState(false)
    }

      private fun beginReadyPhase() {
          lighting.setLightingEnabledState(true)
          phase = GamePhase.READY
         readyTimer = 2f
         soundManager.play("pulseman_beginning")
    }

     private fun startLevelState(resetDots: Boolean) {
         if (resetDots) {
             Maze.loadLayout(MazeLayouts.forLevel(level, mazeMode))
             lighting.refreshMazeGeometry()
         }
         ghostAI.startLevel(level)
         applyLevelSpeed()
         dotsEatenThisLevel = 0
         pulseMan.invalidateDotCache()
         fruitManager.reset()
         particleSystem.reset()
        resetPositions()
    }

    /**
     * Sets [PulseManController.currentSpeed] to the correct value for the current [level] and game state.
     * Uses [LevelSpec.pacFrightSpeed] when ghosts are frightened, otherwise [LevelSpec.pacSpeed].
     */
    private fun applyLevelSpeed() {
        val spec = LevelProgression.forLevel(level)
        val fraction = if (ghostAI.frightenedTimer > 0f) spec.pacFrightSpeed else spec.pacSpeed
        pulseMan.currentSpeed = fraction * maxSpeed
    }

        private fun startNextLevelTransition() {
         level++
         startLevelState(resetDots = true)
         phase = GamePhase.LEVEL_TRANSITION
         levelTransitionTimer = 1.5f
         soundManager.play("pulseman_intermission")
    }

    private fun resetPositions() {
        pulseMan.reset()
        ghostAI.resetPositions()
    }

    private fun updateAttractPulseManControl() {
        pulseMan.updateAttractPulseManControl(ghostAI.ghosts)
    }

    private fun eatDotAt(col: Int, row: Int) {
        if (col !in 0 until Maze.COLS || row !in 0 until Maze.ROWS) return
        when (Maze.grid[row][col]) {
             Maze.DOT -> {
                 Maze.grid[row][col] = Maze.EMPTY
                 dotsEatenThisLevel++
                 pulseMan.invalidateDotCache()
                  scoreManager.addScore(10)
                  fruitManager.maybeSpawnFruit(dotsEatenThisLevel, level)
                 particleSystem.emitDotParticles(Maze.centerX(col), Maze.centerY(row))
                 soundManager.play("pulseman_chomp")
            }

             Maze.POWER -> {
                 Maze.grid[row][col] = Maze.EMPTY
                 dotsEatenThisLevel++
                 pulseMan.invalidateDotCache()
                  scoreManager.addScore(50)
                  fruitManager.maybeSpawnFruit(dotsEatenThisLevel, level)
                 particleSystem.emitPowerPelletParticles(Maze.centerX(col), Maze.centerY(row))
                 ghostAI.activateFrightened(level)
                 soundManager.play("pulseman_beginning")
            }
        }
    }

    private fun checkCollisions() {
        for (ghost in ghostAI.ghosts) {
            if (!ghost.released) continue
            val sameCell = ghost.gridX == pulseMan.gridX && ghost.gridY == pulseMan.gridY
            val adjacent = abs(ghost.gridX - pulseMan.gridX) + abs(ghost.gridY - pulseMan.gridY) <= 1 && ghost.progress > 0.5f

            if (sameCell || (adjacent && pulseMan.progress > 0.5f && ghost.direction == pulseMan.dir.opposite())) {
                when (ghost.mode) {
                     GhostMode.FRIGHTENED -> {
                         ghost.mode = GhostMode.EATEN
                         ghostAI.pelletsEatenForGhostScore++
                          val ghostScore = 200 * (1 shl (ghostAI.pelletsEatenForGhostScore - 1).coerceAtMost(3))
                          scoreManager.addScore(ghostScore)
                          particleSystem.emitGhostEatenParticles(ghostAI.ghostPixelX(ghost), ghostAI.ghostPixelY(ghost), ghost.type)
                          scoreManager.addPopup(ghostAI.ghostPixelX(ghost), ghostAI.ghostPixelY(ghost) - 8f, ghostScore.toString())
                          soundManager.play("pulseman_eatghost")
                     }

                     GhostMode.EATEN -> {}
                     else -> {
                         particleSystem.emitDeathParticles(pulseMan.pixelX(), pulseMan.pixelY())
                         soundManager.play("pulseman_death")
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

    companion object {
        private const val UI_SURFACE_NAME = "pulseman_ui"
    }
}

/**
 * Phases of the game life cycle, controlling which logic and rendering paths are active.
 *
 * Startup sequence: [BOOT] → [ATTRACT] → [HI_SCORE] → [ATTRACT_DEMO] → (loop to [TITLE_POINTS] → [ATTRACT]).
 * Gameplay sequence: [READY] → [PLAYING] → [WON]/[DYING] → [LEVEL_TRANSITION]/[GAME_OVER].
 */
enum class GamePhase { BOOT, ATTRACT, ATTRACT_DEMO, TITLE_POINTS, HI_SCORE, READY, PLAYING, DYING, LEVEL_TRANSITION, GAME_OVER, WON }

/**
 * Behavioral state of a ghost, determining its movement target and vulnerability.
 *
 * - [SCATTER] — ghost retreats to its assigned corner target.
 * - [CHASE] — ghost actively pursues Pulse-Man using its personality-specific targeting.
 * - [FRIGHTENED] — ghost moves randomly and can be eaten by Pulse-Man (triggered by power pellet).
 * - [EATEN] — ghost's eyes return to the ghost house via BFS shortest path for respawn.
 */
enum class GhostMode { SCATTER, CHASE, FRIGHTENED, EATEN }

/**
 * The four ghost characters, each with unique chase targeting in [GhostAISystem]:
 *
 * - [BLINKY] (red) — targets Pulse-Man's current tile directly.
 * - [PINKY] (pink) — targets 4 tiles ahead of Pulse-Man's facing direction.
 * - [INKY] (cyan) — uses a vector from Blinky through 2 tiles ahead of Pulse-Man, doubled.
 * - [CLYDE] (orange) — chases like Blinky when far, scatters when within 8 tiles.
 */
enum class GhostType { BLINKY, PINKY, INKY, CLYDE }

/** User-selectable scene brightness levels, controlling the ambient light color in [LightingManager]. */
enum class SceneBrightness { LOW, MEDIUM, HIGH }

/**
 * Bonus fruit types that appear at dot-count thresholds, with escalating point values.
 * Higher-level fruits appear on later levels via [FruitManager].
 */
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

/**
 * Mutable state of a single ghost, updated each frame by [GhostAISystem].
 *
 * @property gridX Current tile column.
 * @property gridY Current tile row.
 * @property direction Movement direction for interpolation between tiles.
 * @property mode Current behavioral mode (scatter, chase, frightened, eaten).
 * @property progress Interpolation fraction (0–1) between current tile and next tile.
 * @property released Whether the ghost has left the ghost house and is active on the maze.
 * @property releaseTimer Countdown until automatic release from the ghost house.
 */
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

/** Active bonus fruit on the maze, managed by [FruitManager]. Disappears when [timer] expires. */
data class FruitState(
    val type: FruitType,
    val col: Int,
    val row: Int,
    var timer: Float,
)

/** Floating score text that drifts upward and fades out, rendered by [ScoreManager]. */
data class ScorePopup(
    val x: Float,
    var y: Float,
    val text: String,
    var timer: Float,
)

/** A single visual particle with position, velocity, color, and lifetime, managed by [ParticleSystem]. */
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

/** Serializable high score persisted to `highscore.json` between game sessions. */
data class HighScoreData(val score: Int)
