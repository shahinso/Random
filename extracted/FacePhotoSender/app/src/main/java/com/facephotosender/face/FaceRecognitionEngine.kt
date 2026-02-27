package com.facephotosender.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.sqrt

/**
 * FaceRecognitionEngine
 *
 * Uses ML Kit Face Detection to:
 *  1. Detect faces in images
 *  2. Extract a simplified embedding from facial landmark positions
 *  3. Compare embeddings with cosine similarity
 *
 * NOTE: ML Kit's on-device face detection doesn't produce raw embeddings like
 * FaceNet does. We use the face bounding box + landmark geometry as a
 * lightweight descriptor. For production-grade recognition, drop in a
 * TFLite FaceNet model (see FaceNetEngine placeholder below).
 */
class FaceRecognitionEngine(private val context: Context) {

    companion object {
        private const val TAG = "FaceRecognitionEngine"
        const val SIMILARITY_THRESHOLD = 0.72f   // tune as needed
        const val MAX_PHOTOS = 50
    }

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.10f)
            .enableTracking()
            .build()
    )

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Detect faces in a bitmap and return their embeddings + bounding boxes */
    suspend fun detectFaces(bitmap: Bitmap): List<FaceResult> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return runDetector(image).map { face ->
            FaceResult(
                face = face,
                embedding = extractEmbedding(face, bitmap),
                croppedFace = cropFace(face, bitmap)
            )
        }
    }

    /** Detect faces from a content URI (e.g. from MediaStore) */
    suspend fun detectFacesFromUri(uri: Uri): List<FaceResult> {
        val bitmap = loadBitmap(uri) ?: return emptyList()
        return detectFaces(bitmap)
    }

    /** Compare two embeddings and return cosine similarity [0..1] */
    fun similarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot  += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else (dot / denom).coerceIn(0f, 1f)
    }

    /** Serialise float array → byte array for Room storage */
    fun embeddingToBytes(embedding: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(embedding.size * 4)
        embedding.forEach { buf.putFloat(it) }
        return buf.array()
    }

    /** Deserialise byte array → float array */
    fun bytesToEmbedding(bytes: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(bytes)
        return FloatArray(bytes.size / 4) { buf.getFloat() }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private suspend fun runDetector(image: InputImage): List<Face> =
        suspendCancellableCoroutine { cont ->
            detector.process(image)
                .addOnSuccessListener { faces -> cont.resume(faces) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    /**
     * Build a geometric embedding from facial landmarks.
     * We normalise by face bounding-box size so the descriptor is
     * scale- and position-invariant.
     *
     * Dimensions: 22 (11 landmark x/y pairs, normalised)
     */
    private fun extractEmbedding(face: Face, bitmap: Bitmap): FloatArray {
        val box = face.boundingBox
        val w = box.width().toFloat().coerceAtLeast(1f)
        val h = box.height().toFloat().coerceAtLeast(1f)
        val cx = box.centerX().toFloat()
        val cy = box.centerY().toFloat()

        val landmarks = listOf(
            com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE,
            com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE,
            com.google.mlkit.vision.face.FaceLandmark.NOSE_BASE,
            com.google.mlkit.vision.face.FaceLandmark.LEFT_EAR,
            com.google.mlkit.vision.face.FaceLandmark.RIGHT_EAR,
            com.google.mlkit.vision.face.FaceLandmark.LEFT_CHEEK,
            com.google.mlkit.vision.face.FaceLandmark.RIGHT_CHEEK,
            com.google.mlkit.vision.face.FaceLandmark.MOUTH_LEFT,
            com.google.mlkit.vision.face.FaceLandmark.MOUTH_RIGHT,
            com.google.mlkit.vision.face.FaceLandmark.MOUTH_BOTTOM,
            com.google.mlkit.vision.face.FaceLandmark.LEFT_EAR_TIP
        )

        val result = FloatArray(landmarks.size * 2)
        landmarks.forEachIndexed { i, type ->
            val lm = face.getLandmark(type)
            result[i * 2]     = if (lm != null) (lm.position.x - cx) / w else 0f
            result[i * 2 + 1] = if (lm != null) (lm.position.y - cy) / h else 0f
        }

        // Append head Euler angles as extra features (normalised to [-1,1])
        // Not strictly needed but helps discriminate similar geometries
        // result += floatArrayOf(face.headEulerAngleY / 90f, face.headEulerAngleZ / 90f)

        return result
    }

    private fun cropFace(face: Face, src: Bitmap): Bitmap? {
        return try {
            val box = face.boundingBox
            val pad = (box.width() * 0.2f).toInt()
            val left   = (box.left   - pad).coerceAtLeast(0)
            val top    = (box.top    - pad).coerceAtLeast(0)
            val right  = (box.right  + pad).coerceAtMost(src.width)
            val bottom = (box.bottom + pad).coerceAtMost(src.height)
            Bitmap.createBitmap(src, left, top, right - left, bottom - top)
        } catch (e: Exception) {
            null
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $uri", e)
            null
        }
    }

    fun close() = detector.close()
}

data class FaceResult(
    val face: Face,
    val embedding: FloatArray,
    val croppedFace: Bitmap?
)
