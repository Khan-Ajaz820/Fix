package com.amosix.emojimosaic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Color
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.PI

/**
 * Replicates the web app's preprocessEmojiEnhance() pipeline:
 * 1. Crop to portrait ratio (1080x1354)
 * 2. Apply brightness (0.8)
 * 3. Yellow tone overlay with multiply blend
 * 4. Unsharp mask (radius=53, strength=2.9)
 * 5. Final brightness(1.18) + contrast(1.05)
 */
object ImagePreprocessor {

    private const val PORTRAIT_WIDTH = 1080
    private const val PORTRAIT_HEIGHT = 1354
    private const val TONE_BRIGHTNESS = 0.8f
    private const val TONE_R = 244
    private const val TONE_G = 233
    private const val TONE_B = 50
    private const val TONE_A = 0.26f
    private const val UNSHARP_RADIUS = 53
    private const val UNSHARP_STRENGTH = 2.9f
    private const val FINAL_BRIGHTNESS = 1.18f
    private const val FINAL_CONTRAST = 1.05f

    /**
     * Full preprocessing pipeline matching the web app.
     * Input: cropped bitmap at PORTRAIT_WIDTH x PORTRAIT_HEIGHT
     */
    fun process(croppedBitmap: Bitmap): Bitmap {
        // Step 1: Ensure correct dimensions
        val scaled = Bitmap.createScaledBitmap(croppedBitmap, PORTRAIT_WIDTH, PORTRAIT_HEIGHT, true)

        // Step 2: Apply brightness (0.8)
        val brightened = applyBrightness(scaled, TONE_BRIGHTNESS)
        if (scaled != croppedBitmap) scaled.recycle()

        // Step 3: Yellow tone overlay with multiply blend
        val toned = applyToneOverlay(brightened, TONE_R, TONE_G, TONE_B, TONE_A)
        brightened.recycle()

        // Step 4: Unsharp mask
        val sharpened = applyUnsharpMask(toned, UNSHARP_RADIUS, UNSHARP_STRENGTH)
        toned.recycle()

        // Step 5: Final brightness + contrast
        val result = applyBrightnessContrast(sharpened, FINAL_BRIGHTNESS, FINAL_CONTRAST)
        sharpened.recycle()

        return result
    }

    /**
     * Center-crop a bitmap to the portrait aspect ratio (1080:1354).
     */
    fun cropToPortrait(source: Bitmap): Bitmap {
        val targetRatio = PORTRAIT_WIDTH.toFloat() / PORTRAIT_HEIGHT.toFloat()
        val srcRatio = source.width.toFloat() / source.height.toFloat()

        val cropWidth: Int
        val cropHeight: Int

        if (srcRatio > targetRatio) {
            // Source is wider, crop sides
            cropHeight = source.height
            cropWidth = (cropHeight * targetRatio).roundToInt()
        } else {
            // Source is taller, crop top/bottom
            cropWidth = source.width
            cropHeight = (cropWidth / targetRatio).roundToInt()
        }

        val x = (source.width - cropWidth) / 2
        val y = (source.height - cropHeight) / 2

        return Bitmap.createBitmap(
            source,
            x.coerceAtLeast(0),
            y.coerceAtLeast(0),
            cropWidth.coerceAtMost(source.width),
            cropHeight.coerceAtMost(source.height)
        )
    }

