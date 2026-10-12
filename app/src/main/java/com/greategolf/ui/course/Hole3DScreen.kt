package com.greategolf.ui.course

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greategolf.domain.LayoutOps
import com.greategolf.ui.RequireLocationOptional
import com.greategolf.ui.appViewModel
import com.greategolf.ui.fmtDistance

@Composable
fun Hole3DScreen(courseId: String, holeIndex: Int, onBack: () -> Unit) {
    val vm = appViewModel { CourseViewModel(it, courseId, holeIndex) }
    val state by vm.state.collectAsState()
    val selected by vm.selectedHole.collectAsState()
    val fix by vm.fix.collectAsState()
    val target by vm.measurePoint.collectAsState()
    val green by vm.green.collectAsState()
    val layout = (state as? LayoutState.Ready)?.layout
    var satellite by rememberSaveable { mutableStateOf(true) }
    var yards by rememberSaveable { mutableStateOf(false) }

    RequireLocationOptional { granted ->
        DisposableEffect(granted) {
            if (granted) vm.startGps()
            onDispose { vm.stopGps() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
                Text("${vm.course.name} · 3D", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
        }
        if (layout == null || layout.holes.isEmpty()) {
            Text(if (state is LayoutState.Loading) "Đang tải dữ liệu sân…" else "Chưa có dữ liệu hố cho sân này.", Modifier.padding(16.dp))
            return@Column
        }
        val idx = (selected ?: 0).coerceIn(0, layout.holes.lastIndex)
        val hole = layout.holes[idx]

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Hole3DView(layout, hole, green, fix?.point, target, onTarget = vm::measure, yards = yards, satellite = satellite, modifier = Modifier.fillMaxSize())
            Surface(Modifier.align(Alignment.TopStart).padding(8.dp), shape = MaterialTheme.shapes.medium, color = Color(0xCC10301A), contentColor = Color.White) {
                Column(Modifier.padding(10.dp)) {
                    Text("Hố ${hole.ref}" + (hole.par?.let { " · Par $it" } ?: ""), fontWeight = FontWeight.Bold)
                    Text(fmtDistance(hole.lengthM), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Vuốt: xoay · Kéo dọc: nghiêng · Chụm: zoom · Chạm: đặt điểm đo · Giữ: xóa",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { vm.select((idx - 1).coerceAtLeast(0)) }, enabled = idx > 0) { Text("◀") }
            FilterChip(selected = satellite, onClick = { satellite = !satellite }, label = { Text(if (satellite) "Vệ tinh" else "Minh họa") })
            FilterChip(selected = yards, onClick = { yards = !yards }, label = { Text(if (yards) "yd" else "m") })
            OutlinedButton(onClick = { vm.select((idx + 1).coerceAtMost(layout.holes.lastIndex)) }, enabled = idx < layout.holes.lastIndex) { Text("▶") }
        }
        Text("Imagery © Esri, Maxar, Earthstar Geographics · Dữ liệu sân © OpenStreetMap", Modifier.padding(start = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}
