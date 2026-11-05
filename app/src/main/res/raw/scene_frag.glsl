#version 300 es
// Cheap but good-looking lighting for low-poly geometry:
//   - one directional sun with a hard wrap terminator
//   - hemispheric ambient (sky above, bounce below) instead of a flat constant
//   - a rim term, which is what makes faceted shapes read at small sizes
//   - baked per-vertex shade (a crude AO) multiplied in
//   - exponential-free linear fog matched to the sky colour
precision highp float;

in vec3 vWorld;
in vec3 vNormal;
in vec3 vColor;
in float vEmissive;
in float vShade;
in float vFog;

uniform vec3 uCamPos;
uniform vec3 uSunDir;
uniform vec3 uFogColor;

out vec4 fragColor;

void main() {
    vec3 n = normalize(vNormal);
    vec3 viewDir = normalize(uCamPos - vWorld);

    float ndl = max(dot(n, uSunDir), 0.0);
    float hemi = n.y * 0.5 + 0.5;

    vec3 ambient = mix(vec3(0.09, 0.10, 0.18), vec3(0.36, 0.44, 0.58), hemi);
    vec3 lit = vColor * (ambient + ndl * vec3(1.00, 0.93, 0.82) * 0.95);
    lit *= 0.72 + 0.28 * vShade;

    float rim = pow(1.0 - max(dot(n, viewDir), 0.0), 3.0);
    lit += rim * vec3(0.30, 0.58, 0.95) * (0.30 + vEmissive);

    // Emissive instances stay bright in shadow — that is what makes a field of
    // ten thousand crystals look alive instead of like grey rocks.
    lit += vColor * vEmissive * 1.7;

    vec3 col = mix(lit, uFogColor, clamp(vFog, 0.0, 1.0));
    fragColor = vec4(col, 1.0);
}
