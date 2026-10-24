package com.kappi.crunch

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.kappi.crunch.ui.downloads.DownloadsScreen
import com.kappi.crunch.ui.home.HomeScreen
import com.kappi.crunch.ui.settings.SettingsScreen

@Composable
fun MainNavigation(
    sharedUrl: String? = null,
    onSharedUrlConsumed: (() -> Unit)? = null,
    openUpdateDialog: Boolean = false,
    onOpenUpdateDialogConsumed: (() -> Unit)? = null,
) {
    val backStack = rememberNavBackStack(HomeRoute)

    // Automatically navigate to Home when a shared URL arrives
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            if (backStack.lastOrNull() !is HomeRoute) {
                backStack.clear()
                backStack.add(HomeRoute)
            }
        }
    }

    // Automatically navigate to Settings when openUpdateDialog is requested
    LaunchedEffect(openUpdateDialog) {
        if (openUpdateDialog) {
            if (backStack.lastOrNull() !is SettingsRoute) {
                backStack.clear()
                backStack.add(SettingsRoute)
            }
            onOpenUpdateDialogConsumed?.invoke()
        }
    }

    val currentRoute = backStack.lastOrNull()
    val selectedIndex = when (currentRoute) {
        is HomeRoute -> 0
        is DownloadsRoute -> 1
        is SettingsRoute -> 2
        else -> 0
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedIndex == 0,
                    onClick = {
                        if (selectedIndex != 0) {
                            backStack.clear()
                            backStack.add(HomeRoute)
                        }
                    },
                    icon = {
                        Icon(
                            if (selectedIndex == 0) Icons.Filled.Download else Icons.Outlined.Download,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.nav_download)) },
                )
                NavigationBarItem(
                    selected = selectedIndex == 1,
                    onClick = {
                        if (selectedIndex != 1) {
                            backStack.clear()
                            backStack.add(DownloadsRoute)
                        }
                    },
                    icon = {
                        Icon(
                            if (selectedIndex == 1) Icons.Filled.FolderOpen else Icons.Outlined.FolderOpen,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.nav_downloads)) },
                )
                NavigationBarItem(
                    selected = selectedIndex == 2,
                    onClick = {
                        if (selectedIndex != 2) {
                            backStack.clear()
                            backStack.add(SettingsRoute)
                        }
                    },
                    icon = {
                        Icon(
                            if (selectedIndex == 2) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.nav_settings)) },
                )
            }
        }
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<HomeRoute> {
                    HomeScreen(
                        sharedUrl = sharedUrl,
                        onSharedUrlConsumed = onSharedUrlConsumed,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
                entry<DownloadsRoute> {
                    DownloadsScreen(modifier = Modifier.padding(innerPadding))
                }
                entry<SettingsRoute> {
                    SettingsScreen(modifier = Modifier.padding(innerPadding))
                }
            },
        )
    }
}
