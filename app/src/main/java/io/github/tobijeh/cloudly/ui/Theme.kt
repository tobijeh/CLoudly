package io.github.tobijeh.cloudly.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.tobijeh.cloudly.R
import io.github.tobijeh.cloudly.core.Script
import io.github.tobijeh.cloudly.core.Scripts

val CloudBackground = Color(0xFF0E1014)

/** Soft colours for words. A word keeps its colour for life (picked from its id) so transitions stay calm. */
private val SoftPalette = listOf(
    Color(0xFFF2E8CF), // cream
    Color(0xFFA8DADC), // sea glass
    Color(0xFFF4B6B6), // rose
    Color(0xFFCDB4DB), // lavender
    Color(0xFFBFD8A8), // sage
    Color(0xFFFFD6A5), // peach
    Color(0xFFA9BCFF), // periwinkle
    Color(0xFFE9D18A), // soft gold
)

fun wordColor(id: Long): Color = SoftPalette[((id * 7919L) % SoftPalette.size).toInt().let { if (it < 0) -it else it }]

@OptIn(ExperimentalTextApi::class)
object CloudFonts {
    val fraunces = FontFamily(
        Font(
            R.font.fraunces, FontWeight.Medium,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(500),
                FontVariation.Setting("opsz", 72f),
                FontVariation.Setting("SOFT", 50f),
            ),
        ),
    )
    val frankRuhlLibre = FontFamily(
        Font(R.font.frank_ruhl_libre, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    )
    val notoSerifTc = FontFamily(
        Font(R.font.noto_serif_tc, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    )

    fun forText(text: String): FontFamily = when (Scripts.detect(text)) {
        Script.HEBREW -> frankRuhlLibre
        Script.CHINESE -> notoSerifTc
        Script.LATIN -> fraunces
    }
}

private val Colors = darkColorScheme(
    primary = Color(0xFFF2E8CF),
    onPrimary = Color(0xFF1B1D22),
    secondary = Color(0xFFA8DADC),
    onSecondary = Color(0xFF0E1014),
    tertiary = Color(0xFFF4B6B6),
    background = CloudBackground,
    onBackground = Color(0xFFE8E6E1),
    surface = Color(0xFF15181E),
    onSurface = Color(0xFFE8E6E1),
    surfaceVariant = Color(0xFF1F232B),
    onSurfaceVariant = Color(0xFFB4B2AD),
    surfaceContainer = Color(0xFF1A1D24),
    surfaceContainerHigh = Color(0xFF20242C),
    surfaceContainerHighest = Color(0xFF262A33),
    outline = Color(0xFF3A3F4A),
)

@Composable
fun CloudlyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
