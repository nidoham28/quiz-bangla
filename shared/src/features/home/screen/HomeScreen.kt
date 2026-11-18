@file:Suppress("UNCHECKED_CAST")

package features.home.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ui.theme.AuraTheme
import ui.theme.auroraBackdrop

@Composable
internal fun HomeScreen(
    /** Null shows a "coming soon" message until quizzes exist. */
    onPlay: (() -> Unit)? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val play: () -> Unit = remember(onPlay) {
        (onPlay ?: {
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar("Quizzes are coming soon")
            }
        }) as () -> Unit
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .auroraBackdrop(intensity = 0.6f),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = { PlayButton(onClick = play) },
            floatingActionButtonPosition = FabPosition.End,
        ) { innerPadding ->
            // Screen content goes here; the Play button lives in the FAB slot.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(AuraTheme.spacing.md),
            )
        }
    }
}

@Composable
private fun PlayButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(64.dp),
        shape = RoundedCornerShape(percent = 30),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 6.dp,
            pressedElevation = 2.dp,
        ),
    ) {
        PlayIcon(color = MaterialTheme.colorScheme.onPrimary)
        Spacer(Modifier.width(AuraTheme.spacing.sm))
        Text(
            text = "Play",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PlayIcon(color: Color) {
    Canvas(Modifier.size(25.dp)) {
        val w = size.width
        val h = size.height
        val triangle = Path().apply {
            moveTo(w * 0.32f, h * 0.20f)
            lineTo(w * 0.80f, h * 0.50f)
            lineTo(w * 0.32f, h * 0.80f)
            close()
        }
        drawPath(triangle, color)
        // Round the corners with a stroke of the same color.
        drawPath(
            path = triangle,
            color = color,
            style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round),
        )
    }
}