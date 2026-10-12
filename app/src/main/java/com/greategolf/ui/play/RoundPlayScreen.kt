package com.greategolf.ui.play

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greategolf.data.HoleScore
import com.greategolf.domain.CourseLayout
import com.greategolf.domain.FeatureType
import com.greategolf.domain.GeoMath
import com.greategolf.domain.GeoPoint
import com.greategolf.ui.RequireLocationOptional
import com.greategolf.ui.appViewModel
import com.greategolf.ui.course.CourseMap
import com.greategolf.ui.course.Hole3DView
import com.greategolf.ui.course.HoleMap2D
import com.greategolf.ui.course.LayoutState
import com.greategolf.ui.fmtToPar

private val Panel = Color(0xEE10301A)
private val Lime = Color(0xFFC6FF00)

@Composable
fun RoundPlayScreen(
    roundId: Long,
    onOpenScorecard: (Long) -> Unit,
    onOpen3D: (courseId: String, holeIndex: Int) -> Unit,
    onFinished: (Long) -> Unit,
) {
    val vm = appViewModel(key = "round$roundId") { RoundPlayViewModel(it, roundId) }
    val round by vm.round.collectAsState()
    val course by vm.course.collectAsState()
    val layoutState by vm.layoutState.collectAsState()
    val hole by vm.hole.collectAsState()
    val ref by vm.holeRef.collectAsState()
    val green by vm.green.collectAsState()
    val hazards by vm.hazards.collectAsState()
    val fix by vm.fix.collectAsState()
    val measure by vm.measure.collectAsState()
    val score by vm.currentScore.collectAsState()
    val scores by vm.scores.collectAsState()
    val manual by vm.manualHole.collectAsState()
    val hasDetail by vm.hasDetail.collectAsState()
    val placing by vm.placing.collectAsState()

    var yards by rememberSaveable { mutableStateOf(false) }
    // 0 = illustrated 2D hole map (default), 1 = satellite perspective, 2 = OSM/satellite web map
    var viewMode by rememberSaveable { mutableIntStateOf(0) }
    var greenMode by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmFinish by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    RequireLocationOptional { granted ->
        DisposableEffect(granted) {
            if (granted) vm.startGps()
            val act = ctx as? Activity
            act?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { vm.stopGps(); act?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    fun unit(m: Double) = if (yards) GeoMath.metersToYards(m) else m
    val unitLabel = if (yards) "yd" else "m"
    val layout = (layoutState as? LayoutState.Ready)?.layout
    val par = score?.par ?: ref?.hole?.par ?: 4
    val player = fix?.point

    val viewLayout = layout ?: CourseLayout(emptyList(), emptyList())
    // Illustrated map only makes sense when OSM has shapes for the hole; a hand-defined hole shows satellite.
    val effMode = when {
        placing != Placing.NONE -> 2
        viewMode == 0 && !hasDetail -> 1
        else -> viewMode
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF1B3A22))) {
        val holeForView = ref?.hole
        if (effMode == 0 && holeForView != null) {
            HoleMap2D(
                layout = viewLayout, hole = holeForView, green = green, player = player, target = measure,
                onTarget = vm::mapTap, yards = yards, greenMode = greenMode,
                modifier = Modifier.fillMaxSize(), bottomPad = 250.dp,
            )
        } else if (effMode == 1 && holeForView != null) {
            Hole3DView(
                layout = viewLayout, hole = holeForView, green = green, player = player, target = measure,
                onTarget = vm::mapTap, yards = yards, satellite = true,
                modifier = Modifier.fillMaxSize(), bottomPad = 250.dp,
            )
        } else course?.let { c ->
            CourseMap(
                center = player?.takeIf { GeoMath.distanceMeters(it, c.location) < 5_000 } ?: c.location,
                layout = layout, selected = ref?.index?.takeIf { it >= 0 }, satellite = true,
                player = player, measure = measure, onTap = vm::mapTap, modifier = Modifier.fillMaxSize(), manual = manual,
            )
        }

        // ---- top bar -------------------------------------------------------------------------
        Column(Modifier.align(Alignment.TopStart).fillMaxWidth()) {
            Surface(color = Panel, contentColor = Color.White) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
                        Text("$hole", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(course?.name ?: round?.courseName ?: "", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        val len = ref?.hole?.lengthM
                        Text("Par $par" + (len?.let { " · %.0f $unitLabel".format(unit(it)) } ?: ""),
                            fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(onClick = { yards = !yards }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text(unitLabel) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu", tint = Color.White) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Scorecard") }, onClick = { menu = false; onOpenScorecard(roundId) })
                            if (ref != null) DropdownMenuItem(
                                text = { Text("Kiểu bản đồ: " + when (viewMode) { 0 -> "Minh họa 2D → Vệ tinh"; 1 -> "Vệ tinh → Bản đồ"; else -> "Bản đồ → Minh họa 2D" }) },
                                onClick = { menu = false; viewMode = (viewMode + 1) % 3 },
                            )
                            if (effMode == 0 && ref != null) DropdownMenuItem(
                                text = { Text(if (greenMode) "Xem cả hố" else "Phóng to green") },
                                onClick = { menu = false; greenMode = !greenMode },
                            )
                            val idx = ref?.index
                            if (course != null && idx != null && idx >= 0) DropdownMenuItem(text = { Text("Xem 3D hố này") }, onClick = { menu = false; onOpen3D(course!!.id, idx) })
                            DropdownMenuItem(text = { Text("Đánh dấu green tại vị trí tôi") }, onClick = { menu = false; vm.markGreenHere() })
                            if (course != null) DropdownMenuItem(text = { Text("Tự đặt tee/green hố $hole") }, onClick = { menu = false; vm.startPlacing() })
                            if (manual != null) DropdownMenuItem(text = { Text("Xóa tee/green tự đặt") }, onClick = { menu = false; vm.clearManualHole() })
                            DropdownMenuItem(text = { Text("Tải lại dữ liệu sân") }, onClick = { menu = false; vm.reloadLayout() })
                            DropdownMenuItem(text = { Text("Kết thúc vòng") }, onClick = { menu = false; confirmFinish = true })
                        }
                    }
                }
            }

            HoleTabs(count = round?.holeCount ?: 18, selected = hole, scores = scores, onSelect = { vm.hole.value = it; vm.measure.value = null; vm.cancelPlacing() })

            if (placing != Placing.NONE) {
                Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = Panel, contentColor = Color.White)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (placing == Placing.TEE) "Hố $hole · Bước 1/2: chạm vào vị trí TEE trên bản đồ" else "Hố $hole · Bước 2/2: chạm vào vị trí GREEN trên bản đồ",
                            fontWeight = FontWeight.Bold)
                        Text("Chỉ cần làm một lần cho mỗi hố — app nhớ cho các vòng sau.", fontSize = 12.sp, color = Color(0xFFB0BEC5))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = vm::placeHere, enabled = player != null) { Text("Dùng vị trí GPS của tôi") }
                            OutlinedButton(onClick = vm::cancelPlacing, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Hủy") }
                        }
                    }
                }
            } else if (ref == null && layoutState !is LayoutState.Loading) {
                Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = Panel, contentColor = Color.White)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Chưa có bản đồ cho hố $hole", fontWeight = FontWeight.Bold)
                        Text(
                            (if (layoutState is LayoutState.Failed) "Không tải được dữ liệu sân (cần Internet). " else "OpenStreetMap chưa có hố này. ") +
                                "Bạn có thể tự đặt tee và green (2 lần chạm) để có bản đồ riêng của hố.",
                            fontSize = 12.sp, color = Color(0xFFB0BEC5),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = vm::startPlacing, enabled = course != null) { Text("Đặt tee & green") }
                            OutlinedButton(onClick = vm::reloadLayout, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Tải lại dữ liệu sân") }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                // hazards (left)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    hazards.take(4).forEach { h ->
                        val water = h.type == FeatureType.WATER
                        Surface(color = Panel, contentColor = Color.White, shape = MaterialTheme.shapes.small) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(if (water) Color(0xFF42A5F5) else Color(0xFFF3E3A6)))
                                Text(
                                    (if (water) "  Nước " else "  Bunker ") + "%.0f–%.0f".format(unit(h.frontM), unit(h.backM)),
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
                // front / centre / back (right)
                DistancePanel(
                    back = green?.let { g -> player?.let { unit(GeoMath.distanceMeters(it, g.back)) } },
                    center = green?.let { g -> player?.let { unit(GeoMath.distanceMeters(it, g.center)) } },
                    front = green?.let { g -> player?.let { unit(GeoMath.distanceMeters(it, g.front)) } },
                    hasGreen = green != null, hasFix = player != null, unit = unitLabel,
                )
            }
            measure?.let { m ->
                Surface(Modifier.padding(horizontal = 8.dp), color = Panel, contentColor = Color.White, shape = MaterialTheme.shapes.small) {
                    Text(
                        buildList {
                            player?.let { add("từ bạn %.0f $unitLabel".format(unit(GeoMath.distanceMeters(it, m)))) }
                            green?.let { add("tới green %.0f $unitLabel".format(unit(GeoMath.distanceMeters(m, it.center)))) }
                        }.joinToString(" · ").ifEmpty { "Điểm đo" },
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (layoutState is LayoutState.Loading) Text("Đang tải dữ liệu sân…", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp))
        }

        // ---- bottom panel --------------------------------------------------------------------
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = Panel, contentColor = Color.White,
            shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { vm.changeHole(-1) }, enabled = hole > 1) { Text("◀") }
                    Text("Hố $hole / ${round?.holeCount ?: 18}", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    FilledTonalIconButton(onClick = { vm.changeHole(1) }, enabled = hole < (round?.holeCount ?: 18)) { Text("▶") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Ghi điểm") })
                    FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Đo cú đánh") })
                }
                if (tab == 0) ScoreEntry(score, vm, scores) else ShotPanel(vm)
            }
        }
    }

    if (confirmFinish) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmFinish = false },
        title = { Text("Kết thúc vòng?") },
        text = { Text("Các hố chưa ghi điểm sẽ để trống. Bạn vẫn xem lại được trong Scorecard.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmFinish = false; vm.finish { onFinished(roundId) } }) { Text("Kết thúc") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmFinish = false }) { Text("Tiếp tục chơi") } },
    )
}

