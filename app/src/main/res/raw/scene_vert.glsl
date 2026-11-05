#version 300 es
// Instanced scene geometry.
//
// One vertex buffer, one instance buffer, one draw call per mesh archetype.
// The per-instance transform is a quaternion + scale + offset rather than a
// mat4: 12.5% less bandwidth, and the rotation is computed on the GPU where
// there are cycles to spare.
//
// The extra iAnim vector moves idle animation onto the GPU. A field of twelve
// thousand crystals that bob and spin costs the CPU exactly nothing per frame:
// the transform buffer is uploaded once at load and the vertex shader does the
// motion. That is the difference between "can draw ten thousand" and "can draw
// ten thousand at 60 fps while also running the game".
precision highp float;

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in float aShade;

layout(location = 3) in vec3 iOffset;
layout(location = 4) in vec4 iQuat;
layout(location = 5) in vec3 iScale;
layout(location = 6) in vec4 iColor;   // rgb = tint, a = emissive
layout(location = 7) in vec4 iAnim;    // x phase, y bob speed, z bob amp, w spin

uniform mat4 uViewProj;
uniform vec3 uCamPos;
uniform vec3 uFogColor;
uniform float uFogStart;
uniform float uFogEnd;
uniform float uTime;

out vec3 vWorld;
out vec3 vNormal;
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

    // Spin in the mesh's own space, before the instance quaternion.
    float ang = uTime * iAnim.w;
    float cs = cos(ang);
    float sn = sin(ang);
    lp = vec3(lp.x * cs - lp.z * sn, lp.y, lp.x * sn + lp.z * cs);

    // Vertical bob. Applied after rotation so it is always world-up.
    float bob = sin(uTime * iAnim.y + iAnim.x) * iAnim.z;

    vec3 world = iOffset + qrot(iQuat, lp) + vec3(0.0, bob, 0.0);
    // Inverse-scale the normal so non-uniform instances still light correctly.
    vec3 nrm = qrot(iQuat, aNormal / max(iScale, vec3(0.001)));

    vWorld = world;
    vNormal = normalize(nrm);
    vColor = iColor.rgb;
    vEmissive = iColor.a;
    vShade = aShade;

    float dist = length(world - uCamPos);
    vFog = clamp((dist - uFogStart) / max(1.0, uFogEnd - uFogStart), 0.0, 1.0);

    gl_Position = uViewProj * vec4(world, 1.0);
}
