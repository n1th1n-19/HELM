package dev.helm.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Wifi
import dev.helm.app.data.websocket.ConnectionState
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.helm.app.data.nsd.DiscoveredAgent
import dev.helm.app.data.prefs.ConnectionMode
import dev.helm.app.data.prefs.ConnectionPreferences
import dev.helm.app.ui.components.HelmCard
import dev.helm.app.ui.theme.*

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var hostInput by remember(state.wifiHost) { mutableStateOf(state.wifiHost) }
    var portInput by remember(state.wifiPort) { mutableStateOf(state.wifiPort.toString()) }

    val scanOptions = remember {
        ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan HELM pairing QR code")
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
    }

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { viewModel.handleQrResult(it) }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) qrLauncher.launch(scanOptions)
    }

    fun launchQrScanner() {
        val hasPerm = context.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (hasPerm) qrLauncher.launch(scanOptions)
        else cameraPermLauncher.launch(Manifest.permission.CAMERA)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HelmCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("CONNECTION MODE", color = HelmTextSecondary, fontSize = 11.sp)

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = state.mode == ConnectionMode.USB,
                            onClick = { viewModel.setMode(ConnectionMode.USB) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = { Icon(Icons.Outlined.Cable, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = segmentedButtonColors(),
                        ) {
                            Text("USB", fontSize = 13.sp)
                        }
                        SegmentedButton(
                            selected = state.mode == ConnectionMode.WIFI,
                            onClick = { viewModel.setMode(ConnectionMode.WIFI) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = { Icon(Icons.Outlined.Wifi, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = segmentedButtonColors(),
                        ) {
                            Text("WiFi", fontSize = 13.sp)
                        }
                    }

                    val targetUrl = if (state.mode == ConnectionMode.WIFI && state.wifiHost.isNotBlank())
                        "ws://${state.wifiHost}:${state.wifiPort}/helm"
                    else
                        "ws://localhost:${state.wifiPort}/helm (USB)"
                    Text("Target: $targetUrl", color = HelmTextTertiary, fontSize = 10.sp)

                    val (statusColor, statusText) = when (state.connectionState) {
                        ConnectionState.Connected    -> HelmSuccess to "Connected"
                        ConnectionState.Connecting   -> HelmWarning to "Connecting..."
                        ConnectionState.Reconnecting -> HelmWarning to "Reconnecting..."
                        ConnectionState.Disconnected -> HelmError   to "Disconnected"
                    }
                    Text(statusText, color = statusColor, fontSize = 11.sp)

                    state.lastError?.let { err ->
                        Text("Error: $err", color = HelmError, fontSize = 10.sp)
                    }
                }
            }
        }

        if (state.mode == ConnectionMode.WIFI) {
            item {
                HelmCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("AGENT ADDRESS", color = HelmTextSecondary, fontSize = 11.sp)

                        OutlinedTextField(
                            value = hostInput,
                            onValueChange = { hostInput = it },
                            label = { Text("Host", fontSize = 12.sp) },
                            placeholder = { Text("192.168.1.x", color = HelmTextTertiary, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = helmTextFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )

                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it },
                            label = { Text("Port", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = helmTextFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.saveAndConnect(
                                        hostInput.trim(),
                                        portInput.toIntOrNull() ?: ConnectionPreferences.DEFAULT_PORT,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HelmNetwork),
                                border = BorderStroke(1.dp, HelmBorder),
                            ) {
                                Text("Save", fontSize = 12.sp)
                            }
                        }

                        HorizontalDivider(color = HelmBorder)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { launchQrScanner() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HelmNetwork),
                                border = BorderStroke(1.dp, HelmBorder),
                            ) {
                                Icon(
                                    Icons.Outlined.QrCodeScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Scan QR", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    if (state.isDiscovering) viewModel.stopDiscovery()
                                    else viewModel.startDiscovery()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (state.isDiscovering) HelmWarning else HelmNetwork
                                ),
                                border = BorderStroke(1.dp, HelmBorder),
                            ) {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (state.isDiscovering) "Stop" else "Discover",
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }

            if (state.discovered.isNotEmpty() || state.isDiscovering) {
                item {
                    HelmCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("DISCOVERED AGENTS", color = HelmTextSecondary, fontSize = 11.sp)
                                if (state.isDiscovering) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = HelmNetwork,
                                    )
                                }
                            }

                            if (state.discovered.isEmpty()) {
                                Text("Scanning...", color = HelmTextTertiary, fontSize = 12.sp)
                            } else {
                                state.discovered.forEach { agent ->
                                    DiscoveredAgentRow(
                                        agent = agent,
                                        isSelected = agent.host == state.wifiHost && agent.port == state.wifiPort,
                                        onSelect = { viewModel.selectDiscovered(agent) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveredAgentRow(
    agent: DiscoveredAgent,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(agent.name, color = HelmTextPrimary, fontSize = 13.sp)
            Text("${agent.host}:${agent.port}", color = HelmTextTertiary, fontSize = 11.sp)
        }
        if (isSelected) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = "Selected",
                tint = HelmNetwork,
                modifier = Modifier.size(18.dp),
            )
        } else {
            TextButton(onClick = onSelect, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("Use", color = HelmNetwork, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun segmentedButtonColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = HelmNetwork.copy(alpha = 0.15f),
    activeContentColor = HelmNetwork,
    activeBorderColor = HelmNetwork,
    inactiveContainerColor = HelmCard,
    inactiveContentColor = HelmTextSecondary,
    inactiveBorderColor = HelmBorder,
)

@Composable
private fun helmTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = HelmNetwork,
    unfocusedBorderColor = HelmBorder,
    focusedLabelColor = HelmNetwork,
    unfocusedLabelColor = HelmTextTertiary,
    focusedTextColor = HelmTextPrimary,
    unfocusedTextColor = HelmTextPrimary,
    cursorColor = HelmNetwork,
)
