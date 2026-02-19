// Passthrough vertex shader for the scanline post-processing effect.
// Simply forwards the full-screen quad's texture coordinates to the fragment
// shader where the horizontal scanline pattern is applied.
#version 330 core

in vec2 position;    // Vertex position of the full-screen quad (clip space)
in vec2 texCoord;    // Texture coordinates [0..1] mapped to the screen

out vec2 uv;         // Passed to fragment shader as interpolated UV

void main()
{
    uv = texCoord;
    gl_Position = vec4(position, 0.0, 1.0);
}
