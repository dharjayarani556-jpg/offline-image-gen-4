# DARK JISAN - Offline Image Generation

Offline text-to-image generation Android app using MediaPipe Image Generator.

## Features
- Fully offline (after model is loaded)
- Text-to-Image
- Optional Reference Image (EDGE condition)
- Multiple quality presets (Fast / Balanced / Quality / Optimize / Ultra)
- Save to Gallery
- Works with `sd15_bing` style MediaPipe-converted models

## How to open in Android Studio
1. Clone or download this repository
2. Open the folder in Android Studio
3. Let Gradle sync
4. Build & Run on a real device (8GB+ RAM recommended)

## Important
- You need a **MediaPipe-compatible** converted Stable Diffusion model (e.g. `sd15_bing` folder).
- Normal `.safetensors` / `.ckpt` files will **not** work directly.
- First launch → Select Model Folder → wait for copy → then Generate.

## Package
`com.example.offlineimagegen`
