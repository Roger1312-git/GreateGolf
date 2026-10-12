package com.greategolf.ui.course

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greategolf.domain.CourseLayout
import com.greategolf.domain.FeatureType
import com.greategolf.domain.GeoMath
import com.greategolf.domain.GeoPoint
import com.greategolf.domain.GreenInfo
import com.greategolf.domain.Hole
import com.greategolf.domain.LayoutOps
import com.greategolf.domain.Ortho2D
import com.greategolf.domain.V3
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Rough1 = Color(0xFF2E7A1B)
private val Rough2 = Color(0xFF14400C)
private val FairwayC = Color(0xFF5FAE30)
private val FairwayEdge = Color(0xFF4C9A26)
private val TeeC = Color(0xFF7FCB48)
private val GreenC = Color(0xFF9DE35F)
private val GreenStripe = Color(0x33FFFFFF)
private val SandC = Color(0xFFEFDDAA)
private val SandEdge = Color(0xFFCDB070)
private val WaterC = Color(0xFF3C9FE6)
private val WaterEdge = Color(0xFF2678B8)
private val WoodC = Color(0xFF123A0B)
private val TreeC = Color(0xFF2F7F1B)
private val TreeHi = Color(0xFF55A83A)
private val ObC = Color(0xFFE53935)

/**
 * Illustrated 2D hole map (tee at the bottom, green at the top): rough, fairway, tee, striped green, bunkers,
 * water, trees, out-of-bounds, yardage rings, hazard carries, player, and a tap-to-place target.
 *
 * [greenMode] zooms in on the green. When [interactive] is false it is a static thumbnail (no labels/gestures).
 */
@Composable
fun HoleMap2D(
    layout: CourseLayout,
    hole: Hole,
    green: GreenInfo?,
    modifier: Modifier = Modifier,
    player: GeoPoint? = null,
    target: GeoPoint? = null,
    onTarget: (GeoPoint?) -> Unit = {},
    yards: Boolean = false,
    greenMode: Boolean = false,
    interactive: Boolean = true,
    bottomPad: Dp = 0.dp,
) {
    val ctx = LocalContext.current
    val density = ctx.resources.displayMetrics.density
    val bottomPx = bottomPad.value * density
    val scene = remember(layout, hole, green) { buildHoleScene(layout, hole, green, 100.0) }
    var zoom by remember(scene, greenMode) { mutableFloatStateOf(1f) }
    var pan by remember(scene, greenMode) { mutableStateOf(Offset.Zero) }
    val text = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true } }
    val bg = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    fun ortho(w: Float, h: Float): Ortho2D {
        val b = scene.bounds(greenMode)
        val (cu, cv, s) = Ortho2D.fit(b[0], b[1], b[2], b[3], w, h, if (greenMode) 22.0 else 28.0)
        return Ortho2D(cu, cv, s * zoom, w, h, pan.x, pan.y)
    }

    val gestures = if (!interactive) Modifier else Modifier
        .pointerInput(scene, greenMode) {
            detectTransformGestures { centroid, p, z, _ ->
                val old = zoom
                val nz = (old * z).coerceIn(0.6f, 8f)
                val ratio = nz / old
                val cx = centroid.x - size.width / 2f; val cy = centroid.y - (size.height - bottomPx) / 2f
                // keep the point under the fingers fixed while zooming, then apply the pan
                pan = Offset(cx - (cx - pan.x) * ratio + p.x, cy - (cy - pan.y) * ratio + p.y)
                zoom = nz
            }
        }
        .pointerInput(scene, greenMode) {
            detectTapGestures(
                onTap = { o ->
                    val os = ortho(size.width.toFloat(), size.height - bottomPx)
                    onTarget(scene.frame.toGeo(os.u(o.x), os.v(o.y)))
                },
                onLongPress = { onTarget(null) },
            )
        }

    Canvas(modifier.fillMaxSize().then(gestures)) {
        drawHole2D(scene, ortho(size.width, size.height - bottomPx), layout, player, target, yards, interactive, greenMode, text, bg)
    }
}

