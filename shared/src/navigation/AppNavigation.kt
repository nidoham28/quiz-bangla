package navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import features.auth.screen.LoginScreen
import features.auth.screen.RegisterScreen
import features.auth.screen.toAuthDisplayMessage
import features.home.screen.HomeScreen
import features.splash.screen.SplashScreen
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import services.supabase.AuthRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun AppNavigation() {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }
    var initializationError by remember { mutableStateOf<String?>(null) }

    // Session check ar minimum splash time ek sathe cholbe,
    // duitai shesh hole tokhon-i ekbar destination set hobe.
    LaunchedEffect(Unit) {
        val minimumSplash = launch { delay(3.seconds) }

        val hasSession = try {
            AuthRepository.hasActiveSession()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            initializationError = exception.toAuthDisplayMessage()
            false
        }

        minimumSplash.join()
        startDestination = if (hasSession) AppRoutes.HOME else AppRoutes.LOGIN
    }

    val initialRoute = startDestination
    if (initialRoute == null) {
        SplashScreen()
    } else {
        // Login/logout holei ekhane theke navigate hobe.
        LaunchedEffect(navController) {
            AuthRepository.sessionStatus.collect { status ->
                val current = navController.currentDestination?.route
                when (status) {
                    is SessionStatus.Authenticated if current != AppRoutes.HOME ->
                        navController.resetTo(AppRoutes.HOME)

                    is SessionStatus.NotAuthenticated if current == AppRoutes.HOME ->
                        navController.resetTo(AppRoutes.LOGIN)

                    else -> {}
                }
            }
        }

        NavHost(
            navController = navController,
            startDestination = initialRoute,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(AppRoutes.LOGIN) {
                LoginScreen(
                    errorMessage = initializationError,
                    // Success hole upore-r listener HOME-e niye jabe.
                    onLogin = { email, password ->
                        AuthRepository.signIn(email, password)
                    },
                    onGoogleSignIn = AuthRepository::signInWithGoogle,
                    onCreateAccount = {
                        navController.navigate(AppRoutes.REGISTER) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(AppRoutes.REGISTER) {
                RegisterScreen(
                    errorMessage = null,
                    onRegister = AuthRepository::signUp,
                    onGoogleSignIn = AuthRepository::signInWithGoogle,
                    onAuthenticated = { navController.resetTo(AppRoutes.HOME) },
                    onLogin = { navController.popBackStack() },
                )
            }
            composable(AppRoutes.HOME) {
                HomeScreen()
            }
        }
    }
}

private fun NavController.resetTo(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}