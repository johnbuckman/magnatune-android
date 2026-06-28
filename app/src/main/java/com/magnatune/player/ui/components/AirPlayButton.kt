package com.magnatune.player.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magnatune.player.R
import com.magnatune.player.ui.MagnatuneViewModel
import com.magnatune.player.ui.theme.MagSecondary

/**
 * AirPlay icon — visible only when at least one AirPlay receiver is on the network. Tapping it opens
 * a device picker. (Audio streaming over RAOP is not yet wired; selecting a device explains that.)
 */
@Composable
fun AirPlayButton(vm: MagnatuneViewModel, modifier: Modifier = Modifier) {
    val devices by vm.container.airplay.devices.collectAsStateWithLifecycle()
    if (devices.isEmpty()) return
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }

    Icon(
        painterResource(R.drawable.ic_airplay), contentDescription = "AirPlay",
        tint = MagSecondary, modifier = modifier.clickable { show = true },
    )
    if (show) {
        Dialog(onDismissRequest = { show = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.padding(20.dp).fillMaxWidth()) {
                    Text("AirPlay", style = MaterialTheme.typography.titleMedium)
                    devices.forEach { d ->
                        Text(d.name, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable {
                                Toast.makeText(context,
                                    "AirPlay streaming to ${d.name} is coming soon.",
                                    Toast.LENGTH_SHORT).show()
                                show = false
                            }.padding(vertical = 12.dp))
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
