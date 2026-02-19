package pulseman

/**
 * Selectable game mode controlling which maze layouts are used during level progression.
 *
 * - [CLASSIC] uses the single original maze for all levels (faithful to the 1980 arcade).
 * - [MS_PULSEMAN] rotates through four structurally distinct mazes following the
 *   Ms. Pac-Man arcade rotation: maze 1 for levels 1-2, maze 2 for 3-5, maze 3 for 6-9,
 *   maze 4 for 10-13, then mazes 3 and 4 alternate every 4 rounds indefinitely.
 */
enum class MazeMode { CLASSIC, MS_PULSEMAN }

/**
 * Defines a single maze layout including its ASCII grid, tunnel configuration, and wall color.
 *
 * Each layout is a 28-row, 28-column ASCII grid using the same character legend as [Maze]:
 * `#` wall, `.` dot, `o` power pellet, `_` empty, `H` ghost house, `-` ghost door, ` ` void.
 *
 * @property name Display name for the maze (shown in debug/service menu context).
 * @property ascii The 28-row ASCII layout. Each string must be exactly 28 characters.
 * @property tunnelRows Row indices where horizontal wrap-around is enabled.
 * @property wallColor RGB triplet (0-1 range) for the wall outline color.
 */
data class MazeLayout(
    val name: String,
    val ascii: Array<String>,
    val tunnelRows: Set<Int>,
    val wallColor: FloatArray,
)

/**
 * Registry of all available maze layouts and the level-to-maze mapping for each [MazeMode].
 *
 * Contains the original classic layout and four Ms. Pulse-Man layouts inspired by the
 * Ms. Pac-Man arcade (1982). The four Ms. layouts vary in wall structure, dot count,
 * and wall color while sharing the same ghost house region (rows 8-18).
 */
object MazeLayouts {

    /** The original 1980 Pac-Man maze — single layout used for all levels in [MazeMode.CLASSIC]. */
    val CLASSIC = MazeLayout(
        name = "Classic",
        ascii = arrayOf(
            "############################",  // 0
            "#............##............#",  // 1
            "#.####.#####.##.#####.####.#",  // 2
            "#o####.#####.##.#####.####o#",  // 3
            "#.####.#####.##.#####.####.#",  // 4
            "#..........................#",  // 5
            "#.####.##.########.##.####.#",  // 6
            "#......##....##....##......#",  // 7
            "######.#####_##_#####.######",  // 8
            "     #.#####_##_#####.#     ",  // 9
            "     #.##__________##.#     ",  // 10
            "     #.##_###--###_##.#     ",  // 11
            "######.##_#HHHHHH#_##.######",  // 12
            "______.___#HHHHHH#___.______",  // 13
            "######.##_#HHHHHH#_##.######",  // 14
            "     #.##_########_##.#     ",  // 15
            "     #.##__________##.#     ",  // 16
            "     #.##_########_##.#     ",  // 17
            "######.##_########_##.######",  // 18
            "#............##............#",  // 19
            "#.####.#####.##.#####.####.#",  // 20
            "#o..##.......__.......##..o#",  // 21
            "###.##.##.########.##.##.###",  // 22
            "#......##....##....##......#",  // 23
            "#.##########.##.##########.#",  // 24
            "#.##########.##.##########.#",  // 25
            "#..........................#",  // 26
            "############################",  // 27
        ),
        tunnelRows = setOf(13),
        wallColor = floatArrayOf(0.62f, 0.88f, 1f),
    )

    /** Ms. Pulse-Man maze 1 — open top corridors, shifted power pellets. Pink walls. */
    val MS_1_PINK = MazeLayout(
        name = "Pink",
        ascii = arrayOf(
            "############################",  // 0
            "#............##............#",  // 1
            "#.####.#####.##.#####.####.#",  // 2
            "#.####.#####.##.#####.####.#",  // 3
            "#o........................o#",  // 4
            "#.####.##.########.##.####.#",  // 5
            "#......##....##....##......#",  // 6
            "#.####.##.##.##.##.##.####.#",  // 7
            "######.#####_##_#####.######",  // 8
            "     #.#####_##_#####.#     ",  // 9
            "     #.##__________##.#     ",  // 10
            "     #.##_###--###_##.#     ",  // 11
            "######.##_#HHHHHH#_##.######",  // 12
            "______.___#HHHHHH#___.______",  // 13
            "######.##_#HHHHHH#_##.######",  // 14
            "     #.##_########_##.#     ",  // 15
            "     #.##__________##.#     ",  // 16
            "     #.##_########_##.#     ",  // 17
            "######.##_########_##.######",  // 18
            "#..........................#",  // 19
            "#.####.##.########.##.####.#",  // 20
            "#.####.##....__....##.####.#",  // 21
            "#o.....##.##.##.##.##.....o#",  // 22
            "#.######.##......##.######.#",  // 23
            "#........##.####.##........#",  // 24
            "#.######.##.####.##.######.#",  // 25
            "#..........................#",  // 26
            "############################",  // 27
        ),
        tunnelRows = setOf(13),
        wallColor = floatArrayOf(1f, 0.5f, 0.7f),
    )

