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
        // Level + 4: a solo Blue or Red (owner, 2026-11-19): its streaks rush into the core, fast and strong.
        bool rush = level >= 4.0;
        if (rush) level -= 4.0;
        bool isBlue = variant == 0.0, isRed = variant == 1.0, isPurple = variant == 2.0, isPlanet = variant == 3.0;
        float R = 1.0 / 1.55;
        float d = length(q) / R;
        float ang = atan(q.y, q.x);
        vec2 dir = vec2(cos(ang), sin(ang));
        if (rush) {
            // A solo Blue or Red (owner, 2026-11-19): energy pouring into its centre. Spiral streams in log-radius move inward, so they speed
            // up as they near the core; a second finer layer runs faster; outside the rim the same streams are wisps being drawn in.
            float lr = log(max(d, 0.02)), sp = (1.0 + 0.4 * level) * (isRed ? 1.25 : 1.0);
            float a1 = ang + (isRed ? 1.7 : 1.2) * lr + t * 0.9 * sp;
            float s1 = fbm(vec3(cos(a1) * 3.0, sin(a1) * 3.0, lr * 2.4 + t * 3.0 * sp));
            float a2 = ang + (isRed ? 2.4 : 1.8) * lr + t * 1.3 * sp;
            float s2 = fbm(vec3(cos(a2) * 6.0 + 4.0, sin(a2) * 6.0, lr * 4.0 + t * 5.0 * sp));
            float streams = pow(ridge(s1), 5.0) * 0.9 + pow(ridge(s2), 9.0) * 0.7;
            vec3 bright = isRed ? vec3(1.0, 0.3, 0.34) : vec3(0.55, 0.95, 1.0);
            vec3 coreC = isRed ? vec3(1.0, 0.16, 0.2) : vec3(0.88, 0.97, 1.0);
            float inner = 1.0 - min(d, 1.0), od = max(d - 1.0, 0.0);
            float core = exp(-d * d * (isRed ? 6.0 : 7.0)), rim = exp(-pow((d - 1.0) / 0.07, 2.0));
            vec3 cIn = tint * (0.12 + 0.5 * inner) + mix(tint, bright, 0.6) * streams * (0.5 + inner) + coreC * core * (1.2 + 0.5 * level)
                     + mix(tint, bright, 0.5) * rim * 0.6;
            float aOut = clamp(streams * exp(-od * 4.5) * 1.1 + exp(-od * 6.0) * 0.45 + rim * 0.6, 0.0, 1.0) * smoothstep(1.5, 1.15, d);
            float inside = smoothstep(1.02, 0.97, d);
            col = mix(mix(tint, bright, 0.4), cIn, inside);
            a = mix(aOut, 1.0, inside);
        } else {
        // Blue's rim is never a clean circle: a slow lumpy swell plus fast crackle.
        // (Owner, 2026-11-18: soft and glossy, so only the slow swell and a little crackle: thicker and thinner in places, never noisy.)
        float wob = isBlue ? (fbm(vec3(dir * 1.3, t * 0.35)) - 0.5) * 0.22 + (fbm(vec3(dir * 4.0, t * 1.1)) - 0.5) * 0.04
                           : (fbm(vec3(dir * 2.5, t * 0.8)) - 0.5) * 0.03;
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
            // Red keeps its core hot red with only a small white heart (owner, 2026-11-18: red dominant, not a white lamp).
            // Red: bright but all red, no white heart at all (owner, round 49).
            c3 += (isRed ? vec3(1.0, 0.12, 0.16) * 0.8 : vec3(1.0, 0.97, 1.0)) * core * (1.1 + 0.6 * level);
            if (isBlue) {
                // Glassy energy (owner, 2026-11-18): no drawn outline. The edge is a soft cyan fresnel band that thickens towards the rim, a wide
                // refraction glow just inside it, and soft highlights that follow the curve of the surface; the edge melts into the glow outside.
                vec3 n0 = vec3(q / R, z);
                vec3 cyan = mix(tint, vec3(0.55, 0.95, 1.0), 0.65);
                c3 += cyan * pow(1.0 - z, 1.6) * 0.75;
                c3 += cyan * exp(-pow((dd - 0.86) / 0.12, 2.0)) * 0.35;
                float spec = pow(max(dot(n0, normalize(vec3(-0.45, 0.55, 0.7))), 0.0), 18.0);
                float spec2 = pow(max(dot(n0, normalize(vec3(0.5, -0.4, 0.75))), 0.0), 40.0);
                c3 += vec3(0.85, 0.97, 1.0) * (spec * 0.55 + spec2 * 0.25);
                col = c3;
                // Fades only over the last few percent, handing over to the halo outside at the same strength, so no gap opens between them.
                a = mix(0.85, 1.0, smoothstep(1.0, 0.8, dd));
            } else {
                c3 += hot * rimLine * 1.5 + tint * fres * 0.35;
                col = c3;
                a = smoothstep(1.0, 0.975, dd);
            }
            if (isPlanet) {
                // A sphere of churning matter (the owner's 2D frame): cloudy texture flowing across it, dark and bright patches, fine speckles,
                // a soft lit rim. level 0 drifts one way (Red), 1 the other (Blue).
                // Churning (owner, 2026-11-18): the clouds boil and turn in all directions, they do not slide sideways.
                vec3 m = n3 * 2.4;
                m.xz *= rot(t * (level > 0.5 ? -0.35 : 0.35));
                m.xy *= rot(t * 0.22);
                vec3 wv = vec3(fbm(m * 1.3 + t * 0.4), fbm(m * 1.3 - t * 0.33 + 5.0), fbm(m * 1.3 + t * 0.27 + 9.0));
                float cloud = fbm(m + (wv - 0.5) * 2.2);
                float speck = step(0.93, noise(n3 * 38.0 + t)) * 0.6;
                // Ref 39: a burning ball, white-hot clouds over the colour, hottest towards the middle.
                // Ref 43 (owner, 2026-11-18): dark, black and colour, not white: deep black patches, colour in the folds, white only in a few sparks.
                float core = clamp(1.0 - d, 0.0, 1.0);
                float lit = smoothstep(0.42, 0.85, cloud);
                col = mix(tint * 0.03, tint * (0.55 + 0.7 * cloud), lit) + vec3(1.0) * pow(cloud, 7.0) * 0.5 + hot * (speck * 0.5 + rimLine * 0.5) + tint * fres * 0.6;
            }
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
            if (isPlanet) { shards = 0.0; ink = 0.0; }
            // Blue: no thin shards and no bright ring outside; a soft cyan halo continues the inside edge outward.
            float ringK = isBlue ? 0.0 : 1.0;
            if (isBlue) { shards = 0.0; glowF = exp(-od * 4.0) * (0.75 + 0.25 * corona) * 0.85 / 0.85; }
            float v = rimLine * 1.4 * ringK + glowF + shards * 1.3 + flames * 1.2;
            col = mix(isBlue ? mix(tint, vec3(0.55, 0.95, 1.0), 0.45) : tint * 1.1, hot, clamp(rimLine * ringK + shards, 0.0, 1.0));
            col = mix(col, vec3(0.03, 0.0, 0.05), ink);
            a = clamp(max(v, ink), 0.0, 1.0);
        }
        if (isBlue) {
            // A horizontal lens flare through the core (ref 33).
            float fl = exp(-abs(q.y) * 70.0) * exp(-abs(q.x) * 2.0) * (0.55 + 0.35 * level);
            col += vec3(fl);
            a = max(a, clamp(fl, 0.0, 1.0));
        }
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
    } else if (effect == 12) {
        // BRUSH (Red): a stroke of ink or red paint torn by energy. x runs tail (0) to head (1), y across. The stroke wanders inside its quad
        // (warped centreline), its streaks are few and bent rather than ruled, its edges break sharply, and on some stretches it splits into
        // several thin strands. Light comes in local hot patches that drift along it, never as a highlight down the whole body. Ink is
        // uneven: thick in places, thin and see-through in others, with ember-red cracks.
        float warp = fbm(vec3(p.x * 2.0 - t * 1.2, seed, 0.5));
        float qy = q.y + (warp - 0.5) * 0.6;
        float across = 1.0 - abs(qy);
        float striae = fbm(vec3(p.x * 1.6 - t * 1.4, qy * 3.5 + warp * 2.0, seed * 1.7));
        float edgeN = noise(vec3(p.x * 9.0 - t * 1.5, qy > 0.0 ? seed : seed + 5.0, t * 0.7));
        float edge = 0.05 + 0.62 * edgeN * (1.0 - 0.45 * p.x);
        float body = smoothstep(edge, edge + 0.03, across);
        float split = smoothstep(0.45, 0.65, noise(vec3(p.x * 3.0 - t * 0.8, seed + 2.0, 0.0)));
        float strands = smoothstep(0.25, 0.55, abs(sin((qy + warp) * 7.0 + seed)));
        // Ink tears, it does not split into bristles (strands on wide ink strokes read as a zigzag pattern).
        bool inkTint = dot(tint, vec3(0.3, 0.59, 0.11)) < 0.08;
        body *= mix(1.0, strands, split * (inkTint ? 0.2 : 1.0));
        // Dry brush: the streaks punch holes along the stroke (more towards the tail) instead of shading it, so it never reads as a lit tube.
        float bristle = smoothstep(0.42, 0.56, striae + 0.45 * smoothstep(0.0, 0.5, p.x));
        a = body * bristle * smoothstep(1.0, 0.93, p.x);
        float lum = dot(tint, vec3(0.3, 0.59, 0.11));
        bool ink = lum < 0.08;
        float hotN = fbm(vec3(p.x * 2.5 - t * 2.0, qy * 1.5, seed + 9.0));
        float hot = smoothstep(ink ? 0.68 : 0.58, ink ? 0.82 : 0.78, hotN) * across;
        // Ink is uneven: dense in places, thin and see-through in others.
        // Thin ink stays dark ink, not grey glass: it only thins to about half.
        if (ink) a *= clamp(0.55 + 0.7 * fbm(vec3(p.x * 3.0 - t * 0.5, qy * 2.0, seed + 4.0)), 0.0, 1.0);
        vec3 bright = ink ? vec3(0.62, 0.02, 0.05) : mix(tint, vec3(1.0, 0.92, 0.9), 0.65);
        col = tint * (0.85 + 0.3 * striae);
        col = mix(col, bright, hot);
    } else if (effect == 15) {
        // GALAXY (Purple's tear, owner 2026-11-19): deep space inside the slit, a drifting violet-pink-blue nebula, fine stars, and a bright rim.
        float across = abs(q.y);
        float neb = fbm(vec3(p.x * 4.0 + t * 0.04, q.y * 1.6, seed));
        float swirl = fbm(vec3(p.x * 9.0 - neb * 2.0 - t * 0.06, q.y * 3.0, seed + 4.0));
        vec3 space = vec3(0.015, 0.0, 0.04);
        vec3 nebula = mix(vec3(0.35, 0.05, 0.75), vec3(0.95, 0.30, 0.85), smoothstep(0.4, 0.8, swirl));
        nebula = mix(nebula, vec3(0.20, 0.35, 1.0), smoothstep(0.55, 0.9, neb) * 0.5);
        float cloud = smoothstep(0.42, 0.85, neb * 0.6 + swirl * 0.4) * (1.0 - across * 0.6);
        float star = step(0.9965, hash(vec3(floor(gl_FragCoord.xy * 0.5), seed))) * (1.0 - across);
        float rim = smoothstep(0.62, 0.95, across);
        col = space + nebula * cloud * 0.9 + vec3(star) + vec3(0.85, 0.65, 1.0) * rim * 1.2;
        a = smoothstep(1.0, 0.92, across);
    } else if (effect == 16) {
        // SOLID: plain colour, fully opaque (cinematic bars).
        col = tint;
        a = 1.0;
    } else if (effect == 14) {
        // CHALK (Red's charge, owner refs 2026-11-19): a dry, grainy streak like chalk dragged fast. Many fine hairlines run along it, speckled
        // with grain; it breaks up towards its ragged edges and its tail; a pale hot line runs down its middle.
        float wob = noise(vec3(p.x * 3.0 - t * 0.5, seed, 1.0)) - 0.5;
        float across = abs(q.y + wob * 0.35);
        float hair = noise(vec3(p.x * 1.2 - t * 0.4, q.y * 26.0, seed));
        float fine = noise(vec3(p.x * 6.0 - t * 0.9, q.y * 70.0, seed + 3.0));
        // Smooth grain, never a grid (a hashed grid showed as squares up close).
        float grain = noise(vec3(p.x * 260.0, q.y * 48.0, seed));
        float edge = 0.55 + 0.35 * noise(vec3(p.x * 7.0, seed + 5.0, t * 0.6));
        float body = smoothstep(edge, edge - 0.25, across);
        a = body * smoothstep(0.32, 0.62, hair * 0.65 + fine * 0.35) * (0.45 + 0.55 * grain)
          * smoothstep(0.0, 0.12, p.x) * smoothstep(1.0, 0.85, p.x) * (0.55 + 0.45 * p.x);
        // Dark (ink) chalk keeps its colour; only a coloured stroke gets the hot line.
        float lit = dot(tint, vec3(0.3, 0.59, 0.11)) < 0.08 ? 0.0 : 0.35;
        col = mix(tint, tint * 1.6 + vec3(0.15, 0.0, 0.0), smoothstep(0.22, 0.0, across) * lit);
    } else if (effect == 13) {
        // PLASMA (Blue): liquid energy. A softer cousin of BRUSH: the body is eaten from the edges and from inside by slowly churning noise
        // (more towards the tail, which dissolves), its colour pools deep in thin parts and saturated in thick ones, and it glows in soft hot
        // spots that slide along it towards the head (inward).
        float warp = fbm(vec3(p.x * 2.0 - t * 1.6, q.y * 1.5, seed));
        float n = fbm(vec3(p.x * 3.0 - t * 2.4 + warp * 0.8, q.y * 2.5 + warp, seed * 1.3));
        float edgeN = fbm(vec3(p.x * 4.0 - t * 1.8, q.y * 0.5 + seed, t * 0.7));
        float across = 1.0 - abs(q.y + (warp - 0.5) * 0.35);
        float erosion = 0.15 + 0.5 * (1.0 - smoothstep(0.0, 0.5, p.x));
        float m = across * (0.75 + 0.5 * edgeN) - erosion * (1.0 - n);
        a = smoothstep(0.08, 0.45, m) * smoothstep(1.0, 0.9, p.x);
        float hot = smoothstep(0.62, 0.85, n) * (0.4 + 0.6 * p.x);
        col = mix(tint * 0.4, tint, smoothstep(0.1, 0.6, m)) + mix(tint, vec3(1.0), 0.6) * hot * 0.8;
    } else if (effect == 11) {
        // RING: a FLOW-shaped ribbon that keeps its colour (the owner's ref 40 red and blue rings): saturated body, soft frayed edges, only a thin
        // white thread down the middle.
        float across = 1.0 - abs(q.y);
        float n = fbm(vec3(p.x * 4.0 - t * 1.6, q.y * 1.2, seed));
        float shape = across * smoothstep(0.0, 0.2, p.x) * smoothstep(1.0, 0.9, p.x);
        float v = shape * (0.7 + n * 0.6);
        col = tint * (0.75 + 0.5 * n) + vec3(1.0) * smoothstep(0.82, 0.97, across) * 0.45 * shape;
        a = smoothstep(0.08, 0.35, v);
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
        // Darker (owner, 2026-11-18): near black, long streaks of smoke drifting sideways, red on one side, blue on the other, a violet seam.
        // Redone (owner, round 39): soft, blurred, no fine texture (ref 42). Wide horizontal smoke banks drifting sideways, lit red on the left,
        // magenta in the middle, blue on the right, dark gaps between them; mostly black.
        vec2 u = q * 2.6;   // roughly -1..1 across the screen
        float w1 = noise(vec3(u.x * 0.7 - t * 0.05, u.y * 3.2, t * 0.02));
        float w2 = noise(vec3(u.x * 1.3 + t * 0.03, u.y * 5.5 + w1 * 1.2, t * 0.03 + 5.0));
        float bank = smoothstep(0.38, 0.85, w1 * 0.65 + w2 * 0.35);
        vec3 side = mix(vec3(0.60, 0.05, 0.10), vec3(0.48, 0.10, 0.62), smoothstep(-0.9, 0.0, u.x));
        side = mix(side, vec3(0.08, 0.16, 0.75), smoothstep(0.0, 0.9, u.x));
        float fall = exp(-u.y * u.y * 2.2);   // brightest through the middle band, black towards top and bottom
        // Darker still (owner, round 41) so the spheres stand out.
        col = vec3(0.002, 0.0, 0.004) + side * bank * fall * 0.22 + side * 0.012 * fall;
        a = 1.0;
    } else {
        discard;
    }
    fragColor = vec4(col, a * vertexColor.a) * ColorModulator;
    if (fragColor.a < 0.003) discard;
}
