package com.greategolf.ui.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greategolf.GolfApp
import com.greategolf.data.Course
import com.greategolf.domain.CourseLayout
import com.greategolf.domain.GeoFix
import com.greategolf.domain.GeoPoint
import com.greategolf.domain.GreenInfo
import com.greategolf.domain.LayoutOps
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LayoutState {
    data object Loading : LayoutState
    data class Ready(val layout: CourseLayout) : LayoutState
    data object NoData : LayoutState
    data class Failed(val message: String) : LayoutState
}

class CourseViewModel(private val app: GolfApp, val courseId: String, initialHole: Int?) : ViewModel() {
    val course: Course = app.courses.first { it.id == courseId }

    val state = MutableStateFlow<LayoutState>(LayoutState.Loading)
    /** Index into layout.holes. */
    val selectedHole = MutableStateFlow(initialHole)
    val satellite = MutableStateFlow(true)
    val fix = MutableStateFlow<GeoFix?>(null)
    val measurePoint = MutableStateFlow<GeoPoint?>(null)

    val green = combine(state, selectedHole) { s, i ->
        val layout = (s as? LayoutState.Ready)?.layout
        val hole = i?.let { layout?.holes?.getOrNull(it) }
        if (layout != null && hole != null) LayoutOps.greenOf(layout, hole) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var gpsJob: Job? = null

    init { load(false) }

    fun load(force: Boolean) {
        state.value = LayoutState.Loading
        viewModelScope.launch {
            state.value = try {
                val l = app.layouts.load(course, force)
                if (l.isEmpty) LayoutState.NoData else LayoutState.Ready(l)
            } catch (e: Exception) {
                LayoutState.Failed(e.message ?: "Lỗi mạng")
            }
        }
    }

    fun select(i: Int?) { selectedHole.value = i; measurePoint.value = null }
    fun toggleSatellite() { satellite.value = !satellite.value }
    fun measure(p: GeoPoint?) { measurePoint.value = p }

    fun nearestHoleIndex(): Int? {
        val f = fix.value ?: return null
        val layout = (state.value as? LayoutState.Ready)?.layout ?: return null
        return layout.holes.withIndex().minByOrNull { (_, h) ->
            h.line.minOf { com.greategolf.domain.GeoMath.distanceMeters(it, f.point) }
        }?.index
    }

    fun startGps() {
        if (gpsJob?.isActive == true) return
        gpsJob = viewModelScope.launch { app.location.fixes().collect { fix.value = it } }
    }

    fun stopGps() { gpsJob?.cancel(); gpsJob = null }
    override fun onCleared() { stopGps() }
}
