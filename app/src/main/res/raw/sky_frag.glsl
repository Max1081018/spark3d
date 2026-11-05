#version 300 es
// Horizon gradient + a sun disk, all procedural. Matches the fog colour so the
// terrain dissolves into the sky at the far plane instead of ending on a line.
precision highp float;

in vec2 vUv;

uniform vec3 uSkyTop;
uniform vec3 uSkyBottom;
uniform vec3 uSunColor;
uniform vec2 uSunScreen;    // NDC-ish direction projected to uv space
uniform float uSunSize;

out vec4 fragColor;

void main() {
    vec3 col = mix(uSkyBottom, uSkyTop, pow(clamp(vUv.y, 0.0, 1.0), 0.85));

    float d = length((vUv - uSunScreen) * vec2(1.0, 1.6));
    float disk = smoothstep(uSunSize, uSunSize * 0.35, d);
    float halo = pow(max(0.0, 1.0 - d * 6.0), 3.0);
    col += uSunColor * (disk * 0.9 + halo * 0.22);

    fragColor = vec4(col, 1.0);
}