    /** Ms. Pulse-Man maze 2 — wide open design, maximum dots. Cyan walls. */
    val MS_2_BLUE = MazeLayout(
        name = "Blue",
        ascii = arrayOf(
            "############################",  // 0
            "#..........................#",  // 1
            "#.####.####.####.####.####.#",  // 2
            "#o........................o#",  // 3
            "#.####.####.####.####.####.#",  // 4
            "#..........................#",  // 5
            "#.##.####.########.####.##.#",  // 6
            "#.##......##....##......##.#",  // 7
            "######.#####_##_#####.######",  // 8
            "     #.#####_##_#####.#     ",  // 9
            "     #.##__________##.#     ",  // 10
            "     #.##_###--###_##.#     ",  // 11
            "######.##_#HHHHHH#_##.######",  // 12
            "______.___#HHHHHH#___.______",  // 13
            "######.##_#HHHHHH#_##.######",  // 14
            "     #.##_########_##.#     ",  // 15
            "     #.##__________##.#     ",  // 16
            "     #.##_########_##.#     ",  // 17
            "######.##_########_##.######",  // 18
            "#..........................#",  // 19
            "#.##.####.########.####.##.#",  // 20
            "#.##......##....##......##.#",  // 21
            "#.####.##.##.__.##.##.####.#",  // 22
            "#o.....##..........##.....o#",  // 23
            "#.####.##.########.##.####.#",  // 24
            "#......##....##....##......#",  // 25
            "#..........................#",  // 26
            "############################",  // 27
        ),
        tunnelRows = setOf(13),
        wallColor = floatArrayOf(0.4f, 0.9f, 1f),
    )

    /** Ms. Pulse-Man maze 3 — vertical emphasis, wider center block. Orange walls. */
    val MS_3_ORANGE = MazeLayout(
        name = "Orange",
        ascii = arrayOf(
            "############################",  // 0
            "#...........####...........#",  // 1
            "#.####.####.####.####.####.#",  // 2
            "#.####.####......####.####.#",  // 3
            "#......####.####.####......#",  // 4
            "#o####......####......####o#",  // 5
            "#.####.####.####.####.####.#",  // 6
            "#..........._##_...........#",  // 7
            "######.#####_##_#####.######",  // 8
            "     #.#####_##_#####.#     ",  // 9
            "     #.##__________##.#     ",  // 10
            "     #.##_###--###_##.#     ",  // 11
            "######.##_#HHHHHH#_##.######",  // 12
            "______.___#HHHHHH#___.______",  // 13
            "######.##_#HHHHHH#_##.######",  // 14
            "     #.##_########_##.#     ",  // 15
            "     #.##__________##.#     ",  // 16
            "     #.##_########_##.#     ",  // 17
            "######.##_########_##.######",  // 18
            "#...........####...........#",  // 19
            "#.####.####.####.####.####.#",  // 20
            "#.####.####......####.####.#",  // 21
            "#......####.####.####......#",  // 22
            "#o####......####......####o#",  // 23
            "#.####.####.####.####.####.#",  // 24
            "#..........................#",  // 25
            "#.########..####..########.#",  // 26
            "############################",  // 27
        ),
        tunnelRows = setOf(13),
        wallColor = floatArrayOf(1f, 0.6f, 0.2f),
    )

    /** Ms. Pulse-Man maze 4 — grid pattern, symmetric corridors. Deep blue walls. */
    val MS_4_DARK_BLUE = MazeLayout(
        name = "Dark Blue",
        ascii = arrayOf(
            "############################",  // 0
            "#............##............#",  // 1
            "#.####.####..##..####.####.#",  // 2
            "#.####.####..##..####.####.#",  // 3
            "#..........................#",  // 4
            "###.##.##.########.##.##.###",  // 5
            "#o....##.....__.....##....o#",  // 6
            "#.####.#####.##.#####.####.#",  // 7
            "######.#####_##_#####.######",  // 8
            "     #.#####_##_#####.#     ",  // 9
            "     #.##__________##.#     ",  // 10
            "     #.##_###--###_##.#     ",  // 11
            "######.##_#HHHHHH#_##.######",  // 12
            "______.___#HHHHHH#___.______",  // 13
            "######.##_#HHHHHH#_##.######",  // 14
            "     #.##_########_##.#     ",  // 15
            "     #.##__________##.#     ",  // 16
            "     #.##_########_##.#     ",  // 17
            "######.##_########_##.######",  // 18
            "#............##............#",  // 19
            "#.####.####..##..####.####.#",  // 20
            "#......####..__..####......#",  // 21
            "###.##.##.########.##.##.###",  // 22
            "#o....##.....__.....##....o#",  // 23
            "#.####.#####.##.#####.####.#",  // 24
            "#.####.#####.##.#####.####.#",  // 25
            "#..........................#",  // 26
            "############################",  // 27
        ),
        tunnelRows = setOf(13),
        wallColor = floatArrayOf(0.3f, 0.5f, 1f),
    )

    /**
     * Returns the appropriate [MazeLayout] for the given [level] and [mode].
     *
     * In [MazeMode.CLASSIC], always returns [CLASSIC].
     * In [MazeMode.MS_PULSEMAN], follows the Ms. Pac-Man arcade rotation:
     * - Levels 1-2: [MS_1_PINK]
     * - Levels 3-5: [MS_2_BLUE]
     * - Levels 6-9: [MS_3_ORANGE]
     * - Levels 10-13: [MS_4_DARK_BLUE]
     * - Level 14+: [MS_3_ORANGE] and [MS_4_DARK_BLUE] alternate every 4 rounds.
     */
    fun forLevel(level: Int, mode: MazeMode): MazeLayout = when (mode) {
        MazeMode.CLASSIC -> CLASSIC
        MazeMode.MS_PULSEMAN -> when {
            level <= 2 -> MS_1_PINK
            level <= 5 -> MS_2_BLUE
            level <= 9 -> MS_3_ORANGE
            level <= 13 -> MS_4_DARK_BLUE
            else -> {
                val cycle = (level - 14) / 4
                if (cycle % 2 == 0) MS_3_ORANGE else MS_4_DARK_BLUE
            }
        }
    }
}