/** Numbered hole tabs (1..N): tap to show that hole's map. Scored holes are filled. */
@Composable
private fun HoleTabs(count: Int, selected: Int, scores: List<HoleScore>, onSelect: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) { state.animateScrollToItem((selected - 3).coerceAtLeast(0)) }
    LazyRow(state = state, modifier = Modifier.fillMaxWidth().background(Color(0xCC0B2412)).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
        itemsIndexed((1..count).toList()) { _, n ->
            val done = scores.any { it.hole == n && it.strokes > 0 }
            val sel = n == selected
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(if (sel) Lime else if (done) Color(0xFF2E7D32) else Color(0x33FFFFFF))
                    .clickable { onSelect(n) },
                contentAlignment = Alignment.Center,
            ) { Text("$n", color = if (sel) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
    }
}

@Composable
private fun DistancePanel(back: Double?, center: Double?, front: Double?, hasGreen: Boolean, hasFix: Boolean, unit: String) {
    Surface(color = Panel, contentColor = Color.White, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
            if (!hasGreen) { Text("Chưa có green", fontSize = 13.sp); return@Column }
            if (!hasFix) { Text("Chờ GPS…", fontSize = 13.sp); return@Column }
            Text("SAU  %.0f".format(back), fontSize = 16.sp, color = Color(0xFFB0BEC5))
            Text("%.0f".format(center), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Lime)
            Text("TRƯỚC  %.0f".format(front), fontSize = 16.sp, color = Color(0xFFB0BEC5))
            Text(unit, fontSize = 11.sp, color = Color(0xFFB0BEC5))
        }
    }
}

