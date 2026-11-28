package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

data class CompressedImageResult(
    val bitmap: Bitmap,
    val jpegBytes: ByteArray,
    val base64Thumbnail: String
)

/**
 * Field-hardened client-side image compressor.
 * Prevents 10MB+ camera photo OOM crashes and huge Supabase/Gemini payloads by
 * subsampling on decode, scaling proportionally to max 1280px width, and iteratively
 * tuning JPEG quality to stay within ~250KB.
 */
object ImageCompressor {

    const val DEFAULT_MAX_WIDTH = 1280
    const val DEFAULT_MAX_BYTES = 250 * 1024

    fun computeScaledDimensions(
        srcWidth: Int,
        srcHeight: Int,
        maxWidth: Int = DEFAULT_MAX_WIDTH
    ): Pair<Int, Int> {
        if (srcWidth <= 0 || srcHeight <= 0) return 1 to 1
        if (srcWidth <= maxWidth) return srcWidth to srcHeight
        val ratio = maxWidth.toDouble() / srcWidth.toDouble()
        val newHeight = (srcHeight * ratio).roundToInt().coerceAtLeast(1)
        return maxWidth to newHeight
    }

    fun computeInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        targetMaxDim: Int
    ): Int {
        var sample = 1
        if (srcWidth <= 0 || srcHeight <= 0) return sample
        while ((srcWidth / sample) > targetMaxDim * 2 || (srcHeight / sample) > targetMaxDim * 2) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    fun compressBitmapToJpegBytes(
        bitmap: Bitmap,
        maxWidth: Int = DEFAULT_MAX_WIDTH,
        maxBytes: Int = DEFAULT_MAX_BYTES
    ): ByteArray {
        val (targetW, targetH) = computeScaledDimensions(bitmap.width, bitmap.height, maxWidth)
        var working = if (targetW != bitmap.width || targetH != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }

        val qualities = intArrayOf(86, 78, 70, 62, 54, 46, 38, 30)
        var bestBytes = ByteArray(0)
        for (q in qualities) {
            val out = ByteArrayOutputStream()
            working.compress(Bitmap.CompressFormat.JPEG, q, out)
            bestBytes = out.toByteArray()
            if (bestBytes.size <= maxBytes) {
                return bestBytes
            }
        }

        // Fallback extra scale-down if an ultra-dense texture still exceeds ~250KB
        if (bestBytes.size > maxBytes && working.width > 720) {
            val (w2, h2) = computeScaledDimensions(working.width, working.height, (working.width * 0.75f).roundToInt())
            working = Bitmap.createScaledBitmap(working, w2, h2, true)
            val out = ByteArrayOutputStream()
            working.compress(Bitmap.CompressFormat.JPEG, 48, out)
            bestBytes = out.toByteArray()
        }
        return bestBytes
    }

    fun compressBitmapForAi(
        bitmap: Bitmap,
        maxWidth: Int = DEFAULT_MAX_WIDTH,
        maxBytes: Int = DEFAULT_MAX_BYTES
    ): Bitmap {
        val (targetW, targetH) = computeScaledDimensions(bitmap.width, bitmap.height, maxWidth)
        if (targetW == bitmap.width && targetH == bitmap.height) {
            val probe = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, probe)
            if (probe.size() <= maxBytes) return bitmap
        }
        val jpegBytes = compressBitmapToJpegBytes(bitmap, maxWidth, maxBytes)
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: bitmap
    }

    fun compressUriToJpegAndBitmap(
        context: Context,
        uri: Uri,
        maxWidth: Int = DEFAULT_MAX_WIDTH,
        maxBytes: Int = DEFAULT_MAX_BYTES
    ): CompressedImageResult? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            } ?: return null

            val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxWidth)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            val jpegBytes = compressBitmapToJpegBytes(decoded, maxWidth, maxBytes)
            val finalBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: decoded
            val thumbB64 = createCompactBase64Thumbnail(jpegBytes)
            CompressedImageResult(
                bitmap = finalBitmap,
                jpegBytes = jpegBytes,
                base64Thumbnail = thumbB64
            )
        } catch (_: Exception) {
            null
        }
    }

    fun createCompactBase64Thumbnail(
        jpegBytes: ByteArray,
        maxDim: Int = 360,
        quality: Int = 58
    ): String {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)
            val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            val bmp = BitmapFactory.decodeByteArray(
                jpegBytes,
                0,
                jpegBytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return ""
            val scale = minOf(1.0f, maxDim.toFloat() / maxOf(bmp.width, bmp.height).coerceAtLeast(1))
            val scaled = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * scale).roundToInt().coerceAtLeast(1),
                    (bmp.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
            } else bmp
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }
}
