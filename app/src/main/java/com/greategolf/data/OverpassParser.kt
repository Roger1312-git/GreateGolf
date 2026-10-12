package com.greategolf.data

import com.greategolf.domain.CourseLayout
import com.greategolf.domain.FeatureType
import com.greategolf.domain.GeoFeature
import com.greategolf.domain.GeoMath
import com.greategolf.domain.GeoPoint
import com.greategolf.domain.Hole
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

data class Bounds(val minLat: Double, val minLng: Double, val maxLat: Double, val maxLng: Double) {
    val center get() = GeoPoint((minLat + maxLat) / 2, (minLng + maxLng) / 2)
    fun padded(deg: Double) = Bounds(minLat - deg, minLng - deg, maxLat + deg, maxLng + deg)
}

/** Pure JSON <-> domain mapping for Overpass API responses (`out geom` / `out tags bb`). */
object OverpassParser {

    // ---- step 1: locate the course -------------------------------------------------------------

    private val STOP = setOf("golf", "club", "course", "resort", "country", "and", "the", "links", "villas", "spa")

    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace("\\p{M}".toRegex(), "").replace('đ', 'd')

    private fun tokens(s: String) = fold(s).split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in STOP }.toSet()

    /**
     * Picks the `leisure=golf_course` element that best matches [courseName] (shared name tokens first,
     * then proximity to [near]). Null if nothing plausible (name match, or within [maxFallbackM]).
     */
    fun pickCourseBounds(json: String, courseName: String, near: GeoPoint, maxFallbackM: Double = 6_000.0): Bounds? {
        val els = JSONObject(json).optJSONArray("elements") ?: return null
        val want = tokens(courseName)
        var best: Triple<Bounds, Int, Double>? = null
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val b = e.optJSONObject("bounds")?.let {
                Bounds(it.getDouble("minlat"), it.getDouble("minlon"), it.getDouble("maxlat"), it.getDouble("maxlon"))
            } ?: continue
            val tags = e.optJSONObject("tags")
            val names = listOfNotNull(tags?.optString("name"), tags?.optString("name:en"), tags?.optString("name:vi"))
            val score = names.maxOfOrNull { n -> tokens(n).intersect(want).size } ?: 0
            val dist = GeoMath.distanceMeters(b.center, near)
            val cur = best
            if (cur == null || score > cur.second || (score == cur.second && dist < cur.third)) best = Triple(b, score, dist)
        }
        return best?.takeIf { it.second > 0 || it.third <= maxFallbackM }?.first
    }

    // ---- step 2: course features ---------------------------------------------------------------

    fun parseLayout(json: String): CourseLayout {
        val els = JSONObject(json).optJSONArray("elements") ?: return CourseLayout(emptyList(), emptyList())
        val features = ArrayList<GeoFeature>()
        val holes = ArrayList<Hole>()

        fun addGeometry(tags: JSONObject?, geom: List<GeoPoint>) {
            if (geom.size < 2) return
            val golf = tags?.optString("golf").orEmpty()
            if (golf == "hole") {
                val ref = tags?.optString("ref").orEmpty().takeWhile { it.isDigit() }.toIntOrNull() ?: return
                holes += Hole(ref, tags?.optString("par")?.toIntOrNull(), geom, tags?.optString("name")?.ifBlank { null })
                return
            }
            val type = featureType(tags) ?: return
            val closed = geom.size >= 4 && geom.first() == geom.last()
            if (type == FeatureType.CART_PATH) features += GeoFeature(type, geom, false)
            else if (closed) features += GeoFeature(type, geom.dropLast(1), true)
        }

        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val tags = e.optJSONObject("tags")
            when (e.optString("type")) {
                "way" -> addGeometry(tags, points(e.optJSONArray("geometry")))
                "relation" -> {
                    val members = e.optJSONArray("members") ?: continue
                    for (m in 0 until members.length()) {
                        val mem = members.getJSONObject(m)
                        if (mem.optString("type") == "way" && mem.optString("role") != "inner") {
                            addGeometry(tags, points(mem.optJSONArray("geometry")))
                        }
                    }
                }
            }
        }
        val uniqueHoles = holes.distinctBy { it.ref to it.line.first() }.sortedBy { it.ref }
        return CourseLayout(features, uniqueHoles)
    }

    private fun featureType(tags: JSONObject?): FeatureType? {
        if (tags == null) return null
        return when (tags.optString("golf")) {
            "green" -> FeatureType.GREEN
            "fairway" -> FeatureType.FAIRWAY
            "tee" -> FeatureType.TEE
            "bunker" -> FeatureType.BUNKER
            "water_hazard", "lateral_water_hazard" -> FeatureType.WATER
            "rough" -> FeatureType.ROUGH
            "cartpath", "path" -> FeatureType.CART_PATH
            else -> when {
                tags.optString("natural") == "water" -> FeatureType.WATER
                tags.optString("natural") == "wood" -> FeatureType.WOOD
                else -> null
            }
        }
    }

    private fun points(arr: JSONArray?): List<GeoPoint> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { GeoPoint(it.getDouble("lat"), it.getDouble("lon")) }
        }
    }
}
