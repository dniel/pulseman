# Changelog

## v2.6

### Particles
- Reworked all trail particles (Pulse-Man, frightened, ghost) to scatter as independent debris instead of rigidly tracing the grid path.
- Removed directional origin offset, movement velocity bias, turn-blend smoothing, and corner arc-fill from ghost trails.
- Trail particles now spawn at entity center with higher random velocity and longer life.

## v2.5

### Particles
- Physics-driven death burst: 144 particles across 4 color layers bounce off maze walls via PulseEngine physics engine.
- Physics-driven burst effects for ghost eaten, power pellet, and fruit collection events (same 4-layer structure as death burst, color-matched to each event).
- Fixed ghost trail color contamination of death burst by clearing trail particles before emission.

### Fixes
- Fixed PhysicsSystem and EntityUpdater being silently destroyed during scene creation (systems registered before `createEmptyAndSetActive` were lost).
- Fixed PhysicsWallCollider invisible to spatial queries (`DISCOVERABLE` flag was unset, preventing wall collision detection).
- Fixed fat JAR shader conflict: changed `duplicatesStrategy` from INCLUDE to EXCLUDE so project shader overrides take precedence over engine defaults (fixes broken GI rendering on Mesa/Intel/AMD drivers).

## v2.4

### Lighting
- Added a fast white aura pulse for eaten ghosts while returning to the ghost house.
- Increased power-pellet aura radius/intensity for stronger pickup readability.
- Aligned ghost aura/rim reach with Pulse-Man reach in GI lighting.

### Build
- Set project version to `2.4` for release.

## v2.3

### Lighting
- Increased power-pellet GI aura strength and radius to improve pickup readability.
- Added a fast white aura pulse for eaten ghosts while they return to the ghost house.

### Build
- Set project version to `2.3` for release.

## v2.2

### Architecture
- Replaced DirectLightingSystem runtime with a GlobalIlluminationSystem-first lighting pipeline.
- Reworked lighting orchestration around GI setup/sync/scaling and maze occluder integration.
- Expanded `AGENTS.md` with a comprehensive PulseEngine architecture and API reference.

### Lighting
- Reimplemented ghost lighting with GI-backed aura and rim lights driven by per-frame ghost state snapshots.
- Removed spinning cone lights and cone-pair runtime updates from gameplay lighting.
- Added GI-aware color grading and source scaling tuned for game-entity auras.

### Visual
- Removed legacy renderer bloom-halo pass now superseded by GI lighting.
- Tuned core entity color/lighting behavior for better contrast with the GI pipeline.

### Build
- Set project version to `2.2` for release.

## v2.1

### Architecture
- Removed DirectLightingSystem support and switched to GlobalIlluminationSystem-only runtime.
- Simplified LightingManager to GI-only setup/sync/teardown and GI occluder flow.
- Removed direct-light mode switching and direct-only service menu controls.

### Lighting
- Removed all ghost-emitted lighting (aura, rim, and cone lights); ghosts no longer act as light sources.
- Kept Pulse-Man, fruit, board, and power-pellet lighting in GI mode.
- Updated attract demo to keep lighting enabled during demo gameplay.

### UI
- Improved slider readability by displaying values with two decimals.
- Increased aura bloom threshold slider adjustment step so changes are visibly reflected.

### Build
- Set project version to `2.1` for release.

## v2.0

### Multi-Maze Mode
- Ms. Pulse-Man mode with four unique maze layouts (Pink, Blue, Orange, Dark Blue)
- MazeMode enum (CLASSIC / MS_PULSEMAN) toggleable from service menu
- Ms. Pulse-Man maze rotation: maze 1 (L1-2), 2 (L3-5), 3 (L6-9), 4 (L10-13), then 3↔4
- Each layout has distinct wall colors and tunnel configuration
- All layouts share identical ghost house region for AI compatibility

### Architecture
- MazeLayout.kt — layout data model with ASCII grid, tunnel rows, wall color
- Maze.loadLayout() replaces hardcoded grid with dynamic layout loading
- LightingManager.refreshMazeGeometry() rebuilds occluders and pellet lights on layout change
- Extracted createPowerPelletLights() for independent pellet light lifecycle

### Fixes
- Lighting occluders now rebuild when maze layout changes (shadows match visible walls)
- Player start position corrected in MS_1_PINK and MS_4_DARK_BLUE layouts

## v1.7

### Level Progression
- Added ROM-accurate 21-level difficulty table (LevelSpec.kt) sourced from arcade dossier
- Per-level Pulse-Man speed (normal and frightened) replaces fixed speed
- Per-level ghost speed (normal, frightened, tunnel) replaces formula approximation
- Per-level Elroy mode thresholds and speeds for Blinky
- Per-level frightened duration with authentic non-monotonic pattern (0s on levels 17/19-21+)
- Per-level frightened ghost flash count (5 or 3 flashes)
- Three scatter/chase timing patterns matching arcade groups (level 1, 2-4, 5+)
- ROM-accurate fruit sequence (Key persists from level 13+, no longer cycles)
- ROM-accurate ghost release dot counters per level

