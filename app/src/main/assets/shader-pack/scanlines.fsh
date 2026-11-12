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
    vec3 c = texture2D(sampler0, v_texcoord0).rgb;
    // position inside the source pixel row: 0.0 = row centre, 1.0 = row edge
    float edge = abs(fract(v_texcoord0.y / u_texelDelta.y) - 0.5) * 2.0;
    float dark = clamp(u_setting.x, 0.0, 1.0) * pow(edge, max(u_setting.y, 1.0));
    c *= 1.0 - dark;
    // slight brightness boost to compensate for the darkened lines
    c *= 1.0 + 0.25 * clamp(u_setting.x, 0.0, 1.0);
    gl_FragColor = vec4(c, 1.0);
}
