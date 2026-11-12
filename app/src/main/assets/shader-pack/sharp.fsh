#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D sampler0;
uniform vec2 u_texelDelta;
uniform vec4 u_setting;
varying vec2 v_texcoord0;
void main() {
    // "sharp bilinear": keep pixel interiors flat, blend only a thin band at pixel borders.
    // u_setting.x is the approximate on-screen size of one source pixel.
    float scale = max(u_setting.x, 1.0);
    vec2 texel = v_texcoord0 / u_texelDelta;
    vec2 base = floor(texel);
    vec2 centre = fract(texel) - 0.5;
    float range = 0.5 - 0.5 / scale;
    vec2 f = (centre - clamp(centre, -range, range)) * scale + 0.5;
    gl_FragColor = vec4(texture2D(sampler0, (base + f) * u_texelDelta).rgb, 1.0);
}
