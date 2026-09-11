package ir.chidari.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ir.chidari.R

/** فونت فارسی وزیرمتن برای کل برنامه. */
val Vazir = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

private val Teal = Color(0xFF00695C)
private val TealLight = Color(0xFF4DB6AC)
private val Amber = Color(0xFFFFA000)
private val Sand = Color(0xFFF8F5F2)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00251F),
    secondary = Amber,
    onSecondary = Color(0xFF3E2600),
    secondaryContainer = Color(0xFFFFE0B2),
    onSecondaryContainer = Color(0xFF3E2600),
    tertiary = Color(0xFF7B5EA7),
    background = Sand,
    onBackground = Color(0xFF1B1B1B),
    surface = Color.White,
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFFEFEAE5),
    onSurfaceVariant = Color(0xFF4A4741),
    outline = Color(0xFFBDB7B0),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF00332C),
    primaryContainer = Color(0xFF005045),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFFFFCC80),
    onSecondary = Color(0xFF422C00),
    background = Color(0xFF14171A),
    onBackground = Color(0xFFE6E1DC),
    surface = Color(0xFF1D2126),
    onSurface = Color(0xFFE6E1DC),
    surfaceVariant = Color(0xFF2A2E33),
    onSurfaceVariant = Color(0xFFCAC5BF),
    outline = Color(0xFF57606A)
)

private fun typography(scale: Float = 1f): Typography {
    val base = Typography()
    fun TextStyle.fa() = copy(
        fontFamily = Vazir,
        fontSize = fontSize * scale,
        lineHeight = fontSize * scale * 1.6f
    )
    return Typography(
        displayLarge = base.displayLarge.fa(),
        displayMedium = base.displayMedium.fa(),
        displaySmall = base.displaySmall.fa(),
        headlineLarge = base.headlineLarge.fa(),
        headlineMedium = base.headlineMedium.fa(),
        headlineSmall = base.headlineSmall.fa(),
        titleLarge = base.titleLarge.copy(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 19.sp * scale),
        titleMedium = base.titleMedium.copy(fontFamily = Vazir, fontSize = base.titleMedium.fontSize * scale, fontWeight = FontWeight.Medium),
        titleSmall = base.titleSmall.copy(fontFamily = Vazir, fontSize = base.titleSmall.fontSize * scale, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.fa(),
        bodyMedium = base.bodyMedium.fa(),
        bodySmall = base.bodySmall.fa(),
        labelLarge = base.labelLarge.copy(fontFamily = Vazir, fontSize = base.labelLarge.fontSize * scale, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = Vazir),
        labelSmall = base.labelSmall.copy(fontFamily = Vazir)
    )
}

@Composable
fun ChiDariTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = typography(fontScale),
        content = content
    )
}
