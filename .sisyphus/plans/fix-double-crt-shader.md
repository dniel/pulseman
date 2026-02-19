# Fix Double-Application of Post-Processing Effects (CRT, Scanline, Bloom)

## TL;DR

> **Quick Summary**: CRT barrel distortion appears 2x curved because the effect is applied to both `mainSurface` and `uiSurface` independently. Since PulseEngine's mainSurface post-processing runs as the final screen pass (compositing all surfaces), having effects on both surfaces causes double-processing. Fix by removing all post-processing from the UI surface and keeping effects only on the main surface.
>
> **Deliverables**:
> - Fixed `PacmanGame.kt` with effects applied only to mainSurface
> - Cleaned up dead code (UI effect constants, helpers, references)
> - Updated debug overlay to reflect single-surface architecture
>
> **Estimated Effort**: Quick
> **Parallel Execution**: NO - sequential (2 tasks, second depends on first)
> **Critical Path**: Task 1 (validate assumption) -> Task 2 (implement fix)

---

## Context

### Original Request
User reported CRT shader curvature appearing 2x when enabled. Screenshots show:
- CRT OFF: `CRT DBG state: OFF main=N ui=N` - maze appears normal
- CRT ON: `CRT DBG state: ON main=Y ui=Y` - curvature doubled, barrel distortion excessive

### Interview Summary
**Key Discussions**:
- CRT, Scanline, and Bloom all follow the same dual-surface pattern (applied to both `mainSurface` and `uiSurface`)
- User confirmed fixing all three effects, not just CRT

**Research Findings**:
- `ensureCRTEffects()` adds CRTEffect to BOTH surfaces (lines 240-249)
- Same pattern for `ensureScanlineEffects()` (lines 256-265) and `ensureBloomEffects()` (lines 272-295)
- UI bloom was intentionally tuned with different parameters (lower threshold=0.82, larger radius=0.0048) vs main bloom (threshold=1.05, radius=0.0038)
- Init scripts only bind keys, no post-processing config
- Shader files are correct - the bug is in Kotlin wiring

### Metis Review
**Identified Gaps** (addressed):
- **Compositing model assumption**: The fix assumes mainSurface processes the final composite. Addressed by adding a validation task BEFORE full implementation.
- **UI bloom different tuning**: UI bloom had intentionally different params (lower threshold for more glow on text). After fix, UI inherits main bloom params. Acknowledged as acceptable visual change.
- **`onRender` re-ensure loop**: Lines 670-684 check UI effects every frame and would re-add them. Must update this code.
- **Debug overlay**: Currently shows `ui=Y/N` which becomes meaningless. Must simplify.
- **Dead code cleanup**: Six `hasUi*Effect()` helpers and three `UI_*_EFFECT_NAME` constants become dead code. Must remove.

---

## Work Objectives

### Core Objective
Remove post-processing effects (CRT, Scanline, Bloom) from the UI surface so each effect applies exactly once via the main surface's final compositing pass.

### Concrete Deliverables
- Modified `src/main/kotlin/pacman/PacmanGame.kt` with single-surface post-processing

### Definition of Done
- [x] `./gradlew build` succeeds with zero errors
- [x] Zero references to `UI_CRT_EFFECT_NAME`, `UI_SCANLINE_EFFECT_NAME`, `UI_BLOOM_EFFECT_NAME` remain
- [x] CRT curvature visually shows 1x barrel distortion (not 2x)
- [x] Toggling CRT/Scanline/Bloom with hotkeys still works correctly

### Must Have
- CRT, Scanline, Bloom applied ONLY to mainSurface
- All UI-specific effect code removed cleanly
- `onRender` re-ensure loop updated to only check mainSurface
- Debug overlay simplified (remove UI status indicators)
- Build compiles successfully

