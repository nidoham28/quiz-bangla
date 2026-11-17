package navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import features.login.screen.LoginScreen
import features.splash.screen.SplashScreen

@Composable
internal fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.SPLASH,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(AppRoutes.SPLASH) {
            SplashScreen {
                navController.navigate(AppRoutes.LOGIN) {
                    popUpTo(AppRoutes.SPLASH) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        composable(AppRoutes.LOGIN) {
            LoginScreen()
        }
    }
}
