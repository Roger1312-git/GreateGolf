package com.greategolf.ui.course

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.greategolf.domain.CourseLayout
import com.greategolf.domain.FeatureType
import com.greategolf.domain.GeoMath
import com.greategolf.domain.GeoPoint
import com.greategolf.domain.GreenInfo
import com.greategolf.domain.Hole
import com.greategolf.domain.LayoutOps
import com.greategolf.ui.RequireLocationOptional
import com.greategolf.ui.appViewModel
import com.greategolf.ui.fmtDistance
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.util.GeoPoint as OsmPoint

/** Esri World Imagery (z/y/x tile order). Imagery (c) Esri and contributors; attribution is shown on screen. */
private val EsriSatellite = object : OnlineTileSourceBase(
    "EsriWorldImagery", 0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Imagery © Esri, Maxar, Earthstar Geographics",
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}

private class MapHolder {
    var layoutRef: CourseLayout? = null
    var selectedRef: Int? = null
    var initialised = false
    var tileName: String? = null
    val staticOverlays = ArrayList<Overlay>()
    var player: Marker? = null
    var measure: Marker? = null
}

@Composable
fun CourseMapScreen(courseId: String, onBack: () -> Unit, onOpen3D: (Int) -> Unit) {
    val vm = appViewModel { CourseViewModel(it, courseId, null) }
    val state by vm.state.collectAsState()
    val selected by vm.selectedHole.collectAsState()
    val satellite by vm.satellite.collectAsState()
    val fix by vm.fix.collectAsState()
    val measure by vm.measurePoint.collectAsState()
    val green by vm.green.collectAsState()
    val layout = (state as? LayoutState.Ready)?.layout
    val hole = selected?.let { layout?.holes?.getOrNull(it) }

    RequireLocationOptional { granted ->
        DisposableEffect(granted) {
            if (granted) vm.startGps()
            onDispose { vm.stopGps() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        CourseMap(
            center = vm.course.location,
            layout = layout, selected = selected, satellite = satellite,
            player = fix?.point, measure = measure,
            onTap = vm::measure,
            modifier = Modifier.fillMaxSize(),
        )

        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
                Text(vm.course.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2)
                OutlinedButton(onClick = vm::toggleSatellite) { Text(if (satellite) "Bản đồ" else "Vệ tinh") }
                IconButton(onClick = { vm.load(true) }) { Icon(Icons.Filled.Refresh, "Tải lại") }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (val s = state) {
                LayoutState.Loading -> InfoCard { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.padding(4.dp)); Text("Đang tải dữ liệu sân từ OpenStreetMap…")
                } }
                LayoutState.NoData -> InfoCard {
                    Text("OpenStreetMap chưa có dữ liệu chi tiết (hố, green, bunker) cho sân này.", style = MaterialTheme.typography.bodyMedium)
                    Text("Bạn vẫn xem được ảnh vệ tinh. Có thể đóng góp dữ liệu sân tại openstreetmap.org (thẻ golf=hole, golf=green…).", style = MaterialTheme.typography.bodySmall)
                }
                is LayoutState.Failed -> InfoCard {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { vm.load(true) }) { Text("Thử lại") }
                }
                is LayoutState.Ready -> {
                    if (hole != null) HoleCard(hole, green, fix?.point, measure, onOpen3D = { onOpen3D(selected!!) }, onClear = { vm.select(null) })
                    HoleChips(s.layout.holes, selected, vm::select, nearest = if (fix != null) vm.nearestHoleIndex() else null)
                }
            }
            if (satellite) Text("Imagery © Esri, Maxar, Earthstar Geographics · Dữ liệu sân © OpenStreetMap", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() } }
}

@Composable
private fun HoleChips(holes: List<Hole>, selected: Int?, onSelect: (Int?) -> Unit, nearest: Int?) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Cả sân") })
            if (nearest != null) FilterChip(selected = false, onClick = { onSelect(nearest) }, label = { Text("Hố gần tôi") })
            holes.forEachIndexed { i, h ->
                val dup = holes.count { it.ref == h.ref } > 1
                val suffix = if (dup) "·${holes.take(i).count { it.ref == h.ref } + 1}" else ""
                FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text("${h.ref}$suffix") })
            }
        }
    }
}

