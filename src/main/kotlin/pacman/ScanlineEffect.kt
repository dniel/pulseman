package pacman

import no.njoh.pulseengine.core.PulseEngineInternal
import no.njoh.pulseengine.core.asset.types.FragmentShader
import no.njoh.pulseengine.core.asset.types.VertexShader
import no.njoh.pulseengine.core.graphics.api.RenderTexture
import no.njoh.pulseengine.core.graphics.api.ShaderProgram
import no.njoh.pulseengine.core.graphics.postprocessing.effects.BaseEffect

/**
 * Post-processing effect that overlays horizontal CRT scanlines on the rendered frame.
 *
 * Generates a sine-wave pattern aligned to pixel rows (see `scanline.frag` for the math).
 * Each row is subtly dimmed at its center, creating the appearance of dark separator lines
 * between bright scanlines — matching the look of a real CRT monitor.
 *
 * Runs at [order] 97 in the pipeline, after [CRTEffect] (order 96). This means scanlines
 * are drawn on the already barrel-distorted image, so the lines curve with the CRT shape
 * rather than being applied to a flat image and then distorted.
 *
 * @property strength Controls scanline visibility. 0 = invisible, 1 = normal (18% dimming
 *   at row peaks), 2 = maximum. Clamped in the shader to prevent over-darkening.
 */
class ScanlineEffect(
    override val name: String = "scanline",
    override val order: Int = 97,
    var strength: Float = 1f,
) : BaseEffect() {

    /** Loads the scanline vertex/fragment shader pair from resources. */
    override fun loadShaderProgram(engine: PulseEngineInternal): ShaderProgram = ShaderProgram.create(
        engine.asset.loadNow(VertexShader("/shaders/effects/scanline.vert")),
        engine.asset.loadNow(FragmentShader("/shaders/effects/scanline.frag")),
    )

    /**
     * Applies the scanline effect to the input texture.
     *
     * Uploads the current [strength] and screen resolution as shader uniforms, then
     * draws a full-screen quad through the scanline shader. The shader generates a
     * per-pixel-row sine wave and uses it to dim alternating horizontal strips.
     * If no input texture is provided, passes through unchanged.
     */
    override fun applyEffect(engine: PulseEngineInternal, inTextures: List<RenderTexture>): List<RenderTexture> {
        val src = inTextures.firstOrNull() ?: return inTextures
        fbo.bind()
        fbo.clear()
        program.bind()
        program.setUniformSampler("baseTex", src)
        program.setUniform("resolution", src.width.toFloat(), src.height.toFloat())
        program.setUniform("strength", strength)
        renderer.draw()
        fbo.release()
        return fbo.getTextures()
    }
}
