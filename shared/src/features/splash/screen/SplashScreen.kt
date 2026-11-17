package features.splash.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.aurafeed.quiz.resources.Res
import io.aurafeed.quiz.resources.app_logo
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import ui.theme.*
import kotlin.time.Duration.Companion.milliseconds

private val SplashDuration = 2_500.milliseconds

@Composable
internal fun SplashScreen(
    appName: String = "Quiz Game",
    onContinue: () -> Unit,
) {
    // Always call the latest lambda, even if the caller recomposes during the delay.
    val currentOnContinue by rememberUpdatedState(onContinue)
    var entered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        entered = true
        delay(SplashDuration)
        currentOnContinue()
    }

    // One orchestrated entrance: logo settles in, then the name, then the footer.
    val logoAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(AuraMotion.Slow, easing = AuraMotion.Emphasized),
        label = "splashLogoAlpha",
    )
    val logoScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.86f,
        animationSpec = AuraMotion.spatial(),
        label = "splashLogoScale",
    )
    val nameAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(AuraMotion.Slow, delayMillis = 240, easing = AuraMotion.Emphasized),
        label = "splashNameAlpha",
    )
    val footerAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(AuraMotion.Slow, delayMillis = 480, easing = AuraMotion.Emphasized),
        label = "splashFooterAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The graphicsLayer does not clip, so the glow can spill past the logo bounds.
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = logoAlpha
                    scaleX = logoScale
                    scaleY = logoScale
                },
            ) {
                Image(
                    painter = painterResource(Res.drawable.app_logo),
                    contentDescription = null, // decorative: the app name is shown below
                    modifier = Modifier
                        .size(120.dp)
                        .auraShadow(AuraElevation.Floating, AuraShapes.avatar)
                        .clip(AuraShapes.avatar),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(Modifier.height(AuraTheme.spacing.xl))

            Text(
                text = appName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { alpha = nameAlpha },
            )
        }

        Text(
            text = "Made in Bangladesh",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = AuraTheme.spacing.xxl)
                .graphicsLayer { alpha = footerAlpha },
        )
    }
}