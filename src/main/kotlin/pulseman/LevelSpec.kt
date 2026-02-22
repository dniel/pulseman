package pulseman

/**
 * Per-level difficulty specification matching the original Pulse-Man arcade (1980).
 *
 * All speed values are expressed as fractions of [LevelProgression.MAX_SPEED] (1.0 = maximum).
 * Values sourced from the arcade dossier (Jamey Pittman), verified against arcade ROM analysis.
 *
 * @property pacSpeed Pulse-Man normal movement speed.
 * @property pacFrightSpeed Pulse-Man movement speed while ghosts are frightened.
 * @property ghostSpeed Ghost normal movement speed.
 * @property ghostFrightSpeed Ghost movement speed while frightened (0 = frightened disabled).
 * @property ghostTunnelSpeed Ghost movement speed inside the tunnel corridors.
 * @property frightTime Duration in seconds that ghosts remain frightened (0 = no frightened mode).
 * @property frightFlashes Number of blue/white flashes before frightened mode ends.
 * @property elroy1Dots Dots remaining threshold to activate Blinky Elroy 1 mode.
 * @property elroy1Speed Blinky speed during Elroy 1 mode.
 * @property elroy2Dots Dots remaining threshold to activate Blinky Elroy 2 mode.
 * @property elroy2Speed Blinky speed during Elroy 2 mode.
 * @property fruit Bonus fruit type that spawns on this level.
 * @property scatterChasePattern Index into [LevelProgression.SCATTER_CHASE_PATTERNS] for wave timing.
 */
data class LevelSpec(
    val pacSpeed: Float,
    val pacFrightSpeed: Float,
    val ghostSpeed: Float,
    val ghostFrightSpeed: Float,
    val ghostTunnelSpeed: Float,
    val frightTime: Float,
    val frightFlashes: Int,
    val elroy1Dots: Int,
    val elroy1Speed: Float,
    val elroy2Dots: Int,
    val elroy2Speed: Float,
    val fruit: FruitType,
    val scatterChasePattern: Int,
)

/**
 * Lookup table providing [LevelSpec] for each of the 21 distinct difficulty tiers.
 *
 * The original arcade has 255 playable levels before the kill screen at level 256.
 * Difficulty parameters are unique for levels 1 through 21; levels 22+ repeat level 21 settings.
 *
 * Three scatter/chase timing patterns exist:
 * - **Pattern 0** (level 1): Longer scatter phases, balanced for learning.
 * - **Pattern 1** (levels 2-4): Reduced late scatter, long chase dominance.
 * - **Pattern 2** (levels 5+): Shortest scatter phases, near-permanent chase.
 */
object LevelProgression {

    /**
     * Maximum entity speed in tiles per second (before [PulseManGame.gameSpeedScale]).
     *
     * All [LevelSpec] speed values are multiplied by this constant and the global game speed
     * scale to produce the actual movement rate. Calibrated so level-1 Pulse-Man at 80%
     * matches the original arcade feel.
     */
    const val MAX_SPEED = 8.75f

    /**
     * Scatter/chase wave timing patterns.
     *
     * Each array contains 8 durations alternating scatter/chase (seconds):
     * index 0 = scatter 1, index 1 = chase 1, index 2 = scatter 2, etc.
     * The final value is always [Float.MAX_VALUE] for infinite chase.
     */
    val SCATTER_CHASE_PATTERNS: Array<FloatArray> = arrayOf(
        floatArrayOf(7f, 20f, 7f, 20f, 5f, 20f, 5f, Float.MAX_VALUE),
        floatArrayOf(7f, 20f, 7f, 20f, 5f, 1033f, 1f / 60f, Float.MAX_VALUE),
        floatArrayOf(5f, 20f, 5f, 20f, 5f, 1037f, 1f / 60f, Float.MAX_VALUE),
    )

