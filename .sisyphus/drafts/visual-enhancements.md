# Draft: Full Visual Enhancement Sweep (Tiers 1-3)

## Requirements (confirmed)
- Implement ALL tiers of visual enhancements identified in the exploration report
- Tier 1 (Trivial): Frightened ambient color shift, brighter pac-man aura, brighter ghost lights, death light flicker
- Tier 2 (Simple): Frightened particle trail, ambient floating dust, ghost-colored eat explosions, level win confetti
- Tier 3 (Medium): Fog of war via light-based darkness, volumetric fog custom shader, dynamic bloom during frightened mode

## Technical Decisions
- Single file: all changes in `PacmanGame.kt` (except fog shader which needs new files)
- Fog shader: new `FogEffect.kt` + `fog.frag` following CRT/Scanline pattern
- All enhancements should be toggleable via service menu

## Research Findings

### CRITICAL DISCOVERY: PulseEngine has NATIVE FOG support!
- `DirectLightingSystem` exposes: `fogIntensity` (0-100), `fogTurbulence` (0-100), `fogScale` (0-5)
- Currently set to `fogIntensity = 0f` (disabled)
- This means volumetric fog may NOT need a custom shader — native fog is built in!

### Particle System (lines ~1422-1508, 1883-1892, 2636-2647)
- Data class: 10 fields (x, y, vx, vy, size, life, maxLife, red, green, blue)
- `emitBurst()` at line 1439: randomized angle/speed/life/size, adds to `mutableListOf<Particle>()`
- 5 emission sites: dot eat (14 particles), power pellet (18), ghost eat (22), death (124 across 3 bursts + shards), fruit (26)
- Update: friction 0.985x, gravity 5f, size decay 0.992x
- Render: quads with alpha fade based on life/maxLife
- No pooling, no max count

### Lighting System (lines ~290-440, 1894-2050)
- Pac-man aura: yellow (1, 0.92, 0.3), radius=220f, intensity=0.9f, RADIAL/SOFT shadows
- Ghost auras: type-specific colors, radius=170f, intensity=0.65f
- Frightened mode: ghost lights change to blue (0.42, 0.58, 1.0), intensity reduced to 0.42+pulse*0.2
- NO ambient color change during frightened mode currently
- NO death light effects currently
- Ambient color: (0.01, 0.01, 0.02, 0.8) — very dark
- `syncSceneLights()` runs every frame, all lights update based on game phase

### Post-Processing Pipeline (lines ~198-282)
- Pattern: ensure/delete/update for each effect
- CRT (order=96), Scanline (order=97), Bloom (order follows)
- `BaseEffect` abstract class: override `loadShaderProgram()` + `applyEffect()`
- Shader files in `/src/main/resources/shaders/effects/`
- Vertex shader: simple pass-through (position, texCoord → uv)
- Fragment shader: sample baseTex, apply effect, output fragColor

### State Transitions
- Frightened: `activateFrightened()` sets timer, reverses ghost directions, duration = max(3f, 8f - (level-1)*0.5f)
- Death: `GamePhase.DYING`, deathAnimTimer=1.5f, pac-man shrinks, mouth opens
- Level win: `GamePhase.WON`, wonTimer=1.5f, then LEVEL_TRANSITION
- Lighting enabled only during PLAYING and ATTRACT_DEMO

## Decisions Made
- Service menu: YES, all individually toggleable (user confirmed)
- Effect interactions: YES, layer them all during frightened mode (user confirmed)

## Open Questions
- Native fog vs custom shader for volumetric fog? (PulseEngine has built-in fog!)
- Test strategy: TDD / tests-after / none?

## Scope Boundaries
- INCLUDE: All Tier 1, 2, and 3 enhancements from exploration report
- INCLUDE: Service menu toggles for each new effect
- INCLUDE: New FogEffect shader if volumetric fog approach is chosen
- EXCLUDE: Changes to game mechanics / scoring / AI
- EXCLUDE: Sound effects
- EXCLUDE: Changes outside the pacman package
