package com.imhere.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.imhere.app.AppGraph
import com.imhere.app.ui.contacts.ContactsScreen
import com.imhere.app.ui.main.MainScreen
import com.imhere.app.ui.onboarding.OnboardingScreen
import com.imhere.app.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

object Routes {
    const val MAIN = "main"
    const val CONTACTS = "contacts"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost() {
    val onboarded by AppGraph.settings.onboardingState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    when (onboarded) {
        // DataStore has not emitted yet: show neither the first-run screen nor
        // the main screen, so existing users never see onboarding flash.
        null -> LoadingScreen()
        false -> OnboardingScreen(
            onFinish = { scope.launch { AppGraph.settings.completeOnboarding() } },
        )

        true -> MainNavHost()
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MainNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                onOpenContacts = { navController.navigate(Routes.CONTACTS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.CONTACTS) {
            ContactsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
