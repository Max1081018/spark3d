#version 300 es
// Full-screen triangle generated from gl_VertexID: no VBO, no attributes,
// three vertices per frame. Cheaper than clearing with a shader-bound quad.
precision highp float;

out vec2 vUv;

void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    vUv = p;
    gl_Position = vec4(p * 2.0 - 1.0, 1.0, 1.0);
}
