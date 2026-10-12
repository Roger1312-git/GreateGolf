package com.greategolf.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greategolf.GolfApp
import com.greategolf.domain.ScoringMix
import com.greategolf.domain.StatsCalculator
import com.greategolf.domain.StatsSummary
import com.greategolf.ui.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(app: GolfApp) : ViewModel() {
    val stats = app.repository.results.map { StatsCalculator.summarize(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private val Eagle = Color(0xFFFFC107)
private val Birdie = Color(0xFF2E7D32)
private val ParC = Color(0xFF66BB6A)
private val Bogey = Color(0xFFFF9800)
private val DoubleC = Color(0xFFE53935)

@Composable
fun StatsScreen() {
    val vm = appViewModel { StatsViewModel(it) }
    val s by vm.stats.collectAsState()
    val stats = s

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Thống kê", style = MaterialTheme.typography.headlineSmall)
        if (stats == null || stats.roundsPlayed == 0) {
            Text("Chưa có dữ liệu. Chơi một vòng và ghi điểm để xem thống kê (điểm, fairway, GIR, putt, handicap).")
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Big("Handicap (ước tính)", stats.handicapIndex?.let { "%.1f".format(it) } ?: "—", Modifier.weight(1f))
            Big("Điểm TB (18 hố)", stats.avgScore?.let { "%.1f".format(it) } ?: "—", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Big("Tốt nhất", stats.bestScore?.toString() ?: "—", Modifier.weight(1f))
            Big("Putt / 18 hố", stats.puttsPer18?.let { "%.1f".format(it) } ?: "—", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Big("Fairway trúng", stats.firPct?.let { "%.0f%%".format(it) } ?: "—", Modifier.weight(1f))
            Big("Green in regulation", stats.girPct?.let { "%.0f%%".format(it) } ?: "—", Modifier.weight(1f))
        }

        Section("Phân bố điểm số") { MixBar(stats.mix) }
        Section("Điểm trung bình theo loại hố") {
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Par("Par 3", stats.avgPar3, 3); Par("Par 4", stats.avgPar4, 4); Par("Par 5", stats.avgPar5, 5)
            }
        }
        Section("Xu hướng điểm (${stats.trend.size} vòng gần nhất)") {
            if (stats.trend.size < 2) Text("Cần ít nhất 2 vòng 18 hố.", style = MaterialTheme.typography.bodySmall)
            else Trend(stats.trend)
        }
        Text(
            "Handicap là ước tính theo công thức WHS với rating = par và slope 113 (chưa có rating/slope từng sân Việt Nam) — chỉ để tham khảo, không phải chỉ số chính thức.",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun Big(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun Par(label: String, avg: Double?, par: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(avg?.let { "%.2f".format(it) } ?: "—", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
        if (avg != null) Text("%+.2f".format(avg - par), style = MaterialTheme.typography.labelSmall,
            color = if (avg - par > 0.5) Bogey else Birdie)
    }
}

@Composable
private fun MixBar(mix: ScoringMix) {
    val parts = listOf(
        Triple("Eagle+", mix.eagleOrBetter, Eagle), Triple("Birdie", mix.birdie, Birdie), Triple("Par", mix.par, ParC),
        Triple("Bogey", mix.bogey, Bogey), Triple("Double+", mix.doubleOrWorse, DoubleC),
    )
    val total = mix.total.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(22.dp).clip(MaterialTheme.shapes.small)) {
        parts.filter { it.second > 0 }.forEach { (_, n, c) -> Box(Modifier.weight(n.toFloat()).height(22.dp).background(c)) }
    }
    parts.forEach { (name, n, c) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(c))
            Text("  $name", Modifier.width(90.dp))
            Text("$n hố · %.0f%%".format(100.0 * n / total), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Trend(scores: List<Int>) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val lo = scores.min() - 2; val hi = scores.max() + 2
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val w = size.width; val h = size.height
        drawLine(grid, Offset(0f, h), Offset(w, h), 2f)
        fun x(i: Int) = w * i / (scores.size - 1)
        fun y(v: Int) = h - h * (v - lo) / (hi - lo).toFloat()
        val path = Path()
        scores.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        drawPath(path, line, style = Stroke(width = 4f))
        scores.forEachIndexed { i, v -> drawCircle(line, 7f, Offset(x(i), y(v))) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${scores.first()}", style = MaterialTheme.typography.labelSmall)
        Text("${scores.last()} (mới nhất)", style = MaterialTheme.typography.labelSmall)
    }
}
