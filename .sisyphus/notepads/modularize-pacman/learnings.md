# Learnings: Modularize PacmanGame

## Conventions & Patterns

### Manager Instantiation Pattern
```kotlin
// In PacmanGame class
private lateinit var soundManager: SoundManager
private val scoreManager = ScoreManager()

// In onCreate()
soundManager = SoundManager(engine)
scoreManager.highScore = engine.data.loadObject<HighScoreData>("highscore.json")?.score ?: 0
```

### Call Site Replacement Pattern
```kotlin
// Before
addScore(10)
updateScorePopups(dt)
renderScorePopups(s)

// After
scoreManager.addScore(10)
scoreManager.update(dt)
scoreManager.render(s)
```

## Extraction Insights

### Task 1: SoundManager
- **Feasibility**: 95% (completely stateless, zero dependencies)
- **Functions moved**: 4 (loadGameSounds, loadSoundAsset, resolveSoundPath, tryPlaySound)
- **Call sites updated**: 7 (all `tryPlaySound` calls)
- **Result**: 55-line standalone module

### Task 2: ScoreManager
- **Feasibility**: 90% (minimal dependencies, clear boundaries)
- **Functions moved**: 4 (addScore, addPopup, update, render)
- **State moved**: score, highScore, scorePopups
- **Important distinction**: `lives` and `level` stayed in PacmanGame (game phase state, not score state)
- **Local variable gotcha**: Lines 1115-1147 have a local `var score = 0f` used in AI pathfinding heuristic — this is NOT the game score and was correctly left untouched
- **High score persistence**: Line 150 loads highScore from JSON — assignment redirected to `scoreManager.highScore =` to preserve persistence logic
- **Result**: 43-line module with clean separation

## Build Verification
- **Command**: `./gradlew build`
- **Expected**: BUILD SUCCESSFUL
- **Actual**: Passed after both Task 1 and Task 2

### Task 3: PostProcessingManager
- **Feasibility**: 85% (needed parameter passing for phase-dependent logic)
- **Functions moved**: 13 (configurePostEffects, ensure/delete/update for CRT/Scanline/Bloom, has* checkers)
- **State moved**: 6 variables (crtEnabled, scanlineEnabled, bloomEnabled, crtStrength, scanlineStrength, bloomStrength)
- **Constants moved**: 3 (CRT_EFFECT_NAME, SCANLINE_EFFECT_NAME, BLOOM_EFFECT_NAME)
- **Key challenge**: `updateBloomEffectSettings()` needed access to `frightenedTimer`, `dynamicFrightenedBloomEnabled`, and `isGameplayVisualPhase()` — solved by passing as parameters
- **Signature change**: `updateBloomEffectSettings(frightenedTimer: Float, dynamicFrightenedBloomEnabled: Boolean, isGameplayPhase: Boolean = true)`
- **Call sites updated**: 
  - Service menu items (6 references to crt/scanline/bloom properties)
  - onRender() effect management (18 function calls)
- **Result**: 120-line module with clean post-processing encapsulation

### Task 4: ParticleSystem
- **Feasibility**: 90% (needed parameter passing for game state, duplicated ghostAuraColor)
- **Functions moved**: 12 (updateParticles, emitBurst, emitDotParticles, emitPowerPelletParticles, emitGhostEatenParticles, emitDeathParticles, emitFruitParticles, emitPacTrail, emitFrightenedTrail, emitAmbientDust, emitLevelWinConfetti, renderParticles)
- **State moved**: 6 variables (particles list, dustEmitAccumulator, frightenedParticleTrailEnabled, ambientDustEnabled, enhancedGhostExplosionsEnabled, levelWinConfettiEnabled)
- **Constants moved**: 1 (MAX_PARTICLES to companion object)
- **Helper function extracted**: ghostAuraColor() — duplicated in both ParticleSystem (for particle colors) and PacmanGame (for lighting setup)
- **Key challenge**: Trail emission functions needed game state (pacPixelX/Y, phase, frightenedTimer) — solved by passing as parameters
- **Signature changes**:
  - `emitPacTrail(x: Float, y: Float, phase: GamePhase, frightenedTimer: Float)`
  - `emitFrightenedTrail(x: Float, y: Float, phase: GamePhase, frightenedTimer: Float)`
  - `emitAmbientDust(dt: Float, phase: GamePhase)`
  - `emitLevelWinConfetti(x: Float, y: Float)` — changed from reading pacPixelX/Y internally to accepting coordinates
