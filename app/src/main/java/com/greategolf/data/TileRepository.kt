package com.greategolf.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.greategolf.domain.TileKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Satellite imagery tiles with a disk cache, so a hole only has to be downloaded once.
 *
 * NOTE: [URL_TEMPLATE] points at Esri World Imagery, fine for development. For a published app use
 * imagery you are licensed to use (e.g. Mapbox / MapTiler / Maxar / Google) and swap the template here.
 */
class TileRepository(context: Context) {
    private val dir = File(context.cacheDir, "tiles").apply { mkdirs() }

    suspend fun get(k: TileKey): Bitmap? = withContext(Dispatchers.IO) {
        val f = File(dir, "${k.z}_${k.x}_${k.y}.jpg")
        if (f.exists() && f.length() > 0) BitmapFactory.decodeFile(f.path)?.let { return@withContext it }
        try {
            val url = URL_TEMPLATE.replace("{z}", "${k.z}").replace("{x}", "${k.x}").replace("{y}", "${k.y}")
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", "GreateGolf/1.0 (Android golf app)")
            if (c.responseCode !in 200..299) return@withContext null
            val bytes = c.inputStream.use { it.readBytes() }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            f.writeBytes(bytes)
            bmp
        } catch (e: Exception) { null }
    }

    companion object {
        const val URL_TEMPLATE = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
    }
}
