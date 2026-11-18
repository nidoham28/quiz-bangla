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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import features.chat.screen.ChatScreen
import features.explore.screen.ExploreScreen
import features.game.screen.GameScreen
import features.leaderboard.screen.LeaderboardScreen
import features.profile.screen.ProfileScreen
import navigation.AppRoutes
import ui.theme.auroraBackdrop

@Composable
internal fun HomeScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

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
                    homeNavigationItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(AppRoutes.EXPLORE)
                                    launchSingleTop = true
                                }
                            },
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
            NavHost(
                navController = navController,
                startDestination = AppRoutes.EXPLORE,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                composable(AppRoutes.EXPLORE) { ExploreScreen() }
                composable(AppRoutes.GAME) { GameScreen() }
                composable(AppRoutes.LEADERBOARD) { LeaderboardScreen() }
                composable(AppRoutes.CHAT) { ChatScreen() }
                composable(AppRoutes.PROFILE) { ProfileScreen() }
            }
        }
    }
}

private data class HomeNavigationItem(
    val route: String,
    val contentDescription: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val homeNavigationItems = listOf(
    HomeNavigationItem(AppRoutes.EXPLORE, "Explore", Icons.Filled.Explore),
    HomeNavigationItem(AppRoutes.GAME, "Game", Icons.Filled.SportsEsports),
    HomeNavigationItem(AppRoutes.LEADERBOARD, "Leaderboard", Icons.Filled.Leaderboard),
    HomeNavigationItem(AppRoutes.CHAT, "Chat", Icons.AutoMirrored.Filled.Chat),
    HomeNavigationItem(AppRoutes.PROFILE, "Profile", Icons.Filled.Person),
)