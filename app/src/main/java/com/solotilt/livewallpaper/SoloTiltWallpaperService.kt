package com.solotilt.livewallpaper

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.max
import kotlin.math.min

class SoloTiltWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return SoloTiltEngine()
    }

    private inner class SoloTiltEngine : Engine(), SensorEventListener {

        private val sensorManager =
            getSystemService(Context.SENSOR_SERVICE) as SensorManager

        private val rotationSensor =
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        private var surfaceWidth = 0
        private var surfaceHeight = 0

        private var tiltX = 0f
        private var tiltY = 0f

        private var visible = false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)

            // Make sure the wallpaper gets an initial frame.
            surfaceHolder.setFormat(android.graphics.PixelFormat.RGBA_8888)

            drawWallpaper()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible

            if (isVisible) {
                registerSensor()
                drawWallpaper()
            } else {
                unregisterSensor()
            }
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

            // IMPORTANT:
            // Draw immediately when Android gives us a valid surface.
            drawWallpaper()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)

            drawWallpaper()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            unregisterSensor()
            super.onDestroy()
        }

        private fun registerSensor() {
            rotationSensor?.let {
                sensorManager.registerListener(
                    this,
                    it,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }
        }

        private fun unregisterSensor() {
            sensorManager.unregisterListener(this)
        }

        override fun onSensorChanged(event: SensorEvent) {

            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) {
                return
            }

            val rotationMatrix = FloatArray(9)

            SensorManager.getRotationMatrixFromVector(
                rotationMatrix,
                event.values
            )

            val orientation = FloatArray(3)

            SensorManager.getOrientation(
                rotationMatrix,
                orientation
            )

            // Convert radians to a smooth normalized movement.
            val newX = orientation[2] * 18f
            val newY = orientation[1] * 18f

            tiltX = tiltX * 0.85f + newX * 0.15f
            tiltY = tiltY * 0.85f + newY * 0.15f

            if (visible) {
                drawWallpaper()
            }
        }

        override fun onAccuracyChanged(
            sensor: Sensor?,
            accuracy: Int
        ) {
        }

        private fun drawWallpaper() {

            val holder = surfaceHolder

            if (!holder.surface.isValid) {
                return
            }

            if (surfaceWidth <= 0 || surfaceHeight <= 0) {
                return
            }

            var canvas: Canvas? = null

            try {
                canvas = holder.lockCanvas()

                if (canvas == null) {
                    return
                }

                drawBackground(canvas)

            } finally {

                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        }

        private fun drawBackground(canvas: Canvas) {

            val width = canvas.width.toFloat()
            val height = canvas.height.toFloat()

            /*
             * Base Solo Tilt gradient.
             */
            paint.shader = LinearGradient(
                0f,
                0f,
                width,
                height,
                intArrayOf(
                    0xFFFF8BEA.toInt(),
                    0xFFFF63A5.toInt(),
                    0xFFEF5B92.toInt()
                ),
                null,
                Shader.TileMode.CLAMP
            )

            canvas.drawRect(
                0f,
                0f,
                width,
                height,
                paint
            )

            paint.shader = null

            /*
             * Parallax movement.
             */
            val movementX = tiltX * width / 100f
            val movementY = tiltY * height / 100f

            /*
             * Large upper circle.
             */
            paint.color = 0x22FFFFFF

            val upperRadius = width * 0.58f

            canvas.drawCircle(
                width * 0.22f + movementX * 0.35f,
                -height * 0.02f + movementY * 0.35f,
                upperRadius,
                paint
            )

            /*
             * Large lower circle.
             */
            paint.color = 0x332C1745

            val lowerRadius = width * 0.63f

            canvas.drawCircle(
                width * 0.73f + movementX * 0.55f,
                height * 0.93f + movementY * 0.55f,
                lowerRadius,
                paint
            )

            /*
             * Additional depth layer.
             */
            paint.color = 0x18FFFFFF

            canvas.drawCircle(
                width * 0.82f + movementX,
                height * 0.22f + movementY,
                width * 0.22f,
                paint
            )

            /*
             * SOLO TILT text.
             */
            paint.color = 0xDDFFFFFF.toInt()
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = width * 0.035f
            paint.typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
            paint.letterSpacing = 0.35f

            canvas.drawText(
                "SOLO TILT",
                width / 2f + movementX * 0.8f,
                height * 0.78f + movementY * 0.8f,
                paint
            )

            paint.letterSpacing = 0f
            paint.shader = null
        }
    }
}
