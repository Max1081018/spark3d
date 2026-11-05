#version 300 es
// Skinned model, textured where the source mesh had UVs. Lighting is the same
// hemispheric + directional + rim rig as the procedural scene shader, so
// characters read correctly against the same sky and fog.
precision highp float;

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec3 vColor;
in float vEmissive;
in float vFog;

uniform sampler2D uTex;
uniform vec4 uBaseColor;
uniform vec3 uCamPos;
uniform vec3 uSunDir;
uniform vec3 uFogColor;

out vec4 fragColor;

void main() {
    vec4 tex = texture(uTex, vUv);
    vec3 base = uBaseColor.rgb * (tex.a > 0.0 ? tex.rgb : vec3(1.0));

    vec3 n = normalize(vNormal);
    vec3 viewDir = normalize(uCamPos - vWorld);

    float ndl = max(dot(n, uSunDir), 0.0);
    float hemi = n.y * 0.5 + 0.5;

    vec3 ambient = mix(vec3(0.09, 0.10, 0.18), vec3(0.36, 0.44, 0.58), hemi);
    vec3 lit = base * (ambient + ndl * vec3(1.00, 0.93, 0.82) * 0.95);

    float rim = pow(1.0 - max(dot(n, viewDir), 0.0), 3.0);
    lit += rim * vec3(0.30, 0.58, 0.95) * (0.30 + vEmissive);

    lit += base * vEmissive * 1.7;

    vec3 col = mix(lit, uFogColor, clamp(vFog, 0.0, 1.0));
    fragColor = vec4(col, tex.a);
}