### Must NOT Have (Guardrails)
- MUST NOT modify shader files (`crt.frag`, `crt.vert`, `scanline.frag`, `scanline.vert`)
- MUST NOT change `setupUiSurface()` - the UI surface is still needed for rendering separation
- MUST NOT change the `onRender()` rendering flow (which surface receives which draw calls)
- MUST NOT modify mainSurface effect parameters - only remove UI surface effects
- MUST NOT refactor the three effect pairs into a generic helper/abstraction (fix the bug, don't refactor)
- MUST NOT consolidate the toggle handlers (C, T, M keys)
- MUST NOT change effect `order` values (CRT=96, scanline=97)
- MUST NOT add tests or test infrastructure
- MUST NOT adjust main bloom parameters to "compensate" for lost UI bloom tuning

---

## Verification Strategy

> **UNIVERSAL RULE: ZERO HUMAN INTERVENTION**
>
> Note: This is a native OpenGL game (not a web app). Playwright cannot be used.
> Visual verification of shader rendering is inherently difficult to automate.
> Build verification and code-level checks are the primary automated criteria.
> Visual confirmation of curvature is noted as a manual step the user performs after running the game.

### Test Decision
- **Infrastructure exists**: NO
- **Automated tests**: None
- **Framework**: N/A

### Agent-Executed QA Scenarios (per task)

Verification focuses on build success and code-level grep assertions.

---

## Execution Strategy

### Sequential Execution

```
Task 1 (Validate assumption):
└── Quick test: comment out UI CRT only, verify assumption about compositing

Task 2 (Full implementation):
└── Depends on Task 1 confirming the compositing model
└── Remove all UI effects, clean up dead code, update debug overlay

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
| 1 | 1 | task(category="quick", load_skills=[], run_in_background=false) |
| 2 | 2 | task(category="quick", load_skills=[], run_in_background=false) |

---

## TODOs

- [x] 1. Validate compositing model assumption

  **What to do**:
  - In `ensureCRTEffects()`, comment out ONLY the UI surface CRT addition (lines 246-248):
    ```kotlin
    // if (uiSurface.getPostProcessingEffect(UI_CRT_EFFECT_NAME) == null) {
    //     uiSurface.addPostProcessingEffect(CRTEffect(name = UI_CRT_EFFECT_NAME))
    // }
    ```
  - Run `./gradlew build` to verify it compiles
  - Run the game, enable CRT (press C), and observe:
    - If UI text (scores, labels) still shows barrel distortion → **Scenario A confirmed** (mainSurface processes final composite, fix is correct)
    - If UI text is flat/undistorted while maze is curved → **Scenario B** (mainSurface only processes its own content, different fix needed)
  - **Report the result** before proceeding to Task 2
  - **Revert the comment** after testing (or proceed directly to Task 2 which makes the full change)

  **Must NOT do**:
  - Do not make permanent changes yet
  - Do not remove more than the UI CRT lines
  - Do not modify shader files

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Single comment-out test in one file, build, report result
  - **Skills**: []
    - No special skills needed

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential - must complete before Task 2
  - **Blocks**: Task 2
  - **Blocked By**: None

  **References**:

  **Pattern References**:
  - `src/main/kotlin/pacman/PacmanGame.kt:240-249` - `ensureCRTEffects()` function - the three lines to comment out (246-248)

  **Acceptance Criteria**:

  **Agent-Executed QA Scenarios:**

  ```
  Scenario: Build succeeds with UI CRT commented out
    Tool: Bash
    Preconditions: Lines 246-248 commented out
    Steps:
      1. Run: ./gradlew build
      2. Assert: exit code 0
      3. Assert: output contains "BUILD SUCCESSFUL"
    Expected Result: Clean build
    Evidence: Build output captured
  ```

  **Commit**: NO

---

- [x] 2. Remove all UI surface post-processing effects

  **What to do**:

  **Step 1: Simplify ensure functions (remove UI surface additions)**
  - `ensureCRTEffects()` (lines 240-249): Remove lines 242, 243-248 (all UI surface references). Function should only add CRT to mainSurface.
  - `ensureScanlineEffects()` (lines 256-265): Remove lines 258, 262-264 (all UI surface references). Function should only add scanline to mainSurface.
  - `ensureBloomEffects()` (lines 272-295): Remove lines 274, 285-294 (all UI surface references). Function should only add bloom to mainSurface.

  **Step 2: Simplify delete functions (remove UI surface deletions)**
  - `deleteCRTEffects()` (lines 251-254): Remove line 253 (UI surface CRT deletion).
  - `deleteScanlineEffects()` (lines 267-270): Remove line 269 (UI surface scanline deletion).
  - `deleteBloomEffects()` (lines 319-322): Remove line 321 (UI surface bloom deletion).

  **Step 3: Simplify update functions (remove UI effect references)**
  - `updateCRTEffectSettings()` (lines 331-348): Remove line 333 (uiEffect lookup). Remove uiEffect from `listOfNotNull` calls on lines 335 and 344.
  - `updateScanlineEffectSettings()` (lines 350-355): Remove line 352 (uiEffect lookup). Remove uiEffect from `listOfNotNull` call on line 354.
  - `updateBloomEffectSettings()` (lines 297-317): Remove line 300 (uiBloom lookup). Remove entire `uiBloom?.apply { ... }` block (lines 311-316).

  **Step 4: Remove dead helper functions**
  - Delete `hasUiCrtEffect()` (lines 359-360)
  - Delete `hasUiScanlineEffect()` (lines 364-365)
  - Delete `hasUiBloomEffect()` (lines 369-370)

  **Step 5: Simplify `onRender()` re-ensure loop (lines 670-684)**
  - CRT check (lines 670-674): Change `(!hasMainCrtEffect() || !hasUiCrtEffect())` to `!hasMainCrtEffect()`. Change `(hasMainCrtEffect() || hasUiCrtEffect())` to `hasMainCrtEffect()`.
  - Scanline check (lines 675-679): Same pattern - remove `hasUiScanlineEffect()` references.
  - Bloom check (lines 680-684): Same pattern - remove `hasUiBloomEffect()` references.

  **Step 6: Simplify debug overlay**
  - `renderCrtDebugOverlay()` (lines 2033-2044): Remove `crtUi` variable (line 2035). Remove `ui=$crtUi` from the CRT debug text. Remove `scanUi` and `bloomUi` variables and their `u=` debug text entries.

  **Step 7: Remove dead constants from companion object**
  - Remove `UI_CRT_EFFECT_NAME` (line 2376)
  - Remove `UI_SCANLINE_EFFECT_NAME` (line 2378)
  - Remove `UI_BLOOM_EFFECT_NAME` (line 2380)

  **Must NOT do**:
  - Do not modify shader files
  - Do not change `setupUiSurface()`
  - Do not change the rendering flow (which surface receives draw calls)
  - Do not modify mainSurface effect parameters
  - Do not refactor into generic abstraction
  - Do not change effect order values

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Mechanical code removal in one file - delete/simplify functions following a clear pattern
  - **Skills**: []
    - No special skills needed

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential - depends on Task 1
  - **Blocks**: None (final task)
  - **Blocked By**: Task 1

  **References**:

  **Pattern References**:
  - `src/main/kotlin/pacman/PacmanGame.kt:240-249` - `ensureCRTEffects()` - pattern to simplify (remove UI surface additions)
  - `src/main/kotlin/pacman/PacmanGame.kt:256-265` - `ensureScanlineEffects()` - same pattern
  - `src/main/kotlin/pacman/PacmanGame.kt:272-295` - `ensureBloomEffects()` - same pattern (note: UI had different params, acknowledged as acceptable change)
  - `src/main/kotlin/pacman/PacmanGame.kt:251-254` - `deleteCRTEffects()` - remove UI deletion line
  - `src/main/kotlin/pacman/PacmanGame.kt:267-270` - `deleteScanlineEffects()` - remove UI deletion line
  - `src/main/kotlin/pacman/PacmanGame.kt:319-322` - `deleteBloomEffects()` - remove UI deletion line
  - `src/main/kotlin/pacman/PacmanGame.kt:331-348` - `updateCRTEffectSettings()` - remove uiEffect references
  - `src/main/kotlin/pacman/PacmanGame.kt:350-355` - `updateScanlineEffectSettings()` - remove uiEffect references
  - `src/main/kotlin/pacman/PacmanGame.kt:297-317` - `updateBloomEffectSettings()` - remove uiBloom block
  - `src/main/kotlin/pacman/PacmanGame.kt:357-370` - Six `has*Effect()` helpers - delete the three UI ones
  - `src/main/kotlin/pacman/PacmanGame.kt:666-706` - `onRender()` - simplify re-ensure checks (lines 670-684)
  - `src/main/kotlin/pacman/PacmanGame.kt:2033-2044` - `renderCrtDebugOverlay()` - simplify debug text
  - `src/main/kotlin/pacman/PacmanGame.kt:2373-2381` - companion object constants - remove three UI constants

  **Acceptance Criteria**:

  **Agent-Executed QA Scenarios:**

  ```
  Scenario: Build succeeds after all changes
    Tool: Bash
    Preconditions: All UI effect code removed per steps 1-7
    Steps:
      1. Run: ./gradlew build
      2. Assert: exit code 0
      3. Assert: output contains "BUILD SUCCESSFUL"
    Expected Result: Clean build with zero errors
    Evidence: Build output captured

  Scenario: No UI effect references remain in codebase
    Tool: Bash (grep)
    Preconditions: Build succeeded
    Steps:
      1. Run: grep -n "UI_CRT_EFFECT_NAME\|UI_SCANLINE_EFFECT_NAME\|UI_BLOOM_EFFECT_NAME" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: zero matches (exit code 1, empty output)
      3. Run: grep -n "hasUiCrtEffect\|hasUiScanlineEffect\|hasUiBloomEffect" src/main/kotlin/pacman/PacmanGame.kt
      4. Assert: zero matches
      5. Run: grep -n "UI_CRT\|UI_SCANLINE\|UI_BLOOM" src/main/kotlin/pacman/PacmanGame.kt
      6. Assert: zero matches
    Expected Result: All UI effect dead code fully removed
    Evidence: Grep output captured

  Scenario: Main surface effects still present and functional
    Tool: Bash (grep)
    Preconditions: Build succeeded
    Steps:
      1. Run: grep -n "CRT_EFFECT_NAME\|SCANLINE_EFFECT_NAME\|BLOOM_EFFECT_NAME" src/main/kotlin/pacman/PacmanGame.kt
      2. Assert: matches found for main surface effect constants
      3. Run: grep -n "hasMainCrtEffect\|hasMainScanlineEffect\|hasMainBloomEffect" src/main/kotlin/pacman/PacmanGame.kt
      4. Assert: matches found for main surface helpers
    Expected Result: Main surface effects fully intact
    Evidence: Grep output captured
  ```

  **Commit**: YES
  - Message: `fix(effects): remove dual-surface post-processing to fix 2x CRT curvature`
  - Files: `src/main/kotlin/pacman/PacmanGame.kt`
  - Pre-commit: `./gradlew build`

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 2 | `fix(effects): remove dual-surface post-processing to fix 2x CRT curvature` | PacmanGame.kt | `./gradlew build` |

---

## Success Criteria

### Verification Commands
```bash
./gradlew build              # Expected: BUILD SUCCESSFUL
grep -c "UI_CRT\|UI_SCANLINE\|UI_BLOOM" src/main/kotlin/pacman/PacmanGame.kt  # Expected: 0
```

### Final Checklist
- [x] All "Must Have" present (effects on mainSurface only, dead code removed, debug simplified)
- [x] All "Must NOT Have" absent (no shader changes, no refactoring, no parameter changes)
- [x] Build passes (`./gradlew build`)
- [x] Visual: CRT shows 1x barrel distortion (user verifies after running game)
