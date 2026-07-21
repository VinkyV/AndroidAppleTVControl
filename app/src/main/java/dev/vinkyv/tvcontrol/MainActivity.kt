package dev.vinkyv.tvcontrol

import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.vinkyv.tvcontrol.protocol.codec.TlvValue
import dev.vinkyv.tvcontrol.protocol.codec.writeTLV
import dev.vinkyv.tvcontrol.ui.screens.AudioScreen
import dev.vinkyv.tvcontrol.ui.screens.PairScreen
import dev.vinkyv.tvcontrol.ui.screens.RemoteScreen
import dev.vinkyv.tvcontrol.ui.screens.SettingsScreen
import dev.vinkyv.tvcontrol.ui.theme.TVControlTheme
import dev.vinkyv.tvcontrol.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import javax.jmdns.JmDNS

class MainActivity : ComponentActivity() {

    private var jmdns: JmDNS? = null
    private var multicastLock: WifiManager.MulticastLock? = null
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

    override fun onDestroy() {
        super.onDestroy()

        try {
            jmdns?.close()
        } catch (_: Exception) {
        }

        multicastLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    private suspend fun discover() = withContext(Dispatchers.IO) {
        try {
            val wifiManager = getSystemService(WIFI_SERVICE) as WifiManager

            multicastLock = wifiManager.createMulticastLock("JmDNSLock").apply {
                setReferenceCounted(true)
                acquire()
            }

            val address = getWifiIpAddress() ?: throw IOException("No WIFI address found")

            jmdns = JmDNS.create(address).apply {
                addServiceListener("_airplay._tcp.local.", CompanionListener())
            }

            Log.d("JmDNS", "Service discovery started")
        } catch (e: Exception) {
            Log.e("JmDNS", e.stackTraceToString())
        }
    }

    private fun getWifiIpAddress(): InetAddress? {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

        for (networkInterface in interfaces) {
            if (!networkInterface.isUp || networkInterface.isLoopback) continue

            val addresses = Collections.list(networkInterface.inetAddresses)

            for (address in addresses) {
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    return address
                }
            }
        }

        return null
    }

    private fun startPairing() {

        writeTLV(
            mapOf(
                TlvValue.Method.ordinal to byteArrayOf(0x00),
                TlvValue.SeqNo.ordinal to byteArrayOf(0x01)
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(mainViewModel: MainViewModel) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    var currentTab by remember { mutableIntStateOf(0) }
    val bottomNavigationItems = remember { BottomNavigationItems.entries.toTypedArray() }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                title = {
                    if (currentRoute != null) {
                        Text(BottomNavigationItems.valueOf(currentRoute.uppercase()).label)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                bottomNavigationItems.forEachIndexed { index, tabItem ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        label = {
                            Text(tabItem.label)
                        },
                        alwaysShowLabel = false,
                        icon = {
                            Icon(
                                imageVector = if (currentTab == index) tabItem.selectedIcon else tabItem.unselectedIcon,
                                contentDescription = tabItem.label
                            )
                        },
                        onClick = {
                            currentTab = index
                            navController.navigate(tabItem.label) {
                                navController.graph.startDestinationRoute?.let { route ->
                                    popUpTo(route) {
                                        saveState = true
                                    }
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
        content = { padding ->
            Box(modifier = Modifier.padding(padding)) {
                Navigation(navController = navController, atvController = mainViewModel.atvController)
            }
        }
    )
}

enum class BottomNavigationItems(val label: String, val route: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    REMOTE("Remote", "remote", Icons.Filled.SettingsRemote, Icons.Outlined.SettingsRemote),
    AUDIO("Audio", "audio", Icons.Filled.Audiotrack, Icons.Outlined.Audiotrack),
    SETTINGS("Settings", "settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun Navigation(navController: NavHostController, atvController: AtvController) {
    val isConnected by atvController.isConnected.collectAsState()
    NavHost(navController, startDestination = BottomNavigationItems.REMOTE.route) {
        composable(BottomNavigationItems.REMOTE.route) {
            if (isConnected) RemoteScreen(atvController)
            else PairScreen(atvController)
        }
        composable(BottomNavigationItems.AUDIO.route) {
            if (isConnected) AudioScreen(atvController)
            else PairScreen(atvController)
        }
        composable(BottomNavigationItems.SETTINGS.route) {
            SettingsScreen()
        }
    }
}