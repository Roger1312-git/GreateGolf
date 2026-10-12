package com.greategolf.data

import android.content.Context
import com.greategolf.domain.GeoPoint
import org.json.JSONArray

data class Course(
    val id: String,
    val name: String,
    val province: String,
    val region: String,
    val lat: Double,
    val lng: Double,
    val holes: Int,
    val par: Int,
    val note: String,
) {
    val location get() = GeoPoint(lat, lng)
}

/** Courses are bundled in assets/courses_vn.json so the map works offline (tiles aside). */
object CourseCatalog {
    fun load(context: Context): List<Course> {
        val text = context.assets.open("courses_vn.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Course(
                id = o.getString("id"),
                name = o.getString("name"),
                province = o.getString("province"),
                region = o.getString("region"),
                lat = o.getDouble("lat"),
                lng = o.getDouble("lng"),
                holes = o.optInt("holes", 18),
                par = o.optInt("par", 72),
                note = o.optString("note", ""),
            )
        }
    }
}
