# Emoji Mosaic Generator - Android App

Native Android (Kotlin) port of the [Amosix](https://amosix.in) Emoji Mosaic Generator.

## Setup

### Required Asset Files

Before building, you need to add these files to `app/src/main/assets/`:

1. **`kd_tree.json`** - Copy from the root of this repository
2. **`NotoColorEmoji.ttf`** - Download Google's Noto Color Emoji font:
   - Download from: https://fonts.google.com/noto/specimen/Noto+Color+Emoji
   - Or from: https://github.com/googlefonts/noto-emoji/releases
   - Place the `.ttf` file in the assets folder

### Build

1. Open the `android/` folder in Android Studio
2. Ensure assets are in place
3. Sync Gradle
4. Run on device/emulator (API 24+)

## Architecture

```
MainActivity.kt          -> Simple UI: Pick image -> Show result -> Save
MosaicEngine.kt          -> Orchestrates the full pipeline
ImagePreprocessor.kt     -> Crop, brightness, tone overlay, unsharp mask, contrast
TileColorExtractor.kt    -> Split bitmap into 8x8 tiles, compute avg RGB
KDTree.kt                -> Parse kd_tree.json + nearest neighbor search
EmojiUtils.kt            -> Convert emoji filenames to Unicode characters
MosaicRenderer.kt        -> Draw emojis on Android Canvas with Noto font
MosaicExporter.kt        -> Save bitmap to gallery (2K JPEG / 4K PNG)
```

## Pipeline (matches web app exactly)

1. **Crop** to 1080x1354 portrait ratio
2. **Preprocess**: brightness(0.8) -> yellow tone multiply -> unsharp mask(53, 2.9) -> brightness(1.18) + contrast(1.05)
3. **Tile extraction**: 8x8 pixel tiles, average RGB per tile
4. **KD-tree matching**: Find nearest emoji by squared Euclidean distance in RGB space
5. **Render**: Draw emoji characters on Canvas at 4x scale using Noto Color Emoji font
6. **Export**: 2K (62% JPEG q85) or 4K (full PNG)
