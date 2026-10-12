package com.greategolf.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greategolf.domain.GeoMath
import com.greategolf.domain.LayoutOps
import com.greategolf.ui.RequireLocationOptional
import com.greategolf.ui.appViewModel
import kotlin.math.roundToInt

private val Dark = Color(0xFF10301A)

/** Course page: a card with an illustrated map for every hole (like the "Maps" strip in Huawei/Garmin course pages). */
@Composable
fun HoleGridScreen(courseId: String, onBack: () -> Unit, onOpenHole: (Int) -> Unit) {
    val vm = appViewModel { CourseViewModel(it, courseId, null) }
    val state by vm.state.collectAsState()
    val layout = (state as? LayoutState.Ready)?.layout

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
            Column(Modifier.weight(1f)) {
                Text(vm.course.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                if (layout != null) {
                    val par = layout.holes.sumOf { it.par ?: 0 }
                    val yd = layout.holes.sumOf { GeoMath.metersToYards(it.lengthM) }.roundToInt()
                    Text("${layout.holes.size} hố" + (if (par > 0) " · Par $par" else "") + " · $yd yd", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        when {
            layout == null -> Text(if (state is LayoutState.Loading) "Đang tải dữ liệu sân…" else "Chưa có dữ liệu hố cho sân này trên OpenStreetMap.", Modifier.padding(16.dp))
            layout.holes.isEmpty() -> Text("Sân này chưa được vẽ các hố (golf=hole) trên OpenStreetMap.", Modifier.padding(16.dp))
            else -> LazyVerticalGrid(GridCells.Fixed(2), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(layout.holes) { i, hole ->
                    Card(Modifier.clickable { onOpenHole(i) }) {
                        Column {
                            Text(
                                "Hố ${hole.ref}" + (hole.par?.let { " · Par $it" } ?: ""),
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                            )
                            Box(Modifier.fillMaxWidth().aspectRatio(0.62f).clip(RoundedCornerShape(10.dp)).background(Dark)) {
                                HoleMap2D(layout, hole, LayoutOps.greenOf(layout, hole), interactive = false)
                            }
                            Text("%.0f yd".format(GeoMath.metersToYards(hole.lengthM)), Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

/** One hole, big: illustrated map with distances, target, and a green close-up. */
@Composable
fun HoleMapScreen(courseId: String, holeIndex: Int, onBack: () -> Unit, on3D: (Int) -> Unit) {
    val vm = appViewModel { CourseViewModel(it, courseId, holeIndex) }
    val state by vm.state.collectAsState()
    val selected by vm.selectedHole.collectAsState()
    val fix by vm.fix.collectAsState()
    val target by vm.measurePoint.collectAsState()
    val green by vm.green.collectAsState()
    val layout = (state as? LayoutState.Ready)?.layout
    var greenMode by rememberSaveable { mutableStateOf(false) }
    var yards by rememberSaveable { mutableStateOf(false) }

    RequireLocationOptional { granted ->
        DisposableEffect(granted) { if (granted) vm.startGps(); onDispose { vm.stopGps() } }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
            Text(vm.course.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
        if (layout == null || layout.holes.isEmpty()) {
            Text(if (state is LayoutState.Loading) "Đang tải dữ liệu sân…" else "Chưa có dữ liệu hố cho sân này.", Modifier.padding(16.dp))
            return@Column
        }
        val idx = (selected ?: 0).coerceIn(0, layout.holes.lastIndex)
        val hole = layout.holes[idx]
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HoleMap2D(layout, hole, green, player = fix?.point, target = target, onTarget = vm::measure, yards = yards, greenMode = greenMode)
            Surface(Modifier.align(Alignment.TopStart).padding(8.dp), shape = MaterialTheme.shapes.medium, color = Color(0xCC10301A), contentColor = Color.White) {
                Column(Modifier.padding(10.dp)) {
                    Text("Hố ${hole.ref}" + (hole.par?.let { " · Par $it" } ?: ""), fontWeight = FontWeight.Bold)
                    val len = hole.lengthM
                    Text("%.0f %s".format(if (yards) GeoMath.metersToYards(len) else len, if (yards) "yd" else "m"), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Chạm: đặt điểm đo · Giữ: xóa · Chụm/kéo: zoom/di chuyển", Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { vm.select((idx - 1).coerceAtLeast(0)) }, enabled = idx > 0) { Text("◀") }
            FilterChip(selected = !greenMode, onClick = { greenMode = false }, label = { Text("Cả hố") })
            FilterChip(selected = greenMode, onClick = { greenMode = true }, label = { Text("Green") })
            FilterChip(selected = yards, onClick = { yards = !yards }, label = { Text(if (yards) "yd" else "m") })
            OutlinedButton(onClick = { on3D(idx) }) { Text("3D") }
            OutlinedButton(onClick = { vm.select((idx + 1).coerceAtMost(layout.holes.lastIndex)) }, enabled = idx < layout.holes.lastIndex) { Text("▶") }
        }
    }
}
