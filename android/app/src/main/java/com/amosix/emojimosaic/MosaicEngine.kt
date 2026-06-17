package com.amosix.emojimosaic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface

/**
 * Main engine that orchestrates the entire mosaic generation pipeline.
 * This is the single entry point that ties all components together.
 *
 * Pipeline (matching web app exactly):
 * 1. Crop image to portrait ratio
 * 2. Preprocess (brightness, tone, unsharp, contrast)
 * 3. Extract tile colors (8x8 grid)
 * 4. Match each tile to nearest emoji via KD-tree
 * 5. Convert filenames to emoji characters
 * 6. Render mosaic on canvas
 */
class MosaicEngine(private val context: Context) {

    private val kdTree = KDTree()
    private lateinit var emojiTypeface: Typeface
    private var isInitialized = false

    /**
     * Initialize the engine: load KD-tree and emoji font.
     * Call this once before generating mosaics.
     */
    fun initialize() {
        // Load KD-tree from assets
        kdTree.loadFromAssets(context, "kd_tree.json")

        // Load Noto Color Emoji font from assets
        emojiTypeface = Typeface.createFromAsset(context.assets, "NotoColorEmoji.ttf")

        isInitialized = true
    }

    /**
     * Generate a mosaic from a source bitmap.
     * This runs the complete pipeline matching the web app.
     *
     * @param sourceBitmap The original image from the user
     * @param onProgress Callback for progress updates
     * @return The rendered mosaic bitmap
     */
    fun generate(
        sourceBitmap: Bitmap,
        onProgress: ((String) -> Unit)? = null
    ): Bitmap {
        check(isInitialized) { "MosaicEngine not initialized. Call initialize() first." }

        // Step 1: Crop to portrait ratio
        onProgress?.invoke("Cropping image...")
        val cropped = ImagePreprocessor.cropToPortrait(sourceBitmap)

        // Step 2: Preprocess (brightness, tone, unsharp, contrast)
        onProgress?.invoke("Preprocessing image...")
        val preprocessed = ImagePreprocessor.process(cropped)
        if (cropped != sourceBitmap) cropped.recycle()

        // Step 3: Extract tile colors
        onProgress?.invoke("Extracting tile colors...")
        val tileGrid = TileColorExtractor.extract(preprocessed, tileSize = 8)
        preprocessed.recycle()

        // Step 4: Match tiles to emojis via KD-tree
        onProgress?.invoke("Matching emojis (${tileGrid.colors.size} tiles)...")
        val matchedFiles = kdTree.matchAll(tileGrid.colors)

        // Step 5: Convert filenames to emoji characters
        onProgress?.invoke("Converting to emoji characters...")
        val emojis = EmojiUtils.filenamesToEmojis(matchedFiles)

        // Step 6: Render mosaic
        onProgress?.invoke("Rendering mosaic...")
        val mosaic = MosaicRenderer.render(
            emojis = emojis,
            cols = tileGrid.cols,
            rows = tileGrid.rows,
            emojiTypeface = emojiTypeface
        )

        onProgress?.invoke("Done!")
        return mosaic
    }
}
