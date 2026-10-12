package com.greategolf.ui.map

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.greategolf.GolfApp
import com.greategolf.data.Course
import com.greategolf.domain.GeoMath
import com.greategolf.domain.GeoPoint
import com.greategolf.ui.appViewModel
import com.greategolf.ui.hasLocationPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.Normalizer
import org.osmdroid.util.GeoPoint as OsmPoint

class MapViewModel(private val app: GolfApp) : ViewModel() {
    val query = MutableStateFlow("")
    val region = MutableStateFlow<String?>(null)
    val selected = MutableStateFlow<Course?>(null)
    val regions = app.courses.map { it.region }.distinct()

    val filtered = combine(query, region) { q, r ->
        val needle = fold(q)
        app.courses.filter { c ->
            (r == null || c.region == r) &&
                (needle.isEmpty() || fold(c.name).contains(needle) || fold(c.province).contains(needle))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, app.courses)

    fun select(c: Course?) { selected.value = c }

    /** Starts a round at [course] and makes it the active one. */
    fun startRound(course: Course, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = app.repository.startRound(course, course.name, minOf(course.holes, 18), System.currentTimeMillis())
            app.activeRoundId.value = id
            onCreated(id)
        }
    }

    private fun fold(s: String) =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace("\\p{M}".toRegex(), "").replace('đ', 'd')
}

@Composable
fun MapScreen(onPlayHere: () -> Unit, onOpenRound: (Long) -> Unit, onOpenCourse: (String) -> Unit) {
    val vm = appViewModel { MapViewModel(it) }
    val courses by vm.filtered.collectAsState()
    val query by vm.query.collectAsState()
    val region by vm.region.collectAsState()
    val selected by vm.selected.collectAsState()
    val ctx = LocalContext.current
    var myLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var permitted by remember { mutableStateOf(hasLocationPermission(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = hasLocationPermission(ctx)
    }

    Box(Modifier.fillMaxSize()) {
        CourseMap(
            courses = courses,
            showMyLocation = permitted,
            onSelect = vm::select,
            onMyLocation = { myLocation = it },
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.padding(8.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { vm.query.value = it },
                placeholder = { Text("Tìm sân hoặc tỉnh…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = region == null, onClick = { vm.region.value = null }, label = { Text("Tất cả (${courses.size})") })
                vm.regions.forEach { r ->
                    FilterChip(selected = region == r, onClick = { vm.region.value = if (region == r) null else r }, label = { Text(r) })
                }
                if (!permitted) {
                    OutlinedButton(onClick = {
                        permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }) { Text("Vị trí của tôi") }
                }
            }
        }

        selected?.let { c ->
            Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                    Text("${c.province} · ${c.region} · ${c.holes} hố", style = MaterialTheme.typography.bodyMedium)
                    myLocation?.let {
                        val km = GeoMath.distanceMeters(it, c.location) / 1000
                        Text("Cách bạn %.0f km".format(km), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { onOpenCourse(c.id) }, modifier = Modifier.fillMaxWidth()) { Text("Xem sân · bản đồ hố · 3D") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.startRound(c) { onOpenRound(it) } }) { Text("Bắt đầu vòng") }
                        OutlinedButton(onClick = { vm.startRound(c) { onPlayHere() } }) { Text("Chơi với GPS") }
                        OutlinedButton(onClick = { vm.select(null) }) { Text("Đóng") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseMap(
    courses: List<Course>,
    showMyLocation: Boolean,
    onSelect: (Course) -> Unit,
    onMyLocation: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            controller.setZoom(5.5)
            controller.setCenter(OsmPoint(15.5, 107.5)) // Vietnam
        }
    }
    var locationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> { map.onResume(); locationOverlay?.enableMyLocation() }
                Lifecycle.Event.ON_PAUSE -> { locationOverlay?.disableMyLocation(); map.onPause() }
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); locationOverlay?.disableMyLocation(); map.onDetach() }
    }

    DisposableEffect(showMyLocation) {
        if (showMyLocation) {
            val o = MyLocationNewOverlay(GpsMyLocationProvider(ctx), map).also { it.enableMyLocation() }
            map.overlays.add(o)
            locationOverlay = o
            o.runOnFirstFix {
                o.myLocation?.let { onMyLocation(GeoPoint(it.latitude, it.longitude)) }
            }
        }
        onDispose {
            locationOverlay?.let { map.overlays.remove(it); it.disableMyLocation() }
            locationOverlay = null
        }
    }

    AndroidView(
        factory = { map },
        modifier = modifier,
        update = { view ->
            view.overlays.removeAll { it is Marker }
            courses.forEach { c ->
                val m = Marker(view).apply {
                    position = OsmPoint(c.lat, c.lng)
                    title = c.name
                    snippet = c.province
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { marker, mv ->
                        onSelect(c)
                        marker.showInfoWindow()
                        mv.controller.animateTo(marker.position)
                        true
                    }
                }
                view.overlays.add(m)
            }
            view.invalidate()
        },
    )
}
