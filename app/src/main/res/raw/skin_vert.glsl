#version 300 es
// Skinned model. One palette of joint world matrices per character, uploaded
// every frame; skinning happens in the vertex shader. Palette slot j holds
// J_global(j, t) * inverse(J_global(j, bind)), which is the per-joint matrix
// that turns a bind-pose joint offset into animated world space.
precision highp float;

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec4 aJoints;
layout(location = 3) in vec4 aWeights;
layout(location = 4) in vec2 aUv;

uniform mat4 uJoints[64];
uniform int uJointCount;
uniform mat4 uModel;
uniform mat4 uViewProj;
uniform vec3 uCamPos;
uniform vec3 uSunDir;
uniform vec3 uFogColor;
uniform float uFogStart;
uniform float uFogEnd;
uniform float uTime;
uniform vec4 uBaseColor;
uniform sampler2D uTex;

out vec3 vWorld;
out vec3 vNormal;
out vec2 vUv;
out vec3 vColor;
out float vEmissive;
out float vFog;

vec3 qrot(vec4 q, vec3 v) {
    vec3 t = 2.0 * cross(q.xyz, v);
    return v + q.w * t + cross(q.xyz, t);
}

void main() {
    vec4 skinned = vec4(0.0);
    vec4 skinnedNormal = vec4(0.0);
    for (int j = 0; j < 4; j++) {
        float w = aWeights[j];
        if (w > 0.0) {
            int joint = int(aJoints[j]);
            vec4 t = uJoints[joint] * vec4(aPos, 1.0);
            vec4 tn = uJoints[joint] * vec4(aNormal, 0.0);
            skinned = skinned + w * t;
            skinnedNormal = skinnedNormal + w * tn;
        }
    }
    vec3 world = (uModel * skinned).xyz;
    vec3 nrm = normalize(skinnedNormal.xyz);

    vec3 worldPos = world;
    vec3 n = nrm;

    vWorld = worldPos;
    vNormal = n;
    vUv = aUv;
    vColor = uBaseColor.rgb;
    vEmissive = 0.0;

    float dist = length(worldPos - uCamPos);
    vFog = clamp((dist - uFogStart) / max(1.0, uFogEnd - uFogStart), 0.0, 1.0);

    gl_Position = uViewProj * vec4(worldPos, 1.0);
}
