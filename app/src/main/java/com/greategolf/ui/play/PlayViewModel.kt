package com.greategolf.ui.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greategolf.GolfApp
import com.greategolf.data.HoleGreen
import com.greategolf.data.Shot
import com.greategolf.domain.ClubAdvisor
import com.greategolf.domain.DetectedShot
import com.greategolf.domain.GeoFix
import com.greategolf.domain.GeoMath
import com.greategolf.domain.GeoPoint
import com.greategolf.domain.ShotTracker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PlayViewModel(private val app: GolfApp) : ViewModel() {
    private val repo = app.repository
    private val tracker = ShotTracker()

    val round = app.activeRoundId.flatMapLatest { id -> if (id == null) flowOf(null) else repo.round(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val greens = round.flatMapLatest { r ->
        if (r?.courseId == null) flowOf(emptyList<HoleGreen>()) else repo.greens(r.courseId)
    }
    val clubStats = repo.clubStats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hole = MutableStateFlow(1)
    val fix = MutableStateFlow<GeoFix?>(null)
    val selectedClubId = MutableStateFlow<Long?>(null)
    val autoSwing = MutableStateFlow(false)
    val armed = MutableStateFlow(false)
    val liveMeters = MutableStateFlow<Double?>(null)
    val pending = MutableStateFlow<DetectedShot?>(null)   // detected, waiting for a club to be chosen
    val message = MutableStateFlow<String?>(null)
    val swingSensorAvailable get() = app.swingSensor.isAvailable

    /** Distance from the player to the recorded green of the current hole, in meters. */
    val distanceToGreen: StateFlow<Double?> = combine(fix, greens, hole) { f, g, h ->
        val green = g.firstOrNull { it.hole == h } ?: return@combine null
        f?.let { GeoMath.distanceMeters(it.point, GeoPoint(green.lat, green.lng)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val hasGreen: StateFlow<Boolean> = combine(greens, hole) { g, h -> g.any { it.hole == h } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val recommendation = combine(distanceToGreen, clubStats) { d, c -> d?.let { ClubAdvisor.recommend(it, c) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var gpsJob: Job? = null
    private var swingJob: Job? = null

    /** Start/stop follow the screen's visibility so GPS isn't left running in the background. */
    fun startGps() {
        if (gpsJob?.isActive == true) return
        gpsJob = viewModelScope.launch {
            app.location.fixes().collect { f ->
                fix.value = f
                if (tracker.isArmed) {
                    liveMeters.value = tracker.currentMeters(f)
                    tracker.onFix(f)?.let { onShotDetected(it) }
                }
            }
        }
        refreshSwingWatcher()
    }

    fun stopGps() {
        gpsJob?.cancel(); gpsJob = null
        swingJob?.cancel(); swingJob = null
    }

    fun setAutoSwing(on: Boolean) { autoSwing.value = on; refreshSwingWatcher() }

    private fun refreshSwingWatcher() {
        swingJob?.cancel(); swingJob = null
        if (!autoSwing.value || gpsJob == null) return
        swingJob = viewModelScope.launch {
            app.swingSensor.swings().collect {
                if (!tracker.isArmed) armHere("Phát hiện swing — đã đánh dấu vị trí đánh bóng.")
            }
        }
    }

    /** Marks the ball position (where the player stands) as the start of a shot. */
    fun armHere(note: String = "Đã đánh dấu vị trí bóng. Đi tới bóng — app tự đo khi bạn dừng lại.") {
        val f = fix.value ?: run { message.value = "Chưa có tín hiệu GPS."; return }
        tracker.arm(f.point)
        armed.value = true
        liveMeters.value = 0.0
        pending.value = null
        message.value = note
    }

    fun finishManually() {
        val f = fix.value ?: return
        tracker.finishNow(f.point)?.let { onShotDetected(it) }
    }

    fun cancelShot() {
        tracker.cancel(); armed.value = false; liveMeters.value = null
        message.value = "Đã hủy cú đánh."
    }

    private fun onShotDetected(shot: DetectedShot) {
        armed.value = false
        liveMeters.value = null
        if (selectedClubId.value != null) save(shot, selectedClubId.value!!) else {
            pending.value = shot
            message.value = "Đo được %.0f m — chọn gậy để lưu.".format(shot.meters)
        }
    }

    fun selectClub(id: Long) {
        selectedClubId.value = id
        pending.value?.let { save(it, id); pending.value = null }
    }

    private fun save(shot: DetectedShot, clubId: Long) {
        viewModelScope.launch {
            repo.addShot(
                Shot(
                    clubId = clubId, meters = shot.meters, createdAt = System.currentTimeMillis(),
                    startLat = shot.start.lat, startLng = shot.start.lng, endLat = shot.end.lat, endLng = shot.end.lng, auto = true,
                )
            )
            val name = clubStats.value.firstOrNull { it.clubId == clubId }?.name ?: "Gậy"
            message.value = "Đã lưu: $name %.0f m".format(shot.meters)
        }
    }

    fun markGreenHere() {
        val r = round.value
        val f = fix.value
        if (r?.courseId == null || f == null) { message.value = "Cần chọn sân từ bản đồ và có tín hiệu GPS."; return }
        viewModelScope.launch {
            repo.saveGreen(HoleGreen(r.courseId, hole.value, f.point.lat, f.point.lng))
            message.value = "Đã lưu vị trí green hố ${hole.value}."
        }
    }

    fun changeHole(delta: Int) {
        val max = round.value?.holeCount ?: 18
        hole.value = (hole.value + delta).coerceIn(1, max)
    }

    override fun onCleared() { stopGps() }
}
