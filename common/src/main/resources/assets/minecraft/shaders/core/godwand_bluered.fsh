#version 150
// God Wand Blue/Red/Purple VFX. UV v < 2: the VFX atlas, tinted (no alpha cut-off, so edges stay soft).
// UV v >= 2: a procedural effect: id = floor(v) - 2, seed = floor(u), local (0..1, 0..1) = the fractional parts.
// Colour comes from a ramp built from the vertex tint (dark, tint, white hot); vertex alpha is the master alpha.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float VfxTime;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.17, 0.13));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

vec3 ramp(vec3 tint, float v) {
    vec3 dark = tint * 0.16;
    // Ink stays ink: a near-black tint only lifts to a dark wine, it never burns to white.
    float lum = dot(tint, vec3(0.3, 0.59, 0.11));
    vec3 hot = lum < 0.08 ? tint * 2.0 + vec3(0.16, 0.0, 0.03) : mix(tint, vec3(1.0), 0.6);
    return v < 0.5 ? mix(dark, tint, v * 2.0) : mix(tint, hot, (v - 0.5) * 2.0);
}

mat2 rot(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

void main() {
    if (texCoord0.y < 2.0) {
        vec4 c = texture(Sampler0, texCoord0) * vertexColor;
        if (c.a < 0.004) discard;
        fragColor = c * ColorModulator;
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
        // ORB on a camera-facing quad 1.6 sphere radii across its half: churning plasma inside, a fresnel rim, a corona outside.
        // seed mod 4: 0 blue fire (corona licks upward), 1 red plasma (tight), 2 purple storm (electric veins), 3 plain.
        float variant = mod(seed, 4.0);
        float d = length(q) * 1.6;
        if (d < 1.0) {
            float z = sqrt(1.0 - d * d);
            vec3 n = vec3(q * 1.6, z);
            n.xz *= rot(t * 0.35);
            n.xy *= rot(t * 0.21);
            vec3 w = n * 2.2 + vec3(0.0, 0.0, t * 0.4);
            float warp = fbm(w + fbm(w * 1.7 + t * 0.3) * 1.6);
            float swirl = fbm(vec3(rot(t * 0.8 + d * 3.0) * q * 3.0, t * 0.5));
            float core = pow(z, 2.5);
            float fres = pow(1.0 - z, 2.2);
            float v = core * 0.75 + warp * 0.75 + swirl * 0.25 + fres * 0.95 - 0.25;
            if (variant == 2.0) {
                float vein = 1.0 - abs(fbm(w * 2.5 + t) * 2.0 - 1.0);
                v += pow(vein, 8.0) * 1.3;
            }
            col = ramp(tint, clamp(v, 0.0, 1.0)) + vec3(max(v - 1.0, 0.0));
            a = smoothstep(1.0, 0.95, d);
        } else {
            float ang = atan(q.y, q.x);
            float speed = variant == 0.0 ? 1.6 : 0.6;
            float corona = fbm(vec3(cos(ang) * 2.0 + t * 0.2, sin(ang) * 2.0, d * 3.0 - t * speed));
            float fall = exp(-(d - 1.0) * (variant == 0.0 ? 2.6 : 5.0));
            if (variant == 0.0) fall *= mix(0.5, 1.7, smoothstep(-1.0, 1.0, q.y));
            float v = fall * (0.5 + corona * 0.95);
            col = ramp(tint, clamp(v, 0.0, 1.0));
            a = clamp(v * 1.15 - 0.1, 0.0, 1.0);
        }
    } else if (effect == 1) {
        // FLOW: an energy ribbon, x along (tail 0, head 1), y across; noise streams along it and eats its edges.
        float across = 1.0 - abs(q.y);
        float n = fbm(vec3(p.x * 5.0 - t * 2.2, q.y * 1.5, seed));
        float streak = fbm(vec3(p.x * 14.0 - t * 4.0, q.y * 6.0, seed * 3.1));
        float shape = across * (0.25 + 0.75 * smoothstep(0.0, 0.25, p.x)) * smoothstep(1.0, 0.85, p.x);
        float v = shape * (0.6 + n * 0.8) + streak * 0.35 * across;
        col = ramp(tint, clamp(v * 1.1, 0.0, 1.0));
        a = smoothstep(0.16, 0.4, v) * clamp(v * 1.4, 0.0, 1.0);
    } else if (effect == 2) {
        // FLAME: base at y = 1, tip at y = 0; noise rises through it and bends its outline.
        float h = 1.0 - p.y;
        float dist = fbm(vec3(q.x * 3.0, h * 3.0 - t * 3.0, seed));
        float width = (1.0 - h) * 0.75 + 0.05;
        float body = smoothstep(width, width * 0.35, abs(q.x + (dist - 0.5) * 0.7 * h));
        float v = body * (1.25 - h) * (0.65 + dist * 0.7);
        col = ramp(tint, clamp(v, 0.0, 1.0));
        a = smoothstep(0.08, 0.45, v);
    } else if (effect == 3) {
        // SMOKE: a billowing puff, self-shaded.
        float d = length(q);
        float n = fbm(vec3(q * 2.0, t * 0.3 + seed));
        col = ramp(tint, 0.3 + n * 0.55);
        a = smoothstep(1.0, 0.2, d + (n - 0.5) * 0.7) * (0.5 + n * 0.5);
    } else if (effect == 4) {
        // SHOCK: a shockwave ring, noisy.
        float d = length(q);
        float n = fbm(vec3(q * 3.0, t + seed));
        float ring = exp(-pow((d - 0.82) / 0.06, 2.0)) + exp(-pow((d - 0.82) / 0.25, 2.0)) * 0.35 * step(d, 0.82);
        float v = ring * (0.6 + n * 0.7);
        col = ramp(tint, clamp(v, 0.0, 1.0));
        a = clamp(v, 0.0, 1.0) * step(d, 1.0);
    } else if (effect == 5) {
        // BOLT: a flickering electric strip, white-hot core.
        float n = noise(vec3(p.x * 20.0 - t * 30.0, seed, 0.0));
        float core = exp(-pow(q.y / 0.12, 2.0));
        float glow = exp(-pow(q.y / 0.55, 2.0)) * 0.6;
        float flick = 0.65 + 0.35 * sin(t * 90.0 + seed);
        float v = (core * 1.4 + glow) * (0.7 + n * 0.5) * flick;
        col = ramp(tint, clamp(v, 0.0, 1.0)) + vec3(core * 0.6);
        a = clamp(v, 0.0, 1.0);
    } else if (effect == 6) {
        // SPARK: a twinkling four-point star.
        float d = length(q);
        float star = exp(-abs(q.x) * 25.0) * exp(-abs(q.y) * 2.5) + exp(-abs(q.y) * 25.0) * exp(-abs(q.x) * 2.5);
        float v = star + exp(-d * d * 12.0) * 0.8;
        col = ramp(tint, clamp(v, 0.0, 1.0));
        a = clamp(v * (0.6 + 0.4 * sin(t * 20.0 + seed * 7.0)), 0.0, 1.0);
    } else if (effect == 7) {
        // RAYS: a radial burst of streaks, flickering.
        float ang = atan(q.y, q.x);
        float d = length(q);
        float r = pow(noise(vec3(ang * 12.0, t * 2.0, seed)), 3.0);
        float v = r * smoothstep(0.05, 0.25, d) * smoothstep(1.0, 0.5, d) * 1.5 + exp(-d * d * 20.0) * 0.8;
        col = ramp(tint, clamp(v, 0.0, 1.0));
        a = clamp(v, 0.0, 1.0);
    } else {
        // NEBULA: the void behind the merge, dark smoke drifting, red on the left, blue on the right, violet between.
        vec2 s = q * 2.0;
        float n1 = fbm(vec3(s + vec2(t * 0.05, 0.0), t * 0.04));
        float n2 = fbm(vec3(s * 2.0 - n1 * 1.5, t * 0.07));
        vec3 neb = mix(vec3(0.10, 0.02, 0.22), vec3(0.04, 0.05, 0.26), smoothstep(-1.0, 1.0, q.x));
        neb = mix(neb, vec3(0.26, 0.02, 0.07), smoothstep(0.2, -1.0, q.x) * 0.6);
        col = vec3(0.02, 0.005, 0.05) + neb * pow(n2, 1.8) * 2.2;
        a = 1.0;
    }
    fragColor = vec4(col, a * vertexColor.a) * ColorModulator;
    if (fragColor.a < 0.003) discard;
}
