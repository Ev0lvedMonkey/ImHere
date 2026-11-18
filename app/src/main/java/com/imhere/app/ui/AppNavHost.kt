package com.imhere.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.imhere.app.ui.contacts.ContactsScreen
import com.imhere.app.ui.main.MainScreen
import com.imhere.app.ui.settings.SettingsScreen

object Routes {
    const val MAIN = "main"
    const val CONTACTS = "contacts"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost() {
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
