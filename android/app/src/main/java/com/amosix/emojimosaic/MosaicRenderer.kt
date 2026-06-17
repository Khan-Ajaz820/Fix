package com.amosix.emojimosaic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Renders the emoji mosaic onto an Android Bitmap.
 * Matches the web app's renderFromResults() function.
 *
 * Web app logic:
 * - Canvas size: cols * tileSize * 4  x  rows * tileSize * 4  (4x scale for quality)
 * - ctx.scale(4, 4)
 * - Font: NotoEmoji at tileSize * emojiScale pixels
 * - Each emoji drawn at (col * tileSize + offset, row * tileSize + offset)
 * - offset = (tileSize - tileSize * emojiScale) / 2
 * - Background: black
 */
object MosaicRenderer {

    private const val TILE_SIZE = 8
    private const val SCALE_FACTOR = 4  // 4x rendering scale (matching web)
    private const val EMOJI_SCALE = 0.83f  // Matching web app's 0.83 scale

    /**
     * Render the mosaic bitmap.
     *
     * @param emojis List of emoji characters (one per tile)
     * @param cols Number of tile columns
     * @param rows Number of tile rows
     * @param emojiTypeface The Noto Color Emoji typeface
     * @return The rendered mosaic bitmap at 4x resolution
     */
    fun render(
        emojis: List<String>,
        cols: Int,
        rows: Int,
        emojiTypeface: Typeface
    ): Bitmap {
        val canvasWidth = cols * TILE_SIZE * SCALE_FACTOR
        val canvasHeight = rows * TILE_SIZE * SCALE_FACTOR

        val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Scale canvas (matching web: ctx.scale(4, 4))
        canvas.scale(SCALE_FACTOR.toFloat(), SCALE_FACTOR.toFloat())

        // Black background
        canvas.drawColor(Color.BLACK)

        // Setup emoji paint
        val fontSize = TILE_SIZE * EMOJI_SCALE
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = emojiTypeface
            textSize = fontSize
            textAlign = Paint.Align.LEFT
        }

        // Offset to center emoji within tile
        val offset = (TILE_SIZE - fontSize) / 2f

        // Draw each emoji
        var index = 0
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (index < emojis.size) {
                    val emoji = emojis[index]
                    val x = col * TILE_SIZE + offset
                    val y = row * TILE_SIZE + offset + fontSize // drawText uses baseline
                    canvas.drawText(emoji, x, y, paint)
                }
                index++
            }
        }

        return bitmap
    }

    /**
     * Get the output dimensions for a given grid.
     */
    fun getOutputDimensions(cols: Int, rows: Int): Pair<Int, Int> {
        return Pair(cols * TILE_SIZE * SCALE_FACTOR, rows * TILE_SIZE * SCALE_FACTOR)
    }
}
