package com.greategolf.ui.course

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greategolf.GolfApp
import com.greategolf.domain.CourseLayout
import com.greategolf.domain.FeatureType
import com.greategolf.domain.GeoMath
import com.greategolf.domain.GeoPoint
import com.greategolf.domain.GreenInfo
import com.greategolf.domain.HazardInfo
import com.greategolf.domain.Hole
import com.greategolf.domain.LayoutOps
import com.greategolf.domain.LocalFrame
import com.greategolf.domain.OrbitCamera
import com.greategolf.domain.ScreenPt
import com.greategolf.domain.TileKey
import com.greategolf.domain.V3
import com.greategolf.domain.WebMercator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun layerColors(t: FeatureType): Pair<Color, Color> = when (t) {
    FeatureType.WOOD -> Color(0xFF1B4D1B) to Color(0xFF123512)
    FeatureType.WATER -> Color(0xFF2F8FD8) to Color(0xFF1B6BA8)
    FeatureType.FAIRWAY -> Color(0xFF7CC663) to Color(0xFF63AE4D)
    FeatureType.TEE -> Color(0xFF9BD36F) to Color(0xFF7BB552)
    FeatureType.BUNKER -> Color(0xFFF3E3A6) to Color(0xFFCDB468)
    FeatureType.GREEN -> Color(0xFF45E07A) to Color(0xFF22A857)
    else -> Color.Gray to Color.DarkGray
}

private fun outlineColor(t: FeatureType): Color? = when (t) {
    FeatureType.GREEN -> Color(0xFF69F0AE)
    FeatureType.BUNKER -> Color(0xFFFFF3C4)
    FeatureType.WATER -> Color(0xFF80D8FF)
    FeatureType.TEE -> Color(0xFFCCFF90)
    else -> null
}

private const val PITCH0 = 52.0
private fun defaultDist(length: Double) = maxOf(1.1 * length * sin(Math.toRadians(PITCH0)) + 60, 150.0)

/**
 * Golfshot-style hole view: the hole stands upright (tee at the bottom, green at the top) in perspective, draped
 * with satellite imagery when [satellite] is set, plus carry distances to hazards, green distance, a draggable
 * target (tap to place, long-press to clear) and the player's position.
 */
