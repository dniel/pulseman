# Visual Enhancement Sweep — Tiers 1-3

## TL;DR

> **Quick Summary**: Add 11 visual enhancements across lighting, particles, and atmosphere to make the Pac-Man game dramatically more immersive. Frightened mode becomes a layered spectacle (blue ambient + particle trail + bloom boost). Native PulseEngine fog replaces custom shader. All effects individually toggleable via service menu.
> 
> **Deliverables**:
> - 4 lighting enhancements (ambient color shift, enhanced auras, death flicker)
> - 4 particle effects (frightened trail, ambient dust, colored explosions, confetti)
> - 3 atmospheric effects (native fog, fog of war, dynamic bloom)
> - ~11 new service menu items under "Visual Effects" header
> - All effects respect game phase boundaries and clean up on state transitions
> 
> **Estimated Effort**: Large
> **Parallel Execution**: NO — sequential (single-file codebase, all changes in PacmanGame.kt)
> **Critical Path**: Task 0 → Tasks 1-4 → Tasks 5-8 → Tasks 9-11

---

## Context

### Original Request
User asked to implement ALL three tiers of visual enhancements identified in a prior exploration session: trivial lighting tweaks, particle effects, and atmospheric/fog effects for dramatic visual impact.

### Interview Summary
**Key Discussions**:
- **Service menu**: All effects individually toggleable (+10 items, +1 header)
- **Effect layering**: Frightened mode triggers ambient shift + particle trail + bloom boost simultaneously
- **Fog approach**: Use PulseEngine's native fog (fogIntensity/fogTurbulence/fogScale) — NOT a custom shader
- **Tests**: No unit tests — build verification + agent-executed QA only

**Research Findings**:
- **Particle system**: `emitBurst()` pattern with 10-field Particle data class. No pooling, no capacity limit. 5 existing emission sites.
- **Lighting**: 19 active lights via `syncSceneLights()`. Pac-man aura yellow radius=220f. Ghost auras radius=170f. Frightened → blue ghost lights. No death/won light effects.
- **Post-processing**: BaseEffect pattern. CRT(96), Scanline(97), Bloom. ensure/delete/update lifecycle.
- **Native fog API confirmed**: PulseEngine DirectLightingSystem exposes `fogIntensity` (0-100), `fogTurbulence` (0-100), `fogScale` (0-5) — confirmed via PulseEngine 0.13.0 source jar inspection. Currently unused in game (fogIntensity=0).
- **State transitions**: Lights only enabled during PLAYING/ATTRACT_DEMO phases. Death=1.5s timer. Won=1.5s timer.

### Metis Review
**Identified Gaps** (addressed):
- **Fog API validity**: Metis flagged "zero fog references in codebase" — RESOLVED: Agent confirmed API exists in PulseEngine source jar, just unused. `fogIntensity`, `fogTurbulence`, `fogScale` are `@Prop` properties on `DirectLightingSystem`.
- **syncSceneLights() phase gating**: Lights zeroed for DYING/WON phases → death flicker and confetti need light updates. RESOLVED: Task 4 explicitly extends phase gating.
- **Service menu overflow**: 30 items × 28px + header may overflow at 800p. RESOLVED: Task 0 reduces lineHeight from 28f to 22f.
- **Particle capacity**: Unbounded list + continuous emission = risk. RESOLVED: Task 0 adds MAX_PARTICLES cap (~300).
- **Three-way ambient conflict**: Brightness cycle vs frightened shift vs fog-of-war. RESOLVED: Priority chain defined (fog-of-war > frightened > brightness setting).
- **Ghost type not passed to emitGhostEatenParticles**: RESOLVED: Task 7 changes function signature.
- **State restoration on reset**: RESOLVED: Each task specifies reset additions.
- **Fog of war complexity**: Metis notes ~3x complexity of other items. RESOLVED: Kept in scope but plan provides exhaustive guidance with explicit guardrails.

---

## Work Objectives

### Core Objective
Transform the Pac-Man game from a flat-lit retro experience into a dramatically lit atmospheric game, with frightened mode as the peak visual moment and fog/darkness creating tension throughout.

### Concrete Deliverables
- Modified `PacmanGame.kt` with 11 new visual effects, ~11 new state variables, ~11 service menu items
- All effects individually toggleable, all respecting game phase boundaries
- Frightened mode layers: ambient color shift + particle trail + bloom boost simultaneously

### Definition of Done
- [ ] `./gradlew build` passes with zero errors
- [ ] All 11 effects visible during gameplay when enabled
- [ ] All 11 service menu toggles work (ON shows effect, OFF removes effect completely)
- [ ] Frightened mode activates all 3 layered effects simultaneously
- [ ] Game runs at 60fps with all effects enabled
- [ ] No visual artifacts on phase transitions (frightened end, death, level complete)

### Must Have
- Every effect toggleable independently via service menu
- Clean state restoration on phase transitions (no lingering effects)
- Particle capacity cap to prevent unbounded growth
- Phase-aware particle spawning (no spawning during BOOT/ATTRACT/GAME_OVER)
- Effects active during both PLAYING and ATTRACT_DEMO phases

### Must NOT Have (Guardrails)
- NO custom fog shader — use native PulseEngine fog only
- NO changes to game mechanics, scoring, ghost AI, or collision logic
- NO new files except this plan — all code changes in `PacmanGame.kt`
- NO modifications to Maze.kt, CRTEffect.kt, or ScanlineEffect.kt
- NO abstract particle pool classes or over-engineered particle systems — keep the simple list + cap
- NO sub-menus or nested navigation in service menu — flat list with headers
- NO smooth transitions for ambient color (snap transitions are fine for retro aesthetic)
- NO per-particle rotation, texture, or complex physics — keep existing quad+alpha model
- DO NOT move or rename existing functions — add new code alongside existing patterns
- DO NOT change existing particle emission parameters (dot eat, power pellet, etc.)

---

## Verification Strategy (MANDATORY)

> **UNIVERSAL RULE: ZERO HUMAN INTERVENTION**
>
> ALL tasks in this plan MUST be verifiable WITHOUT any human action.
> Every criterion is verified by running a command or using a tool.

### Test Decision
- **Infrastructure exists**: NO
- **Automated tests**: None
- **Framework**: N/A
- **Primary verification**: `./gradlew build` + Agent-Executed QA via Playwright screenshots

### Agent-Executed QA Scenarios (MANDATORY — ALL tasks)

Every task includes QA scenarios where the executing agent:
1. Runs `./gradlew build` to verify compilation
2. Launches the game
3. Uses Playwright to screenshot specific game states
4. Verifies visual effects are present/absent based on service menu toggles

**Evidence Directory**: `.sisyphus/evidence/`

---

## Execution Strategy

### Sequential Execution (Single File)

All changes target `PacmanGame.kt` (~2590 lines). Sequential execution prevents merge conflicts.

```
Task 0: Infrastructure Prep (particle cap, menu lineHeight, new state vars)
  ↓
Task 1: Frightened Ambient Color Shift
Task 2: Enhanced Pac-Man Aura
Task 3: Enhanced Ghost Lights
Task 4: Death Sequence Light Flicker
  ↓
Task 5: Frightened Particle Trail
Task 6: Ambient Floating Dust
Task 7: Ghost-Colored Eat Explosions
Task 8: Level Win Confetti
  ↓
Task 9: Native Fog Activation
Task 10: Fog of War / Darkness
Task 11: Dynamic Bloom During Frightened
```

### Dependency Matrix

