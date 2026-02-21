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

## PulseEngine Architecture

PulseEngine is a 2D Kotlin/LWJGL game engine. The engine source and example games are available locally for API reference:

```
/home/daniel/code/dniel/pulse/
  PulseEngine/               # Engine source (v0.13.0)
  PulseEngineGameTemplate/   # Starter template
  botgang/                   # Bot programming top-down shooter
  shotgang/                  # Competitive couch co-op shooter
  caesars-salads/            # Salad-themed game
  capra-ml-talk/             # Neural network interactive presentation
```

### Game Lifecycle

Games subclass `PulseEngineGame` and override lifecycle hooks called in this order:

1. `onCreate()` — one-time startup. Register services, create surfaces, load assets.
2. `onStart()` — fires after `onCreate`, once assets are loaded and engine is fully initialized.
3. `onFixedUpdate()` — fixed tick rate, independent of framerate. All game logic goes here.
4. `onUpdate()` — every frame. Rendering prep, animation interpolation.
5. `onRender()` — every frame. All draw calls go here.
6. `onDestroy()` — one-time shutdown.

The game holds an `engine` property (`PulseEngine` interface) providing access to all subsystems.

### Engine Subsystems

Access all subsystems through `engine.*`:

| Property | Type | Purpose |
|---|---|---|
| `engine.gfx` | `Graphics` | Surface creation, cameras, texture management |
| `engine.input` | `Input` | Keyboard, mouse, gamepad input |
| `engine.audio` | `Audio` | Sound playback and spatial audio |
| `engine.scene` | `SceneManager` | Entity/system lifecycle, queries, scene state |
| `engine.asset` | `AssetManager` | Loading textures, sounds, fonts, shaders |
| `engine.config` | `Configuration` | Engine and game configuration |
| `engine.console` | `Console` | Commands, scripts (`.pes` files), logging |
| `engine.data` | `Data` | Save/load, metrics, profiling |
| `engine.service` | `ServiceManager` | Dev tools (SceneEditor, MetricViewer, GpuMonitor) |
| `engine.window` | `Window` | Window size, title, fullscreen |
| `engine.network` | `Network` | UDP/TCP client-server networking |

### Entity System

Entities subclass `SceneEntity`. There is no component system — entities are plain classes with properties.

**Entity flags** (bitmask on `entity.flags`):
- `DEAD` — marks for removal. Engine cleans up next `Scene.update()` cycle.
- `HIDDEN` — skips rendering but entity still updates.
- `DISCOVERABLE` — visible to spatial queries from other entities.
- `POSITION_UPDATED`, `ROTATION_UPDATED`, `SIZE_UPDATED` — dirty flags for spatial grid.
- `SELECTED`, `EDITABLE` — editor integration flags.

**Lifecycle operations**:
```kotlin
// Add entity — returns assigned ID
val id = engine.scene.addEntity(entity)

// Remove entity — NO removeEntity() API. Mark DEAD instead.
entity.set(SceneEntity.DEAD)

// Parent/child hierarchy
parent.addChild(child)    // sets child.parentId
parent.removeChild(child) // clears child.parentId
```

**Entity queries** (all on `engine.scene`):
```kotlin
// By type
engine.scene.forEachEntityOfType<MyEntity> { entity -> ... }
engine.scene.getAllEntitiesOfType<MyEntity>()          // returns SceneEntityList<T>?
engine.scene.getFirstEntityOfType<MyEntity>()          // returns T?
engine.scene.getFirstEntityOfType<MyEntity> { it.active } // with predicate

// By ID
engine.scene.getEntity(id)                // returns SceneEntity?
engine.scene.getEntityOfType<MyEntity>(id) // returns T?

// Spatial queries (use engine's spatial grid)
engine.scene.forEachEntityNearby(x, y, width, height) { entity -> ... }
engine.scene.forEachEntityNearbyOfType<T>(x, y, w, h) { entity -> ... }
engine.scene.forEachEntityAtPoint(x, y) { entity -> ... }
engine.scene.forEachEntityAlongRay(x, y, angle, length, width) { entity -> ... }
engine.scene.getFirstEntityAlongRay(x, y, angle, length) // returns HitResult?
```

### Scene Systems

Systems subclass `SceneSystem` and provide their own lifecycle hooks mirroring the game:

```kotlin
class MySystem : SceneSystem() {
    override fun onCreate(engine: PulseEngine) { }
    override fun onStart(engine: PulseEngine) { }
    override fun onFixedUpdate(engine: PulseEngine) { }
    override fun onUpdate(engine: PulseEngine) { }
    override fun onRender(engine: PulseEngine) { }
    override fun onStop(engine: PulseEngine) { }
    override fun onDestroy(engine: PulseEngine) { }
    override fun onStateChanged(engine: PulseEngine) { } // called when enabled flag changes
}
```

