#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
out vec3 skyDirection;
out float reveal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // The direction is carried in the colour, so the sky stays fixed to the geometry as the camera moves.
    skyDirection = Color.rgb * 2.0 - 1.0;
    // How much the opening's front has revealed here (UbwReveal).
    reveal = Color.a;
}
