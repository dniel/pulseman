package pulseman

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.HIDDEN
import no.njoh.pulseengine.modules.physics.BodyType
import no.njoh.pulseengine.modules.physics.bodies.PolygonBody
import no.njoh.pulseengine.modules.physics.shapes.RectangleShape
import no.njoh.pulseengine.modules.scene.entities.CommonSceneEntity

class PhysicsWallCollider(
    xCenter: Float,
    yCenter: Float,
    widthPx: Float,
    heightPx: Float,
    wallLayerMask: Int,
) : CommonSceneEntity(), PolygonBody {

    override var shape = RectangleShape()
    override var bodyType = BodyType.STATIC
    override var layerMask = wallLayerMask
    override var collisionMask = 0
    override var restitution = 0f
    override var density = 1f
    override var friction = 0.6f
    override var drag = 0f

    init {
        x = xCenter
        y = yCenter
        width = widthPx
        height = heightPx
        set(HIDDEN)
    }

    override fun onCreate() {
        shape.init(x, y, width, height, rotation, density)
    }

    override fun onCollision(engine: PulseEngine, otherBody: no.njoh.pulseengine.modules.physics.bodies.PhysicsBody, result: no.njoh.pulseengine.modules.physics.ContactResult) {
    }
}
