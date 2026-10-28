#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform GodWandConfig {
    float BloomStrength;
};

uniform sampler2D DiffuseSampler;
uniform sampler2D BloomSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; vec2 AuxiliarySize; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec3 scene = texture(DiffuseSampler, texCoord).rgb;
    vec3 bloom = texture(BloomSampler, texCoord).rgb;
    vec3 color = 1.0 - (1.0 - scene) * exp(-bloom * BloomStrength);
    fragColor = vec4(color, 1.0);
}
