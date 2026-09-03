package com.owo233.tcqt.hooks.func.liquidglass

/**
 * 液态玻璃渲染所需的 AGSL 着色器程序集合。
 *
 * 折射透镜基于圆角矩形的有向距离场（SDF）：先由 [SDF_SOURCE] 计算像素到矩形边缘的
 * 带符号距离，再在距边缘一个「折射带」宽度内，按带内位置的圆弧映射曲线求出偏移量，
 * 沿 SDF 梯度方向对背景采样坐标施加位移，从而产生边缘内凹、中间通透的透镜效果。
 * 距边缘超过折射带的区域直接原样采样，保证玻璃中心不被扭曲。
 */
internal object GlassShaderLibrary {

    /** 圆角矩形 SDF 及其梯度：所有透镜与内阴影共用的几何基元。 */
    val SDF_SOURCE = """
        float radiusAt(float2 coord, float4 radii) {
            if (coord.x >= 0.0) {
                if (coord.y <= 0.0) return radii.y; else return radii.z;
            } else {
                if (coord.y <= 0.0) return radii.x; else return radii.w;
            }
        }
        float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
            float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
            float outside = length(max(cornerCoord, 0.0)) - radius;
            float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
            return outside + inside;
        }
        float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
            float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
            if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
                return sign(coord) * normalize(max(cornerCoord, 0.0));
            } else {
                float gradX = step(cornerCoord.y, cornerCoord.x);
                return sign(coord) * float2(gradX, 1.0 - gradX);
            }
        }
    """.trimIndent()

    /**
     * 静置玻璃药丸的透镜。
     *
     * 采样链为「饱和度提升 → 高斯模糊 → 边缘折射」，其中饱和与模糊由
     * [android.graphics.RenderEffect] 链式效果完成，本程序仅负责最外层的折射。
     */
    val PILL_LENS = """
        uniform shader content;
        uniform float2 size;
        uniform float2 offset;
        uniform float4 cornerRadii;
        uniform float refractionHeight;
        uniform float refractionAmount;
        uniform float depthEffect;
        $SDF_SOURCE
        float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }
        half4 main(float2 coord) {
            float2 halfSize = size * 0.5;
            float2 centeredCoord = (coord + offset) - halfSize;
            float radius = radiusAt(coord, cornerRadii);
            float sd = sdRoundedRect(centeredCoord, halfSize, radius);
            if (-sd >= refractionHeight) { return content.eval(coord); }
            sd = min(sd, 0.0);
            float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
            float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
            float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize,
                    gradRadius) + depthEffect * normalize(centeredCoord));
            float2 refractedCoord = coord + d * grad;
            return content.eval(refractedCoord);
        }
    """.trimIndent()

    /**
     * 液滴透镜：在药丸透镜的基础上增加色散。
     *
     * 沿折射方向按光谱顺序（红→紫）以不同偏移量多次采样背景并加权混合，
     * 使折射边缘出现细微的色散条纹。色散强度由对角坐标乘积调制，
     * 越靠近四角越明显，与真实玻璃棱镜的表现一致。
     */
    val DROPLET_LENS = """
        uniform shader content;
        uniform float2 size;
        uniform float2 offset;
        uniform float4 cornerRadii;
        uniform float refractionHeight;
        uniform float refractionAmount;
        uniform float depthEffect;
        uniform float chromaticAberration;
        $SDF_SOURCE
        float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }
        half4 main(float2 coord) {
            float2 halfSize = size * 0.5;
            float2 centeredCoord = (coord + offset) - halfSize;
            float radius = radiusAt(coord, cornerRadii);
            float sd = sdRoundedRect(centeredCoord, halfSize, radius);
            if (-sd >= refractionHeight) { return content.eval(coord); }
            sd = min(sd, 0.0);
            float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
            float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
            float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize,
                    gradRadius) + depthEffect * normalize(centeredCoord));
            float2 refractedCoord = coord + d * grad;
            float dispersionIntensity = chromaticAberration
                    * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
            float2 dispersedCoord = d * grad * dispersionIntensity;
            half4 color = half4(0.0);
            half4 red = content.eval(refractedCoord + dispersedCoord);
            color.r += red.r / 3.5; color.a += red.a / 7.0;
            half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
            color.r += orange.r / 3.5; color.g += orange.g / 7.0; color.a += orange.a / 7.0;
            half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
            color.r += yellow.r / 3.5; color.g += yellow.g / 3.5; color.a += yellow.a / 7.0;
            half4 green = content.eval(refractedCoord);
            color.g += green.g / 3.5; color.a += green.a / 7.0;
            half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
            color.g += cyan.g / 3.5; color.b += cyan.b / 3.0; color.a += cyan.a / 7.0;
            half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
            color.b += blue.b / 3.0; color.a += blue.a / 7.0;
            half4 purple = content.eval(refractedCoord - dispersedCoord);
            color.r += purple.r / 7.0; color.b += purple.b / 3.0; color.a += purple.a / 7.0;
            return color;
        }
    """.trimIndent()

    /**
     * 交互高光：跟随液滴位置的一层径向辉光。
     *
     * 以液滴中心为圆心、按半径的平滑阶梯衰减输出白色，配合 Plus 混合模式
     * 叠加到玻璃表面，在拖拽时产生局部泛光。alpha 以预乘形式返回，
     * 保证实际混合强度与设定的透明度一致。
     */
    val INTERACTIVE_HIGHLIGHT = """
        uniform float2 size;
        uniform float alpha;
        uniform float radius;
        uniform float2 position;
        half4 main(float2 coord) {
            float dist = distance(coord, position);
            float intensity = smoothstep(radius, radius * 0.5, dist);
            half a = half(alpha * intensity);
            return half4(a, a, a, a);
        }
    """.trimIndent()

    /**
     * 内阴影：从玻璃边缘向内平滑衰减的暗色渐变。
     *
     * 直接复用 SDF 距离做 smoothstep 衰减，避免描边式模拟产生的生硬内边。
     * 衰减呈平方曲线，使阴影在边缘处最浓、向内迅速消散。
     */
    val INNER_SHADOW = """
        uniform float2 size;
        uniform float radius;
        uniform float blur;
        uniform float alpha;
        $SDF_SOURCE
        half4 main(float2 coord) {
            float2 halfSize = size * 0.5;
            float sd = sdRoundedRect(coord - halfSize, halfSize, radius);
            float t = 1.0 - smoothstep(0.0, blur, -sd);
            half a = half(alpha * t * t);
            return half4(0.0, 0.0, 0.0, a);
        }
    """.trimIndent()
}
