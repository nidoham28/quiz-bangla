package features.home.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ui.theme.AuraTheme
import ui.theme.auroraBackdrop

@Composable
internal fun HomeScreen() {
    var selectedNavigationItem by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .auroraBackdrop(intensity = 0.6f),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                ) {
                    homeNavigationItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedNavigationItem == index,
                            onClick = { selectedNavigationItem = index },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.contentDescription,
                                    modifier = Modifier.size(28.dp),
                                )
                            },
                            label = null,
                            alwaysShowLabel = false,
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(AuraTheme.spacing.md),
            )
        }
    }
}

private data class HomeNavigationItem(
    val contentDescription: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val homeNavigationItems = listOf(
    HomeNavigationItem("Explore", Icons.Filled.Explore),
    HomeNavigationItem("Game", Icons.Filled.SportsEsports),
    HomeNavigationItem("Leaderboard", Icons.Filled.Leaderboard),
    HomeNavigationItem("Chat", Icons.AutoMirrored.Filled.Chat),
    HomeNavigationItem("Profile", Icons.Filled.Person),
)