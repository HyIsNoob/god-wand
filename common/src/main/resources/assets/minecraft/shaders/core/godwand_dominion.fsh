#version 150
// God Wand Block Dominion custom procedural GLSL shader.
// UV encoding (matches BlueRedShader):
//   effect = int(floor(texCoord0.y)) - 2
//   seed   = floor(texCoord0.x)
//   p      = clamp((fract(texCoord0) - 0.001) / 0.998, 0.0, 1.0)
// Effects:
//   0 = RUNE_RING      (sovereign halo, auto gravity field, air-step stair pads)
//   1 = HEX_SHIELD     (Voronoi/hexagonal kinetic shield wall & dome barrier)
//   2 = ENERGY_RIBBON  (pickup tether, wingtip slipstream, volley beams, projectile trail)
//   3 = MACH_SHOCK     (wing-boost Mach cone, parry double shockwave, unfold/volley blast)
//   4 = SPARK_PARTICLE (custom 3D GPU particle: star spark, ember orb, crystal shard)
//   5 = IMPACT_FRAME   (stylized anime impact frame radial fracture burst)

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
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.04 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

// Analytical hexagonal distance to nearest cell edge (0 at edge, >0 inside cell).
float hexEdgeDist(vec2 p) {
    const vec2 s = vec2(1.0, 1.7320508);
    vec4 hC = floor(vec4(p, p - vec2(0.5, 1.0)) / s.xyxy) + 0.5;
    vec4 h = vec4(p - hC.xy * s, p - (hC.zw + 0.5) * s);
    vec2 g = dot(h.xy, h.xy) < dot(h.zw, h.zw) ? h.xy : h.zw;
    vec2 a = abs(g);
    float d = max(dot(a, normalize(s)), a.x);
    return 0.5 - d;
}

