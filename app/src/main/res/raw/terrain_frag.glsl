#version 300 es
// Height-banded terrain colour plus an anti-aliased world grid.
//
// The grid is derived from world coordinates with screen-space derivatives
// (fwidth), so it stays one pixel wide at every distance and every zoom instead
// of shimmering. No texture, no mipmaps, no tiling artefacts.
precision highp float;

in vec3 vWorld;
in vec3 vNormal;
in float vShade;
in float vFog;
in float vHeight;

uniform vec3 uCamPos;
uniform vec3 uSunDir;
uniform vec3 uFogColor;
uniform float uGridScale;
uniform vec3 uAccent;

out vec4 fragColor;

void main() {
    vec3 n = normalize(vNormal);

    // Three bands: damp lowland, mossy mid, cold rock high.
    vec3 low  = vec3(0.055, 0.085, 0.115);
    vec3 mid  = vec3(0.075, 0.135, 0.130);
    vec3 high = vec3(0.130, 0.150, 0.190);
    float h = clamp((vHeight + 3.0) / 24.0, 0.0, 1.0);
    vec3 base = mix(low, mid, smoothstep(0.0, 0.55, h));
    base = mix(base, high, smoothstep(0.55, 1.0, h));

    float ndl = max(dot(n, uSunDir), 0.0);
    vec3 col = base * (0.55 + ndl * 0.75);

    // Grid: derivative-based, so it never aliases.
    vec2 coord = vWorld.xz * uGridScale;
    vec2 grid = abs(fract(coord - 0.5) - 0.5) / max(fwidth(coord), vec2(1e-5));
    float line = 1.0 - min(min(grid.x, grid.y), 1.0);
    col += uAccent * line * 0.16;

    // A faint scan ring that travels outward from the origin, purely decorative.
    float ring = 1.0 - min(abs(fract(length(vWorld.xz) * 0.012 - 0.15) - 0.5) * 8.0, 1.0);
    col += uAccent * ring * 0.03;

    col *= 0.80 + 0.20 * vShade;
    col = mix(col, uFogColor, clamp(vFog, 0.0, 1.0));
    fragColor = vec4(col, 1.0);
}
