package com.greategolf.data

import android.content.Context
import com.greategolf.domain.CourseLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Course geometry (holes, greens, fairways, bunkers, water) from OpenStreetMap via the Overpass API,
 * cached on disk so a course only has to be downloaded once and then works offline.
 */
class CourseLayoutRepository(context: Context) {
    private val dir = File(context.filesDir, "layouts").apply { mkdirs() }
    private val endpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
    )

    // v2: also stores trees / pins / out-of-bounds; older cache files are ignored and re-downloaded
    private fun cacheFile(courseId: String) = File(dir, "$courseId.v2.json")

    /** Disk cache only; never touches the network. */
    suspend fun cached(courseId: String): CourseLayout? = withContext(Dispatchers.IO) {
        cacheFile(courseId).takeIf { it.exists() }?.let {
            runCatching { OverpassParser.parseLayout(it.readText()) }.getOrNull()
        }
    }

    suspend fun load(course: Course, forceRefresh: Boolean = false): CourseLayout = withContext(Dispatchers.IO) {
        if (!forceRefresh) cached(course.id)?.takeIf { !it.isEmpty }?.let { return@withContext it }

        val near = course.location
        val found = runCatching {
            OverpassParser.pickCourseBounds(
                post("[out:json][timeout:40];nwr[\"leisure\"=\"golf_course\"](around:10000,${near.lat},${near.lng});out tags bb;"),
                course.name, near,
            )
        }.getOrNull()
        // Our bundled coordinates are approximate, so prefer the real course outline when OSM has it.
        val b = (found ?: Bounds(near.lat - 0.02, near.lng - 0.02, near.lat + 0.02, near.lng + 0.02)).padded(0.002)
        val bbox = "${b.minLat},${b.minLng},${b.maxLat},${b.maxLng}"
        val json = post(
            "[out:json][timeout:90];(" +
                "way[\"golf\"]($bbox);relation[\"golf\"]($bbox);" +
                "way[\"natural\"=\"water\"]($bbox);way[\"natural\"=\"wood\"]($bbox);way[\"natural\"=\"tree_row\"]($bbox);" +
                "node[\"golf\"=\"pin\"]($bbox);node[\"natural\"=\"tree\"]($bbox);" +
                ");out geom;"
        )
        val layout = OverpassParser.parseLayout(json)
        if (!layout.isEmpty) cacheFile(course.id).writeText(json)
        layout
    }

    private fun post(query: String): String {
        var last: Exception? = null
        for (ep in endpoints) {
            try {
                val c = URL(ep).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 15_000
                c.readTimeout = 100_000
                c.setRequestProperty("User-Agent", "GreateGolf/1.0 (Android golf app)")
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
                if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
                return c.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) { last = e }
        }
        throw IOException("Không tải được dữ liệu sân: ${last?.message}")
    }
}

