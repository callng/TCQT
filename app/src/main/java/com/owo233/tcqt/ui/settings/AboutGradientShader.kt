package com.owo233.tcqt.ui.settings

internal const val AboutGradientShader = """
    uniform float2 uResolution;
    uniform float uAnimTime;
    uniform float4 uBound;
    uniform float uTranslateY;
    uniform float3 uPoints[4];
    uniform float2 uPointsAnim[4];
    uniform float4 uColors[4];
    uniform float uAlphaMulti;
    uniform float uNoiseScale;
    uniform float uPointRadiusMulti;
    uniform float uSaturateOffset;
    uniform float uLightOffset;

    float3 rgb2hsv(float3 c) {
        float4 K = float4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
        float4 p = mix(float4(c.bg, K.wz), float4(c.gb, K.xy), step(c.b, c.g));
        float4 q = mix(float4(p.xyw, c.r), float4(c.r, p.yzx), step(p.x, c.r));
        float d = q.x - min(q.w, q.y);
        float e = 1.0e-10;
        return float3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
    }

    float3 hsv2rgb(float3 c) {
        float4 K = float4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
        float3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
        return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
    }

    float hash(float2 p) {
        float3 p3 = fract(float3(p.xyx) * 0.13);
        p3 += dot(p3, p3.yzx + 3.333);
        return fract((p3.x + p3.y) * p3.z);
    }

    float perlin(float2 x) {
        float2 i = floor(x); float2 f = fract(x);

        float a = hash(i); float b = hash(i + float2(1.0, 0.0));
        float c = hash(i + float2(0.0, 1.0)); float d = hash(i + float2(1.0, 1.0));

        float2 u = f * f * (3.0 - 2.0 * f);
        return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
    }

    float gradientNoise(in float2 uv) {
        return fract(52.9829189 * fract(dot(uv, float2(0.06711056, 0.00583715))));
    }

    float4 main(float2 fragCoord){
        float2 vUv = fragCoord/uResolution;
        vUv.y = 1.0-vUv.y;
        float2 uv = vUv;
        uv -= float2(0., uTranslateY);
        uv.xy -= uBound.xy;
        uv.xy /= uBound.zw;

        float4 color = float4(0.0);
        float noiseValue = perlin(vUv * uNoiseScale + float2(-uAnimTime, -uAnimTime));

        for (int i = 0; i < 4; i++){
            float4 pointColor = uColors[i];
            pointColor.rgb *= pointColor.a;
            float2 point = uPointsAnim[i];
            float rad = uPoints[i].z * uPointRadiusMulti;

            float d = distance(uv, point);
            float pct = smoothstep(rad, 0., d);
            color.rgb = mix(color.rgb, pointColor.rgb, pct);
            color.a = mix(color.a, pointColor.a, pct);
        }

        float oppositeNoise = smoothstep(0., 1., noiseValue);
        if (color.a <= 0.0) return float4(0.0);
        color.rgb /= color.a;
        float3 hsv = rgb2hsv(color.rgb);
        hsv.y = mix(hsv.y, 0.0, oppositeNoise * uSaturateOffset);
        color.rgb = hsv2rgb(hsv);
        color.rgb += oppositeNoise * uLightOffset;

        color.a = clamp(color.a, 0., 1.);
        color.a *= uAlphaMulti;

        color += (10.0 / 255.0) * gradientNoise(fragCoord.xy) - (5.0 / 255.0);
        return float4(color.rgb * color.a, color.a);
    }
"""