@Composable
fun Hole3DView(
    layout: CourseLayout,
    hole: Hole,
    green: GreenInfo?,
    player: GeoPoint?,
    target: GeoPoint?,
    onTarget: (GeoPoint?) -> Unit,
    yards: Boolean,
    satellite: Boolean,
    modifier: Modifier = Modifier,
    bottomPad: Dp = 0.dp,
) {
    val ctx = LocalContext.current
    val repo = remember { (ctx.applicationContext as GolfApp).tiles }
    val scene = remember(layout, hole, green) { buildHoleScene(layout, hole, green) }
    val tiles = remember(scene) { mutableStateMapOf<TileKey, Bitmap>() }
    var imageryFailed by remember(scene) { mutableStateOf(false) }

    LaunchedEffect(scene, satellite) {
        if (!satellite) return@LaunchedEffect
        var ok = false; var fail = false
        for (chunk in scene.tiles.filter { it !in tiles }.chunked(6)) {
            coroutineScope { chunk.map { k -> async { k to repo.get(k) } }.awaitAll() }.forEach { (k, b) ->
                if (b != null) { tiles[k] = b; ok = true } else fail = true
            }
        }
        imageryFailed = !ok && fail && tiles.isEmpty()
    }

    var yaw by remember(scene) { mutableDoubleStateOf(0.0) }
    var pitch by remember(scene) { mutableDoubleStateOf(PITCH0) }
    var dist by remember(scene) { mutableDoubleStateOf(defaultDist(scene.length)) }

    val text = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true } }
    val bg = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val bmpPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG) }
    val density = ctx.resources.displayMetrics.density
    val bottomPx = bottomPad.value * density

    fun camera() = OrbitCamera(V3(0.0, scene.length / 2, 0.0), yaw, pitch, dist)

    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(scene) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        yaw -= pan.x * 0.25
                        pitch = (pitch + pan.y * 0.2).coerceIn(15.0, 88.0)
                        dist = (dist / zoom).coerceIn(50.0, 1500.0)
                    }
                }
                .pointerInput(scene) {
                    detectTapGestures(
                        onTap = { o ->
                            camera().unproject(o.x, o.y, size.width.toFloat(), size.height - bottomPx)?.let { g ->
                                if (kotlin.math.abs(g.x) < 1500 && g.y > -500 && g.y < scene.length + 800) onTarget(scene.frame.toGeo(g.x, g.y))
                            }
                        },
                        onLongPress = { onTarget(null) },
                    )
                },
        ) {
            drawHole(scene, camera(), size.width, size.height - bottomPx, satellite && tiles.isNotEmpty(), tiles, layout,
                player, target, yards, text, bg, bmpPaint)
        }

        if (satellite && imageryFailed) Text("Không tải được ảnh vệ tinh (cần Internet) — đang hiển thị dạng minh họa.",
            Modifier.align(Alignment.TopCenter).padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
        if (yaw != 0.0 || pitch != PITCH0 || dist != defaultDist(scene.length)) {
            Surface(Modifier.align(Alignment.TopEnd).padding(8.dp).clickable { yaw = 0.0; pitch = PITCH0; dist = defaultDist(scene.length) },
                color = Color(0xCC10301A), contentColor = Color.White, shape = MaterialTheme.shapes.small) {
                Text("Đặt lại góc", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun DrawScope.drawHole(
    scene: HoleScene, cam: OrbitCamera, w: Float, h: Float, useImagery: Boolean, tiles: Map<TileKey, Bitmap>, layout: CourseLayout,
    player: GeoPoint?, target: GeoPoint?, yards: Boolean, text: Paint, bg: Paint, bmpPaint: Paint,
) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF6FB7E9), Color(0xFFDCEEFB))))
    val L = scene.length

    fun path(pts: List<V3>, closed: Boolean): List<ScreenPt> = cam.projectPath(pts, closed, w, h)
    fun toPath(sp: List<ScreenPt>, closeIt: Boolean) = Path().apply {
        sp.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        if (closeIt) close()
    }
    fun poly(pts: List<V3>, fill: Color?, stroke: Color?, sw: Float = 1.5f) {
        val sp = path(pts, true); if (sp.size < 3) return
        val p = toPath(sp, true)
        if (fill != null) drawPath(p, fill)
        if (stroke != null) drawPath(p, stroke, style = Stroke(width = sw))
    }
    fun d(m: Double) = (if (yards) GeoMath.metersToYards(m) else m).roundToInt().toString()
    fun pill(label: String, p: ScreenPt?, fill: Int, fg: Int = android.graphics.Color.WHITE, size: Float = 30f) {
        if (p == null) return
        text.textSize = size; text.color = fg
        val tw = text.measureText(label)
        bg.color = fill
        drawIntoCanvas {
            it.nativeCanvas.drawRoundRect(RectF(p.x - tw / 2 - 12, p.y - size * 0.62f, p.x + tw / 2 + 12, p.y + size * 0.45f), 18f, 18f, bg)
            it.nativeCanvas.drawText(label, p.x, p.y + size * 0.3f, text)
        }
    }
    fun scr(v: V3) = cam.project(v, w, h)
    fun loc(g: GeoPoint) = scene.frame.toLocal(g).let { V3(it.first, it.second) }

    // ground
    poly(listOf(V3(-900.0, -600.0), V3(900.0, -600.0), V3(900.0, L + 900), V3(-900.0, L + 900)), Color(0xFF3D7A32), null)

    if (useImagery) {
        // Each tile is warped through the camera with an 8x8 mesh (perspective-correct enough at this scale).
        val n = 8
        for ((k, bmp) in tiles) {
            val verts = FloatArray((n + 1) * (n + 1) * 2)
            var ok = true
            loop@ for (j in 0..n) for (i in 0..n) {
                val lat = WebMercator.latAt(k.y + j.toDouble() / n, k.z)
                val lng = WebMercator.lngAt(k.x + i.toDouble() / n, k.z)
                val sp = scr(loc(GeoPoint(lat, lng))) ?: run { ok = false; null }
                if (sp == null) break@loop
                val idx = (j * (n + 1) + i) * 2
                verts[idx] = sp.x; verts[idx + 1] = sp.y
            }
            if (ok) drawIntoCanvas { it.nativeCanvas.drawBitmapMesh(bmp, n, n, verts, 0, null, 0, bmpPaint) }
        }
        for ((type, polys) in scene.layers) outlineColor(type)?.let { c -> polys.forEach { poly(it, null, c.copy(alpha = 0.85f), 2f) } }
    } else {
        for ((type, polys) in scene.layers) {
            val (fill, stroke) = layerColors(type)
            polys.forEach { poly(it, fill, stroke) }
        }
        scene.paths.forEach { pl ->
            val sp = path(pl, false)
            if (sp.size >= 2) drawPath(toPath(sp, false), Color(0xAAD8D2C4), style = Stroke(width = 3f, cap = StrokeCap.Round))
        }
        val cl = path(scene.centerLine, false)
        if (cl.size >= 2) drawPath(toPath(cl, false), Color(0x99FFFFFF), style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
    }

    // yardage arcs measured back from the green centre
    for (yd in listOf(50, 100, 150, 200, 250, 300)) {
        val r = yd * GeoMath.METERS_PER_YARD
        if (r > L + 20) continue
        val ring = (-60..60 step 6).map { a -> val t = Math.toRadians(a.toDouble()); V3(scene.green.x + r * sin(t), scene.green.y - r * cos(t)) }
        val sp = path(ring, false)
        if (sp.size >= 2) drawPath(toPath(sp, false), Color(0x66FFFFFF), style = Stroke(width = 2f))
    }

    // hazards: carry (near edge) and far edge, measured from the player (or the tee)
    val here = player?.takeIf { GeoMath.distanceMeters(it, scene.hole.tee) < 800 } ?: scene.hole.tee
    LayoutOps.hazardsAhead(layout, scene.hole, here, limit = 5).forEach { hz: HazardInfo ->
        val water = hz.type == FeatureType.WATER
        val fill = if (water) 0xCC1565C0.toInt() else 0xCCF3E3A6.toInt()
        val fg = if (water) android.graphics.Color.WHITE else android.graphics.Color.rgb(60, 45, 0)
        pill(d(hz.frontM), scr(loc(hz.front)), fill, fg, 28f)
        if (hz.backM - hz.frontM > 12) pill(d(hz.backM), scr(loc(hz.back)), fill, fg, 24f)
    }

    // tee
    scr(V3(0.0, 0.0))?.let { p -> drawCircle(Color.White, 7f, Offset(p.x, p.y)); drawCircle(Color(0xFF1B5E20), 4f, Offset(p.x, p.y)) }

    // flag + green distance
    val base = scr(scene.green)
    val top = scr(V3(scene.green.x, scene.green.y, 9.0))
    val tip = scr(V3(scene.green.x + 4.0, scene.green.y, 7.5))
    val mid = scr(V3(scene.green.x, scene.green.y, 6.0))
    if (base != null && top != null) drawLine(Color.White, Offset(base.x, base.y), Offset(top.x, top.y), 3f)
    if (top != null && tip != null && mid != null) drawPath(Path().apply { moveTo(top.x, top.y); lineTo(tip.x, tip.y); lineTo(mid.x, mid.y); close() }, Color(0xFFE53935))

    // player
    val pl = player?.takeIf { GeoMath.distanceMeters(it, scene.hole.tee) < 800 }
    pl?.let { scr(loc(it)) }?.let { p ->
        drawCircle(Color.White, 15f, Offset(p.x, p.y)); drawCircle(Color(0xFF2196F3), 10f, Offset(p.x, p.y))
    }
    pill(d(GeoMath.distanceMeters(here, scene.frame.toGeo(scene.green.x, scene.green.y))), scr(V3(scene.green.x, scene.green.y, 14.0)),
        0xDD10301A.toInt(), 0xFFC6FF00.toInt(), 34f)

    // target: here → target → green
    if (target != null) {
        val t = loc(target)
        val a = loc(here)
        val gc = scene.green
        for ((p, q) in listOf(a to t, t to gc)) {
            val sp = path(listOf(p, q), false)
            if (sp.size >= 2) drawPath(toPath(sp, false), Color.White, style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f))))
        }
        scr(t)?.let { p -> drawCircle(Color.White, 20f, Offset(p.x, p.y), style = Stroke(width = 5f)); drawCircle(Color(0xFFFFC107), 7f, Offset(p.x, p.y)) }
        val gcGeo = scene.frame.toGeo(gc.x, gc.y)
        pill(d(GeoMath.distanceMeters(here, target)), scr(V3((a.x + t.x) / 2, (a.y + t.y) / 2)), 0xDD10301A.toInt(), android.graphics.Color.WHITE, 30f)
        pill(d(GeoMath.distanceMeters(target, gcGeo)), scr(V3((t.x + gc.x) / 2, (t.y + gc.y) / 2)), 0xDD10301A.toInt(), 0xFFC6FF00.toInt(), 30f)
    }
}
