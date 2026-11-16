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

float ridge(float n) { return 1.0 - abs(n * 2.0 - 1.0); }

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
        // ORB (owner references 28-34): one designed sphere per quad. A thin crisp rim ring, a deep interior with fine streaks spiralling
        // into a white-hot core, thin bright filaments, and fine shards torn off round the outside. Sphere radius = 1/1.55 of the quad half.
        // seed mod 4: 0 blue energy (bright body, folds, lens flare), 1 red (dark, spiral flakes, rim flames), 2 purple (dark, wound strands,
        // purple and ink shards). floor(seed / 4) = level: 1 ready / charged (busier), 2 bursting (core floods the sphere).
        float variant = mod(seed, 4.0), level = floor(seed / 4.0);
        bool isBlue = variant == 0.0, isRed = variant == 1.0, isPurple = variant == 2.0;
        float R = 1.0 / 1.55;
        float d = length(q) / R;
        float ang = atan(q.y, q.x);
        vec2 dir = vec2(cos(ang), sin(ang));
        float wob = (fbm(vec3(dir * 2.5, t * 0.8)) - 0.5) * (isBlue ? 0.14 : 0.03);
        float dd = d - wob;
        vec3 hot = mix(tint, vec3(1.0), 0.6);
        float rimLine = exp(-pow((dd - 1.0) / (isBlue ? 0.035 : 0.022), 2.0));
        if (dd < 1.0) {
            float dz = clamp(d, 0.0, 0.999);
            float z = sqrt(1.0 - dz * dz);
            vec3 n3 = vec3(q / R, z);
            n3.xz *= rot(t * 0.3);
            n3.yz *= rot(t * 0.17);
            float fres = pow(1.0 - z, 3.0);
            // Fine streaks along log spirals, flowing in towards the core.
            float sp = ang - (isRed ? 2.6 : 1.8) * log(max(d, 0.03)) + t * (isRed ? 1.1 : 0.7);
            float flakes = fbm(vec3(cos(sp) * 5.5, sin(sp) * 5.5, log(max(d, 0.03)) * 2.2 + t * 1.6));
            flakes = smoothstep(0.5, 0.8, flakes) * smoothstep(0.0, 0.25, d);
            // Thin bright filaments (ridged noise) drifting through the volume.
            float fil = pow(ridge(fbm(n3 * 2.2 + vec3(0.0, 0.0, t * 0.6))), isBlue ? 10.0 : 16.0);
            float coreK = level >= 2.0 ? 1.4 : isPurple ? 6.0 : isBlue ? 9.0 : 5.0;
            float core = exp(-d * d * coreK);
            vec3 body;
            if (isBlue) {
                float folds = fbm(n3 * 1.6 + t * 0.35);
                // Deep blue at the edge, cyan folds, white only in thin creases and the core (refs 33, 34).
                body = mix(tint * 0.28, tint * 0.85, folds * 0.7 + (1.0 - fres) * 0.25);
                body += mix(tint, vec3(0.55, 0.95, 1.0), 0.6) * pow(ridge(fbm(n3 * 1.3 - t * 0.25)), 7.0) * 0.8;
            } else {
                body = tint * (0.07 + 0.22 * fres) + tint * 0.12 * fbm(n3 * 2.0 + t * 0.2);
            }
            vec3 c3 = body + tint * flakes * (isBlue ? 0.3 : 1.25) + hot * fil * (isBlue ? 0.7 : 0.65 + 0.4 * level);
            if (isPurple) {
                // Strands wound round the ball (ref 30), lit from within between the dark coils.
                vec3 m = n3;
                m.xy *= rot(0.5);
                float lat = asin(clamp(m.y, -1.0, 1.0)), lon = atan(m.z, m.x);
                float coil = sin(lat * 26.0 + lon * 1.5 + fbm(m * 2.5 + t * 0.3) * 6.0 - t * 2.2);
                c3 += hot * pow(max(coil, 0.0), 22.0) * (0.35 + 0.65 * z) * 1.3;
            }
            c3 += vec3(1.0, 0.97, 1.0) * core * (1.1 + 0.6 * level);
            c3 += hot * rimLine * 1.5 + tint * fres * 0.35;
            col = c3;
            a = smoothstep(1.0, 0.975, dd);
        } else {
            float od = dd - 1.0;
            float corona = fbm(vec3(dir * 2.2, od * 4.0 - t * 1.4));
            float glowF = exp(-od * (isBlue ? 7.0 : 11.0)) * (0.35 + 0.5 * corona);
            // Fine shards torn off the rim and spun away (ref 28): thin bands of noise along the angle.
            float shardN = fbm(vec3(dir.x * 3.0 + t * 0.15, dir.y * 3.0, od * 3.0 - t * 1.1));
            float spin = ang + od * 3.0 - t * 0.8;
            float thin = pow(ridge(noise(vec3(cos(spin) * 7.0, sin(spin) * 7.0, od * 2.0 + t))), 18.0);
            float shards = smoothstep(0.55, 0.66, shardN) * thin * smoothstep(0.5, 0.06, od) * (0.6 + 0.4 * level);
            float ink = isPurple ? smoothstep(0.6, 0.7, fbm(vec3(dir * 3.5 - t * 0.1, od * 3.0 + 7.0))) * smoothstep(0.42, 0.08, od) * 0.85 : 0.0;
            float flames = isRed ? smoothstep(0.58, 0.82, fbm(vec3(dir * 4.0, od * 7.0 - t * 3.0))) * exp(-od * 9.0) : 0.0;
            if (isBlue) {
                float fire = fbm(vec3(q.x * 4.0, q.y * 2.4 - t * 2.8, t * 0.35 + 3.0));
                fire = smoothstep(0.48, 0.78, fire) * exp(-od * 2.6) * (0.35 + smoothstep(-0.7, 1.0, q.y) * 1.1);
                flames = max(flames, fire);
            }
            float v = rimLine * 1.4 + glowF + shards * 1.3 + flames * 1.2;
            col = mix(tint * 1.1, hot, clamp(rimLine + shards, 0.0, 1.0));
            col = mix(col, vec3(0.03, 0.0, 0.05), ink);
            a = clamp(max(v, ink), 0.0, 1.0);
        }
        if (isBlue) {
            // A horizontal lens flare through the core (ref 33).
            float fl = exp(-abs(q.y) * 70.0) * exp(-abs(q.x) * 2.0) * (0.55 + 0.35 * level);
            col += vec3(fl);
            a = max(a, clamp(fl, 0.0, 1.0));
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
    } else if (effect == 9) {
        // SPEEDLINES: anime speed lines streaming out from the centre of the view, a dark vignette closing in.
        float ang = atan(q.y, q.x);
        float d = length(q);
        float lines = pow(noise(vec3(ang * 46.0, t * 9.0, seed)), 10.0) * smoothstep(0.25, 0.95, d);
        float vign = smoothstep(0.55, 1.35, d);
        col = mix(vec3(0.0), tint, lines);
        a = clamp(lines * 1.6 + vign * 0.75, 0.0, 1.0);
    } else if (effect == 10) {
        // DEBRIS: a jagged shard, dark with a lit rim on one side.
        float d = length(q);
        float ang = atan(q.y, q.x);
        float edge = 0.55 + 0.35 * noise(vec3(ang * 1.6, seed, 0.0)) + 0.1 * noise(vec3(ang * 5.0, seed + 3.0, 0.0));
        float inside = smoothstep(edge, edge - 0.06, d);
        float lit = smoothstep(edge - 0.2, edge, d) * smoothstep(-0.2, 0.8, q.x - q.y);
        col = mix(tint * 0.12, mix(tint, vec3(1.0), 0.4), lit * 0.8);
        a = inside;
    } else if (effect == 8) {
        // NEBULA: the void behind the merge, dark smoke drifting, red on the left, blue on the right, violet between.
        vec2 s = q * 2.0;
        float n1 = fbm(vec3(s + vec2(t * 0.05, 0.0), t * 0.04));
        float n2 = fbm(vec3(s * 2.0 - n1 * 1.5, t * 0.07));
        vec3 neb = mix(vec3(0.10, 0.02, 0.22), vec3(0.04, 0.05, 0.26), smoothstep(-1.0, 1.0, q.x));
        neb = mix(neb, vec3(0.26, 0.02, 0.07), smoothstep(0.2, -1.0, q.x) * 0.6);
        col = vec3(0.02, 0.005, 0.05) + neb * pow(n2, 1.8) * 2.2;
        a = 1.0;
    } else {
        discard;
    }
    fragColor = vec4(col, a * vertexColor.a) * ColorModulator;
    if (fragColor.a < 0.003) discard;
}
