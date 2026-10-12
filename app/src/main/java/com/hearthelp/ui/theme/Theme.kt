package com.hearthelp.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hearthelp.R

/** "Night watch" palette: calm is the absence of colour; only the heartbeat and alarms are red. */
object Hh {
    val Night = Color(0xFF141B26)   // background
    val Dusk = Color(0xFF1E2735)    // raised surfaces, inputs
    val Mist = Color(0xFF8A97AA)    // secondary text
    val Moon = Color(0xFFE8EDF4)    // primary text
    val Pulse = Color(0xFFF0687A)   // the heartbeat; primary actions
    val Alarm = Color(0xFFC62839)   // alarm screen and SOS only
}

@OptIn(ExperimentalTextApi::class) // variable-font weights; stable on API 26+
private fun plex(weight: Int) = Font(
    R.font.ibm_plex_sans, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Plex = FontFamily(plex(300), plex(400), plex(500), plex(600))

object HhType {
    /** The live bpm. Tabular figures so the number doesn't jitter as it changes. */
    val Reading = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 96.sp,
        lineHeight = 100.sp, fontFeatureSettings = "tnum", color = Hh.Moon)
    val Title = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp)
    val Heading = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp)
    val Body = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val Small = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp,
        fontFeatureSettings = "tnum")
    val Label = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)
}

private val colors = darkColorScheme(
    primary = Hh.Pulse, onPrimary = Hh.Night,
    secondary = Hh.Mist, onSecondary = Hh.Night,
    background = Hh.Night, onBackground = Hh.Moon,
    surface = Hh.Night, onSurface = Hh.Moon,
    surfaceVariant = Hh.Dusk, onSurfaceVariant = Hh.Mist,
    surfaceContainerHigh = Hh.Dusk, surfaceContainer = Hh.Dusk,
    error = Hh.Pulse, onError = Hh.Night,
    outline = Hh.Mist,
)

private val typography = Typography(
    titleLarge = HhType.Title, titleMedium = HhType.Heading,
    bodyLarge = HhType.Body, bodyMedium = HhType.Body, bodySmall = HhType.Small,
    labelLarge = HhType.Label, labelMedium = HhType.Small,
)

@Composable
fun HeartHelpTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colors, typography = typography, content = content)

private val Pill = RoundedCornerShape(50)

/** The one filled button per screen. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) =
    Button(onClick, modifier.fillMaxWidth().height(56.dp), enabled = enabled, shape = Pill,
        colors = ButtonDefaults.buttonColors(containerColor = Hh.Pulse, contentColor = Hh.Night,
            disabledContainerColor = Hh.Dusk, disabledContentColor = Hh.Mist)) { Text(text, style = HhType.Label) }

@Composable
fun QuietButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) =
    OutlinedButton(onClick, modifier.height(48.dp), enabled = enabled, shape = Pill,
        border = BorderStroke(1.dp, if (enabled) Hh.Mist.copy(alpha = 0.5f) else Hh.Dusk),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Hh.Moon, disabledContentColor = Hh.Mist),
        contentPadding = PaddingValues(horizontal = 20.dp)) { Text(text, style = HhType.Label) }

@Composable
fun LinkButton(text: String, onClick: () -> Unit, color: Color = Hh.Mist, modifier: Modifier = Modifier) =
    TextButton(onClick, modifier, colors = ButtonDefaults.textButtonColors(contentColor = color)) {
        Text(text, style = HhType.Label)
    }

@Composable
fun hhFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Hh.Dusk, unfocusedContainerColor = Hh.Dusk, disabledContainerColor = Hh.Dusk,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
    focusedLabelColor = Hh.Pulse, unfocusedLabelColor = Hh.Mist, cursorColor = Hh.Pulse,
    focusedTextColor = Hh.Moon, unfocusedTextColor = Hh.Moon,
)

val FieldShape = RoundedCornerShape(12.dp)
