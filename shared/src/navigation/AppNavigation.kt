package navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import features.auth.screen.AuthenticatedScreen
import features.auth.screen.LoginScreen
import features.auth.screen.RegisterScreen
import features.splash.screen.SplashScreen
import features.auth.screen.toAuthDisplayMessage
import services.supabase.AuthRepository
import kotlin.coroutines.cancellation.CancellationException

@Composable
internal fun AppNavigation() {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }
    var initializationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            startDestination = if (AuthRepository.hasActiveSession()) {
                AppRoutes.HOME
            } else {
                AppRoutes.LOGIN
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            initializationError = exception.toAuthDisplayMessage()
            startDestination = AppRoutes.LOGIN
        }
    }

    val initialRoute = startDestination
    if (initialRoute == null) {
        SplashScreen(onContinue = {})
        return
    }

    NavHost(
        navController = navController,
        startDestination = initialRoute,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(AppRoutes.LOGIN) {
            LoginScreen(
                errorMessage = initializationError,
                onLogin = { email, password ->
                    AuthRepository.signIn(email, password)
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onCreateAccount = {
                    navController.navigate(AppRoutes.REGISTER) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(AppRoutes.REGISTER) {
            RegisterScreen(
                errorMessage = initializationError,
                onRegister = AuthRepository::signUp,
                onAuthenticated = {
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(AppRoutes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onLogin = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.HOME) {
            AuthenticatedScreen(
                onSignOut = {
                    AuthRepository.signOut()
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(AppRoutes.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
