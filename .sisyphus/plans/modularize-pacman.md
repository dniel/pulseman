# Plan: Modularize PacmanGame.kt

**Goal**: Extract 8 cohesive modules from the monolithic 2874-line PacmanGame.kt, following SOLID principles, separation of concerns, high cohesion, and low coupling. PacmanGame becomes a thin orchestrator (~1200 lines).

**Strategy**: Extract easiest/most isolated modules first. Each extraction is atomic — build must pass after every task. Phased approach minimizes risk.

**Verification**: `./gradlew build` after every task. No test suite exists, so compilation is the verification gate.

---

## Phase 1: Foundation Extractions (Zero-Dependency Modules)

---

- [x] 1. Extract SoundManager

  **What to do**:
  1. Create `src/main/kotlin/pacman/SoundManager.kt`
  2. Move these functions from PacmanGame.kt:
     - `loadGameSounds()` (line 1440)
     - `loadSoundAsset(name: String)` (line 1452)
     - `resolveSoundPath(filename: String)` (line 1458)
     - `tryPlaySound(name: String)` (line 1722)
  3. Class takes `engine: PulseEngine` as constructor parameter
  4. In PacmanGame: add `private val soundManager = SoundManager(engine)`
  5. Replace all `tryPlaySound("...")` calls with `soundManager.play("...")`
  6. Replace `loadGameSounds()` call in `onCreate()` with `soundManager.loadAll()`

  **Must NOT do**:
  - Do NOT change sound file names or paths
  - Do NOT change when sounds are triggered

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Acceptance Criteria**:
  - [ ] `SoundManager.kt` created with 4 functions
  - [ ] All `tryPlaySound` calls in PacmanGame replaced
  - [ ] `./gradlew build` passes

  **Commit**: YES
  - Message: `refactor: extract SoundManager from PacmanGame`
  - Files: `SoundManager.kt`, `PacmanGame.kt`

---

