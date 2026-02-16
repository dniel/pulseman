package pacman

import no.njoh.pulseengine.core.PulseEngine
import no.njoh.pulseengine.core.graphics.postprocessing.effects.BloomEffect

class PostProcessingManager(private val engine: PulseEngine) {

    var crtEnabled = true
    var scanlineEnabled = true
    var bloomEnabled = true
    var crtStrength = 1f
    var scanlineStrength = 1f
    var bloomStrength = 0.5f

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

    fun ensureCRTEffects() {
        val mainSurface = engine.gfx.mainSurface
        if (mainSurface.getPostProcessingEffect(CRT_EFFECT_NAME) == null) {
            mainSurface.addPostProcessingEffect(CRTEffect(name = CRT_EFFECT_NAME))
        }
    }

    fun deleteCRTEffects() {
        engine.gfx.mainSurface.deletePostProcessingEffect(CRT_EFFECT_NAME)
    }

    fun ensureScanlineEffects() {
        val mainSurface = engine.gfx.mainSurface
        if (mainSurface.getPostProcessingEffect(SCANLINE_EFFECT_NAME) == null) {
            mainSurface.addPostProcessingEffect(ScanlineEffect(name = SCANLINE_EFFECT_NAME))
        }
    }

    fun deleteScanlineEffects() {
        engine.gfx.mainSurface.deletePostProcessingEffect(SCANLINE_EFFECT_NAME)
    }

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

    fun deleteBloomEffects() {
        engine.gfx.mainSurface.deletePostProcessingEffect(BLOOM_EFFECT_NAME)
    }

    fun updateBloomEffectSettings(frightenedTimer: Float, dynamicFrightenedBloomEnabled: Boolean, isGameplayPhase: Boolean = true) {
        val mainBloom = engine.gfx.mainSurface.getPostProcessingEffect(BLOOM_EFFECT_NAME) as? BloomEffect
        mainBloom?.apply {
            val frightenedBoost = if (dynamicFrightenedBloomEnabled && frightenedTimer > 0f) 0.4f else 0f
            intensity = (if (isGameplayPhase) 1.35f * bloomStrength else 0.95f * bloomStrength) + frightenedBoost
            threshold = if (isGameplayPhase) 0.78f else 0.9f
            thresholdSoftness = if (isGameplayPhase) 0.86f else 0.78f
            radius = if (isGameplayPhase) {
                0.0062f + (bloomStrength - 1f) * 0.0018f
            } else {
                0.0042f + (bloomStrength - 1f) * 0.0012f
            }
        }
    }

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

    fun updateScanlineEffectSettings() {
        val mainEffect = engine.gfx.mainSurface.getPostProcessingEffect(SCANLINE_EFFECT_NAME) as? ScanlineEffect
        val strength = if (scanlineEnabled) scanlineStrength else 0f
        mainEffect?.strength = strength
    }

    fun hasMainCrtEffect(): Boolean = engine.gfx.mainSurface.getPostProcessingEffect(CRT_EFFECT_NAME) != null

    fun hasMainScanlineEffect(): Boolean = engine.gfx.mainSurface.getPostProcessingEffect(SCANLINE_EFFECT_NAME) != null

    fun hasMainBloomEffect(): Boolean = engine.gfx.mainSurface.getPostProcessingEffect(BLOOM_EFFECT_NAME) != null

    companion object {
        private const val CRT_EFFECT_NAME = "pacman_crt"
        private const val SCANLINE_EFFECT_NAME = "pacman_scanline"
        private const val BLOOM_EFFECT_NAME = "pacman_bloom"
    }
}
