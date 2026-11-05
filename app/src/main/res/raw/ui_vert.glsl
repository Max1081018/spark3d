#version 300 es
// 2D batch for the HUD: text glyphs and solid quads in one call.
// Positions arrive in pixels, top-left origin, and are converted here so the
// Java side never has to know about NDC.
precision highp float;

layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aUv;
layout(location = 2) in vec4 aColor;

uniform vec2 uResolution;

out vec2 vUv;
out vec4 vColor;

void main() {
    vUv = aUv;
    vColor = aColor;
    vec2 ndc = vec2((aPos.x / uResolution.x) * 2.0 - 1.0,
                    1.0 - (aPos.y / uResolution.y) * 2.0);
    gl_Position = vec4(ndc, 0.0, 1.0);
}
