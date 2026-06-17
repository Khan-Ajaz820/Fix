package com.amosix.emojimosaic

import android.graphics.Bitmap

/**
 * Extracts average RGB color for each tile in the image.
 * Matches the web app's calculateTileColorsFromCanvas() function.
 *
 * Divides the image into tileSize x tileSize pixel blocks and
 * computes the average R, G, B for each block (ignoring fully
 * transparent pixels, alpha == 0).
 */
object TileColorExtractor {

    data class TileGrid(
        val colors: List<FloatArray>,  // Each entry is [R, G, B] as floats
        val cols: Int,
        val rows: Int
    )

    /**
     * Extract tile colors from bitmap.
     * @param bitmap The preprocessed image
     * @param tileSize Size of each tile in pixels (default 8, matching web app)
     * @return TileGrid with colors array and grid dimensions
     */
    fun extract(bitmap: Bitmap, tileSize: Int = 8): TileGrid {
        val width = bitmap.width
        val height = bitmap.height
        val cols = width / tileSize
        val rows = height / tileSize

        // Get all pixels at once for performance
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val colors = ArrayList<FloatArray>(cols * rows)

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val startX = col * tileSize
                val startY = row * tileSize

                var sumR = 0L
                var sumG = 0L
                var sumB = 0L
                var count = 0

                for (dy in 0 until tileSize) {
                    for (dx in 0 until tileSize) {
                        val px = pixels[(startY + dy) * width + (startX + dx)]
                        val alpha = (px shr 24) and 0xFF

                        // Skip fully transparent pixels (matching web: if alpha !== 0)
                        if (alpha != 0) {
                            sumR += (px shr 16) and 0xFF
                            sumG += (px shr 8) and 0xFF
                            sumB += px and 0xFF
                            count++
                        }
                    }
                }

                if (count == 0) {
                    colors.add(floatArrayOf(0f, 0f, 0f))
                } else {
                    colors.add(floatArrayOf(
                        sumR.toFloat() / count,
                        sumG.toFloat() / count,
                        sumB.toFloat() / count
                    ))
                }
            }
        }

        return TileGrid(colors, cols, rows)
    }
}
