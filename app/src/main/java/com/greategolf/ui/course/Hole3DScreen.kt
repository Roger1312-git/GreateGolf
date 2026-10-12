package com.greategolf.ui.course

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.greategolf.domain.CourseLayout
import com.greategolf.domain.FeatureType
import com.greategolf.domain.GeoMath
import com.greategolf.domain.GreenInfo
import com.greategolf.domain.Hole
import com.greategolf.domain.LayoutOps
import com.greategolf.domain.LocalFrame
import com.greategolf.domain.OrbitCamera
import com.greategolf.domain.ScreenPt
import com.greategolf.domain.V3
import com.greategolf.ui.appViewModel
import com.greategolf.ui.fmtDistance
import kotlin.math.cos
import kotlin.math.sin

/** A hole converted to local meters: x = right of the tee→green axis, y = along it. */
private class Scene(
    val hole: Hole,
    val length: Double,
    val green: V3,
    val layers: List<Pair<FeatureType, List<List<V3>>>>,
    val centerLine: List<V3>,
    val pathPolylines: List<List<V3>>,
)

private fun buildScene(layout: CourseLayout, hole: Hole): Scene {
    val g: GreenInfo? = LayoutOps.greenOf(layout, hole)
    val greenPt = g?.center ?: hole.greenEnd
    val frame = LocalFrame(hole.tee, LayoutOps.bearingDeg(hole.tee, greenPt))
    fun v(p: com.greategolf.domain.GeoPoint) = frame.toLocal(p).let { V3(it.first, it.second) }
    val near = LayoutOps.featuresForHole(layout, hole)
    val order = listOf(FeatureType.WOOD, FeatureType.WATER, FeatureType.FAIRWAY, FeatureType.TEE, FeatureType.BUNKER, FeatureType.GREEN)
    val layers = order.map { t -> t to near.filter { it.type == t && it.closed }.map { f -> f.ring.map(::v) } }
    val gv = v(greenPt)
    return Scene(
        hole, gv.y, gv, layers,
        hole.line.map(::v),
        near.filter { it.type == FeatureType.CART_PATH }.map { f -> f.ring.map(::v) },
    )
}

private fun layerColors(t: FeatureType): Pair<Color, Color> = when (t) {
    FeatureType.WOOD -> Color(0xFF1B4D1B) to Color(0xFF123512)
    FeatureType.WATER -> Color(0xFF2F8FD8) to Color(0xFF1B6BA8)
    FeatureType.FAIRWAY -> Color(0xFF7CC663) to Color(0xFF63AE4D)
    FeatureType.TEE -> Color(0xFF9BD36F) to Color(0xFF7BB552)
    FeatureType.BUNKER -> Color(0xFFF3E3A6) to Color(0xFFCDB468)
    FeatureType.GREEN -> Color(0xFF45E07A) to Color(0xFF22A857)
    else -> Color.Gray to Color.DarkGray
}