@Composable
private fun HoleCard(hole: Hole, green: GreenInfo?, player: GeoPoint?, measure: GeoPoint?, onOpen3D: () -> Unit, onClear: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Hố ${hole.ref}" + (hole.par?.let { " · Par $it" } ?: "") + " · ${fmtDistance(hole.lengthM)}", style = MaterialTheme.typography.titleMedium)
            if (player != null && green != null) {
                Text("Bạn → green: trước %.0f · giữa %.0f · sau %.0f m".format(
                    GeoMath.distanceMeters(player, green.front), GeoMath.distanceMeters(player, green.center), GeoMath.distanceMeters(player, green.back)),
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (measure != null) {
                val parts = buildList {
                    if (player != null) add("từ bạn ${fmtDistance(GeoMath.distanceMeters(player, measure))}")
                    if (green != null) add("tới green ${fmtDistance(GeoMath.distanceMeters(measure, green.center))}")
                }
                if (parts.isNotEmpty()) Text("Điểm chạm: " + parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Chạm lên bản đồ để đo khoảng cách tới một điểm.", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen3D) { Text("Xem 3D") }
                OutlinedButton(onClick = onClear) { Text("Cả sân") }
            }
        }
    }
}

@Composable
internal fun CourseMap(
    center: GeoPoint,
    layout: CourseLayout?,
    selected: Int?,
    satellite: Boolean,
    player: GeoPoint?,
    measure: GeoPoint?,
    onTap: (GeoPoint?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { MapHolder() }
    val tap by rememberUpdatedState(onTap)
    val map = remember {
        MapView(ctx).apply {
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            controller.setZoom(16.0)
            controller.setCenter(OsmPoint(center.lat, center.lng))
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: OsmPoint): Boolean { tap(GeoPoint(p.latitude, p.longitude)); return true }
                override fun longPressHelper(p: OsmPoint): Boolean { tap(null); return true }
            }))
        }
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) map.onResume() else if (e == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); map.onDetach() }
    }

    AndroidView(factory = { map }, modifier = modifier, update = { view ->
        val src = if (satellite) EsriSatellite else TileSourceFactory.MAPNIK
        if (holder.tileName != src.name()) { view.setTileSource(src); holder.tileName = src.name() }

        if (!holder.initialised || holder.layoutRef !== layout || holder.selectedRef != selected) {
            val firstLayout = holder.layoutRef !== layout
            holder.initialised = true
            view.overlays.removeAll(holder.staticOverlays.toSet())
            holder.staticOverlays.clear()
            if (layout != null) holder.staticOverlays += buildOverlays(view, layout, selected)
            // keep the tap overlay (index 0) under everything, dynamic markers on top
            view.overlays.addAll(1, holder.staticOverlays)
            holder.layoutRef = layout; holder.selectedRef = selected
            frame(view, layout, selected, firstLayout)
        }

        holder.player?.let { view.overlays.remove(it) }
        holder.player = player?.let { dot(view, it, Color.rgb(33, 150, 243)) }?.also { view.overlays.add(it) }
        holder.measure?.let { view.overlays.remove(it) }
        holder.measure = measure?.let { dot(view, it, Color.rgb(244, 67, 54)) }?.also { view.overlays.add(it) }
        view.invalidate()
    })
}

private fun frame(view: MapView, layout: CourseLayout?, selected: Int?, first: Boolean) {
    val hole = selected?.let { layout?.holes?.getOrNull(it) }
    if (hole != null && layout != null) {
        val g = LayoutOps.greenOf(layout, hole)
        val pts = hole.line + listOfNotNull(g?.front, g?.back)
        // Rotate so the tee is at the bottom and the green at the top.
        view.mapOrientation = -LayoutOps.bearingDeg(hole.tee, hole.greenEnd).toFloat()
        view.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts.map { OsmPoint(it.lat, it.lng) }), true, 120)
    } else {
        view.mapOrientation = 0f
        if (layout != null && layout.holes.isNotEmpty() && first) {
            val all = layout.holes.flatMap { it.line }
            view.zoomToBoundingBox(BoundingBox.fromGeoPoints(all.map { OsmPoint(it.lat, it.lng) }), true, 60)
        }
    }
}

