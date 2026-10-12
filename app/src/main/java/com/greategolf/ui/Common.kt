package com.greategolf.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.greategolf.GolfApp
import com.greategolf.domain.GeoMath
import java.util.Locale

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (GolfApp) -> VM): VM =
    viewModel(key = key, factory = viewModelFactory { initializer { create(this[APPLICATION_KEY] as GolfApp) } })

fun CreationExtras.app() = this[APPLICATION_KEY] as GolfApp

/** "143 m · 156 yd" */
fun fmtDistance(meters: Double): String =
    String.format(Locale.US, "%.0f m · %.0f yd", meters, GeoMath.metersToYards(meters))

fun fmtToPar(diff: Int): String = when {
    diff == 0 -> "E"
    diff > 0 -> "+$diff"
    else -> "$diff"
}

fun hasLocationPermission(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Renders [content] once fine-location permission is granted; otherwise shows a prompt.
 */
@Composable
fun RequireLocation(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasLocationPermission(ctx)
    }
    if (granted) {
        content()
    } else {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Cần quyền vị trí (GPS) để đo khoảng cách.", style = MaterialTheme.typography.bodyLarge)
            Button(onClick = {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Text("Cấp quyền vị trí") }
        }
    }
}

/** Like [RequireLocation] but never blocks: renders [content] with whether permission is currently granted. */
@Composable
fun RequireLocationOptional(content: @Composable (granted: Boolean) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasLocationPermission(ctx)
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!granted) launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    content(granted)
}
