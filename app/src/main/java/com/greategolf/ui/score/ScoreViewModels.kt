package com.greategolf.ui.score

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greategolf.GolfApp
import com.greategolf.data.Course
import com.greategolf.data.HoleScore
import com.greategolf.data.Round
import com.greategolf.data.RoundTotal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoundRow(val round: Round, val strokes: Int, val par: Int)

class RoundsViewModel(private val app: GolfApp) : ViewModel() {
    val courses: List<Course> = app.courses
    val activeRoundId: StateFlow<Long?> = app.activeRoundId

    val rows = combine(app.repository.rounds, app.repository.roundTotals) { rounds, totals ->
        val byId = totals.associateBy(RoundTotal::roundId)
        rounds.map { r -> RoundRow(r, byId[r.id]?.strokes ?: 0, byId[r.id]?.par ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun newRound(course: Course?, name: String, holes: Int, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = app.repository.startRound(course, name.ifBlank { course?.name ?: "Vòng golf" }, holes, System.currentTimeMillis())
            app.activeRoundId.value = id
            onCreated(id)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            app.repository.deleteRound(id)
            if (app.activeRoundId.value == id) app.activeRoundId.value = null
        }
    }

    fun setActive(id: Long) { app.activeRoundId.value = id }
}

class RoundViewModel(private val app: GolfApp, private val roundId: Long) : ViewModel() {
    val round = app.repository.round(roundId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val scores = app.repository.scores(roundId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun update(score: HoleScore) {
        viewModelScope.launch { app.repository.setScore(score.copy(
            par = score.par.coerceIn(3, 6),
            strokes = score.strokes.coerceIn(0, 20),
            putts = score.putts.coerceIn(0, 10),
        )) }
    }
}
