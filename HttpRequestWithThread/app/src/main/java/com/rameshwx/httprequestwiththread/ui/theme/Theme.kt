package com.rameshwx.httprequestwiththread.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1E704B)
private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    secondary = Color(0xFF48745D),
    background = Color(0xFFF6F7F2),
    surface = Color.White,
    error = Color(0xFFB3261E)
)

@Composable
fun GroceryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
