#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D MainDepthSampler;

in vec2 texCoord;

uniform vec2 OutSize;
uniform mat4 InvProjMat;
uniform mat4 InvViewMat;
uniform vec3 CameraPos;
uniform vec3 BlastCenter;
uniform float CoreRadius;
uniform float NovaAftermath;
uniform float NovaProgress;
uniform float SkyRiftAge;
uniform float SkyRiftScale;
uniform vec3 SkyRiftCenter;
uniform float SkyRiftCollapse;
uniform float SkyBlackout;
uniform float RiftPulseRadius;
uniform float RiftPulseStrength;
uniform float EmissionOnly;
uniform float CollapseProgress;
uniform float LensingPower;

uniform float ImpactIntensity;
uniform float ImpactFrame;
uniform float ImpactMode;
uniform float ShockwaveRadius;
uniform float DistortionPower;
uniform float ChromaticAmount;
uniform float ZoomBlur;
uniform float RetinaBurn;
uniform float CenterX;
uniform float CenterY;
uniform float Aspect;
uniform float GTime;
uniform float FxType;

out vec4 fragColor;

#define TAU 6.28318530718

float saturate(float x) {
    return clamp(x, 0.0, 1.0);
}

float sqr(float x) {
    return x * x;
}

float lum(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 prismStop(int i) {
    return i == 0 ? vec3(1.0, 0.07, 0.10) :
           i == 1 ? vec3(1.0, 0.42, 0.03) :
           i == 2 ? vec3(1.0, 0.88, 0.06) :
           i == 3 ? vec3(0.10, 0.95, 0.28) :
           i == 4 ? vec3(0.06, 0.48, 1.00) :
           i == 5 ? vec3(0.28, 0.14, 1.00) :
                    vec3(0.74, 0.16, 1.00);
}

vec3 prism7(float h) {
    float x = fract(h) * 7.0;
    int i = int(floor(x));
    float f = x - float(i);
    f = f * f * (3.0 - 2.0 * f);
    return mix(prismStop(i), prismStop(i == 6 ? 0 : i + 1), f);
}

const vec3 VOID_VIOLET = vec3(0.72, 0.025, 0.62);
const vec3 VOID_MAGENTA = vec3(1.0, 0.055, 0.36);
const vec3 VOID_LIGHT = vec3(1.0, 0.76, 0.94);

vec3 voidPlasma(float heat) {
    return mix(VOID_VIOLET * 0.45, mix(VOID_VIOLET, VOID_MAGENTA, 0.4), smoothstep(0.08, 0.75, heat))
            + VOID_LIGHT * pow(clamp(heat, 0.0, 1.0), 6.0) * 0.55;
}

vec3 blackbody(float heat) {
    float t = clamp(heat, 0.0, 1.2);
    vec3 c = vec3(0.0);
    c.r = smoothstep(0.02, 0.45, t) + smoothstep(0.85, 1.15, t) * 0.4;
    c.g = smoothstep(0.18, 0.75, t);
    c.b = smoothstep(0.48, 1.00, t) + smoothstep(0.80, 1.20, t) * 0.6;
    return c;
}

vec3 tonemap(vec3 c) {
    vec3 hot = max(c - 1.0, 0.0);
    c += dot(hot, vec3(0.3333)) * 0.45;
    vec3 over = max(c - 0.85, 0.0);
    return min(c, 0.85) + 0.15 * (1.0 - exp(-over / 0.15));
}

float cloudHash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

// Value and analytic gradient share the same eight corners for billow lighting.
vec4 cloudNoise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float a = cloudHash(i), b = cloudHash(i + vec3(1,0,0));
    float c = cloudHash(i + vec3(0,1,0)), d = cloudHash(i + vec3(1,1,0));
    float e = cloudHash(i + vec3(0,0,1)), g = cloudHash(i + vec3(1,0,1));
    float h = cloudHash(i + vec3(0,1,1)), j = cloudHash(i + vec3(1,1,1));
    float x00 = mix(a,b,u.x), x10 = mix(c,d,u.x);
    float x01 = mix(e,g,u.x), x11 = mix(h,j,u.x);
    float lower = mix(x00,x10,u.y), upper = mix(x01,x11,u.y);
    vec3 gradient = vec3(mix(mix(b-a,d-c,u.y),mix(g-e,j-h,u.y),u.z),
                         mix(x10-x00,x11-x01,u.z), upper-lower) * (6.0 * f * (1.0-f));
    return vec4(mix(lower,upper,u.z), gradient);
}

