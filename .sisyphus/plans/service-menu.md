# Service Menu Overlay

## TL;DR

> **Quick Summary**: Consolidate 20+ scattered debug keyboard shortcuts (C, T, M, L, H, V, J, X, O, I, G, K, B, N, +/-, U/Y, ,/.) into a single navigable service menu overlay opened with the S key. Menu pauses the game, shows categorized settings with toggles/sliders/cycles, navigated with UP/DOWN/SPACE/LEFT/RIGHT.
>
> **Deliverables**:
> - Service menu overlay rendered on UI surface with dark background, categorized items, cursor navigation
> - All 17 settings accessible via menu (boolean toggles, float sliders, enum cycles)
> - All old debug key handlers removed from `onUpdate()`
> - Old keybinding help text and debug status lines replaced with "S: Service Menu" hint
> - Game pauses while menu is open
>
> **Estimated Effort**: Medium
> **Parallel Execution**: NO - sequential (Task 2 depends on Task 1)
> **Critical Path**: Task 1 (add infrastructure) -> Task 2 (integrate + cleanup)

---

## Context

### Original Request
User requested: "Move all toggles to a service menu. The service menu should be opened with keyboard shortcut S, and should overlay the whole screen to show only the service menu. You should be able to navigate up and down in the service menu to toggle each setting on and off by space."

### Interview Summary
**Key Discussions**:
- S key opens/closes menu; game pauses while open
- Navigate UP/DOWN, SPACE to toggle/cycle, LEFT/RIGHT for sliders
- Remove S from movement keys (DOWN arrow still works)
- Use `serviceMenuOpen: Boolean` flag, NOT a new GamePhase
- Keep Key.R, Key.ENTER, and movement keys outside the menu (but blocked while menu is open)
- Remove ALL individual debug shortcuts from `onUpdate()`
- Replace old keybinding help text with simple "S: Service Menu" hint
- `renderCrtDebugOverlay()` on debug surface stays unchanged

**Research Findings**:
- File: `src/main/kotlin/pacman/PacmanGame.kt` (~2423 lines, only file modified)
- Three surfaces: `mainSurface` (game + post-processing), `uiSurface` ("pacman_ui"), `debugSurface` ("pacman_debug")
- 17 settings across 4 categories: Post-Processing (6), Lighting (5), Walls (4), Debug (2)
- All toggle/slider handlers at lines 152-222 in `onUpdate()` — each with specific side-effect patterns
- Key.S currently mapped as DOWN movement alias at line 148
- CRT/Scanline/Bloom toggles have side-effect chains: `ensure*Effects()` / `delete*Effects()` / `update*Settings()`
- Lighting toggle uses `setLightingEnabledState()` with `isNonGameplayLightsOffPhase()` guard
- `lightingTargetMainEnabled` toggle also mutates `lightingSystem?.targetSurfaces`
- Brightness is a 3-value enum (`SceneBrightness.LOW/MEDIUM/HIGH`) cycled via `cycleSceneBrightness()`

### Metis Review
**Identified Gaps** (all addressed):
- **E6/E7 — ENTER/R bypassing menu**: If user presses ENTER or R while menu is open, game would restart under the open menu. Fix: when `serviceMenuOpen` is true, block ALL non-menu keys. Also reset `serviceMenuOpen = false` in `resetGame()`, `startNewGameFromStartup()`, and `enterStartScreen()` as a safety net.
- **Q1/Q2 — Menu during non-gameplay phases**: Menu should work during ALL phases (BOOT, ATTRACT, etc.) — it's a service/DIP-switch console, always accessible.
- **Q3 — ENTER/R conflict**: Resolved by blocking all non-menu input when menu is open.
- **Q4 — Overlay surface**: Render on `uiSurface` with high-alpha dark overlay (~0.85). Game content on mainSurface still visible as dim background.
- **Q5 — uiPulseTime freezing**: Early-return in `onFixedUpdate()` freezes pulse animations. Accepted as expected behavior (game is paused).
- **A2 — Menu items cached**: Build items list once as `private val menuItems` property using `by lazy { buildMenuItems() }`. Lambdas capture `this` (PacmanGame) so they always read current property values.
- **A3 — Cursor wrapping**: YES, wraps around (UP from first selectable -> last selectable, and vice versa).
- **A4 — Skip headers**: Cursor navigation skips Header items.

---

## Work Objectives

### Core Objective
Replace all 20+ scattered debug keyboard shortcuts with a single navigable service menu overlay, simplifying the input model while preserving all existing toggle/slider/cycle functionality.

