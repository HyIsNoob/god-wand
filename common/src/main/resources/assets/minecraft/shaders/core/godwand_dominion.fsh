#version 150
// God Wand Block Dominion — Purple-grade volumetric procedural GLSL shader.
// Designed for two-pass rendering:
//   Pass 1 (BODY, alpha blend): billowing dark-gold/obsidian smoke, torn ink/gold brush strokes,
//          churning liquid plasma currents, dark-core lightning.
//   Pass 2 (GLOW, additive): soft volumetric light wash, luminous plasma veins, hot golden-white
//          lightning cores, organic dissolving hex shield shimmer, and GPU 3D sparks/embers/wisps.
//
// UV encoding:
//   effect = int(floor(texCoord0.y)) - 2
//   seed   = floor(texCoord0.x)
//   p      = clamp((fract(texCoord0) - 0.001) / 0.998, 0.0, 1.0)

uniform vec4 ColorModulator;
uniform float VfxTime;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.11, 0.17, 0.13));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x),
            mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
        mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
            mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y),
        f.z
    );
}

float fbm(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

float hexEdgeDist(vec2 p) {
    const vec2 s = vec2(1.0, 1.7320508);
    vec4 hC = floor(vec4(p, p - vec2(0.5, 1.0)) / s.xyxy) + 0.5;
    vec4 h = vec4(p - hC.xy * s, p - (hC.zw + 0.5) * s);
    vec2 g = dot(h.xy, h.xy) < dot(h.zw, h.zw) ? h.xy : h.zw;
    vec2 a = abs(g);
    float d = max(dot(a, normalize(s)), a.x);
    return 0.5 - d;
}

vec3 ramp(vec3 tint, float v) {
    float lum = dot(tint, vec3(0.3, 0.59, 0.11));
    // Ink stays deep obsidian ink with warm ember-amber cracks; bright tints lift to molten white-gold.
    vec3 dark = tint * 0.18;
    vec3 hot = lum < 0.08
        ? tint * 1.8 + vec3(0.24, 0.14, 0.02)
        : mix(tint, vec3(1.0, 0.98, 0.90), 0.68);
    return v < 0.5 ? mix(dark, tint, v * 2.0) : mix(tint, hot, (v - 0.5) * 2.0);
}

void main() {
    if (texCoord0.y < 2.0) {
        fragColor = vertexColor * ColorModulator;
        if (fragColor.a < 0.003) discard;
        return;
    }

    int effect = int(floor(texCoord0.y)) - 2;
    vec2 p = clamp((fract(texCoord0) - 0.001) / 0.998, 0.0, 1.0);
    float seed = floor(texCoord0.x);
    float t = VfxTime + seed * 1.37;
    vec3 tint = vertexColor.rgb;
    vec2 q = p * 2.0 - 1.0;
    vec3 col = tint;
    float a = 0.0;

    if (effect == 0) {
        // SMOKE: billowing self-shaded volumetric dust / golden-obsidian smoke puff (Purple standard)
        float d = length(q);
        float w = fbm(vec3(q * 1.8, t * 0.28 + seed));
        float n = fbm(vec3(q * 2.4 + (w - 0.5) * 0.7, t * 0.36 + seed + 3.1));
        float mask = smoothstep(1.0, 0.14, d + (n - 0.5) * 0.82);
        col = ramp(tint, clamp(0.24 + n * 0.62, 0.0, 1.0));
        a = mask * (0.48 + n * 0.52);
    } else if (effect == 1) {
        // PLASMA: liquid energy ribbon (p.x = 0 tail -> 1 head, q.y = across), eroded organically by churning noise
        float warp = fbm(vec3(p.x * 2.2 - t * 1.8, q.y * 1.5, seed * 0.41));
        float n = fbm(vec3(p.x * 3.4 - t * 2.6 + warp * 0.85, q.y * 2.6 + warp, seed * 0.73));
        float edgeN = fbm(vec3(p.x * 4.5 - t * 1.9, q.y * 0.6 + seed * 0.2, t * 0.75));
        float across = 1.0 - abs(q.y + (warp - 0.5) * 0.36);
        float erosion = 0.14 + 0.48 * (1.0 - smoothstep(0.0, 0.45, p.x));
        float m = across * (0.76 + 0.52 * edgeN) - erosion * (1.0 - n);
        a = smoothstep(0.06, 0.44, m) * smoothstep(0.0, 0.08, p.x) * smoothstep(1.0, 0.88, p.x);
        float hot = smoothstep(0.58, 0.84, n) * (0.35 + 0.65 * p.x);
        col = mix(tint * 0.42, tint, smoothstep(0.10, 0.60, m)) + mix(tint, vec3(1.0, 0.98, 0.90), 0.72) * hot * 0.85;
    } else if (effect == 2) {
        // BRUSH: torn anime ink / molten-gold stroke with dry-brush bristles & ragged outline
        float warp = fbm(vec3(p.x * 2.1 - t * 1.3, seed * 0.37, 0.5));
        float qy = q.y + (warp - 0.5) * 0.55;
        float across = 1.0 - abs(qy);
        float striae = fbm(vec3(p.x * 1.8 - t * 1.5, qy * 3.8 + warp * 2.0, seed * 1.3));
        float edgeN = noise(vec3(p.x * 8.5 - t * 1.6, qy > 0.0 ? seed : seed + 5.0, t * 0.7));
        float edge = 0.06 + 0.58 * edgeN * (1.0 - 0.42 * p.x);
        float body = smoothstep(edge, edge + 0.04, across);
        float bristle = smoothstep(0.38, 0.56, striae + 0.42 * smoothstep(0.0, 0.48, p.x));
        a = body * bristle * smoothstep(0.0, 0.06, p.x) * smoothstep(1.0, 0.90, p.x);
        float lum = dot(tint, vec3(0.3, 0.59, 0.11));
        bool ink = lum < 0.08;
        float hotN = fbm(vec3(p.x * 2.8 - t * 2.2, qy * 1.6, seed + 9.0));
        float hot = smoothstep(ink ? 0.68 : 0.56, ink ? 0.84 : 0.78, hotN) * across;
        if (ink) a *= clamp(0.58 + 0.65 * fbm(vec3(p.x * 3.0 - t * 0.6, qy * 2.0, seed + 4.0)), 0.0, 1.0);
        vec3 bright = ink ? vec3(0.72, 0.46, 0.08) : mix(tint, vec3(1.0, 0.97, 0.88), 0.70);
        col = mix(tint * (0.85 + 0.30 * striae), bright, hot);
    } else if (effect == 3) {
        // BOLT: flickering electric lightning vein (works as dark ink sheath in BODY pass or hot core in GLOW pass)
        float n = noise(vec3(p.x * 18.0 - t * 26.0, seed, 0.0));
        float core = exp(-pow(q.y / 0.14, 2.0));
        float glow = exp(-pow(q.y / 0.56, 2.0)) * 0.55;
        float flick = 0.68 + 0.32 * sin(t * 78.0 + seed * 3.1);
        float v = (core * 1.35 + glow) * (0.72 + n * 0.48) * flick * smoothstep(0.0, 0.08, p.x) * smoothstep(1.0, 0.92, p.x);
        float lum = dot(tint, vec3(0.3, 0.59, 0.11));
        col = lum < 0.08 ? tint * (0.6 + 0.6 * core) : ramp(tint, clamp(v, 0.0, 1.0)) + vec3(core * 0.45);
        a = clamp(v, 0.0, 1.0);
    } else if (effect == 4) {
        // SPARK_PARTICLE: 4 soft organic GPU particle types (0 star, 1 molten ember, 2 smoke wisp, 3 stone shard)
        float variant = mod(seed, 4.0);
        float d = length(q);
        if (variant < 0.5) {
            float twinkle = 0.72 + 0.28 * sin(t * 14.0 + seed * 3.7);
            float star = exp(-abs(q.x) * 16.0) * exp(-abs(q.y) * 2.4)
                       + exp(-abs(q.y) * 16.0) * exp(-abs(q.x) * 2.4);
            float core = exp(-d * d * 9.0);
            float v = (star * 0.9 + core * 1.15) * twinkle * smoothstep(1.0, 0.68, d);
            col = ramp(tint, clamp(v, 0.0, 1.0));
            a = clamp(v, 0.0, 1.0);
        } else if (variant < 1.5) {
            float core = exp(-d * d * 6.5);
            float halo = exp(-d * d * 2.0) * 0.42;
            float v = (core * 1.15 + halo) * smoothstep(1.0, 0.55, d);
            col = ramp(tint, clamp(v, 0.0, 1.0));
            a = clamp(v, 0.0, 1.0);
        } else if (variant < 2.5) {
            float n = fbm(vec3(q * 2.2, t * 0.45 + seed));
            float mask = smoothstep(1.0, 0.15, d + (n - 0.5) * 0.8);
            col = ramp(tint, clamp(0.32 + n * 0.55, 0.0, 1.0));
            a = mask * (0.42 + n * 0.48);
        } else {
            float ang = atan(q.y, q.x);
            float edge = 0.54 + 0.32 * noise(vec3(ang * 1.7, seed, 0.0)) + 0.10 * noise(vec3(ang * 5.0, seed + 3.0, 0.0));
            float inside = smoothstep(edge, edge - 0.08, d);
            float lit = smoothstep(edge - 0.22, edge, d) * smoothstep(-0.2, 0.8, q.x - q.y);
            col = mix(tint * 0.22, mix(tint, vec3(1.0, 0.96, 0.85), 0.55), lit * 0.85);
            a = inside;
        }
    } else if (effect == 5) {
        // GLOW: ultra-soft atmospheric volumetric radial light wash (never a hard disc)
        float d = length(q);
        float haze = fbm(vec3(q * 1.6, t * 0.35 + seed));
        float g = (exp(-d * d * 5.2) * 0.72 + exp(-d * d * 1.85) * 0.34) * (0.82 + 0.32 * haze) * smoothstep(1.0, 0.72, d);
        col = ramp(tint, clamp(g * 0.95, 0.0, 1.0));
        a = clamp(g, 0.0, 1.0);
    } else if (effect == 6) {
        // HEX_MIST: organic smoky hexagonal kinetic barrier (soft noise-eroded perimeter, no rigid circle cut)
        float d = length(q);
        float turb = fbm(vec3(q * 2.8, t * 0.9 + seed));
        float radialMask = smoothstep(1.0, 0.42, d + (turb - 0.5) * 0.48);
        float hexEdge = hexEdgeDist(q * 4.5 + (turb - 0.5) * 0.25);
        float gridLine = smoothstep(0.08, 0.014, hexEdge);
        float pulseWave = exp(-pow((d - fract(t * 0.55)) / 0.16, 2.0));
        float mist = smoothstep(0.95, 0.10, d) * (0.14 + 0.24 * turb);
        float v = (gridLine * (0.42 + 0.85 * pulseWave + 0.35 * turb) + mist) * radialMask;
        col = ramp(tint, clamp(v * 0.92, 0.0, 1.0));
        a = clamp(v, 0.0, 1.0);
    } else if (effect == 7) {
        // IMPACT_FRAME: organic hand-drawn anime ink-and-gold radial fracture burst (like Purple/Red sakuga)
        float d = length(q);
        float ang = atan(q.y, q.x);
        float snap = floor(VfxTime * 20.0);
        float warp = (fbm(vec3(cos(ang) * 2.5, sin(ang) * 2.5, snap * 0.6 + seed)) - 0.5) * 0.22;
        float aW = ang + warp;
        float r1 = pow(noise(vec3(aW * 22.0, seed * 2.3, snap * 0.7)), 3.0);
        float r2 = pow(noise(vec3(aW * 48.0 + 5.0, seed * 1.1, snap * 1.1)), 4.6);
        float grain = fbm(vec3(q * 4.5, snap * 0.5 + seed));
        float outerMask = smoothstep(0.22, 0.88, d + (grain - 0.5) * 0.24);
        float inkStrokes = smoothstep(0.28, 0.68, r1 * 0.85 + r2 * 1.1) * outerMask;
        float goldTears = smoothstep(0.45, 0.82, r2 * 1.3 + r1 * 0.55) * outerMask;
        float shockRing = exp(-pow((d + (grain - 0.5) * 0.16 - 0.56) / 0.075, 2.0)) * (0.55 + 0.65 * grain);
        float vign = smoothstep(0.48, 1.24, d + (grain - 0.5) * 0.22);
        vec3 ink = vec3(0.015, 0.010, 0.022);
        vec3 goldFlash = mix(tint, vec3(1.0, 0.98, 0.90), clamp(goldTears * 0.75 + shockRing, 0.0, 1.0));
        float bright = clamp(goldTears * 1.2 + shockRing * 1.35, 0.0, 1.0);
        col = mix(ink, goldFlash, bright);
        a = clamp(vign * 0.82 + inkStrokes * 0.68 + bright * 0.92, 0.0, 1.0);
    } else {
        discard;
    }

    fragColor = vec4(col, a * vertexColor.a) * ColorModulator;
    if (fragColor.a < 0.003) discard;
}
