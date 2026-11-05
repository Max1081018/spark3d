#version 300 es
// Terrain: a single static mesh, drawn in one call.
precision highp float;

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in float aShade;

uniform mat4 uViewProj;
uniform vec3 uCamPos;
uniform vec3 uFogColor;
uniform float uFogStart;
uniform float uFogEnd;

out vec3 vWorld;
out vec3 vNormal;
out float vShade;
out float vFog;
out float vHeight;

void main() {
    vWorld = aPos;
    vNormal = aNormal;
    vShade = aShade;
    vHeight = aPos.y;
    float dist = length(aPos - uCamPos);
    vFog = clamp((dist - uFogStart) / max(1.0, uFogEnd - uFogStart), 0.0, 1.0);
    gl_Position = uViewProj * vec4(aPos, 1.0);
}
