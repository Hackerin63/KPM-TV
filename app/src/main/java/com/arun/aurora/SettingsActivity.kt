package com.arun.aurora

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private lateinit var wp: LiveWallpaperView
    private var cur = Theme.SNOW
    private val themeBtns = ArrayList<Pair<Theme, TextView>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cur = ThemeManager.current(this)
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        // Animated wallpaper shows through the translucent cards
        val root = FrameLayout(this)
        wp = LiveWallpaperView(this)
        wp.animated = Prefs.animated(this)
        wp.setTheme(cur)
        root.addView(wp, FrameLayout.LayoutParams(match, match))
        root.addView(View(this).apply { setBackgroundColor(0x22000000) }, FrameLayout.LayoutParams(match, match))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@SettingsActivity, 48), Ui.dp(this@SettingsActivity, 24),
                Ui.dp(this@SettingsActivity, 48), Ui.dp(this@SettingsActivity, 24))
        }

        fun title(s: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            setPadding(Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 4), 0, Ui.dp(this@SettingsActivity, 8))
        }

        fun card(heading: String): LinearLayout {
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val pad = Ui.dp(this@SettingsActivity, 16)
                setPadding(pad, pad, pad, pad)
                background = Ui.cardBg(this@SettingsActivity)
            }
            c.addView(title(heading, 15f, 0xFFB8C2DD.toInt()))
            col.addView(c, LinearLayout.LayoutParams(match, wrap).apply { bottomMargin = Ui.dp(this@SettingsActivity, 14) })
            return c
        }

        fun rowOf(parent: LinearLayout): LinearLayout {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            parent.addView(r, LinearLayout.LayoutParams(match, wrap))
            return r
        }

        fun cell(v: View) {
            v.layoutParams = LinearLayout.LayoutParams(0, wrap, 1f)
                .apply { marginEnd = Ui.dp(this@SettingsActivity, 10); bottomMargin = Ui.dp(this@SettingsActivity, 10) }
        }

        col.addView(title("KPM TV  -  Launcher Settings", 28f))

        // 1. Wallpaper themes
        val themeCard = card("Wallpaper theme")
        var row = rowOf(themeCard)
        Theme.values().forEachIndexed { i, th ->
            if (i > 0 && i % 3 == 0) row = rowOf(themeCard)
            val b = Ui.button(this, th.title, th.accent) { chooseTheme(th) }
            cell(b); themeBtns.add(th to b); row.addView(b)
        }
        relabel()

        // 2. Wallpaper from device
        val picCard = card("Wallpaper from this TV / USB drive")
        val picRow = rowOf(picCard)
        val b1 = Ui.button(this, "Choose from device", cur.accent) { startActivity(Intent(this, ImagePickerActivity::class.java)) }
        val b2 = Ui.button(this, "From file manager", cur.accent) { pickWithFileManager() }
        cell(b1); cell(b2); picRow.addView(b1); picRow.addView(b2)
        picCard.addView(title("Picks any photo (JPG / PNG) and shows it with a slow zoom as My Photo.", 12f, 0xFF8A94B0.toInt()))

        // 3. Launcher options
        val optCard = card("Launcher")
        var optRow = rowOf(optCard)
        var n = 0
        fun toggle(name: String, get: () -> Boolean, set: (Boolean) -> Unit, after: () -> Unit = {}) {
            if (n > 0 && n % 3 == 0) optRow = rowOf(optCard)
            n++
            lateinit var b: TextView
            fun lbl() { b.text = "$name: " + if (get()) "ON" else "OFF" }
            b = Ui.button(this, "", cur.accent) { set(!get()); lbl(); after() }
            lbl(); cell(b); optRow.addView(b)
        }
        toggle("Wallpaper animation", { Prefs.animated(this) }, { Prefs.setAnimated(this, it) }) { wp.setAnimatedFlag(Prefs.animated(this)) }
        toggle("Menu sounds", { Prefs.sounds(this) }, { Prefs.setSounds(this, it) })
        toggle("KPM TV intro", { Prefs.intro(this) }, { Prefs.setIntro(this, it) })
        toggle("Waterfall sound", { Prefs.themeSound(this) }, { Prefs.setThemeSound(this, it) }) { refreshSound() }
        toggle("Reading mode", { Prefs.reading(this) }, { Prefs.setReading(this, it) }) { Overlay.apply(this) }

        // 4. Brightness
        val brCard = card("Brightness")
        val brRow = rowOf(brCard)
        lateinit var brLabel: TextView
        fun brText() { brLabel.text = "Brightness: ${Prefs.brightness(this)}%" }
        val dim = Ui.button(this, "Dimmer", cur.accent) { Prefs.setBrightness(this, Prefs.brightness(this) - 10); Overlay.apply(this); brText() }
        brLabel = Ui.button(this, "", cur.accent) {}
        val bright = Ui.button(this, "Brighter", cur.accent) { Prefs.setBrightness(this, Prefs.brightness(this) + 10); Overlay.apply(this); brText() }
        brText()
        cell(dim); cell(brLabel); cell(bright); brRow.addView(dim); brRow.addView(brLabel); brRow.addView(bright)
        brCard.addView(title("Dims the KPM TV screens. The TV backlight itself is in the TV's Picture settings.", 12f, 0xFF8A94B0.toInt()))
        val pic = Ui.button(this, "TV picture / display settings", cur.accent) { Ui.openSettings(this, Settings.ACTION_DISPLAY_SETTINGS) }
        pic.layoutParams = LinearLayout.LayoutParams(wrap, wrap)
        brCard.addView(pic)

        // 5. System settings shortcuts
        val sysCard = card("System settings (opens the original TV Settings)")
        val items = listOf(
            "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
            "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
            "Display" to Settings.ACTION_DISPLAY_SETTINGS,
            "Sound" to Settings.ACTION_SOUND_SETTINGS,
            "Storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
            "Apps" to Settings.ACTION_APPLICATION_SETTINGS,
            "Developer" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
            "All Settings" to Settings.ACTION_SETTINGS
        )
        var srow = rowOf(sysCard)
        items.forEachIndexed { i, (label, action) ->
            if (i > 0 && i % 4 == 0) srow = rowOf(sysCard)
            val b = Ui.button(this, label, cur.accent) { Ui.openSettings(this, action) }
            cell(b); srow.addView(b)
        }

        val scroll = ScrollView(this)
        scroll.addView(col)
        root.addView(scroll, FrameLayout.LayoutParams(match, match))
        setContentView(root)
        themeBtns.firstOrNull()?.second?.requestFocus()
    }

    private fun relabel() { themeBtns.forEach { (t, b) -> b.text = (if (t == cur) "● " else "") + t.title } }

    private fun chooseTheme(th: Theme) {
        if (th == Theme.PHOTO && !WallpaperStore.file(this).exists()) {
            Toast.makeText(this, "Choose a photo first", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ImagePickerActivity::class.java))
            return
        }
        cur = th
        ThemeManager.set(this, th)
        wp.setTheme(th)
        relabel()
        refreshSound()
    }

    private fun refreshSound() {
        if (cur == Theme.WATER && Prefs.themeSound(this)) WaterSound.start() else WaterSound.stop()
    }

    private fun pickWithFileManager() {
        try {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 21)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No file manager picker found - use 'Choose from device'", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data
        if (req == 21 && res == RESULT_OK && uri != null) {
            Thread {
                val ok = WallpaperStore.saveFromUri(this, uri)
                runOnUiThread {
                    if (ok) { chooseTheme(Theme.PHOTO); Toast.makeText(this, "Wallpaper set", Toast.LENGTH_SHORT).show() }
                    else Toast.makeText(this, "Could not use that image", Toast.LENGTH_LONG).show()
                }
            }.start()
        }
    }

    override fun onResume() {
        super.onResume()
        Overlay.apply(this)
        val t = ThemeManager.current(this)
        if (t != cur) { cur = t; wp.setTheme(t); relabel() }
        refreshSound()
    }

    override fun onPause() { WaterSound.stop(); super.onPause() }
}
