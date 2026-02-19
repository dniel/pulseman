package pacman

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Sound
import java.io.File

/**
 * Manages the loading and playback of game sound effects using the PulseEngine audio system.
 */
class SoundManager(private val engine: PulseEngine) {

    /**
     * Loads all required sound assets into the engine's asset manager.
     */
    fun loadAll() {
        listOf(
            "pacman_beginning",
            "pacman_chomp",
            "pacman_death",
            "pacman_eatfruit",
            "pacman_eatghost",
            "pacman_extrapac",
            "pacman_intermission",
        ).forEach(::loadAsset)
    }

    /**
     * Plays the sound effect associated with the given [name].
     */
    fun play(name: String) {
        engine.audio.playSound(name)
    }

    /**
     * Resolves the path and loads a single sound asset.
     */
    private fun loadAsset(name: String) {
        val filename = "$name.ogg"
        val path = resolvePath(filename) ?: return
        engine.asset.load(Sound(path, name))
    }

    /**
     * Attempts to find the absolute path for a sound file by searching through local
     * directories and classpath resources. Handles extracting resources from JARs if necessary.
     */
    private fun resolvePath(filename: String): String? {
        listOf(
            "src/main/resources/$filename",
            "sounds/$filename",
            filename,
        ).map(::File)
            .firstOrNull { it.isFile }
            ?.absolutePath
            ?.let { return it }

        val resource = javaClass.classLoader.getResource(filename) ?: return null
        return if (resource.protocol == "file") {
            File(resource.toURI()).absolutePath
        } else {
            val tempFile = File.createTempFile("pulsdniel-sound-", "-$filename")
            tempFile.deleteOnExit()
            resource.openStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.absolutePath
        }
    }
}
