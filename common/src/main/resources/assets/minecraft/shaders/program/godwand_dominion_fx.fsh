#version 150
// Block Dominion true screen-space cinematic post-process shader:
// 1. Expanding real-world refraction shockwave with chromatic dispersion (no flat 2D rings!)
// 2. Aerodynamic radial velocity motion blur + subtle prism edge during Mach/Jet flight
// 3. True scene-space sakuga impact frame using Sobel contours on the real 3D scene tinted by Material Resonance.

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float CenterX;
uniform float CenterY;
uniform float SpeedBlur;
uniform float ShockRadius;
uniform float ShockStrength;
uniform float ImpactMode;
uniform float AccentR;
uniform float AccentG;
uniform float AccentB;
uniform float VfxTime;

in vec2 texCoord;
out vec4 fragColor;

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    vec3 AccentColor = vec3(AccentR, AccentG, AccentB);
    vec2 uv = texCoord;
    vec2 aspect = vec2(OutSize.x / max(OutSize.y, 1.0), 1.0);
    vec2 center = vec2(CenterX, CenterY);
    vec2 delta = (uv - center) * aspect;
    float dist = length(delta);
    vec2 dir = dist > 1e-4 ? (delta / dist) : vec2(0.0);

    // 1. Real-scene spatial refraction shockwave (bends the actual 3D terrain & sky behind the wavefront)
    float caShift = 0.0;
    float rimHighlight = 0.0;
    if (ShockStrength > 0.002) {
        float diff = dist - ShockRadius;
        float band = exp(-pow(diff / 0.075, 2.0));
        float wave = -diff * band * ShockStrength * 0.62;
        uv = clamp(uv + (dir / aspect) * wave, vec2(0.001), vec2(0.999));
        caShift += band * ShockStrength * 0.0085;
        rimHighlight = exp(-pow(diff / 0.022, 2.0)) * ShockStrength * 0.24;
    }

    // 2. Aerodynamic radial motion blur during high-speed Mach / Jet flight (center stays razor-sharp!)
    vec3 scene;
    vec2 screenDelta = (uv - vec2(0.5)) * aspect;
    float screenDist = length(screenDelta);
    if (SpeedBlur > 0.005) {
        float edgeMask = smoothstep(0.18, 0.86, screenDist);
        vec2 blurStep = (uv - vec2(0.5)) * (SpeedBlur * 0.036 * edgeMask);
        vec3 acc = vec3(0.0);
        float wSum = 0.0;
        for (int i = 0; i < 7; i++) {
            float f = (float(i) / 6.0) - 0.5;
            float w = 1.0 - abs(f) * 1.1;
            vec2 suv = clamp(uv - blurStep * f, vec2(0.001), vec2(0.999));
            acc += texture(DiffuseSampler, suv).rgb * w;
            wSum += w;
        }
        scene = acc / max(wSum, 0.001);
        caShift += SpeedBlur * edgeMask * 0.0042;
    } else {
        scene = texture(DiffuseSampler, uv).rgb;
    }

    // Chromatic prism shift on shockwave crest & Mach flight edges
    if (caShift > 0.0004) {
        vec2 off = (dir / aspect) * caShift;
        float rC = texture(DiffuseSampler, clamp(uv + off, vec2(0.001), vec2(0.999))).r;
        float bC = texture(DiffuseSampler, clamp(uv - off, vec2(0.001), vec2(0.999))).b;
        scene = vec3(mix(scene.r, rC, 0.72), scene.g, mix(scene.b, bC, 0.72));
    }

    // Subtle refraction glint on the shockwave crest + mild cinema vignette at Mach speed
    if (rimHighlight > 0.001) {
        scene += mix(AccentColor, vec3(1.0), 0.45) * rimHighlight;
    }
    if (SpeedBlur > 0.01) {
        float vign = smoothstep(0.58, 1.18, screenDist) * SpeedBlur * 0.28;
        scene = mix(scene, scene * (0.55 + 0.45 * AccentColor), vign);
    }

    // 3. True Scene-Space Sakuga Impact Frame (Sobel contours on the real 3D world, zero fake 2D PNGs!)
    if (ImpactMode >= 0.5) {
        vec2 px = 1.6 / max(OutSize, vec2(1.0));
        float lumC = luma(scene);
        float lumL = luma(texture(DiffuseSampler, clamp(uv - vec2(px.x, 0.0), vec2(0.001), vec2(0.999))).rgb);
        float lumR = luma(texture(DiffuseSampler, clamp(uv + vec2(px.x, 0.0), vec2(0.001), vec2(0.999))).rgb);
        float lumD = luma(texture(DiffuseSampler, clamp(uv - vec2(0.0, px.y), vec2(0.001), vec2(0.999))).rgb);
        float lumU = luma(texture(DiffuseSampler, clamp(uv + vec2(0.0, px.y), vec2(0.001), vec2(0.999))).rgb);
        float edge = length(vec2(lumR - lumL, lumU - lumD));

        // Crisp two-tone anime shading + real 3D silhouette ink lines
        float lit = smoothstep(0.32, 0.40, lumC);
        float outline = smoothstep(0.045, 0.11, edge);
        float tone = abs(lit - outline);

        // Subtle organic radial fracture accent near the outer rim (never opaque fake stripes)
        float ang = atan(screenDelta.y, screenDelta.x);
        float radialCrack = pow(hash21(vec2(floor(ang * 26.0), floor(VfxTime * 20.0))), 6.0)
                          * smoothstep(0.36, 0.95, screenDist);
        tone = clamp(tone + radialCrack * 0.45, 0.0, 1.0);

        bool inverted = ImpactMode > 1.5;
        if (inverted) tone = 1.0 - tone;

        vec3 deepInk = AccentColor * 0.07 + vec3(0.012, 0.008, 0.020);
        vec3 resonantPaper = mix(AccentColor, vec3(1.0, 0.98, 0.94), inverted ? 0.62 : 0.38);
        vec3 sakuga = mix(deepInk, resonantPaper, tone);

        // Blend 92% sakuga so real 3D motion reads unmistakably without feeling like a static screen swap
        scene = mix(scene, sakuga, 0.92);
    }

    fragColor = vec4(scene, 1.0);
}
