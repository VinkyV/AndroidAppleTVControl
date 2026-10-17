package dev.vinkyv.tvcontrol.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import dev.vinkyv.tvcontrol.ui.icons.play_pause
import dev.vinkyv.tvcontrol.viewmodel.MainViewModel

private const val PREFS_NAME = "settings"
private const val KEY_USE_TOUCHPAD = "remote_use_touchpad"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(viewModel: MainViewModel) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var useTouchpad by remember { mutableStateOf(prefs.getBoolean(KEY_USE_TOUCHPAD, true)) }
    val send: (String) -> Unit = { command ->
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        viewModel.sendCommand(command)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
        ) {
            listOf(true to "Touchpad", false to "D-pad").forEachIndexed { index, (touchpad, label) ->
                SegmentedButton(
                    selected = useTouchpad == touchpad,
                    onClick = {
                        useTouchpad = touchpad
                        prefs.edit().putBoolean(KEY_USE_TOUCHPAD, touchpad).apply()
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                ) {
                    Text(label)
                }
            }
        }

        if (useTouchpad) {
            Touchpad(
                onCommand = viewModel::sendCommand,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
        } else {
            DPad(
                onCommand = send,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.widthIn(max = 360.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularButton(Icons.Default.ArrowBackIosNew, "Back") { send("menu") }
                CircularButton(play_pause, "Play/Pause") { send("play_pause") }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularButton(Icons.Default.Tv, "Home") { send("home") }
                CircularButton(Icons.AutoMirrored.Default.VolumeDown, "Volume down") { send("volume_down") }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularButton(Icons.Default.Apps, "Control Center") { send("home_hold") }
                CircularButton(Icons.AutoMirrored.Default.VolumeUp, "Volume up") { send("volume_up") }
            }
        }
    }
}

@Composable
private fun DPad(onCommand: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.aspectRatio(1f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            DPadArrow(Icons.Default.KeyboardArrowUp, "Up", Alignment.TopCenter) { onCommand("up") }
            DPadArrow(Icons.Default.KeyboardArrowDown, "Down", Alignment.BottomCenter) { onCommand("down") }
            DPadArrow(Icons.AutoMirrored.Default.KeyboardArrowLeft, "Left", Alignment.CenterStart) { onCommand("left") }
            DPadArrow(Icons.AutoMirrored.Default.KeyboardArrowRight, "Right", Alignment.CenterEnd) { onCommand("right") }
            FilledTonalButton(
                onClick = { onCommand("select") },
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize(0.38f)
            ) {
                Text("OK", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun BoxScope.DPadArrow(
    icon: ImageVector,
    description: String,
    alignment: Alignment,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .align(alignment)
            .padding(8.dp)
            .size(72.dp)
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun CircularButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Icon(icon, contentDescription = description)
    }
}