### Concrete Deliverables
- Modified `src/main/kotlin/pacman/PacmanGame.kt` with service menu infrastructure and integration

### Definition of Done
- [x] `./gradlew build` succeeds with zero errors
- [x] Zero references to old debug key handlers (Key.C, Key.T, etc.) remain in `onUpdate()`
- [x] Key.S no longer mapped as DOWN movement
- [x] Service menu opens/closes with S key
- [x] All 17 settings accessible through menu
- [x] Old help text and inline status lines removed from `renderUI()`

### Must Have
- Service menu overlay with dark background on `uiSurface`
- 4 category headers: POST-PROCESSING, LIGHTING, WALLS, DEBUG
- 17 menu items: 13 boolean toggles, 3 float sliders, 1 enum cycle
- UP/DOWN navigation that skips headers and wraps around
- SPACE toggles booleans and cycles enums
- LEFT/RIGHT adjusts sliders (0.1f step, 0.0-2.0 range)
- Game pauses while menu is open (`onFixedUpdate` early-return)
- All existing side-effect chains preserved in menu setters
- `serviceMenuOpen = false` safety reset in `resetGame()`, `startNewGameFromStartup()`, `enterStartScreen()`
- "S: Service Menu" hint in `renderUI()` replacing old help text

### Must NOT Have (Guardrails)
- MUST NOT add new files — everything goes in PacmanGame.kt
- MUST NOT change `renderCrtDebugOverlay()` (lines 2002-2010)
- MUST NOT change existing method signatures or rename properties
- MUST NOT change effect side-effect logic — menu setters must call the SAME functions the old key handlers called
- MUST NOT change shader files
- MUST NOT change `setupUiSurface()` or the rendering flow
- MUST NOT change effect `order` values (CRT=96, scanline=97)
- MUST NOT add ESC key to close menu (S only)
- MUST NOT add sound effects, animation/transitions, settings persistence/serialization, mouse/gamepad support, or scrolling
- MUST NOT make category headers selectable/interactive
- MUST NOT add any abstraction or "helper framework" — keep it simple and direct
- MUST NOT change game physics or timing beyond the early-return guard
- MUST NOT change main surface effect parameters

---

## Verification Strategy

> **UNIVERSAL RULE: ZERO HUMAN INTERVENTION**
>
> Note: This is a native OpenGL game (not a web app). Playwright cannot be used for visual verification.
> Build verification and code-level grep checks are the primary automated criteria.

### Test Decision
- **Infrastructure exists**: NO
- **Automated tests**: None
- **Framework**: N/A

### Agent-Executed QA Scenarios

Verification focuses on build success and code-level grep assertions (same strategy as the CRT fix plan).

---

## Execution Strategy

### Sequential Execution

```
Task 1 (Add infrastructure - purely additive):
├── New state variables
├── New types (MenuItemType, MenuItem)
├── New methods (buildMenuItems, handleServiceMenuInput, renderServiceMenu)
└── Build to verify no compile errors

Task 2 (Integrate + cleanup - modify existing code):
├── Rewrite onUpdate() input handling
├── Add onFixedUpdate() pause guard
├── Add onRender() menu rendering call
├── Simplify renderUI() (remove old text)
├── Add safety resets to reset/start methods
├── Build verification
└── Git commit

Critical Path: Task 1 -> Task 2
```

### Dependency Matrix

| Task | Depends On | Blocks | Can Parallelize With |
|------|------------|--------|---------------------|
| 1 | None | 2 | None |
| 2 | 1 | None | None (final) |

### Agent Dispatch Summary

| Wave | Tasks | Recommended Agents |
|------|-------|-------------------|
| 1 | 1 | task(category="unspecified-high", load_skills=[], run_in_background=false) |
| 2 | 2 | task(category="unspecified-high", load_skills=["git-master"], run_in_background=false) |

---

## TODOs

