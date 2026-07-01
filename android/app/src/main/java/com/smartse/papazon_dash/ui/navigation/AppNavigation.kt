package com.smartse.papazon_dash.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartse.papazon_dash.ui.screens.history.HistoryScreen
import com.smartse.papazon_dash.ui.screens.main.MainScreen
import com.smartse.papazon_dash.ui.screens.main.MainSlaveScreen
import com.smartse.papazon_dash.ui.screens.pairing.PairingInviteScreen
import com.smartse.papazon_dash.ui.screens.pairing.PairingJoinScreen
import com.smartse.papazon_dash.ui.screens.pairing.PairingScreen
import com.smartse.papazon_dash.ui.screens.settings.SettingsScreen
import com.smartse.papazon_dash.ui.screens.signin.SignInScreen
import com.smartse.papazon_dash.ui.screens.splash.SplashScreen
import com.smartse.papazon_dash.ui.state.AppViewModel

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object SignIn : Screen("signin")
    object Pairing : Screen("pairing")
    object PairingInvite : Screen("pairing/invite")
    object PairingJoin : Screen("pairing/join")
    object MainMaster : Screen("main_master")
    object MainSlave : Screen("main_slave")
    object History : Screen("history")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onSignedIn = { navController.navigate(Screen.MainMaster.route) { popUpTo(0) } },
                onNotSignedIn = { navController.navigate(Screen.SignIn.route) { popUpTo(0) } },
            )
        }

        composable(Screen.SignIn.route) {
            SignInScreen(
                onSignInSuccess = {
                    navController.navigate(Screen.Pairing.route) {
                        popUpTo(Screen.SignIn.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Pairing.route) {
            PairingScreen(
                onGenerateCode = { navController.navigate(Screen.PairingInvite.route) },
                onEnterCode = { navController.navigate(Screen.PairingJoin.route) },
            )
        }

        composable(Screen.PairingInvite.route) {
            PairingInviteScreen(
                onPairingComplete = {
                    navController.navigate(Screen.MainMaster.route) {
                        popUpTo(Screen.Pairing.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.PairingJoin.route) {
            PairingJoinScreen(
                onJoinSuccess = {
                    navController.navigate(Screen.MainSlave.route) {
                        popUpTo(Screen.Pairing.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.MainMaster.route) {
            MainScreen(
                onNavigateHistory = { navController.navigate(Screen.History.route) },
                onNavigateSettings = { navController.navigate(Screen.Settings.route) },
            )
        }

        composable(Screen.MainSlave.route) {
            MainSlaveScreen(
                onNavigateSettings = { navController.navigate(Screen.Settings.route) },
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            val appViewModel: AppViewModel = hiltViewModel()
            val currentUser by appViewModel.currentUser.collectAsState()
            SettingsScreen(
                userRole = currentUser?.role,
                onBack = { navController.popBackStack() },
                onUnpair = {
                    navController.navigate(Screen.Pairing.route) {
                        popUpTo(0)
                    }
                },
                onSignOut = {
                    navController.navigate(Screen.SignIn.route) {
                        popUpTo(0)
                    }
                },
            )
        }
    }
}
