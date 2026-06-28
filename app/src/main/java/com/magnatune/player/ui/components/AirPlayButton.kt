package com.magnatune.player.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magnatune.player.R
import com.magnatune.player.ui.MagnatuneViewModel
import com.magnatune.player.ui.theme.MagAccent
import com.magnatune.player.ui.theme.MagSecondary

/**
 * AirPlay control: an AirPlay glyph, with the chosen device's name shown to its left in a small
 * rounded pill. The pill and the glyph both open the device picker (which pre-selects the current
 * device). Hidden entirely when no AirPlay receiver is on the network and none is selected.
 *
 * NOTE: selection is UI state only — RAOP audio streaming is not yet implemented.
 */
@Composable
fun AirPlayButton(vm: MagnatuneViewModel, modifier: Modifier = Modifier) {
    val devices by vm.container.airplay.devices.collectAsStateWithLifecycle()
    val selected by vm.container.airplay.selected.collectAsStateWithLifecycle()
    if (devices.isEmpty() && selected == null) return
    var show by remember { mutableStateOf(false) }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        selected?.let { sel ->
            Surface(
                shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.background,
                modifier = Modifier.border(1.dp, MagAccent, RoundedCornerShape(10.dp)).clickable { show = true },
            ) {
                Text(sel.name, style = MaterialTheme.typography.labelSmall, color = MagAccent,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            painterResource(R.drawable.ic_airplay), contentDescription = "AirPlay",
            tint = if (selected != null) MagAccent else MagSecondary,
            modifier = Modifier.size(22.dp).clickable { show = true },
        )
    }

    if (show) {
        AirPlayPicker(
            devices = devices,
            selected = selected,
            onSelect = { vm.container.airplay.select(it); show = false },
            onDismiss = { show = false },
        )
    }
}

@Composable
private fun AirPlayPicker(
    devices: List<com.magnatune.player.peer.AirPlayDiscovery.Device>,
    selected: com.magnatune.player.peer.AirPlayDiscovery.Device?,
    onSelect: (com.magnatune.player.peer.AirPlayDiscovery.Device?) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text("AirPlay", style = MaterialTheme.typography.titleMedium)
                // Off / "This device" option to clear the selection.
                AirPlayRow("This device", selected == null) { onSelect(null) }
                HorizontalDivider()
                devices.forEach { d ->
                    AirPlayRow(d.name, selected?.id == d.id) { onSelect(d) }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AirPlayRow(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) MagAccent else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (isSelected) FaIcon(Fa.check, "Selected", tint = MagAccent, size = 18.dp)
    }
}