- **Call sites updated**:
  - Service menu items (4 references to particle toggle properties)
  - Game loop emission calls (9 function calls with new parameters)
  - Reset calls (3 `particles.clear()` → `particleSystem.reset()`)
- **Result**: 235-line module with complete particle system encapsulation

## Pattern Refinement

### Handling Shared Helper Functions
When a helper function is used by both the extracted manager and the main game:
- **Option A**: Duplicate the function if it's pure logic (e.g., ghostAuraColor for color mapping)
- **Option B**: Pass as lambda/callback if it has dependencies
- **Decision criteria**: If function is <10 lines and stateless → duplicate; otherwise → callback

### Parameter Passing for Context
Extracted managers should NOT hold references back to PacmanGame. Instead:
- Pass required game state as function parameters
- Example: `emitPacTrail(x, y, phase, frightenedTimer)` instead of `emitPacTrail()` that reads from game
- Benefit: Managers become testable in isolation

### Task 5: FruitManager
- **Feasibility**: 95% (clean boundaries, all dependencies already extracted)
- **Functions moved**: 4 (maybeSpawnFruit, spawnFruit, updateFruit, checkFruitCollision)
- **State moved**: activeFruit, fruitSpawn70Done, fruitSpawn170Done, fruitTypeCycle
- **State kept in PacmanGame**: `dotsEatenThisLevel` — incremented in `eatDotAt()` (dot eating logic), not fruit logic
- **Signature changes**:
  - `maybeSpawnFruit(dotsEaten: Int, level: Int)` — accepts both counters as parameters
  - `checkFruitCollision(pacGridX: Int, pacGridY: Int): Boolean` — accepts pac position, returns collision result
  - `spawnFruit(level: Int)` — private, accepts level for fruit type selection
- **activeFruit visibility**: `var activeFruit: FruitState? = null` with `private set` — public read for rendering
- **reset()**: Added `reset()` function called from `startLevelState()` to clear all fruit state
- **fruitTypeCycle**: Moved entirely to FruitManager (was only used in `spawnFruit`)
- **ast_grep_replace gotcha**: Tool reported replacements but didn't apply them — had to use Edit tool manually
- **Result**: 75-line module with complete fruit lifecycle management

### Task 6: PacmanController
- **Feasibility**: 80% (trickiest extraction — state read by many systems)
- **Functions moved**: 6 (updatePacman, updateAttractPacmanControl, scoreAttractDirection, nearestDotDistance, updateMouthAnimation, pixelX/pixelY)
- **State moved**: 7 variables (gridX, gridY, dir, nextDir, progress, mouthAngle, mouthOpening)
- **Constructor params**: `pacSpeed: Float, gameSpeedScale: Float` — passed at construction time
- **Property ordering critical**: `gameSpeedScale` and `pacSpeed` must be declared BEFORE `pacman` in PacmanGame class (Kotlin initializes properties in order)
- **Callback pattern for dot eating**: `updatePacman()` calls `eatDotAt()` and `fruitManager.checkFruitCollision()` — solved with lambda callbacks:
  ```kotlin
  fun updatePacman(dt: Float, onDotEaten: (Int, Int) -> Unit, onFruitCheck: (Int, Int) -> Unit)
  ```
- **Return type mismatch**: `fruitManager.checkFruitCollision()` returns `Boolean`, not `Unit` — used lambda wrapper at call site: `{ col, row -> fruitManager.checkFruitCollision(col, row) }`
- **Ghost list for AI**: `updateAttractPacmanControl()` needs ghost list — passed as parameter, thin wrapper kept in PacmanGame
- **State visibility**: All 7 state vars are public with `private set` (read by ghost AI, collision, rendering, lighting)
- **nextDir exception**: `nextDir` is fully public (written by keyboard input in PacmanGame)
- **reset()**: Added `reset(startX, startY, startDir)` with defaults from `Maze.PAC_START_*`
- **References updated**: 20 occurrences across getGhostTarget, checkCollisions, renderPacman, renderEntityBloomHalos, syncSceneLights
- **Result**: 145-line module with complete pac-man movement and animation encapsulation