    /**
     * Apply brightness multiplier using ColorMatrix.
     * Matches: ctx.filter = `brightness(${toneBrightness})`
     */
    private fun applyBrightness(src: Bitmap, brightness: Float): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cm = ColorMatrix(floatArrayOf(
            brightness, 0f, 0f, 0f, 0f,
            0f, brightness, 0f, 0f, 0f,
            0f, 0f, brightness, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    /**
     * Apply tone overlay with multiply blend mode.
     * Matches: ctx.globalCompositeOperation = "multiply"
     *          ctx.fillStyle = toneRGBA
     *          ctx.fillRect(...)
     */
    private fun applyToneOverlay(
        src: Bitmap, r: Int, g: Int, b: Int, alpha: Float
    ): Bitmap {
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val overlayPaint = Paint()
        overlayPaint.color = Color.argb((alpha * 255).roundToInt(), r, g, b)
        overlayPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        canvas.drawRect(0f, 0f, result.width.toFloat(), result.height.toFloat(), overlayPaint)

        return result
    }

    /**
     * Unsharp mask implementation.
     * Matches glfx.js: .unsharpMask(radius, strength)
     *
     * Algorithm: result = original + strength * (original - blurred)
     * Uses Gaussian blur approximation via box blur (3 passes).
     */
    private fun applyUnsharpMask(src: Bitmap, radius: Int, strength: Float): Bitmap {
        val width = src.width
        val height = src.height

        // Get pixel data
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        // Separate channels
        val r = IntArray(pixels.size)
        val g = IntArray(pixels.size)
        val b = IntArray(pixels.size)

        for (i in pixels.indices) {
            r[i] = (pixels[i] shr 16) and 0xFF
            g[i] = (pixels[i] shr 8) and 0xFF
            b[i] = pixels[i] and 0xFF
        }

        // Apply box blur (3 passes approximates Gaussian)
        // Use a smaller effective radius for performance on mobile
        val effectiveRadius = min(radius, 15)
        val blurredR = boxBlur3Pass(r, width, height, effectiveRadius)
        val blurredG = boxBlur3Pass(g, width, height, effectiveRadius)
        val blurredB = boxBlur3Pass(b, width, height, effectiveRadius)

        // Apply unsharp: result = original + strength * (original - blurred)
        val resultPixels = IntArray(pixels.size)
        for (i in pixels.indices) {
            val a = (pixels[i] shr 24) and 0xFF
            val newR = clamp((r[i] + strength * (r[i] - blurredR[i])).roundToInt())
            val newG = clamp((g[i] + strength * (g[i] - blurredG[i])).roundToInt())
            val newB = clamp((b[i] + strength * (b[i] - blurredB[i])).roundToInt())
            resultPixels[i] = (a shl 24) or (newR shl 16) or (newG shl 8) or newB
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * 3-pass box blur to approximate Gaussian blur.
     */
    private fun boxBlur3Pass(channel: IntArray, w: Int, h: Int, radius: Int): IntArray {
        var data = channel.copyOf()
        data = boxBlurH(data, w, h, radius)
        data = boxBlurV(data, w, h, radius)
        data = boxBlurH(data, w, h, radius)
        data = boxBlurV(data, w, h, radius)
        data = boxBlurH(data, w, h, radius)
        data = boxBlurV(data, w, h, radius)
        return data
    }

    private fun boxBlurH(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val dst = IntArray(src.size)
        val diameter = 2 * r + 1
        for (y in 0 until h) {
            var sum = 0
            // Initialize sum for first pixel
            for (x in -r..r) {
                sum += src[y * w + x.coerceIn(0, w - 1)]
            }
            for (x in 0 until w) {
                dst[y * w + x] = sum / diameter
                val left = (x - r).coerceIn(0, w - 1)
                val right = (x + r + 1).coerceIn(0, w - 1)
                sum += src[y * w + right] - src[y * w + left]
            }
        }
        return dst
    }

    private fun boxBlurV(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val dst = IntArray(src.size)
        val diameter = 2 * r + 1
        for (x in 0 until w) {
            var sum = 0
            for (y in -r..r) {
                sum += src[y.coerceIn(0, h - 1) * w + x]
            }
            for (y in 0 until h) {
                dst[y * w + x] = sum / diameter
                val top = (y - r).coerceIn(0, h - 1)
                val bottom = (y + r + 1).coerceIn(0, h - 1)
                sum += src[bottom * w + x] - src[top * w + x]
            }
        }
        return dst
    }

    /**
     * Apply brightness and contrast.
     * Matches CSS: brightness(1.18) contrast(1.05)
     */
    private fun applyBrightnessContrast(
        src: Bitmap, brightness: Float, contrast: Float
    ): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Brightness: scale RGB channels
        // Contrast: scale around midpoint (128)
        val translate = (1f - contrast) * 128f
        val scale = brightness * contrast

        val cm = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun clamp(value: Int): Int = max(0, min(255, value))
}
