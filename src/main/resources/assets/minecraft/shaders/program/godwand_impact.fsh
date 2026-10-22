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

void main() {
    vec2 center = vec2(CenterX, CenterY);
    vec2 diff = texCoord - center;
    vec2 aspectDiff = vec2(diff.x * Aspect, diff.y);
    float dist = length(aspectDiff);

    // =========================================================================
    // 0. 3D RAYMARCHING, DEPTH RECONSTRUCTION & GRAVITATIONAL LENSING
    // =========================================================================
    vec2 lensOffset = vec2(0.0);
    vec3 addGlow = vec3(0.0);
    bool insideHorizon = false;
    float impactParamVal = 999.0;

    if (CoreRadius > 0.005) {
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
        float sceneDist = (rawDepth >= 0.99999) ? 1e6 : length(worldHitRel.xyz);

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
        // =========================================================================
        if (FxType > 3.5 && inFront) {
            // 1. Lensing, Event Horizon & Einstein Ring chỉ hiện khi tâm Hố Đen không bị che
            if (!coreOccluded) {
                vec2 lensDir = (dist > 0.0001) ? normalize(aspectDiff) : vec2(0.0);
                float lensShift = (LensingPower * sqr(CoreRadius)) / (sqr(impactParam) + 0.06);
                lensShift = clamp(lensShift, 0.0, 0.35);
                lensOffset = vec2(lensDir.x / Aspect, lensDir.y) * lensShift;

                if (impactParam < CoreRadius && sceneDist > (tBH - CoreRadius * 0.8)) {
                    insideHorizon = true;
                }

                float ringDist = abs(impactParam - CoreRadius * 1.35);
                float einsteinRing = exp(-sqr(ringDist) / 0.035) * LensingPower;
                vec3 ringCol = (vec3(0.85, 0.95, 1.0) * 2.6 + blackbody(1.15) * 1.4) * einsteinRing;
                addGlow += ringCol;
            }

            // 2. Relativistic Accretion Disk (Đĩa bồi tụ nghiêng 18 độ, xoay chao sống động quanh tâm)
            vec3 diskNormal = normalize(vec3(sin(GTime * 0.35) * 0.26, 0.96, cos(GTime * 0.35) * 0.26));
            float denomDisk = dot(rd, diskNormal);
            if (abs(denomDisk) > 0.0005) {
                float tDisk = dot(bhRel, diskNormal) / denomDisk;
                if (tDisk > 0.0 && tDisk < sceneDist && (!insideHorizon || tDisk < tBH)) {
                    vec3 relDisk = (rd * tDisk) - bhRel;
                    float rDisk = length(relDisk);
                    if (rDisk > CoreRadius * 1.05 && rDisk < CoreRadius * 4.5) {
                        float diskAngle = atan(relDisk.z, relDisk.x) + GTime * 3.5;
                        float swirl = 0.75 + 0.25 * sin(diskAngle * 5.0 - rDisk * 4.0);
                        vec3 orbVel = normalize(cross(diskNormal, relDisk));
                        float doppler = 1.0 + 0.45 * dot(rd, orbVel);
                        float diskHeat = saturate(1.2 - (rDisk - CoreRadius) / (CoreRadius * 3.45));
                        float diskAlpha = smoothstep(CoreRadius * 1.05, CoreRadius * 1.3, rDisk) *
                                          (1.0 - smoothstep(CoreRadius * 3.2, CoreRadius * 4.5, rDisk));
                        addGlow += blackbody(diskHeat) * swirl * doppler * 3.5 * diskAlpha;
                    }
                }
            }

            // 3. Volumetric Relativistic Pulsar Jets (Từ không có -> mỏng -> to dần theo CoreRadius)
            float jetScale = clamp((CoreRadius - 0.85) / (5.2 - 0.85), 0.0, 1.0);
            if (jetScale > 0.005) {
                vec2 jetXZ = bhRel.xz;
                float denom = max(dot(rd.xz, rd.xz), 1e-6);
                float tJet = dot(jetXZ, rd.xz) / denom;
                if (tJet > 0.0 && tJet < sceneDist) {
                    vec3 pJet = rd * tJet;
                    float dJet = length(pJet.xz - bhRel.xz);
                    float hJet = pJet.y - bhRel.y;
                    float maxDown = -22.0 * jetScale;
                    float maxUp = 48.0 * jetScale;
                    if (hJet > maxDown && hJet < maxUp) {
                        // Độ rộng tia tỷ lệ thuận với jetScale (từ tia chỉ siêu mỏng -> cột sáng tối đa)
                        float wFactor = max(0.04, jetScale);
                        float jetCore = exp(-dJet / (0.12 * wFactor));
                        float jetSheath = exp(-dJet / (0.75 * wFactor)) * 0.45 * jetScale;
                        float jetAura = exp(-dJet / (2.8 * wFactor)) * 0.15 * jetScale;
                        float jetFade = smoothstep(maxDown, maxDown * 0.8, hJet) * (1.0 - smoothstep(maxUp * 0.7, maxUp, hJet));
                        vec3 jetColor = (vec3(1.0, 0.98, 0.95) * jetCore * 4.0 +
                                         vec3(0.05, 0.88, 1.0) * jetSheath * 2.2 +
                                         vec3(0.55, 0.15, 1.0) * jetAura * 1.2) * jetFade * jetScale;
                        addGlow += jetColor * (0.85 + 0.15 * sin(GTime * 25.0));
                    }
                }
            }
        }
        // =========================================================================
        // NHÁNH 2: CHIÊU 4 - ĐẠI VỤ NỔ SIÊU TÂN TINH (FxType == 3.0: TYPE_SUPERNOVA)
        // Multi-Shell Volumetric Blast Wave 3D bẻ cong không gian và bức xạ nhiệt
        // =========================================================================
        else if (FxType > 2.5 && FxType < 3.5 && inFront) {
            if (CoreRadius > 0.5) {
                float dSphere = impactParam;
                float outerLimit = CoreRadius * 1.35;
                if (dSphere < outerLimit && sceneDist > (tBH - outerLimit)) {
                    // Tính biến dạng méo 3D để không bao giờ có viền tròn phẳng kiểu PNG
                    float angle3D = atan(pClosest.y - bhRel.y, pClosest.x - bhRel.x);
                    float turb = sin(angle3D * 5.0 + GTime * 12.0) * 0.12 + cos(angle3D * 9.0 - GTime * 18.0) * 0.08;
                    float noisyRadius = CoreRadius * (1.0 + turb);
                    float normD = dSphere / max(0.1, noisyRadius);

                    // Bẻ cong khúc xạ không gian 3D quanh vỏ cầu nổ
                    float shellRing = exp(-sqr(normD - 0.85) / 0.045);
                    vec2 blastDir = (dist > 0.0001) ? normalize(aspectDiff) : vec2(0.0);
                    lensOffset += vec2(blastDir.x / Aspect, blastDir.y) * shellRing * 0.12 * ImpactIntensity;

                    // Chỉ tỏa sáng lõi năng lượng rực rỡ trong 1.2 giây đầu (khi CoreRadius < 22m), sau đó nhường chỗ cho khói bụi 3D trong thế giới
                    float flashFade = clamp(1.0 - (CoreRadius - 5.2) / 28.0, 0.0, 1.0);
                    float coreGlow = exp(-normD * 2.2) * flashFade;
                    float shockRim = shellRing * clamp(1.0 - (CoreRadius - 5.2) / 34.0, 0.0, 1.0);

                    vec3 fireCol = blackbody(clamp(coreGlow * 1.4 + shockRim * 0.9, 0.0, 1.2));
                    float depthMask = smoothstep(tBH - outerLimit, tBH + outerLimit * 0.25, sceneDist);
                    addGlow += fireCol * (coreGlow * 5.5 + shockRim * 3.2) * ImpactIntensity * depthMask;
                }
            }
        }
        // =========================================================================
        // NHÁNH 3: CHIÊU 2 - CẦU NĂNG LƯỢNG MA PHÁP (FxType == 0.0: TYPE_DETONATION)
        // Không lỗ đen, không đĩa bồi tụ, không cột Pulsar
        // =========================================================================
        else if (FxType < 0.5 && inFront && !coreOccluded && CoreRadius > 0.05) {
            float dSphere = impactParam;
            if (dSphere < CoreRadius * 1.65 && sceneDist > (tBH - CoreRadius)) {
                vec2 lensDir = (dist > 0.0001) ? normalize(aspectDiff) : vec2(0.0);
                float lensShift = (LensingPower * sqr(CoreRadius)) / (sqr(dSphere) + 0.10);
                lensOffset = vec2(lensDir.x / Aspect, lensDir.y) * clamp(lensShift, 0.0, 0.16);

                float coreGrad = smoothstep(CoreRadius * 1.4, 0.0, dSphere);
                float electric = 0.85 + 0.15 * sin(dSphere * 24.0 - GTime * 28.0 + hash12(texCoord * 120.0) * 3.5);

                vec3 plasmaCore = vec3(0.95, 0.98, 1.0) * exp(-dSphere / (CoreRadius * 0.40)) * 4.5;
                vec3 plasmaCorona = vec3(0.08, 0.85, 1.0) * exp(-dSphere / (CoreRadius * 0.90)) * 3.0;
                vec3 plasmaAura = vec3(0.55, 0.15, 1.0) * coreGrad * electric * 1.6;

                addGlow += (plasmaCore + plasmaCorona + plasmaAura) * min(ImpactIntensity * 1.5, 1.8);
            }
        }
        // =========================================================================
        // NHÁNH 4: CHIÊU 3 - QUẢ CẦU TRỌNG LỰC HÓA ĐỎ MŨI KHOAN (FxType == 6.0: TYPE_GRAVITY_CORE)
        // Đổi màu mượt từ Vàng Hổ Phách (Amber Gravity) -> Đỏ Thẫm Nhiệt Hạch (Crimson Drill Overheat)
        // =========================================================================
        else if (FxType > 5.5 && inFront && !coreOccluded && CoreRadius > 0.05) {
            float dSphere = impactParam;
            if (dSphere < CoreRadius * 1.75 && sceneDist > (tBH - CoreRadius)) {
                vec2 lensDir = (dist > 0.0001) ? normalize(aspectDiff) : vec2(0.0);
                float lensShift = (LensingPower * sqr(CoreRadius)) / (sqr(dSphere) + 0.10);
                lensOffset = vec2(lensDir.x / Aspect, lensDir.y) * clamp(lensShift, 0.0, 0.18);

                // redMix = 0.0 (Vàng Hổ Phách ở Pha 1-2) -> 1.0 (Đỏ Thẫm Nhiệt Hạch ở Pha 3 Mũi Khoan)
                float redMix = saturate((ImpactIntensity - 1.0) / 1.2);
                float coreGrad = smoothstep(CoreRadius * 1.5, 0.0, dSphere);
                float spiralWave = 0.8 + 0.2 * sin(dSphere * 20.0 - GTime * (25.0 + redMix * 35.0));

                vec3 coreColor = mix(vec3(1.0, 0.95, 0.75), vec3(1.0, 0.85, 0.85), redMix);
                vec3 coronaColor = mix(vec3(1.0, 0.65, 0.08), vec3(1.0, 0.08, 0.02), redMix);
                vec3 auraColor = mix(vec3(0.85, 0.35, 0.02), vec3(0.75, 0.0, 0.08), redMix);

                vec3 gravCore = coreColor * exp(-dSphere / (CoreRadius * 0.42)) * 4.5;
                vec3 gravCorona = coronaColor * exp(-dSphere / (CoreRadius * 0.92)) * 3.2;
                vec3 gravAura = auraColor * coreGrad * spiralWave * 1.8;

                addGlow += (gravCore + gravCorona + gravAura) * 1.4;
            }
        }
    }

    // =========================================================================
    // 1. SÓNG XUNG KÍCH BẺ CONG KHÚC XẠ (Shockwave Refraction Ring)
    // =========================================================================
    float waveThickness = 0.068;
    float deltaDist = dist - ShockwaveRadius;
    float wave = exp(-sqr(deltaDist) / sqr(waveThickness));
    vec2 dir = (dist > 0.0001) ? normalize(aspectDiff) : vec2(0.0);
    vec2 uvOffset = vec2(dir.x / Aspect, dir.y) * wave * DistortionPower;
    vec2 baseUV = clamp(texCoord - lensOffset + uvOffset, 0.001, 0.999);

    // =========================================================================
    // 2. RADIAL ZOOM BLUR (Hút / Đẩy đa tầng hướng tâm)
    // =========================================================================
    vec3 col = texture(DiffuseSampler, baseUV).rgb;
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
    if (insideHorizon) {
        float horizonEdge = smoothstep(CoreRadius * 0.88, CoreRadius, impactParamVal);
        col *= horizonEdge;
    }
    col += addGlow;

    // =========================================================================
    // 4. JIMENEZ-STYLE DUAL-FILTER SOFT BLOOM (Hào quang không cháy trắng)
    // =========================================================================
    if (ImpactIntensity > 0.001) {
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
    if (ImpactIntensity > 0.001) {
        float vig = smoothstep(0.32, 1.20, length(texCoord - vec2(0.5)) * 1.35);
        col *= (1.0 - vig * 0.48 * ImpactIntensity);
        col += vec3(0.02, 0.12, 0.25) * vig * ImpactIntensity;
    }

    col = tonemap(col);
    fragColor = vec4(col, 1.0);
}
