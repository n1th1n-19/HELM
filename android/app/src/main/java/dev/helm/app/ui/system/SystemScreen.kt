package dev.helm.app.ui.system

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.helm.app.ui.components.*
import dev.helm.app.ui.theme.*

@Composable
fun SystemScreen(
    modifier: Modifier = Modifier,
    viewModel: SystemViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val system = state.system

    // Keep a rolling 60-point CPU history
    val cpuHistory = remember { mutableStateListOf<Float>() }
    LaunchedEffect(system.cpuPercent) {
        system.cpuPercent?.let { cpu ->
            if (cpuHistory.size >= 60) cpuHistory.removeFirst()
            cpuHistory.add(cpu)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // CPU section
        item {
            HelmCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CPU", color = HelmTextSecondary, fontSize = 11.sp)
                        Text(
                            text = system.cpuPercent?.let { "%.1f%%".format(it) } ?: "--",
                            color = HelmCpu,
                            fontSize = 20.sp,
                        )
                    }
                    SparklineGraph(
                        values = cpuHistory.toList(),
                        color = HelmCpu,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        system.cpuFreqMhz?.let {
                            LabelValue("FREQ", "%.0f MHz".format(it), HelmTextSecondary)
                        }
                        system.cpuTempC?.let {
                            LabelValue("TEMP", "%.1f°C".format(it), HelmTemperature)
                        }
                        system.uptimeSecs?.let {
                            LabelValue("UPTIME", formatUptime(it), HelmTextSecondary)
                        }
                    }
                }
            }
        }

        // Memory section
        item {
            HelmCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MEMORY", color = HelmTextSecondary, fontSize = 11.sp)
                    val ramUsed = system.ramUsedMb ?: 0L
                    val ramTotal = system.ramTotalMb?.coerceAtLeast(1L) ?: 1L
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("RAM", color = HelmTextPrimary, fontSize = 13.sp)
                        Text("${formatMb(ramUsed)} / ${formatMb(ramTotal)}", color = HelmRam, fontSize = 13.sp)
                    }
                    HelmProgressBar(progress = ramUsed.toFloat() / ramTotal, color = HelmRam)
                    val swapUsed = system.swapUsedMb ?: 0L
                    val swapTotal = system.swapTotalMb?.coerceAtLeast(1L) ?: 1L
                    if (swapTotal > 1L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("SWAP", color = HelmTextPrimary, fontSize = 13.sp)
                            Text("${formatMb(swapUsed)} / ${formatMb(swapTotal)}", color = HelmTextSecondary, fontSize = 13.sp)
                        }
                        HelmProgressBar(progress = swapUsed.toFloat() / swapTotal, color = HelmTextSecondary)
                    }
                }
            }
        }

        // Network section
        item {
            HelmCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("NETWORK", color = HelmTextSecondary, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        LabelValue("↑ UP", system.netUpBps?.let { formatBytesPerSec(it) } ?: "--", HelmNetwork)
                        LabelValue("↓ DOWN", system.netDownBps?.let { formatBytesPerSec(it) } ?: "--", HelmNetwork)
                    }
                }
            }
        }

        // Disk section
        item {
            HelmCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DISK", color = HelmTextSecondary, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        LabelValue("READ", system.diskReadBps?.let { formatBytesPerSec(it) } ?: "--", HelmTextSecondary)
                        LabelValue("WRITE", system.diskWriteBps?.let { formatBytesPerSec(it) } ?: "--", HelmTextSecondary)
                    }
                }
            }
        }

        // Processes section
        item {
            HelmCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("PROCESSES", color = HelmTextSecondary, fontSize = 11.sp)
                    state.process.processes.take(10).forEach { proc ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = proc.name,
                                color = HelmTextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            Text(
                                text = "%.1f%%".format(proc.cpuPercent),
                                color = HelmCpu,
                                fontSize = 12.sp,
                                modifier = Modifier.width(48.dp),
                            )
                            Text(
                                text = formatMb(proc.memMb.toLong()),
                                color = HelmRam,
                                fontSize = 12.sp,
                                modifier = Modifier.width(72.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, color = HelmTextTertiary, fontSize = 10.sp)
        Text(value, color = valueColor, fontSize = 14.sp)
    }
}
