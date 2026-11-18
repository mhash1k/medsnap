package com.pharmabill.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object FlagColors {
    val green = Color(0xFF2E7D32)
    val yellow = Color(0xFFF9A825)
    val red = Color(0xFFC62828)
}

private val colors = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF455A64),
    secondaryContainer = Color(0xFFCFD8DC),
    tertiary = Color(0xFF6D4C41),
    error = FlagColors.red,
    background = Color(0xFFF7F9F9),
    surface = Color.White,
)

private val base = Typography()
private val type = base.copy(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp),
    labelLarge = base.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

val BigNumber = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold)

@Composable
fun PharmaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
