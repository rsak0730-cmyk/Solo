package com.solotilt.livewallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SoloTiltWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return SoloTiltEngine()
    }

    private inner class SoloTiltEngine : Engine(), SensorEventListener {

        private val prefs = getSharedPreferences(
            MainActivity.PREFS,
            Context.MODE_PRIVATE
        )

        private val sensorManager =
            getSystemService(Context.SENSOR_SERVICE) as SensorManager

        private val rotationSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        private val rotationMatrix = FloatArray(9)
        private val orientation = FloatArray(3)

        private val renderThread = HandlerThread("SoloTiltRenderer").apply {
            start()
        }

        private val renderHandler = Handler(renderThread.looper)

        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val imagePaint = Paint(
            Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
        )
        private val blurPaint = Paint(
            Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
        )
        private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private var bitmap: Bitmap? = null
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var visible = false
        private var frameScheduled = false

        private var rawTiltX = 0f
        private var rawTiltY = 0f
        private var tiltX = 0f
        private var tiltY = 0f

        private var sensitivity = 100f
        private var depth = 100f
        private var blur = 30f

        private val prefsListener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (
                    key == MainActivity.KEY_IMAGE_URI ||
                    key == MainActivity.KEY_SENSITIVITY ||
                    key == MainActivity.KEY_DEPTH ||
                    key == MainActivity.KEY_BLUR
                ) {
                    reloadSettings()
                    if (key == MainActivity.KEY_IMAGE_URI) {
                        loadBitmap()
                    }
                    requestFrame()
                }
            }

        private val frameRunnable = object : Runnable {
            override fun run() {
                frameScheduled = false

                if (!visible) return

                drawFrame()

                if (visible) {
                    requestFrame()
                }
            }
        }

        init {
            prefs.registerOnSharedPreferenceChangeListener(prefsListener)
            reloadSettings()
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)

            surfaceHolder.setFormat(android.graphics.PixelFormat.RGBA_8888)
            setOffsetNotificationsEnabled(false)
            loadBitmap()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceWidth = holder.surfaceFrame.width()
            surfaceHeight = holder.surfaceFrame.height()
            requestFrame()
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width
            surfaceHeight = height
            requestFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            surfaceWidth = 0
            surfaceHeight = 0
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible

            if (visible) {
                startSensors()
                requestFrame()
            } else {
                stopSensors()
                renderHandler.removeCallbacks(frameRunnable)
                frameScheduled = false
            }
        }

        override fun onDestroy() {
            visible = false
            stopSensors()
            renderHandler.removeCallbacksAndMessages(null)

            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)

            bitmap?.recycle()
            bitmap = null

            renderThread.quitSafely()
            super.onDestroy()
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (!visible) return

            if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR ||
                event.sensor.type == Sensor.TYPE_ROTATION_VECTOR
            ) {
                SensorManager.getRotationMatrixFromVector(
                    rotationMatrix,
                    event.values
                )

                SensorManager.getOrientation(
                    rotationMatrix,
                    orientation
                )

                val pitch = orientation[1]
                val roll = orientation[2]

                rawTiltX = (roll / 0.65f).coerceIn(-1f, 1f)
                rawTiltY = (pitch / 0.65f).coerceIn(-1f, 1f)
            }

            // Low-pass smoothing for a stable "floating photo" feel.
            val smoothing = 0.14f
            tiltX += (rawTiltX - tiltX) * smoothing
            tiltY += (rawTiltY - tiltY) * smoothing
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        private fun startSensors() {
            rotationSensor?.let {
                sensorManager.registerListener(
                    this,
                    it,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }
        }

        private fun stopSensors() {
            sensorManager.unregisterListener(this)
        }

        private fun reloadSettings() {
            sensitivity = prefs.getInt(
                MainActivity.KEY_SENSITIVITY,
                100
            ).toFloat() / 100f

            depth = prefs.getInt(
                MainActivity.KEY_DEPTH,
                100
            ).toFloat()

            blur = prefs.getInt(
                MainActivity.KEY_BLUR,
                30
            ).toFloat()
        }

        private fun loadBitmap() {
            val uriString = prefs.getString(
                MainActivity.KEY_IMAGE_URI,
                null
            )

            val old = bitmap
            bitmap = null

            if (old != null && !old.isRecycled) {
                old.recycle()
            }

            if (uriString.isNullOrBlank()) return

            val uri = Uri.parse(uriString)

            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    bitmap = BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
                bitmap = null
            }
        }

        private fun requestFrame() {
            if (!visible || frameScheduled) return

            frameScheduled = true

            renderHandler.post(frameRunnable)
        }

        private fun drawFrame() {
            val holder = surfaceHolder

            if (!holder.surface.isValid ||
                surfaceWidth <= 0 ||
                surfaceHeight <= 0
            ) {
                return
            }

            var canvas: Canvas? = null

            try {
                canvas = holder.lockCanvas()

                if (canvas == null) return

                render(canvas)
            } catch (_: Exception) {
                // Surface can disappear while the launcher is switching windows.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        private fun render(canvas: Canvas) {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()

            drawDefaultBackground(canvas, w, h)

            val currentBitmap = bitmap

            if (currentBitmap != null &&
                !currentBitmap.isRecycled &&
                currentBitmap.width > 0 &&
                currentBitmap.height > 0
            ) {
                drawBlurredDepthLayer(canvas, currentBitmap, w, h)
                drawSharpImageLayer(canvas, currentBitmap, w, h)
            }

            drawVignette(canvas, w, h)
        }

        private fun drawDefaultBackground(
            canvas: Canvas,
            w: Float,
            h: Float
        ) {
            val x = tiltX * 120f * sensitivity
            val y = tiltY * 160f * sensitivity

            val gradient = LinearGradient(
                w * 0.15f + x,
                h * 0.05f + y,
                w * 0.9f + x,
                h * 1.1f + y,
                Color.rgb(239, 107, 213),
                Color.rgb(249, 89, 142),
                Shader.TileMode.CLAMP
            )

            backgroundPaint.shader = gradient
            canvas.drawRect(0f, 0f, w, h, backgroundPaint)
            backgroundPaint.shader = null

            val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

            circlePaint.color = 0x28FFFFFF
            canvas.drawCircle(
                w * 0.18f + tiltX * 55f,
                h * 0.05f + tiltY * 55f,
                w * 0.48f,
                circlePaint
            )

            circlePaint.color = 0x22000000
            canvas.drawCircle(
                w * 0.78f - tiltX * 75f,
                h * 0.88f - tiltY * 80f,
                w * 0.50f,
                circlePaint
            )
        }

        private fun drawBlurredDepthLayer(
            canvas: Canvas,
            image: Bitmap,
            w: Float,
            h: Float
        ) {
            val matrix = createCoverMatrix(
                image,
                w,
                h,
                1.18f
            )

            val dx = tiltX * depth * 1.15f * sensitivity
            val dy = tiltY * depth * 1.15f * sensitivity

            matrix.postTranslate(dx, dy)

            blurPaint.alpha = 135

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val radius = min(blur, 80f) * 0.18f

                blurPaint.renderEffect =
                    if (radius > 0.1f) {
                        RenderEffect.createBlurEffect(
                            radius,
                            radius,
                            Shader.TileMode.CLAMP
                        )
                    } else {
                        null
                    }
            }

            canvas.drawBitmap(
                image,
                matrix,
                blurPaint
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurPaint.renderEffect = null
            }

            blurPaint.alpha = 255
        }

        private fun drawSharpImageLayer(
            canvas: Canvas,
            image: Bitmap,
            w: Float,
            h: Float
        ) {
            val matrix = createCoverMatrix(
                image,
                w,
                h,
                1.12f
            )

            val dx = tiltX * depth * sensitivity
            val dy = tiltY * depth * sensitivity

            matrix.postTranslate(dx, dy)

            imagePaint.alpha = 255

            canvas.drawBitmap(
                image,
                matrix,
                imagePaint
            )

            // A soft light wash moves in the opposite direction,
            // reinforcing the depth illusion.
            val glowX = w * 0.5f - tiltX * 90f
            val glowY = h * 0.25f - tiltY * 90f

            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    glowX - w * 0.45f,
                    glowY - h * 0.25f,
                    glowX + w * 0.45f,
                    glowY + h * 0.55f,
                    0x38FFFFFF,
                    0x00FFFFFF,
                    Shader.TileMode.CLAMP
                )
            }

            canvas.drawRect(
                0f,
                0f,
                w,
                h,
                glowPaint
            )
        }

        private fun createCoverMatrix(
            image: Bitmap,
            viewWidth: Float,
            viewHeight: Float,
            extraScale: Float
        ): Matrix {
            val imageWidth = image.width.toFloat()
            val imageHeight = image.height.toFloat()

            val scale = max(
                viewWidth / imageWidth,
                viewHeight / imageHeight
            ) * extraScale

            val drawWidth = imageWidth * scale
            val drawHeight = imageHeight * scale

            val left = (viewWidth - drawWidth) / 2f
            val top = (viewHeight - drawHeight) / 2f

            return Matrix().apply {
                postScale(scale, scale)
                postTranslate(left, top)
            }
        }

        private fun drawVignette(
            canvas: Canvas,
            w: Float,
            h: Float
        ) {
            val shader = LinearGradient(
                0f,
                0f,
                0f,
                h,
                0x12000000,
                0x2E000000,
                Shader.TileMode.CLAMP
            )

            vignettePaint.shader = shader
            canvas.drawRect(
                RectF(0f, 0f, w, h),
                vignettePaint
            )
            vignettePaint.shader = null
        }
    }
}
