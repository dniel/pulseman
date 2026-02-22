package pulseman

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.postprocessing.effects.BloomEffect

/**
 * Manages post-processing effects for the game, including CRT emulation, scanlines, and bloom.
 * Provides methods to enable, disable, and dynamically adjust effect parameters.
 */
class PostProcessingManager(private val engine: PulseEngine) {

    /** Whether the CRT (curvature and vignette) effect is enabled. */
    var crtEnabled = true

    /** Whether the scanline effect is enabled. */
    var scanlineEnabled = true

    /** Whether the bloom (glow) effect is enabled. */
    var bloomEnabled = true

    /** The intensity of the CRT effect. */
    var crtStrength = 1f

    /** The intensity of the scanline effect. */
    var scanlineStrength = 1f

    /** The intensity of the bloom effect. */
    var bloomStrength = 0.5f

    var auraBloomThreshold = 0.5f

    /**
     * Configures and initializes all enabled post-processing effects on the main surface.
     */
    fun configurePostEffects() {
        if (crtEnabled) ensureCRTEffects()
        updateCRTEffectSettings()
        if (scanlineEnabled) {
            ensureScanlineEffects()
            updateScanlineEffectSettings()
        }
        if (bloomEnabled) {
            ensureBloomEffects()
            updateBloomEffectSettings(0f, false)
        }
    }

    /**
     * Ensures the CRT effect is added to the main rendering surface.
     */
    fun ensureCRTEffects() {
        val mainSurface = engine.gfx.mainSurface
        if (mainSurface.getPostProcessingEffect(CRT_EFFECT_NAME) == null) {
            mainSurface.addPostProcessingEffect(CRTEffect(name = CRT_EFFECT_NAME))
        }
    }

    /**
     * Removes the CRT effect from the main rendering surface.
     */
    fun deleteCRTEffects() {
        engine.gfx.mainSurface.deletePostProcessingEffect(CRT_EFFECT_NAME)
    }

    /**
     * Ensures the scanline effect is added to the main rendering surface.
     */
    fun ensureScanlineEffects() {
        val mainSurface = engine.gfx.mainSurface
        if (mainSurface.getPostProcessingEffect(SCANLINE_EFFECT_NAME) == null) {
            mainSurface.addPostProcessingEffect(ScanlineEffect(name = SCANLINE_EFFECT_NAME))
        }
    }

    /**
     * Removes the scanline effect from the main rendering surface.
     */
    fun deleteScanlineEffects() {
        engine.gfx.mainSurface.deletePostProcessingEffect(SCANLINE_EFFECT_NAME)
    }

    /**
     * Ensures the bloom effect is added to the main rendering surface with default settings.
     */
    fun ensureBloomEffects() {
        val mainSurface = engine.gfx.mainSurface
        if (mainSurface.getPostProcessingEffect(BLOOM_EFFECT_NAME) == null) {
            mainSurface.addPostProcessingEffect(
                BloomEffect(name = BLOOM_EFFECT_NAME).apply {
                    intensity = 0.85f
                    threshold = 1.05f
                    thresholdSoftness = 0.7f
                    radius = 0.0038f
                }
            )
        }
    }

    /**
     * Removes the bloom effect from the main rendering surface.
     */
    fun deleteBloomEffects() {
        engine.gfx.mainSurface.deletePostProcessingEffect(BLOOM_EFFECT_NAME)
    }

    /**
     * Dynamically updates bloom effect parameters based on the current game phase and state.
     * Can increase intensity when ghosts are in frightened mode.
     */
    fun updateBloomEffectSettings(frightenedTimer: Float, dynamicFrightenedBloomEnabled: Boolean, isGameplayPhase: Boolean = true) {
        val mainBloom = engine.gfx.mainSurface.getPostProcessingEffect(BLOOM_EFFECT_NAME) as? BloomEffect
        mainBloom?.apply {
            val frightenedBoost = if (dynamicFrightenedBloomEnabled && frightenedTimer > 0f) 0.4f else 0f
            intensity = (if (isGameplayPhase) 1.35f * bloomStrength else 0.95f * bloomStrength) + frightenedBoost
            threshold = if (isGameplayPhase) auraBloomThreshold else 0.9f
            thresholdSoftness = if (isGameplayPhase) 0.86f else 0.78f
            radius = if (isGameplayPhase) {
                0.0062f + (bloomStrength - 1f) * 0.0018f
            } else {
                0.0042f + (bloomStrength - 1f) * 0.0012f
            }
        }
    }

    /**
     * Updates CRT effect parameters like vignette strength and screen curvature.
     */
    fun updateCRTEffectSettings() {
        val mainEffect = engine.gfx.mainSurface.getPostProcessingEffect(CRT_EFFECT_NAME) as? CRTEffect
        if (!crtEnabled) {
            mainEffect?.apply {
                vignetteStrength = 0f
                curvature = 0f
            }
            return
        }

        val vignetteBase = 0.2f
        val curvatureBase = 0.035f
        mainEffect?.apply {
            vignetteStrength = vignetteBase * crtStrength
            curvature = curvatureBase * crtStrength
        }
    }

    /**
     * Updates the strength of the scanline effect.
     */
    fun updateScanlineEffectSettings() {
        val mainEffect = engine.gfx.mainSurface.getPostProcessingEffect(SCANLINE_EFFECT_NAME) as? ScanlineEffect
        val strength = if (scanlineEnabled) scanlineStrength else 0f
        mainEffect?.strength = strength
    }

    /** @return True if the CRT effect is currently active on the main surface. */
    fun hasMainCrtEffect(): Boolean = engine.gfx.mainSurface.getPostProcessingEffect(CRT_EFFECT_NAME) != null

    /** @return True if the scanline effect is currently active on the main surface. */
    fun hasMainScanlineEffect(): Boolean = engine.gfx.mainSurface.getPostProcessingEffect(SCANLINE_EFFECT_NAME) != null

    /** @return True if the bloom effect is currently active on the main surface. */
    fun hasMainBloomEffect(): Boolean = engine.gfx.mainSurface.getPostProcessingEffect(BLOOM_EFFECT_NAME) != null

    companion object {
        private const val CRT_EFFECT_NAME = "pulseman_crt"
        private const val SCANLINE_EFFECT_NAME = "pulseman_scanline"
        private const val BLOOM_EFFECT_NAME = "pulseman_bloom"
    }
}