| Task | Depends On | Blocks |
|------|------------|--------|
| 0 | None | All |
| 1 | 0 | 10, 11 |
| 2 | 0 | 10 |
| 3 | 0 | — |
| 4 | 0 | — |
| 5 | 0 | — |
| 6 | 0 | — |
| 7 | 0 | — |
| 8 | 0 | — |
| 9 | 0 | 10 |
| 10 | 1, 2, 9 | — |
| 11 | 1 | — |

### Agent Dispatch Summary

| Order | Task | Recommended Agent |
|-------|------|-------------------|
| 1 | 0 (Infrastructure) | `task(category="quick", load_skills=[], ...)` |
| 2 | 1 (Ambient Shift) | `task(category="quick", load_skills=[], ...)` |
| 3 | 2 (Pac Aura) | `task(category="quick", load_skills=[], ...)` |
| 4 | 3 (Ghost Lights) | `task(category="quick", load_skills=[], ...)` |
| 5 | 4 (Death Flicker) | `task(category="quick", load_skills=[], ...)` |
| 6 | 5 (Frightened Trail) | `task(category="quick", load_skills=[], ...)` |
| 7 | 6 (Ambient Dust) | `task(category="quick", load_skills=[], ...)` |
| 8 | 7 (Ghost Explosions) | `task(category="quick", load_skills=[], ...)` |
| 9 | 8 (Confetti) | `task(category="quick", load_skills=[], ...)` |
| 10 | 9 (Native Fog) | `task(category="quick", load_skills=[], ...)` |
| 11 | 10 (Fog of War) | `task(category="unspecified-low", load_skills=[], ...)` |
| 12 | 11 (Dynamic Bloom) | `task(category="quick", load_skills=[], ...)` |

---

## TODOs

