#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform GodWandConfig {
    vec2 BlurAxis;
    float CenterX;
    float CenterY;
    float BloomBurst;
};

uniform sampler2D DiffuseSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() {
    vec2 stepUV = BlurAxis * 2.4 / max(InSize, vec2(1.0));
    vec3 color = texture(DiffuseSampler, texCoord).rgb * 0.227027;
    for (int i = 1; i <= 4; i++) {
        float weight = i == 1 ? 0.194595 : i == 2 ? 0.121622 : i == 3 ? 0.054054 : 0.016216;
        vec2 offset = stepUV * float(i);
        color += (texture(DiffuseSampler, clamp(texCoord + offset, 0.001, 0.999)).rgb
                + texture(DiffuseSampler, clamp(texCoord - offset, 0.001, 0.999)).rgb) * weight;
    }
    if (BlurAxis.y > 0.5 && BloomBurst > 0.001) {
        vec2 delta = (vec2(CenterX, CenterY) - texCoord) * 0.04;
        vec2 uv = texCoord;
        vec3 shafts = vec3(0.0);
        float weight = 1.0;
        for (int i = 0; i < 8; i++) {
            uv += delta;
            shafts += texture(DiffuseSampler, clamp(uv, 0.001, 0.999)).rgb * weight;
            weight *= 0.86;
        }
        color += shafts * BloomBurst * 0.10;
    }
    fragColor = vec4(color, 1.0);
}
