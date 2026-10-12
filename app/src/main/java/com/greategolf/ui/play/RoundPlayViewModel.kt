package com.greategolf.ui.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greategolf.GolfApp
import com.greategolf.data.Course
import com.greategolf.data.HoleGreen
import com.greategolf.data.HoleScore
import com.greategolf.data.HoleTee
import com.greategolf.data.Shot
import com.greategolf.domain.CourseLayout
import com.greategolf.domain.DetectedShot
import com.greategolf.domain.GeoFix
import com.greategolf.domain.GeoMath
import com.greategolf.domain.GeoPoint
import com.greategolf.domain.FeatureType
import com.greategolf.domain.GreenInfo
import com.greategolf.domain.HazardInfo
import com.greategolf.domain.Hole
import com.greategolf.domain.LayoutOps
import com.greategolf.domain.ShotTracker
import com.greategolf.ui.course.LayoutState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [index] is the hole's position in the mapped layout, or -1 for a hole the player defined by hand. */
data class HoleRef(val index: Int, val hole: Hole)

enum class Placing { NONE, TEE, GREEN }

@OptIn(ExperimentalCoroutinesApi::class)
class RoundPlayViewModel(private val app: GolfApp, val roundId: Long) : ViewModel() {
    private val repo = app.repository
    private val tracker = ShotTracker()

