package com.hrvib.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrvib.app.BuildConfig
import com.hrvib.app.data.BleGateway
import com.hrvib.app.data.SessionRepository
import com.hrvib.app.data.db.SessionEntity
import com.hrvib.app.data.db.TimeSeriesPoint
import com.hrvib.app.domain.BleDevice
import com.hrvib.app.domain.BreathPhase
import com.hrvib.app.domain.ConnectionState
import com.hrvib.app.domain.HrvMath
import com.hrvib.app.domain.MetronomeEngine
import com.hrvib.app.domain.RangeFilter
import com.hrvib.app.domain.SessionConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LiveUiState(
    val currentHr: Int? = null,
    val rawHrv: Double? = null,
    val smoothedHrv: Double? = null,
    val rrWave: List<Pair<Long, Double>> = emptyList(),
    val phase: BreathPhase = BreathPhase.Inhale,
    val remainingMs: Long = 0L,
    val inSession: Boolean = false,
    val activeSessionId: Long? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val bleGateway: BleGateway,
    private val sessionRepository: SessionRepository,
    private val metronomeEngine: MetronomeEngine
) : ViewModel() {
    val connectionState = bleGateway.connectionState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ConnectionState.Disconnected
    )
    val devices = bleGateway.scanResults.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val configState = MutableStateFlow(SessionConfig())
    val config: StateFlow<SessionConfig> = configState

    private val rangeState = MutableStateFlow(RangeFilter.Week)
    val range: StateFlow<RangeFilter> = rangeState

    private val liveState = MutableStateFlow(LiveUiState())
    val live: StateFlow<LiveUiState> = liveState

    private val showExcludedState = MutableStateFlow(false)
    val showExcluded: StateFlow<Boolean> = showExcludedState
    val demoMode = bleGateway.demoMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val sessions = combine(rangeState, showExcludedState) { r, s -> r to s }
        .flatMapLatest { (r, s) -> sessionRepository.observeSessions(r, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scatter = combine(rangeState, showExcludedState) { r, s -> r to s }
        .flatMapLatest { (r, s) -> sessionRepository.observeScatter(r, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timeSeries = combine(rangeState, showExcludedState) { r, s -> r to s }
        .flatMapLatest { (r, s) -> sessionRepository.observeTimeSeries(r, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<TimeSeriesPoint>())

    private val rrWindow = mutableListOf<HrvMath.TimedRr>()
    private var sampleCollectorJob: Job? = null
    private var rollingCalcJob: Job? = null
    private var metronomeJob: Job? = null

    init {
        viewModelScope.launch {
            bleGateway.demoMode.collect { bleGateway.setDemoMode(it) }
        }
        viewModelScope.launch {
            bleGateway.showExcluded.collect { showExcludedState.value = it }
        }
        if (BuildConfig.SEED_DEMO_HISTORY) {
            viewModelScope.launch {
                sessionRepository.seedDemoHistoryIfEmpty()
            }
        }
    }

    fun updateConfig(config: SessionConfig) {
        configState.value = config
    }

    fun updateRange(rangeFilter: RangeFilter) {
        rangeState.value = rangeFilter
    }

    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch { bleGateway.persistDemoMode(enabled) }
    }

    fun setShowExcluded(enabled: Boolean) {
        viewModelScope.launch { bleGateway.persistShowExcluded(enabled) }
    }

    fun setFakeVector(fileName: String) {
        bleGateway.setFakeVector(fileName)
    }

    fun startScan() {
        viewModelScope.launch { bleGateway.startScan() }
    }

    fun connect(device: BleDevice) {
        viewModelScope.launch { bleGateway.connect(device.id) }
    }

    fun disconnect() {
        viewModelScope.launch { bleGateway.disconnect() }
    }

    fun startSession() {
        if (liveState.value.inSession) return
        val start = System.currentTimeMillis()
        viewModelScope.launch {
            val sessionId = sessionRepository.createSession(configState.value, start)
            liveState.value = liveState.value.copy(
                inSession = true,
                activeSessionId = sessionId,
                remainingMs = configState.value.durationMinutes * 60_000L
            )
            startCollectors()
        }
    }

    fun stopSession() {
        val sessionId = liveState.value.activeSessionId ?: return
        sampleCollectorJob?.cancel()
        rollingCalcJob?.cancel()
        metronomeJob?.cancel()
        viewModelScope.launch {
            sessionRepository.completeSession(sessionId, System.currentTimeMillis())
            liveState.value = liveState.value.copy(inSession = false, activeSessionId = null)
            rrWindow.clear()
        }
    }

    private fun startCollectors() {
        val sessionId = liveState.value.activeSessionId ?: return
        sampleCollectorJob?.cancel()
        sampleCollectorJob = viewModelScope.launch {
            bleGateway.samples.collect { sample ->
                liveState.value = liveState.value.copy(currentHr = sample.hrBpm ?: liveState.value.currentHr)
                sample.rrIntervalsMs.forEach { rr ->
                    val timed = HrvMath.TimedRr(sample.timestampMs, rr)
                    rrWindow += timed
                    sessionRepository.appendRr(sessionId, sample.timestampMs, rr, sample.hrBpm)
                }
                rrWindow.removeAll { it.timestampMs < System.currentTimeMillis() - 30_000L }
                liveState.value = liveState.value.copy(
                    rrWave = rrWindow.takeLast(120).map { it.timestampMs to it.rrMs }
                )
            }
        }

        rollingCalcJob?.cancel()
        rollingCalcJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val now = System.currentTimeMillis()
                val raw = HrvMath.rolling3SecRmssd(rrWindow, now)
                val smoothed = raw?.let { HrvMath.ema(liveState.value.smoothedHrv, it) }
                liveState.value = liveState.value.copy(
                    rawHrv = raw,
                    smoothedHrv = smoothed,
                    remainingMs = (liveState.value.remainingMs - 1000L).coerceAtLeast(0L)
                )
                sessionRepository.appendEpoch(sessionId, now, raw, liveState.value.currentHr)
                if (liveState.value.remainingMs <= 0L) {
                    stopSession()
                    break
                }
            }
        }

        metronomeJob?.cancel()
        metronomeJob = viewModelScope.launch {
            metronomeEngine.ticks(configState.value).collect { tick ->
                liveState.value = liveState.value.copy(phase = tick.phase)
            }
        }
    }

    fun toggleSoftDelete(session: SessionEntity) {
        viewModelScope.launch {
            sessionRepository.setDeleted(session.id, !session.isDeleted)
        }
    }

    suspend fun getSession(sessionId: Long): SessionEntity? = sessionRepository.getSession(sessionId)
}
