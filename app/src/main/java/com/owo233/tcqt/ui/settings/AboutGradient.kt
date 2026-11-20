package com.owo233.tcqt.ui.settings

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun rememberAboutGradient(): AboutGradient {
    val surface = MiuixTheme.colorScheme.surface
    val foreground = MiuixTheme.colorScheme.onSurface
    val dark = surface.luminance() < 0.5f
    val palette = if (dark) DarkAboutPalette else LightAboutPalette
    val lifecycleOwner = LocalLifecycleOwner.current
    val colorStage = remember { Animatable(0f) }
    var time by remember { mutableFloatStateOf(0f) }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= 33) RuntimeShader(AboutGradientShader).apply {
            setFloatUniform("uTranslateY", 0f)
            setFloatUniform("uNoiseScale", 1.5f)
            setFloatUniform("uPointRadiusMulti", 1f)
            setFloatUniform("uAlphaMulti", 1f)
            setFloatUniform("uPoints", floatArrayOf(0.8f, 0.2f, 1f, 0.8f, 0.9f, 1f, 0.2f, 0.9f, 1f, 0.2f, 0.2f, 1f))
        } else null
    }
    val brush = remember(shader, palette, surface) {
        if (Build.VERSION.SDK_INT >= 33 && shader != null) ShaderBrush(shader) else Brush.verticalGradient(
            listOf(Color(palette.colors[0][0], palette.colors[0][1], palette.colors[0][2]), surface)
        )
    }
    val points = remember { FloatArray(8) }
    val colors = remember { FloatArray(16) }
    var pageSize by remember { mutableStateOf(Size.Zero) }
    var pagePosition by remember { mutableStateOf(Offset.Zero) }
    var titlePosition by remember { mutableStateOf(Offset.Zero) }
    val titleShader = remember(shader) {
        if (Build.VERSION.SDK_INT >= 33 && shader != null) RuntimeShader(AboutTitleShader) else null
    }
    val titleBrush = remember(titleShader, palette, foreground) {
        if (Build.VERSION.SDK_INT >= 33 && titleShader != null) ShaderBrush(titleShader) else Brush.linearGradient(
            listOf(0, 12).map { index ->
                lerp(Color(palette.colors[0][index], palette.colors[0][index + 1], palette.colors[0][index + 2]), foreground, 0.65f)
            }
        )
    }
    LaunchedEffect(lifecycleOwner, palette) {
        // The shader is an Android 13 API; older supported hosts keep the same palette statically.
        if (Build.VERSION.SDK_INT < 33) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val motionScale = coroutineContext[MotionDurationScale]
            if (motionScale?.scaleFactor == 0f) return@repeatOnLifecycle
            launch {
                var nextStage = floor(colorStage.value) + 1f
                while (isActive) {
                    delay(palette.periodMillis.milliseconds)
                    colorStage.animateTo(nextStage, spring(dampingRatio = 0.9f, stiffness = 35f))
                    nextStage += 1f
                }
            }
            val startTime = time
            val origin = withFrameNanos { it }
            var previous = origin
            while (isActive) {
                val now = withFrameNanos { it }
                if (now - previous < 1_000_000_000L / 60) continue
                previous = now
                val scale = motionScale?.scaleFactor ?: 1f
                if (scale > 0f) time = startTime + (now - origin) / (1_000_000_000f * scale)
            }
        }
    }
    // Both drawing layers read the same clock and update the same sampled background.
    val updateShader: (Size) -> Unit = { size ->
        if (Build.VERSION.SDK_INT >= 33 && shader != null) {
            val stage = colorStage.value
            val base = floor(stage).toInt()
            val fraction = stage - base
            val start = palette.colors[base.mod(4)]
            val end = palette.colors[(base + 1).mod(4)]
            for (i in colors.indices) colors[i] = start[i] + (end[i] - start[i]) * fraction
            for (i in 0 until 4) {
                val x = if (i < 2) 0.8f else 0.2f
                val y = if (i == 0 || i == 3) 0.2f else 0.9f
                points[i * 2] = x + sin(time + y) * palette.pointOffset
                points[i * 2 + 1] = y + cos(time + points[i * 2]) * palette.pointOffset
            }
            val boundHeight = if (size.width <= size.height) 0.5f else size.width / size.height
            shader.setFloatUniform("uResolution", size.width, size.height)
            shader.setFloatUniform("uBound", 0f, 0.75f - boundHeight / 2, 1f, boundHeight)
            shader.setFloatUniform("uColors", colors)
            shader.setFloatUniform("uPointsAnim", points)
            shader.setFloatUniform("uAnimTime", time)
            shader.setFloatUniform("uLightOffset", palette.lightOffset)
            shader.setFloatUniform("uSaturateOffset", palette.saturateOffset)
        }
    }
    return AboutGradient(
        background = Modifier.onSizeChanged { pageSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .onGloballyPositioned { pagePosition = it.positionInRoot() }
            .drawBehind {
                drawRect(surface)
                updateShader(size)
                drawRect(brush)
            },
        title = Modifier.onGloballyPositioned { titlePosition = it.positionInRoot() }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                if (Build.VERSION.SDK_INT >= 33 && titleShader != null && shader != null) {
                    updateShader(pageSize)
                    // setInputShader captures the native shader, so rebind after the frame uniforms change.
                    titleShader.setInputShader("background", shader)
                    val offset = titlePosition - pagePosition
                    titleShader.setFloatUniform("uOffset", offset.x, offset.y)
                    titleShader.setFloatUniform("uSurface", surface.red, surface.green, surface.blue)
                    titleShader.setFloatUniform("uDark", if (dark) 1f else 0f)
                    titleShader.setFloatUniform("uBlurRadius", 150.dp.toPx() / 3f)
                }
                drawContent()
                drawRect(titleBrush, blendMode = BlendMode.SrcIn)
            },
    )
}

