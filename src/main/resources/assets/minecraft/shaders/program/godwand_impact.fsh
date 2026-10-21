#version 150

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

uniform float ImpactIntensity;
uniform float ImpactFrame;
uniform float ShockwaveRadius;
uniform float DistortionPower;
uniform float ChromaticAmount;
uniform float CenterX;
uniform float CenterY;
uniform float Aspect;

out vec4 fragColor;

void main() {
    // Center point of the shockwave
    vec2 center = vec2(CenterX, CenterY);
    vec2 diff = texCoord - center;

    // Aspect-ratio corrected delta for circular wavefront
    vec2 aspectDiff = vec2(diff.x * Aspect, diff.y);
    float dist = length(aspectDiff);

    // 1. Shockwave Refraction Ring
    float waveThickness = 0.065;
    float deltaDist = dist - ShockwaveRadius;
    float wave = exp(-(deltaDist * deltaDist) / (waveThickness * waveThickness));
    vec2 dir = (dist > 0.0001) ? normalize(aspectDiff) : vec2(0.0);
    vec2 uvOffset = vec2(dir.x / Aspect, dir.y) * wave * DistortionPower;
    vec2 sampleUV = clamp(texCoord + uvOffset, 0.001, 0.999);

    // 2. Chromatic Aberration (Radial RGB Split)
    vec2 caDir = (sampleUV - vec2(0.5)) * ChromaticAmount * (1.0 + wave * 2.0);
    float r = texture(DiffuseSampler, clamp(sampleUV + caDir, 0.0, 1.0)).r;
    float g = texture(DiffuseSampler, sampleUV).g;
    float b = texture(DiffuseSampler, clamp(sampleUV - caDir, 0.0, 1.0)).b;
    vec3 color = vec3(r, g, b);

    // 3. Shockwave Wavefront Luminous Arcane Edge
    vec3 waveGlow = vec3(0.25, 0.88, 1.0) * wave * abs(DistortionPower) * 14.0 * ImpactIntensity;
    color += waveGlow;

    // 4. Anime / Manga High-Contrast Impact Frame Inversion
    if (ImpactFrame > 0.001) {
        float lum = dot(color, vec3(0.299, 0.587, 0.114));
        float invLum = 1.0 - lum;

        vec3 darkInk = vec3(0.04, 0.04, 0.07);
        vec3 neonCyan = vec3(0.32, 0.95, 1.0);
        vec3 whiteHot = vec3(1.0, 1.0, 1.0);

        vec3 impactGrade;
        if (invLum > 0.65) {
            impactGrade = mix(neonCyan, whiteHot, (invLum - 0.65) / 0.35);
        } else {
            impactGrade = mix(darkInk, neonCyan, smoothstep(0.18, 0.65, invLum));
        }

        color = mix(color, impactGrade, ImpactFrame);
    }

    // 5. Arcane Vignette / Dark Atmospheric Pressure
    if (ImpactIntensity > 0.001) {
        float vig = smoothstep(0.35, 1.15, length(texCoord - vec2(0.5)) * 1.4);
        color *= (1.0 - vig * 0.45 * ImpactIntensity);
        color += vec3(0.02, 0.14, 0.28) * vig * ImpactIntensity;
    }

    fragColor = vec4(color, 1.0);
}