private fun DrawScope.drawHole2D(
    scene: HoleScene, o: Ortho2D, layout: CourseLayout, player: GeoPoint?, target: GeoPoint?,
    yards: Boolean, labels: Boolean, greenMode: Boolean, text: Paint, bg: Paint,
) {
    val full = Offset(0f, 0f)
    // rough with a soft vignette
    drawRect(Brush.radialGradient(listOf(Rough1, Rough2), center = Offset(size.width / 2, size.height * 0.45f), radius = size.maxDimension * 0.75f))

    fun path(pts: List<V3>, close: Boolean) = Path().apply {
        pts.forEachIndexed { i, p -> if (i == 0) moveTo(o.sx(p.x), o.sy(p.y)) else lineTo(o.sx(p.x), o.sy(p.y)) }
        if (close) close()
    }
    val px = o.scale.toFloat()                    // pixels per meter
    fun d(m: Double) = (if (yards) GeoMath.metersToYards(m) else m).roundToInt().toString()
    fun pill(label: String, x: Float, y: Float, fill: Int, fg: Int = android.graphics.Color.WHITE, sz: Float = 28f) {
        text.textSize = sz; text.color = fg
        val tw = text.measureText(label)
        bg.color = fill
        drawIntoCanvas {
            it.nativeCanvas.drawRoundRect(RectF(x - tw / 2 - 11, y - sz * 0.62f, x + tw / 2 + 11, y + sz * 0.45f), 16f, 16f, bg)
            it.nativeCanvas.drawText(label, x, y + sz * 0.3f, text)
        }
    }
    fun tree(p: V3, rM: Double) {
        val r = maxOf((rM * px).toFloat(), 3.5f)
        val c = Offset(o.sx(p.x), o.sy(p.y))
        drawCircle(Color(0x55000000), r, Offset(c.x + r * 0.25f, c.y + r * 0.3f))
        drawCircle(TreeC, r, c)
        drawCircle(TreeHi, r * 0.55f, Offset(c.x - r * 0.25f, c.y - r * 0.25f))
    }

    for ((type, polys) in scene.layers) {
        if (type == FeatureType.WOOD) {
            polys.forEach { drawPath(path(it, true), WoodC) }
            scene.woodTrees.forEach { tree(it, 4.0) }
            continue
        }
        val (fill, edge) = when (type) {
            FeatureType.WATER -> WaterC to WaterEdge
            FeatureType.FAIRWAY -> FairwayC to FairwayEdge
            FeatureType.TEE -> TeeC to FairwayEdge
            FeatureType.BUNKER -> SandC to SandEdge
            else -> GreenC to Color(0xFF6CC03C)
        }
        for (poly in polys) {
            val p = path(poly, true)
            drawPath(p, fill)
            drawPath(p, edge, style = Stroke(width = if (type == FeatureType.GREEN) 3f else 2f, join = StrokeJoin.Round))
            if (type == FeatureType.GREEN) {
                // mowing stripes
                val step = maxOf(px * 3f, 8f)
                clipPath(p) {
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(GreenStripe, Offset(x, size.height), Offset(x + size.height, 0f), step / 2)
                        x += step
                    }
                }
            }
        }
    }
    scene.paths.forEach { drawPath(path(it, false), Color(0xAAE6DCC0), style = Stroke(width = maxOf(px * 1.5f, 2f), cap = StrokeCap.Round)) }
    scene.trees.forEach { tree(it, 3.5) }
    scene.ob.forEach { drawPath(path(it, false), ObC, style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f)))) }

    if (!labels) {
        // thumbnail: just the flag
        val g = scene.pin ?: scene.green
        val c = Offset(o.sx(g.x), o.sy(g.y))
        drawLine(Color.White, c, Offset(c.x, c.y - 22f), 3f)
        drawPath(Path().apply { moveTo(c.x, c.y - 22f); lineTo(c.x + 14f, c.y - 17f); lineTo(c.x, c.y - 12f); close() }, Color(0xFFE53935))
        return
    }

    // yardage rings around the green centre
    val gc = scene.green
    val gcGeo = scene.frame.toGeo(gc.x, gc.y)
    if (!greenMode) for (yd in listOf(50, 100, 150, 200, 250, 300)) {
        val r = yd * GeoMath.METERS_PER_YARD
        if (r > scene.length + 15) continue
        drawCircle(Color(0x66FFFFFF), (r * px).toFloat(), Offset(o.sx(gc.x), o.sy(gc.y)), style = Stroke(width = 2f))
        pill("$yd", o.sx(gc.x), o.sy(gc.y - r), 0x99000000.toInt(), android.graphics.Color.WHITE, 24f)
    }

    // hazards: carry (near edge) / far edge from the player, or from the tee when not on the hole
    val here = player?.takeIf { GeoMath.distanceMeters(it, scene.hole.tee) < 800 } ?: scene.hole.tee
    LayoutOps.hazardsAhead(layout, scene.hole, here, limit = 5).forEach { hz ->
        val water = hz.type == FeatureType.WATER
        val fill = if (water) 0xCC1565C0.toInt() else 0xDDF3E3A6.toInt()
        val fg = if (water) android.graphics.Color.WHITE else android.graphics.Color.rgb(60, 45, 0)
        val a = scene.frame.toLocal(hz.front); val b = scene.frame.toLocal(hz.back)
        pill(d(hz.frontM), o.sx(a.first), o.sy(a.second), fill, fg, 24f)
        if (hz.backM - hz.frontM > 15) pill(d(hz.backM), o.sx(b.first), o.sy(b.second), fill, fg, 20f)
    }

    // flag
    val flag = scene.pin ?: gc
    val fc = Offset(o.sx(flag.x), o.sy(flag.y))
    drawCircle(Color(0xFF212121), 5f, fc)
    drawLine(Color.White, fc, Offset(fc.x, fc.y - 34f), 3f)
    drawPath(Path().apply { moveTo(fc.x, fc.y - 34f); lineTo(fc.x + 22f, fc.y - 27f); lineTo(fc.x, fc.y - 20f); close() }, Color(0xFFE53935))

    // tee
    val tc = Offset(o.sx(0.0), o.sy(0.0))
    drawCircle(Color.White, 8f, tc); drawCircle(Color(0xFF1B5E20), 4.5f, tc)

    // green: front / centre / back along the approach
    if (greenMode) {
        val zs = scene.greenRing
        if (zs.isNotEmpty()) {
            val front = zs.minByOrNull { it.y }!!; val back = zs.maxByOrNull { it.y }!!
            val pf = scene.frame.toGeo(front.x, front.y); val pb = scene.frame.toGeo(back.x, back.y)
            pill("F " + d(GeoMath.distanceMeters(here, pf)), o.sx(front.x), o.sy(front.y) + 30f, 0xCC10301A.toInt(), android.graphics.Color.WHITE, 24f)
            pill("B " + d(GeoMath.distanceMeters(here, pb)), o.sx(back.x), o.sy(back.y) - 40f, 0xCC10301A.toInt(), android.graphics.Color.WHITE, 24f)
        }
    }
    pill(d(GeoMath.distanceMeters(here, gcGeo)), fc.x, fc.y - 62f, 0xDD10301A.toInt(), 0xFFC6FF00.toInt(), 32f)

    // player
    player?.takeIf { GeoMath.distanceMeters(it, scene.hole.tee) < 800 }?.let {
        val l = scene.frame.toLocal(it); val c = Offset(o.sx(l.first), o.sy(l.second))
        drawCircle(Color.White, 15f, c); drawCircle(Color(0xFF2196F3), 10f, c)
    }

    // target: here → target → green, with both distances (like the watch view)
    if (target != null) {
        val tl = scene.frame.toLocal(target)
        val hl = scene.frame.toLocal(here)
        val tp = Offset(o.sx(tl.first), o.sy(tl.second))
        val hp = Offset(o.sx(hl.first), o.sy(hl.second))
        val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 10f))
        drawLine(Color.White, hp, tp, 4f, pathEffect = dash)
        drawLine(Color.White, tp, fc, 4f, pathEffect = dash)
        drawCircle(Color(0xFFE53935), 20f, tp, style = Stroke(width = 5f)); drawCircle(Color.White, 6f, tp)
        pill(d(GeoMath.distanceMeters(here, target)), (hp.x + tp.x) / 2, (hp.y + tp.y) / 2, 0xDD10301A.toInt(), android.graphics.Color.WHITE, 30f)
        pill(d(GeoMath.distanceMeters(target, gcGeo)), (tp.x + fc.x) / 2, (tp.y + fc.y) / 2, 0xDD10301A.toInt(), 0xFFC6FF00.toInt(), 30f)
    }
}
