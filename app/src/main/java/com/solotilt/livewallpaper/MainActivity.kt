package com.solotilt.livewallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

object SoloTiltPrefs {
    const val PREFS = "solo_tilt"

    const val KEY_IMAGE_URI = "image_uri"
    const val KEY_SENSITIVITY = "sensitivity"
    const val KEY_DEPTH = "depth"
    const val KEY_BLUR = "blur"
}

class MainActivity : Activity() {

    companion object {
        private const val PICK_IMAGE = 1001
    }

    private val prefs by lazy {
        getSharedPreferences(
            SoloTiltPrefs.PREFS,
            MODE_PRIVATE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUi()
    }

    private fun buildUi() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(24),
                dp(22),
                dp(24),
                dp(22)
            )

            setBackgroundColor(
                0xFF09090B.toInt()
            )
        }

        val title = TextView(this).apply {
            text = "Solo Tilt"
            textSize = 30f
            setTextColor(
                0xFFFFFFFF.toInt()
            )
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Native Live Wallpaper"
            textSize = 15f
            setTextColor(
                0xFFB8B8C2.toInt()
            )
            gravity = Gravity.CENTER
        }

        root.addView(
            title,
            match()
        )

        root.addView(
            subtitle,
            match()
        )

        val info = TextView(this).apply {
            text =
                "Choose an image, adjust the motion effect, then set Solo Tilt as your Live Wallpaper."

            textSize = 15f

            setTextColor(
                0xFFE5E5EA.toInt()
            )

            setPadding(
                0,
                dp(18),
                0,
                dp(18)
            )
        }

        root.addView(
            info,
            match()
        )

        val choose = Button(this).apply {
            text = "Choose Wallpaper Image"

            setOnClickListener {
                chooseImage()
            }
        }

        root.addView(
            choose,
            match()
        )

        root.addView(
            label("Motion sensitivity")
        )

        root.addView(
            seekBar(
                SoloTiltPrefs.KEY_SENSITIVITY,
                25,
                200,
                100
            )
        )

        root.addView(
            label("Depth / parallax")
        )

        root.addView(
            seekBar(
                SoloTiltPrefs.KEY_DEPTH,
                20,
                200,
                100
            )
        )

        root.addView(
            label("Blur")
        )

        root.addView(
            seekBar(
                SoloTiltPrefs.KEY_BLUR,
                0,
                100,
                30
            )
        )

        val setWallpaper = Button(this).apply {

            text = "SET AS LIVE WALLPAPER"

            setOnClickListener {
                openWallpaperPreview()
            }
        }

        val wallpaperParams =
            match().apply {
                topMargin = dp(22)
            }

        root.addView(
            setWallpaper,
            wallpaperParams
        )

        val help = TextView(this).apply {

            text =
                "After setting the wallpaper, press Home and tilt your phone to move the image."

            textSize = 13f

            setTextColor(
                0xFF9696A3.toInt()
            )

            setPadding(
                0,
                dp(16),
                0,
                0
            )
        }

        root.addView(
            help,
            match()
        )

        setContentView(root)
    }

    private fun chooseImage() {

        val intent =
            Intent(
                Intent.ACTION_OPEN_DOCUMENT
            ).apply {

                addCategory(
                    Intent.CATEGORY_OPENABLE
                )

                type = "image/*"

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }

        startActivityForResult(
            intent,
            PICK_IMAGE
        )
    }

    @Deprecated(
        "Using Activity result API to keep dependencies minimal."
    )
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode != PICK_IMAGE ||
            resultCode != RESULT_OK
        ) {
            return
        }

        val uri =
            data?.data ?: return

        try {

            val flags =
                data.flags and
                    Intent.FLAG_GRANT_READ_URI_PERMISSION

            contentResolver.takePersistableUriPermission(
                uri,
                flags
            )

        } catch (_: SecurityException) {
        }

        prefs.edit()
            .putString(
                SoloTiltPrefs.KEY_IMAGE_URI,
                uri.toString()
            )
            .apply()

        Toast.makeText(
            this,
            "Wallpaper image selected",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun openWallpaperPreview() {

        if (
            !WallpaperManager
                .getInstance(this)
                .isWallpaperSupported
        ) {

            Toast.makeText(
                this,
                "Live wallpapers are not supported on this device.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val component =
            ComponentName(
                this,
                SoloTiltWallpaperService::class.java
            )

        try {

            val intent =
                Intent(
                    WallpaperManager
                        .ACTION_CHANGE_LIVE_WALLPAPER
                ).apply {

                    putExtra(
                        WallpaperManager
                            .EXTRA_LIVE_WALLPAPER_COMPONENT,
                        component
                    )
                }

            startActivity(intent)

        } catch (_: Exception) {

            try {

                startActivity(
                    Intent(
                        WallpaperManager
                            .ACTION_LIVE_WALLPAPER_CHOOSER
                    )
                )

            } catch (_: Exception) {

                startActivity(
                    Intent(
                        Settings.ACTION_DISPLAY_SETTINGS
                    )
                )
            }
        }
    }

    private fun label(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text = text

            textSize = 14f

            setTextColor(
                0xFFD7D7DE.toInt()
            )

            setPadding(
                0,
                dp(14),
                0,
                0
            )
        }
    }

    private fun seekBar(
        key: String,
        min: Int,
        max: Int,
        default: Int
    ): SeekBar {

        val bar =
            SeekBar(this).apply {

                this.max = max - min

                progress =
                    (
                        prefs.getInt(
                            key,
                            default
                        ) - min
                    ).coerceIn(
                        0,
                        this.max
                    )
            }

        bar.setOnSeekBarChangeListener(
            object :
                SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {

                    if (fromUser) {

                        prefs.edit()
                            .putInt(
                                key,
                                progress + min
                            )
                            .apply()
                    }
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) = Unit

                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) = Unit
            }
        )

        return bar
    }

    private fun match():
        LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
            ).toInt()
    }
}
