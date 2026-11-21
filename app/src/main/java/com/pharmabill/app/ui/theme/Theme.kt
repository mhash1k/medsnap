package com.pharmabill.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette taken from the reference prototype (Google-blue, soft surfaces). */
object Pb {
    val bg = Color(0xFFF7F9FC)
    val surface = Color.White
    val surface2 = Color(0xFFF0F4F9)
    val surface3 = Color(0xFFE8EEF6)
    val ink = Color(0xFF1B1F24)
    val muted = Color(0xFF667085)
    val line = Color(0xFFE4E8EF)
    val lineSoft = Color(0xFFEEF1F5)
    val primary = Color(0xFF1967D2)
    val primaryDark = Color(0xFF1557B0)
    val primarySoft = Color(0xFFE8F0FE)
    val green = Color(0xFF18865B)
    val greenSoft = Color(0xFFE6F4EA)
    val yellow = Color(0xFF9A6700)
    val yellowSoft = Color(0xFFFFF4D6)
    val red = Color(0xFFC5221F)
    val redSoft = Color(0xFFFCE8E6)
    val iconInk = Color(0xFF475467)
    val tonalInk = Color(0xFF344054)
    val medIcon = Color(0xFF4777BD)
    val medIconBg = Color(0xFFF1F5FB)
    val voiceBg = Color(0xFFEAF2FF)
    val voiceBorder = Color(0xFFC9DCFB)
    val voiceInk = Color(0xFF174EA6)
    val voiceSub = Color(0xFF536F99)
    val voiceAction = Color(0xFFD4E4FF)
    val infoBg = Color(0xFFF0F6FF)
    val infoBorder = Color(0xFFD8E7FB)
    val warnBg = Color(0xFFFFF8E7)
    val warnBorder = Color(0xFFF5E4BB)
    val navInactive = Color(0xFF737B89)
}

object FlagColors {
    val green = Pb.green
    val yellow = Color(0xFFF2B01E)
    val red = Pb.red
}

private val colors = lightColorScheme(
    primary = Pb.primary,
    onPrimary = Color.White,
    primaryContainer = Pb.primarySoft,
    onPrimaryContainer = Pb.primary,
    secondary = Pb.tonalInk,
    onSecondary = Color.White,
    secondaryContainer = Pb.primarySoft,
    onSecondaryContainer = Pb.primary,
    tertiary = Pb.green,
    tertiaryContainer = Pb.greenSoft,
    onTertiaryContainer = Pb.green,
    error = Pb.red,
    errorContainer = Pb.redSoft,
    onErrorContainer = Pb.red,
    background = Pb.bg,
    onBackground = Pb.ink,
    surface = Pb.surface,
    onSurface = Pb.ink,
    surfaceVariant = Pb.surface2,
    onSurfaceVariant = Pb.muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    surfaceTint = Pb.primary,
    outline = Pb.line,
    outlineVariant = Pb.lineSoft,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val base = Typography()
private val type = base.copy(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = base.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleMedium = base.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.15).sp),
    titleSmall = base.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.sp, color = Color.Unspecified),
    labelLarge = base.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
)

val BigNumber = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)

@Composable
fun PharmaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, shapes = shapes, content = content)
}
