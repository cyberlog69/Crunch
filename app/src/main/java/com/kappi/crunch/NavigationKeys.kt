package com.kappi.crunch

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data object DownloadsRoute : NavKey

@Serializable
data object SettingsRoute : NavKey
