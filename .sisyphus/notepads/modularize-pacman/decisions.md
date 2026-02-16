# Architectural Decisions: Modularize PacmanGame

## Decision 1: Score vs Game Phase State Separation
**Context**: ScoreManager extraction (Task 2)

**Decision**: Keep `lives` and `level` in PacmanGame, move only `score`, `highScore`, `scorePopups` to ScoreManager

**Rationale**:
- `score` and `highScore` are pure scoring concerns
- `lives` and `level` are game phase state (affect game flow, not just display)
- Mixing them would violate single responsibility principle

**Impact**: ScoreManager remains focused on score tracking and display, PacmanGame retains game flow control

---

## Decision 2: Manager Initialization Timing
**Context**: SoundManager and ScoreManager initialization

**Decision**: 
- `soundManager` uses `lateinit var` and initializes in `onCreate()`
- `scoreManager` uses `val` and initializes at declaration

**Rationale**:
- SoundManager needs `engine` reference (available in onCreate)
- ScoreManager has no constructor dependencies (can initialize immediately)

**Impact**: Clear initialization pattern based on dependency requirements

---

## Decision 3: Commit Strategy
**Context**: Git commit organization

**Decision**: Separate commits for code changes vs orchestration metadata

**Rationale**:
- Code refactoring (ScoreManager.kt + PacmanGame.kt) is one atomic unit
- Orchestration metadata (.sisyphus/*, build.gradle.kts) is a separate concern
- Allows independent reversion if needed

**Impact**: Clean git history with clear separation of concerns
