# Issues & Gotchas: Modularize PacmanGame

## Issue 1: Local Variable Name Collision
**Task**: ScoreManager extraction (Task 2)

**Problem**: Lines 1115-1147 in PacmanGame have a local `var score = 0f` used in AI pathfinding heuristic

**Resolution**: This is NOT the game score — it's a pathfinding score variable. Left untouched during extraction.

**Lesson**: Always grep for variable names before extraction to identify all usages and distinguish between different contexts

---

## Issue 2: High Score Persistence Integration
**Task**: ScoreManager extraction (Task 2)

**Problem**: Line 150 loads highScore from JSON persistence — needed to redirect to scoreManager

**Resolution**: Changed `highScore = engine.data.loadObject<HighScoreData>("highscore.json")?.score ?: 0` to `scoreManager.highScore = ...`

**Lesson**: Check for persistence/serialization logic that touches extracted state

---

## Issue 3: Rendering Order Preservation
**Task**: ScoreManager extraction (Task 2)

**Problem**: Score popups must render at correct z-order in the render pipeline

**Resolution**: Kept `scoreManager.render(surface)` call in PacmanGame's main render loop at the same position

**Lesson**: Rendering order matters — don't move render calls unless you understand the z-order implications

## Issue 4: Phase-Dependent Effect Settings
**Task**: PostProcessingManager extraction (Task 3)

**Problem**: `updateBloomEffectSettings()` needed access to game phase state (`isGameplayVisualPhase()`) and frightened mode state (`frightenedTimer`, `dynamicFrightenedBloomEnabled`)

**Resolution**: Changed function signature to accept these as parameters instead of accessing PacmanGame state directly:
```kotlin
fun updateBloomEffectSettings(
    frightenedTimer: Float, 
    dynamicFrightenedBloomEnabled: Boolean, 
    isGameplayPhase: Boolean = true
)
```

**Lesson**: When extracting managers that need contextual state, pass it as parameters rather than creating tight coupling back to the main class

---

## Issue 5: ast_grep_replace Silent Failure
**Task**: FruitManager extraction (Task 5)

**Problem**: `ast_grep_replace` reported "3 replacement(s)" for `updateFruit(dt)` → `fruitManager.updateFruit(dt)` but the file was not actually modified. Same for `activeFruit` → `fruitManager.activeFruit`.

**Resolution**: Used Edit tool manually with unique surrounding context for each occurrence.

**Lesson**: Always verify ast_grep_replace results with a grep after applying. Don't trust the reported count — check the actual file content.

---

## Issue 6: Kotlin Property Initialization Order
**Task**: PacmanController extraction (Task 6)

**Problem**: `private val pacman = PacmanController(pacSpeed, gameSpeedScale)` was placed before `gameSpeedScale` and `pacSpeed` declarations in the class body. Kotlin initializes class properties in declaration order, so `pacman` would be initialized with uninitialized values.

**Resolution**: Moved `gameSpeedScale` and `pacSpeed` declarations to appear BEFORE `pacman` in the class body, then removed the duplicate declarations further down.

**Lesson**: When extracting a controller that takes constructor parameters from the host class, ensure those parameters are declared BEFORE the controller field.

---

## Issue 7: Callback Return Type Mismatch
**Task**: PacmanController extraction (Task 6)

**Problem**: `updatePacman()` callback `onFruitCheck: (Int, Int) -> Unit` but `fruitManager.checkFruitCollision()` returns `Boolean`. Method reference `fruitManager::checkFruitCollision` is `(Int, Int) -> Boolean`, incompatible with `(Int, Int) -> Unit`.

**Resolution**: Used lambda wrapper at call site: `{ col, row -> fruitManager.checkFruitCollision(col, row) }` — Kotlin discards the return value.

**Lesson**: When defining callback types for extracted functions, check the return types of the actual implementations being passed. Use `Unit` callbacks and wrap non-Unit functions in lambdas.

---

## Issue 8: Data Class Import Cascade Failure
**Task**: LightingManager extraction (Task 8)

**Problem**: Removed `Lamp` import from PacmanGame.kt when extracting all lighting code. But `LightPair` data class still referenced `Lamp` and remained in PacmanGame.kt. This caused `LightPair.first` and `LightPair.second` to have unknown type, which cascaded to 30+ "Unresolved reference 'intensity'/'x'/'y'/'rotation'" errors in LightingManager.kt when accessing `it.first.intensity` etc.

**Resolution**: Moved `LightPair` from PacmanGame.kt to LightingManager.kt (where Lamp import was present). Deleted from PacmanGame.kt.

**Lesson**: When removing imports from a file during extraction, grep ALL uses of the imported type in the REMAINING code — not just the functions being moved. Data classes that use the imported type must also be moved or the import kept.

---

---

