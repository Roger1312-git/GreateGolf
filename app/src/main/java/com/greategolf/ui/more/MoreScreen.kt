package com.greategolf.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MoreScreen(onClubs: () -> Unit, onSwing: () -> Unit, onHistory: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Thêm", style = MaterialTheme.typography.headlineSmall)
        Item("Bộ gậy & khoảng cách", "Khoảng cách mỗi gậy học từ GPS, gợi ý gậy", onClubs)
        Item("Phân tích swing", "Phát hiện swing bằng cảm biến, phân tích video tại driving range", onSwing)
        Item("Lịch sử scorecard", "Tất cả vòng đã chơi", onHistory)
        Text("Dữ liệu sân © OpenStreetMap contributors · Ảnh vệ tinh © Esri, Maxar, Earthstar Geographics",
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Item(title: String, sub: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}
