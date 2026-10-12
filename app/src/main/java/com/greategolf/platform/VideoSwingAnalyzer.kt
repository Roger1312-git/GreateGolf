package com.greategolf.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.greategolf.domain.Joint
import com.greategolf.domain.PoseFrame
import com.greategolf.domain.Pt
import com.greategolf.domain.SwingAnalysis
import com.greategolf.domain.SwingReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class KeyFrame(val label: String, val image: Bitmap)
data class VideoAnalysis(val report: SwingReport, val keyFrames: List<KeyFrame>, val framesAnalyzed: Int)

/**
 * Samples a recorded swing video, runs ML Kit pose detection on each sampled frame (on device,
 * offline) and feeds the joints to [SwingAnalysis].
 */
class VideoSwingAnalyzer(private val context: Context) {

    private val landmarks = mapOf(
        Joint.NOSE to PoseLandmark.NOSE,
        Joint.L_SHOULDER to PoseLandmark.LEFT_SHOULDER, Joint.R_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
        Joint.L_HIP to PoseLandmark.LEFT_HIP, Joint.R_HIP to PoseLandmark.RIGHT_HIP,
        Joint.L_WRIST to PoseLandmark.LEFT_WRIST, Joint.R_WRIST to PoseLandmark.RIGHT_WRIST,
        Joint.L_KNEE to PoseLandmark.LEFT_KNEE, Joint.R_KNEE to PoseLandmark.RIGHT_KNEE,
        Joint.L_ANKLE to PoseLandmark.LEFT_ANKLE, Joint.R_ANKLE to PoseLandmark.RIGHT_ANKLE,
    )

    /** Returns null if no usable swing was found (no person, too short...). */
    suspend fun analyze(uri: Uri, onProgress: (Float) -> Unit): VideoAnalysis? = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        val detector = PoseDetection.getClient(
            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE).build()
        )
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return@withContext null
            val stepMs = maxOf(40L, durationMs / MAX_FRAMES)
            val frames = ArrayList<PoseFrame>()

            var t = 0L
            while (t < durationMs) {
                val bmp = scaledFrame(retriever, t)
                if (bmp != null) {
                    val pose = Tasks.await(detector.process(InputImage.fromBitmap(bmp, 0)))
                    val joints = HashMap<Joint, Pt>()
                    for ((joint, id) in landmarks) {
                        val lm = pose.getPoseLandmark(id) ?: continue
                        if (lm.inFrameLikelihood < 0.5f) continue
                        joints[joint] = Pt(lm.position.x.toDouble() / bmp.width, lm.position.y.toDouble() / bmp.height)
                    }
                    if (joints.size >= 6) frames += PoseFrame(t, joints)
                    bmp.recycle()
                }
                t += stepMs
                onProgress((t.toFloat() / durationMs).coerceAtMost(1f))
            }

            val report = SwingAnalysis.analyze(frames) ?: return@withContext null
            val keys = listOf(
                "Address" to report.takeawayIndex, "Đỉnh backswing" to report.topIndex,
                "Impact" to report.impactIndex, "Finish" to report.finishIndex,
            ).mapNotNull { (label, idx) ->
                val f = frames.getOrNull(idx) ?: return@mapNotNull null
                scaledFrame(retriever, f.timeMs)?.let { KeyFrame(label, drawSkeleton(it, f)) }
            }
            VideoAnalysis(report, keys, frames.size)
        } finally {
            detector.close()
            retriever.release()
        }
    }

    private fun scaledFrame(r: MediaMetadataRetriever, timeMs: Long): Bitmap? {
        val raw = r.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null
        val scale = MAX_SIDE.toFloat() / maxOf(raw.width, raw.height)
        if (scale >= 1f) return raw.copy(Bitmap.Config.ARGB_8888, true)
        val out = Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true)
        if (out !== raw) raw.recycle()
        return out.copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun drawSkeleton(bmp: Bitmap, f: PoseFrame): Bitmap {
        val c = Canvas(bmp)
        val line = Paint().apply { color = Color.rgb(255, 214, 0); strokeWidth = bmp.width / 150f; isAntiAlias = true }
        val dot = Paint().apply { color = Color.rgb(0, 200, 83); isAntiAlias = true }
        fun p(j: Joint) = f.joints[j]?.let { it.x.toFloat() * bmp.width to it.y.toFloat() * bmp.height }
        val bones = listOf(
            Joint.L_SHOULDER to Joint.R_SHOULDER, Joint.L_HIP to Joint.R_HIP,
            Joint.L_SHOULDER to Joint.L_HIP, Joint.R_SHOULDER to Joint.R_HIP,
            Joint.L_SHOULDER to Joint.L_WRIST, Joint.R_SHOULDER to Joint.R_WRIST,
            Joint.L_HIP to Joint.L_KNEE, Joint.R_HIP to Joint.R_KNEE,
            Joint.L_KNEE to Joint.L_ANKLE, Joint.R_KNEE to Joint.R_ANKLE,
        )
        for ((a, b) in bones) {
            val pa = p(a) ?: continue; val pb = p(b) ?: continue
            c.drawLine(pa.first, pa.second, pb.first, pb.second, line)
        }
        for (j in f.joints.keys) p(j)?.let { c.drawCircle(it.first, it.second, bmp.width / 90f, dot) }
        return bmp
    }

    private companion object {
        const val MAX_FRAMES = 240
        const val MAX_SIDE = 480
    }
}