- [x] 1. Add service menu infrastructure (new code only)

  **What to do**:

  All changes are purely ADDITIVE — no existing code is modified. Add the following to `src/main/kotlin/pacman/PacmanGame.kt`:

  **Step 1: Add state variables** (after line 76, with the other toggle booleans)

  Add two new properties:
  - `private var serviceMenuOpen = false`
  - `private var serviceMenuCursorIndex = 0`

  **Step 2: Add menu data types** (inside PacmanGame class, before `companion object` at line 2339)

  Add a sealed interface `MenuItemType` with four subtypes:
  - `object Toggle : MenuItemType` — for boolean on/off settings
  - `data class Slider(val step: Float, val min: Float, val max: Float) : MenuItemType` — for float adjustments
  - `object Cycle : MenuItemType` — for enum cycling (brightness)
  - `object Header : MenuItemType` — for category headers (non-interactive)

  Add a data class `MenuItem` with fields:
  - `label: String` — display name
  - `type: MenuItemType` — determines interaction behavior
  - `getter: () -> String` — returns current value as display string (e.g., "ON", "OFF", "1.0x", "HIGH")
  - `setter: ((Boolean) -> Unit)?` — for Toggle items (called with new value on SPACE)
  - `sliderSetter: ((Float) -> Unit)?` — for Slider items (called with new value on LEFT/RIGHT)
  - `cycleSetter: (() -> Unit)?` — for Cycle items (called on SPACE)

  **Step 3: Add `buildMenuItems()` method** (near `renderUI()` around line 1860, as a private method)

  Returns a `List<MenuItem>` with exactly 21 entries (4 headers + 17 items). Each item's setter lambdas MUST call the same side-effect functions that the original key handlers called. The exact item list:

  **Category: POST-PROCESSING**
  | # | Label | Type | Property | Getter | Setter Side Effects |
  |---|-------|------|----------|--------|-------------------|
  | 0 | "== POST-PROCESSING ==" | Header | — | "" | — |
  | 1 | "CRT Effect" | Toggle | `crtEnabled` | "ON"/"OFF" | On true: `ensureCRTEffects()` then `updateCRTEffectSettings()`. On false: `deleteCRTEffects()` |
  | 2 | "CRT Strength" | Slider(0.1f, 0.0f, 2.0f) | `crtStrength` | `"%.1f".format(crtStrength) + "x"` | After value change: `updateCRTEffectSettings()` |
  | 3 | "Scanline Effect" | Toggle | `scanlineEnabled` | "ON"/"OFF" | On true: `ensureScanlineEffects()` then `updateScanlineEffectSettings()`. On false: `deleteScanlineEffects()` |
  | 4 | "Scanline Strength" | Slider(0.1f, 0.0f, 2.0f) | `scanlineStrength` | `"%.1f".format(scanlineStrength) + "x"` | After value change: `updateScanlineEffectSettings()` |
  | 5 | "Bloom Effect" | Toggle | `bloomEnabled` | "ON"/"OFF" | On true: `ensureBloomEffects()` then `updateBloomEffectSettings()`. On false: `deleteBloomEffects()` |
  | 6 | "Bloom Strength" | Slider(0.1f, 0.0f, 2.0f) | `bloomStrength` | `"%.1f".format(bloomStrength) + "x"` | After value change: `updateBloomEffectSettings()` |

  **Category: LIGHTING**
  | # | Label | Type | Property | Getter | Setter Side Effects |
  |---|-------|------|----------|--------|-------------------|
  | 7 | "== LIGHTING ==" | Header | — | "" | — |
  | 8 | "Lighting" | Toggle | `lightingEnabled` | "ON"/"OFF" | Must mirror lines 152-158: call `setLightingEnabledState()`. When enabling, guard with `isNonGameplayLightsOffPhase()` check (if in non-gameplay phase, keep disabled) |
  | 9 | "Entity Halos" | Toggle | `entityHaloEnabled` | "ON"/"OFF" | Direct property flip, no side effects |
  | 10 | "Board Backlight" | Toggle | `boardBacklightEnabled` | "ON"/"OFF" | Direct property flip |
  | 11 | "Aura Lights" | Toggle | `auraLightsEnabled` | "ON"/"OFF" | Direct property flip |
  | 12 | "Brightness" | Cycle | `sceneBrightness` | `sceneBrightness.name` | Call `cycleSceneBrightness()` — this handles the cycling and ambient color update |

  **Category: WALLS**
  | # | Label | Type | Property | Getter | Setter Side Effects |
  |---|-------|------|----------|--------|-------------------|
  | 13 | "== WALLS ==" | Header | — | "" | — |
  | 14 | "Wall Bevel" | Toggle | `wallBevelEnabled` | "ON"/"OFF" | Direct property flip |
  | 15 | "Bevel Debug" | Toggle | `wallBevelDebug` | "ON"/"OFF" | Direct property flip |
  | 16 | "Wall Outline" | Toggle | `wallOutlineEnabled` | "ON"/"OFF" | Direct property flip |
  | 17 | "Thin Outline" | Toggle | `wallThinOutlineMode` | "ON"/"OFF" | Direct property flip |

  **Category: DEBUG**
  | # | Label | Type | Property | Getter | Setter Side Effects |
  |---|-------|------|----------|--------|-------------------|
  | 18 | "== DEBUG ==" | Header | — | "" | — |
  | 19 | "Geometry Test" | Toggle | `geometryTestOverlayEnabled` | "ON"/"OFF" | Direct property flip |
  | 20 | "Light Target Main" | Toggle | `lightingTargetMainEnabled` | "ON"/"OFF" | Must mirror lines 218-221: flip property AND mutate `lightingSystem?.targetSurfaces` — when enabling, set to `listOf(mainSurface)`; when disabling, set to `listOf(mainSurface, uiSurface)` (look at lines 218-221 for the exact pattern) |

  Store the result as `private val menuItems: List<MenuItem> by lazy { buildMenuItems() }`.

  **Step 4: Add `handleServiceMenuInput()` method** (private, near buildMenuItems)

  Logic flow:
  ```
  if wasClicked(Key.UP):
      Move cursor UP, skipping Header items, wrapping from first to last selectable
  if wasClicked(Key.DOWN):
      Move cursor DOWN, skipping Header items, wrapping from last to first selectable
  if wasClicked(Key.SPACE):
      Get item at serviceMenuCursorIndex
      If Toggle: flip the boolean via setter (pass !currentValue)
      If Cycle: call cycleSetter
      If Slider/Header: no-op
  if wasClicked(Key.LEFT):
      Get item at serviceMenuCursorIndex
      If Slider: decrease value by step, clamp with coerceIn(min, max), call sliderSetter
      Others: no-op
  if wasClicked(Key.RIGHT):
      Get item at serviceMenuCursorIndex
      If Slider: increase value by step, clamp with coerceIn(min, max), call sliderSetter
      Others: no-op
  ```

  IMPORTANT: `serviceMenuCursorIndex` must be initialized to the first non-Header index (index 1, since index 0 is the first header). After wrapping/skipping logic, cursor must NEVER land on a Header item.

  **Step 5: Add `renderServiceMenu(s: Surface)` method** (private, near handleServiceMenuInput)

  Layout specification — follow existing Surface drawing style (`s.setDrawColor(...)` then `s.drawQuad(...)` / `s.drawText(...)`):

  1. **Dark overlay**: `s.setDrawColor(0f, 0f, 0f, 0.85f)` then `s.drawQuad(0f, 0f, s.width.toFloat(), s.height.toFloat())` — covers entire surface
  2. **Title**: Draw "SERVICE MENU" near the top, centered or left-aligned with padding. Use a bright/prominent color (white or yellow). Look at how `renderStartupScreen()` (around line 2186) positions title text for coordinate reference.
  3. **Menu items**: Iterate `menuItems` with index. For each item:
     - Calculate Y position: start below title, each item gets a fixed line height (look at existing `drawText` calls in `renderUI()` for the font size and spacing used)
     - **Header items**: Draw in a distinct color (e.g., yellow or cyan), no cursor, maybe with padding above
     - **Selected item** (index == serviceMenuCursorIndex): Draw `"> "` prefix, use highlight color (e.g., bright white or yellow)
     - **Unselected item**: Draw `"  "` prefix (same width for alignment), use dimmer color (e.g., gray)
     - **Toggle value**: Append "ON" (green) or "OFF" (red/gray) after the label
     - **Slider value**: Append the formatted value string (e.g., "1.0x") after the label. Optionally draw a simple text-based bar like `[||||------]` using characters
     - **Cycle value**: Append the current enum name after the label
  4. **Help text**: Draw at bottom of screen: `"UP/DOWN: Navigate   SPACE: Toggle   LEFT/RIGHT: Adjust   S: Close"`

  IMPORTANT: Use the same `drawText` font/size that `renderUI()` uses. Look at existing calls in `renderUI()` (around lines 1966-1988) for the exact method signature, font parameter, and text sizing conventions.

  **Must NOT do**:
  - Do not modify any existing methods or code
  - Do not change `renderCrtDebugOverlay()`
  - Do not add new files

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Substantial code addition (~200 lines of new Kotlin) requiring careful understanding of existing patterns, side-effect chains, and Surface drawing API
  - **Skills**: `[]`
    - No special skills needed — pure Kotlin code in one file
  - **Skills Evaluated but Omitted**:
    - `frontend-ui-ux`: Not applicable — this is a native OpenGL game, not web frontend
    - `playwright`: Not applicable — native game, no browser
    - `git-master`: Not needed in Task 1 (no commit)

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential — must complete before Task 2
  - **Blocks**: Task 2
  - **Blocked By**: None (can start immediately)

  **References**:

  **Pattern References** (existing code to follow):
  - `src/main/kotlin/pacman/PacmanGame.kt:60-76` — All toggle/slider/enum state variables. New `serviceMenuOpen` and `serviceMenuCursorIndex` go after line 76.
  - `src/main/kotlin/pacman/PacmanGame.kt:167-175` — CRT toggle handler with `ensureCRTEffects()`/`deleteCRTEffects()`/`updateCRTEffectSettings()` side-effect chain. The menu Toggle setter for CRT must replicate this EXACT pattern.
  - `src/main/kotlin/pacman/PacmanGame.kt:198-206` — Scanline toggle handler. Same side-effect pattern to replicate.
  - `src/main/kotlin/pacman/PacmanGame.kt:176-184` — Bloom toggle handler. Same side-effect pattern.
  - `src/main/kotlin/pacman/PacmanGame.kt:152-158` — Lighting toggle with `setLightingEnabledState()` and `isNonGameplayLightsOffPhase()` guard. Menu setter MUST include this guard.
  - `src/main/kotlin/pacman/PacmanGame.kt:218-221` — `lightingTargetMainEnabled` toggle with `lightingSystem?.targetSurfaces` mutation. Menu setter must replicate this.
  - `src/main/kotlin/pacman/PacmanGame.kt:222` — `cycleSceneBrightness()` call for brightness cycling.
  - `src/main/kotlin/pacman/PacmanGame.kt:159-162` — CRT strength increase pattern (0.1f step, coerceIn 0f..2f, then `updateCRTEffectSettings()`). Menu slider must use same step/range.
  - `src/main/kotlin/pacman/PacmanGame.kt:207-214` — Scanline strength pattern (same step/range).
  - `src/main/kotlin/pacman/PacmanGame.kt:185-192` — Bloom strength pattern (same step/range).
  - `src/main/kotlin/pacman/PacmanGame.kt:1966-1988` — `renderUI()` drawing calls. Follow the same `drawText` method signature, font, and coordinate system for `renderServiceMenu()`.
  - `src/main/kotlin/pacman/PacmanGame.kt:2186-2240` — `renderStartupScreen()`. Reference for full-screen overlay pattern: `drawQuad` for background, text positioning.
  - `src/main/kotlin/pacman/PacmanGame.kt:2339-2345` — `companion object` location. New types go BEFORE this.

  **API/Type References**:
  - `src/main/kotlin/pacman/PacmanGame.kt:2363-2366` — `GamePhase` and `SceneBrightness` declarations at the end of the file. Follow this pattern for where to place `MenuItemType` and `MenuItem` (as private declarations inside the PacmanGame class, before the companion object).

  **Acceptance Criteria**:

  **Agent-Executed QA Scenarios:**

  ```
  Scenario: Build succeeds with new infrastructure added
    Tool: Bash
    Preconditions: All new code added (state vars, types, 3 methods)
    Steps:
      1. Run: ./gradlew build
      2. Assert: exit code 0
      3. Assert: output contains "BUILD SUCCESSFUL"
    Expected Result: Clean build — new code compiles without errors
    Evidence: Build output captured

  Scenario: New types and methods exist in source
    Tool: Bash (grep)
    Preconditions: Build succeeded
    Steps:
      1. grep -n "serviceMenuOpen" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: at least 1 match (property declaration)
      3. grep -n "MenuItemType" src/main/kotlin/pacman/PacmanGame.kt
      4. Assert: at least 1 match (sealed interface)
      5. grep -n "fun buildMenuItems" src/main/kotlin/pacman/PacmanGame.kt
      6. Assert: 1 match
      7. grep -n "fun handleServiceMenuInput" src/main/kotlin/pacman/PacmanGame.kt
      8. Assert: 1 match
      9. grep -n "fun renderServiceMenu" src/main/kotlin/pacman/PacmanGame.kt
      10. Assert: 1 match
    Expected Result: All new declarations present
    Evidence: Grep output captured
  ```

  **Commit**: NO (Task 2 handles the commit after full integration)

