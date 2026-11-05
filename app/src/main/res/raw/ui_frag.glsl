#version 300 es
// One texture: the glyph atlas. Solid quads sample a reserved white cell, so
// outlines, bars and text all share a single draw call.
precision highp float;

in vec2 vUv;
in vec4 vColor;

uniform sampler2D uAtlas;
uniform float uAlpha;

out vec4 fragColor;

void main() {
    float a = texture(uAtlas, vUv).a * vColor.a * uAlpha;
    fragColor = vec4(vColor.rgb, a);
}