@Composable
fun Hole3DScreen(courseId: String, holeIndex: Int, onBack: () -> Unit) {
    val vm = appViewModel { CourseViewModel(it, courseId, holeIndex) }
    val state by vm.state.collectAsState()
    val selected by vm.selectedHole.collectAsState()
    val layout = (state as? LayoutState.Ready)?.layout

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
                Text("${vm.course.name} · 3D", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
        }
        if (layout == null || layout.holes.isEmpty()) {
            Text(
                if (state is LayoutState.Loading) "Đang tải dữ liệu sân…" else "Chưa có dữ liệu hố cho sân này.",
                Modifier.padding(16.dp),
            )
            return@Column
        }
        val idx = (selected ?: 0).coerceIn(0, layout.holes.lastIndex)
        val hole = layout.holes[idx]
        val scene = remember(layout, idx) { buildScene(layout, hole) }

        var yaw by remember(idx) { mutableDoubleStateOf(0.0) }
        var pitch by remember(idx) { mutableDoubleStateOf(38.0) }
        var dist by remember(idx) { mutableDoubleStateOf(maxOf(scene.length * 1.15, 160.0)) }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(idx) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        yaw = (yaw - pan.x * 0.25)
                        pitch = (pitch + pan.y * 0.2).coerceIn(12.0, 85.0)
                        dist = (dist / zoom).coerceIn(60.0, 1500.0)
                    }
                },
            ) { drawScene(scene, OrbitCamera(V3(0.0, scene.length / 2, 0.0), yaw, pitch, dist)) }

            Surface(Modifier.align(Alignment.TopStart).padding(8.dp), shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
                Column(Modifier.padding(10.dp)) {
                    Text("Hố ${hole.ref}" + (hole.par?.let { " · Par $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                    Text(fmtDistance(GeoMath.distanceMeters(hole.tee, LayoutOps.greenOf(layout, hole)?.center ?: hole.greenEnd)),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Vuốt: xoay · Kéo dọc: nghiêng · Chụm: zoom",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { vm.select((idx - 1).coerceAtLeast(0)) }, enabled = idx > 0) { Text("◀ Hố trước") }
            Button(onClick = { yaw = 0.0; pitch = 38.0; dist = maxOf(scene.length * 1.15, 160.0) }) { Text("Đặt lại") }
            OutlinedButton(onClick = { vm.select((idx + 1).coerceAtMost(layout.holes.lastIndex)) }, enabled = idx < layout.holes.lastIndex) { Text("Hố sau ▶") }
        }
    }
}

private fun DrawScope.drawScene(scene: Scene, cam: OrbitCamera) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF6FB7E9), Color(0xFFDCEEFB))))

    fun path(pts: List<V3>, closed: Boolean): List<ScreenPt> = cam.projectPath(pts, closed, w, h)
    fun toPath(sp: List<ScreenPt>, closeIt: Boolean) = Path().apply {
        sp.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        if (closeIt) close()
    }
    fun fillPoly(pts: List<V3>, fill: Color, stroke: Color?) {
        val sp = path(pts, true)
        if (sp.size < 3) return
        val p = toPath(sp, true)
        drawPath(p, fill)
        if (stroke != null) drawPath(p, stroke, style = Stroke(width = 1.5f))
    }

    // ground
    val L = scene.length
    fillPoly(listOf(V3(-700.0, -500.0), V3(700.0, -500.0), V3(700.0, L + 700), V3(-700.0, L + 700)), Color(0xFF3D7A32), null)

    for ((type, polys) in scene.layers) {
        val (fill, stroke) = layerColors(type)
        polys.forEach { fillPoly(it, fill, stroke) }
    }
    scene.pathPolylines.forEach { pl ->
        val sp = path(pl, false)
        if (sp.size >= 2) drawPath(toPath(sp, false), Color(0xAAD8D2C4), style = Stroke(width = 3f, cap = StrokeCap.Round))
    }

    // centre line
    val cl = path(scene.centerLine, false)
    if (cl.size >= 2) drawPath(toPath(cl, false), Color(0x99FFFFFF),
        style = Stroke(width = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))

    // yardage arcs (measured back from the green centre), labelled in yards
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 30f; textAlign = Paint.Align.CENTER; setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK) }
    for (yd in listOf(50, 100, 150, 200, 250, 300)) {
        val r = yd * GeoMath.METERS_PER_YARD
        if (r > L + 20) continue
        val ring = (-60..60 step 6).map { a ->
            val t = Math.toRadians(a.toDouble())
            V3(scene.green.x + r * sin(t), scene.green.y - r * cos(t))
        }
        val sp = path(ring, false)
        if (sp.size >= 2) drawPath(toPath(sp, false), Color(0x88FFFFFF), style = Stroke(width = 2f))
        cam.project(V3(scene.green.x, scene.green.y - r), w, h)?.let { p ->
            drawIntoCanvas { it.nativeCanvas.drawText("$yd", p.x, p.y - 6f, label) }
        }
    }

    // tee marker
    cam.project(V3(0.0, 0.0), w, h)?.let { p ->
        drawCircle(Color.White, 7f, Offset(p.x, p.y)); drawCircle(Color(0xFF1B5E20), 4f, Offset(p.x, p.y))
        drawIntoCanvas { it.nativeCanvas.drawText("TEE", p.x, p.y + 34f, label) }
    }

    // flag
    val base = cam.project(scene.green, w, h)
    val top = cam.project(V3(scene.green.x, scene.green.y, 8.0), w, h)
    val tip = cam.project(V3(scene.green.x + 3.5, scene.green.y, 6.5), w, h)
    val mid = cam.project(V3(scene.green.x, scene.green.y, 5.0), w, h)
    if (base != null && top != null) drawLine(Color.White, Offset(base.x, base.y), Offset(top.x, top.y), 3f)
    if (top != null && tip != null && mid != null) {
        drawPath(Path().apply { moveTo(top.x, top.y); lineTo(tip.x, tip.y); lineTo(mid.x, mid.y); close() }, Color(0xFFE53935))
    }
}
