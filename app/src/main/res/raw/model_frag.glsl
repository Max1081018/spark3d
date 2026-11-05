#version 300 es
// Textured lit model. Base colour comes from the texture sampled at the
// baked UVs, multiplied by the per-instance tint. Emissive and baked shade
// behave exactly like the procedural scene shader, so a flock of real models
// sits correctly in the same field.
precision highp float;

in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in vec3 vColor;
in float vEmissive;
in float vShade;
in float vFog;

uniform sampler2D uTex;
uniform vec3 uCamPos;
uniform vec3 uSunDir;
uniform vec3 uFogColor;

out vec4 fragColor;

void main() {
    vec4 tex = texture(uTex, vUv);
    vec3 base = vColor * tex.rgb;

    vec3 n = normalize(vNormal);
    vec3 viewDir = normalize(uCamPos - vWorld);

    float ndl = max(dot(n, uSunDir), 0.0);
    float hemi = n.y * 0.5 + 0.5;

    vec3 ambient = mix(vec3(0.09, 0.10, 0.18), vec3(0.36, 0.44, 0.58), hemi);
    vec3 lit = base * (ambient + ndl * vec3(1.00, 0.93, 0.82) * 0.95);
    lit *= 0.72 + 0.28 * vShade;

    float rim = pow(1.0 - max(dot(n, viewDir), 0.0), 3.0);
    lit += rim * vec3(0.30, 0.58, 0.95) * (0.30 + vEmissive);

    lit += base * vEmissive * 1.7;

    vec3 col = mix(lit, uFogColor, clamp(vFog, 0.0, 1.0));
    fragColor = vec4(col, tex.a);
}
