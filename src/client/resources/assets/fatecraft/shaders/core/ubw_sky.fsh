#version 330

#moj_import <fatecraft:ubw_clouds.glsl>

in vec3 skyDirection;
in float reveal;
out vec4 fragColor;

void main() {
    fragColor = vec4(ubwSky(normalize(skyDirection)), reveal);
}
