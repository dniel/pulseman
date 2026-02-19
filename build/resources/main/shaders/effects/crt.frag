// =============================================================================
// CRT Fragment Shader — Barrel Distortion + Vignette + Edge Masking
// =============================================================================
//
// Simulates a curved CRT monitor by applying three layered effects:
//
//   1. BARREL DISTORTION (warp function)
//      Bends the image outward from the center, mimicking the convex glass
//      of a CRT tube. Pixels farther from center are displaced more.
//
//   2. VIGNETTE
//      Darkens the corners and edges of the screen, reproducing the natural
//      brightness falloff of a CRT's electron beam at the periphery.
//
//   3. EDGE MASKING
//      Fades pixels near the border to black/transparent, creating a clean
//      rounded-rectangle boundary. Without this, barrel distortion would
//      cause visible clamping artifacts at the texture edges.
//
// Applied as a full-screen post-processing pass: the entire rendered frame
// is sampled through warped UV coordinates, then composited with vignette
// darkening and edge fade.
// =============================================================================

#version 330 core

in vec2 uv;                       // Interpolated texture coordinates from vertex shader [0..1]
out vec4 fragColor;               // Final output color for this pixel

uniform sampler2D baseTex;        // The rendered frame to apply the CRT effect to
uniform float vignetteStrength;   // How aggressively corners darken (0 = none, higher = darker edges)
uniform float curvature;          // Barrel distortion intensity (0 = flat, 0.035 = subtle CRT bulge)

// -----------------------------------------------------------------------------
// Barrel Distortion
// -----------------------------------------------------------------------------
// Maps flat UV coordinates to curved ones, simulating a convex CRT screen.
//
// How it works:
//   1. Remap UV from [0,1] to [-1,1] so the center of the screen is at origin
//   2. Compute squared distance from center: r2 = x*x + y*y
//   3. Scale coordinates by (1 + curvature * r2) — pushes pixels outward
//      proportionally to their distance from center (quadratic falloff)
//   4. Remap back to [0,1] UV space
//
// The quadratic term means center pixels barely move while edge pixels
// shift significantly, creating the characteristic CRT "bulge."
// -----------------------------------------------------------------------------
vec2 warp(vec2 p)
{
    vec2 c = p * 2.0 - 1.0;       // Remap [0,1] -> [-1,1], center at origin
    float r2 = dot(c, c);         // Squared distance from center (x*x + y*y)
    c *= 1.0 + curvature * r2;    // Apply barrel distortion — farther pixels move more
    return c * 0.5 + 0.5;         // Remap [-1,1] -> [0,1] back to UV space
}

void main()
{
    // --- Step 1: Apply barrel distortion to UV coordinates ---
    vec2 warpedUv = warp(uv);
    vec2 sampleUv = clamp(warpedUv, 0.0, 1.0);   // Prevent sampling outside texture bounds

    // --- Step 2: Sample the rendered frame at the warped position ---
    vec4 src = texture(baseTex, sampleUv);
    vec3 baseColor = src.rgb;
    float baseAlpha = src.a;

    // --- Step 3: Vignette — darken corners based on distance from center ---
    //
    // The trick: uv * (1 - uv) creates a parabola peaking at 0.5 and zero
    // at edges. Multiplying x and y components gives a 2D radial falloff.
    // The * 16.0 normalizes the peak to 1.0:
    //   At center: 0.5 * 0.5 * 0.5 * 0.5 * 16 = 1.0
    //   At edges:  approaches 0.0
    //
    // pow() controls falloff steepness — higher vignetteStrength = larger
    // exponent = brightness drops off faster toward edges.
    //
    // The final mix() ensures:
    //   - vignetteStrength ~ 0  ->  vig ~ 1.0 (no darkening)
    //   - vignetteStrength > 0  ->  corners darken, but floor at 0.45
    //     prevents pure black corners (which look unnatural on a real CRT)
    vec2 v = sampleUv * (1.0 - sampleUv.yx);
    float vigRaw = pow(clamp(v.x * v.y * 16.0, 0.0, 1.0), 0.35 + vignetteStrength * 1.1);
    float vig = mix(1.0, max(vigRaw, 0.45), clamp(vignetteStrength, 0.0, 1.0));

    // --- Step 4: Edge masking — fade to black at screen border ---
    //
    // After barrel distortion, pixels near the original edge may sample
    // clamped/repeated texture data, creating visual artifacts. Four
    // smoothstep() calls create a soft rectangular mask that fades to 0
    // within 2% of each edge. Multiplying all four produces a smooth
    // rounded-rectangle alpha mask.
    float edge = smoothstep(0.0, 0.02, warpedUv.x) *      // Left edge fade
                 smoothstep(0.0, 0.02, warpedUv.y) *      // Bottom edge fade
                 smoothstep(0.0, 0.02, 1.0 - warpedUv.x) * // Right edge fade
                 smoothstep(0.0, 0.02, 1.0 - warpedUv.y);  // Top edge fade

    // --- Step 5: Composite all effects ---
    vec3 crtColor = baseColor * vig;              // Apply vignette darkening
    vec3 color = mix(baseColor, crtColor, 0.85);  // 85% CRT + 15% original (softens effect slightly)
    color *= mix(0.5, 1.0, edge);                 // Darken near border (CRT bezel shadow)
    float alpha = baseAlpha * edge;                // Fade alpha at border for clean compositing

    fragColor = vec4(color, alpha);
}
