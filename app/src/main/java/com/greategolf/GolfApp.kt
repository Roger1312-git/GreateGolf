package com.greategolf

import android.app.Application
import com.greategolf.data.Course
import com.greategolf.data.CourseCatalog
import com.greategolf.data.CourseLayoutRepository
import com.greategolf.data.GolfDatabase
import com.greategolf.data.GolfRepository
import com.greategolf.platform.LocationProvider
import com.greategolf.platform.SwingSensor
import com.greategolf.platform.VideoSwingAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

/** Poor-man's DI container: everything the screens need lives here. */
class GolfApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository by lazy { GolfRepository(GolfDatabase.create(this)) }
    val courses: List<Course> by lazy { CourseCatalog.load(this) }
    val layouts by lazy { CourseLayoutRepository(this) }
    val location by lazy { LocationProvider(this) }
    val swingSensor by lazy { SwingSensor(this) }
    val videoAnalyzer by lazy { VideoSwingAnalyzer(this) }

    /** Round the player is currently playing; shared by the GPS and scorecard tabs. */
    val activeRoundId = MutableStateFlow<Long?>(null)

    override fun onCreate() {
        super.onCreate()
        // osmdroid: identify ourselves to the OSM tile servers and keep its cache private.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = filesDir
            osmdroidTileCache = cacheDir.resolve("osm")
        }
        appScope.launch { repository.seedDefaultBag() }
    }
}
