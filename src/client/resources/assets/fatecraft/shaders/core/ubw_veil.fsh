#version 330

#moj_import <fatecraft:ubw_clouds.glsl>

in vec3 skyDirection;
in float reveal;
out vec4 fragColor;

// The cloud layer the gears hang in: the sky's own colour, shown where a wispy layer of cloud (streaked flat, like the
// banks) is thick, so parts of a gear sink into the clouds round it.
void main() {
    vec3 d = normalize(skyDirection);
    float wisps = ubwClouds(d * vec3(5.0, 14.0, 5.0) + vec3(8.1, 2.6, 5.3));
    fragColor = vec4(ubwSky(d), smoothstep(0.48, 0.66, wisps) * reveal);
}
