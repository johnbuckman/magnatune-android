package com.magnatune.player.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.magnatune.player.R

/** Font Awesome 7 Free (solid) as a Compose font family. */
val FaSolid = FontFamily(Font(R.font.fa_solid_900))

/** Font Awesome 7 glyph codepoints used in the app (solid set). */
object Fa {
    // Navigation
    const val star = ""            // Popular
    const val user = ""            // Artists
    const val compactDisc = ""     // Albums
    const val guitar = ""          // Genres (tab) / Alt Rock
    const val tag = ""             // Tags
    const val rectangleList = ""   // Featured
    const val magnifyingGlass = "" // Search
    const val heart = ""           // Favorites
    const val listUl = ""          // Playlists
    const val gear = ""            // Settings

    // Transport / player
    const val play = ""
    const val pause = ""
    const val next = ""            // forward-step
    const val prev = ""            // backward-step
    const val shuffle = ""
    const val repeat = "\uF363"     // fa-repeat
    const val penToSquare = "\uF044" // fa-pen-to-square (rename / edit)
    const val volumeHigh = ""
    const val volumeLow = ""
    const val volumeOff = ""       // volume-xmark
    const val xmark = ""
    const val check = ""
    const val trash = ""           // trash-can
    const val chevronLeft = ""
    const val music = ""

    // Genre icons
    const val moon = ""
    const val waveSquare = ""
    const val globe = ""
    const val wind = ""
    const val microphone = ""
    const val bolt = ""
    const val fire = ""
}

/**
 * Renders a Font Awesome glyph as an icon. [size] sets the glyph height; tint defaults to the
 * ambient content color.
 */
@Composable
fun FaIcon(
    glyph: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 20.dp,
) {
    Text(
        text = glyph,
        fontFamily = FaSolid,
        color = tint,
        fontSize = with(LocalDensity.current) { size.toSp() },
        textAlign = TextAlign.Center,
        modifier = modifier.semantics { if (contentDescription != null) this.contentDescription = contentDescription },
    )
}
