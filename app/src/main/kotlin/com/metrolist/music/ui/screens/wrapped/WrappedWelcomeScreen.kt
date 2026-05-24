package com.metrolist.music.ui.screens.wrapped

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.metrolist.music.R
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.ui.theme.bbhBartle
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.HomeViewModel
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WrappedWelcomeScreen(
    pageOffset: Float,
    modifier: Modifier = Modifier,
) {
    val seedColors = remember {
        listOf(
            Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0),
            Color(0xFF673AB7), Color(0xFF3F51B5), Color(0xFF2196F3),
            Color(0xFF00BCD4), Color(0xFF009688), Color(0xFF4CAF50),
            Color(0xFFFFC107), Color(0xFFFF9800),
        )
    }
    val seedColor = remember { seedColors.random() }
    val isDark = isSystemInDarkTheme()
    val colorScheme = rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = isDark,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
        style = PaletteStyle.TonalSpot,
    )

    val surface = colorScheme.surface
    val primaryContainer = colorScheme.primaryContainer
    val tileColor = colorScheme.onSurface.copy(alpha = if (isDark) 0.12f else 0.08f)

    val expressiveShapes = remember {
        listOf(
            MaterialShapes.Boom, MaterialShapes.Burst,
            MaterialShapes.Flower, MaterialShapes.Ghostish,
            MaterialShapes.Heart, MaterialShapes.PixelCircle,
            MaterialShapes.Puffy, MaterialShapes.SoftBoom,
            MaterialShapes.SoftBurst, MaterialShapes.Sunny,
            MaterialShapes.VerySunny,
        )
    }
    val (topLeftShape, bottomRightShape) = remember {
        val shuffled = expressiveShapes.shuffled()
        shuffled[0] to shuffled[1]
    }

    val accountNameFromPref by rememberPreference(AccountNameKey, "")

    val activity = LocalContext.current as? ComponentActivity
    val homeViewModel: HomeViewModel? = activity?.let { hiltViewModel(it) }
    val homeAccountName by homeViewModel?.accountName?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf("") }

    val accountName = if (accountNameFromPref.isNotBlank()) accountNameFromPref
        else if (homeAccountName.isNotBlank() && homeAccountName != "Guest") homeAccountName
        else ""
    val showUsername = accountName.isNotBlank()
    val firstName = accountName.substringBefore(' ', missingDelimiterValue = accountName)

    val density = LocalDensity.current
    val tileSizePx = with(density) { 80.dp.toPx() }
    val tlBaseOffX = with(density) { (-120).dp.toPx() }
    val tlBaseOffY = with(density) { (-120).dp.toPx() }
    val brBaseOffX = with(density) { 160.dp.toPx() }
    val brBaseOffY = with(density) { 160.dp.toPx() }

    val cookiePath = remember(density, tileSizePx) {
        val polygon = MaterialShapes.Cookie12Sided.normalized()
        val bounds = FloatArray(4)
        polygon.calculateBounds(bounds)
        val shapeWidth = bounds[2] - bounds[0]
        val shapeHeight = bounds[3] - bounds[1]
        val desiredSize = tileSizePx * 0.5f
        val scale = if (shapeWidth > 0f && shapeHeight > 0f) {
            desiredSize / maxOf(shapeWidth, shapeHeight)
        } else 1f

        val path = Path()
        val cubics = polygon.cubics
        if (cubics.isNotEmpty()) {
            path.moveTo(cubics[0].anchor0X * scale, cubics[0].anchor0Y * scale)
            for (cubic in cubics) {
                path.cubicTo(
                    cubic.control0X * scale, cubic.control0Y * scale,
                    cubic.control1X * scale, cubic.control1Y * scale,
                    cubic.anchor1X * scale, cubic.anchor1Y * scale,
                )
            }
            path.close()
        }
        path
    }

    // Entrance animation
    val welcomeLetterOffPx = with(density) { 300.dp.toPx() }
    val usernameLetterOffPx = with(density) { 300.dp.toPx() }
    val subtitleSlidePx = with(density) { 24.dp.toPx() }
    val tlEntranceInitPx = with(density) { (-800).dp.toPx() }
    val brEntranceInitPx = with(density) { 800.dp.toPx() }

    val bgEntranceScale = remember { Animatable(1.8f) }
    val tlEntranceOffX = remember { Animatable(tlEntranceInitPx) }
    val tlEntranceOffY = remember { Animatable(tlEntranceInitPx) }
    val brEntranceOffX = remember { Animatable(brEntranceInitPx) }
    val brEntranceOffY = remember { Animatable(brEntranceInitPx) }

    val welcomeTitle = stringResource(R.string.wrapped_welcome_title)
    val welcomeLetterProgress = remember { welcomeTitle.map { Animatable(0f) } }
    val usernameLetterProgress = remember(firstName) {
        firstName.uppercase().map { Animatable(0f) }
    }
    val subtitleAlpha = remember { Animatable(0f) }
    val subtitleOffsetY = remember { Animatable(subtitleSlidePx) }

    LaunchedEffect(Unit) {
        launch {
            bgEntranceScale.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing))
        }

        launch {
            tlEntranceOffX.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f))
        }
        launch {
            tlEntranceOffY.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f))
        }
        launch {
            brEntranceOffX.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f))
        }
        launch {
            brEntranceOffY.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f))
        }

        launch {
            delay(600)
            welcomeLetterProgress.forEachIndexed { i, anim ->
                launch {
                    delay(i * 40L)
                    anim.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
                }
            }
        }

        launch {
            delay(1400)
            usernameLetterProgress.forEachIndexed { i, anim ->
                launch {
                    delay((usernameLetterProgress.size - 1 - i) * 40L)
                    anim.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
                }
            }
        }

        launch {
            delay(2500)
            subtitleAlpha.animateTo(1f, tween(500))
            subtitleOffsetY.animateTo(0f, tween(500))
        }
    }

    val scrollProgress = remember(pageOffset) {
        maxOf(0f, -pageOffset).coerceIn(0f, 1f)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "welcome_anim")

    val bgBaseRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 25000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bgRotation",
    )

    val tlRotationAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tlRotation",
    )

    val brRotationAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "brRotation",
    )

    val bgRotation = bgBaseRotation + scrollProgress * 360f
    val bgAlpha = if (scrollProgress <= 0.15f) 1f
    else lerp(1f, 0f, (scrollProgress - 0.15f) / 0.3f).coerceIn(0f, 1f)

    val shapeProgress = scrollProgress
    val shapeScale = if (shapeProgress <= 0.1f) lerp(1f, 1.3f, shapeProgress / 0.1f)
    else lerp(1.3f, 0.3f, (shapeProgress - 0.1f) / 0.25f).coerceIn(0.3f, 1.3f)
    val shapeAlpha = if (shapeProgress <= 0.35f) 1f
    else lerp(1f, 0f, (shapeProgress - 0.35f) / 0.15f).coerceIn(0f, 1f)

    val tlTranslationX = lerp(0f, -250f, (shapeProgress / 0.5f).coerceAtMost(1f))
    val tlTranslationY = lerp(0f, -250f, (shapeProgress / 0.5f).coerceAtMost(1f))
    val brTranslationX = lerp(0f, 250f, (shapeProgress / 0.5f).coerceAtMost(1f))
    val brTranslationY = lerp(0f, 250f, (shapeProgress / 0.5f).coerceAtMost(1f))

    val textAlpha = if (scrollProgress <= 0.2f) 1f
    else lerp(1f, 0f, (scrollProgress - 0.2f) / 0.35f).coerceIn(0f, 1f)
    val textTranslationY = lerp(0f, -150f, (scrollProgress / 0.55f).coerceAtMost(1f))
    val textScale = lerp(1f, 0.6f, (scrollProgress / 0.55f).coerceAtMost(1f))

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surface),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cols = ceil(size.width / tileSizePx).toInt() + 4
            val rows = ceil(size.height / tileSizePx).toInt() + 4

            rotate(bgRotation, pivot = Offset(size.width / 2f, size.height / 2f)) {
                scale(bgEntranceScale.value, bgEntranceScale.value, pivot = Offset(size.width / 2f, size.height / 2f)) {
                    val startX = -cols * tileSizePx / 2f + size.width / 2f
                    val startY = -rows * tileSizePx / 2f + size.height / 2f

                    for (row in 0 until rows) {
                        for (col in 0 until cols) {
                            translate(
                                left = startX + col * tileSizePx + tileSizePx / 2f,
                                top = startY + row * tileSizePx + tileSizePx / 2f,
                            ) {
                                drawPath(
                                    path = cookiePath,
                                    color = tileColor,
                                    alpha = bgAlpha,
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        (tlBaseOffX + tlTranslationX + tlEntranceOffX.value).roundToInt(),
                        (tlBaseOffY + tlTranslationY + tlEntranceOffY.value).roundToInt(),
                    )
                }
                .size(280.dp)
                .graphicsLayer {
                    scaleX = shapeScale
                    scaleY = shapeScale
                    alpha = shapeAlpha
                    rotationZ = tlRotationAnim
                }
                .clip(topLeftShape.toShape())
                .background(primaryContainer),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset {
                    IntOffset(
                        (brBaseOffX + brTranslationX + brEntranceOffX.value).roundToInt(),
                        (brBaseOffY + brTranslationY + brEntranceOffY.value).roundToInt(),
                    )
                }
                .size(320.dp)
                .graphicsLayer {
                    scaleX = shapeScale
                    scaleY = shapeScale
                    alpha = shapeAlpha
                    rotationZ = brRotationAnim
                }
                .clip(bottomRightShape.toShape())
                .background(primaryContainer),
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = textAlpha
                    translationY = textTranslationY
                    scaleX = textScale
                    scaleY = textScale
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                welcomeTitle.forEachIndexed { index, char ->
                    val progress = welcomeLetterProgress[index].value
                    Text(
                        text = char.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontFamily = bbhBartle,
                            fontSize = 42.sp,
                        ),
                        color = Color.White,
                        modifier = Modifier.graphicsLayer {
                            translationX = -welcomeLetterOffPx * (1f - progress)
                            rotationZ = -15f * (1f - progress)
                        },
                    )
                }
            }

            if (showUsername) {
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val letters = firstName.uppercase()
                    letters.forEachIndexed { index, char ->
                        val progress = usernameLetterProgress[index].value
                        Text(
                            text = char.toString(),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = bbhBartle,
                                fontSize = 42.sp,
                            ),
                            color = Color.White,
                            modifier = Modifier.graphicsLayer {
                                translationX = usernameLetterOffPx * (1f - progress)
                                rotationZ = 15f * (1f - progress)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.wrapped_welcome_subtitle),
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .graphicsLayer {
                        alpha = subtitleAlpha.value
                        translationY = subtitleOffsetY.value
                    },
            )
        }
    }
}
