package com.everett.motionextractor.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Editor : Screen("editor")
    data object Result : Screen("result")
}
