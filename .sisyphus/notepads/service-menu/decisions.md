# Service Menu - Decisions

## [2026-02-16T15:55:38] Plan Initialized

### Menu Architecture
- Render on `uiSurface` with 0.85 alpha dark overlay
- Available during ALL game phases (service console concept)
- Menu open blocks ALL non-menu input (prevents ENTER/R bypass bugs)
- Cursor wraps around, skips Header items
- uiPulseTime continues during pause (early-return placed AFTER `+= dt`)

### Safety Measures
- `serviceMenuOpen = false` resets in: resetGame(), startNewGameFromStartup(), enterStartScreen()
- Cursor initialized to first non-Header index (index 1)
