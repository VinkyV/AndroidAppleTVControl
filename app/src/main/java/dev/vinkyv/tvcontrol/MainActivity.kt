package dev.vinkyv.tvcontrol

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.vinkyv.tvcontrol.ui.screens.AudioScreen
import dev.vinkyv.tvcontrol.ui.screens.PairScreen
import dev.vinkyv.tvcontrol.ui.screens.PinSubmitDialog
import dev.vinkyv.tvcontrol.ui.screens.RemoteScreen
import dev.vinkyv.tvcontrol.ui.screens.SettingsScreen
import dev.vinkyv.tvcontrol.ui.theme.TVControlTheme
import dev.vinkyv.tvcontrol.session.ConnectionState
import dev.vinkyv.tvcontrol.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TVControlTheme {
                MainScreen(mainViewModel)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val up = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> true
            KeyEvent.KEYCODE_VOLUME_DOWN -> false
            else -> return super.dispatchKeyEvent(event)
        }
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> mainViewModel.onVolumeKey(up)
                || super.dispatchKeyEvent(event)
            // Swallow the matching key-up so the phone's volume panel doesn't appear
            else -> mainViewModel.volumeKeysControlTv || super.dispatchKeyEvent(event)
        }
    }
}

enum class Destination(val label: String, val route: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    REMOTE("Remote", "remote", Icons.Filled.SettingsRemote, Icons.Outlined.SettingsRemote),
    AUDIO("Audio", "audio", Icons.Filled.Audiotrack, Icons.Outlined.Audiotrack),
    SETTINGS("Settings", "settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val current = Destination.entries.firstOrNull { it.route == currentRoute } ?: Destination.REMOTE
    val connection by viewModel.connection.collectAsState()
    val busyStatus by viewModel.busyStatus.collectAsState()
    val pinRequest by viewModel.pinRequest.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                title = {
                    val deviceName = (connection as? ConnectionState.Connected)?.device?.name
                    Text(if (deviceName.isNullOrEmpty()) current.label else "${current.label} · $deviceName")
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Destination.entries.forEach { destination ->
                    val selected = destination == current
                    NavigationBarItem(
                        selected = selected,
                        label = { Text(destination.label) },
                        alwaysShowLabel = false,
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label
                            )
                        },
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            busyStatus?.let { status ->
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                Navigation(navController, viewModel)
            }
        }
    }

    pinRequest?.let { request ->
        PinSubmitDialog(
            request = request,
            onDismissRequest = viewModel::cancelPin,
            onPinSubmitted = viewModel::submitPin
        )
    }
}

@Composable
fun Navigation(navController: NavHostController, viewModel: MainViewModel) {
    NavHost(navController, startDestination = Destination.REMOTE.route) {
        composable(Destination.REMOTE.route) {
            WhenConnected(viewModel) { RemoteScreen(viewModel) }
        }
        composable(Destination.AUDIO.route) {
            WhenConnected(viewModel) { connected -> AudioScreen(viewModel, connected) }
        }
        composable(Destination.SETTINGS.route) {
            SettingsScreen(viewModel)
        }
    }
}

/** Shows [content] while connected, otherwise the device picker / pairing screen. */
@Composable
private fun WhenConnected(viewModel: MainViewModel, content: @Composable (ConnectionState.Connected) -> Unit) {
    val connection by viewModel.connection.collectAsState()
    when (val state = connection) {
        is ConnectionState.Connected -> content(state)
        else -> PairScreen(viewModel)
    }
}