- [x] 2. Extract ScoreManager

  **What to do**:
  1. Create `src/main/kotlin/pacman/ScoreManager.kt`
  2. Move these functions from PacmanGame.kt:
     - `addScore(amount: Int)` (line 1713)
     - `addScorePopup(x: Float, y: Float, text: String)` (line 1718)
     - `updateScorePopups(dt: Float)` (line 1500)
     - `renderScorePopups(s: Surface)` (line 2045)
  3. Move these state variables:
     - `score`, `highScore` (lines 38-39)
     - `scorePopups: MutableList<ScorePopup>` (line 41)
  4. Keep `lives` and `level` in PacmanGame (they're game phase state, not score state)
  5. Class is standalone — no constructor dependencies
  6. In PacmanGame: add `private val scoreManager = ScoreManager()`
  7. Replace direct `score`/`highScore` reads with `scoreManager.score`/`scoreManager.highScore`
  8. Replace `addScore()` calls with `scoreManager.addScore()`
  9. Replace `addScorePopup()` calls with `scoreManager.addScorePopup()`

  **Must NOT do**:
  - Do NOT move `lives` or `level` — those are game phase state
  - Do NOT change score calculation logic
  - Do NOT change high score persistence (if any)

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Acceptance Criteria**:
  - [ ] `ScoreManager.kt` created with 4 functions + score/highScore/scorePopups state
  - [ ] All score-related calls in PacmanGame use scoreManager
  - [ ] `./gradlew build` passes

  **Commit**: YES
  - Message: `refactor: extract ScoreManager from PacmanGame`
  - Files: `ScoreManager.kt`, `PacmanGame.kt`

---

- [x] 3. Extract PostProcessingManager

  **What to do**:
  1. Create `src/main/kotlin/pacman/PostProcessingManager.kt`
  2. Move these functions from PacmanGame.kt:
     - `configurePostEffects()` (line 197)
     - `ensureCRTEffects()` (line 210), `deleteCRTEffects()` (line 217)
     - `ensureScanlineEffects()` (line 221), `deleteScanlineEffects()` (line 228)
     - `ensureBloomEffects()` (line 232), `deleteBloomEffects()` (line 262)
     - `updateCRTEffectSettings()` (line 273)
     - `updateScanlineEffectSettings()` (line 291)
     - `updateBloomEffectSettings()` (line 246)
     - `hasMainCrtEffect()` (line 297), `hasMainScanlineEffect()` (line 299), `hasMainBloomEffect()` (line 301)
  3. Move these state variables:
     - `crtEnabled`, `crtStrength` (lines 61-62)
     - `scanlineEnabled`, `scanlineStrength` (lines 63-64)
     - `bloomEnabled`, `bloomStrength` (lines 65-66)
  4. Class takes `engine: PulseEngine` as constructor parameter
  5. `updateBloomEffectSettings()` needs `frightenedTimer` and `dynamicFrightenedBloomEnabled` — pass these as parameters: `fun updateBloom(frightenedTimer: Float, dynamicBoostEnabled: Boolean)`
  6. Move companion object constants: `CRT_EFFECT_NAME`, `SCANLINE_EFFECT_NAME`, `BLOOM_EFFECT_NAME` to PostProcessingManager
  7. In PacmanGame: add `private val postProcessing = PostProcessingManager(engine)`
  8. Update service menu items to reference `postProcessing.crtEnabled` etc.

  **Must NOT do**:
  - Do NOT change CRTEffect.kt or ScanlineEffect.kt
  - Do NOT change effect parameter values
  - Do NOT move `dynamicFrightenedBloomEnabled` — it's a visual effects toggle

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
  - **Skills**: `[]`

  **Acceptance Criteria**:
  - [ ] `PostProcessingManager.kt` created with 13 functions + CRT/scanline/bloom state
  - [ ] Effect constants moved to PostProcessingManager companion object
  - [ ] Service menu items reference postProcessing properties
  - [ ] `./gradlew build` passes

  **Commit**: YES
  - Message: `refactor: extract PostProcessingManager from PacmanGame`
  - Files: `PostProcessingManager.kt`, `PacmanGame.kt`

---

## Phase 2: Game Entity Extractions

---

- [x] 4. Extract ParticleSystem

  **What to do**:
  1. Create `src/main/kotlin/pacman/ParticleSystem.kt`
  2. Move these functions from PacmanGame.kt:
     - `updateParticles(dt: Float)` (line 1510)
     - `emitBurst(...)` (line 1528)
     - `emitDotParticles(x, y)` (line 1562)
     - `emitPowerPelletParticles(x, y)` (line 1566)
     - `emitGhostEatenParticles(x, y, ghostType)` (line 1570)
     - `emitDeathParticles(x, y)` (line 1579)
     - `emitFruitParticles(x, y, type)` (line 1605)
     - `emitPacTrail()` (line 1628) — change signature to accept (x, y, phase, frightenedTimer)
     - `emitFrightenedTrail()` (line 1643) — change signature to accept (x, y, phase, frightenedTimer)
     - `emitAmbientDust(dt)` (line 1660) — change signature to accept (dt, phase)
     - `emitLevelWinConfetti()` (line 1684) — change signature to accept (x, y)
     - `renderParticles(s: Surface)` (line 2053)
  3. Move these state variables:
     - `particles: MutableList<Particle>` (line 36)
     - `dustEmitAccumulator` (line 59)
  4. Move toggle state:
     - `frightenedParticleTrailEnabled` (line 80)
     - `ambientDustEnabled` (line 81)
     - `enhancedGhostExplosionsEnabled` (line 82)
     - `levelWinConfettiEnabled` (line 83)
  5. Move `MAX_PARTICLES` constant to ParticleSystem companion object
  6. Class needs `ghostAuraColor` — pass it as a lambda `(GhostType) -> Color` or extract the function too
  7. In PacmanGame: add `private val particleSystem = ParticleSystem()`
  8. Update all call sites to use `particleSystem.emitXxx(...)`

  **Must NOT do**:
  - Do NOT change the Particle data class
  - Do NOT change particle colors or parameters
  - Do NOT change when particles are emitted (keep call sites in same game logic locations)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
  - **Skills**: `[]`

  **Acceptance Criteria**:
  - [ ] `ParticleSystem.kt` created with 12 functions + particle state + toggles
  - [ ] All particle emission/update/render calls use particleSystem
  - [ ] `./gradlew build` passes

  **Commit**: YES
  - Message: `refactor: extract ParticleSystem from PacmanGame`
  - Files: `ParticleSystem.kt`, `PacmanGame.kt`

---

- [x] 5. Extract FruitManager

  **What to do**:
  1. Create `src/main/kotlin/pacman/FruitManager.kt`
  2. Move these functions from PacmanGame.kt:
     - `maybeSpawnFruit()` (line 1190)
     - `spawnFruit()` (line 1202)
     - `updateFruit(dt: Float)` (line 1213)
     - `checkFruitCollision()` (line 1221) — change to accept pacGridX/Y, return collision info
  3. Move these state variables:
     - `activeFruit: FruitState?` (line 107)
     - `dotsEatenThisLevel` (find near fruit logic)
     - `fruitSpawn70Done`, `fruitSpawn170Done` (find near fruit logic)
  4. Class needs:
     - `scoreManager: ScoreManager` (for addScore on fruit eat)
     - `particleSystem: ParticleSystem` (for emitFruitParticles)
     - `soundManager: SoundManager` (for tryPlaySound on fruit eat)
  5. Fruit rendering (`renderFruit()`) stays in PacmanGame for now — it reads `activeFruit` from FruitManager
  6. In PacmanGame: add `private val fruitManager = FruitManager(scoreManager, particleSystem, soundManager)`

  **Must NOT do**:
  - Do NOT change fruit spawn timing or score values
  - Do NOT move fruit rendering yet (it's tightly coupled to the render pipeline)
  - Do NOT change FruitType or FruitState data classes

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
  - **Skills**: `[]`

  **Acceptance Criteria**:
  - [ ] `FruitManager.kt` created with 4 functions + fruit state
  - [ ] PacmanGame delegates fruit logic to fruitManager
  - [ ] `./gradlew build` passes

  **Commit**: YES
  - Message: `refactor: extract FruitManager from PacmanGame`
  - Files: `FruitManager.kt`, `PacmanGame.kt`

---

- [ ] 6. Extract PacmanController

  **What to do**:
  1. Create `src/main/kotlin/pacman/PacmanController.kt`
  2. Move these functions from PacmanGame.kt:
     - `updatePacman(dt: Float)` (line 1067)
     - `updateAttractPacmanControl()` (line 1097)
     - `scoreAttractDirection(direction, choiceCount)` (line 1110)
     - `nearestDotDistance(startCol, startRow)` (line 1151)
     - `updateMouthAnimation(dt: Float)` (line 1483)
     - `pacPixelX()` (line 1704), `pacPixelY()` (line 1705)
  3. Move these state variables:
     - `pacGridX`, `pacGridY` (lines 26-27)
     - `pacDir`, `pacNextDir` (lines 28-29)
     - `pacProgress` (line 30)
     - `mouthAngle`, `mouthOpening` (lines 31-32)
  4. Pac-man movement needs maze walkability — pass via lambda `canMove: (Int, Int) -> Boolean` or accept Maze directly
  5. Attract mode AI needs dot locations — pass Maze reference
  6. In PacmanGame: add `private val pacman = PacmanController()`
  7. Replace all `pacGridX` reads with `pacman.gridX`, etc.
  8. Pac-man rendering stays in PacmanGame (reads position from controller)

  **Must NOT do**:
  - Do NOT change movement speed calculations
  - Do NOT change maze collision logic
  - Do NOT move pac-man rendering

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
  - **Skills**: `[]`

  **Acceptance Criteria**:
  - [ ] `PacmanController.kt` created with 6 functions + position/direction state
  - [ ] PacmanGame reads position from pacman controller
  - [ ] `./gradlew build` passes

  **Commit**: YES
  - Message: `refactor: extract PacmanController from PacmanGame`
  - Files: `PacmanController.kt`, `PacmanGame.kt`

---

## Phase 3: Complex System Extractions

---

- [ ] 7. Extract GhostAISystem

  **What to do**:
  1. Create `src/main/kotlin/pacman/GhostAISystem.kt`
  2. Move these functions from PacmanGame.kt:
     - `updateGhosts(dt: Float)` (line 1271)
     - `updateGhostModes(dt: Float)` (line 1244)
     - `chooseGhostDirection(ghost)` (line 1311)
     - `getGhostTarget(ghost)` (line 1375)
     - `nextGhostStepTowards(...)` (line 1342)
     - `distSq(...)` (line 1398)
     - `activateFrightened()` (line 1232)
     - `ghostSpeedForLevel()` (line 1709), `frightenedGhostSpeedForLevel()` (line 1711)
     - `ghostPixelX(g)` (line 1706), `ghostPixelY(g)` (line 1707)
  3. Move these state variables:
     - `ghosts: MutableList<GhostState>` (line 34)
     - `currentGhostMode`, `ghostModeIndex`, `ghostModeTimer`
     - `modeSequence`
     - `ghostReleaseTimers`
     - `pelletsEatenForGhostScore`
     - `frightenedTimer` (line 54)
  4. GhostAI needs:
     - Pac-man position (from PacmanController) — pass as interface or direct reference
     - Maze (for pathfinding) — pass as constructor parameter
     - `level` and `gameSpeedScale` — pass as parameters to update functions
  5. In PacmanGame: add `private val ghostAI = GhostAISystem(pacman)`
  6. `frightenedTimer` moves to GhostAISystem — update all reads to `ghostAI.frightenedTimer`
  7. Ghost rendering stays in PacmanGame (reads ghost state from ghostAI)

  **Must NOT do**:
  - Do NOT change ghost targeting logic
  - Do NOT change scatter/chase mode timing
  - Do NOT change ghost speed calculations
  - Do NOT move ghost rendering
  - Do NOT change GhostState or GhostMode data classes

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: `[]`

  **Acceptance Criteria**:
  - [ ] `GhostAISystem.kt` created with 10 functions + ghost state + frightenedTimer
  - [ ] PacmanGame delegates ghost logic to ghostAI
  - [ ] All frightenedTimer reads updated to ghostAI.frightenedTimer
  - [ ] `./gradlew build` passes

  **Commit**: YES
  - Message: `refactor: extract GhostAISystem from PacmanGame`
  - Files: `GhostAISystem.kt`, `PacmanGame.kt`

---

- [ ] 8. Extract LightingManager

  **What to do**:
  1. Create `src/main/kotlin/pacman/LightingManager.kt`
  2. Move these functions from PacmanGame.kt:
     - `setupSceneLighting()` (line 303)
     - `addBoardBacklight()` (line 327)
     - `createAuraLights()` (line 350)
     - `createAuraLamp(...)` (line 413)
     - `createConePair(...)` (line 432)
     - `ghostAuraColor(type)` (line 448)
     - `cycleSceneBrightness()` (line 455)
     - `sceneBrightnessAmbient()` (line 464)
     - `createMazeOccluders()` (line 747)
     - `setLightingEnabledState(enabled)` (line 944)
     - `syncSceneLights()` (line 2064) — THE BIG ONE (180+ lines)
  3. Move these state variables:
     - `lightingEnabled` (line 61 area)
     - `lightingSystem: DirectLightingSystem?`
     - `boardBacklight`, `pacAuraLight`, `fruitAuraLight`, `fruitConeLights`
     - `ghostAuraLights`, `eatenGhostConeLights`
     - `powerPelletAuraLights`, `powerPelletConeLights`
     - `entityHaloEnabled`, `boardBacklightEnabled`, `auraLightsEnabled`
     - `enhancedPacAuraEnabled`, `enhancedGhostLightsEnabled`
     - `sceneBrightness`
     - `lightingTargetMainEnabled`
     - `frightenedAmbientShiftEnabled`
     - `nativeFogIntensity`, `fogOfWarEnabled`
  4. `syncSceneLights()` needs a snapshot of game state — create a data class:
     ```kotlin
     data class LightingSnapshot(
         val phase: GamePhase,
         val pacX: Float, val pacY: Float,
         val ghosts: List<GhostState>,
         val fruit: FruitState?,
         val frightenedTimer: Float,
         val deathAnimTimer: Float,
         val uiPulseTime: Float,
     )
     ```
  5. Change `syncSceneLights()` to accept `LightingSnapshot` instead of reading global state
  6. Class takes `engine: PulseEngine` as constructor parameter
  7. In PacmanGame: add `private val lighting = LightingManager(engine)`
  8. Update service menu items to reference `lighting.sceneBrightness` etc.

  **Must NOT do**:
  - Do NOT change light colors, intensities, or positions
  - Do NOT change fog behavior
  - Do NOT change ambient color logic
  - Do NOT simplify syncSceneLights — just move it as-is with snapshot parameter
  - Do NOT move wall rendering toggles (wallBevelEnabled etc.) — those are rendering concerns

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: `[]`

  **Acceptance Criteria**:
  - [ ] `LightingManager.kt` created with 11 functions + all lighting state
  - [ ] `LightingSnapshot` data class defined
  - [ ] `syncSceneLights()` accepts snapshot instead of reading globals
  - [ ] Service menu items reference lighting properties
  - [ ] `./gradlew build` passes

  **Commit**: YES
  - Message: `refactor: extract LightingManager from PacmanGame`
  - Files: `LightingManager.kt`, `PacmanGame.kt`

---

## Summary

| Task | Module | Functions | State Vars | Risk | Category |
|------|--------|-----------|------------|------|----------|
| 1 | SoundManager | 4 | 0 | Low | quick |
| 2 | ScoreManager | 4 | 3 | Low | quick |
| 3 | PostProcessingManager | 13 | 6 | Low | unspecified-low |
| 4 | ParticleSystem | 12 | 6 | Medium | unspecified-low |
| 5 | FruitManager | 4 | 4 | Medium | unspecified-low |
| 6 | PacmanController | 6 | 7 | Medium | unspecified-low |
| 7 | GhostAISystem | 10 | 7+ | High | unspecified-high |
| 8 | LightingManager | 11 | 15+ | High | unspecified-high |

**Expected outcome**: PacmanGame.kt shrinks from 2874 → ~1200 lines. 8 new cohesive modules. Each independently understandable and (future) testable.

---

## Commit Strategy

| After Task | Message |
|------------|---------|
| 1 | `refactor: extract SoundManager from PacmanGame` |
| 2 | `refactor: extract ScoreManager from PacmanGame` |
| 3 | `refactor: extract PostProcessingManager from PacmanGame` |
| 4 | `refactor: extract ParticleSystem from PacmanGame` |
| 5 | `refactor: extract FruitManager from PacmanGame` |
| 6 | `refactor: extract PacmanController from PacmanGame` |
| 7 | `refactor: extract GhostAISystem from PacmanGame` |
| 8 | `refactor: extract LightingManager from PacmanGame` |

---

## Success Criteria

### Verification Commands
```bash
./gradlew build  # Expected: BUILD SUCCESSFUL
```

### After ALL extractions
- [ ] PacmanGame.kt is ~1200 lines (orchestrator only)
- [ ] 8 new `.kt` files in `src/main/kotlin/pacman/`
- [ ] Each module has single responsibility
- [ ] No circular dependencies between modules
- [ ] All existing game behavior preserved
- [ ] `./gradlew build` passes
