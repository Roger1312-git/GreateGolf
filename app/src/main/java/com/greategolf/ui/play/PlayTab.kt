package com.greategolf.ui.play

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.greategolf.GolfApp
import com.greategolf.data.Course
import com.greategolf.domain.GeoMath
import com.greategolf.domain.GeoPoint
import com.greategolf.ui.RequireLocationOptional
import com.greategolf.ui.appViewModel
import com.greategolf.ui.score.RoundsViewModel
import com.greategolf.ui.course.fold

/** The "Chơi" tab: the live hole view while a round is active, otherwise the course picker. */
@Composable
fun PlayTab(onOpenScorecard: (Long) -> Unit, onOpen3D: (String, Int) -> Unit, onFinished: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as GolfApp
    val active by app.activeRoundId.collectAsState()
    val id = active
    if (id != null) RoundPlayScreen(id, onOpenScorecard, onOpen3D, onFinished) else StartRoundScreen()
}

@Composable
private fun StartRoundScreen() {
    val vm = appViewModel { RoundsViewModel(it) }
    val app = LocalContext.current.applicationContext as GolfApp
    var query by remember { mutableStateOf("") }
    var me by remember { mutableStateOf<GeoPoint?>(null) }
    var chosen by remember { mutableStateOf<Course?>(null) }
    var free by remember { mutableStateOf(false) }

    RequireLocationOptional { granted -> LaunchedEffect(granted) { if (granted) me = app.location.lastKnown() } }

    val courses = remember(query, me) {
        val q = fold(query)
        vm.courses
            .filter { q.isEmpty() || fold(it.name).contains(q) || fold(it.province).contains(q) }
            .sortedBy { c -> me?.let { GeoMath.distanceMeters(it, c.location) } ?: 0.0 }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Bắt đầu vòng mới", style = MaterialTheme.typography.headlineSmall)
        Text(if (me != null) "Sân gần bạn nhất ở trên cùng" else "Chọn sân để chơi với GPS", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Tìm sân hoặc tỉnh…") })
        OutlinedButton(onClick = { free = true }, Modifier.fillMaxWidth()) { Text("Vòng tự do (không chọn sân)") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(courses, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth().clickable { chosen = c }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            Text("${c.province} · ${c.holes} hố", style = MaterialTheme.typography.bodySmall)
                        }
                        me?.let { Text("%.0f km".format(GeoMath.distanceMeters(it, c.location) / 1000), style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        }
    }

    val pick = chosen
    if (pick != null || free) HolesDialog(pick?.name ?: "Vòng tự do", pick?.holes ?: 18, onDismiss = { chosen = null; free = false }) { holes ->
        chosen = null; free = false
        vm.newRound(pick, pick?.name ?: "Vòng tự do", holes) { }
    }
}

@Composable
private fun HolesDialog(title: String, courseHoles: Int, onDismiss: () -> Unit, onStart: (Int) -> Unit) {
    var holes by remember { mutableIntStateOf(if (courseHoles >= 18) 18 else 9) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = holes == 18, onClick = { holes = 18 }, label = { Text("18 hố") })
                FilterChip(selected = holes == 9, onClick = { holes = 9 }, label = { Text("9 hố") })
            }
        },
        confirmButton = { TextButton(onClick = { onStart(holes) }) { Text("Bắt đầu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}
