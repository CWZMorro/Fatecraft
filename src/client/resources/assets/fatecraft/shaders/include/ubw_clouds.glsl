#version 330

// The marble's clouded sky (UbwSky), shared with the cloud veil in front of the gears (UbwGears) so both show one sky.
// The clouds are 3D value noise, so the sky has no texture seam.
float ubwHash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

float ubwNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(ubwHash(i), ubwHash(i + vec3(1, 0, 0)), f.x),
                   mix(ubwHash(i + vec3(0, 1, 0)), ubwHash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(ubwHash(i + vec3(0, 0, 1)), ubwHash(i + vec3(1, 0, 1)), f.x),
                   mix(ubwHash(i + vec3(0, 1, 1)), ubwHash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float ubwClouds(vec3 p) {
    float total = 0.0;
    float amplitude = 0.55;
    for (int octave = 0; octave < 5; octave++) {
        total += ubwNoise(p) * amplitude;
        p = p * 2.03 + vec3(11.7, 5.1, 9.2);
        amplitude *= 0.49;
    }
    return total;
}

// The sky's colour toward the unit direction d from the sphere's centre.
vec3 ubwSky(vec3 d) {
    float height = smoothstep(0.0, 0.85, d.y);
    vec3 ochreHaze = vec3(0.70, 0.49, 0.30);
    vec3 bronze = vec3(0.42, 0.25, 0.18);
    vec3 base = mix(ochreHaze, bronze, height);
    // Broad dark cloud banks with small scalloped lit rims.
    float n = ubwClouds(d * vec3(4.1, 9.5, 4.1) + vec3(3.4, 7.2, 1.8));
    float bank = smoothstep(0.43, 0.67, n);
    float horizon = smoothstep(-0.05, 0.24, d.y);
    vec3 chocolate = vec3(0.16, 0.085, 0.075);
    vec3 color = mix(base, chocolate, bank * horizon * 0.91);
    float rim = exp(-abs(n - 0.45) * 65.0) * horizon;
    return color + vec3(0.18, 0.10, 0.035) * rim;
}