vec3 goldRamp(vec3 tint, float v) {
    vec3 deep = tint * 0.28 + vec3(0.08, 0.03, 0.0);
    vec3 hot = mix(tint, vec3(1.0, 0.99, 0.93), 0.78);
    return v < 0.5 ? mix(deep, tint, v * 2.0) : mix(tint, hot, (v - 0.5) * 2.0);
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
    float t = VfxTime + seed * 1.13;
    vec3 tint = vertexColor.rgb;
    vec2 q = p * 2.0 - 1.0;
    vec3 col = tint;
    float a = 0.0;

    if (effect == 0) {
        // RUNE_RING: sovereign arcane halo / stair pad / gravity field ring
        float d = length(q);
        float ang = atan(q.y, q.x);
        float flow = fbm(vec3(cos(ang * 3.0 - t * 0.8) * 1.6, sin(ang * 3.0 - t * 0.8) * 1.6, d * 4.0 + t * 0.5));
        float mainRing = exp(-pow((d - 0.82) / 0.048, 2.0));
        float innerRing = exp(-pow((d - 0.64) / 0.024, 2.0)) * 0.72;
        float ticks = smoothstep(0.55, 0.92, sin(ang * 18.0 + t * 1.6)) * exp(-pow((d - 0.73) / 0.055, 2.0));
        float cardinal = pow(max(0.0, cos(ang * 4.0 - t * 0.55)), 12.0) * exp(-pow((d - 0.82) / 0.09, 2.0));
        float halo = exp(-pow((d - 0.82) / 0.16, 2.0)) * 0.35 * (0.65 + 0.55 * flow);
        float energy = mainRing * (0.75 + 0.55 * flow) + innerRing + ticks * 0.75 + cardinal * 0.85 + halo;
        float clip = smoothstep(1.0, 0.92, d);
        col = goldRamp(tint, clamp(energy * 0.85, 0.0, 1.0));
        a = clamp(energy * clip, 0.0, 1.0);
    } else if (effect == 1) {
        // HEX_SHIELD: Voronoi/hexagonal kinetic energy barrier
        float d = length(q);
        float radialMask = smoothstep(1.0, 0.84, d);
        float hexEdge = hexEdgeDist(q * 4.8);
        float gridLine = smoothstep(0.085, 0.012, hexEdge);
        float pulseWave = exp(-pow((d - fract(t * 0.65)) / 0.14, 2.0));
        float rim = exp(-pow((d - 0.90) / 0.045, 2.0));
        float shimmer = fbm(vec3(q * 3.2, t * 1.2));
        float coreFill = smoothstep(0.95, 0.15, d) * (0.10 + 0.14 * shimmer);
        float v = (gridLine * (0.55 + 0.75 * pulseWave + 0.35 * shimmer) + rim * 1.35 + coreFill) * radialMask;
        col = goldRamp(tint, clamp(v * 0.92, 0.0, 1.0));
        a = clamp(v, 0.0, 1.0);
    } else if (effect == 2) {
        // ENERGY_RIBBON: flowing molten plasma stream (p.x = 0 tail -> 1 head, q.y = across)
        float across = 1.0 - abs(q.y);
        float warp = fbm(vec3(p.x * 4.5 - t * 3.4, q.y * 1.6, seed * 0.37));
        float streak = fbm(vec3(p.x * 11.0 - t * 6.0, q.y * 5.0 + warp * 1.2, seed * 0.71));
        float endFade = smoothstep(0.0, 0.18, p.x) * smoothstep(1.0, 0.88, p.x);
        float core = exp(-pow((q.y + (warp - 0.5) * 0.25) / 0.18, 2.0));
        float body = pow(max(0.0, across), 1.4) * (0.55 + 0.75 * warp) + streak * 0.32 * across;
        float v = (core * 1.15 + body * 0.75) * endFade;
        col = goldRamp(tint, clamp(v, 0.0, 1.0));
        a = clamp(v * 1.15, 0.0, 1.0);
    } else if (effect == 3) {
        // MACH_SHOCK: aerodynamic shockwave / supersonic ring with streaked pressure rays
        float d = length(q);
        float ang = atan(q.y, q.x);
        float rays = fbm(vec3(cos(ang) * 5.0, sin(ang) * 5.0, d * 5.0 - t * 4.5));
        float shockFront = exp(-pow((d - 0.84) / 0.045, 2.0));
        float trail = exp(-pow((d - 0.74) / 0.19, 2.0)) * smoothstep(0.92, 0.60, d) * (0.35 + 0.75 * rays);
        float innerFlash = exp(-pow((d - 0.55) / 0.04, 2.0)) * 0.55;
        float v = (shockFront * (0.9 + 0.5 * rays) + trail + innerFlash) * smoothstep(1.0, 0.92, d);
        col = goldRamp(tint, clamp(v, 0.0, 1.0));
        a = clamp(v, 0.0, 1.0);
    } else if (effect == 4) {
        // SPARK_PARTICLE: custom GPU 3D particle (variant by seed mod 3)
        float variant = mod(seed, 3.0);
        float d = length(q);
        float twinkle = 0.72 + 0.28 * sin(t * 14.0 + seed * 3.7);
        float v = 0.0;
        if (variant < 0.5) {
            // 4-point celestial star spark
            float star = exp(-abs(q.x) * 18.0) * exp(-abs(q.y) * 2.4)
                       + exp(-abs(q.y) * 18.0) * exp(-abs(q.x) * 2.4);
            float core = exp(-d * d * 10.0);
            v = (star * 0.95 + core * 1.15) * twinkle * smoothstep(1.0, 0.75, d);
        } else if (variant < 1.5) {
            // Molten golden ember orb
            float core = exp(-d * d * 7.0);
            float halo = exp(-d * d * 2.2) * 0.45;
            v = (core * 1.2 + halo) * smoothstep(1.0, 0.65, d);
        } else {
            // Crisp crystalline diamond shard
            float diam = abs(q.x) * 1.35 + abs(q.y) * 0.85;
            float shape = smoothstep(0.82, 0.52, diam);
            float ridge = exp(-abs(q.x) * 12.0) * shape;
            v = (shape * 0.75 + ridge * 0.85) * twinkle;
        }
        col = goldRamp(tint, clamp(v, 0.0, 1.0));
        a = clamp(v, 0.0, 1.0);
    } else if (effect == 5) {
        // IMPACT_FRAME: stylized anime high-contrast black-gold-white radial impact frame
        float d = length(q);
        float ang = atan(q.y, q.x);
        float rayA = pow(noise(vec3(ang * 26.0, seed * 3.1, floor(VfxTime * 24.0) * 0.7)), 3.2);
        float rayB = pow(noise(vec3(ang * 54.0 + 7.0, seed * 1.7, floor(VfxTime * 24.0) * 1.1)), 5.0);
        float rays = clamp(rayA * 1.4 + rayB * 1.8, 0.0, 1.0);
        float outerMask = smoothstep(0.26, 0.92, d);
        float shockRing = exp(-pow((d - 0.58) / 0.06, 2.0)) * (0.65 + 0.55 * rayA);
        float darkVign = smoothstep(0.45, 1.22, d);
        // High contrast anime palette: obsidian ink outer frame + blazing gold/white speedlines & ring
        vec3 ink = vec3(0.02, 0.015, 0.03);
        vec3 flash = mix(tint, vec3(1.0, 0.98, 0.92), clamp(rays * 0.85 + shockRing, 0.0, 1.0));
        float bright = clamp(rays * outerMask * 1.4 + shockRing * 1.35, 0.0, 1.0);
        col = mix(ink, flash, bright);
        a = clamp(darkVign * 0.78 + bright * 0.92, 0.0, 1.0);
    } else {
        discard;
    }

    fragColor = vec4(col, a * vertexColor.a) * ColorModulator;
    if (fragColor.a < 0.003) discard;
}