void main() {
    // Inactive impacts are a plain copy; Spatial Path never invokes this pipeline.
    if (FxType < -0.5) {
        fragColor = texture(DiffuseSampler, texCoord);
        return;
    }
    vec2 center = vec2(CenterX, CenterY);
    vec2 diff = texCoord - center;
    vec2 aspectDiff = vec2(diff.x * Aspect, diff.y);
    float dist = length(aspectDiff);

    // =========================================================================
    // 0. 3D RAYMARCHING, DEPTH RECONSTRUCTION & GRAVITATIONAL LENSING
    // =========================================================================
    vec2 lensOffset = vec2(0.0);
    vec3 addGlow = vec3(0.0);
    float novaOpacity = 0.0;
    vec3 novaVolume = vec3(0.0);
    vec3 riftColor = vec3(0.0);
    float riftMask = 0.0;
    float blackout = ((FxType > 2.5 && FxType < 4.5) || (FxType > 8.5 && FxType < 9.5))
            ? clamp(SkyBlackout, 0.0, 1.0) : 0.0;
    bool insideHorizon = false;
    float impactParamVal = 999.0;

    if (CoreRadius > 0.005 || (FxType > 3.5 && FxType < 4.5) || SkyRiftScale > 0.001 || RiftPulseStrength > 0.001) {
        // Construct 3D camera ray in camera-relative world space
        vec2 ndc = texCoord * 2.0 - 1.0;
        vec4 viewFar = InvProjMat * vec4(ndc, 1.0, 1.0);
        viewFar.xyz /= viewFar.w;
        vec3 rd = normalize((InvViewMat * vec4(viewFar.xyz, 0.0)).xyz);

        // Reconstruct scene depth from Minecraft's main depth buffer
        float rawDepth = texture(MainDepthSampler, texCoord).r;
        vec4 viewHit = InvProjMat * vec4(ndc, rawDepth * 2.0 - 1.0, 1.0);
        viewHit.xyz /= viewHit.w;
        vec4 worldHitRel = InvViewMat * vec4(viewHit.xyz, 1.0);
        float sceneDist = (rawDepth >= 0.99999) ? 1e6 : length(viewHit.xyz);

        // A world-anchored aperture only replaces untouched sky, never terrain or the horizon.
        if (SkyRiftScale > 0.001 && rawDepth >= 0.99999 && rd.y > 0.06
                && ((FxType > 2.5 && FxType < 4.5) || (FxType > 8.5 && FxType < 9.5))) {
            float opening = SkyRiftScale;
            float visibility = clamp(opening * 8.0, 0.0, 1.0);
            float planeT = (SkyRiftCenter.y + 110.0 - CameraPos.y) / rd.y;
            if (opening > 0.001 && planeT > 0.0) {
                vec2 q = (CameraPos + rd * planeT - SkyRiftCenter).xz;
                float angle = atan(q.y, q.x);
                float radius = max(1.0, opening * (138.0 + 24.0 * sin(angle * 3.0 + 0.5)
                        + 13.0 * sin(angle * 7.0 - 0.4) + 5.0 * sin(angle * 17.0 + SkyRiftAge * 0.014)));
                float torn = length(q) - radius;
                float horizon = smoothstep(0.06, 0.18, rd.y);
                riftMask = (1.0 - smoothstep(-1.5, 1.5, torn)) * horizon * visibility;
                float gas = cloudNoise(vec3(q * 0.021, SkyRiftAge * 0.002)).x;
                float filament = cloudNoise(vec3(q * 0.058 + 8.0, SkyRiftAge * 0.003)).x;
                riftColor = vec3(0.012, 0.002, 0.015) + vec3(0.25, 0.012, 0.19)
                        * pow(gas, 2.0) + vec3(0.055, 0.003, 0.035) * gas * filament;
                vec2 stars = q * 0.42;
                vec2 cell = floor(stars);
                vec2 spot = fract(stars) - vec2(hash12(cell), hash12(cell + 43.7));
                float aa = max(length(fwidth(stars)), 0.02);
                float star = (1.0 - smoothstep(0.018, 0.055 + aa, length(spot))) * 0.08 / (0.08 + aa)
                        * step(0.78, hash12(cell + 19.2));
                riftColor += VOID_LIGHT * star;
                float rim = exp(-sqr(torn / 2.5)) * 0.6 + exp(-abs(torn) / 8.0) * 0.12;
                float tendril = pow(0.5 + 0.5 * sin(angle * 37.0 + length(q) * 0.11
                        + gas * 8.0 - SkyRiftAge * 0.018), 6.0);
                float corona = exp(-abs(torn - 4.0 - filament * 8.0) / 8.0) * gas;
                float wisps = exp(-abs(torn + 9.0) / 17.0) * tendril * (1.0 + SkyRiftCollapse);
                float closingLip = exp(-abs(length(q) - radius * 1.10) / 3.0)
                        * sin(SkyRiftCollapse * 3.14159265);
                rim += closingLip * 1.4;
                float folds = 0.75 + 0.25 * sin(angle * 19.0 - length(q) * 0.12 + SkyRiftAge * 0.025);
                addGlow += mix(VOID_VIOLET, VOID_MAGENTA, gas * 0.75)
                        * (rim * folds * 1.65 + corona * 0.65 + wisps * 0.85) * visibility * horizon * (1.0 - blackout);
            }
        }

        // The detonation swallows the entire sky dome; daylight returns on the same closing clock.
        if (blackout > 0.001 && rawDepth >= 0.99999) {
            vec3 skyAxis = normalize(SkyRiftCenter + vec3(0.0, 110.0, 0.0) - CameraPos);
            float angle = acos(clamp(dot(rd, skyAxis), -1.0, 1.0));
            float cover = 1.0 - smoothstep(blackout * 3.5 - 0.08, blackout * 3.5 + 0.08, angle);
            float nebula = cloudNoise(rd * 12.0 + vec3(SkyRiftAge * 0.001)).x;
            vec3 lostSky = vec3(0.004, 0.001, 0.006) + VOID_VIOLET * nebula * 0.012;
            riftColor = mix(riftColor, lostSky, cover);
            riftMask = max(riftMask, cover);
        }

        // One outward release pulse, then one inward return pulse on the same world center.
        if (RiftPulseStrength > 0.001 && RiftPulseRadius > 0.01 && rawDepth >= 0.99999 && rd.y > 0.06
                && ((FxType > 2.5 && FxType < 4.5) || (FxType > 8.5 && FxType < 9.5))) {
            vec3 pulseCenter = SkyRiftCenter - CameraPos;
            float projection = dot(pulseCenter, rd);
            float lateralSq = dot(pulseCenter, pulseCenter) - projection * projection;
            float discriminant = RiftPulseRadius * RiftPulseRadius - lateralSq;
            if (discriminant > 0.0) {
                float nearHit = projection - sqrt(discriminant);
                float hit = nearHit > 0.0 ? nearHit : projection + sqrt(discriminant);
                if (hit > 0.0 && hit < sceneDist) {
                    vec3 normal = normalize(rd * hit - pulseCenter);
                    float edge = pow(1.0 - abs(dot(normal, rd)), 3.0);
                    addGlow += VOID_VIOLET * (0.04 + edge * 0.24) * RiftPulseStrength * smoothstep(0.06, 0.25, rd.y);
                }
            }
        }

        // Black Hole center relative to camera
        vec3 bhRel = BlastCenter - CameraPos;
        float distToBH = length(bhRel);
        float tBH = dot(bhRel, rd);
        vec3 pClosest = rd * tBH;
        float impactParam = length(pClosest - bhRel);
        impactParamVal = impactParam;

        bool inFront = (tBH > -CoreRadius);
        bool coreOccluded = (sceneDist < (tBH - CoreRadius * 1.1));

        // =========================================================================
        // NHÁNH 1: CHIÊU 4 - ĐỘC QUYỀN HỐ ĐEN (FxType == 4.0: TYPE_BLACK_HOLE)
        // Tuyệt đối chỉ chạy khi FxType đúng bằng 4.0, không lọt sang Chiêu 3 (FxType 6.0)
        // =========================================================================
        if (FxType > 3.5 && FxType < 4.5 && inFront) {
            // 1. Lensing, Event Horizon & Einstein Ring chỉ hiện khi tâm Hố Đen không bị che
            if (!coreOccluded) {
                if (impactParam < CoreRadius && sceneDist > (tBH - CoreRadius * 0.8)) {
                    insideHorizon = true;
                }

                if (sceneDist > (tBH - CoreRadius * 0.8)) {
                    float normalizedRadius = impactParam / max(CoreRadius, 0.05);
                    float ringWidth = max(0.028, fwidth(normalizedRadius));
                    float einsteinRing = exp(-sqr((normalizedRadius - 1.06) / ringWidth));
                    vec3 rimPoint = rd * tBH - bhRel;
                    float rimAngle = atan(rimPoint.y, rimPoint.x);
                    float crescent = 0.5 + 0.5 * sin(rimAngle * 2.0 + GTime * 0.7);
                    vec3 ringCol = mix(VOID_MAGENTA, VOID_LIGHT, 0.25 + crescent * 0.55)
                            * einsteinRing * 2.6;
                    float outerEdge = exp(-sqr((normalizedRadius - 1.16) / 0.045));
                    float violetHalo = exp(-sqr((normalizedRadius - 1.12) / 0.22));
                    addGlow += ringCol + VOID_VIOLET * outerEdge * 0.75 + VOID_MAGENTA * violetHalo * 0.24;
                }

            }

            float flare = smoothstep(82.0 / 90.0, 87.0 / 90.0, CollapseProgress);
            if (!coreOccluded && flare > 0.0 && sceneDist > (tBH - CoreRadius * 0.8)) {
                vec3 flarePoint = rd * tBH - bhRel;
                vec3 cameraRight = normalize(mat3(InvViewMat) * vec3(1.0, 0.0, 0.0));
                vec3 cameraUp = normalize(mat3(InvViewMat) * vec3(0.0, 1.0, 0.0));
                vec2 flareUV = vec2(dot(flarePoint, cameraRight), dot(flarePoint, cameraUp)) / max(tBH, 1.0);
                float streak = exp(-abs(flareUV.y) * 260.0) * exp(-abs(flareUV.x) * 3.0);
                float star = exp(-length(flareUV) * 48.0);
                float vertical = exp(-abs(flareUV.x) * 330.0) * exp(-abs(flareUV.y) * 24.0);
                addGlow += (VOID_MAGENTA * streak * 1.7 + VOID_LIGHT * (star * 3.5 + vertical * 1.2)) * flare;
            }

            // 2. Relativistic Accretion Disk (Đĩa bồi tụ nghiêng 18 độ, xoay chao sống động quanh tâm)
            vec3 diskNormal = normalize(vec3(sin(GTime * 0.35) * 0.26, 0.96, cos(GTime * 0.35) * 0.26));
            float denomDisk = dot(rd, diskNormal);
            if (abs(denomDisk) > 0.0005) {
                float tDisk = dot(bhRel, diskNormal) / denomDisk;
                if (tDisk > 0.0 && tDisk < sceneDist && (!insideHorizon || tDisk < tBH)) {
                    vec3 relDisk = (rd * tDisk) - bhRel;
                    float swallow = smoothstep(0.0, 0.84, CollapseProgress);
                    float diskFade = 1.0 - smoothstep(32.0 / 60.0, 0.84, CollapseProgress);
                    float diskAngle = atan(relDisk.z, relDisk.x);
                    float travel = swallow * CoreRadius * (4.5 + 0.35 * sin(diskAngle * 3.0 - swallow * 8.0));
                    float rDisk = length(relDisk) + travel;
                    if (rDisk > CoreRadius * 1.05 && rDisk < CoreRadius * 3.5) {
                        diskAngle += GTime * 7.5 - travel * 0.65;
                        vec3 flowPoint = relDisk / max(CoreRadius, 0.05);
                        float eddies = cloudNoise(flowPoint * 2.1 + vec3(GTime * 0.12, 0.0, -GTime * 0.15)).x;
                        float ridge = pow(0.5 + 0.5 * sin(diskAngle * 3.0 + log(max(rDisk / CoreRadius, 1.0)) * 9.0 + eddies * 7.0), 3.0);
                        float swirl = (0.25 + 0.75 * eddies) * (0.3 + 0.7 * ridge);
                        vec3 orbVel = normalize(cross(diskNormal, relDisk));
                        float doppler = 1.0 + 0.45 * dot(rd, orbVel);
                        float diskHeat = saturate(1.2 - (rDisk - CoreRadius) / (CoreRadius * 2.6));
                        float diskAlpha = smoothstep(CoreRadius * 1.05, CoreRadius * 1.3, rDisk) *
                                          (1.0 - smoothstep(CoreRadius * 2.4, CoreRadius * 3.5, rDisk)) * smoothstep(0.24, 0.72, eddies);
                        addGlow += voidPlasma(diskHeat) * swirl * doppler * 2.2 * diskAlpha * diskFade;
                    }
                }
            }

            if (!coreOccluded && impactParam > CoreRadius && tBH < sceneDist) {
                vec3 ribbonPoint = rd * tBH - bhRel;
                float radius = length(ribbonPoint) / max(CoreRadius, 0.05);
                float angle = atan(ribbonPoint.z + ribbonPoint.y * 0.5, ribbonPoint.x);
                float spiral = angle * 7.0 + log(max(radius, 1.0)) * 12.0 - GTime * (4.0 + CollapseProgress * 8.0);
                float filament = pow(max(0.0, cos(spiral)), 42.0);
                float annulus = smoothstep(1.0, 1.4, radius) * (1.0 - smoothstep(3.0, 6.0, radius));
                addGlow += mix(VOID_VIOLET, VOID_MAGENTA, 0.65) * filament * annulus * (0.35 + CollapseProgress * 1.5) * (1.0 - smoothstep(0.60, 0.86, CollapseProgress));
            }

            // Broken turbulent shells carry the rotation through a real 3D volume.
            if (!coreOccluded && impactParam > CoreRadius * 1.02) {
                float capture = smoothstep(0.0, 0.84, CollapseProgress);
                float shellLife = smoothstep(0.35, 1.4, CoreRadius)
                        * (1.0 - smoothstep(0.58, 0.84, CollapseProgress));
                for (int layer = 0; layer < 2; layer++) {
                    float shellRadius = CoreRadius * (1.8 + (0.6 + float(layer) * 0.7) * (1.0 - capture));
                    float discriminant = shellRadius * shellRadius - impactParam * impactParam;
                    if (discriminant <= 0.0 || shellLife <= 0.001) continue;
                    float nearHit = tBH - sqrt(discriminant);
                    float hit = nearHit > 0.0 ? nearHit : tBH + sqrt(discriminant);
                    if (hit <= 0.0 || hit >= sceneDist) continue;
                    vec3 point = (rd * hit - bhRel) / max(CoreRadius, 0.05);
                    vec3 normal = normalize(point);
                    float curl = atan(point.z, point.x) * 6.0 + point.y * 5.0 - GTime * 1.6;
                    float gas = cloudNoise(point * 1.3 + vec3(GTime * 0.12, -GTime * 0.18, 0.0)).x;
                    float detail = cloudNoise(point * 3.1 - vec3(0.0, GTime * 0.25, 0.0)).x;
                    float edge = pow(1.0 - abs(dot(normal, rd)), 1.6);
                    float strands = pow(max(0.0, cos(curl + gas * 7.0)), 24.0);
                    float breaks = smoothstep(0.30, 0.66, gas);
                    float cloud = smoothstep(0.28, 0.72, gas * 0.7 + detail * 0.3) * (0.2 + edge * 0.8) * breaks * shellLife;
                    novaOpacity = max(novaOpacity, cloud * 0.24);
                    novaVolume += VOID_VIOLET * cloud * 0.08;
                    addGlow += mix(VOID_VIOLET, VOID_MAGENTA, detail)
                            * (strands * (0.2 + edge * 0.8) + cloud * 0.75) * breaks * shellLife * 0.85;
                }
            }

            // 3. Volumetric Relativistic Pulsar Jets (Từ không có -> to rõ, rực sáng uy lực theo yêu cầu)
            float jetScale = clamp((CoreRadius - 0.85) / (8.5 - 0.85), 0.0, 1.0) * (1.0 - smoothstep(0.0, 0.3, CollapseProgress));
            if (jetScale > 0.005) {
                vec2 jetXZ = bhRel.xz;
                float denom = max(dot(rd.xz, rd.xz), 1e-6);
                float tJet = dot(jetXZ, rd.xz) / denom;
                if (tJet > 0.0 && tJet < sceneDist) {
                    vec3 pJet = rd * tJet;
                    float dJet = length(pJet.xz - bhRel.xz);
                    float hJet = pJet.y - bhRel.y;
                    float maxDown = -CoreRadius * 2.5 * jetScale;
                    float maxUp = CoreRadius * 3.0 * jetScale;
                    if (hJet > maxDown && hJet < maxUp) {
                        // Cột sáng Relativistic Pulsar Jet mỏng, sắc nét, tập trung, không che khuất tầm nhìn camera
                        float wFactor = max(0.06, pow(jetScale, 1.25) * 0.45);
                        float jetCore = exp(-dJet / (0.40 * wFactor));
                        float jetSheath = exp(-dJet / (1.10 * wFactor)) * 0.65 * jetScale;
                        float jetAura = exp(-dJet / (2.60 * wFactor)) * 0.20 * jetScale;
                        float jetFade = smoothstep(maxDown, maxDown * 0.85, hJet) * (1.0 - smoothstep(maxUp * 0.75, maxUp, hJet));
                        vec3 jetColor = (VOID_LIGHT * jetCore * 2.0 +
                                         VOID_MAGENTA * jetSheath * 1.4 +
                                         VOID_VIOLET * jetAura * 0.9) * jetFade * jetScale;
                        addGlow += jetColor * (0.95 + 0.05 * sin(GTime * 1.8));
                    }
                }
            }
        }
        // =========================================================================
        // NHÁNH 2: CHIÊU 4 - ĐẠI VỤ NỔ SIÊU TÂN TINH (FxType == 3.0: TYPE_SUPERNOVA)
        // Volumetric 3D Mushroom Cloud & Hemispherical Fireball
        // Tuyệt đối không phẳng 2D, không lạm dụng distortion
        // =========================================================================
        else if (FxType > 2.5 && FxType < 3.5 && inFront) {
            float age = NovaProgress * 240.0;
            float lifeAge = age - clamp(age - 48.0, 0.0, 40.0);
            float alive = 1.0 - smoothstep(0.65, 1.0, lifeAge / 240.0);
            float r = max(CoreRadius, 0.1);
            float radial = impactParam / r;
            float haloEntry = tBH - sqrt(max(0.0, sqr(r * 1.15) - sqr(impactParam)));
            if (haloEntry < 0.0) haloEntry = tBH + sqrt(max(0.0, sqr(r * 1.15) - sqr(impactParam)));
            float rimLife = 1.0 - smoothstep(38.0, 100.0, age);
            if (sceneDist > haloEntry && haloEntry > 0.0) {
                float haze = exp(-sqr((radial - 1.0) / 0.12));
                float violetEdge = exp(-sqr((radial - 1.035) / 0.018));
                vec3 edgePoint = (rd * tBH - bhRel) / r;
                float billow = cloudNoise(edgePoint * 3.5 - vec3(0, age * 0.04, 0)).x;
                addGlow += (vec3(0.65, 0.045, 0.11) * haze * (0.3 + billow * 0.7)
                        + vec3(0.22, 0.045, 0.30) * violetEdge * 0.18) * rimLife;
            }
            if (!coreOccluded && age < 12.0 && tBH > 0.0) {
                vec3 crossPoint = rd * tBH - bhRel;
                vec3 cameraRight = normalize(mat3(InvViewMat) * vec3(1.0, 0.0, 0.0));
                vec3 cameraUp = normalize(mat3(InvViewMat) * vec3(0.0, 1.0, 0.0));
                vec2 angular = vec2(dot(crossPoint, cameraRight), dot(crossPoint, cameraUp)) / max(tBH, 1.0);
                float crossGlare = exp(-abs(angular.y) * 280.0) * exp(-abs(angular.x) * 3.5);
                addGlow += vec3(1.2, 0.8, 1.0) * crossGlare * exp(-age / 5.0);
            }
            if (sceneDist > haloEntry && haloEntry > 0.0) {
                float heatBand = exp(-sqr((radial - 0.95) / 0.16));
                lensOffset += vec2(sin(rd.y * tBH * 0.28 + age * 0.09), cos(rd.x * tBH * 0.31 - age * 0.07))
                        * heatBand * NovaAftermath * alive * 0.0012;
            }
            float volumeRadius = r * 1.06;
            float chordSq = volumeRadius * volumeRadius - impactParam * impactParam;
            if (chordSq > 0.0) {
                float halfChord = sqrt(chordSq);
                float nearT = tBH - halfChord, farT = tBH + halfChord;
                float entry = max(0.0, nearT), exitT = min(sceneDist, farT);
                if (exitT > entry) {
                    float stepLength = (exitT - entry) / 3.0;
                    float transmission = 1.0;
                    vec3 radiance = vec3(0.0);
                    float massLife = 1.0 - smoothstep(48.0, 140.0, lifeAge);
                    float cooling = smoothstep(70.0, 135.0, lifeAge);
                    float turn = age * 0.012;
                    mat2 spin = mat2(cos(turn), -sin(turn), sin(turn), cos(turn));
                    for (int sampleIndex = 0; sampleIndex < 3; sampleIndex++) {
                        float t = entry + (float(sampleIndex) + 0.5) * stepLength;
                        vec3 local = (rd * t - bhRel) / r;
                        vec3 flow = local * 6.2;
                        flow.xz = spin * flow.xz;
                        flow.y -= age * 0.065;
                        flow += sin(flow.yzx * 1.3 + vec3(0.0, 2.1, 4.2) + age * 0.025) * 0.40;
                        vec4 coarse = cloudNoise(flow);
                        vec4 fine = cloudNoise(flow * 2.4 + vec3(7.1, 3.2, -5.4));
                        float detail = fine.x;
                        float edge = length(local) + (coarse.x - 0.5) * 0.16;
                        float density = (0.55 + coarse.x * 0.65 + detail * 0.2)
                                * (1.0 - smoothstep(0.82, 1.04, edge)) * massLife;
                        float opacity = 1.0 - exp(-density * stepLength / r * 26.0);
                        float heat = saturate(0.25 + coarse.x * 0.65 + detail * 0.20 + (1.0 - length(local)) * 0.45);
                        vec3 normal = normalize(local * 0.8 - coarse.yzw * 0.28 - fine.yzw * 0.11 + vec3(0.0001));
                        float light = 0.30 + 0.70 * max(0.0, dot(normal, normalize(vec3(-0.4, 0.8, 0.35))));
                        vec3 fire = mix(vec3(0.07, 0.002, 0.025), vec3(1.25, 0.07, 0.13), smoothstep(0.22, 0.66, heat));
                        fire = mix(fire, vec3(1.9, 0.95, 0.55), smoothstep(0.60, 0.94, heat) * 0.8) * light;
                        float coreHeat = exp(-radial * radial * 3.0)
                                * (0.9 * exp(-age / 8.0) + 0.25 * exp(-age / 60.0) + 0.08);
                        fire += vec3(1.5, 0.9, 0.65) * coreHeat * (0.6 + detail * 0.4);
                        fire = mix(fire, vec3(0.12, 0.035, 0.07) * (0.5 + coarse.x), cooling);
                        radiance += transmission * opacity * fire;
                        transmission *= 1.0 - opacity;
                        if (transmission < 0.005) break;
                    }
                    novaOpacity = (1.0 - transmission) * alive;
                    novaVolume = radiance * alive;
                    // Short tongues of light follow the body instead of outlining a force field.
                    vec3 surface = normalize(rd * (nearT > 0.0 ? nearT : farT) - bhRel);
                    float arcs = 0.0, arcHalo = 0.0;
                    for (int i = 0; i < 3; i++) {
                        float lane = float(i), localAge = max(0.0, age - lane * 1.5);
                        float angle = localAge * 0.10 + lane * 2.094;
                        vec3 axis = normalize(vec3(cos(angle), 0.55 + lane * 0.3, sin(angle)));
                        vec3 tangent = normalize(cross(axis, vec3(0.0, 1.0, 0.0)));
                        float longitude = atan(dot(surface, cross(axis, tangent)), dot(surface, tangent));
                        float curve = dot(surface, axis) - 0.20 * sin(longitude * 2.0 - localAge * 0.08);
                        float tip = pow(max(0.0, sin(longitude - localAge * 0.14 + lane)), 3.0);
                        float reveal = smoothstep(0.0, 3.0, localAge) * tip;
                        arcs += exp(-sqr(curve) / 0.0005) * reveal;
                        arcHalo += exp(-sqr(curve) / 0.005) * reveal;
                    }
                    float arcLife = 1.0 - smoothstep(28.0, 80.0, age);
                    addGlow += (vec3(1.2, 0.38, 0.38) * arcs * 0.85
                            + vec3(0.65, 0.025, 0.12) * arcHalo * 0.35) * arcLife * novaOpacity;
                }
            }
            // Two pressure fronts are ray/plane intersections in the world, never HUD circles.
            for (int i = 0; i < 2; i++) {
                float waveAge = age - float(i) * 3.0;
                float fade = smoothstep(0.0, 2.0, waveAge) * (1.0 - smoothstep(10.0, 24.0, waveAge));
                vec3 normal = normalize(i == 0 ? vec3(0.0, 1.0, 0.0) : vec3(0.12, 1.0, -0.08));
                vec3 waveCenter = bhRel - vec3(0.0, i == 0 ? 19.0 : 10.0, 0.0);
                float denom = dot(rd, normal);
                if (abs(denom) > 0.0001 && fade > 0.0) {
                    float t = dot(waveCenter, normal) / denom;
                    if (t > 0.0 && t < sceneDist + 1.0) {
                        float distance = length(rd * t - waveCenter);
                        vec3 pressurePoint = rd * t - waveCenter;
                        float pressureAngle = atan(pressurePoint.z, pressurePoint.x);
                        float front = 14.0 + waveAge * 5.8 + sin(pressureAngle * 7.0) * 1.4;
                        float width = 0.8 + max(waveAge, 0.0) * 0.07;
                        float edge = exp(-sqr((distance - front) / width));
                        float wake = exp(-sqr((distance - front + 3.0) / (width * 4.0)));
                        float fringe = exp(-sqr((distance - front - 2.0) / (width * 0.65)));
                        addGlow += (vec3(2.0, 1.3, 1.6) * edge * 0.95 + vec3(0.85, 0.02, 0.24) * wake
                                + vec3(0.28, 0.14, 1.0) * fringe) * fade;
                    }
                }
            }
            float rayFade = (1.0 - smoothstep(14.0, 34.0, age));
            if (rayFade > 0.0) for (int i = 0; i < 20; i++) {
                float seed = float(i);
                float y = -0.8 + 1.7 * fract(seed * 0.618034 + 0.2);
                float angle = seed * 2.399963;
                vec3 direction = vec3(cos(angle) * sqrt(1.0 - y * y), y, sin(angle) * sqrt(1.0 - y * y));
                float launch = max(0.0, age - mod(seed, 4.0) * 0.8);
                vec3 start = bhRel + direction * (r * 0.4 + launch * 2.5);
                float segmentLength = 20.0 + launch * (3.0 + fract(seed * 0.37) * 1.5);
                float c = dot(rd, direction);
                float along = clamp((c * dot(rd, start) - dot(direction, start)) / max(1.0 - c * c, 0.0001), 0.0, segmentLength);
                vec3 point = start + direction * along;
                float rayT = dot(point, rd);
                if (rayT > 0.0 && rayT < sceneDist) {
                    float distance = length(rd * rayT - point);
                    float width = 0.10 + fract(seed * 0.43) * 0.16;
                    float tip = sin((along / segmentLength) * 3.141593);
                    vec3 tint = mix(vec3(0.35, 0.08, 1.0), vec3(1.0, 0.26, 0.58), fract(seed * 0.73));
                    addGlow += (vec3(1.8, 1.4, 1.0) * exp(-sqr(distance / width)) * 3.0
                            + tint * exp(-sqr(distance / (width * 4.5)))) * tip * rayFade;
                }
            }

        }

        // =========================================================================
        // NHÁNH 3: CHIÊU 2 - CẦU NĂNG LƯỢNG MA PHÁP (FxType == 0.0: TYPE_DETONATION)
        // Không lỗ đen, không đĩa bồi tụ, nhịp thở scale nhẹ nhàng êm ái
        // =========================================================================
        else if ((FxType < 0.5 || (FxType > 6.5 && FxType < 7.5)) && inFront && CoreRadius > 0.05) {
            float dSphere = impactParam;
            float radius = CoreRadius;
            if (!coreOccluded && dSphere < radius * 2.3 && sceneDist > tBH - radius) {
                float radial = dSphere / radius;
                float halo = exp(-radial * radial * 1.6);
                vec3 plasma = vec3(0.16, 0.48, 1.0) * halo * 0.55;
                if (dSphere < radius) {
                    float surfaceDepth = sqrt(max(0.0, radius * radius - dSphere * dSphere));
                    float surfaceT = tBH - surfaceDepth;
                    if (surfaceT < 0.0) surfaceT = tBH + surfaceDepth;
                    vec3 normal = normalize(rd * surfaceT - bhRel);
                    float latitude = normal.y;
                    float longitude = atan(normal.z, normal.x);
                    float flow = sin(longitude * 5.0 + latitude * 11.0 - GTime * 2.8);
                    float veins = pow(max(0.0, cos(longitude * 7.0 - latitude * 9.0 + GTime * 3.3)), 14.0);
                    float fresnel = pow(1.0 - abs(dot(normal, rd)), 2.5);
                    float light = 0.55 + 0.45 * max(0.0, dot(normal, normalize(vec3(-0.4, 0.8, 0.5))));
                    vec3 shell = mix(vec3(0.10, 0.25, 0.70), vec3(0.40, 0.12, 0.85), flow * 0.5 + 0.5);
                    plasma += shell * light * 0.75 + vec3(0.18, 0.85, 1.0) * (fresnel * 1.7 + veins * 0.7);
                    plasma += vec3(0.72, 0.96, 1.0) * exp(-radial * radial * 13.0) * 1.9;
                }
                // Tilted, broken plasma bands orbit in 3D instead of forming static particle circles.
                for (int band = 0; band < 2; band++) {
                    vec3 axis = band == 0 ? normalize(vec3(0.3, 0.85, 0.43)) : normalize(vec3(-0.7, 0.35, 0.62));
                    float denom = dot(rd, axis);
                    if (abs(denom) > 0.001) {
                        float t = dot(bhRel, axis) / denom;
                        vec3 point = rd * t - bhRel;
                        float ringRadius = length(point) / radius;
                        bool visible = t > 0.0 && t < sceneDist && (dSphere >= radius || t < tBH - sqrt(max(0.0, radius * radius - dSphere * dSphere)));
                        if (visible) {
                            vec3 u = normalize(cross(axis, vec3(0, 1, 0)));
                            vec3 v = cross(axis, u);
                            float angle = atan(dot(point, v), dot(point, u));
                            float phase = angle * 3.0 - GTime * (band == 0 ? 2.6 : -2.1);
                            float streak = pow(max(0.0, cos(phase)), 3.0);
                            float ribbon = exp(-sqr((ringRadius - 1.46 - sin(phase) * 0.045) / 0.045));
                            plasma += mix(vec3(0.46, 0.15, 1.0), vec3(0.35, 0.95, 1.0), streak) * ribbon * (0.25 + streak * 1.8);
                        }
                    }
                }
                addGlow += plasma * min(ImpactIntensity, 1.4);
                vec2 lensDir = dist > 0.0001 ? normalize(aspectDiff) : vec2(0);
                lensOffset += vec2(lensDir.x / Aspect, lensDir.y) * min(0.006, LensingPower * halo * 0.03);
            }
        }
        // =========================================================================
        // NHÁNH 4: CHIÊU 3 - KHỐI CẦU NĂNG LƯỢNG ĐỎ RỰC (FxType == 6.0: TYPE_GRAVITY_CORE)
        // Cầu Plasma Đỏ Ruby / Crimson Red rực sáng với các tia chớp điện đỏ bao quanh
        // Tuyệt đối không cam, không vàng, không hố đen, chuẩn màu đỏ rực theo yêu cầu!
        // =========================================================================
        else if (FxType > 5.5 && FxType < 6.5 && inFront && !coreOccluded && CoreRadius > 0.05) {
            float dSphere = impactParam;
            if (dSphere < CoreRadius * 1.55 && sceneDist > (tBH - CoreRadius)) {
                float coreGrad = smoothstep(CoreRadius * 1.35, 0.0, dSphere);
                float electric = 0.90 + 0.10 * sin(dSphere * 18.0 - GTime * 16.0 + hash12(texCoord * 120.0) * 2.0);

                // Màu Đỏ Rực (Pure Crimson Ruby Red): Tâm trắng hồng siêu nhiệt -> Vành đỏ thẫm ruby rực cháy
                vec3 plasmaCore = vec3(1.0, 0.40, 0.48) * exp(-dSphere / (CoreRadius * 0.35)) * 4.5;
                vec3 plasmaCorona = vec3(1.0, 0.02, 0.06) * exp(-dSphere / (CoreRadius * 0.78)) * 3.8;
                vec3 plasmaAura = vec3(0.88, 0.01, 0.04) * coreGrad * electric * 2.2;
                vec3 plasmaSparks = vec3(1.0, 0.15, 0.20) * electric * coreGrad * 1.6;

                addGlow += (plasmaCore + plasmaCorona + plasmaAura + plasmaSparks) * min(ImpactIntensity * 1.3, 1.8);
            }
        }
    }

    // =========================================================================
    // 1. SÓNG XUNG KÍCH PHÁO ĐIỆN TỪ QUỸ ĐẠO (Orbital Railgun Shockwave Rings)
    // Sóng năng lượng đa tầng: bung tỏa lúc bắn và co rút lúc thu về
    // =========================================================================
    float waveThickness = 0.042;
    float deltaDist = dist - ShockwaveRadius;
    float wave1 = exp(-sqr(deltaDist) / sqr(waveThickness));
    float wave2 = exp(-sqr(deltaDist - 0.048) / sqr(waveThickness * 0.85)) * 0.55;
    float wave3 = exp(-sqr(deltaDist + 0.048) / sqr(waveThickness * 0.85)) * 0.45;
    float railgunWave = wave1 + wave2 + wave3;
    float wave = railgunWave;

    vec2 dir = (dist > 0.0001) ? normalize(aspectDiff) : vec2(0.0);
    vec2 uvOffset = vec2(dir.x / Aspect, dir.y) * railgunWave * DistortionPower;
    vec2 baseUV = clamp(texCoord - lensOffset + uvOffset, 0.001, 0.999);

    // Vành đai Plasma Ion hóa phát quang kiểu Orbital Railgun (Cyan & Neon White)
    if (ShockwaveRadius > 0.02 && abs(DistortionPower) > 0.002) {
        vec3 railgunColor = mix(vec3(0.08, 0.92, 1.0), vec3(1.0, 1.0, 1.0), wave1 * 0.8);
        float ringIntensity = clamp(abs(DistortionPower) * 24.0, 0.0, 2.2);
        addGlow += railgunColor * (wave1 * 3.8 + wave2 * 1.8 + wave3 * 1.2) * ringIntensity;
    }

    // =========================================================================
    // 2. RADIAL ZOOM BLUR (Hút / Đẩy đa tầng hướng tâm)
    // =========================================================================
    if (EmissionOnly > 0.5) {
        fragColor = vec4(clamp((addGlow + novaVolume * 0.6 - 0.55) * 0.45, 0.0, 1.0), 1.0);
        return;
    }
    vec3 col = texture(DiffuseSampler, baseUV).rgb;
    if (FxType > 8.5 && FxType < 9.5) {
        fragColor = vec4(tonemap(mix(col * (1.0 - blackout * 0.86), riftColor, riftMask) + addGlow), 1.0);
        return;
    }

    if (abs(ZoomBlur) > 0.001) {
        vec3 acc = col;
        float total = 1.0;
        for (int i = 1; i < 8; i++) {
            float k = float(i) / 8.0;
            vec2 suv = center + (baseUV - center) * (1.0 - ZoomBlur * 0.14 * k);
            float w = 1.0 - k * 0.55;
            acc += texture(DiffuseSampler, clamp(suv, 0.001, 0.999)).rgb * w;
            total += w;
        }
        col = acc / total;
    }

    // =========================================================================
    // 3. SẮC SAI KHÔNG GIAN (Radial Chromatic Aberration - Chạy trên cảnh nền)
    // =========================================================================
    if (ChromaticAmount > 0.001) {
        vec2 caDir = (baseUV - vec2(0.5)) * ChromaticAmount * (1.0 + wave * 2.2);
        float r = texture(DiffuseSampler, clamp(baseUV + caDir, 0.001, 0.999)).r;
        float b = texture(DiffuseSampler, clamp(baseUV - caDir, 0.001, 0.999)).b;
        col.r = mix(col.r, r, 0.75);
        col.b = mix(col.b, b, 0.75);
    }

    // =========================================================================
    // ĐẶT HỐ ĐEN & TIA PULSAR Ở ĐÂY ĐỂ KHÔNG BỊ CHROMATIC ABERRATION GHI ĐÈ
    // =========================================================================
    col = mix(col * (1.0 - blackout * 0.86), riftColor, riftMask);
    if (insideHorizon) {
        float horizonEdge = smoothstep(CoreRadius * 0.88, CoreRadius, impactParamVal);
        col *= horizonEdge;
        addGlow *= horizonEdge;
        novaVolume *= horizonEdge;
    }
    col = col * (1.0 - novaOpacity) + novaVolume;
    col += addGlow;

    // =========================================================================
    // 4. JIMENEZ-STYLE DUAL-FILTER SOFT BLOOM (Hào quang không cháy trắng)
    // =========================================================================
    if (ImpactIntensity > 0.001 && !(FxType > 2.5 && FxType < 4.5)) {
        vec3 bloom = vec3(0.0);
        vec2 bStep = vec2(2.4 / OutSize.x, 2.4 / OutSize.y);
        for (int i = 0; i < 8; i++) {
            float ang = float(i) * 0.785398;
            vec2 bUV = clamp(baseUV + vec2(cos(ang), sin(ang)) * bStep, 0.001, 0.999);
            vec3 sCol = texture(DiffuseSampler, bUV).rgb;
            bloom += max(sCol - 0.72, vec3(0.0)) * 1.5;
        }
        bloom /= 8.0;
        // Soft screen blend formula: Tỏa sáng êm ái, bảo toàn chi tiết
        vec3 bFinal = bloom * (ImpactIntensity * 1.4);
        col = col + bFinal - col * bFinal * 0.35;
    }

    // =========================================================================
    // 5. GỢN SÓNG BỨC XẠ VẬT ĐEN (Blackbody Luminous Shockwave Edge)
    // =========================================================================
    vec3 waveGlow = blackbody(clamp(ImpactIntensity * 1.15, 0.0, 1.2)) * wave * abs(DistortionPower) * 16.0;
    col += waveGlow;

    // =========================================================================
    // 6. 36 ANIME IMPACT FRAMES & MANGA SCREENTONE (Công nghệ The Shooting Star)
    // =========================================================================
    if (ImpactFrame > 0.001) {
        float mode = ImpactMode;
        vec2 step1 = 1.5 / OutSize;

        // Trích xuất đường biên bằng bộ lọc Sobel (Inked Edges)
        float gx = lum(texture(DiffuseSampler, baseUV + vec2(step1.x, 0.0)).rgb) -
                   lum(texture(DiffuseSampler, baseUV - vec2(step1.x, 0.0)).rgb);
        float gy = lum(texture(DiffuseSampler, baseUV + vec2(0.0, step1.y)).rgb) -
                   lum(texture(DiffuseSampler, baseUV - vec2(0.0, step1.y)).rgb);
        float ink = smoothstep(0.05, 0.18, length(vec2(gx, gy)));

        // Ngưỡng thích ứng cục bộ (Adaptive Local Threshold)
        float l = lum(col);
        float avg = 0.0;
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.785398;
            avg += lum(texture(DiffuseSampler, clamp(baseUV + vec2(cos(a) / Aspect, sin(a)) * 0.042, 0.001, 0.999)).rgb);
        }
        avg /= 8.0;
        float two = step(avg + 0.004, l);
        float core = step(avg + 0.18, l);

        // Lưới điểm Manga Screentone xoay 45 độ
        vec2 tp = mat2(0.7071, -0.7071, 0.7071, 0.7071) * (texCoord * OutSize) / 5.2;
        float tone = saturate((l - avg) * 5.0 + 0.5);
        float dots = step(length(fract(tp) - 0.5), sqrt(saturate(1.0 - tone)) * 0.62);
        float mid = smoothstep(0.15, 0.35, tone) * (1.0 - smoothstep(0.65, 0.85, tone));

        vec2 d = (texCoord - center) * vec2(Aspect, 1.0);
        float ang = atan(d.y, d.x);

        vec3 frame = vec3(0.0);

        // Xử lý các chế độ 36 Anime Impact Frame
        if (mode > 32.5) {
            vec3 black = vec3(0.015, 0.012, 0.025);
            if (mode < 33.5) {
                // Mode 33: Prism Line-Art (Đường nét 7 màu cầu vồng tách ra từ tâm hit)
                vec2 out1 = normalize(d + 1e-5) / vec2(Aspect, 1.0);
                vec3 acc = vec3(0.0);
                for (int k = 0; k < 7; k++) {
                    vec2 o = baseUV + out1 * float(k - 3) * (0.0035 + 0.004 * length(d));
                    float ex = lum(texture(DiffuseSampler, o + vec2(step1.x, 0.0)).rgb) -
                               lum(texture(DiffuseSampler, o - vec2(step1.x, 0.0)).rgb);
                    float ey = lum(texture(DiffuseSampler, o + vec2(0.0, step1.y)).rgb) -
                               lum(texture(DiffuseSampler, o - vec2(0.0, step1.y)).rgb);
                    acc += prism7(float(k) / 7.0) * smoothstep(0.05, 0.16, length(vec2(ex, ey)));
                }
                frame = black + acc * 0.85;
                frame = mix(frame, vec3(1.0), core * 0.95);
            } else if (mode < 34.5) {
                // Mode 34: Spectral Posterized Bands (Vành đai phân tầng quang phổ 7 màu)
                float band = floor(saturate((l - avg) * 2.4 + 0.5) * 6.999);
                int ring = int(floor(length(d) * 9.0 - GTime * 6.0));
                frame = prismStop(int(mod(float(6 - int(band) + ring), 7.0)));
                frame = mix(frame, vec3(1.0), core);
                frame = mix(frame, black, ink * 0.9);
            } else {
                // Mode 35: Isometric 30-degree Wireframe (Lưới không gian Isometric)
                vec2 sp = texCoord * OutSize / 46.0;
                float gridD = 1.0;
                gridD = min(gridD, abs(fract(dot(sp, vec2(-0.5, 0.866))) - 0.5));
                gridD = min(gridD, abs(fract(dot(sp, vec2(0.5, 0.866))) - 0.5));
                gridD = min(gridD, abs(fract(sp.x * 1.1547) - 0.5));
                float gl = smoothstep(0.035, 0.0, gridD);
                vec3 hue = prism7(dot(texCoord, vec2(0.6, 0.4)) + GTime * 0.5);
                frame = black + hue * gl * 0.32;
                frame = mix(frame, hue * 1.15, ink);
                frame = mix(frame, vec3(1.0), core * 0.90);
            }
        } else if (mode < 0.5) {
            // Mode 0: Ink-Wash Threshold (Mực tàu cổ điển)
            frame = vec3(1.0 - two) * (1.0 - ink);
            frame = mix(frame, vec3(1.0 - dots), mid * 0.8);
        } else if (mode < 1.5) {
            // Mode 1: Inverted Ink
            frame = vec3(two) * (1.0 - ink);
            frame = mix(frame, vec3(dots), mid * 0.8);
        } else if (mode < 2.5) {
            // Mode 2: Crimson / Blood Red Invert (Huyết Sắc Jujutsu)
            frame = mix(vec3(0.04, 0.01, 0.01), vec3(1.0, 0.12, 0.14), two) * (1.0 - ink);
            frame = mix(frame, vec3(1.0, 0.12, 0.14) * dots, mid * 0.75);
            frame = mix(frame, vec3(1.0), core * 0.85);
        } else if (mode < 5.5) {
            // Mode 3: Six-Eyes Cyan / Mode 4: Shadow Blue / Mode 5: Cursed Magenta
            vec3 accent = (mode < 3.5) ? vec3(0.28, 0.92, 1.0) :
                          (mode < 4.5) ? vec3(0.20, 0.42, 1.0) :
                                         vec3(1.0, 0.22, 0.88);
            frame = mix(vec3(0.02, 0.02, 0.05), accent, two);
            frame = mix(frame, vec3(1.0), core);
            frame = mix(frame, accent * dots, mid * 0.75);
            frame *= (1.0 - ink * 0.85);
        } else {
            // Fallback: Tương phản cao đảo ngược
            frame = mix(vec3(0.03), vec3(0.35, 0.92, 1.0), two);
            frame = mix(frame, vec3(1.0), core);
        }

        col = mix(col, frame, ImpactFrame);
    }

    // =========================================================================
    // 7. LƯU ẢNH VÕNG MẠC & LÓA SÁNG (Retina Burn / Flash Blindness)
    // =========================================================================
    if (RetinaBurn > 0.001) {
        float ghost = smoothstep(0.32, 0.92, lum(col)) * saturate(RetinaBurn);
        col = 1.0 - (1.0 - col) * (1.0 - ghost * vec3(1.0, 0.96, 0.88));
        col += vec3(1.0, 0.98, 0.92) * saturate(RetinaBurn - 0.45) * 1.6;
    }

    // =========================================================================
    // 8. ARCANE VIGNETTE & TỔNG HỢP ÁNH SÁNG (Tonemapping)
    // =========================================================================
    if (ImpactIntensity > 0.001 && !(FxType > 2.5 && FxType < 4.5)) {
        float vig = smoothstep(0.32, 1.20, length(texCoord - vec2(0.5)) * 1.35);
        col *= (1.0 - vig * 0.48 * ImpactIntensity);
        col += (FxType > 3.5 && FxType < 4.5 ? VOID_VIOLET * 0.08 : vec3(0.02, 0.12, 0.25)) * vig * ImpactIntensity;
    }

    col = tonemap(col);
    fragColor = vec4(col, 1.0);
}
