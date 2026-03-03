package com.hrvib.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.hrvib.app.domain.BreathPhase
import com.hrvib.app.domain.RangeFilter
import com.hrvib.app.domain.SessionConfig
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun HrvIbRoot(vm: MainViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm,
                onOpenDetail = { nav.navigate("detail/$it") },
                onOpenDevice = { nav.navigate("device") },
                onOpenSetup = { nav.navigate("setup") }
            )
        }
        composable("device") { DeviceScreen(vm) }
        composable("setup") { SessionSetupScreen(vm, onStart = { nav.navigate("live") }) }
        composable("live") { LiveScreen(vm, onFinish = { nav.navigate("summary") }) }
        composable("summary") { SessionSummaryScreen(vm) }
        composable(
            "detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStack ->
            SessionDetailScreen(vm, backStack.arguments?.getLong("id") ?: 0L)
        }
    }
}

@Composable
private fun HomeScreen(
    vm: MainViewModel,
    onOpenDetail: (Long) -> Unit,
    onOpenDevice: () -> Unit,
    onOpenSetup: () -> Unit
) {
    val range by vm.range.collectAsState()
    val scatter by vm.scatter.collectAsState()
    val series by vm.timeSeries.collectAsState()
    val demoMode by vm.demoMode.collectAsState()
    val showExcluded by vm.showExcluded.collectAsState()
    val sessions by vm.sessions.collectAsState()
    Scaffold { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("HRV IB Home", style = MaterialTheme.typography.headlineSmall)
                RangeSelector(range = range, onSelected = vm::updateRange)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenSetup) { Text("Setup") }
                    Button(onClick = onOpenDevice) { Text("Device") }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Demo Mode")
                        Switch(checked = demoMode, onCheckedChange = vm::setDemoMode)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Show Excluded")
                        Switch(checked = showExcluded, onCheckedChange = vm::setShowExcluded)
                    }
                }
            }
            item {
                Text("Scatter: Breaths/min vs Avg HRV", fontWeight = FontWeight.Bold)
                Text("Scatter points: ${scatter.size}", modifier = Modifier.testTag("scatter_count"))
                ScatterComposeChart(scatter, onOpenDetail)
            }
            item {
                Text("Time Series: Avg/Peak HRV", fontWeight = FontWeight.Bold)
                Text("Time buckets: ${series.size}", modifier = Modifier.testTag("timeseries_count"))
                LineComposeChart(series.map { it.bucket.toFloat() to it.avgHRV.toFloat() }, Color.Cyan)
                LineComposeChart(series.map { it.bucket.toFloat() to it.peakHRV.toFloat() }, Color.Magenta)
            }
            item {
                Text("Session History", fontWeight = FontWeight.Bold)
            }
            items(sessions.takeLast(20).reversed(), key = { it.id }) { s ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDetail(s.id) }
                        .testTag("session_card_${s.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${"%.1f".format(s.breathsPerMin)} br/min")
                        Text("avg ${s.avgHRV?.let { "%.1f".format(it) } ?: "—"}")
                    }
                }
            }
            item {
                HorizontalDivider()
                TextButton(onClick = onOpenDevice) { Text("Go Device") }
                TextButton(onClick = onOpenSetup) { Text("Go Setup") }
            }
        }
    }
}

