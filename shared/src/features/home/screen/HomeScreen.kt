package features.home.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import features.auth.screen.toAuthDisplayMessage
import io.aurafeed.quiz.resources.Res
import io.aurafeed.quiz.resources.app_logo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import ui.theme.AuraShapes
import ui.theme.AuraTheme
import ui.theme.auroraBackdrop
import kotlin.coroutines.cancellation.CancellationException

private const val AppName = "Quiz Bangla"

/** What the top bar shows. Feed this from your ViewModel (balance is in whole taka). */
@Immutable
internal data class PlayerWallet(
    val diamonds: Int = 0,
    val gold: Int = 0,
    val balance: Int = 0,
)

@Composable
internal fun HomeScreen(
    onSignOut: suspend () -> Unit,
    wallet: PlayerWallet = PlayerWallet(),
    /** Null shows a "coming soon" message until quizzes exist. */
    onPlay: (() -> Unit)? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var signingOut by remember { mutableStateOf(false) }

    fun signOut() {
        signingOut = true
        scope.launch {
            try {
                onSignOut()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                snackbarHostState.showSnackbar(exception.toAuthDisplayMessage())
            } finally {
                signingOut = false
            }
        }
    }

    val play: () -> Unit = onPlay ?: {
        scope.launch { snackbarHostState.showSnackbar("Quizzes are coming soon") }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Row(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = AuraTheme.spacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AuraTheme.spacing.md),
                ) {
                    Image(
                        painter = painterResource(Res.drawable.app_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(AuraShapes.avatar),
                    )
                    Text(text = AppName, style = MaterialTheme.typography.titleLarge)
                }
                NavigationDrawerItem(
                    label = { Text("Home") },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .auroraBackdrop(intensity = 0.6f),
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    HomeTopBar(
                        wallet = wallet,
                        signingOut = signingOut,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onSignOut = ::signOut,
                    )
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = play,
                        shape = AuraShapes.pill,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { PlayIcon(color = MaterialTheme.colorScheme.onPrimary) },
                        text = { Text("Play", style = MaterialTheme.typography.labelLarge) },
                    )
                },
            ) { contentPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .verticalScroll(scrollState)
                        // Extra bottom space so the last card never hides behind the Play button.
                        .padding(
                            start = AuraTheme.spacing.gutter,
                            end = AuraTheme.spacing.gutter,
                            top = AuraTheme.spacing.sm,
                            bottom = 96.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(AuraTheme.spacing.xl),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(AuraTheme.spacing.sm)) {
                        Text(
                            text = "Welcome back",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Ready for a challenge?",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Explore topics and get ready to test what you know.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// region Top bar

/** Drawer icon, app name, diamonds, gold, balance, overflow menu. Nothing else. */
@Composable
private fun HomeTopBar(
    wallet: PlayerWallet,
    signingOut: Boolean,
    onMenuClick: () -> Unit,
    onSignOut: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val iconColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
            .height(56.dp)
            .padding(horizontal = AuraTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = "Open menu" },
        ) {
            HamburgerIcon(color = iconColor)
        }

        Spacer(Modifier.width(AuraTheme.spacing.sm))

        Text(
            text = AppName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(AuraTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatChip(
                description = "${wallet.diamonds} diamonds",
                value = wallet.diamonds.compact(),
                icon = { DiamondIcon(color = AuraTheme.colors.auroraCyan) },
            )
            StatChip(
                description = "${wallet.gold} gold",
                value = wallet.gold.compact(),
                icon = { GoldCoinIcon(color = AuraTheme.colors.chart[4]) },
            )
            StatChip(
                description = "Balance ${wallet.balance} taka",
                value = wallet.balance.compact(),
                icon = {
                    Text(
                        text = "৳",
                        style = AuraTheme.text.metric,
                        color = AuraTheme.colors.success,
                    )
                },
            )
        }

        Box {
            IconButton(
                onClick = { menuOpen = true },
                enabled = !signingOut,
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = "More options" },
            ) {
                if (signingOut) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    MoreDotsIcon(color = iconColor)
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Sign out") },
                    onClick = {
                        menuOpen = false
                        onSignOut()
                    },
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    description: String,
    value: String,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuraTheme.spacing.xs),
    ) {
        icon()
        Text(
            text = value,
            style = AuraTheme.text.metric, // tabular figures: numbers don't jitter as they change
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** 950 -> "950", 1250 -> "1250", 12_500 -> "12.5K", 2_000_000 -> "2M". */
private fun Int.compact(): String {
    fun scaled(divisor: Int, suffix: String): String {
        val whole = this / divisor
        val tenth = (this % divisor) / (divisor / 10)
        return if (tenth == 0) "$whole$suffix" else "$whole.$tenth$suffix"
    }
    return when {
        this >= 1_000_000 -> scaled(1_000_000, "M")
        this >= 10_000 -> scaled(1_000, "K")
        else -> toString()
    }
}

// endregion

// region Icons (drawn, so no icon-library dependency)

@Composable
private fun HamburgerIcon(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2
        val rows = listOf(0.2f to 1f, 0.5f to 0.62f, 0.8f to 1f) // y, relative width
        rows.forEach { (y, width) ->
            drawLine(
                color = color,
                start = Offset(inset, size.height * y),
                end = Offset(inset + (size.width - stroke) * width, size.height * y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun MoreDotsIcon(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val radius = 1.9.dp.toPx()
        listOf(0.17f, 0.5f, 0.83f).forEach { y ->
            drawCircle(color, radius, Offset(size.width / 2f, size.height * y))
        }
    }
}

@Composable
private fun DiamondIcon(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        fun p(x: Float, y: Float) = Offset(w * x, h * y)

        val gem = Path().apply {
            moveTo(w * 0.24f, h * 0.08f)
            lineTo(w * 0.76f, h * 0.08f)
            lineTo(w * 0.96f, h * 0.38f)
            lineTo(w * 0.50f, h * 0.94f)
            lineTo(w * 0.04f, h * 0.38f)
            close()
        }
        drawPath(gem, color)

        val facet = Color.White.copy(alpha = 0.55f)
        val width = 0.9.dp.toPx()
        drawLine(facet, p(0.04f, 0.38f), p(0.96f, 0.38f), width)
        drawLine(facet, p(0.24f, 0.08f), p(0.38f, 0.38f), width)
        drawLine(facet, p(0.38f, 0.38f), p(0.50f, 0.94f), width)
        drawLine(facet, p(0.76f, 0.08f), p(0.62f, 0.38f), width)
        drawLine(facet, p(0.62f, 0.38f), p(0.50f, 0.94f), width)
    }
}

@Composable
private fun GoldCoinIcon(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        drawCircle(color)
        drawCircle(
            color = Color.Black.copy(alpha = 0.22f),
            radius = size.minDimension * 0.30f,
            style = Stroke(width = 1.2.dp.toPx()),
        )
    }
}

@Composable
private fun PlayIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
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

// endregion

