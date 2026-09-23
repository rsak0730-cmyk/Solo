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
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.max

class SoloTiltWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return SoloTiltEngine()
    }

    private inner class SoloTiltEngine :
        Engine(),
        SensorEventListener {

        private val prefs =
            getSharedPreferences(
                SoloTiltPrefs.PREFS,
                Context.MODE_PRIVATE
            )

        private val sensorManager =
            getSystemService(
                Context.SENSOR_SERVICE
            ) as SensorManager

        private val rotationSensor =
            sensorManager.getDefaultSensor(
                Sensor.TYPE_GAME_ROTATION_VECTOR
            )
                ?: sensorManager.getDefaultSensor(
                    Sensor.TYPE_ROTATION_VECTOR
                )

        private val rotationMatrix =
            FloatArray(9)

        private val orientation =
            FloatArray(3)

        private var bitmap: Bitmap? = null

        private var surfaceWidth = 0
        private var surfaceHeight = 0

        private var visible = false

        private var rawTiltX = 0f
        private var rawTiltY = 0f

        private var tiltX = 0f
        private var tiltY = 0f

        private var sensitivity = 1f
        private var depth = 100f
        private var blur = 30f

        private val backgroundPaint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        private val imagePaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG
            )

        private val depthPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG
            )

        private val vignettePaint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        private val prefsListener =
            android.content.SharedPreferences
                .OnSharedPreferenceChangeListener {
                    _,
                    key ->

                    reloadSettings()

                    if (
                        key ==
                        SoloTiltPrefs.KEY_IMAGE_URI
                    ) {
                        loadBitmap()
                    }
                }

        init {

            prefs.registerOnSharedPreferenceChangeListener(
                prefsListener
            )

            reloadSettings()
        }

        override fun onCreate(
            surfaceHolder: SurfaceHolder
        ) {

            super.onCreate(
                surfaceHolder
            )

            surfaceHolder.setFormat(
                android.graphics.PixelFormat.RGBA_8888
            )

            setOffsetNotificationsEnabled(
                false
            )

            loadBitmap()
        }

        override fun onSurfaceCreated(
            holder: SurfaceHolder
        ) {

            super.onSurfaceCreated(
                holder
            )

            surfaceWidth =
                holder.surfaceFrame.width()

            surfaceHeight =
                holder.surfaceFrame.height()

            drawFrame()
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {

            super.onSurfaceChanged(
                holder,
                format,
                width,
                height
            )

            surfaceWidth = width
            surfaceHeight = height

            drawFrame()
        }

        override fun onVisibilityChanged(
            isVisible: Boolean
        ) {

            visible = isVisible

            if (visible) {
                startSensors()
                drawFrame()
            } else {
                stopSensors()
            }
        }

        override fun onSurfaceDestroyed(
            holder: SurfaceHolder
        ) {

            super.onSurfaceDestroyed(
                holder
            )

            surfaceWidth = 0
            surfaceHeight = 0
        }

        override fun onDestroy() {

            visible = false

            stopSensors()

            prefs.unregisterOnSharedPreferenceChangeListener(
                prefsListener
            )

            bitmap?.recycle()

            bitmap = null

            super.onDestroy()
        }

        override fun onSensorChanged(
            event: SensorEvent
        ) {

            if (!visible) {
                return
            }

            if (
                event.sensor.type ==
                Sensor.TYPE_GAME_ROTATION_VECTOR ||

                event.sensor.type ==
                Sensor.TYPE_ROTATION_VECTOR
            ) {

                SensorManager
                    .getRotationMatrixFromVector(
                        rotationMatrix,
                        event.values
                    )

                SensorManager
                    .getOrientation(
                        rotationMatrix,
                        orientation
                    )

                val pitch =
                    orientation[1]

                val roll =
                    orientation[2]

                rawTiltX =
                    (roll / 0.65f)
                        .coerceIn(
                            -1f,
                            1f
                        )

                rawTiltY =
                    (pitch / 0.65f)
                        .coerceIn(
                            -1f,
                            1f
                        )

                val smoothing =
                    0.14f

                tiltX +=
                    (
                        rawTiltX -
                            tiltX
                        ) * smoothing

                tiltY +=
                    (
                        rawTiltY -
                            tiltY
                        ) * smoothing

                drawFrame()
            }
        }

        override fun onAccuracyChanged(
            sensor: Sensor?,
            accuracy: Int
        ) {
        }

        private fun startSensors() {

            rotationSensor?.let { sensor ->

                sensorManager.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }
        }

        private fun stopSensors() {

            sensorManager.unregisterListener(
                this
            )
        }

        private fun reloadSettings() {

            sensitivity =
                prefs.getInt(
                    SoloTiltPrefs.KEY_SENSITIVITY,
                    100
                ).toFloat() / 100f

            depth =
                prefs.getInt(
                    SoloTiltPrefs.KEY_DEPTH,
                    100
                ).toFloat()

            blur =
                prefs.getInt(
                    SoloTiltPrefs.KEY_BLUR,
                    30
                ).toFloat()
        }

        private fun loadBitmap() {

            val uriString =
                prefs.getString(
                    SoloTiltPrefs.KEY_IMAGE_URI,
                    null
                )

            bitmap?.let { old ->

                if (!old.isRecycled) {
                    old.recycle()
                }
            }

            bitmap = null

            if (uriString.isNullOrBlank()) {
                return
            }

            try {

                val uri =
                    Uri.parse(uriString)

                contentResolver
                    .openInputStream(uri)
                    ?.use { stream ->

                        bitmap =
                            BitmapFactory
                                .decodeStream(
                                    stream
                                )
                    }

            } catch (_: Exception) {

                bitmap = null
            }
        }

        private fun drawFrame() {

            val holder =
                surfaceHolder

            if (
                !holder.surface.isValid ||
                surfaceWidth <= 0 ||
                surfaceHeight <= 0
            ) {
                return
            }

            var canvas: Canvas? = null

            try {

                canvas =
                    holder.lockCanvas()

                if (canvas != null) {
                    render(canvas)
                }

            } catch (_: Exception) {

            } finally {

                canvas?.let {

                    try {
                        holder.unlockCanvasAndPost(
                            it
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        }

        private fun render(
            canvas: Canvas
        ) {

            val width =
                canvas.width.toFloat()

            val height =
                canvas.height.toFloat()

            drawDefaultBackground(
                canvas,
                width,
                height
            )

            val currentBitmap =
                bitmap

            if (
                currentBitmap != null &&
                !currentBitmap.isRecycled
            ) {

                drawDepthLayer(
                    canvas,
                    currentBitmap,
                    width,
                    height
                )

                drawMainImage(
                    canvas,
                    currentBitmap,
                    width,
                    height
                )
            }

            drawVignette(
                canvas,
                width,
                height
            )
        }

        private fun drawDefaultBackground(
            canvas: Canvas,
            width: Float,
            height: Float
        ) {

            val moveX =
                tiltX *
                    120f *
                    sensitivity

            val moveY =
                tiltY *
                    160f *
                    sensitivity

            val gradient =
                LinearGradient(
                    width * 0.15f + moveX,
                    height * 0.05f + moveY,
                    width * 0.9f + moveX,
                    height * 1.1f + moveY,
                    Color.rgb(
                        239,
                        107,
                        213
                    ),
                    Color.rgb(
                        249,
                        89,
                        142
                    ),
                    Shader.TileMode.CLAMP
                )

            backgroundPaint.shader =
                gradient

            canvas.drawRect(
                0f,
                0f,
                width,
                height,
                backgroundPaint
            )

            backgroundPaint.shader = null

            val light =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                )

            light.color =
                0x35FFFFFF

            canvas.drawCircle(
                width * 0.18f +
                    tiltX * 55f,
                height * 0.05f +
                    tiltY * 55f,
                width * 0.48f,
                light
            )

            light.color =
                0x26000000

            canvas.drawCircle(
                width * 0.78f -
                    tiltX * 75f,
                height * 0.88f -
                    tiltY * 80f,
                width * 0.50f,
                light
            )
        }

        private fun drawDepthLayer(
            canvas: Canvas,
            image: Bitmap,
            width: Float,
            height: Float
        ) {

            val matrix =
                createCoverMatrix(
                    image,
                    width,
                    height,
                    1.18f
                )

            matrix.postTranslate(
                tiltX *
                    depth *
                    1.15f *
                    sensitivity,

                tiltY *
                    depth *
                    1.15f *
                    sensitivity
            )

            depthPaint.alpha =
                (
                    80 +
                        blur * 1.5f
                    ).toInt()
                        .coerceIn(
                            70,
                            200
                        )

            canvas.drawBitmap(
                image,
                matrix,
                depthPaint
            )

            depthPaint.alpha = 255
        }

        private fun drawMainImage(
            canvas: Canvas,
            image: Bitmap,
            width: Float,
            height: Float
        ) {

            val matrix =
                createCoverMatrix(
                    image,
                    width,
                    height,
                    1.12f
                )

            matrix.postTranslate(
                tiltX *
                    depth *
                    sensitivity,

                tiltY *
                    depth *
                    sensitivity
            )

            canvas.drawBitmap(
                image,
                matrix,
                imagePaint
            )

            val glowX =
                width * 0.5f -
                    tiltX * 90f

            val glowY =
                height * 0.25f -
                    tiltY * 90f

            val glowPaint =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                )

            glowPaint.shader =
                LinearGradient(
                    glowX -
                        width * 0.45f,
                    glowY -
                        height * 0.25f,
                    glowX +
                        width * 0.45f,
                    glowY +
                        height * 0.55f,
                    0x38FFFFFF,
                    0x00FFFFFF,
                    Shader.TileMode.CLAMP
                )

            canvas.drawRect(
                0f,
                0f,
                width,
                height,
                glowPaint
            )
        }

        private fun createCoverMatrix(
            image: Bitmap,
            viewWidth: Float,
            viewHeight: Float,
            extraScale: Float
        ): Matrix {

            val imageWidth =
                image.width.toFloat()

            val imageHeight =
                image.height.toFloat()

            val scale =
                max(
                    viewWidth / imageWidth,
                    viewHeight / imageHeight
                ) * extraScale

            val drawWidth =
                imageWidth * scale

            val drawHeight =
                imageHeight * scale

            val left =
                (
                    viewWidth -
                        drawWidth
                    ) / 2f

            val top =
                (
                    viewHeight -
                        drawHeight
                    ) / 2f

            return Matrix().apply {

                postScale(
                    scale,
                    scale
                )

                postTranslate(
                    left,
                    top
                )
            }
        }

        private fun drawVignette(
            canvas: Canvas,
            width: Float,
            height: Float
        ) {

            val shader =
                LinearGradient(
                    0f,
                    0f,
                    0f,
                    height,
                    0x12000000,
                    0x30000000,
                    Shader.TileMode.CLAMP
                )

            vignettePaint.shader =
                shader

            canvas.drawRect(
                RectF(
                    0f,
                    0f,
                    width,
                    height
                ),
                vignettePaint
            )

            vignettePaint.shader = null
        }
    }
}
