package dev.vinkyv.tvcontrol.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vinkyv.tvcontrol.AtvController
import dev.vinkyv.tvcontrol.ui.icons.play_pause

@Composable
fun RemoteScreen(atvController: AtvController) {
    Column {
        Card {}
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularButton(
                    modifier = Modifier.weight(1f),
                    atvController = atvController,
                    commandOnClick = "menu",
                    icon = {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "back")
                    }
                )
                CircularButton(
                    modifier = Modifier.weight(1f),
                    atvController = atvController,
                    commandOnClick = "play_pause",
                    icon = {
                        Icon(play_pause, contentDescription = "play pause")
                    }
                )
                CircularButton(
                    modifier = Modifier.weight(1f),
                    atvController = atvController,
                    commandOnClick = "mute",
                    icon = {
                        Icon(Icons.AutoMirrored.Default.VolumeOff, contentDescription = "mute")
                    }
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularButton(
                    modifier = Modifier.weight(1f),
                    atvController = atvController,
                    commandOnClick = "home",
                    icon = {
                        Icon(Icons.Default.Tv, contentDescription = "home")
                    }
                )
                CircularButton(
                    modifier = Modifier.weight(1f),
                    atvController = atvController,
                    commandOnClick = "volume_up",
                    icon = {
                        Icon(Icons.AutoMirrored.Default.VolumeUp, contentDescription = "volume up")
                    }
                )
                CircularButton(
                    modifier = Modifier.weight(1f),
                    atvController = atvController,
                    commandOnClick = "volume_down",
                    icon = {
                        Icon(Icons.AutoMirrored.Default.VolumeDown, contentDescription = "volume down")
                    },
                )
            }
        }
    }
}

@Composable
private fun CircularButton(
    modifier: Modifier = Modifier,
    atvController: AtvController,
    commandOnClick: String,
    icon: @Composable () -> Unit
) {
    Button(
        onClick = {
            atvController.sendCommand(commandOnClick, { isOk, _ ->
                if (!isOk) {
                    // TODO: Make device not online and goto PairScreen
                }
            })
        },
        shape = CircleShape,
        modifier = Modifier.aspectRatio(1f).fillMaxHeight()
    ) {
        icon()
    }
}