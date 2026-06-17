package com.amosix.emojimosaic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var engine: MosaicEngine
    private var mosaicBitmap: Bitmap? = null

    private lateinit var btnPickImage: Button
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var ivResult: ImageView
    private lateinit var layoutButtons: View
    private lateinit var btnSave2K: Button
    private lateinit var btnSave4K: Button

    // Image picker launcher
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind views
        btnPickImage = findViewById(R.id.btnPickImage)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        ivResult = findViewById(R.id.ivResult)
        layoutButtons = findViewById(R.id.layoutButtons)
        btnSave2K = findViewById(R.id.btnSave2K)
        btnSave4K = findViewById(R.id.btnSave4K)

        // Initialize engine in background
        engine = MosaicEngine(this)
        lifecycleScope.launch {
            tvStatus.text = "Loading emoji data..."
            progressBar.visibility = View.VISIBLE

            withContext(Dispatchers.IO) {
                engine.initialize()
            }

            tvStatus.text = "Ready! Select an image to generate mosaic"
            progressBar.visibility = View.GONE
        }

        // Pick image button
        btnPickImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Save buttons
        btnSave2K.setOnClickListener {
            mosaicBitmap?.let { bitmap ->
                lifecycleScope.launch {
                    val uri = withContext(Dispatchers.IO) {
                        MosaicExporter.saveStandard(this@MainActivity, bitmap)
                    }
                    if (uri != null) {
                        Toast.makeText(this@MainActivity, "✅ Saved 2K mosaic!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Failed to save", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnSave4K.setOnClickListener {
            mosaicBitmap?.let { bitmap ->
                lifecycleScope.launch {
                    val uri = withContext(Dispatchers.IO) {
                        MosaicExporter.saveUltraHD(this@MainActivity, bitmap)
                    }
                    if (uri != null) {
                        Toast.makeText(this@MainActivity, "✅ Saved 4K mosaic!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Failed to save", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun processImage(uri: Uri) {
        lifecycleScope.launch {
            try {
                // Show loading state
                btnPickImage.isEnabled = false
                progressBar.visibility = View.VISIBLE
                layoutButtons.visibility = View.GONE
                ivResult.setImageBitmap(null)

                // Load bitmap from URI
                val sourceBitmap = withContext(Dispatchers.IO) {
                    loadBitmapFromUri(uri)
                } ?: run {
                    tvStatus.text = "Failed to load image"
                    btnPickImage.isEnabled = true
                    progressBar.visibility = View.GONE
                    return@launch
                }

                // Generate mosaic
                val result = withContext(Dispatchers.IO) {
                    engine.generate(sourceBitmap) { status ->
                        // Update status on main thread
                        lifecycleScope.launch {
                            tvStatus.text = status
                        }
                    }
                }

                // Recycle source
                sourceBitmap.recycle()

                // Show result
                mosaicBitmap?.recycle()
                mosaicBitmap = result
                ivResult.setImageBitmap(result)
                layoutButtons.visibility = View.VISIBLE
                tvStatus.text = "✨ Mosaic generated! (${result.width}x${result.height})"

            } catch (e: Exception) {
                tvStatus.text = "Error: ${e.message}"
                e.printStackTrace()
            } finally {
                btnPickImage.isEnabled = true
                progressBar.visibility = View.GONE
            }
        }
    }

    /**
     * Load a bitmap from a content URI with downsampling for large images.
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            // First, get dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            // Calculate sample size to avoid OOM
            val maxDim = 2048
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }

            // Decode with sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mosaicBitmap?.recycle()
    }
}