    val round = repo.round(roundId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val scores = repo.scores(roundId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val clubStats = repo.clubStats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val course = MutableStateFlow<Course?>(null)
    val layoutState = MutableStateFlow<LayoutState>(LayoutState.Loading)

    val hole = MutableStateFlow(1)
    val fix = MutableStateFlow<GeoFix?>(null)
    val measure = MutableStateFlow<GeoPoint?>(null)

    init {
        viewModelScope.launch {
            val r = repo.round(roundId).first()
            val c = r?.courseId?.let { id -> app.courses.firstOrNull { it.id == id } }
            course.value = c
            if (c == null) layoutState.value = LayoutState.NoData else loadLayout(c, false)
        }
    }

    fun reloadLayout() { course.value?.let { loadLayout(it, true) } }

    private fun loadLayout(c: Course, force: Boolean) {
        layoutState.value = LayoutState.Loading
        viewModelScope.launch {
            layoutState.value = try {
                val l = app.layouts.load(c, force)
                if (l.isEmpty) LayoutState.NoData else { applyMappedPars(l); LayoutState.Ready(l) }
            } catch (e: Exception) {
                LayoutState.Failed(e.message ?: "Lỗi mạng")
            }
        }
    }

    /** Use the par values mapped in OSM for holes that haven't been scored yet. */
    private suspend fun applyMappedPars(l: CourseLayout) {
        val current = repo.scores(roundId).first()
        for (s in current) {
            val mapped = l.holes.firstOrNull { it.ref == s.hole }?.par ?: continue
            if (s.strokes == 0 && mapped in 3..6 && mapped != s.par) repo.setScore(s.copy(par = mapped))
        }
    }

    private val layout: StateFlow<CourseLayout?> = layoutState.combine(hole) { s, _ -> (s as? LayoutState.Ready)?.layout }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val userGreens = course.flatMapLatest { c -> if (c == null) flowOf(emptyList<HoleGreen>()) else repo.greens(c.id) }
    private val userTees = course.flatMapLatest { c -> if (c == null) flowOf(emptyList<HoleTee>()) else repo.tees(c.id) }

    /** A hole defined by the player (tee + green) — wins over the OSM hole, and works where OSM has no data. */
    val manualHole: StateFlow<Hole?> = combine(userTees, userGreens, hole) { t, g, h ->
        val tee = t.firstOrNull { it.hole == h }; val gr = g.firstOrNull { it.hole == h }
        if (tee != null && gr != null) Hole(h, null, listOf(GeoPoint(tee.lat, tee.lng), GeoPoint(gr.lat, gr.lng))) else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The mapped hole for the current hole number (on 27/36-hole sites, the one the player is nearest to). */
    val holeRef: StateFlow<HoleRef?> = combine(layout, hole, fix, manualHole) { l, h, f, manual ->
        if (manual != null) return@combine HoleRef(-1, manual)
        val cands = l?.holes?.withIndex()?.filter { it.value.ref == h }.orEmpty()
        val pick = if (f == null) cands.firstOrNull()
        else cands.minByOrNull { c -> c.value.line.minOf { GeoMath.distanceMeters(it, f.point) } }
        pick?.let { HoleRef(it.index, it.value) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val green: StateFlow<GreenInfo?> = combine(holeRef, layout, userGreens, hole) { ref, l, ug, h ->
        ug.firstOrNull { it.hole == h }?.let {
            val p = GeoPoint(it.lat, it.lng); return@combine GreenInfo(p, p, p)
        }
        if (l != null && ref != null) LayoutOps.greenOf(l, ref.hole) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True when the mapped data has real shapes (fairway/green/bunker/water) for this hole, i.e. the illustrated map is worth showing. */
    val hasDetail: StateFlow<Boolean> = combine(layout, holeRef) { l, ref ->
        l != null && ref != null && LayoutOps.featuresForHole(l, ref.hole).any {
            it.type in setOf(FeatureType.GREEN, FeatureType.FAIRWAY, FeatureType.BUNKER, FeatureType.WATER)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val hazards: StateFlow<List<HazardInfo>> = combine(layout, holeRef, fix) { l, ref, f ->
        if (l == null || ref == null) emptyList()
        else LayoutOps.hazardsAhead(l, ref.hole, f?.point ?: ref.hole.tee)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- scoring ----------------------------------------------------------------------------
    val currentScore: StateFlow<HoleScore?> = combine(scores, hole) { s, h -> s.firstOrNull { it.hole == h } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (HoleScore) -> HoleScore) {
        val cur = currentScore.value ?: return
        var n = transform(cur)
        n = n.copy(par = n.par.coerceIn(3, 6), strokes = n.strokes.coerceIn(0, 20), putts = n.putts.coerceIn(0, 10), penalties = n.penalties.coerceIn(0, 9))
        // Green in regulation = on the green in (par - 2) strokes or fewer, i.e. strokes - putts <= par - 2.
        if (n.strokes > 0 && n.putts > 0) n = n.copy(gir = if (n.strokes - n.putts <= n.par - 2) 1 else 2)
        viewModelScope.launch { repo.setScore(n) }
    }

    fun changeHole(delta: Int) {
        val max = round.value?.holeCount ?: 18
        hole.value = (hole.value + delta).coerceIn(1, max)
        measure.value = null
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.finishRound(roundId)
            if (app.activeRoundId.value == roundId) app.activeRoundId.value = null
            onDone()
        }
    }

    // ---- GPS + shots ------------------------------------------------------------------------
    val selectedClubId = MutableStateFlow<Long?>(null)
    val autoSwing = MutableStateFlow(false)
    val armed = MutableStateFlow(false)
    val liveMeters = MutableStateFlow<Double?>(null)
    val pending = MutableStateFlow<DetectedShot?>(null)
    val message = MutableStateFlow<String?>(null)
    val swingSensorAvailable get() = app.swingSensor.isAvailable

    private var gpsJob: Job? = null
    private var swingJob: Job? = null

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

    fun stopGps() { gpsJob?.cancel(); gpsJob = null; swingJob?.cancel(); swingJob = null }

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

    fun armHere(note: String = "Đã đánh dấu vị trí bóng. Đi tới bóng — app tự đo khi bạn dừng lại.") {
        val f = fix.value ?: run { message.value = "Chưa có tín hiệu GPS."; return }
        tracker.arm(f.point); armed.value = true; liveMeters.value = 0.0; pending.value = null; message.value = note
    }

    fun finishManually() { fix.value?.let { f -> tracker.finishNow(f.point)?.let(::onShotDetected) } }

    fun cancelShot() { tracker.cancel(); armed.value = false; liveMeters.value = null; message.value = "Đã hủy cú đánh." }

    private fun onShotDetected(shot: DetectedShot) {
        armed.value = false; liveMeters.value = null
        val club = selectedClubId.value
        if (club != null) save(shot, club) else {
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
            repo.addShot(Shot(clubId = clubId, meters = shot.meters, createdAt = System.currentTimeMillis(),
                startLat = shot.start.lat, startLng = shot.start.lng, endLat = shot.end.lat, endLng = shot.end.lng, auto = true))
            val name = clubStats.value.firstOrNull { it.clubId == clubId }?.name ?: "Gậy"
            message.value = "Đã lưu: $name %.0f m".format(shot.meters)
        }
    }

    // ---- defining a hole by hand (tee, then green) -------------------------------------------------
    val placing = MutableStateFlow(Placing.NONE)

    fun startPlacing() { placing.value = Placing.TEE; measure.value = null; message.value = null }
    fun cancelPlacing() { placing.value = Placing.NONE }

    /** Map tap: sets the tee / green while defining a hole, otherwise places the measuring target. */
    fun mapTap(p: GeoPoint?) {
        val c = course.value
        when (placing.value) {
            Placing.NONE -> measure.value = p
            Placing.TEE -> if (p != null && c != null) viewModelScope.launch {
                repo.saveTee(HoleTee(c.id, hole.value, p.lat, p.lng)); placing.value = Placing.GREEN
            }
            Placing.GREEN -> if (p != null && c != null) viewModelScope.launch {
                repo.saveGreen(HoleGreen(c.id, hole.value, p.lat, p.lng)); placing.value = Placing.NONE
                message.value = "Đã lưu hố ${hole.value}."
            }
        }
    }

    fun placeHere() { fix.value?.let { mapTap(it.point) } ?: run { message.value = "Chưa có tín hiệu GPS." } }

    fun clearManualHole() {
        val c = course.value ?: return
        viewModelScope.launch { repo.clearHole(c.id, hole.value) }
    }

    fun markGreenHere() {
        val c = course.value; val f = fix.value
        if (c == null || f == null) { message.value = "Cần sân đã chọn và có tín hiệu GPS."; return }
        viewModelScope.launch {
            repo.saveGreen(HoleGreen(c.id, hole.value, f.point.lat, f.point.lng))
            message.value = "Đã lưu vị trí green hố ${hole.value}."
        }
    }

    override fun onCleared() { stopGps() }
}
