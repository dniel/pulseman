package pulseman

import no.njoh.pulseengine.core.graphics.api.Camera
import kotlin.random.Random

class CameraShakeManager {
    private var trauma = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    var strength = 1f
        set(value) {
            field = value.coerceIn(0f, 2f)
        }

    fun addImpulse(amount: Float) {
        trauma = (trauma + amount).coerceIn(0f, 1f)
    }

    fun update(dt: Float) {
        trauma = (trauma - dt * 1.15f).coerceAtLeast(0f)
        val shake = trauma * trauma * strength
        offsetX = (Random.nextFloat() * 2f - 1f) * shake * 24f
        offsetY = (Random.nextFloat() * 2f - 1f) * shake * 24f
    }

    fun apply(camera: Camera) {
        camera.position.x = offsetX
        camera.position.y = offsetY
    }
}