Register and query systems:
```kotlin
engine.scene.addSystem(system)
engine.scene.removeSystem(system)
engine.scene.getSystemOfType<DirectLightingSystem>()
```

### Rendering (Surface API)

All rendering goes through `Surface` objects. The engine provides `engine.gfx.mainSurface` by default.

**Creating surfaces**:
```kotlin
engine.gfx.createSurface(
    name = "my_surface",
    width = null,           // null = match window
    height = null,
    zOrder = null,          // draw order (lower = earlier)
    camera = engine.gfx.mainCamera,
    isVisible = true,       // false = off-screen render target
    textureFormat = RGBA16F,
    textureFilter = LINEAR,
    textureScale = 1f,
    multisampling = NONE,
    blendFunction = BlendFunction.NORMAL,
    backgroundColor = Color.BLANK
)
engine.gfx.getSurface("name")           // returns Surface?
engine.gfx.getSurfaceOrDefault("name")  // falls back to mainSurface
engine.gfx.deleteSurface("name")
```

**Drawing primitives** (all on a `Surface` instance):
```kotlin
surface.setDrawColor(r, g, b, a)  // Float 0–1, returns Surface for chaining
surface.drawLine(x0, y0, x1, y1)
surface.drawQuad(x, y, width, height)
surface.drawTexture(texture, x, y, w, h, angle, xOrigin, yOrigin, cornerRadius)
surface.drawText(text, x, y, font, fontSize, angle, xOrigin, yOrigin)
surface.drawWithin(x, y, w, h) { /* stencil-clipped drawing */ }
```

**Text rendering** supports a `TextBuilder` lambda to avoid String allocations:
```kotlin
surface.drawText(text = { "Score: " plus score }, x, y, font, fontSize)
```

**Camera** access: `engine.gfx.mainCamera` or `surface.camera`. Supports `screenPosToWorldPos()`.

### Input

```kotlin
// Keyboard
engine.input.isPressed(Key.W)      // held down
engine.input.wasClicked(Key.SPACE) // pressed this frame only
engine.input.wasReleased(Key.ESC)  // released this frame only
engine.input.clickedKeys           // List<Key> of keys pressed this frame
engine.input.textInput             // String typed this frame (for text fields)
engine.input.setOnKeyPressed { key -> ... } // returns Subscription

// Mouse
engine.input.isPressed(MouseButton.LEFT)
engine.input.wasClicked(MouseButton.LEFT)
engine.input.xMouse, engine.input.yMouse         // screen space
engine.input.xWorldMouse, engine.input.yWorldMouse // world space
engine.input.xdMouse, engine.input.ydMouse       // delta since last frame
engine.input.xScroll, engine.input.yScroll        // scroll wheel delta

// Gamepads
engine.input.gamepads // List<Gamepad>

// Focus system (for UI layers / input capture)
engine.input.requestFocus(focusArea)
engine.input.acquireFocus(focusArea)
engine.input.releaseFocus(focusArea)
engine.input.hasFocus(focusArea)
```

### Audio

```kotlin
// Simple playback
engine.audio.playSound("sound_asset_name", volume = 1f, pitch = 1f, looping = false)
engine.audio.playSound(soundObject, volume, pitch, looping)

// Source-based control (for ongoing sounds)
val sourceId = engine.audio.createSource(sound, volume, pitch, looping)
engine.audio.playSource(sourceId)
engine.audio.pauseSource(sourceId)
engine.audio.stopSource(sourceId)
engine.audio.stopAllSources()
engine.audio.setSourceVolume(sourceId, volume)
engine.audio.setSourcePitch(sourceId, pitch)
engine.audio.setSourceLooping(sourceId, looping)
engine.audio.isSourcePlaying(sourceId)

// Spatial audio
engine.audio.setSourcePosition(sourceId, x, y, z)
engine.audio.setSourceReferenceDistance(sourceId, distance)
engine.audio.setListenerPosition(x, y, z)
engine.audio.setDistanceModel(model)
```

### Direct Lighting System

`DirectLightingSystem` is a `SceneSystem` that manages 2D shadow-casting lights.

**System properties**: `ambientColor`, `dithering`, `fogIntensity`, `fogTurbulence`, `fogScale`, `textureScale`, `textureFilter`, `targetSurfaces` (comma-separated surface names), `enableFXAA`, `useNormalMap`, `enableLightSpill`, `drawDebug`.

