package pulseman

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.surface.Surface
import no.njoh.pulseengine.core.scene.SceneEntity.Companion.DEAD
import no.njoh.pulseengine.modules.physics.BodyType
import no.njoh.pulseengine.modules.physics.bodies.PointBody
import no.njoh.pulseengine.modules.physics.shapes.PointShape
import no.njoh.pulseengine.modules.scene.entities.CommonSceneEntity

class PhysicsTrailParticle : CommonSceneEntity(), PointBody {
    override val shape = PointShape()

    override var bodyType = BodyType.DYNAMIC
    override var layerMask = 0
    override var collisionMask = 0
    override var restitution = 0f
    override var density = 0.1f
    override var friction = 0f
    override var drag = 0.02f

    var life = 0f
    var maxLife = 0f
    var size = 0f
    var red = 1f
    var green = 1f
    var blue = 1f
    private var counted = false

    init {
        setNot(DISCOVERABLE)
    }

    fun initialize(
        x: Float,
        y: Float,
        vx: Float,
        vy: Float,
        life: Float,
        size: Float,
        red: Float,
        green: Float,
        blue: Float,
        layerMask: Int,
        collisionMask: Int,
        restitution: Float,
        friction: Float,
        drag: Float,
        density: Float,
    ) {
        this.x = x
        this.y = y
        this.life = life
        this.maxLife = life
        this.size = size
        this.red = red
        this.green = green
        this.blue = blue
        this.layerMask = layerMask
        this.collisionMask = collisionMask
        this.restitution = restitution
        this.friction = friction
        this.drag = drag
        this.density = density
        val dt = 1f / 60f
        shape.init(x, y)
        shape.xLast = x - vx * dt
        shape.yLast = y - vy * dt
        shape.xAcc = 0f
        shape.yAcc = 0f
    }

    override fun onCreate() {
        if (!counted) {
            activeCount++
            counted = true
        }
    }

    override fun onFixedUpdate(engine: PulseEngine) {
        life -= engine.data.fixedDeltaTime
        if (life <= 0f) {
            expireNow()
            return
        }
        size *= SIZE_SHRINK_FACTOR
    }

    fun expireNow() {
        if (counted) {
            activeCount = (activeCount - 1).coerceAtLeast(0)
            counted = false
        }
        set(DEAD)
    }

    override fun onRender(engine: PulseEngine, surface: Surface) {
        val alpha = (life / maxLife).coerceIn(0f, 1f)
        val renderSize = size * (0.72f + alpha * 0.45f)
        surface.setDrawColor(red, green, blue, alpha)
        surface.drawQuad(x - renderSize * 0.5f, y - renderSize * 0.5f, renderSize, renderSize)
    }

    companion object {
        var activeCount = 0
            private set
        private const val SIZE_SHRINK_FACTOR = 0.992f
    }
}
