package dev.vinkyv.tvcontrol.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.vinkyv.tvcontrol.session.ConnectionState
import dev.vinkyv.tvcontrol.viewmodel.MainViewModel
import dev.vinkyv.tvcontrol.session.NowPlaying

@Composable
fun AudioScreen(viewModel: MainViewModel, connection: ConnectionState.Connected) {
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val artworkBitmap by viewModel.artwork.collectAsState()
    val artwork = remember(artworkBitmap) { artworkBitmap?.asImageBitmap() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Hardware volume keys control the Apple TV while this screen is shown
    DisposableEffect(viewModel) {
        viewModel.volumeKeysControlTv = true
        onDispose { viewModel.volumeKeysControlTv = false }
    }

    // Poll only while this screen is visible and the app is in the foreground
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.observeNowPlaying()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!connection.hasMetadata) {
            AirPlayHint(onPair = viewModel::pairAirPlay)
        }

        Artwork(
            artwork = artwork,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
        )

        TrackInfo(nowPlaying, hasMetadata = connection.hasMetadata)

        Timeline(nowPlaying, onSeek = viewModel::seekTo)

        TransportControls(
            isPlaying = nowPlaying?.isPlaying ?: false,
            onCommand = viewModel::sendCommand,
            onPlayPause = viewModel::togglePlayPause
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(modifier = Modifier.weight(1f), onClick = { viewModel.sendCommand("volume_down") }) {
                Icon(Icons.AutoMirrored.Default.VolumeDown, contentDescription = "Volume down")
            }
            FilledTonalButton(modifier = Modifier.weight(1f), onClick = { viewModel.sendCommand("volume_up") }) {
                Icon(Icons.AutoMirrored.Default.VolumeUp, contentDescription = "Volume up")
            }
        }
    }
}

@Composable
private fun AirPlayHint(onPair: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Now playing info, album art and seeking need AirPlay pairing.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(onClick = onPair) { Text("Pair AirPlay") }
        }
    }
}

@Composable
private fun Artwork(artwork: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = artwork, label = "artwork") { image ->
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = "Album artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(96.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackInfo(nowPlaying: NowPlaying?, hasMetadata: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            nowPlaying?.title ?: if (hasMetadata) "Not playing" else "Unknown",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val subtitle = listOfNotNull(nowPlaying?.artist, nowPlaying?.album).joinToString(" — ")
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun Timeline(nowPlaying: NowPlaying?, onSeek: (Int) -> Unit) {
    val total = nowPlaying?.totalTime?.takeIf { it > 0 }
    val position = (nowPlaying?.position ?: 0).coerceIn(0, total ?: 0)
    // Value while the user drags; null when not dragging so live updates are shown
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val haptics = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = dragValue ?: position.toFloat(),
            valueRange = 0f..(total ?: 1).toFloat(),
            enabled = total != null && nowPlaying?.canSeek == true,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSeek(it.toInt())
                }
                dragValue = null
            }
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatTime(dragValue?.toInt() ?: position).takeIf { total != null } ?: "--:--",
                style = MaterialTheme.typography.labelSmall
            )
            Text(total?.let(::formatTime) ?: "--:--", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TransportControls(isPlaying: Boolean, onCommand: (String) -> Unit, onPlayPause: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            modifier = Modifier.size(56.dp),
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCommand("previous")
            }
        ) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
        }
        FilledIconButton(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f),
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPlayPause()
            }
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(36.dp)
            )
        }
        FilledTonalIconButton(
            modifier = Modifier.size(56.dp),
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCommand("next")
            }
        ) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next")
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