### Build
- Kotlin 2.2.20 → 2.3.10
- Gradle 8.13 → 9.3.1

### Documentation
- Added comprehensive AGENTS.md with build, style, and release instructions

## v1.6

### Refactoring
- Renamed all legacy references to Pulse-Man (package, classes, files, sound assets, display strings)
- Renamed package, game class, and controller class to `pulseman`/`PulseManGame`/`PulseManController`
- Renamed sound files to `pulseman_*.ogg`

### Fixes
- Fixed unused `resolution` uniform warning in CRT shader
- Override PulseEngine lighting shader to fix `edgeCount`/`ambientColor` uniform warnings
- Added SceneEditor, MetricViewer, GpuMonitor services to engine (F2/F3/F4 dev tools)

## v1.5

### Architecture
- Modularized PulseManGame.kt rendering from ~1675 lines down to ~756 lines
- Extracted RenderUtils.kt — shared drawing primitives (circles, ghost bodies, glow text)
- Extracted GameplayRenderer.kt — maze, entity, and overlay rendering
- Extracted ScreenRenderer.kt — boot, attract, title, and hi-score screen rendering
- Extracted HUDRenderer.kt — score, lives, and phase text overlay
- Extracted ServiceMenuManager.kt — service menu UI, input handling, and menu item types

### Documentation
- Added comprehensive KDoc to all classes and major functions across the codebase
- GhostAISystem: documented ghost modes, release rules, Elroy mode, tunnel slowdown, BFS caching, scatter/chase wave timing
- Maze: documented tile types, coordinate system, tunnel row encoding, BFS flood-fill initialization
- LightingManager: documented light layers, ambient color priority chain, occluder system
- All enums and data classes: documented game phases, ghost modes, ghost personalities, fruit types, state objects

## v1.4

### AI Improvements
- Elroy mode: Blinky speeds up when few dots remain (+5% at 20 dots, +15% at 10 dots)
- Tunnel slowdown: ghosts move at 60% speed in tunnel corridors
- Dot-counter ghost release: ghosts now release on dot thresholds in addition to timers
- BFS path caching for eaten ghosts: pathfinding runs once per tile, not every frame
- Attract mode AI: precomputed multi-source BFS distance grid replaces brute-force dot search
- Attract mode AI: 3-step greedy lookahead prevents dead-end traps

## v1.3

### Architecture
- Modularized PulseManGame.kt (2874 lines) into 8 cohesive modules
- Extracted SoundManager, ScoreManager, PostProcessingManager, ParticleSystem, FruitManager, PulseManController, GhostAISystem, LightingManager
- PulseManGame.kt reduced to ~1690 lines (41% reduction)

## v1.2

### Visual Enhancements
- Frightened mode ambient color shift in lighting
- Enhanced pulse-man aura radius and intensity
- Enhanced ghost aura radius and intensity
- Death sequence light flicker effect
- Frightened mode particle trail (blue particles behind pulse-man)
- Ambient floating dust particles
- Ghost-colored eat explosions (match ghost type color)
- Level win confetti effect (5-color burst)
- Native PulseEngine fog system integration
- Fog of war darkness mode
- Dynamic bloom boost during frightened mode

## v1.1

### UI and Controls
- Service menu overlay replacing debug keyboard shortcuts
- Boot video test hold (Key.T)

### Lighting
- Normalized cone light intensities (0.5 base + 0.3 pulse)
- Increased spinning cone light intensities
- Default light target set to main surface

### Fixes
- Removed dual-surface post-processing that caused 2x CRT curvature
- Restored Key.D as RIGHT movement alias

## v1.0

### Core Game
- Full Pulse-Man gameplay: maze, dots, power pellets, fruit, lives, scoring
- 4 ghost personalities with unique targeting (Blinky, Pinky, Inky, Clyde)
- Scatter/chase/frightened/eaten ghost modes with authentic timing
- Grid-based movement with turn queueing
- Level progression with increasing difficulty
- High score persistence

### Visual
- CRT barrel distortion shader with vignette and edge masking
- Horizontal scanline shader
- Bloom post-processing
- Dynamic lighting system with per-entity aura lights and shadow occluders
- Procedural ghost and pulse-man rendering (no sprite sheets)

### Audio
- Sound effects for dot eating, ghost eating, death, fruit collection

### Modes
- Boot screen, attract mode with AI demo, title screen, gameplay, game over
