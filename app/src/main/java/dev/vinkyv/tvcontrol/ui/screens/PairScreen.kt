package dev.vinkyv.tvcontrol.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Airplay
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.vinkyv.tvcontrol.utils.PROTOCOL_AIRPLAY
import dev.vinkyv.tvcontrol.utils.PROTOCOL_COMPANION
import dev.vinkyv.tvcontrol.viewmodel.MainViewModel
import dev.vinkyv.tvcontrol.viewmodel.PinRequest

private const val PIN_LENGTH = 4

@Composable
fun PinSubmitDialog(
    request: PinRequest,
    onDismissRequest: () -> Unit,
    onPinSubmitted: (String) -> Unit
) {
    var pinValue by remember(request) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val submit: () -> Unit = { if (pinValue.length == PIN_LENGTH) onPinSubmitted(pinValue) }

    LaunchedEffect(request) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Enter PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (request.protocol == PROTOCOL_AIRPLAY) {
                        "Enter the PIN shown on ${request.deviceName} to enable now playing info, artwork and seeking."
                    } else {
                        "Enter the PIN shown on ${request.deviceName}."
                    }
                )
                OutlinedTextField(
                    value = pinValue,
                    onValueChange = { newValue ->
                        if (newValue.length <= PIN_LENGTH && newValue.all { it.isDigit() }) {
                            pinValue = newValue
                        }
                    },
                    label = { Text("$PIN_LENGTH-digit PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = pinValue.length == PIN_LENGTH, onClick = submit) {
                Text("Pair")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(if (request.protocol == PROTOCOL_AIRPLAY) "Skip" else "Cancel")
            }
        }
    )
}

@Composable
fun PairScreen(viewModel: MainViewModel) {
    val devices by viewModel.devices.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val busyStatus by viewModel.busyStatus.collectAsState()
    val isBusy = busyStatus != null

    DisposableEffect(viewModel) {
        viewModel.startDiscovery()
        onDispose { viewModel.stopDiscovery() }
    }

    // Paired devices that weren't found by discovery can still be tried at their last address
    val discoveredIds = devices.map { it.uuid }.toSet()
    val offlinePaired = pairedDevices.filter { it.uuid !in discoveredIds && it.ip.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !isDiscovering,
            onClick = viewModel::startDiscovery
        ) {
            if (isDiscovering) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Searching…")
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Search again")
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (devices.isEmpty() && !isDiscovering) {
                item {
                    Text(
                        "No Apple TVs found. Make sure this phone is on the same Wi-Fi network.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(devices, key = { it.uuid }) { device ->
                val paired = pairedDevices.any { it.uuid == device.uuid && it.credentials[PROTOCOL_COMPANION] != null }
                DeviceCard(
                    icon = Icons.Filled.Airplay,
                    name = device.name,
                    subtitle = if (paired) "Paired · ${device.ip}" else "Tap to pair · ${device.ip}",
                    highlighted = paired,
                    enabled = !isBusy,
                    onClick = { viewModel.selectDevice(device) }
                )
            }
            if (offlinePaired.isNotEmpty()) {
                item {
                    Text(
                        "Paired devices",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(offlinePaired, key = { "paired-${it.uuid}" }) { device ->
                    DeviceCard(
                        icon = Icons.Filled.Link,
                        name = device.name.ifEmpty { "Apple TV" },
                        subtitle = "Last seen at ${device.ip}",
                        highlighted = false,
                        enabled = !isBusy,
                        onClick = { viewModel.connectPaired(device) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    icon: ImageVector,
    name: String,
    subtitle: String,
    highlighted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(name, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