**Light sources**: entities implementing the `DirectLightSource` interface:
```kotlin
interface DirectLightSource {
    var lightColor: Color      // RGB color
    var intensity: Float       // multiplier
    var radius: Float          // max reach
    var size: Float            // emitting area size
    var coneAngle: Float       // spread in degrees (0–360)
    var spill: Float           // light bleeding into occluders (0–1)
    var type: DirectLightType  // RADIAL or LINEAR
    var shadowType: DirectShadowType // NONE or shadow-casting
    var x: Float; var y: Float; var z: Float // position
    var rotation: Float        // beam direction in degrees
}
```

**Shadow occluders**: entities implementing `DirectLightOccluder` (extends `Physical`):
```kotlin
interface DirectLightOccluder : Physical {
    val shape: Shape       // geometry for shadow edges
    var castShadows: Boolean
}
```

**Internal surfaces** created by the system: `light_surface`, `light_normal_map`, `light_occluder_map`. Light blending onto target surfaces uses `DirectLightBlendEffect` post-processing.

### Post-Processing Effects

Effects implement `PostProcessingEffect` (interface) or extend `BaseEffect` (abstract class with FBO/shader management).

**`PostProcessingEffect` interface**:
```kotlin
interface PostProcessingEffect {
    val name: String
    val order: Int  // execution order (lower = first)
    fun init(engine: PulseEngineInternal)
    fun process(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture>
    fun getTexture(index: Int): RenderTexture?
    fun destroy()
}
```

**`BaseEffect` abstract class** provides:
- `frameBuffers`, `renderers`, `programs` — managed FBO/shader resources
- `loadShaderProgram(engine)` or `loadShaderPrograms(engine)` — override to provide shaders
- `applyEffect(engine, inTextures)` — override with rendering logic
- Automatic FBO resizing when input texture dimensions change
- Cleanup via `destroy()`

**Loading shaders in effects**:
```kotlin
override fun loadShaderProgram(engine: PulseEngineInternal) = ShaderProgram.create(
    engine.asset.loadNow(VertexShader("/shaders/effects/my_effect.vert")),
    engine.asset.loadNow(FragmentShader("/shaders/effects/my_effect.frag"))
)
```

**Shader uniforms**: `program.setUniform("name", value)`, `program.setUniformSampler("name", texture)`.

**Built-in effects**: `BloomEffect` (multi-pass down/up-sample with threshold and lens dirt), `BlurEffect`, `ColorGradingEffect`, `FrostedGlassEffect`, `MultiplyEffect`, `ThresholdEffect`.

**Attaching effects to surfaces**:
```kotlin
surface.addPostProcessingEffect(effect)
surface.getPostProcessingEffect<BloomEffect>()
surface.getPostProcessingEffect("name")
surface.deletePostProcessingEffect("name")
surface.getPostProcessingEffects() // all effects
```

### Asset Management

Assets are loaded via `engine.asset`:
- `engine.asset.loadNow<T>(path)` — synchronous load (blocks until ready)
- `engine.asset.getOrNull<Texture>("name")` — retrieve previously loaded asset
- Asset types live in `core/asset/types/`: `Texture`, `Sound`, `Font`, `VertexShader`, `FragmentShader`, `Cursor`

### Services and Dev Tools

Services must be registered in `onCreate` before init scripts run:
```kotlin
engine.service.add(SceneEditor::class)
engine.service.add(MetricViewer::class)
engine.service.add(GpuMonitor::class)
```

Console commands are bound via `.pes` scripts (`init.pes`, `init-dev.pes`).

### PulseEngine Integration Notes

- Custom shader overrides go in `src/main/resources/pulseengine/shaders/` — classloader picks project resources over engine JAR resources
- Post-processing effects extend `BaseEffect` with custom GLSL shaders in `src/main/resources/shaders/effects/`
- The `BloomEffect` parameters most relevant to this game: `intensity`, `threshold`, `thresholdSoftness`, `radius`
- `TextureDescriptor` controls FBO texture properties per effect pass: `filter`, `wrapping`, `format`, `scale`

## Common Pitfalls

- `Maze.grid` is mutable (dots get eaten) — use `Maze.reset()` between levels
- Ghost `dotsRemaining` must init to `Int.MAX_VALUE`, not `0` (prevents instant release)
- Sound files must be `.ogg` format — `SoundManager` extracts from JAR to temp files at runtime
- Uniform warnings from PulseEngine shaders are suppressed via shader overrides — don't remove the override files in `src/main/resources/pulseengine/`
- When rebuilding scene entities (e.g. maze occluders on layout change), mark old entities DEAD and create new ones — the engine handles cleanup next frame