internal class AboutGradient(val background: Modifier, val title: Modifier)

private class AboutPalette(
    colors1: FloatArray,
    colors2: FloatArray,
    colors3: FloatArray,
    val periodMillis: Long,
    val lightOffset: Float,
    val saturateOffset: Float,
    val pointOffset: Float,
) {
    val colors = listOf(colors2, colors1, colors2, colors3)
}

private val LightAboutPalette = AboutPalette(
    colors1 = floatArrayOf(1f, 0.9f, 0.94f, 1f, 1f, 0.84f, 0.89f, 1f, 0.97f, 0.73f, 0.82f, 1f, 0.64f, 0.65f, 0.98f, 1f),
    colors2 = floatArrayOf(0.58f, 0.74f, 1f, 1f, 1f, 0.9f, 0.93f, 1f, 0.74f, 0.76f, 1f, 1f, 0.97f, 0.77f, 0.84f, 1f),
    colors3 = floatArrayOf(0.98f, 0.86f, 0.9f, 1f, 0.6f, 0.73f, 0.98f, 1f, 0.92f, 0.93f, 1f, 1f, 0.56f, 0.69f, 1f, 1f),
    periodMillis = 2500,
    lightOffset = 0.1f,
    saturateOffset = 0.2f,
    pointOffset = 0.2f,
)

private val DarkAboutPalette = AboutPalette(
    colors1 = floatArrayOf(0.2f, 0.06f, 0.88f, 0.4f, 0.3f, 0.14f, 0.55f, 0.5f, 0f, 0.64f, 0.96f, 0.5f, 0.11f, 0.16f, 0.83f, 0.4f),
    colors2 = floatArrayOf(0.07f, 0.15f, 0.79f, 0.5f, 0.62f, 0.21f, 0.67f, 0.5f, 0.06f, 0.25f, 0.84f, 0.5f, 0f, 0.2f, 0.78f, 0.5f),
    colors3 = floatArrayOf(0.58f, 0.3f, 0.74f, 0.4f, 0.27f, 0.18f, 0.6f, 0.5f, 0.66f, 0.26f, 0.62f, 0.5f, 0.12f, 0.16f, 0.7f, 0.6f),
    periodMillis = 4000,
    lightOffset = 0f,
    saturateOffset = 0.17f,
    pointOffset = 0.4f,
)
