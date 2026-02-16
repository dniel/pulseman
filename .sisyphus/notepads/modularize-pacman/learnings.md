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

