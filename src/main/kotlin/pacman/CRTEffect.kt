package pacman

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.postprocessing.effects.BaseEffect

/**
 * Post-processing effect that simulates a curved CRT monitor.
 *
 * Applies three visual layers via GLSL shaders (see `crt.frag` for the math):
 * - **Barrel distortion**: bends the image outward from center, mimicking convex CRT glass
 * - **Vignette**: darkens corners/edges like a real CRT's brightness falloff
 * - **Edge masking**: fades border pixels to black for a clean rounded boundary
 *
 * Runs at [order] 96 in the post-processing pipeline, meaning it executes before
 * the [ScanlineEffect] (order 97) — distortion is applied first, then scanlines
 * are overlaid on the already-curved image.
 *
 * @property vignetteStrength Controls corner darkening intensity. 0 = no darkening,
 *   higher values darken edges more aggressively. The shader floors the effect at ~45%
 *   brightness to prevent unnaturally black corners.
 * @property curvature Controls barrel distortion amount. 0 = perfectly flat screen,
 *   0.035 = subtle CRT bulge (default). Higher values produce a more pronounced fisheye.
 */
class CRTEffect(
    override val name: String = "crt",
    override val order: Int = 96,
    var vignetteStrength: Float = 0.2f,
    var curvature: Float = 0.035f,
) : BaseEffect() {

    /** Loads the CRT vertex/fragment shader pair from resources. */
    override fun loadShaderProgram(engine: PulseEngineInternal): ShaderProgram = ShaderProgram.create(
        engine.asset.loadNow(VertexShader("/shaders/effects/crt.vert")),
        engine.asset.loadNow(FragmentShader("/shaders/effects/crt.frag")),
    )

    /**
     * Applies the CRT effect to the input texture.
     *
     * Binds the framebuffer, uploads the current [vignetteStrength] and [curvature]
     * as shader uniforms, draws a full-screen quad through the CRT shader, and
     * returns the processed texture. If no input texture is provided, passes through unchanged.
     */
    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture> {
        val src = inTextures.firstOrNull() ?: return inTextures
        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniformSampler("baseTex", src)
        program.setUniform("vignetteStrength", vignetteStrength)
        program.setUniform("curvature", curvature)
        renderer.draw()
        fbo.release()
        return fbo.getTextures()
    }
}
