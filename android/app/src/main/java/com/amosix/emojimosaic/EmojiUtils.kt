package com.amosix.emojimosaic

/**
 * Utility to convert emoji SVG filenames to Unicode characters.
 * Direct port of the web app's filenameToEmoji() function.
 *
 * Example:
 *   "sprite/emoji_u1f600.svg" -> "😀"
 *   "sprite/emoji_u1f468_200d_1f9b0.svg" -> "👨‍🦰" (ZWJ sequence)
 */
object EmojiUtils {

    /**
     * Convert emoji source filename to Unicode string.
     * Matches: filenameToEmoji() in bundle.min.js
     *
     * Logic:
     * 1. Take filename part (after last /)
     * 2. Remove "emoji_u" prefix
     * 3. Remove ".svg" suffix
     * 4. Split by "_"
     * 5. Parse each part as hex code point
     * 6. Combine into string
     */
    fun filenameToEmoji(filename: String): String {
        return try {
            val name = filename
                .lowercase()
                .substringAfterLast("/")
                .removePrefix("emoji_u")
                .removeSuffix(".svg")

            val codePoints = name.split("_")
                .filter { it.isNotEmpty() }
                .map { it.toInt(16) }

            String(codePoints.toIntArray(), 0, codePoints.size)
        } catch (e: Exception) {
            // Fallback to white square if parsing fails
            "\u2B1C" // ⬜
        }
    }

    /**
     * Convert a list of emoji source filenames to Unicode strings.
     * Null entries become the fallback emoji (⬜).
     */
    fun filenamesToEmojis(filenames: List<String?>): List<String> {
        return filenames.map { filename ->
            if (filename != null) filenameToEmoji(filename) else "\u2B1C"
        }
    }
}
