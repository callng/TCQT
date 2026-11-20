package com.owo233.tcqt.ui.settings

internal const val AboutTitleShader = """
    uniform shader background;
    uniform float2 uOffset;
    uniform float3 uSurface;
    uniform float uDark;
    uniform float uBlurRadius;

    float3 rgb2lab(float3 c) {
        c.r = c.r > 0.04045 ? pow((c.r + 0.055) / 1.055, 2.4) : c.r / 12.92;
        c.g = c.g > 0.04045 ? pow((c.g + 0.055) / 1.055, 2.4) : c.g / 12.92;
        c.b = c.b > 0.04045 ? pow((c.b + 0.055) / 1.055, 2.4) : c.b / 12.92;
        c *= 100.0;
        float3 xyz = float3(
            c.r * 0.4124 + c.g * 0.3576 + c.b * 0.1805,
            c.r * 0.2126 + c.g * 0.7152 + c.b * 0.0722,
            c.r * 0.0193 + c.g * 0.1192 + c.b * 0.9505) / float3(95.047, 100.0, 108.883);
        xyz.x = xyz.x > 0.008856 ? pow(xyz.x, 1.0/3.0) : 7.787 * xyz.x + 16.0/116.0;
        xyz.y = xyz.y > 0.008856 ? pow(xyz.y, 1.0/3.0) : 7.787 * xyz.y + 16.0/116.0;
        xyz.z = xyz.z > 0.008856 ? pow(xyz.z, 1.0/3.0) : 7.787 * xyz.z + 16.0/116.0;
        return float3(116.0 * xyz.y - 16.0, 500.0 * (xyz.x - xyz.y), 200.0 * (xyz.y - xyz.z));
    }

    float3 lab2rgb(float3 lab) {
        float y = (lab.x + 16.0) / 116.0;
        float x = lab.y / 500.0 + y;
        float z = y - lab.z / 200.0;
        y = pow(y, 3.0) > 0.008856 ? pow(y, 3.0) : (y - 16.0/116.0) / 7.787;
        x = pow(x, 3.0) > 0.008856 ? pow(x, 3.0) : (x - 16.0/116.0) / 7.787;
        z = pow(z, 3.0) > 0.008856 ? pow(z, 3.0) : (z - 16.0/116.0) / 7.787;
        float3 xyz = float3(x * 95.047, y * 100.0, z * 108.883) / 100.0;
        float r = xyz.x *  3.2406 + xyz.y * -1.5372 + xyz.z * -0.4986;
        float g = xyz.x * -0.9689 + xyz.y *  1.8758 + xyz.z *  0.0415;
        float b = xyz.x *  0.0557 + xyz.y * -0.2040 + xyz.z *  1.0570;
        r = r > 0.0031308 ? 1.055 * pow(r, 1.0/2.4) - 0.055 : 12.92 * r;
        g = g > 0.0031308 ? 1.055 * pow(g, 1.0/2.4) - 0.055 : 12.92 * g;
        b = b > 0.0031308 ? 1.055 * pow(b, 1.0/2.4) - 0.055 : 12.92 * b;
        return clamp(float3(r, g, b), 0.0, 1.0);
    }

    half4 main(float2 fragCoord) {
        // Sample the page coordinates, so the text follows the colors behind it even when scrolled.
        float3 c = float3(0.0);
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                float4 sample = background.eval(fragCoord + uOffset + float2(x, y) * uBlurRadius);
                c += sample.rgb + uSurface * (1.0 - sample.a);
            }
        }
        c = clamp(c / 9.0, 0.0, 1.0);
        if (uDark > 0.5) {
            // ColorDodge #e6a1a1a1, then LinearLight #4de6e6e6.
            c = mix(c, min(c / (1.0 - 161.0/255.0), 1.0), 230.0/255.0);
            c = mix(c, min(c + 2.0 * (230.0/255.0 - 0.5), 1.0), 77.0/255.0);
        } else {
            // ColorBurn #cc4a4a4a, then LinearLight #ff4f4f4f.
            c = mix(c, max(1.0 - (1.0 - c) / (74.0/255.0), 0.0), 204.0/255.0);
            c = max(c + 2.0 * (79.0/255.0) - 1.0, 0.0);
        }
        float3 lab = rgb2lab(c);
        float low = 26.0/255.0;
        float high = (uDark > 0.5 ? 245.0 : 242.0)/255.0;
        lab.x = (high - low) * lab.x + low * 100.0;
        return half4(half3(lab2rgb(lab)), 1.0);
    }
"""
