package com.greategolf.ui.swing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greategolf.GolfApp
import com.greategolf.data.SwingRecord
import com.greategolf.platform.VideoAnalysis
import com.greategolf.ui.appViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data class Running(val progress: Float) : AnalysisState
    data class Done(val result: VideoAnalysis) : AnalysisState
    data class Failed(val message: String) : AnalysisState
}

class SwingViewModel(private val app: GolfApp) : ViewModel() {
    val state = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val history = app.repository.swings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val detecting = MutableStateFlow(false)
    val swingCount = MutableStateFlow(0)
    val lastPeak = MutableStateFlow<Double?>(null)
    val sensorAvailable get() = app.swingSensor.isAvailable
    private var detectJob: Job? = null

    fun setDetecting(on: Boolean) {
        detecting.value = on
        detectJob?.cancel(); detectJob = null
        if (!on) return
        detectJob = viewModelScope.launch {
            app.swingSensor.swings().collect {
                swingCount.value += 1
                lastPeak.value = it.peakAccel
            }
        }
    }

    fun resetCount() { swingCount.value = 0; lastPeak.value = null }
    fun stopDetecting() = setDetecting(false)

    fun analyze(uri: Uri) {
        if (state.value is AnalysisState.Running) return
        state.value = AnalysisState.Running(0f)
        viewModelScope.launch {
            try {
                val result = app.videoAnalyzer.analyze(uri) { p -> state.value = AnalysisState.Running(p) }
                if (result == null) {
                    state.value = AnalysisState.Failed(
                        "Không nhận diện được người đánh golf. Hãy quay toàn thân, đủ sáng, chính diện/hơi chếch và giữ máy cố định."
                    )
                    return@launch
                }
                val r = result.report
                app.repository.addSwing(
                    SwingRecord(
                        createdAt = System.currentTimeMillis(), videoUri = uri.toString(), tempo = r.tempoRatio,
                        shoulderTurn = r.shoulderTurnDeg, hipTurn = r.hipTurnDeg, xFactor = r.xFactorDeg,
                        headSway = r.headSwayPct, summary = r.feedback.firstOrNull().orEmpty(),
                    )
                )
                state.value = AnalysisState.Done(result)
            } catch (e: Exception) {
                state.value = AnalysisState.Failed("Lỗi phân tích video: ${e.message}")
            }
        }
    }

    fun delete(r: SwingRecord) { viewModelScope.launch { app.repository.deleteSwing(r) } }
    override fun onCleared() { detectJob?.cancel() }
}

@Composable
fun SwingScreen() {
    val vm = appViewModel { SwingViewModel(it) }
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()
    val detecting by vm.detecting.collectAsState()
    val count by vm.swingCount.collectAsState()
    val peak by vm.lastPeak.collectAsState()
    val history by vm.history.collectAsState()
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    DisposableEffect(Unit) { onDispose { vm.stopDetecting() } }

    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        if (ok) captureUri?.let { vm.analyze(Uri.parse(it)) }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.analyze(uri)
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Swing", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Phát hiện swing", style = MaterialTheme.typography.titleMedium)
                Text("Dùng cảm biến gia tốc. Để điện thoại trong túi hoặc gắn tay, rồi vung gậy.", style = MaterialTheme.typography.bodySmall)
                if (!vm.sensorAvailable) {
                    Text("Thiết bị không có cảm biến gia tốc tuyến tính.", color = MaterialTheme.colorScheme.error)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (detecting) "Đang theo dõi" else "Tắt", Modifier.weight(1f))
                        Switch(checked = detecting, onCheckedChange = vm::setDetecting)
                    }
                    Text("Số swing: $count", style = MaterialTheme.typography.headlineSmall)
                    peak?.let { Text("Gia tốc đỉnh gần nhất: %.0f m/s²".format(it), style = MaterialTheme.typography.bodySmall) }
                    if (count > 0) OutlinedButton(onClick = vm::resetCount) { Text("Đặt lại") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Phân tích swing từ video (driving range)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Đặt điện thoại cố định (chân máy), ngang tầm hông, đối diện người đánh (face-on), thấy toàn thân từ đầu đến chân. " +
                        "Quay một swing, app nhận diện tư thế ngay trên máy và đánh giá nhịp, xoay vai/hông, độ ổn định đầu và cột sống.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val dir = File(ctx.cacheDir, "videos").apply { mkdirs() }
                        val file = File(dir, "swing_${System.currentTimeMillis()}.mp4")
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                        captureUri = uri.toString()
                        capture.launch(uri)
                    }, enabled = state !is AnalysisState.Running) { Text("Quay video") }
                    OutlinedButton(onClick = { pick.launch("video/*") }, enabled = state !is AnalysisState.Running) { Text("Chọn video") }
                }
                when (val s = state) {
                    AnalysisState.Idle -> Unit
                    is AnalysisState.Running -> {
                        Text("Đang phân tích… ${(s.progress * 100).toInt()}%")
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    is AnalysisState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                    is AnalysisState.Done -> AnalysisResult(s.result)
                }
            }
        }

        if (history.isNotEmpty()) {
            Text("Lịch sử phân tích", style = MaterialTheme.typography.titleMedium)
            history.forEach { r ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(df.format(Date(r.createdAt)), style = MaterialTheme.typography.labelMedium)
                            Text(
                                buildString {
                                    append("Nhịp %.1f:1".format(r.tempo))
                                    r.shoulderTurn?.let { append(" · vai %.0f°".format(it)) }
                                    r.hipTurn?.let { append(" · hông %.0f°".format(it)) }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(r.summary, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { vm.delete(r) }) { Icon(Icons.Filled.Delete, "Xóa") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisResult(a: VideoAnalysis) {
    val r = a.report
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            a.keyFrames.forEach { k ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(k.image.asImageBitmap(), contentDescription = k.label, modifier = Modifier.height(220.dp))
                    Text(k.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text("Nhịp swing %.1f : 1  (backswing %d ms / downswing %d ms)".format(r.tempoRatio, r.backswingMs, r.downswingMs))
        r.shoulderTurnDeg?.let { Text("Xoay vai ở đỉnh: %.0f°".format(it)) }
        r.hipTurnDeg?.let { Text("Xoay hông ở đỉnh: %.0f°".format(it)) }
        r.xFactorDeg?.let { Text("X-factor: %.0f°".format(it)) }
        r.headSwayPct?.let { Text("Đầu dịch ngang: %.0f%% chiều rộng vai".format(it)) }
        Text("Nhận xét", style = MaterialTheme.typography.titleSmall)
        r.feedback.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        Text(
            "Lưu ý: ước lượng từ video 2D, mang tính tham khảo — nên kết hợp với huấn luyện viên.",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