    private val SPECS: List<LevelSpec> = listOf(
        //                 pacSpd frtSpd gstSpd gFrtSpd gTunSpd frtTm fl  e1D  e1Spd e2D  e2Spd fruit                     scp
        /* Level  1 */ lv(0.80f, 0.90f, 0.75f, 0.50f,  0.40f,  6f,  5,  20,  0.80f, 10, 0.85f, FruitType.CHERRY,         0),
        /* Level  2 */ lv(0.90f, 0.95f, 0.85f, 0.55f,  0.45f,  5f,  5,  30,  0.90f, 15, 0.95f, FruitType.STRAWBERRY,     1),
        /* Level  3 */ lv(0.90f, 0.95f, 0.85f, 0.55f,  0.45f,  4f,  5,  40,  0.90f, 20, 0.95f, FruitType.ORANGE,         1),
        /* Level  4 */ lv(0.90f, 0.95f, 0.85f, 0.55f,  0.45f,  3f,  5,  40,  0.90f, 20, 0.95f, FruitType.ORANGE,         1),
        /* Level  5 */ lv(1.00f, 1.00f, 0.95f, 0.60f,  0.50f,  2f,  5,  40,  1.00f, 20, 1.05f, FruitType.APPLE,          2),
        /* Level  6 */ lv(1.00f, 1.00f, 0.95f, 0.60f,  0.50f,  5f,  5,  50,  1.00f, 25, 1.05f, FruitType.APPLE,          2),
        /* Level  7 */ lv(1.00f, 1.00f, 0.95f, 0.60f,  0.50f,  2f,  5,  50,  1.00f, 25, 1.05f, FruitType.MELON,          2),
        /* Level  8 */ lv(1.00f, 1.00f, 0.95f, 0.60f,  0.50f,  2f,  5,  50,  1.00f, 25, 1.05f, FruitType.MELON,          2),
        /* Level  9 */ lv(1.00f, 1.00f, 0.95f, 0.60f,  0.50f,  1f,  3,  60,  1.00f, 30, 1.05f, FruitType.GALAXIAN,       2),
        /* Level 10 */ lv(1.00f, 1.00f, 0.95f, 0.60f,  0.50f,  5f,  5,  60,  1.00f, 30, 1.05f, FruitType.GALAXIAN,       2),
        /* Level 11 */ lv(1.00f, 1.00f, 0.95f, 0.60f,  0.50f,  2f,  5,  60,  1.00f, 30, 1.05f, FruitType.BELL,           2),
        /* Level 12 */ lv(1.00f, 1.00f, 0.95f, 0.60f,  0.50f,  1f,  3,  80,  1.00f, 40, 1.05f, FruitType.BELL,           2),
        /* Level 13 */ lv(1.00f, 1.00f, 0.95f, 0.60f,  0.50f,  1f,  3,  80,  1.00f, 40, 1.05f, FruitType.KEY,            2),
        /* Level 14 */ lv(1.00f, 1.00f, 0.95f, 0.60f,  0.50f,  3f,  5,  80,  1.00f, 40, 1.05f, FruitType.KEY,            2),
        /* Level 15 */ lv(1.00f, 1.00f, 0.95f, 0.60f,  0.50f,  1f,  3, 100,  1.00f, 50, 1.05f, FruitType.KEY,            2),
        /* Level 16 */ lv(1.00f, 1.00f, 0.95f, 0.60f,  0.50f,  1f,  3, 100,  1.00f, 50, 1.05f, FruitType.KEY,            2),
        /* Level 17 */ lv(1.00f, 1.00f, 0.95f, 0.00f,  0.50f,  0f,  0, 100,  1.00f, 50, 1.05f, FruitType.KEY,            2),
        /* Level 18 */ lv(1.00f, 1.00f, 0.95f, 0.60f,  0.50f,  1f,  3, 100,  1.00f, 50, 1.05f, FruitType.KEY,            2),
        /* Level 19 */ lv(1.00f, 1.00f, 0.95f, 0.00f,  0.50f,  0f,  0, 120,  1.00f, 60, 1.05f, FruitType.KEY,            2),
        /* Level 20 */ lv(1.00f, 1.00f, 0.95f, 0.00f,  0.50f,  0f,  0, 120,  1.00f, 60, 1.05f, FruitType.KEY,            2),
        /* Level 21 */ lv(0.90f, 0.00f, 0.95f, 0.00f,  0.50f,  0f,  0, 120,  1.00f, 60, 1.05f, FruitType.KEY,            2),
    )

    /**
     * Returns the [LevelSpec] for the given level number (1-based).
     * Levels 22+ use level 21 settings (the final difficulty tier).
     */
    fun forLevel(level: Int): LevelSpec = SPECS[(level - 1).coerceIn(0, SPECS.lastIndex)]

    private fun lv(
        pacSpeed: Float, pacFrightSpeed: Float,
        ghostSpeed: Float, ghostFrightSpeed: Float, ghostTunnelSpeed: Float,
        frightTime: Float, frightFlashes: Int,
        elroy1Dots: Int, elroy1Speed: Float, elroy2Dots: Int, elroy2Speed: Float,
        fruit: FruitType, scatterChasePattern: Int,
    ) = LevelSpec(
        pacSpeed, pacFrightSpeed,
        ghostSpeed, ghostFrightSpeed, ghostTunnelSpeed,
        frightTime, frightFlashes,
        elroy1Dots, elroy1Speed, elroy2Dots, elroy2Speed,
        fruit, scatterChasePattern,
    )
}
