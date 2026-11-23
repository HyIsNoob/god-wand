#version 150
// God Wand Block Dominion — True 3D Volumetric & Atmospheric GLSL shader (zero flat PNG stickers).
// Two-pass rendering:
//   Pass 1 (BODY, alpha blend): soft self-shaded world-space smoke/dust, dark 3D-lit stone splinters.
//   Pass 2 (GLOW, additive): 3D Fresnel Mach cones & curved domes, world-space ground light pools,
//          gossamer aerodynamic slipstream ribbons, ultra-fine crackling arcs, and micro dust motes.
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
    for (int i = 0; i < 4; i++) {
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
    vec3 dark = tint * 0.22;
    vec3 hot = lum < 0.08
        ? tint * 1.6 + vec3(0.20, 0.12, 0.04)
        : mix(tint, vec3(1.0, 0.98, 0.94), 0.58);
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
        // SMOKE: ultra-soft, billowing self-shaded volumetric dust/mist (zero hard edges)
        float d = length(q);
        float w = fbm(vec3(q * 1.65, t * 0.26 + seed));
        float n = fbm(vec3(q * 2.25 + (w - 0.5) * 0.65, t * 0.34 + seed + 3.1));
        float radial = smoothstep(1.0, 0.18, d + (n - 0.5) * 0.72);
        col = ramp(tint, clamp(0.28 + n * 0.54, 0.0, 1.0));
        a = radial * radial * (0.42 + n * 0.48);
    } else if (effect == 1) {
        // PLASMA / AERODYNAMIC SLIPSTREAM: gossamer vapor contrail eroded smoothly by wind turbulence
        float warp = fbm(vec3(p.x * 2.6 - t * 2.4, q.y * 1.4, seed * 0.41));
        float n = fbm(vec3(p.x * 4.0 - t * 3.2 + warp * 0.8, q.y * 2.4 + warp, seed * 0.73));
        float across = exp(-pow((q.y + (warp - 0.5) * 0.30) / 0.46, 2.0));
        float tailFade = smoothstep(0.0, 0.22, p.x) * smoothstep(1.0, 0.86, p.x);
        float density = across * (0.45 + 0.72 * n) * tailFade;
        a = smoothstep(0.08, 0.52, density) * density;
        float hot = smoothstep(0.62, 0.88, n) * across * p.x;
        col = mix(tint * 0.6, tint, density) + mix(tint, vec3(1.0, 0.98, 0.94), 0.65) * hot * 0.55;
    } else if (effect == 2) {
        // BRUSH / DARK WAKE: soft torn atmospheric dust ribbon (used delicately in BODY pass)
        float warp = fbm(vec3(p.x * 2.2 - t * 1.6, seed * 0.37, 0.5));
        float qy = q.y + (warp - 0.5) * 0.48;
        float across = exp(-pow(qy / 0.52, 2.0));
        float striae = fbm(vec3(p.x * 2.4 - t * 1.8, qy * 3.2 + warp * 1.6, seed * 1.3));
        a = across * smoothstep(0.32, 0.68, striae) * smoothstep(0.0, 0.14, p.x) * smoothstep(1.0, 0.86, p.x);
        col = tint * (0.75 + 0.35 * striae);
    } else if (effect == 3) {
        // BOLT: razor-thin, delicate electric filament (never thick white sticks!)
        float n = noise(vec3(p.x * 22.0 - t * 32.0, seed, 0.0));
        float core = exp(-pow(q.y / 0.11, 2.0));
        float glow = exp(-pow(q.y / 0.44, 2.0)) * 0.42;
        float flick = 0.62 + 0.38 * sin(t * 86.0 + seed * 3.1);
        float v = (core * 1.15 + glow) * (0.68 + n * 0.45) * flick * smoothstep(0.0, 0.10, p.x) * smoothstep(1.0, 0.90, p.x);
        float lum = dot(tint, vec3(0.3, 0.59, 0.11));
        col = lum < 0.08 ? tint * (0.5 + 0.5 * core) : ramp(tint, clamp(v, 0.0, 1.0)) + vec3(core * 0.32);
        a = clamp(v, 0.0, 1.0);
    } else if (effect == 4) {
        // MICRO-PARTICLES (NO giant PNG stars!):
        // 0 = pinpoint luminous dust mote, 1 = velocity-aligned wind/spark needle,
        // 2 = soft dust wisp, 3 = 3D-lit tumbling stone chip
        float variant = mod(seed, 4.0);
        float d = length(q);
        if (variant < 0.5) {
            // Pinpoint natural dust mote catching light
            float twinkle = 0.78 + 0.22 * sin(t * 10.0 + seed * 3.7);
            float core = exp(-d * d * 12.0);
            float halo = exp(-d * d * 3.6) * 0.32;
            float v = (core + halo) * twinkle * smoothstep(1.0, 0.55, d);
            col = ramp(tint, clamp(v * 0.95, 0.0, 1.0));
            a = clamp(v, 0.0, 1.0);
        } else if (variant < 1.5) {
            // Thin aerodynamic velocity needle / spark streak (along local X)
            float needle = exp(-pow(q.y / 0.14, 2.0)) * exp(-pow(q.x / 0.72, 2.0));
            float tip = exp(-pow((q.x - 0.25) / 0.35, 2.0)) * exp(-pow(q.y / 0.09, 2.0)) * 0.6;
            float v = (needle + tip) * smoothstep(1.0, 0.65, abs(q.x));
            col = ramp(tint, clamp(v, 0.0, 1.0));
            a = clamp(v, 0.0, 1.0);
        } else if (variant < 2.5) {
            // Gossamer dust puff
            float n = fbm(vec3(q * 2.1, t * 0.42 + seed));
            float mask = smoothstep(1.0, 0.20, d + (n - 0.5) * 0.75);
            col = ramp(tint, clamp(0.32 + n * 0.48, 0.0, 1.0));
            a = mask * mask * (0.38 + n * 0.42);
        } else {
            // Faceted dark rock chip with subtle lit bevel
            float ang = atan(q.y, q.x);
            float edge = 0.50 + 0.34 * noise(vec3(ang * 1.9, seed, 0.0)) + 0.10 * noise(vec3(ang * 5.0, seed + 3.0, 0.0));
            float inside = smoothstep(edge, edge - 0.09, d);
            float lit = smoothstep(edge - 0.22, edge, d) * smoothstep(-0.2, 0.8, q.x - q.y);
            col = mix(tint * 0.20, mix(tint, vec3(1.0, 0.96, 0.88), 0.45), lit * 0.80);
            a = inside;
        }
    } else if (effect == 5) {
        // SOFT VOLUMETRIC HAZE (used subtly, never a hard glowing circle)
        float d = length(q);
        float haze = fbm(vec3(q * 1.4, t * 0.30 + seed));
        float g = exp(-d * d * 3.8) * (0.65 + 0.35 * haze) * smoothstep(1.0, 0.60, d);
        col = ramp(tint, clamp(g * 0.78, 0.0, 1.0));
        a = clamp(g * 0.65, 0.0, 1.0);
    } else if (effect == 6) {
        // 3D FRESNEL BARRIER DOME (hexagonal kinetic refraction shimmering ONLY near curved 3D grazing angles!)
        // Vertex alpha carries 3D Fresnel weight from emitFresnelDome.
        float flow = fbm(vec3(p.x * 5.0 - t * 0.65, p.y * 4.0 + t * 0.4, seed));
        float hex = hexEdgeDist(vec2(p.x * 10.0, p.y * 5.5) + (flow - 0.5) * 0.35);
        float grid = smoothstep(0.075, 0.014, hex);
        float shimmer = 0.28 + 0.72 * smoothstep(0.32, 0.78, flow);
        float v = (grid * 0.78 + 0.22 * flow) * shimmer;
        col = ramp(tint, clamp(0.45 + v * 0.55, 0.0, 1.0));
        a = clamp(v, 0.0, 1.0);
    } else if (effect == 7) {
        // Minimal fallback flash only when PostFx is disabled in config
        float d = length(q);
        float vign = smoothstep(0.38, 1.18, d);
        col = mix(tint * 0.15, mix(tint, vec3(1.0), 0.5), 1.0 - vign);
        a = vign * 0.65;
    } else if (effect == 8) {
        // 3D FRESNEL AERODYNAMIC MACH SHELL / SLIPSTREAM CONE
        // p.x = around circumference, p.y = 0 nose -> 1 trailing wake; vertex alpha carries 3D Fresnel!
        float streak1 = fbm(vec3(p.x * 6.0, p.y * 3.8 - t * 5.2, seed * 0.31));
        float streak2 = fbm(vec3(p.x * 12.0 + 3.1, p.y * 6.5 - t * 7.8, seed * 0.67));
        float flow = smoothstep(0.28, 0.80, streak1 * 0.62 + streak2 * 0.38);
        float noseFade = smoothstep(0.0, 0.16, p.y);
        float tailFade = smoothstep(1.0, 0.28, p.y);
        float v = (0.28 + 0.78 * flow) * noseFade * tailFade;
        col = ramp(tint, clamp(0.42 + flow * 0.58, 0.0, 1.0));
        a = clamp(v, 0.0, 1.0);
    } else if (effect == 9) {
        // WORLD-SPACE GROUND LIGHT POOL (dyes real terrain beneath wings/surge/stairs, zero ring edges!)
        float d = length(q);
        float ripple = fbm(vec3(q * 2.4, t * 0.55 + seed));
        float pool = exp(-d * d * 3.6) * (0.72 + 0.38 * ripple) * smoothstep(1.0, 0.52, d);
        col = mix(tint * 0.85, mix(tint, vec3(1.0, 0.98, 0.94), 0.35), exp(-d * d * 8.0));
        a = clamp(pool, 0.0, 1.0);
    } else {
        discard;
    }

    fragColor = vec4(col, a * vertexColor.a) * ColorModulator;
    if (fragColor.a < 0.003) discard;
}