### Task 7: GhostAISystem
- **Feasibility**: 80% (highest risk — frightenedTimer used across 5+ systems, ghosts list read everywhere)
- **Functions moved**: 12 (update, updateGhostModes, updateGhosts, chooseGhostDirection, nextGhostStepTowards, getGhostTarget, distSq, activateFrightened, ghostPixelX, ghostPixelY, ghostSpeedForLevel, frightenedDurationForLevel, frightenedGhostSpeedForLevel)
- **State moved**: ghosts list, frightenedTimer, ghostModeTimer, ghostModeIndex, currentGhostMode, ghostReleaseTimers, pelletsEatenForGhostScore, eatenGhostSpeed, modeSequence
- **Constructor params**: `pacman: PacmanController, gameSpeedScale: Float` — PacmanController reference for targeting
- **Instantiation**: `private val ghostAI = GhostAISystem(pacman, gameSpeedScale)` — use `val` directly (pacman already initialized as val)
- **level parameter**: `level` changes over time, passed as parameter to `update(dt, level)`, `activateFrightened(level)`, `startLevel(level)`
- **API design**: Exposed `update(dt, level)` combining both ghost mode + ghost movement (always called together)
- **resetPositions() + startLevel(level)** are separate — startLevel sets mode state, resetPositions rebuilds ghost list (different call sites)
- **pelletsEatenForGhostScore**: Fully public `var` — written from PacmanGame.checkCollisions when ghost is eaten
- **frightenedTimer**: Public with `private set` — many systems read it, only GhostAI writes it
- **ghostPixelX/Y**: Public functions — used by rendering, lighting, particles in PacmanGame
- **frightenedTimer references updated**: 7 call sites across buildMenuItems (2), onFixedUpdate ATTRACT_DEMO (2), onFixedUpdate PLAYING (2), onRender (1), setGhostColor (2)
- **ghosts references updated**: 6 locations (updateAttractPacmanControl, checkCollisions, renderEntityBloomHalos, renderGhosts, syncSceneLights anyGhostFrightened check, syncSceneLights ghost lights loop)
- **Result**: 230-line module. Build passed first attempt with zero errors.

### Task 8: LightingManager
- **Feasibility**: 75% (most coupled extraction — 15+ state vars, 180-line syncSceneLights, 10+ service menu items)
- **Functions moved**: 11 (setupSceneLighting, addBoardBacklight, createAuraLights, createAuraLamp, createConePair, ghostAuraColor, cycleSceneBrightness, sceneBrightnessAmbient, createMazeOccluders, setLightingEnabledState, syncSceneLights)
- **State moved**: 20 variables (lightingEnabled, entityHaloEnabled, boardBacklightEnabled, auraLightsEnabled, lightingTargetMainEnabled, sceneBrightness, frightenedAmbientShiftEnabled, enhancedPacAuraEnabled, enhancedGhostLightsEnabled, nativeFogEnabled, nativeFogIntensity, fogOfWarEnabled, lightingSystem, boardBacklight, pacAuraLight, fruitAuraLight, fruitConeLights, ghostAuraLights, eatenGhostConeLights, powerPelletAuraLights, powerPelletConeLights)
- **New data classes**: LightPair (moved from PacmanGame.kt), LightingSnapshot (new — snapshot of game state for syncSceneLights)
- **MazeOccluder**: Renamed to LightingMazeOccluder in LightingManager.kt, removed from PacmanGame.kt
- **syncSceneLights() snapshot pattern**: Changed from reading global state to accepting LightingSnapshot parameter. Ghost pixel position computed by duplicating ghostPixelX/Y helpers in LightingManager (private methods)
- **LightingTargetMain**: Added setLightingTargetMain(value) method to encapsulate both state update AND lightingSystem?.targetSurfaces side effect
- **Initial sync**: setupSceneLighting() calls syncSceneLights() with a BOOT-phase snapshot — all lights turn off immediately (playfieldLightsEnabled = false for BOOT phase)
- **Service menu items updated**: 10 items (lightingEnabled, entityHaloEnabled, boardBacklightEnabled, auraLightsEnabled, sceneBrightness, lightingTargetMainEnabled, frightenedAmbientShiftEnabled, enhancedPacAuraEnabled, enhancedGhostLightsEnabled, fogOfWarEnabled, nativeFogIntensity)
- **Critical bug**: Removed `Lamp` import from PacmanGame.kt but `LightPair` still referenced `Lamp` there — caused 30+ "Unresolved reference" errors on LightPair.first/second properties. Fix: moved `LightPair` to LightingManager.kt and deleted from PacmanGame.kt
- **Result**: 460-line LightingManager.kt. Build passed after fixing import issue.
