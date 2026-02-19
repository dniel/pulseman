# Changelog

## v1.5

### Architecture
- Modularized PacmanGame.kt rendering from ~1675 lines down to ~756 lines
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
- Modularized PacmanGame.kt (2874 lines) into 8 cohesive modules
- Extracted SoundManager, ScoreManager, PostProcessingManager, ParticleSystem, FruitManager, PacmanController, GhostAISystem, LightingManager
- PacmanGame.kt reduced to ~1690 lines (41% reduction)

## v1.2

### Visual Enhancements
- Frightened mode ambient color shift in lighting
- Enhanced pac-man aura radius and intensity
- Enhanced ghost aura radius and intensity
- Death sequence light flicker effect
- Frightened mode particle trail (blue particles behind pac-man)
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
- Full Pac-Man gameplay: maze, dots, power pellets, fruit, lives, scoring
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
- Procedural ghost and pac-man rendering (no sprite sheets)

### Audio
- Sound effects for dot eating, ghost eating, death, fruit collection

### Modes
- Boot screen, attract mode with AI demo, title screen, gameplay, game over