private fun scoreName(strokes: Int, par: Int) = when {
    strokes == 0 -> "—"
    strokes == 1 -> "Hole in one!"
    strokes - par <= -3 -> "Albatross"
    strokes - par == -2 -> "Eagle"
    strokes - par == -1 -> "Birdie"
    strokes == par -> "Par"
    strokes - par == 1 -> "Bogey"
    strokes - par == 2 -> "Double bogey"
    else -> "+${strokes - par}"
}

@Composable
private fun ScoreEntry(score: HoleScore?, vm: RoundPlayViewModel, all: List<HoleScore>) {
    if (score == null) { Text("Đang tải…"); return }
    val played = all.filter { it.strokes > 0 }
    val total = played.sumOf { it.strokes }
    val diff = total - played.sumOf { it.par }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BigStepper("Gậy", score.strokes, onChange = { n ->
            vm.update { it.copy(strokes = if (it.strokes == 0 && n == 1) it.par else n) }
        }, big = true, sub = scoreName(score.strokes, score.par))
        BigStepper("Putt", score.putts, onChange = { n -> vm.update { it.copy(putts = n) } })
        BigStepper("Par", score.par, onChange = { n -> vm.update { it.copy(par = n) } })
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (score.par >= 4) {
            FilterChip(selected = score.fairway == 1, onClick = { vm.update { it.copy(fairway = if (it.fairway == 1) 0 else 1) } }, label = { Text("Fairway ✓") })
            FilterChip(selected = score.fairway == 2, onClick = { vm.update { it.copy(fairway = if (it.fairway == 2) 0 else 2) } }, label = { Text("Fairway ✗") })
        }
        if (score.putts > 0 && score.strokes > 0) {
            Text(if (score.gir == 1) "GIR ✓ (tự tính)" else "GIR ✗ (tự tính)", fontSize = 13.sp)
        } else {
            FilterChip(selected = score.gir == 1, onClick = { vm.update { it.copy(gir = if (it.gir == 1) 0 else 1) } }, label = { Text("GIR ✓") })
            FilterChip(selected = score.gir == 2, onClick = { vm.update { it.copy(gir = if (it.gir == 2) 0 else 2) } }, label = { Text("GIR ✗") })
        }
        FilterChip(selected = score.penalties > 0, onClick = { vm.update { it.copy(penalties = (it.penalties + 1) % 4) } },
            label = { Text("Phạt: ${score.penalties}") })
    }
    if (played.isNotEmpty()) Text("Tổng $total (${fmtToPar(diff)}) sau ${played.size} hố", fontSize = 13.sp, color = Color(0xFFB0BEC5))
}

