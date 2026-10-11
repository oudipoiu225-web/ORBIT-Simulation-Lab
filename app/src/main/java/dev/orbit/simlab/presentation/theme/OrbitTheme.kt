package dev.orbit.simlab.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Aqua = Color(0xFF31D9CA)
private val Dark = darkColorScheme(primary = Aqua, secondary = Color(0xFFFFC05A),
    background = Color(0xFF10191E), surface = Color(0xFF18252B),
    error = Color(0xFFFF7979), onBackground = Color(0xFFE4F3F2))
private val Light = lightColorScheme(primary = Color(0xFF006D65), secondary = Color(0xFF825000),
    background = Color(0xFFF5FAF9), surface = Color.White)

@Composable
fun OrbitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
