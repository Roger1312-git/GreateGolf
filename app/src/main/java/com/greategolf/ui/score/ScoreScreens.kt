package com.greategolf.ui.score

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greategolf.data.Course
import com.greategolf.data.HoleScore
import com.greategolf.ui.appViewModel
import com.greategolf.ui.fmtToPar
import java.text.DateFormat
import java.util.Date

@Composable
fun RoundsScreen(onOpen: (Long) -> Unit) {
    val vm = appViewModel { RoundsViewModel(it) }
    val rows by vm.rows.collectAsState()
    val active by vm.activeRoundId.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text("Scorecard", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
            if (rows.isEmpty()) {
                Text("Chưa có vòng golf nào. Nhấn + để bắt đầu.", Modifier.padding(16.dp))
            }
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { it.round.id }) { row ->
                    Card(
                        Modifier.fillMaxWidth().clickable { vm.setActive(row.round.id); onOpen(row.round.id) },
                        colors = if (row.round.id == active) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(row.round.courseName, style = MaterialTheme.typography.titleMedium)
                                Text(df.format(Date(row.round.startedAt)) + " · ${row.round.holeCount} hố", style = MaterialTheme.typography.bodySmall)
                                if (row.round.id == active) Text("Đang chơi", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            if (row.strokes > 0) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${row.strokes}", style = MaterialTheme.typography.headlineSmall)
                                    Text(fmtToPar(row.strokes - row.par), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { vm.delete(row.round.id) }) { Icon(Icons.Filled.Delete, "Xóa") }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { showNew = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, "Vòng mới")
        }
    }

    if (showNew) {
        NewRoundDialog(vm.courses, onDismiss = { showNew = false }) { course, name, holes ->
            showNew = false
            vm.newRound(course, name, holes, onOpen)
        }
    }
}

@Composable
private fun NewRoundDialog(courses: List<Course>, onDismiss: () -> Unit, onCreate: (Course?, String, Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<Course?>(null) }
    var holes by remember { mutableIntStateOf(18) }
    val matches = remember(query) {
        if (query.isBlank()) emptyList() else courses.filter { it.name.contains(query, true) || it.province.contains(query, true) }.take(5)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vòng golf mới") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = chosen?.name ?: query,
                    onValueChange = { chosen = null; query = it },
                    label = { Text("Tên sân (tìm hoặc tự nhập)") },
                    singleLine = true,
                )
                if (chosen == null) matches.forEach { c ->
                    Text("${c.name} · ${c.province}", Modifier.fillMaxWidth().clickable { chosen = c; holes = minOf(c.holes, 18) }.padding(vertical = 6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = holes == 18, onClick = { holes = 18 }, label = { Text("18 hố") })
                    FilterChip(selected = holes == 9, onClick = { holes = 9 }, label = { Text("9 hố") })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(chosen, chosen?.name ?: query, holes) }) { Text("Bắt đầu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundScreen(roundId: Long, onBack: () -> Unit) {
    val vm = appViewModel { RoundViewModel(it, roundId) }
    val round by vm.round.collectAsState()
    val scores by vm.scores.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(round?.courseName ?: "") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
        )
        Summary(scores)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text("Hố", Modifier.width(36.dp), fontWeight = FontWeight.Bold)
            Text("Par", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("Gậy", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("Putt", Modifier.weight(1f), fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f)) {
            items(scores, key = { it.hole }) { s ->
                HoleRow(s, onChange = vm::update)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Summary(scores: List<HoleScore>) {
    val played = scores.filter { it.strokes > 0 }
    val strokes = played.sumOf { it.strokes }
    val par = played.sumOf { it.par }
    val putts = played.sumOf { it.putts }
    val front = scores.filter { it.hole <= 9 && it.strokes > 0 }.sumOf { it.strokes }
    val back = scores.filter { it.hole > 9 && it.strokes > 0 }.sumOf { it.strokes }
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("Tổng", if (strokes > 0) "$strokes" else "-")
            Stat("So với par", if (strokes > 0) fmtToPar(strokes - par) else "-")
            Stat("Out", if (front > 0) "$front" else "-")
            if (scores.size > 9) Stat("In", if (back > 0) "$back" else "-")
            Stat("Putt", if (putts > 0) "$putts" else "-")
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HoleRow(s: HoleScore, onChange: (HoleScore) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${s.hole}", Modifier.width(36.dp), style = MaterialTheme.typography.titleMedium)
        Stepper(s.par, { onChange(s.copy(par = it)) }, Modifier.weight(1f))
        Stepper(s.strokes, { onChange(s.copy(strokes = if (s.strokes == 0 && it == 1) s.par else it)) }, Modifier.weight(1f), highlight = scoreColor(s))
        Stepper(s.putts, { onChange(s.copy(putts = it)) }, Modifier.weight(1f))
    }
}

@Composable
private fun scoreColor(s: HoleScore) = when {
    s.strokes == 0 -> MaterialTheme.colorScheme.onSurface
    s.strokes < s.par -> MaterialTheme.colorScheme.primary
    s.strokes > s.par -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    highlight: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(value - 1) }, Modifier.height(36.dp).width(32.dp)) { Icon(Icons.Filled.Remove, "-") }
        Text(if (value == 0) "-" else "$value", Modifier.widthIn(min = 18.dp), color = highlight, fontWeight = FontWeight.Bold)
        IconButton(onClick = { onChange(value + 1) }, Modifier.height(36.dp).width(32.dp)) { Icon(Icons.Filled.Add, "+") }
    }
}
