#version 300 es
// Instanced textured model: baked glTF static meshes (duck flocks, rocks
// carved from real models) drawn with one instance buffer and one draw call.
precision highp float;

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
layout(location = 3) in float aShade;

layout(location = 4) in vec3 iOffset;
layout(location = 5) in vec4 iQuat;
layout(location = 6) in vec3 iScale;
layout(location = 7) in vec4 iColor;   // rgb = tint, a = emissive
layout(location = 8) in vec4 iAnim;    // x phase, y bob speed, z bob amp, w spin

uniform mat4 uViewProj;
uniform vec3 uCamPos;
uniform sampler2D uTex;
uniform vec3 uFogColor;
uniform float uFogStart;
uniform float uFogEnd;
uniform float uTime;

out vec3 vWorld;
out vec3 vNormal;
out vec2 vUv;
out vec3 vColor;
out float vEmissive;
out float vShade;
out float vFog;

vec3 qrot(vec4 q, vec3 v) {
    vec3 t = 2.0 * cross(q.xyz, v);
    return v + q.w * t + cross(q.xyz, t);
}

void main() {
    vec3 lp = aPos * iScale;

    float ang = uTime * iAnim.w;
    float cs = cos(ang);
    float sn = sin(ang);
    lp = vec3(lp.x * cs - lp.z * sn, lp.y, lp.x * sn + lp.z * cs);

    float bob = sin(uTime * iAnim.y + iAnim.x) * iAnim.z;

    vec3 world = iOffset + qrot(iQuat, lp) + vec3(0.0, bob, 0.0);
    vec3 nrm = qrot(iQuat, aNormal / max(iScale, vec3(0.001)));

    vWorld = world;
    vNormal = normalize(nrm);
    vUv = aUv;
    vColor = iColor.rgb;
    vEmissive = iColor.a;
    vShade = aShade;

    float dist = length(world - uCamPos);
    vFog = clamp((dist - uFogStart) / max(1.0, uFogEnd - uFogStart), 0.0, 1.0);

    gl_Position = uViewProj * vec4(world, 1.0);
}
