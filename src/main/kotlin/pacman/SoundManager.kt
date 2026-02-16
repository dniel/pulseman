package pacman

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.asset.types.Sound
import java.io.File

class SoundManager(private val engine: PulseEngine) {
    
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
    
    fun play(name: String) {
        engine.audio.playSound(name)
    }
    
    private fun loadAsset(name: String) {
        val filename = "$name.ogg"
        val path = resolvePath(filename) ?: return
        engine.asset.load(Sound(path, name))
    }
    
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
