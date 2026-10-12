package com.greategolf.ui.clubs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greategolf.GolfApp
import com.greategolf.data.Club
import com.greategolf.data.Shot
import com.greategolf.domain.ClubStats
import com.greategolf.ui.appViewModel
import com.greategolf.ui.fmtDistance
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClubsViewModel(private val app: GolfApp) : ViewModel() {
    private val repo = app.repository
    val stats = repo.clubStats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val clubs = repo.clubs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addClub(name: String) = viewModelScope.launch {
        repo.addClub(name.trim(), (stats.value.maxOfOrNull { it.sortOrder } ?: -1) + 1)
    }

    fun rename(clubId: Long, name: String) = viewModelScope.launch {
        clubs.value.firstOrNull { it.id == clubId }?.let { repo.renameClub(it, name.trim()) }
    }

    fun delete(clubId: Long) = viewModelScope.launch {
        clubs.value.firstOrNull { it.id == clubId }?.let { repo.deleteClub(it) }
    }

    /** Manual entry, e.g. a number read from a launch monitor or rangefinder at the range. */
    fun addManualShot(clubId: Long, meters: Double) = viewModelScope.launch {
        repo.addShot(Shot(clubId = clubId, meters = meters, createdAt = System.currentTimeMillis(),
            startLat = 0.0, startLng = 0.0, endLat = 0.0, endLng = 0.0, auto = false))
    }
}

private sealed interface Dialog {
    data object AddClub : Dialog
    data class Rename(val club: ClubStats) : Dialog
    data class AddShot(val club: ClubStats) : Dialog
}

@Composable
fun ClubsScreen() {
    val vm = appViewModel { ClubsViewModel(it) }
    val stats by vm.stats.collectAsState()
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var target by remember { mutableStateOf("") }
    val recommended = target.toDoubleOrNull()?.let { com.greategolf.domain.ClubAdvisor.recommend(it, stats) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Bộ gậy & khoảng cách", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Khoảng cách mỗi gậy được học từ các cú đánh đo bằng GPS (tab GPS) hoặc nhập tay.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Chọn gậy theo khoảng cách", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = target, onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Khoảng cách tới cờ (m)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        if (target.isNotBlank()) {
                            Text(
                                recommended?.let { "Gợi ý: ${it.name} (trung bình ${fmtDistance(it.avgMeters!!)})" }
                                    ?: "Chưa đủ dữ liệu cú đánh để gợi ý.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            items(stats, key = { it.clubId }) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            if (c.avgMeters != null) {
                                Text("TB ${fmtDistance(c.avgMeters)}", style = MaterialTheme.typography.bodyMedium)
                                Text("Xa nhất ${fmtDistance(c.maxMeters!!)} · ${c.shots} cú", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("Chưa có dữ liệu", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        OutlinedButton(onClick = { dialog = Dialog.AddShot(c) }) { Text("+ Cú") }
                        IconButton(onClick = { dialog = Dialog.Rename(c) }) { Icon(Icons.Filled.Edit, "Đổi tên") }
                        IconButton(onClick = { vm.delete(c.clubId) }) { Icon(Icons.Filled.Delete, "Xóa") }
                    }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(40.dp)) }
        }
        FloatingActionButton(onClick = { dialog = Dialog.AddClub }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, "Thêm gậy")
        }
    }

    when (val d = dialog) {
        Dialog.AddClub -> TextDialog("Thêm gậy", "Tên gậy", "", false, { dialog = null }) { vm.addClub(it); dialog = null }
        is Dialog.Rename -> TextDialog("Đổi tên gậy", "Tên gậy", d.club.name, false, { dialog = null }) { vm.rename(d.club.clubId, it); dialog = null }
        is Dialog.AddShot -> TextDialog("${d.club.name}: thêm cú đánh", "Khoảng cách (m)", "", true, { dialog = null }) {
            it.toDoubleOrNull()?.takeIf { m -> m in 1.0..400.0 }?.let { m -> vm.addManualShot(d.club.clubId, m) }
            dialog = null
        }
        null -> Unit
    }
}

@Composable
private fun TextDialog(
    title: String, label: String, initial: String, numeric: Boolean,
    onDismiss: () -> Unit, onOk: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
            )
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onOk(text) }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}
