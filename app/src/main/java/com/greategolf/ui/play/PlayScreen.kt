package com.greategolf.ui.play

import android.view.WindowManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.greategolf.domain.GeoMath
import com.greategolf.ui.RequireLocation
import com.greategolf.ui.appViewModel
import com.greategolf.ui.fmtDistance
import android.app.Activity

@Composable
fun PlayScreen(onOpenScorecard: (Long) -> Unit) {
    RequireLocation { PlayContent(onOpenScorecard) }
}

@Composable
private fun PlayContent(onOpenScorecard: (Long) -> Unit) {
    val vm = appViewModel { PlayViewModel(it) }
    val round by vm.round.collectAsState()
    val hole by vm.hole.collectAsState()
    val fix by vm.fix.collectAsState()
    val toGreen by vm.distanceToGreen.collectAsState()
    val hasGreen by vm.hasGreen.collectAsState()
    val recommendation by vm.recommendation.collectAsState()
    val clubs by vm.clubStats.collectAsState()
    val selectedClub by vm.selectedClubId.collectAsState()
    val armed by vm.armed.collectAsState()
    val live by vm.liveMeters.collectAsState()
    val autoSwing by vm.autoSwing.collectAsState()
    val message by vm.message.collectAsState()
    val pending by vm.pending.collectAsState()

    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        vm.startGps()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            vm.stopGps()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("GPS", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(round?.courseName ?: "Chưa chọn vòng chơi", style = MaterialTheme.typography.titleMedium)
                if (round == null) {
                    Text("Chọn sân trên Bản đồ (Chơi với GPS) hoặc tạo vòng ở Scorecard để đo khoảng cách tới green.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.changeHole(-1) }) { Text("◀") }
                        Text("Hố $hole", style = MaterialTheme.typography.titleLarge)
                        OutlinedButton(onClick = { vm.changeHole(1) }) { Text("▶") }
                        OutlinedButton(onClick = { onOpenScorecard(round!!.id) }) { Text("Scorecard") }
                    }
                    when {
                        toGreen != null -> {
                            Text(fmtDistance(toGreen!!), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                            Text("tới green", style = MaterialTheme.typography.bodySmall)
                            recommendation?.let { Text("Gợi ý: ${it.name}", style = MaterialTheme.typography.titleMedium) }
                        }
                        hasGreen -> Text("Đang chờ tín hiệu GPS…")
                        else -> Text("Chưa có vị trí green hố này. Đứng trên green và bấm \"Đánh dấu green\" — lần sau app sẽ tự đo.", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = vm::markGreenHere) { Text("Đánh dấu green hố $hole tại đây") }
                }
                Text(
                    fix?.accuracyM?.let { "GPS ±%.0f m".format(it) } ?: "Đang tìm GPS…",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Đo khoảng cách cú đánh", style = MaterialTheme.typography.titleMedium)
                Text("1. Chọn gậy  2. Bấm \"Đánh\" (hoặc bật tự phát hiện swing)  3. Đi tới bóng — app tự đo khi bạn dừng lại.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    clubs.filter { !it.name.equals("Putter", true) }.forEach { c ->
                        FilterChip(
                            selected = selectedClub == c.clubId,
                            onClick = { vm.selectClub(c.clubId) },
                            label = { Text(c.name + (c.avgMeters?.let { " · %.0f".format(it) } ?: "")) },
                        )
                    }
                }
                if (armed) {
                    Text(live?.let { "Đã đi ${fmtDistance(it)}" } ?: "Đang đo…", style = MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::finishManually) { Text("Đã tới bóng") }
                        OutlinedButton(onClick = vm::cancelShot) { Text("Hủy") }
                    }
                } else {
                    Button(onClick = { vm.armHere() }, enabled = fix != null) { Text("Đánh (đánh dấu vị trí)") }
                }
                if (pending != null) {
                    Text("Cú đánh ${GeoMath.distanceMeters(pending!!.start, pending!!.end).let { fmtDistance(it) }} chưa có gậy — chọn gậy ở trên.", color = MaterialTheme.colorScheme.error)
                }
                if (vm.swingSensorAvailable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Tự phát hiện swing")
                            Text("Để điện thoại trong túi quần/áo", style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(checked = autoSwing, onCheckedChange = vm::setAutoSwing)
                    }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}
