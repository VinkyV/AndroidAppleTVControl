package dev.vinkyv.tvcontrol.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vinkyv.tvcontrol.utils.PairedDevice
import dev.vinkyv.tvcontrol.session.ConnectionState
import dev.vinkyv.tvcontrol.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val connection by viewModel.connection.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    var deviceToForget by remember { mutableStateOf<PairedDevice?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader("Connection")
            when (val state = connection) {
                is ConnectionState.Connected -> ListItem(
                    headlineContent = { Text(state.device.name.ifEmpty { "Apple TV" }) },
                    supportingContent = {
                        Text(
                            if (state.hasMetadata) "Connected · ${state.device.ip}"
                            else "Connected · ${state.device.ip} · no AirPlay (now playing unavailable)"
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = viewModel::disconnect) {
                            Icon(Icons.Default.LinkOff, contentDescription = "Disconnect")
                        }
                    }
                )
                is ConnectionState.Connecting -> ListItem(
                    headlineContent = { Text(state.deviceName) },
                    supportingContent = { Text("Connecting…") }
                )
                ConnectionState.Disconnected -> ListItem(
                    headlineContent = { Text("Not connected") },
                    supportingContent = { Text("Pick an Apple TV on the Remote tab") }
                )
            }
            HorizontalDivider()
            SectionHeader("Paired devices")
            if (pairedDevices.isEmpty()) {
                ListItem(headlineContent = { Text("No paired devices") })
            }
        }
        items(pairedDevices, key = { it.uuid }) { device ->
            ListItem(
                headlineContent = { Text(device.name.ifEmpty { "Apple TV" }) },
                supportingContent = {
                    val protocols = device.credentials.filterValues { it.isNotEmpty() }.keys.joinToString(", ")
                    Text(listOf(device.ip, protocols).filter { it.isNotEmpty() }.joinToString(" · "))
                },
                trailingContent = {
                    IconButton(onClick = { deviceToForget = device }) {
                        Icon(Icons.Default.Delete, contentDescription = "Forget device")
                    }
                }
            )
        }
    }

    deviceToForget?.let { device ->
        AlertDialog(
            onDismissRequest = { deviceToForget = null },
            title = { Text("Forget ${device.name.ifEmpty { "Apple TV" }}?") },
            text = {
                Text(
                    "Stored credentials will be deleted and you'll need to pair again. " +
                        "You may also want to remove this phone in the Apple TV's Remotes and Devices settings."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.forgetDevice(device)
                    deviceToForget = null
                }) { Text("Forget") }
            },
            dismissButton = {
                TextButton(onClick = { deviceToForget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}