@Composable
private fun BigStepper(label: String, value: Int, onChange: (Int) -> Unit, big: Boolean = false, sub: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Color(0xFFB0BEC5))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = { onChange(value - 1) }, Modifier.size(if (big) 40.dp else 34.dp)) { Icon(Icons.Filled.Remove, "-") }
            Text(if (value == 0) "–" else "$value", Modifier.width(if (big) 48.dp else 36.dp),
                fontSize = if (big) 32.sp else 22.sp, fontWeight = FontWeight.Bold, color = if (big) Lime else Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            FilledIconButton(onClick = { onChange(value + 1) }, Modifier.size(if (big) 40.dp else 34.dp)) { Icon(Icons.Filled.Add, "+") }
        }
        if (sub != null) Text(sub, fontSize = 12.sp, color = Color.White)
    }
}

@Composable
private fun ShotPanel(vm: RoundPlayViewModel) {
    val clubs by vm.clubStats.collectAsState()
    val selected by vm.selectedClubId.collectAsState()
    val armed by vm.armed.collectAsState()
    val live by vm.liveMeters.collectAsState()
    val autoSwing by vm.autoSwing.collectAsState()
    val message by vm.message.collectAsState()
    val pending by vm.pending.collectAsState()
    val fix by vm.fix.collectAsState()

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        clubs.filter { !it.name.equals("Putter", true) }.forEach { c ->
            FilterChip(selected = selected == c.clubId, onClick = { vm.selectClub(c.clubId) },
                label = { Text(c.name + (c.avgMeters?.let { " · %.0f".format(it) } ?: "")) })
        }
    }
    if (armed) {
        Text(live?.let { "Đã đi %.0f m".format(it) } ?: "Đang đo…", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Lime)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::finishManually) { Text("Đã tới bóng") }
            OutlinedButton(onClick = vm::cancelShot, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Hủy") }
        }
    } else {
        Button(onClick = { vm.armHere() }, enabled = fix != null) { Text("Đánh — đánh dấu vị trí bóng") }
    }
    pending?.let { Text("Cú %.0f m chưa có gậy — chọn gậy ở trên.".format(it.meters), color = Color(0xFFFF8A80)) }
    if (vm.swingSensorAvailable) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tự phát hiện swing (để máy trong túi)", Modifier.weight(1f), fontSize = 13.sp)
            Switch(checked = autoSwing, onCheckedChange = vm::setAutoSwing)
        }
    }
    message?.let { Text(it, color = Lime, fontSize = 13.sp) }
    Spacer(Modifier.size(2.dp))
}
