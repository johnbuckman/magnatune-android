package com.magnatune.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A scrollable, dismissible bottom-sheet with a title — the in-app About info pages (mirrors the
 *  iOS InfoSheet). Replaces the old external browser links in Settings → About. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** Bullet point row used in the "Why we are not evil" sheet. */
@Composable
fun InfoBullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text("•", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** "Why we are not evil" points — verbatim from the iOS app (magnatune.com/info/whynotevil). */
val whyNotEvilPoints: List<String> = listOf(
    "Fantastic music: we work with artists directly, not with record labels, and all our music is hand-picked. On average, we accept 3% of submissions.",
    "Perfect audio quality: our music is available in both lossless and smaller-size \"lossy\" formats.",
    "Many audio formats: albums download as WAV, super-high-quality VBR MP3, AAC, Apple Lossless (ALAC), and open-source-friendly FLAC, OGG and OPUS.",
    "Listen to everything: all our albums can be heard in their entirety before you buy.",
    "Download everything: once you've paid, download anything from our entire catalog — no limits.",
    "Musicians get paid: 50% of your purchase price goes directly to the musician, not to labels and their lawyers.",
    "Name your price: you choose how much to pay, and 50% of your choice goes to the artist.",
    "Give to your friends: we encourage you to give 3 copies of any music from your membership to your friends.",
    "Creative Commons: our 128k MP3s are some-rights-reserved Creative Commons licensed.",
    "Remix friendly: tons of our music, acapellas and samples are available for remixing at CC Mixter.",
    "100% legal: Magnatune is completely legal all over the world.",
    "Licensing: all our music can be licensed for commercial use instantly and online, with no additional permissions.",
    "Artists direct: we sign contracts directly with musicians — no middlemen in the way of the artist's royalties.",
    "Podcast-legal: non-commercial podcasters can use our music for free.",
    "No major labels: we have nothing to do with major labels, their lawyers or the RIAA.",
    "Supports Open Source: we financially support open source projects such as Amarok and Rhythmbox.",
    "No DRM: no copy protection, so you can do what you like with your music.",
)

/** Founder's rant — verbatim from the iOS app (magnatune.com/info/why). */
val foundersRantText: String = """
by John Buckman, founder/owner

Magnatune was born out of some observations I'd gathered about the music industry, along with personal experiences from my wife releasing her CD on an Indie record label.

Personal experience:

When my wife was signed to an Indie record label, we were really excited. In the end, she sold close to 1000 CDs, lost all rights to her music for 8 years (even though the CD had been out of print for several years), and earned a little over ${'$'}100 in royalties (no one is really sure), some of which was paid to her as CD copies of her own CD which she then gave away for promotion.

The record label that signed her wasn't evil: they were one of the good guys, and gave her a 70/30 split of the profits (of which there were few). The label got screwed at every turn: distributors refused to carry their CDs unless they spent thousands on useless print ads, or they didn't pay for the CDs sold, etc. In general, all forces colluded to prevent this small, progressive label from succeeding.

She was one of the lucky ones. We knew several musicians, signed to various labels, who were also frustrated, who received no money ever and who lost the rights to their music forever.

Industry observations:

Radio is boring: everyone I know is into interesting music, yet good music is rarely played on the air. I'm into everything from Ambient, Industrial, Goth and Metal to Renaissance, Baroque, Tango, Indian Classical and New Age — and so are many of my friends. Yet these genres are barely visible in record stores, and totally absent from the airwaves.

CDs cost too much, and artists only get 20 cents to a dollar for each CD sold — if they're lucky. And most CDs quickly go out of print.

Online sales often cost the artist 50% of their already-pathetic royalty (due to a common record-contract provision). International sales and mark-downs often net the artist no royalties.

Record labels lock their artists into legal agreements that hold them for a decade or more. If it's not working out, labels don't print the band's recordings but nonetheless keep them locked into the contract. Even hugely successful artists often end up owing their record label money.

Peer-to-peer software has proven there's a huge, continuing demand for music, and people want to share it. Clearly there's a huge public demand for Open Music.

Using the Internet to listen to music is usually tedious: too many ads, too many clicks, and the sound quality is usually bad. A well-run Internet radio station solves that, but the entrenched record industry wants to kill that too, using extremely high fees as the weapon.

My solution:

I thought: why not make a record label that has a clue? One that helps artists get exposure, make at least as much money as they would with traditional labels, and helps them get fans and concerts.

Magnatune is my project. The goal is to find a way to run a record label in the Internet reality: file trading, Internet radio, musicians' rights, the whole nine yards.

If you think Magnatune is a worthy goal, please support it. There are powerful forces who want it to fail, so I need your help if this is going to work.

Magnatune was founded in April 2003, and is located in the People's Republic of Berkeley, California.
""".trimIndent()