@Composable
private fun DeviceScreen(vm: MainViewModel) {
    val state by vm.connectionState.collectAsState()
    val devices by vm.devices.collectAsState()
    val context = LocalContext.current
    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    var permissionDenied by remember {
        mutableStateOf(
            perms.any {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionDenied = result.values.any { granted -> !granted }
    }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(perms)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Device", style = MaterialTheme.typography.headlineSmall)
        if (permissionDenied) {
            Text("Bluetooth permission denied. Allow permission to scan/connect.", color = Color.Yellow)
            OutlinedButton(onClick = { permissionLauncher.launch(perms) }) { Text("Request permission again") }
        }
        Text("Connection: $state", modifier = Modifier.testTag("connection_status"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::startScan) { Text("Scan") }
            OutlinedButton(onClick = vm::disconnect) { Text("Disconnect") }
        }
        Text("Demo vector")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { vm.setFakeVector("hr_only.json") }) { Text("HR only") }
            OutlinedButton(onClick = { vm.setFakeVector("hr_rr_valid.json") }) { Text("HR+RR") }
            OutlinedButton(onClick = { vm.setFakeVector("disconnect_reconnect.json") }) { Text("Disconnect") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { vm.setFakeVector("artifact_rr.json") }) { Text("Artifact") }
            OutlinedButton(onClick = { vm.setFakeVector("sparse_rr.json") }) { Text("Sparse") }
        }
        devices.forEach { d ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.connect(d) }
                    .testTag("device_${d.id}")
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(d.name)
                    Text("RSSI ${d.rssi}")
                }
            }
        }
        if (devices.isEmpty()) {
            Text("No devices. In demo mode, Fake Polar H10 appears after scan.")
        }
    }
}

@Composable
private fun SessionSetupScreen(vm: MainViewModel, onStart: () -> Unit) {
    val config by vm.config.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Session Setup", style = MaterialTheme.typography.headlineSmall)
        NumberSlider(
            title = "Metronome BPM ${"%.1f".format(config.metronomeBpm)}",
            value = config.metronomeBpm.toFloat(),
            range = 20f..120f,
            steps = 999
        ) { v ->
            vm.updateConfig(config.copy(metronomeBpm = ((v * 10).roundToInt()) / 10.0))
        }
        Stepper("Inhale beats", config.inhaleBeats, 1, 12) { vm.updateConfig(config.copy(inhaleBeats = it)) }
        Stepper("Exhale beats", config.exhaleBeats, 1, 12) { vm.updateConfig(config.copy(exhaleBeats = it)) }
        Stepper("Duration (min)", config.durationMinutes, 1, 60) { vm.updateConfig(config.copy(durationMinutes = it)) }
        Text("Ratio: ${config.inhaleBeats}:${config.exhaleBeats}")
        Text("Breaths/min: ${"%.1f".format(config.breathsPerMinute)}")
        Button(onClick = {
            vm.startSession()
            onStart()
        }) { Text("Start Session") }
    }
}

@Composable
private fun LiveScreen(vm: MainViewModel, onFinish: () -> Unit) {
    val live by vm.live.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Live Session", style = MaterialTheme.typography.headlineSmall)
        Text("Phase: ${if (live.phase == BreathPhase.Inhale) "Inhale" else "Exhale"}")
        Text("Time left: ${live.remainingMs / 1000}s")
        Text("HR: ${live.currentHr ?: "--"}", modifier = Modifier.testTag("live_hr"))
        Text(
            "HRV(3s RMSSD): ${live.smoothedHrv?.let { "%.1f".format(it) } ?: "—"}",
            modifier = Modifier.testTag("live_hrv")
        )
        RrWaveChart(live.rrWave)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::stopSession) { Text("Stop") }
            if (!live.inSession) {
                OutlinedButton(onClick = onFinish) { Text("Summary") }
            }
        }
    }
}

@Composable
private fun SessionSummaryScreen(vm: MainViewModel) {
    val sessions by vm.sessions.collectAsState()
    val latest = sessions.lastOrNull()
    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Session Summary", style = MaterialTheme.typography.headlineSmall)
        if (latest == null) {
            Text("No session data yet.")
        } else {
            Text("Breaths/min: ${"%.1f".format(latest.breathsPerMin)}")
            Text("Avg HRV: ${latest.avgHRV?.let { "%.1f".format(it) } ?: "—"}", modifier = Modifier.testTag("summary_avg_hrv"))
            Text("Peak HRV: ${latest.peakHRV?.let { "%.1f".format(it) } ?: "—"}")
            Text("Avg HR: ${latest.avgHR?.let { "%.1f".format(it) } ?: "—"}")
            Text("First 60s excluded + artifact + MAD outlier cleaning applied")
        }
    }
}