---

- [x] 2. Integrate service menu into game loop and clean up old handlers

  **What to do**:

  Modify EXISTING code in `src/main/kotlin/pacman/PacmanGame.kt` to wire up the service menu infrastructure from Task 1 and remove old debug key handlers.

  **Step 1: Rewrite `onUpdate()` input handling** (line 141)

  The current `onUpdate()` structure (lines 141-226) needs to be restructured:

  **NEW structure:**
  ```
  fun onUpdate() {
      // 1. S key toggles menu (ALWAYS, regardless of menu state)
      if (wasClicked(Key.S)) {
          serviceMenuOpen = !serviceMenuOpen
          if (serviceMenuOpen) {
              // Ensure cursor is on a valid (non-Header) item
              if (menuItems[serviceMenuCursorIndex].type is MenuItemType.Header) {
                  // Move to first non-header item
              }
          }
      }

      // 2. If menu is open: handle menu input ONLY, then return
      if (serviceMenuOpen) {
          handleServiceMenuInput()
          return
      }

      // 3. Normal game input (menu is closed):
      //    - ENTER handling (lines 142-146 — keep AS-IS)
      //    - Movement: UP/W, DOWN (NO Key.S!), LEFT/A, RIGHT/D (keep AS-IS minus Key.S)
      //    - Key.R reset (line 151 — keep AS-IS)
      //    - ENTER for game over (lines 223-225 — keep AS-IS)
      //
      // 4. ALL debug key handlers REMOVED (lines 152-222 — DELETE ENTIRELY):
      //    Key.L, Key.EQUAL, Key.KP_ADD, Key.MINUS, Key.KP_SUBTRACT,
      //    Key.C, Key.M, Key.PERIOD, Key.COMMA, Key.B, Key.H, Key.V,
      //    Key.J, Key.X, Key.T, Key.U, Key.Y, Key.O, Key.I, Key.G,
      //    Key.K, Key.N
  }
  ```

  **CRITICAL changes to movement keys (line 148)**:
  - BEFORE: `if (wasClicked(Key.DOWN) || wasClicked(Key.S)) pacNextDir = Direction.DOWN`
  - AFTER:  `if (wasClicked(Key.DOWN)) pacNextDir = Direction.DOWN`
  - Remove `|| wasClicked(Key.S)` from the DOWN movement line

  **CRITICAL: Delete these ENTIRE blocks** (lines 152-222):
  - Lines 152-158: Key.L (lighting toggle)
  - Lines 159-162: Key.EQUAL / Key.KP_ADD (CRT strength +)
  - Lines 163-166: Key.MINUS / Key.KP_SUBTRACT (CRT strength -)
  - Lines 167-175: Key.C (CRT toggle)
  - Lines 176-184: Key.M (Bloom toggle)
  - Lines 185-188: Key.PERIOD (Bloom strength +)
  - Lines 189-192: Key.COMMA (Bloom strength -)
  - Line 193: Key.B (wallBevelDebug)
  - Line 194: Key.H (entityHaloEnabled)
  - Line 195: Key.V (boardBacklightEnabled)
  - Line 196: Key.J (auraLightsEnabled)
  - Line 197: Key.X (wallBevelEnabled)
  - Lines 198-206: Key.T (Scanline toggle)
  - Lines 207-210: Key.U (Scanline strength +)
  - Lines 211-214: Key.Y (Scanline strength -)
  - Line 215: Key.O (wallOutlineEnabled)
  - Line 216: Key.I (wallThinOutlineMode)
  - Line 217: Key.G (geometryTestOverlayEnabled)
  - Lines 218-221: Key.K (lightingTargetMainEnabled)
  - Line 222: Key.N (sceneBrightness cycle)

  **Step 2: Add pause guard in `onFixedUpdate()`** (line 524)

  Add early-return AFTER `uiPulseTime += dt` (so pulse animation continues, preventing jarring freeze):
  ```
  At line 526 (after uiPulseTime += dt):
      if (serviceMenuOpen) return
  ```

  Note: This means `uiPulseTime` keeps advancing (text pulse animation is smooth) but ALL game logic (ghost movement, timers, physics) pauses.

  **Step 3: Add menu rendering in `onRender()`** (around line 675, at END of method)

  Add at the very end of `onRender()`, AFTER all existing rendering:
  ```
  if (serviceMenuOpen) {
      renderServiceMenu(uiSurface)
  }
  ```

  This renders the menu LAST, on TOP of everything else on the UI surface.

  **Step 4: Simplify `renderUI()`** (line 1860)

  Find and REMOVE the old keybinding help text (line 1966 — the long string starting with "WASD/Arrows: Move  R: Reset  L: Lights  C: CRT...").

  Find and REMOVE the debug status lines (lines 1974-1988):
  - The "BRIGHTNESS:" line
  - The "CRT:" line
  - The "SCANLINE:" line
  - The "BLOOM:" line
  - The "FX HALO:..." compound status line
  - The "WALL BEVEL BOOST" conditional line

  REPLACE all of the above with a single small hint text:
  ```
  Draw "S: Service Menu" at the bottom of the screen, in a subtle/dim color
  ```

  Use the same position/font as the old help text but with just this one short string.

  **Step 5: Add safety resets** (find these methods in the file)

  Add `serviceMenuOpen = false` as the FIRST line in each of these methods:
  - `resetGame()` — prevents menu staying open during game reset
  - `startNewGameFromStartup()` — prevents menu staying open when starting new game
  - `enterStartScreen()` — prevents menu staying open when returning to start screen

  Find these methods by searching for `fun resetGame()`, `fun startNewGameFromStartup()`, `fun enterStartScreen()`.

  **Step 6: Build and verify**

  Run `./gradlew build` and verify BUILD SUCCESSFUL.

  **Step 7: Git commit**

  Commit all changes with message: `feat(ui): add service menu overlay replacing debug keyboard shortcuts`

  **Must NOT do**:
  - Do not change `renderCrtDebugOverlay()` (lines 2002-2010)
  - Do not modify any Task 1 code (types, buildMenuItems, etc.)
  - Do not change shader files
  - Do not rename existing properties
  - Do not change game physics or effect parameters

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Substantial modification of existing game loop code — must carefully restructure onUpdate() (removing ~70 lines of handlers), modify 4 methods, and add safety resets without breaking game flow
  - **Skills**: `["git-master"]`
    - `git-master`: Needed for the final commit step — atomic commit of all changes
  - **Skills Evaluated but Omitted**:
    - `frontend-ui-ux`: Not applicable — native OpenGL game
    - `playwright`: Not applicable — native game

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential — depends on Task 1
  - **Blocks**: None (final task)
  - **Blocked By**: Task 1

  **References**:

  **Pattern References** (existing code to modify):
  - `src/main/kotlin/pacman/PacmanGame.kt:141-226` — Current `onUpdate()` method. Lines 142-146 (ENTER) and 147-150 (movement) are KEPT (minus Key.S on line 148). Line 151 (Key.R) is KEPT. Lines 152-222 are DELETED. Lines 223-225 (ENTER game over) are KEPT.
  - `src/main/kotlin/pacman/PacmanGame.kt:148` — `if (wasClicked(Key.DOWN) || wasClicked(Key.S))` — remove the `|| wasClicked(Key.S)` part
  - `src/main/kotlin/pacman/PacmanGame.kt:524-526` — `onFixedUpdate()` start. Add `if (serviceMenuOpen) return` after `uiPulseTime += dt`.
  - `src/main/kotlin/pacman/PacmanGame.kt:634-675` — `onRender()`. Add `renderServiceMenu(uiSurface)` call at line 675 (end of method).
  - `src/main/kotlin/pacman/PacmanGame.kt:1966` — Old keybinding help text in `renderUI()` — DELETE this entire string/drawText call.
  - `src/main/kotlin/pacman/PacmanGame.kt:1974-1988` — Old debug status lines in `renderUI()` — DELETE all these drawText calls.

  **Task 1 References** (new code added by Task 1 that this task wires up):
  - `handleServiceMenuInput()` — called from rewritten `onUpdate()` when menu is open
  - `renderServiceMenu(s: Surface)` — called from `onRender()` with `uiSurface`
  - `serviceMenuOpen` property — checked in `onUpdate()`, `onFixedUpdate()`, `onRender()`; reset in safety methods
  - `menuItems` property — cursor initialization check in `onUpdate()` S-key handler

  **Acceptance Criteria**:

  **Agent-Executed QA Scenarios:**

  ```
  Scenario: Build succeeds after all integration changes
    Tool: Bash
    Preconditions: Task 1 completed, all integration changes applied
    Steps:
      1. Run: ./gradlew build
      2. Assert: exit code 0
      3. Assert: output contains "BUILD SUCCESSFUL"
    Expected Result: Clean build with zero errors
    Evidence: Build output captured

  Scenario: Old debug key handlers removed from onUpdate
    Tool: Bash (grep)
    Preconditions: Build succeeded
    Steps:
      1. grep -n "Key\.C\b" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: zero matches in onUpdate (may exist in other contexts but NOT as wasClicked handlers)
      3. grep -n "Key\.T\b" src/main/kotlin/pacman/PacmanGame.kt
      4. Assert: zero matches as wasClicked handlers
      5. grep -n "Key\.M\b" src/main/kotlin/pacman/PacmanGame.kt
      6. Assert: zero matches as wasClicked handlers
      7. grep -n "Key\.H\b" src/main/kotlin/pacman/PacmanGame.kt
      8. Assert: zero matches as wasClicked handlers
      9. grep -n "Key\.V\b" src/main/kotlin/pacman/PacmanGame.kt
      10. Assert: zero matches as wasClicked handlers
      11. grep -c "wasClicked(Key\." src/main/kotlin/pacman/PacmanGame.kt
      12. Assert: count is significantly lower than before (~25+ removed, only ~10 remain: S, UP, W, DOWN, LEFT, A, RIGHT, D, R, ENTER, SPACE, arrows in menu)
    Expected Result: All old debug key handlers removed
    Evidence: Grep output captured

  Scenario: Key.S no longer mapped as DOWN movement
    Tool: Bash (grep)
    Preconditions: Build succeeded
    Steps:
      1. grep -n "Key\.S" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: Key.S appears ONLY in the service menu toggle block (wasClicked(Key.S) -> serviceMenuOpen toggle)
      3. Assert: Key.S does NOT appear alongside Key.DOWN or Direction.DOWN
    Expected Result: S key is menu-only, not movement
    Evidence: Grep output captured

  Scenario: Old help text removed from renderUI
    Tool: Bash (grep)
    Preconditions: Build succeeded
    Steps:
      1. grep -n "L: Lights" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: zero matches (old help text gone)
      3. grep -n "C: CRT" src/main/kotlin/pacman/PacmanGame.kt
      4. Assert: zero matches (old help text gone)
      5. grep -n "S: Service Menu" src/main/kotlin/pacman/PacmanGame.kt
      6. Assert: at least 1 match (new hint text present)
    Expected Result: Old help text replaced with new hint
    Evidence: Grep output captured

  Scenario: Safety resets present in reset/start methods
    Tool: Bash (grep)
    Preconditions: Build succeeded
    Steps:
      1. grep -A 3 "fun resetGame" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: serviceMenuOpen = false appears within first few lines
      3. grep -A 3 "fun startNewGameFromStartup" src/main/kotlin/pacman/PacmanGame.kt
      4. Assert: serviceMenuOpen = false appears within first few lines
      5. grep -A 3 "fun enterStartScreen" src/main/kotlin/pacman/PacmanGame.kt
      6. Assert: serviceMenuOpen = false appears within first few lines
    Expected Result: Menu auto-closes on game reset/restart
    Evidence: Grep output captured

  Scenario: renderCrtDebugOverlay unchanged
    Tool: Bash (grep)
    Preconditions: Build succeeded
    Steps:
      1. grep -n "CRT DBG" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: still present (debug overlay unchanged)
      3. grep -n "PP DBG" src/main/kotlin/pacman/PacmanGame.kt
      4. Assert: still present
    Expected Result: Debug overlay untouched
    Evidence: Grep output captured
  ```

  **Commit**: YES
  - Message: `feat(ui): add service menu overlay replacing debug keyboard shortcuts`
  - Files: `src/main/kotlin/pacman/PacmanGame.kt`
  - Pre-commit: `./gradlew build`

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 2 | `feat(ui): add service menu overlay replacing debug keyboard shortcuts` | PacmanGame.kt | `./gradlew build` |

---

## Success Criteria

### Verification Commands
```bash
./gradlew build                                    # Expected: BUILD SUCCESSFUL
grep -c "wasClicked(Key\." src/main/kotlin/pacman/PacmanGame.kt  # Expected: ~12 (menu keys + movement + R + ENTER)
grep "L: Lights" src/main/kotlin/pacman/PacmanGame.kt            # Expected: 0 matches (old help text gone)
grep "S: Service Menu" src/main/kotlin/pacman/PacmanGame.kt      # Expected: 1+ matches (new hint present)
grep "serviceMenuOpen" src/main/kotlin/pacman/PacmanGame.kt      # Expected: 5+ matches (declaration + checks + resets)
```

### Final Checklist
- [x] All "Must Have" present (menu overlay, 17 items, navigation, pause, safety resets, hint text)
- [x] All "Must NOT Have" absent (no new files, no shader changes, no ESC key, no animation/persistence/scrolling)
- [x] Build passes (`./gradlew build`)
- [x] Old debug key handlers removed
- [x] Key.S removed from movement mapping
- [x] `renderCrtDebugOverlay()` unchanged
