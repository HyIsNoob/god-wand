#version 150
// Silent Gate impact frame, the anime kind: for a frame or two the picture is ink, two tones and nothing between. Ink 1: what is lit is white on black;
// Ink 2: the same turned over, black on white. Outlines survive as lines, so shapes in the dark are still drawn. Ink 0 passes the frame through.
uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Ink;
uniform float Bar;
in vec2 texCoord;
out vec4 fragColor;

float lum(vec2 uv) {
    return dot(texture(DiffuseSampler, uv).rgb, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec3 scene = texture(DiffuseSampler, texCoord).rgb;
    // The scope bars stay black: the frame flips, not the screen.
    if (Ink < 0.5 || texCoord.y < Bar || texCoord.y > 1.0 - Bar) {
        fragColor = vec4(scene, 1.0);
        return;
    }
    vec2 px = 1.5 / OutSize;
    float edge = length(vec2(lum(texCoord + vec2(px.x, 0.0)) - lum(texCoord - vec2(px.x, 0.0)),
                             lum(texCoord + vec2(0.0, px.y)) - lum(texCoord - vec2(0.0, px.y))));
    // Lit or not, and the outlines drawn in the other tone: black lines inside what is white, white lines in the dark. A flat silhouette would lose the drawing.
    float tone = abs(smoothstep(0.30, 0.36, dot(scene, vec3(0.2126, 0.7152, 0.0722))) - smoothstep(0.04, 0.10, edge));
    if (Ink > 1.5) tone = 1.0 - tone;
    fragColor = vec4(vec3(tone), 1.0);
}
