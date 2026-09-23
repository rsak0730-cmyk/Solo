# Solo Tilt Live Wallpaper

Native Android live wallpaper inspired by the Solo Tilt web experience.

## What it does

- Runs as a real Android Live Wallpaper.
- Uses the device rotation-vector sensor for smooth tilt/parallax motion.
- Lets the user choose an image from the device.
- Uses a layered render with depth, zoom, parallax and optional blur.
- Includes sensitivity, depth and blur controls.
- Stops the render loop and sensor updates when the wallpaper is not visible.

## Repository

This is a native Android/Kotlin project. It does not use Flutter.

## Build

The GitHub Actions workflow installs Gradle 9.3.1 and builds:

`app/build/outputs/apk/release/app-release.apk`

## Install

1. Install the APK.
2. Open Solo Tilt.
3. Choose an image (optional).
4. Adjust sensitivity/depth/blur.
5. Tap **Set as Live Wallpaper**.
6. Confirm it in Android's wallpaper preview.
7. Return to the Home screen and tilt the phone.

## Notes

Android's Live Wallpaper API creates a WallpaperService/Engine surface and notifies the engine when the wallpaper becomes visible or hidden. The renderer therefore only runs continuously while the wallpaper is visible.

The effect is a native implementation of the interaction pattern, not a copy of the website's source code.
