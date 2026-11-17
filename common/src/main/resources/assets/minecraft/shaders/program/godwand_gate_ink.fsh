#version 150
// Silent Gate impact frame, the anime kind: for a frame or two the picture is ink, two tones and nothing between. Ink 1: what is lit is white on black;
// Ink 2: the same turned over, black on white. Outlines survive as lines, so shapes in the dark are still drawn. Ink 0 passes the frame through.
uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Ink;
uniform float Bar;
// Blue/Red/Purple: the world bent round Purple (a swirl, a pinch and a ripple), 0 leaves the frame untouched.
uniform float CenterX;
uniform float CenterY;
uniform float Warp;
uniform float WarpRadius;
uniform float WarpTime;
in vec2 texCoord;
out vec4 fragColor;

float lum(vec2 uv) {
    return dot(texture(DiffuseSampler, uv).rgb, vec3(0.2126, 0.7152, 0.0722));
}

vec2 warped(vec2 uv) {
    if (Warp <= 0.0) return uv;
    vec2 aspect = vec2(OutSize.x / OutSize.y, 1.0);
    vec2 c = vec2(CenterX, CenterY);
    vec2 d = (uv - c) * aspect;
    float r = length(d);
    float k = Warp * exp(-pow(r / max(WarpRadius, 0.001), 2.0));
    float a = k * 2.4;
    d = mat2(cos(a), -sin(a), sin(a), cos(a)) * d;
    d *= 1.0 - 0.3 * k + 0.04 * k * sin(r * 70.0 - WarpTime * 9.0);
    return c + d / aspect;
}

void main() {
    vec3 scene = texture(DiffuseSampler, warped(texCoord)).rgb;
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