@Composable
private fun SessionDetailScreen(vm: MainViewModel, sessionId: Long) {
    val sessions by vm.sessions.collectAsState()
    val session = sessions.find { it.id == sessionId } ?: sessions.lastOrNull()
    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Session Detail", style = MaterialTheme.typography.headlineSmall)
        if (session == null) {
            Text("No session selected")
        } else {
            Text("ID ${session.id}")
            Text("Avg HRV ${session.avgHRV?.let { "%.1f".format(it) } ?: "—"}")
            Text("Avg HR ${session.avgHR?.let { "%.1f".format(it) } ?: "—"}")
            Button(
                onClick = { vm.toggleSoftDelete(session) },
                modifier = Modifier.testTag("soft_delete_toggle")
            ) {
                Text(if (session.isDeleted) "Restore" else "Exclude from analysis")
            }
        }
    }
}

@Composable
private fun RangeSelector(range: RangeFilter, onSelected: (RangeFilter) -> Unit) {
    val options = RangeFilter.entries.toList()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { option ->
            val selected = option == range
            if (selected) {
                Button(onClick = { onSelected(option) }) { Text(option.name) }
            } else {
                OutlinedButton(onClick = { onSelected(option) }) { Text(option.name) }
            }
        }
    }
}

@Composable
private fun Stepper(title: String, value: Int, min: Int, max: Int, onValue: (Int) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(title, modifier = Modifier.width(140.dp))
        OutlinedButton(onClick = { onValue((value - 1).coerceAtLeast(min)) }) { Text("-") }
        Spacer(Modifier.width(8.dp))
        Text(value.toString())
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { onValue((value + 1).coerceAtMost(max)) }) { Text("+") }
    }
}

@Composable
private fun NumberSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onValue: (Float) -> Unit) {
    Column {
        Text(title)
        Slider(value = value, onValueChange = onValue, valueRange = range, steps = steps)
    }
}

@Composable
private fun RrWaveChart(data: List<Pair<Long, Double>>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(Color(0xFF121212))
            .testTag("live_wave")
    ) {
        if (data.size < 2) return@Canvas
        val minY = data.minOf { it.second }.toFloat()
        val maxY = data.maxOf { it.second }.toFloat()
        val yRange = (maxY - minY).takeIf { it > 1e-3f } ?: 1f
        val minX = data.first().first.toFloat()
        val maxX = data.last().first.toFloat()
        val xRange = (maxX - minX).takeIf { it > 1f } ?: 1f

        for (i in 1 until data.size) {
            val p0 = data[i - 1]
            val p1 = data[i]
            val x0 = ((p0.first - minX) / xRange) * size.width
            val x1 = ((p1.first - minX) / xRange) * size.width
            val y0 = size.height - (((p0.second - minY) / yRange) * size.height).toFloat()
            val y1 = size.height - (((p1.second - minY) / yRange) * size.height).toFloat()
            drawLine(Color.Cyan, Offset(x0, y0), Offset(x1, y1), strokeWidth = 4f)
        }
    }
}

@Composable
private fun ScatterComposeChart(
    data: List<Pair<Long, Pair<Double, Double>>>,
    onOpenDetail: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().testTag("scatter_chart")) {
        data.forEach { (id, xy) ->
            Row(modifier = Modifier.fillMaxWidth().clickable { onOpenDetail(id) }.padding(vertical = 2.dp)) {
                Text("bpm ${"%.1f".format(xy.first)} -> HRV ${"%.1f".format(xy.second)}")
            }
        }
        if (data.isEmpty()) Text("No points in selected range")
    }
}

@Composable
private fun LineComposeChart(points: List<Pair<Float, Float>>, color: Color) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
            }
        },
        modifier = Modifier.fillMaxWidth().height(160.dp).testTag("timeseries_chart"),
        update = { chart ->
            val entries = points.mapIndexed { index, p -> Entry(index.toFloat(), p.second) }
            val set = LineDataSet(entries, "").apply {
                this.color = color.toArgb()
                setDrawCircles(true)
            }
            chart.data = LineData(set)
            chart.invalidate()
        }
    )
}
