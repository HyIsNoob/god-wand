#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D DiffuseSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
void main() { fragColor = texture(DiffuseSampler, texCoord); }
