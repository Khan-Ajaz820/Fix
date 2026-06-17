package com.amosix.emojimosaic

import android.content.Context
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.InputStreamReader

/**
 * KD-Tree for nearest-neighbor emoji color matching.
 * Direct port of the WebAssembly (full_mosaic.ts) logic.
 *
 * The tree is built from kd_tree.json which maps emoji SVG filenames
 * to their average RGB colors. Each node splits on one axis (R=0, G=1, B=2)
 * and stores the emoji source path.
 *
 * Search uses squared Euclidean distance (no sqrt needed) with
 * deterministic tie-breaking (smaller source index wins).
 */
class KDTree {

    // Flattened node arrays (matching the WASM approach for performance)
    private var nodeCount = 0
    private lateinit var nodeAvgR: FloatArray
    private lateinit var nodeAvgG: FloatArray
    private lateinit var nodeAvgB: FloatArray
    private lateinit var nodeLeft: IntArray
    private lateinit var nodeRight: IntArray
    private lateinit var nodeAxis: IntArray
    private lateinit var nodeSrcIndex: IntArray

    private var rootIndex = -1

    // Emoji source paths (e.g., "sprite/emoji_u1f600.svg")
    val emojiSrcs = mutableListOf<String>()
    private val srcMap = mutableMapOf<String, Int>()

    // Temporary best match state for recursive search
    private var tmpBestDist = Float.MAX_VALUE
    private var tmpBestSrc = -1

    /**
     * Load and parse kd_tree.json from assets.
     * Matches the web app's loadKDTreeIntoWasm() function.
     */
    fun loadFromAssets(context: Context, filename: String = "kd_tree.json") {
        val inputStream = context.assets.open(filename)
        val reader = InputStreamReader(inputStream)
        val jsonRoot = JsonParser.parseReader(reader)
        reader.close()

        // First pass: flatten the tree into arrays
        val nodes = mutableListOf<NodeData>()
        emojiSrcs.clear()
        srcMap.clear()

        rootIndex = buildFlatTree(jsonRoot, nodes)

        // Initialize arrays
        nodeCount = nodes.size
        nodeAvgR = FloatArray(nodeCount)
        nodeAvgG = FloatArray(nodeCount)
        nodeAvgB = FloatArray(nodeCount)
        nodeLeft = IntArray(nodeCount)
        nodeRight = IntArray(nodeCount)
        nodeAxis = IntArray(nodeCount)
        nodeSrcIndex = IntArray(nodeCount)

        for (i in nodes.indices) {
            val node = nodes[i]
            nodeAvgR[i] = node.avgR
            nodeAvgG[i] = node.avgG
            nodeAvgB[i] = node.avgB
            nodeLeft[i] = node.left
            nodeRight[i] = node.right
            nodeAxis[i] = node.axis
            nodeSrcIndex[i] = node.srcIndex
        }
    }

    /**
     * Recursively build flat tree from JSON.
     * Matches the web app's recursive tree flattening in loadKDTreeIntoWasm().
     *
     * The web app processes: left subtree first, then right, then current node.
     * Returns the index of the current node in the flat array.
     */
    private fun buildFlatTree(json: JsonElement?, nodes: MutableList<NodeData>): Int {
        if (json == null || json.isJsonNull) return -1

        val obj = json.asJsonObject

        // Recurse left and right first (matching web app order)
        val leftIdx = buildFlatTree(
            if (obj.has("left") && !obj.get("left").isJsonNull) obj.get("left") else null,
            nodes
        )
        val rightIdx = buildFlatTree(
            if (obj.has("right") && !obj.get("right").isJsonNull) obj.get("right") else null,
            nodes
        )

        // Get emoji source index
        var srcIndex = -1
        if (obj.has("point")) {
            val point = obj.getAsJsonObject("point")
            if (point.has("src")) {
                val src = point.get("src").asString
                srcIndex = srcMap.getOrPut(src) {
                    val idx = emojiSrcs.size
                    emojiSrcs.add(src)
                    idx
                }
            }
        }

        // Get average color
        var avgR = 0f
        var avgG = 0f
        var avgB = 0f
        if (obj.has("point")) {
            val point = obj.getAsJsonObject("point")
            if (point.has("avg")) {
                val avg = point.getAsJsonArray("avg")
                avgR = avg[0].asFloat
                avgG = avg[1].asFloat
                avgB = avg[2].asFloat
            }
        }

        // Get axis
        val axis = if (obj.has("axis")) obj.get("axis").asInt else 0

        // Add current node
        val currentIdx = nodes.size
        nodes.add(NodeData(avgR, avgG, avgB, leftIdx, rightIdx, axis, srcIndex))

        return currentIdx
    }

    /**
     * Find the nearest emoji for a single tile color.
     * Matches the WASM searchNode() function exactly.
     */
    fun findNearest(r: Float, g: Float, b: Float): String? {
        if (rootIndex < 0) return null

        tmpBestDist = Float.MAX_VALUE
        tmpBestSrc = -1

        searchNode(rootIndex, r, g, b)

        return if (tmpBestSrc in emojiSrcs.indices) emojiSrcs[tmpBestSrc] else null
    }

    /**
     * Match all tile colors at once.
     * Matches the WASM processAll() function.
     */
    fun matchAll(colors: List<FloatArray>): List<String?> {
        return colors.map { color ->
            findNearest(color[0], color[1], color[2])
        }
    }

    /**
     * Recursive KD-tree nearest neighbor search.
     * Direct port of searchNode() from full_mosaic.ts.
     */
    private fun searchNode(nodeIdx: Int, tr: Float, tg: Float, tb: Float) {
        if (nodeIdx < 0) return

        val nr = nodeAvgR[nodeIdx]
        val ng = nodeAvgG[nodeIdx]
        val nb = nodeAvgB[nodeIdx]

        // Squared distance (no sqrt needed)
        val dr = nr - tr
        val dg = ng - tg
        val db = nb - tb
        val d = dr * dr + dg * dg + db * db

        // Check current node - with deterministic tie-breaking
        val src = nodeSrcIndex[nodeIdx]
        if (d < tmpBestDist || (d == tmpBestDist && src >= 0 && tmpBestSrc >= 0 && src < tmpBestSrc)) {
            tmpBestDist = d
            tmpBestSrc = src
        } else if (tmpBestSrc < 0 && src >= 0) {
            tmpBestDist = d
            tmpBestSrc = src
        }

        // Decide which subtree to search first
        val axis = nodeAxis[nodeIdx]
        val diff = when (axis) {
            0 -> tr - nr
            1 -> tg - ng
            else -> tb - nb
        }

        val first: Int
        val second: Int
        if (diff <= 0) {
            first = nodeLeft[nodeIdx]
            second = nodeRight[nodeIdx]
        } else {
            first = nodeRight[nodeIdx]
            second = nodeLeft[nodeIdx]
        }

        // Search closer subtree first
        if (first >= 0) searchNode(first, tr, tg, tb)

        // Only search other subtree if it could contain a closer match
        val diffSq = diff * diff
        if (diffSq <= tmpBestDist && second >= 0) {
            searchNode(second, tr, tg, tb)
        }
    }

    private data class NodeData(
        val avgR: Float,
        val avgG: Float,
        val avgB: Float,
        val left: Int,
        val right: Int,
        val axis: Int,
        val srcIndex: Int
    )
}
