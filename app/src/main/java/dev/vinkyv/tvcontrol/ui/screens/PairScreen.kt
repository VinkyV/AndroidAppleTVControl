package dev.vinkyv.tvcontrol.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Airplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.vinkyv.tvcontrol.AtvController
import dev.vinkyv.tvcontrol.utils.DeviceStorage
import dev.vinkyv.tvcontrol.utils.DiscoveredDevice
import dev.vinkyv.tvcontrol.utils.Discovery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun PinSubmitDialog(
    onDismissRequest: () -> Unit,
    onPinSubmitted: (String) -> Unit
) {
    var pinValue by remember { mutableStateOf("") }
    val maxPinLength = 4

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Enter PIN Code") },
        text = {
            TextField(
                value = pinValue,
                onValueChange = { newValue ->
                    // Prevent entry if it exceeds max length or contains non-digits
                    if (newValue.length <= maxPinLength && newValue.all { it.isDigit() }) {
                        pinValue = newValue
                    }
                },
                label = { Text("4-Digit PIN") },
                singleLine = true,
                // Force numeric keyboard view
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                // Mask the input digits like a password (••••)
                visualTransformation = PasswordVisualTransformation()
            )
        },
        confirmButton = {
            TextButton(
                // Only enable confirm button when the required PIN length is met
                enabled = pinValue.length == maxPinLength,
                onClick = { onPinSubmitted(pinValue) }
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PairScreen(atvController: AtvController) {
    val context = LocalContext.current
    val devices = remember {
        mutableStateListOf<DiscoveredDevice>()
    }
    var targetDevice by remember { mutableStateOf(DiscoveredDevice()) }
    var pinRequired by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
            Discovery().discover(context) { result ->
                devices.clear()
                devices.addAll(result)
            }
        }
        ) {
            Text("Discover")
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(devices) { device ->
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (DeviceStorage.hasCredential(context, device.uuid)) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                    onClick = {
                        if (DeviceStorage.hasCredential(context, device.uuid)) {
                            atvController.connect(device.ip, "Companion", DeviceStorage.getCredential(context, device.uuid), { isOk, _ ->
                                if (isOk) {
                                    targetDevice = device
                                } else {
                                    // TODO: Show snackbar that something go wrong or device offline
                                }
                            })
                        } else {
                            atvController.pair(device.ip, { isOk, _ ->
                                if (isOk) {
                                    targetDevice = device
                                    // Show dialog to enter pairing pin code
                                    pinRequired = true
                                } else {
                                    // TODO: Show snackbar that something go wrong
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .wrapContentSize(Alignment.CenterStart)
                                .padding(8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Icon(Icons.Filled.Airplay, contentDescription = "")
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(device.name, fontWeight = FontWeight.Bold)
                            Text("${device.ip}:${device.port}")
                            Text(device.uuid)
                        }
                    }
                }
            }
        }
        if (pinRequired) {
            PinSubmitDialog(
                onDismissRequest = {
                    pinRequired = false
                },
                onPinSubmitted = { pinCode ->
                    atvController.submitPin(pinCode, { isOk, message ->
                        if (isOk) {
                            // Hide dialog
                            pinRequired = false
                            val deserializedElement: JsonElement = Json.parseToJsonElement(message)
                            val credentials = deserializedElement.jsonObject["credentials"]?.jsonPrimitive?.content.toString()
                            DeviceStorage.saveCredential(context, targetDevice.uuid, credentials)
                            atvController.connect(targetDevice.ip, "Companion", credentials, { isOk2, message2 ->
                                if (isOk2) {
                                    // TODO: Show snackbar device connected succesfully
                                } else {
                                    // TODO: Show snackbar that something go wrong or device is offline
                                }
                            })
                        } else {
                            // TODO: Show snackbar that something go wrong or incorrect pin
                        }
                    })
                }
            )
        }
    }
}