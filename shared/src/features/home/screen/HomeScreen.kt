package features.home.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import ui.theme.auroraBackdrop

@Composable
internal fun HomeScreen(onAddQuestions: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .auroraBackdrop(intensity = 0.6f),
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Quiz Bangla",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text("Add Questions") },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onAddQuestions()
                            }
                        },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            },
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                homeNavigationItems
                                    .firstOrNull { it.route == currentRoute }
                                    ?.contentDescription
                                    ?: "Quiz Bangla",
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                            ) {
                                Icon(Icons.Filled.Menu, contentDescription = "Open navigation drawer")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                    )
                },
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