- [x] 0. Infrastructure Prep: Particle Cap, Menu Compact, New State Variables

  **What to do**:
  1. Add `MAX_PARTICLES` constant (value `300`) in the companion object / constants area (near line ~2573 where other constants like `CRT_EFFECT_NAME` are defined)
  2. In `updateParticles()` (line ~1422): after the update loop, add a trim check:
     ```kotlin
     while (particles.size > MAX_PARTICLES) particles.removeFirst()
     ```
  3. In `renderServiceMenu()` (line ~2144): change `lineHeight` from `28f` to `22f` to fit more items. Also reduce font scale proportionally if text gets cramped.
  4. Add new state variables near existing toggles (lines ~60-76). Add ALL of these at once:
     ```kotlin
     private var frightenedAmbientShiftEnabled = true
     private var enhancedPacAuraEnabled = true
     private var enhancedGhostLightsEnabled = true
     private var frightenedParticleTrailEnabled = true
     private var ambientDustEnabled = true
     private var enhancedGhostExplosionsEnabled = true
     private var levelWinConfettiEnabled = true
     private var nativeFogEnabled = false          // OFF by default — dramatic effect
     private var nativeFogIntensity = 15f          // 0-100 range
     private var fogOfWarEnabled = false            // OFF by default — changes gameplay feel
     private var dynamicFrightenedBloomEnabled = true
     ```
  5. In `buildMenuItems()` (line ~511): add a new header and 10 toggle/slider items at the END of the existing list:
     ```kotlin
     MenuItem(MenuItemType.Header, "── Visual Effects ──", false, 0f, 0f, 0f),
     MenuItem(MenuItemType.Toggle, "Frightened Ambient Shift", frightenedAmbientShiftEnabled, ...),
     MenuItem(MenuItemType.Toggle, "Enhanced Pac Aura", enhancedPacAuraEnabled, ...),
     MenuItem(MenuItemType.Toggle, "Enhanced Ghost Lights", enhancedGhostLightsEnabled, ...),
     MenuItem(MenuItemType.Toggle, "Frightened Particle Trail", frightenedParticleTrailEnabled, ...),
     MenuItem(MenuItemType.Toggle, "Ambient Dust", ambientDustEnabled, ...),
     MenuItem(MenuItemType.Toggle, "Ghost Color Explosions", enhancedGhostExplosionsEnabled, ...),
     MenuItem(MenuItemType.Toggle, "Level Win Confetti", levelWinConfettiEnabled, ...),
     MenuItem(MenuItemType.Slider, "Native Fog", nativeFogEnabled, nativeFogIntensity, 0f, 100f),
     MenuItem(MenuItemType.Toggle, "Fog of War", fogOfWarEnabled, ...),
     MenuItem(MenuItemType.Toggle, "Frightened Bloom Boost", dynamicFrightenedBloomEnabled, ...),
     ```
     Follow the exact same MenuItem patterns used for existing items (line ~515-560). Match the constructor arguments precisely.
  6. In `handleServiceMenuInput()` (line ~673): add cases for each new menu item. Follow the exact same pattern as existing toggle/slider handlers. When a toggle is changed, update the corresponding state variable.
  7. In `resetGame()` (line ~863): do NOT reset the new state variables (they are settings, not game state — same as existing toggles like `crtEnabled`).

  **Must NOT do**:
  - Do NOT create a particle pool class or change the Particle data class
  - Do NOT add scroll logic to the service menu — just reduce line height
  - Do NOT change existing menu items or their order
  - Do NOT set fog/fogOfWar defaults to true (they are dramatic and should be opt-in)

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`
    - No special skills needed — straightforward Kotlin code additions

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential — must complete first
  - **Blocks**: All other tasks (1-11)
  - **Blocked By**: None

  **References**:

  **Pattern References**:
  - `src/main/kotlin/pacman/PacmanGame.kt:60-76` — Existing state variable declarations (follow same `private var` pattern, same indentation)
  - `src/main/kotlin/pacman/PacmanGame.kt:511-560` — `buildMenuItems()` function showing MenuItem construction pattern (Header, Toggle, Slider, Cycle types)
  - `src/main/kotlin/pacman/PacmanGame.kt:673-690` — `handleServiceMenuInput()` showing how toggle/slider changes update state variables
  - `src/main/kotlin/pacman/PacmanGame.kt:2573-2576` — Constants area where `MAX_PARTICLES` should be added
  - `src/main/kotlin/pacman/PacmanGame.kt:1422-1437` — `updateParticles()` where capacity trim goes
  - `src/main/kotlin/pacman/PacmanGame.kt:2144` — `renderServiceMenu()` where lineHeight is defined

  **API/Type References**:
  - `src/main/kotlin/pacman/PacmanGame.kt:~88-90` — `MenuItem` data class and `MenuItemType` sealed interface definitions

  **Acceptance Criteria**:

  - [ ] `./gradlew build` passes
  - [ ] 11 new state variables declared near existing toggles
  - [ ] `MAX_PARTICLES = 300` constant added
  - [ ] `updateParticles()` trims list when exceeding MAX_PARTICLES
  - [ ] `buildMenuItems()` returns list with new "Visual Effects" header + 10 items
  - [ ] `handleServiceMenuInput()` handles all new toggles/sliders
  - [ ] `renderServiceMenu()` uses lineHeight of 22f

  **Agent-Executed QA Scenarios**:

  ```
  Scenario: Build succeeds with all new infrastructure
    Tool: Bash
    Preconditions: None
    Steps:
      1. Run: ./gradlew build
      2. Assert: BUILD SUCCESSFUL in output
      3. Assert: exit code 0
    Expected Result: Clean compilation
    Evidence: Build output captured

  Scenario: Verify new state variables exist via grep
    Tool: Bash (grep)
    Preconditions: Build passes
    Steps:
      1. grep -c "frightenedAmbientShiftEnabled\|enhancedPacAuraEnabled\|enhancedGhostLightsEnabled\|frightenedParticleTrailEnabled\|ambientDustEnabled\|enhancedGhostExplosionsEnabled\|levelWinConfettiEnabled\|nativeFogEnabled\|fogOfWarEnabled\|dynamicFrightenedBloomEnabled" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: count >= 20 (each variable declared + referenced in menu)
    Expected Result: All 11 state variables present and referenced
    Evidence: grep output

  Scenario: Verify MAX_PARTICLES constant and trim logic
    Tool: Bash (grep)
    Preconditions: Build passes
    Steps:
      1. grep "MAX_PARTICLES" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: constant defined with value 300
      3. Assert: used in updateParticles() for trimming
    Expected Result: Particle cap implemented
    Evidence: grep output
  ```

  **Commit**: YES
  - Message: `feat(visual): add infrastructure for visual enhancement system`
  - Files: `src/main/kotlin/pacman/PacmanGame.kt`
  - Pre-commit: `./gradlew build`

---

- [x] 1. Frightened Mode Ambient Color Shift

  **What to do**:
  1. In `syncSceneLights()` (line ~1894): after the `playfieldLightsEnabled` check and before individual light updates, add ambient color logic:
     ```kotlin
     // Ambient color shift during frightened mode
     val anyGhostFrightened = ghosts.any { it.mode == GhostMode.FRIGHTENED }
     if (frightenedAmbientShiftEnabled && anyGhostFrightened) {
         lightingSystem?.ambientColor = Color(0.02f, 0.03f, 0.12f, 0.85f)  // Cool blue
     } else {
         lightingSystem?.ambientColor = sceneBrightnessAmbient()  // Restore normal
     }
     ```
  2. The color should be a cool deep blue — darker than normal ambient but with blue tint. The alpha controls how much ambient affects the scene.
  3. The snap transition (instant, no fade) is intentional — matches retro aesthetic.
  4. This MUST work during both PLAYING and ATTRACT_DEMO phases (both have `playfieldLightsEnabled = true`).

  **Must NOT do**:
  - Do NOT add gradual fade/lerp for the transition — snap is correct for retro feel
  - Do NOT check `frightenedTimer` directly — check ghost modes instead (more robust when timer approaches 0)
  - Do NOT modify `sceneBrightnessAmbient()` function itself

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO (sequential, same file)
  - **Blocks**: Tasks 10, 11
  - **Blocked By**: Task 0

  **References**:

  **Pattern References**:
  - `src/main/kotlin/pacman/PacmanGame.kt:1894-1927` — `syncSceneLights()` opening section where `playfieldLightsEnabled` is checked and lights are zeroed for non-gameplay phases. Insert ambient logic AFTER the early return for non-gameplay phases.
  - `src/main/kotlin/pacman/PacmanGame.kt:1964-1971` — Existing frightened mode light handling (ghost lights turn blue). Shows the pattern for mode-based visual changes.
  - `src/main/kotlin/pacman/PacmanGame.kt:290-312` — `setupSceneLighting()` where `ambientColor` is initially set via `sceneBrightnessAmbient()`. Shows the API call pattern.

  **API/Type References**:
  - `DirectLightingSystem.ambientColor: Color` — The ambient color property on the lighting system
  - `sceneBrightnessAmbient()` — Returns Color based on current SceneBrightness enum setting
  - `GhostMode.FRIGHTENED` — The ghost mode to check against

  **WHY Each Reference Matters**:
  - Line 1894-1927: This is WHERE the code goes — must insert after early return but before individual light updates
  - Line 1964-1971: Shows the existing pattern of checking ghost mode for visual changes
  - Line 290-312: Shows how `lightingSystem?.ambientColor` is set (the API call)

  **Acceptance Criteria**:
  - [ ] `./gradlew build` passes
  - [ ] `syncSceneLights()` sets ambient to cool blue when any ghost is FRIGHTENED and `frightenedAmbientShiftEnabled` is true
  - [ ] `syncSceneLights()` restores ambient to `sceneBrightnessAmbient()` when no ghost is FRIGHTENED or toggle is off
  - [ ] Ambient change is instantaneous (no lerp/fade)

  **Agent-Executed QA Scenarios**:

  ```
  Scenario: Verify ambient shift code structure
    Tool: Bash (grep)
    Preconditions: Build passes
    Steps:
      1. grep -A5 "frightenedAmbientShiftEnabled" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: conditional check for ghost FRIGHTENED mode exists
      3. Assert: Color with blue-heavy values (low R, low G, higher B) is applied
      4. Assert: sceneBrightnessAmbient() is called for the else/restore case
    Expected Result: Ambient shift logic correctly structured
    Evidence: grep output

  Scenario: Build verification
    Tool: Bash
    Steps:
      1. ./gradlew build
      2. Assert: BUILD SUCCESSFUL
    Expected Result: Clean compilation
  ```

  **Commit**: YES
  - Message: `feat(lighting): add frightened mode ambient color shift`
  - Files: `src/main/kotlin/pacman/PacmanGame.kt`
  - Pre-commit: `./gradlew build`

---

- [ ] 2. Enhanced Pac-Man Aura

  **What to do**:
  1. In `syncSceneLights()` where pac-man aura is updated (line ~1927-1934): modify the intensity and radius based on the `enhancedPacAuraEnabled` toggle:
     ```kotlin
     pacAuraLight?.apply {
         x = pacPixelX()
         y = pacPixelY()
         if (enhancedPacAuraEnabled) {
             radius = 320f       // Up from 220f
             size = 44f          // Up from 34f (proportional scale)
             intensity = if (auraLightsEnabled) 0.78f + pulse * 0.22f else 0f  // Brighter
         } else {
             radius = 220f       // Original
             size = 34f          // Original
             intensity = if (auraLightsEnabled) 0.58f + pulse * 0.32f else 0f  // Original
         }
     }
     ```
  2. The size must scale proportionally with radius (34/220 ≈ 44/320) to maintain the same visual falloff shape.
  3. The intensity boost makes pac-man's light more dominant, which also supports the "fog of war" effect in Task 10.

  **Must NOT do**:
  - Do NOT change the pac-man aura COLOR (keep yellow)
  - Do NOT change the light type (keep RADIAL) or shadow type (keep SOFT)
  - Do NOT modify `createAuraLamp()` — changes are in the per-frame update only

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocks**: Task 10
  - **Blocked By**: Task 0

  **References**:

  **Pattern References**:
  - `src/main/kotlin/pacman/PacmanGame.kt:1927-1934` — Current pac-man aura update in `syncSceneLights()`. This is the exact code to modify.
  - `src/main/kotlin/pacman/PacmanGame.kt:338-343` — `createAuraLamp()` call showing initial values (radius=220f, size=34f, intensity=0.9f). Reference for original values.

  **WHY Each Reference Matters**:
  - Line 1927-1934: The EXACT location to modify — per-frame aura update
  - Line 338-343: Shows original creation values to use for the "disabled" branch

  **Acceptance Criteria**:
  - [ ] `./gradlew build` passes
  - [ ] When `enhancedPacAuraEnabled=true`: radius=320, size=44, boosted intensity
  - [ ] When `enhancedPacAuraEnabled=false`: radius=220, size=34, original intensity
  - [ ] Size scales proportionally with radius

  **Agent-Executed QA Scenarios**:

  ```
  Scenario: Verify enhanced aura parameters
    Tool: Bash (grep)
    Steps:
      1. grep -A10 "enhancedPacAuraEnabled" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: radius = 320f when enabled
      3. Assert: size = 44f when enabled
      4. Assert: original values (220f, 34f) when disabled
    Expected Result: Conditional aura enhancement present
    Evidence: grep output
  ```

  **Commit**: YES
  - Message: `feat(lighting): enhance pac-man aura radius and intensity`
  - Files: `src/main/kotlin/pacman/PacmanGame.kt`
  - Pre-commit: `./gradlew build`

---

- [ ] 3. Enhanced Ghost Lights

  **What to do**:
  1. In `syncSceneLights()` where ghost aura lights are updated (line ~1950-1996): wrap the normal-mode intensity/radius with the `enhancedGhostLightsEnabled` check:
     ```kotlin
     GhostMode.SCATTER, GhostMode.CHASE -> {
         light.lightColor = ghostAuraColor(type)
         if (enhancedGhostLightsEnabled) {
             light.radius = 220f      // Up from 170f
             light.size = 32f         // Up from 26f (proportional)
             light.intensity = if (auraLightsEnabled) 0.85f + pulse * 0.15f else 0f
         } else {
             light.radius = 170f      // Original
             light.size = 26f         // Original
             light.intensity = if (auraLightsEnabled) 0.65f + pulse * 0.15f else 0f
         }
     }
     ```
  2. Only modify SCATTER/CHASE modes. FRIGHTENED and EATEN modes keep their existing values (they have distinct visual identity).
  3. Size scales proportionally (26/170 ≈ 32/220).

  **Must NOT do**:
  - Do NOT change FRIGHTENED mode ghost light values (blue, lower intensity)
  - Do NOT change EATEN mode ghost light values (white, spinning cones)
  - Do NOT change ghost light colors — only radius, size, intensity

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocks**: None
  - **Blocked By**: Task 0

  **References**:

  **Pattern References**:
  - `src/main/kotlin/pacman/PacmanGame.kt:1950-1996` — Ghost light update section in `syncSceneLights()`. The `when (ghost.mode)` block handles SCATTER/CHASE, FRIGHTENED, and EATEN differently.
  - `src/main/kotlin/pacman/PacmanGame.kt:362-367` — `createAuraLamp()` call for ghost lights showing initial values (radius=170f, size=26f, intensity=0.65f).
  - `src/main/kotlin/pacman/PacmanGame.kt:435-440` — `ghostAuraColor()` function showing per-ghost colors.

  **WHY Each Reference Matters**:
  - Line 1950-1996: The exact code to modify — ghost mode switch in syncSceneLights
  - Line 362-367: Original ghost light creation values for the "disabled" branch
  - Line 435-440: Shows ghost colors aren't changing, only radius/size/intensity

  **Acceptance Criteria**:
  - [ ] `./gradlew build` passes
  - [ ] SCATTER/CHASE ghost lights: radius=220, size=32, intensity=0.85 when enabled
  - [ ] SCATTER/CHASE ghost lights: radius=170, size=26, intensity=0.65 when disabled
  - [ ] FRIGHTENED and EATEN mode lights are UNCHANGED

  **Agent-Executed QA Scenarios**:

  ```
  Scenario: Verify ghost light enhancement
    Tool: Bash (grep)
    Steps:
      1. grep -B2 -A8 "enhancedGhostLightsEnabled" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: radius 220f and size 32f when enabled
      3. Assert: radius 170f and size 26f when disabled
      4. Assert: only applies in SCATTER/CHASE context
    Expected Result: Conditional ghost light enhancement
    Evidence: grep output
  ```

  **Commit**: YES
  - Message: `feat(lighting): enhance ghost aura radius and intensity`
  - Files: `src/main/kotlin/pacman/PacmanGame.kt`
  - Pre-commit: `./gradlew build`

---

- [ ] 4. Death Sequence Light Flicker

  **What to do**:
  1. In `syncSceneLights()`: extend `playfieldLightsEnabled` to include `GamePhase.DYING`:
     ```kotlin
     val playfieldLightsEnabled = phase in setOf(
         GamePhase.PLAYING,
         GamePhase.ATTRACT_DEMO,
         GamePhase.DYING,        // NEW — needed for death flicker
     )
     ```
  2. AFTER the existing pac-man aura update, add death flicker logic:
     ```kotlin
     // Death sequence light flicker
     if (phase == GamePhase.DYING) {
         val deathProgress = 1f - (deathAnimTimer / 1.5f).coerceIn(0f, 1f)  // 0→1 as death progresses
         val flicker = if ((deathAnimTimer * 12f).toInt() % 2 == 0) 0.3f else 1.0f  // Rapid on/off
         val fade = (1f - deathProgress).coerceAtLeast(0f)  // Dim as death progresses
         
         pacAuraLight?.intensity = (pacAuraLight?.intensity ?: 0f) * flicker * fade
         ghostAuraLights.values.forEach { it.intensity = (it.intensity) * flicker * fade * 0.5f }
         boardBacklight?.intensity = (boardBacklight?.intensity ?: 0f) * fade
     }
     ```
  3. The flicker rate is `12f` oscillations per second (fast strobe). The overall fade dims everything as the death animation progresses.
  4. Ghost lights dim faster (×0.5) than pac-man's light to focus attention on the dying pac-man.
  5. No toggle needed for this — it always happens during death. It's a natural consequence of the DYING phase now having lights enabled.

  **Must NOT do**:
  - Do NOT modify the death animation itself (pac-man shrink, mouth open)
  - Do NOT change `deathAnimTimer` duration (keep 1.5s)
  - Do NOT add death flicker to the service menu (it's not a toggle — it's core behavior)

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocks**: None
  - **Blocked By**: Task 0

  **References**:

  **Pattern References**:
  - `src/main/kotlin/pacman/PacmanGame.kt:1897-1900` — `playfieldLightsEnabled` set definition. Add `GamePhase.DYING` here.
  - `src/main/kotlin/pacman/PacmanGame.kt:1927-1934` — Pac-man aura update section (insert flicker AFTER this).
  - `src/main/kotlin/pacman/PacmanGame.kt:752-765` — Death phase update in `onFixedUpdate()` showing `deathAnimTimer` countdown. Reference for timer duration.
  - `src/main/kotlin/pacman/PacmanGame.kt:1659-1667` — Death rendering showing the shrink animation pattern. Reference for visual timing.

  **WHY Each Reference Matters**:
  - Line 1897-1900: Must extend this set to include DYING — without this, all lights zero during death
  - Line 1927-1934: Insert point for flicker code (after normal aura update, apply flicker multiplier)
  - Line 752-765: Shows deathAnimTimer counts down from 1.5f — needed for progress calculation
  - Line 1659-1667: Shows death visual timing pattern to sync flicker with

  **Acceptance Criteria**:
  - [ ] `./gradlew build` passes
  - [ ] `playfieldLightsEnabled` includes `GamePhase.DYING`
  - [ ] During DYING phase: lights flicker with rapid on/off pattern
  - [ ] Lights dim progressively as death animation progresses
  - [ ] Ghost lights dim faster than pac-man light

  **Agent-Executed QA Scenarios**:

  ```
  Scenario: Verify death flicker code structure
    Tool: Bash (grep)
    Steps:
      1. grep -n "GamePhase.DYING" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: DYING appears in playfieldLightsEnabled set
      3. grep -A10 "Death.*flicker\|death.*flicker\|DYING.*flicker" src/main/kotlin/pacman/PacmanGame.kt
      4. Assert: flicker logic with deathAnimTimer exists
    Expected Result: Death flicker implemented in syncSceneLights
    Evidence: grep output
  ```

  **Commit**: YES
  - Message: `feat(lighting): add death sequence light flicker effect`
  - Files: `src/main/kotlin/pacman/PacmanGame.kt`
  - Pre-commit: `./gradlew build`

---

- [ ] 5. Frightened Mode Particle Trail

  **What to do**:
  1. Create a new function `emitFrightenedTrail()` near the other emit functions (after `emitFruitParticles()`, around line ~1531):
     ```kotlin
     private fun emitFrightenedTrail() {
         if (!frightenedParticleTrailEnabled) return
         if (frightenedTimer <= 0f) return
         if (phase != GamePhase.PLAYING && phase != GamePhase.ATTRACT_DEMO) return
         
         // Emit 1-2 small particles per call behind pac-man
         val count = if (Random.nextFloat() > 0.4f) 2 else 1
         emitBurst(
             x = pacPixelX(),
             y = pacPixelY(),
             count = count,
             speedMin = 8f, speedMax = 25f,    // Slow drift
             lifeMin = 0.3f, lifeMax = 0.6f,   // Short-lived
             sizeMin = 1.2f, sizeMax = 2.4f,   // Small
             red = 0.3f, green = 0.5f, blue = 1f,  // Cool blue to match frightened color
         )
     }
     ```
  2. Call `emitFrightenedTrail()` from the pac-man movement update in `onFixedUpdate()`. The best insertion point is after pac-man position is updated (inside the PLAYING phase movement logic), approximately where `updatePacman(dt)` is called (line ~787):
     ```kotlin
     updatePacman(dt)
     emitFrightenedTrail()  // After position update, emit trail at new position
     ```
  3. Also call it during ATTRACT_DEMO (line ~715) after `updatePacman(dt)`:
     ```kotlin
     updatePacman(dt)
     emitFrightenedTrail()
     ```
  4. The trail particles are intentionally small and short-lived — they should fade quickly, leaving a subtle blue wake behind pac-man.

  **Must NOT do**:
  - Do NOT emit particles from `onRender()` — always from `onFixedUpdate()`
  - Do NOT use high particle counts (max 2 per frame = ~120/sec at 60fps, but short lifespan keeps active count ~36-72)
  - Do NOT change existing particle colors or the emitBurst function signature

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocks**: None
  - **Blocked By**: Task 0

  **References**:

  **Pattern References**:
  - `src/main/kotlin/pacman/PacmanGame.kt:1474-1531` — Existing emit functions (`emitDotParticles`, `emitPowerPelletParticles`, `emitGhostEatenParticles`, `emitDeathParticles`, `emitFruitParticles`). Follow same pattern: private function, calls `emitBurst()`.
  - `src/main/kotlin/pacman/PacmanGame.kt:1439-1471` — `emitBurst()` function signature and implementation.
  - `src/main/kotlin/pacman/PacmanGame.kt:787` — `updatePacman(dt)` call in PLAYING phase — insert `emitFrightenedTrail()` after this.
  - `src/main/kotlin/pacman/PacmanGame.kt:715` — `updatePacman(dt)` call in ATTRACT_DEMO phase.

  **WHY Each Reference Matters**:
  - Line 1474-1531: Pattern to follow for new emission function
  - Line 1439-1471: emitBurst signature — must match parameter types
  - Lines 787, 715: Exact insertion points for calling the new function

  **Acceptance Criteria**:
  - [ ] `./gradlew build` passes
  - [ ] `emitFrightenedTrail()` function exists and checks toggle + frightenedTimer + phase
  - [ ] Called after `updatePacman(dt)` in both PLAYING and ATTRACT_DEMO phases
  - [ ] Particles are blue-tinted (matching frightened mode color scheme)
  - [ ] Low count per frame (1-2) with short lifespan (0.3-0.6s)

  **Agent-Executed QA Scenarios**:

  ```
  Scenario: Verify frightened trail function
    Tool: Bash (grep)
    Steps:
      1. grep -A15 "fun emitFrightenedTrail" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: function checks frightenedParticleTrailEnabled
      3. Assert: function checks frightenedTimer > 0
      4. Assert: function checks phase (PLAYING or ATTRACT_DEMO)
      5. Assert: calls emitBurst with blue color values
      6. grep -B1 "emitFrightenedTrail()" src/main/kotlin/pacman/PacmanGame.kt
      7. Assert: called after updatePacman in at least 2 locations
    Expected Result: Trail function properly guarded and called
    Evidence: grep output
  ```

  **Commit**: YES
  - Message: `feat(particles): add frightened mode particle trail`
  - Files: `src/main/kotlin/pacman/PacmanGame.kt`
  - Pre-commit: `./gradlew build`

---

- [ ] 6. Ambient Floating Dust

  **What to do**:
  1. Create a new function `emitAmbientDust()` near the other emit functions:
     ```kotlin
     private var dustEmitAccumulator = 0f
     
     private fun emitAmbientDust(dt: Float) {
         if (!ambientDustEnabled) return
         if (phase != GamePhase.PLAYING && phase != GamePhase.ATTRACT_DEMO) return
         
         dustEmitAccumulator += dt
         val interval = 0.15f  // Emit every ~150ms
         if (dustEmitAccumulator < interval) return
         dustEmitAccumulator -= interval
         
         // Pick a random position within the maze area
         val mazePixelWidth = Maze.COLS * Maze.TILE_SIZE.toFloat()
         val mazePixelHeight = Maze.ROWS * Maze.TILE_SIZE.toFloat()
         val rx = Maze.OFFSET_X + Random.nextFloat() * mazePixelWidth
         val ry = Maze.OFFSET_Y + Random.nextFloat() * mazePixelHeight
         
         emitBurst(
             x = rx, y = ry,
             count = 1,
             speedMin = 2f, speedMax = 8f,       // Very slow drift
             lifeMin = 1.5f, lifeMax = 3.0f,     // Long-lived
             sizeMin = 0.8f, sizeMax = 1.4f,     // Tiny
             red = 0.6f, green = 0.6f, blue = 0.7f,  // Neutral gray-blue
         )
     }
     ```
  2. Add `dustEmitAccumulator` as a state variable (near particle-related vars, line ~36).
  3. Call `emitAmbientDust(dt)` from `onFixedUpdate()` in both PLAYING and ATTRACT_DEMO phases, after the main update calls.
  4. Reset `dustEmitAccumulator = 0f` in `startLevelState()` and `resetGame()`.
  5. At 1 particle per 150ms with 1.5-3s lifespan, steady state is ~10-20 dust particles. Very lightweight.

  **Must NOT do**:
  - Do NOT emit dust during BOOT, ATTRACT, DYING, WON, GAME_OVER, LEVEL_TRANSITION
  - Do NOT use gravity for dust — the existing particle gravity (5f) will make dust fall. Consider this acceptable (dust settles slowly) OR set very low vy to counteract. The simplest approach: just accept gravity — dust will drift downward, which looks natural.
  - Do NOT emit more than 1 particle per interval

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocks**: None
  - **Blocked By**: Task 0

  **References**:

  **Pattern References**:
  - `src/main/kotlin/pacman/PacmanGame.kt:1439-1471` — `emitBurst()` for the emission call
  - `src/main/kotlin/pacman/PacmanGame.kt:36` — `particles` list declaration (add `dustEmitAccumulator` nearby)
  - `src/main/kotlin/pacman/PacmanGame.kt:787-795` — PLAYING phase in onFixedUpdate — call site
  - `src/main/kotlin/pacman/PacmanGame.kt:709-723` — ATTRACT_DEMO phase in onFixedUpdate — call site
  - `src/main/kotlin/pacman/PacmanGame.kt` — `Maze.COLS`, `Maze.ROWS`, `Maze.TILE_SIZE`, `Maze.OFFSET_X`, `Maze.OFFSET_Y` — for calculating random positions within the maze bounds

  **WHY Each Reference Matters**:
  - Line 1439: emitBurst is the emission API
  - Line 36: Where to add the accumulator variable
  - Lines 787, 709: Where to call emitAmbientDust(dt)
  - Maze constants: Need to know maze bounds for random dust positions

  **Acceptance Criteria**:
  - [ ] `./gradlew build` passes
  - [ ] `emitAmbientDust()` function emits 1 tiny particle every ~150ms
  - [ ] Only emits during PLAYING and ATTRACT_DEMO phases
  - [ ] `dustEmitAccumulator` reset in resetGame/startLevelState
  - [ ] Dust particles are small (0.8-1.4), slow (2-8), long-lived (1.5-3s), gray-blue

  **Agent-Executed QA Scenarios**:

  ```
  Scenario: Verify dust emission function
    Tool: Bash (grep)
    Steps:
      1. grep -A20 "fun emitAmbientDust" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: checks ambientDustEnabled
      3. Assert: checks phase PLAYING/ATTRACT_DEMO
      4. Assert: uses accumulator pattern for rate limiting
      5. Assert: emits count=1 with small size and slow speed
    Expected Result: Dust emission properly implemented
    Evidence: grep output
  ```

  **Commit**: YES
  - Message: `feat(particles): add ambient floating dust effect`
  - Files: `src/main/kotlin/pacman/PacmanGame.kt`
  - Pre-commit: `./gradlew build`

---

- [ ] 7. Ghost-Colored Eat Explosions

  **What to do**:
  1. Change `emitGhostEatenParticles()` signature to accept ghost type:
     ```kotlin
     private fun emitGhostEatenParticles(x: Float, y: Float, ghostType: GhostType) {
         if (!enhancedGhostExplosionsEnabled) {
             // Original white explosion
             emitBurst(x, y, count = 22, speedMin = 48f, speedMax = 130f,
                 lifeMin = 0.35f, lifeMax = 0.85f, sizeMin = 2f, sizeMax = 4.8f,
                 red = 1f, green = 1f, blue = 1f)
             return
         }
         // Ghost-colored explosion
         val color = ghostAuraColor(ghostType)
         emitBurst(x, y, count = 28, speedMin = 48f, speedMax = 140f,
             lifeMin = 0.35f, lifeMax = 0.9f, sizeMin = 2f, sizeMax = 5.2f,
             red = color.red, green = color.green, blue = color.blue)
     }
     ```
  2. Update the CALL SITE (line ~1329) where `emitGhostEatenParticles` is invoked to pass the ghost type. Find where ghost eat collision is detected — the `ghost` variable should be in scope. Pass `ghost.type` or the equivalent enum.
  3. When enhanced: slightly more particles (28 vs 22), slightly bigger (5.2 vs 4.8), using the ghost's color from `ghostAuraColor()`.
  4. When disabled: exact original behavior (22 white particles).

  **Must NOT do**:
  - Do NOT change the call site logic for WHEN ghost eat happens — only change the particle emission
  - Do NOT add new parameters to `emitBurst()` itself
  - Do NOT change other emission functions (dot, power pellet, death, fruit)

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocks**: None
  - **Blocked By**: Task 0

  **References**:

  **Pattern References**:
  - `src/main/kotlin/pacman/PacmanGame.kt:1481-1484` — Current `emitGhostEatenParticles()` function (takes only x, y, uses white color). This is what gets modified.
  - `src/main/kotlin/pacman/PacmanGame.kt:1329` — Call site where `emitGhostEatenParticles` is invoked during ghost collision. Need to find the ghost variable in scope to pass its type.
  - `src/main/kotlin/pacman/PacmanGame.kt:435-440` — `ghostAuraColor(type)` function that returns Color per ghost type. Reuse this for particle colors.

  **WHY Each Reference Matters**:
  - Line 1481-1484: The function to modify — add ghostType parameter, add toggle branch
  - Line 1329: The call site to update — must pass ghost type
  - Line 435-440: The color lookup function to reuse

  **Acceptance Criteria**:
  - [ ] `./gradlew build` passes
  - [ ] `emitGhostEatenParticles` accepts ghost type parameter
  - [ ] Call site passes ghost type correctly
  - [ ] When enabled: particles use ghost-specific color, slightly larger burst
  - [ ] When disabled: original white particles, original count

  **Agent-Executed QA Scenarios**:

  ```
  Scenario: Verify ghost-colored explosions
    Tool: Bash (grep)
    Steps:
      1. grep -A15 "fun emitGhostEatenParticles" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: function signature includes ghostType parameter
      3. Assert: enhancedGhostExplosionsEnabled check exists
      4. Assert: ghostAuraColor called when enabled
      5. grep "emitGhostEatenParticles(" src/main/kotlin/pacman/PacmanGame.kt
      6. Assert: call site passes ghost type argument
    Expected Result: Ghost-colored explosion with toggle
    Evidence: grep output
  ```

  **Commit**: YES
  - Message: `feat(particles): add ghost-colored eat explosions`
  - Files: `src/main/kotlin/pacman/PacmanGame.kt`
  - Pre-commit: `./gradlew build`

---

- [ ] 8. Level Win Confetti

  **What to do**:
  1. Create a new function `emitLevelWinConfetti()`:
     ```kotlin
     private fun emitLevelWinConfetti() {
         if (!levelWinConfettiEnabled) return
         
         val cx = pacPixelX()
         val cy = pacPixelY()
         
         // Multi-colored burst from pac-man position
         val colors = listOf(
             Triple(1f, 0.2f, 0.2f),    // Red
             Triple(0.2f, 1f, 0.3f),    // Green
             Triple(0.3f, 0.5f, 1f),    // Blue
             Triple(1f, 1f, 0.2f),      // Yellow
             Triple(1f, 0.5f, 1f),      // Pink
         )
         for ((r, g, b) in colors) {
             emitBurst(cx, cy, count = 10, speedMin = 60f, speedMax = 200f,
                 lifeMin = 0.5f, lifeMax = 1.2f, sizeMin = 1.8f, sizeMax = 4f,
                 red = r, green = g, blue = b)
         }
     }
     ```
  2. Call `emitLevelWinConfetti()` ONCE when the WON phase is entered. Find where `phase = GamePhase.WON` is set (line ~748) and add the call immediately after:
     ```kotlin
     phase = GamePhase.WON
     wonTimer = 1.5f
     emitLevelWinConfetti()
     ```
  3. Total burst: 50 particles (5 colors × 10 each). These will naturally decay within 1.2s, which is within the 1.5s WON timer.
  4. Confetti during ATTRACT_DEMO: the same dot-clearing check exists (line ~720). Add confetti there too for consistency.

  **Must NOT do**:
  - Do NOT extend `playfieldLightsEnabled` to include WON (lights off during WON is fine — confetti particles render regardless since particle rendering isn't light-dependent)
  - Do NOT change `wonTimer` duration
  - Do NOT emit confetti continuously — single burst only on WON entry

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocks**: None
  - **Blocked By**: Task 0

  **References**:

  **Pattern References**:
  - `src/main/kotlin/pacman/PacmanGame.kt:746-749` — Where `phase = GamePhase.WON` is set when all dots eaten. Insert confetti call here.
  - `src/main/kotlin/pacman/PacmanGame.kt:1486-1508` — `emitDeathParticles()` as pattern for multi-burst emission (3 different colored bursts). Follow same approach but with 5 colors.
  - `src/main/kotlin/pacman/PacmanGame.kt:720` — Dot-clearing during ATTRACT_DEMO — may also trigger confetti.

  **WHY Each Reference Matters**:
  - Line 746-749: Exact insertion point for confetti call
  - Line 1486-1508: Pattern showing multi-burst emission with different colors
  - Line 720: ATTRACT_DEMO dot clear — secondary insertion point

  **Acceptance Criteria**:
  - [ ] `./gradlew build` passes
  - [ ] `emitLevelWinConfetti()` emits 50 multi-colored particles (5 colors × 10)
  - [ ] Called once when WON phase starts (not continuously)
  - [ ] Checks `levelWinConfettiEnabled` toggle
  - [ ] Particles decay within wonTimer (1.5s)

  **Agent-Executed QA Scenarios**:

  ```
  Scenario: Verify confetti function and call site
    Tool: Bash (grep)
    Steps:
      1. grep -A20 "fun emitLevelWinConfetti" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: checks levelWinConfettiEnabled
      3. Assert: 5 different colors
      4. Assert: calls emitBurst for each color
      5. grep -B2 "emitLevelWinConfetti()" src/main/kotlin/pacman/PacmanGame.kt
      6. Assert: called near phase = GamePhase.WON
    Expected Result: Confetti burst at level win
    Evidence: grep output
  ```

  **Commit**: YES
  - Message: `feat(particles): add level win confetti effect`
  - Files: `src/main/kotlin/pacman/PacmanGame.kt`
  - Pre-commit: `./gradlew build`

---

- [ ] 9. Native Fog Activation

  **What to do**:
  1. In `syncSceneLights()` or a new helper called from `syncSceneLights()`, add fog control:
     ```kotlin
     // Native fog control
     lightingSystem?.apply {
         if (nativeFogEnabled && playfieldLightsEnabled) {
             fogIntensity = nativeFogIntensity  // Controlled by service menu slider (0-100)
             fogTurbulence = 1.5f               // Animated fog movement
             fogScale = 0.3f                    // Default noise scale
         } else {
             fogIntensity = 0f                  // Disabled
         }
     }
     ```
  2. Insert this AFTER the ambient color logic (from Task 1) and BEFORE individual light updates.
  3. The `nativeFogIntensity` is controlled by the service menu slider (0-100 range, default 15).
  4. Fog automatically disabled during non-gameplay phases (when `playfieldLightsEnabled` is false).

  **Must NOT do**:
  - Do NOT create a FogEffect class or custom shader
  - Do NOT change fogTurbulence/fogScale dynamically — keep them constant
  - Do NOT set fog intensity above 30 by default (15 is subtle, 30+ is very heavy)

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocks**: Task 10
  - **Blocked By**: Task 0

  **References**:

  **Pattern References**:
  - `src/main/kotlin/pacman/PacmanGame.kt:290-312` — `setupSceneLighting()` where `lightingSystem` is configured. Shows how properties are set on the lighting system.
  - `src/main/kotlin/pacman/PacmanGame.kt:1894-1927` — `syncSceneLights()` opening. Insert fog logic after ambient color section.

  **API/Type References**:
  - `DirectLightingSystem.fogIntensity: Float` — 0-100 range, controls fog density
  - `DirectLightingSystem.fogTurbulence: Float` — 0-100, controls fog animation speed
  - `DirectLightingSystem.fogScale: Float` — 0-5, controls fog noise scale
  - These properties are confirmed in PulseEngine 0.13.0 source: `/tmp/PulseEngine/src/main/kotlin/no/njoh/pulseengine/modules/lighting/direct/DirectLightingSystem.kt`

  **WHY Each Reference Matters**:
  - Line 290-312: Shows the `lightingSystem?.apply { }` pattern for setting properties
  - Line 1894-1927: The insertion point — fog control goes near ambient color control
  - DirectLightingSystem source: Confirms fog API exists with property ranges

  **Acceptance Criteria**:
  - [ ] `./gradlew build` passes
  - [ ] fogIntensity set to `nativeFogIntensity` when enabled and in gameplay phase
  - [ ] fogIntensity set to 0 when disabled or non-gameplay phase
  - [ ] fogTurbulence and fogScale set to reasonable defaults
  - [ ] Service menu slider controls fog intensity in real-time

  **Agent-Executed QA Scenarios**:

  ```
  Scenario: Verify fog integration
    Tool: Bash (grep)
    Steps:
      1. grep -A8 "nativeFogEnabled" src/main/kotlin/pacman/PacmanGame.kt | grep -i fog
      2. Assert: fogIntensity, fogTurbulence, fogScale are set
      3. Assert: fog disabled (intensity=0) when toggle is off
    Expected Result: Native fog integrated with lighting system
    Evidence: grep output
  ```

  **Commit**: YES
  - Message: `feat(atmosphere): activate native PulseEngine fog system`
  - Files: `src/main/kotlin/pacman/PacmanGame.kt`
  - Pre-commit: `./gradlew build`

---

- [ ] 10. Fog of War / Darkness

  **What to do**:
  This is the most complex task. It creates a limited-visibility experience where pac-man's aura is the primary light source and the rest of the maze is dark.

  1. In `syncSceneLights()`, add fog-of-war ambient override. This must have HIGHEST priority in the ambient color chain:
     ```kotlin
     // Priority: fogOfWar > frightenedAmbient > sceneBrightness
     if (fogOfWarEnabled && playfieldLightsEnabled) {
         lightingSystem?.ambientColor = Color(0.005f, 0.005f, 0.015f, 0.95f)  // Near-black with slight blue
     } else if (frightenedAmbientShiftEnabled && anyGhostFrightened) {
         lightingSystem?.ambientColor = Color(0.02f, 0.03f, 0.12f, 0.85f)
     } else {
         lightingSystem?.ambientColor = sceneBrightnessAmbient()
     }
     ```
     This REPLACES the ambient logic from Task 1 — combine both into a single priority chain.

  2. When fog of war is ON, the pac-man aura becomes critical for visibility. The enhanced aura (Task 2) radius of 320f is the primary "flashlight." If enhanced aura is also off, fall back to 220f which creates a tighter visibility cone.

  3. Ghost lights become the player's warning system. They glow through the darkness, so the player sees colored light approaching before the ghost itself. This is already handled by existing ghost aura lights.

  4. Board backlight intensity should be reduced when fog of war is on:
     ```kotlin
     boardBacklight?.apply {
         intensity = if (fogOfWarEnabled) 0.05f else if (auraLightsEnabled) 0.35f + pulse * 0.1f else 0f
     }
     ```

  5. Dots and power pellets are rendered as sprites (not light sources), so they'll only be visible within pac-man's aura radius. This is the DESIRED behavior — it creates tension.

  6. Fog of war defaults to OFF. It dramatically changes the game feel and should be a conscious choice.

  **Must NOT do**:
  - Do NOT add per-tile visibility calculations or true fog-of-war algorithms
  - Do NOT modify dot/pellet/fruit rendering logic — they're naturally hidden by darkness
  - Do NOT change the aura light TYPE or shadow type
  - Do NOT conflict with the frightened ambient shift — the priority chain handles this
  - Do NOT create separate dark overlay surfaces

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
  - **Skills**: `[]`
    - Slightly more complex than "quick" due to ambient priority chain integration

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocks**: None
  - **Blocked By**: Tasks 1, 2, 9

  **References**:

  **Pattern References**:
  - `src/main/kotlin/pacman/PacmanGame.kt` — The ambient color section from Task 1 (will exist after Task 1 completes). This task MODIFIES that section into a priority chain.
  - `src/main/kotlin/pacman/PacmanGame.kt:290-312` — `setupSceneLighting()` for initial ambient color pattern.
  - `src/main/kotlin/pacman/PacmanGame.kt:1927-1934` — Pac-man aura update (Task 2 will have enhanced this).
  - `src/main/kotlin/pacman/PacmanGame.kt` — Board backlight update section in `syncSceneLights()` (search for `boardBacklight`).

  **WHY Each Reference Matters**:
  - Task 1's ambient section: MUST be refactored into priority chain (fogOfWar > frightened > default)
  - Line 290-312: Shows how ambientColor is set initially
  - Line 1927-1934: Pac-man aura is the "flashlight" — its radius defines visibility
  - Board backlight: Must be dimmed for fog-of-war to work

  **Acceptance Criteria**:
  - [ ] `./gradlew build` passes
  - [ ] Ambient priority chain: fogOfWar (near-black) > frightened (blue) > sceneBrightness (normal)
  - [ ] Board backlight dimmed to 0.05 when fog of war enabled
  - [ ] Pac-man aura remains the primary light source (unchanged from Task 2)
  - [ ] Ghost lights still visible through darkness (unchanged)
  - [ ] Fog of war defaults to OFF
  - [ ] Toggling fog of war OFF immediately restores normal ambient and backlight

  **Agent-Executed QA Scenarios**:

  ```
  Scenario: Verify fog-of-war ambient priority chain
    Tool: Bash (grep)
    Steps:
      1. grep -B2 -A15 "fogOfWarEnabled.*playfieldLightsEnabled\|fogOfWarEnabled.*ambient" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: fogOfWarEnabled is checked FIRST (highest priority)
      3. Assert: frightenedAmbientShiftEnabled is checked SECOND
      4. Assert: sceneBrightnessAmbient() is the fallback (else)
      5. Assert: near-black ambient color when fogOfWar enabled
    Expected Result: Three-tier ambient priority chain
    Evidence: grep output

  Scenario: Verify board backlight dimming
    Tool: Bash (grep)
    Steps:
      1. grep -A3 "fogOfWarEnabled.*boardBacklight\|boardBacklight.*fogOfWar" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: backlight intensity reduced (0.05 or similar) when fogOfWar on
    Expected Result: Board backlight dimmed for fog-of-war
    Evidence: grep output
  ```

  **Commit**: YES
  - Message: `feat(atmosphere): add fog of war darkness mode`
  - Files: `src/main/kotlin/pacman/PacmanGame.kt`
  - Pre-commit: `./gradlew build`

---

- [ ] 11. Dynamic Bloom During Frightened Mode

  **What to do**:
  1. In `updateBloomEffectSettings()` (or wherever bloom is updated per frame, around line ~269-282): add frightened boost:
     ```kotlin
     private fun updateBloomEffectSettings() {
         val mainEffect = engine.gfx.mainSurface.getPostProcessingEffect(BLOOM_EFFECT_NAME) as? BloomEffect
         mainEffect?.apply {
             val frightenedBoost = if (dynamicFrightenedBloomEnabled && frightenedTimer > 0f) 0.4f else 0f
             intensity = (bloomStrength * 0.85f) + frightenedBoost
             // threshold, thresholdSoftness, radius remain unchanged
         }
     }
     ```
  2. The bloom intensity increases by 0.4 during frightened mode, making the scene glow more dramatically. Combined with the blue ambient (Task 1) and particle trail (Task 5), this creates the layered frightened atmosphere.
  3. The transition is instant (snap) — consistent with retro aesthetic.
  4. When frightened ends (`frightenedTimer <= 0`), bloom snaps back to normal.

  **Must NOT do**:
  - Do NOT add smooth lerp for bloom transition — snap is retro-correct
  - Do NOT change bloom threshold or radius — only intensity
  - Do NOT change the base bloomStrength variable — only add the boost on top

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `[]`

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Blocks**: None
  - **Blocked By**: Task 1

  **References**:

  **Pattern References**:
  - `src/main/kotlin/pacman/PacmanGame.kt:269-282` — `updateBloomEffectSettings()` function. This is where bloom intensity is set per frame. Add frightened boost here.
  - `src/main/kotlin/pacman/PacmanGame.kt:220-231` — `ensureBloomEffects()` showing BloomEffect creation with initial parameters.

  **API/Type References**:
  - `BloomEffect.intensity: Float` — Controls bloom glow strength
  - `frightenedTimer: Float` — Timer > 0 when frightened mode active

  **WHY Each Reference Matters**:
  - Line 269-282: The EXACT function to modify — bloom intensity is set here per frame
  - Line 220-231: Reference for BloomEffect properties (don't change threshold/radius)

  **Acceptance Criteria**:
  - [ ] `./gradlew build` passes
  - [ ] Bloom intensity increased by ~0.4 when `dynamicFrightenedBloomEnabled && frightenedTimer > 0`
  - [ ] Bloom returns to normal when frightened ends
  - [ ] No change to bloom threshold, thresholdSoftness, or radius
  - [ ] Toggle respects `dynamicFrightenedBloomEnabled` state variable

  **Agent-Executed QA Scenarios**:

  ```
  Scenario: Verify dynamic bloom logic
    Tool: Bash (grep)
    Steps:
      1. grep -A10 "frightenedBoost\|dynamicFrightenedBloomEnabled" src/main/kotlin/pacman/PacmanGame.kt | head -20
      2. Assert: frightenedBoost calculated based on toggle + frightenedTimer
      3. Assert: boost value added to bloom intensity
      4. Assert: boost is 0 when toggle off or frightenedTimer <= 0
    Expected Result: Dynamic bloom during frightened mode
    Evidence: grep output
  ```

  **Commit**: YES
  - Message: `feat(atmosphere): add dynamic bloom boost during frightened mode`
  - Files: `src/main/kotlin/pacman/PacmanGame.kt`
  - Pre-commit: `./gradlew build`

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 0 | `feat(visual): add infrastructure for visual enhancement system` | PacmanGame.kt | `./gradlew build` |
| 1 | `feat(lighting): add frightened mode ambient color shift` | PacmanGame.kt | `./gradlew build` |
| 2 | `feat(lighting): enhance pac-man aura radius and intensity` | PacmanGame.kt | `./gradlew build` |
| 3 | `feat(lighting): enhance ghost aura radius and intensity` | PacmanGame.kt | `./gradlew build` |
| 4 | `feat(lighting): add death sequence light flicker effect` | PacmanGame.kt | `./gradlew build` |
| 5 | `feat(particles): add frightened mode particle trail` | PacmanGame.kt | `./gradlew build` |
| 6 | `feat(particles): add ambient floating dust effect` | PacmanGame.kt | `./gradlew build` |
| 7 | `feat(particles): add ghost-colored eat explosions` | PacmanGame.kt | `./gradlew build` |
| 8 | `feat(particles): add level win confetti effect` | PacmanGame.kt | `./gradlew build` |
| 9 | `feat(atmosphere): activate native PulseEngine fog system` | PacmanGame.kt | `./gradlew build` |
| 10 | `feat(atmosphere): add fog of war darkness mode` | PacmanGame.kt | `./gradlew build` |
| 11 | `feat(atmosphere): add dynamic bloom boost during frightened mode` | PacmanGame.kt | `./gradlew build` |

---

## Success Criteria

### Verification Commands
```bash
./gradlew build  # Expected: BUILD SUCCESSFUL
```

### Final Checklist
- [ ] All 12 tasks completed (0-11) with passing builds
- [ ] Service menu shows "Visual Effects" header with 10 items beneath
- [ ] Each toggle independently enables/disables its effect
- [ ] Frightened mode layers: ambient blue + particle trail + bloom boost
- [ ] Death sequence: lights flicker and fade
- [ ] Level win: multi-colored confetti burst
- [ ] Native fog: visible animated fog when enabled
- [ ] Fog of war: near-dark maze with pac-man aura as flashlight
- [ ] Ambient dust: tiny particles drift through maze
- [ ] Ghost eat: particles match ghost color when enhanced
- [ ] No visual artifacts on phase transitions
- [ ] Game runs smoothly with all effects enabled
- [ ] Particle count stays bounded (MAX_PARTICLES cap)
