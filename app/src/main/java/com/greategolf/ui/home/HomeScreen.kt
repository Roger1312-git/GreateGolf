package com.greategolf.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greategolf.GolfApp
import com.greategolf.domain.StatsCalculator
import com.greategolf.ui.appViewModel
import com.greategolf.ui.fmtToPar
import com.greategolf.ui.score.RoundRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormat
import java.util.Date

class HomeViewModel(private val app: GolfApp) : ViewModel() {
    val stats = app.repository.results.map { StatsCalculator.summarize(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val activeRoundId = app.activeRoundId
    val recent = combine(app.repository.rounds, app.repository.roundTotals) { rounds, totals ->
        val byId = totals.associateBy { it.roundId }
        rounds.take(4).map { r -> RoundRow(r, byId[r.id]?.strokes ?: 0, byId[r.id]?.par ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private val Lime = Color(0xFFC6FF00)

@Composable
fun HomeScreen(
    onPlay: () -> Unit, onOpenRound: (Long) -> Unit, onStats: () -> Unit,
    onCourses: () -> Unit, onClubs: () -> Unit, onSwing: () -> Unit, onHistory: () -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it) }
    val stats by vm.stats.collectAsState()
    val active by vm.activeRoundId.collectAsState()
    val recent by vm.recent.collectAsState()
    val df = androidx.compose.runtime.remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Box(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF0E3B14), Color(0xFF2E7D32))))
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("GreateGolf", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                Text("Handicap Index (ước tính)", color = Color.White, fontSize = 13.sp)
                Text(stats?.handicapIndex?.let { "%.1f".format(it) } ?: "—", color = Lime, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (stats?.handicapIndex == null) "Cần ít nhất 3 vòng 18 hố đã ghi đủ điểm" else "Tính từ ${stats?.fullRounds} vòng 18 hố gần nhất (rating = par, slope 113)",
                    color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
                )
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Button(
                onClick = onPlay, Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Color.Black),
            ) { Text(if (active != null) "Tiếp tục vòng đang chơi" else "Bắt đầu vòng mới", fontSize = 18.sp, fontWeight = FontWeight.Bold) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Điểm TB", stats?.avgScore?.let { "%.1f".format(it) } ?: "—", Modifier.weight(1f))
                Tile("Tốt nhất", stats?.bestScore?.toString() ?: "—", Modifier.weight(1f))
                Tile("Putt/18", stats?.puttsPer18?.let { "%.0f".format(it) } ?: "—", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Fairway", stats?.firPct?.let { "%.0f%%".format(it) } ?: "—", Modifier.weight(1f))
                Tile("GIR", stats?.girPct?.let { "%.0f%%".format(it) } ?: "—", Modifier.weight(1f))
                Tile("Số vòng", "${stats?.roundsPlayed ?: 0}", Modifier.weight(1f))
            }
            OutlinedButton(onClick = onStats, Modifier.fillMaxWidth()) { Text("Xem thống kê chi tiết") }

            Text("Công cụ", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Action("Bản đồ sân & 3D", onCourses, Modifier.weight(1f))
                Action("Bộ gậy", onClubs, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Action("Phân tích swing", onSwing, Modifier.weight(1f))
                Action("Lịch sử scorecard", onHistory, Modifier.weight(1f))
            }

            if (recent.isNotEmpty()) {
                Text("Vòng gần đây", style = MaterialTheme.typography.titleMedium)
                recent.forEach { row ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenRound(row.round.id) }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(row.round.courseName, style = MaterialTheme.typography.titleSmall)
                                Text(df.format(Date(row.round.startedAt)) + " · ${row.round.holeCount} hố", style = MaterialTheme.typography.bodySmall)
                            }
                            if (row.strokes > 0) Column(horizontalAlignment = Alignment.End) {
                                Text("${row.strokes}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Text(fmtToPar(row.strokes - row.par), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit, modifier: Modifier) {
    Card(modifier.clickable(onClick = onClick)) {
        Text(label, Modifier.padding(18.dp).fillMaxWidth(), style = MaterialTheme.typography.titleSmall)
    }
}
