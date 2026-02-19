// =============================================================================
// Scanline Fragment Shader — Horizontal CRT Scanline Simulation
// =============================================================================
//
// Simulates the horizontal scanlines visible on real CRT monitors. On a CRT,
// the electron beam scans left-to-right in discrete rows, with slight gaps
// between each row creating faint dark horizontal bands.
//
// This shader reproduces that pattern by generating a sine wave aligned to
// pixel rows and using it to subtly darken alternating horizontal strips.
//
// The effect is purely multiplicative — it only dims pixels, never brightens
// them — so it layers cleanly on top of any content.
// =============================================================================

#version 330 core

in vec2 uv;                    // Interpolated texture coordinates from vertex shader [0..1]
out vec4 fragColor;            // Final output color for this pixel

uniform sampler2D baseTex;     // The rendered frame (or output from prior effects like CRT)
uniform vec2 resolution;       // Screen resolution in pixels (width, height)
uniform float strength;        // Scanline intensity (0 = invisible, 1 = normal, 2 = max)

void main()
{
    vec4 src = texture(baseTex, uv);

    // Clamp strength to safe range to prevent over-darkening
    float s = clamp(strength, 0.0, 2.0);

    // --- Scanline pattern generation ---
    //
    // uv.y is in [0..1], so (uv.y * resolution.y) converts to pixel row index.
    // Multiplying by pi makes the sine wave complete exactly one full cycle
    // per pixel row:
    //   - At pixel row centers:   sin() peaks at +1  ->  line = 1.0 (brightest)
    //   - Between pixel rows:     sin() troughs at -1 ->  line = 0.0 (darkest)
    //
    // The 0.5 + 0.5 * sin() remaps from [-1,+1] to [0,1] so the pattern
    // oscillates between "full brightness" and "gap between rows."
    float line = 0.5 + 0.5 * sin(uv.y * resolution.y * 3.14159265);

    // --- Apply dimming ---
    //
    // dim = maximum darkening amount. At strength=1, this is 18% — a subtle
    // effect that's visible but doesn't overpower the image.
    //
    // scan = 1.0 - dim * line:
    //   - Where line = 0.0 (between rows): scan = 1.0 (no darkening)
    //   - Where line = 1.0 (at row peak):  scan = 1.0 - dim (darkened by up to 18%)
    //
    // This means the "gaps between rows" stay bright and the "row centers"
    // get slightly dimmed, which is the opposite of what you might expect —
    // but it creates the visual impression of dark separator lines between
    // bright scanlines, matching the CRT look.
    float dim = 0.18 * s;
    float scan = 1.0 - dim * line;

    // Multiply RGB by the scanline factor, preserve original alpha
    fragColor = vec4(src.rgb * scan, src.a);
}
