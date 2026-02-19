# Agent Instructions

Project-specific instructions for AI agents working on this codebase.

## Project Overview

Pulse-Man is a Pac-Man clone built with [PulseEngine](https://github.com/niclasnilsson/PulseEngine) (Kotlin/JVM game engine). Single-package (`pulseman`), no submodules, no test suite. All source lives in `src/main/kotlin/pulseman/`.

- **Language**: Kotlin 2.3.x, JVM toolchain 23
- **Build**: Gradle 9.x (Kotlin DSL)
- **Engine**: PulseEngine 0.13.0 (from `repo.repsy.io/mvn/njoh/public`)
- **Entry point**: `pulseman.PulseManGameKt.main()`

## Build Commands

```bash
./gradlew build          # Full build (compile + jar + dist). This is the verification gate.
./gradlew compileKotlin  # Compile only (faster feedback loop)
./gradlew run            # Run the game
./gradlew clean build    # Clean rebuild
```

There is **no test suite**. Compilation is the only automated verification. Always run `./gradlew build` after changes to verify.

## Release Versioning

When bumping to the next version, always follow this three-step flow:

1. **Release** the current SNAPSHOT — remove `-SNAPSHOT` suffix from `build.gradle.kts`
2. **Tag** the release — `git tag vX.Y` and commit
3. **Bump** to the next SNAPSHOT — increment minor version and add `-SNAPSHOT`

Example: `1.6-SNAPSHOT` → `1.6` (release + tag) → `1.7-SNAPSHOT` (next dev cycle)

Never skip the release step. Every version gets a tag before moving to the next SNAPSHOT.

### Changelog

Update `CHANGELOG.md` with a new section for each release before tagging. Document changes under categories like Architecture, Fixes, Features, etc.

## Project Structure

```
src/main/kotlin/pulseman/
  PulseManGame.kt          # Main orchestrator — game loop, state machine, collision
  PulseManController.kt    # Player movement, animation, attract-mode AI
  GhostAISystem.kt         # Ghost AI — scatter/chase/frightened/eaten modes, BFS pathfinding
  Maze.kt                  # 28x28 tile grid, walkability, coordinate mapping
  MazeLayout.kt            # Multi-maze system — MazeMode, MazeLayout, 5 layouts (classic + 4 Ms.)
  LevelSpec.kt             # ROM-accurate 21-level difficulty table
  LightingManager.kt       # Dynamic lighting via PulseEngine DirectLightingSystem
  GameplayRenderer.kt      # Maze, entities, overlay rendering
  ScreenRenderer.kt        # Boot, attract, title, hi-score screens
  HUDRenderer.kt           # Score, lives, phase text overlay
  RenderUtils.kt           # Shared drawing primitives (package-level functions)
  ServiceMenuManager.kt    # In-game service menu UI
  ParticleSystem.kt        # Particle effects (death, trails, confetti)
  PostProcessingManager.kt # CRT, scanline, bloom post-processing
  ScoreManager.kt          # Score tracking and floating popups
  SoundManager.kt          # Sound loading and playback
  FruitManager.kt          # Bonus fruit spawning and collection
  CRTEffect.kt             # CRT barrel distortion shader effect
  ScanlineEffect.kt        # Scanline overlay shader effect
  Direction.kt             # Cardinal direction enum

src/main/resources/
  shaders/effects/         # Custom GLSL shaders (crt, scanline)
  pulseengine/shaders/     # PulseEngine shader overrides
  *.ogg                    # Sound effects (pulseman_*.ogg)
  *.pes                    # PulseEngine init scripts
  application.cfg          # Engine configuration
```

## Code Style

### General

- **Kotlin official style** (`kotlin.code.style=official` in gradle.properties)
- **4-space indentation**, no tabs
- All source in single package `pulseman` — no sub-packages
- One primary class per file, named to match the class

### Imports

- PulseEngine imports first: `no.njoh.pulseengine.*`
- Kotlin stdlib / Java imports after
- Wildcard imports acceptable for `kotlin.math.*` only
- No unused imports

### Naming Conventions

- **Classes**: PascalCase — `PulseManGame`, `GhostAISystem`, `ScoreManager`
- **Functions**: camelCase — `updatePulseMan()`, `syncSceneLights()`
- **Constants**: SCREAMING_SNAKE — `PULSE_START_X`, `GHOST_DOOR`, `TILE`
- **Properties**: camelCase — `frightenedTimer`, `gameSpeedScale`
- **Enum values**: SCREAMING_SNAKE — `SCATTER`, `CHASE`, `FRIGHTENED`
- **Shader/resource names**: snake_case — `pulseman_crt`, `pulseman_chomp`
- **PulseEngine scene files**: kebab-case — `pulseman-lighting.scn`

### Types and Data

- Use Kotlin primitive types (`Float`, `Int`, `Boolean`) — not boxed Java types
- Prefer `const val` for compile-time constants in companion objects or top-level
- Use `data class` for value-holding types (`GhostState`, `Particle`, `ScorePopup`)
- Use `enum class` with properties for typed constants (`Direction`, `FruitType`)
- Use `object` for singletons (`Maze`)
- Mutable state via `var` with `private set` when external read-only access is needed

### Functions

- Single-expression functions with `=` when body is one expression
- Extension functions and package-level functions for shared utilities (`RenderUtils.kt`)
- Constructor injection for dependencies — pass `PulseEngine` or specific managers
- Lambda parameters for callbacks: `::eatDotAt`, `{ col, row -> ... }`

### Error Handling

- No try/catch in game logic — fail fast during development
- Maintain type safety — no unsafe casts or suppression annotations
- Guard with early returns, not deep nesting
- Use `?: return` for nullable fallbacks (see `SoundManager.resolvePath`)

### Documentation

- **KDoc (`/** */`)** on all classes and major public functions
- Class-level KDoc describes WHAT the class does and WHY it exists
- Function-level KDoc describes behavior, not implementation
- Use `@property` tags for data class fields when semantics aren't obvious
- Use `[ClassName]` and `[methodName]` KDoc links for cross-references
- **No inline comments** (`//` style) unless explaining complex algorithms

### Rendering Patterns

- All rendering goes through PulseEngine `Surface` objects
- Colors set via `surface.setDrawColor(r, g, b, a)` with `Float` 0–1 range
- Coordinate system: pixel positions computed from grid via `Maze.centerX/Y()`
- Shared drawing primitives are package-level functions in `RenderUtils.kt`

### Game Architecture

- `PulseManGame` is the sole `PulseEngineGame` subclass — it orchestrates everything
- Game state is a `GamePhase` enum driving a state machine in `onFixedUpdate`
- Subsystems receive the engine or specific managers via constructor injection
- Lighting uses a snapshot pattern (`LightingSnapshot`) to decouple from game state
- Ghost AI uses BFS pathfinding with result caching for performance

## PulseEngine Specifics

- Engine services (SceneEditor, MetricViewer, GpuMonitor) must be registered via `engine.service.add()` in `onCreate` before init scripts run
- Console commands are bound via `.pes` scripts (`init.pes`, `init-dev.pes`)
- Post-processing effects extend `BaseEffect` with custom GLSL shaders
- Lighting uses `DirectLightingSystem` with `DirectLightOccluder` entities for shadows
- Custom shader overrides go in `src/main/resources/pulseengine/shaders/` (same path as JAR resources — classloader picks ours first)
- **Entity lifecycle**: no `removeEntity()` API — mark entities for removal with `entity.set(SceneEntity.DEAD)`. The engine cleans up DEAD-flagged entities during the next `Scene.update()` cycle. Add entities via `engine.scene.addEntity(entity)`.
- **Entity queries**: `engine.scene.forEachEntityOfType<T> { }`, `engine.scene.getAllEntitiesOfType<T>()`, `engine.scene.getEntity(id)`
- **PulseEngine source** is available at `/home/daniel/code/dniel/PulseEngine` and samples at `/home/daniel/code/dniel/PulseEngineGameTemplate` for API reference

## Common Pitfalls

- `Maze.grid` is mutable (dots get eaten) — use `Maze.reset()` between levels
- Ghost `dotsRemaining` must init to `Int.MAX_VALUE`, not `0` (prevents instant release)
- Sound files must be `.ogg` format — `SoundManager` extracts from JAR to temp files at runtime
- Uniform warnings from PulseEngine shaders are suppressed via shader overrides — don't remove the override files in `src/main/resources/pulseengine/`
- When rebuilding scene entities (e.g. maze occluders on layout change), mark old entities DEAD and create new ones — the engine handles cleanup next frame