private fun dot(view: MapView, p: GeoPoint, color: Int): Marker = Marker(view).apply {
    position = OsmPoint(p.lat, p.lng)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    icon = circleIcon(view, null, color, 14)
    setOnMarkerClickListener { _, _ -> true }
}

private fun circleIcon(view: MapView, label: String?, color: Int, radiusDp: Int): BitmapDrawable {
    val d = view.resources.displayMetrics.density
    val r = (radiusDp * d)
    val size = (r * 2 + 4).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2 * d }
    c.drawCircle(size / 2f, size / 2f, r - d, fill)
    c.drawCircle(size / 2f, size / 2f, r - d, ring)
    if (label != null) {
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = r * 1.1f; isFakeBoldText = true }
        c.drawText(label, size / 2f, size / 2f - (t.descent() + t.ascent()) / 2, t)
    }
    return BitmapDrawable(view.resources, bmp)
}

private fun buildOverlays(view: MapView, layout: CourseLayout, selected: Int?): List<Overlay> {
    val out = ArrayList<Overlay>()
    val order = listOf(FeatureType.ROUGH, FeatureType.WOOD, FeatureType.WATER, FeatureType.FAIRWAY, FeatureType.TEE, FeatureType.BUNKER, FeatureType.GREEN)
    fun style(t: FeatureType): Pair<Int, Int> = when (t) {
        FeatureType.ROUGH -> Color.argb(30, 0, 160, 0) to Color.TRANSPARENT
        FeatureType.WOOD -> Color.argb(90, 0, 77, 0) to Color.TRANSPARENT
        FeatureType.WATER -> Color.argb(170, 33, 150, 243) to Color.rgb(21, 101, 192)
        FeatureType.FAIRWAY -> Color.argb(110, 118, 200, 106) to Color.rgb(76, 175, 80)
        FeatureType.TEE -> Color.argb(170, 139, 195, 74) to Color.rgb(104, 159, 56)
        FeatureType.BUNKER -> Color.argb(210, 240, 225, 160) to Color.rgb(191, 161, 74)
        FeatureType.GREEN -> Color.argb(200, 0, 200, 83) to Color.rgb(0, 150, 60)
        FeatureType.CART_PATH -> 0 to Color.argb(160, 200, 200, 200)
    }
    for (t in order) for (f in layout.features.filter { it.type == t }) {
        val (fillC, strokeC) = style(t)
        out += Polygon(view).apply {
            points = f.ring.map { OsmPoint(it.lat, it.lng) }
            fillPaint.color = fillC
            outlinePaint.color = strokeC
            outlinePaint.strokeWidth = 2f
            setOnClickListener { _, _, _ -> false }
        }
    }
    for (f in layout.features.filter { it.type == FeatureType.CART_PATH }) {
        out += Polyline(view).apply {
            setPoints(f.ring.map { OsmPoint(it.lat, it.lng) })
            outlinePaint.color = style(FeatureType.CART_PATH).second
            outlinePaint.strokeWidth = 3f
            setOnClickListener { _, _, _ -> false }
        }
    }
    layout.holes.forEachIndexed { i, h ->
        val sel = i == selected
        out += Polyline(view).apply {
            setPoints(h.line.map { OsmPoint(it.lat, it.lng) })
            outlinePaint.color = if (sel) Color.WHITE else Color.argb(150, 255, 235, 59)
            outlinePaint.strokeWidth = if (sel) 5f else 3f
            setOnClickListener { _, _, _ -> false }
        }
        out += Marker(view).apply {
            position = OsmPoint(h.tee.lat, h.tee.lng)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = circleIcon(view, h.ref.toString(), if (sel) Color.rgb(255, 87, 34) else Color.rgb(27, 94, 32), 11)
            setOnMarkerClickListener { _, _ -> true }
        }
    }
    return out
}
