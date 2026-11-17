package com.arun.aurora

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.concurrent.Executors

// Pick a wallpaper from photos stored on the TV / USB drive
class ImagePickerActivity : Activity() {
    private val exec = Executors.newFixedThreadPool(2)
    private val paths = ArrayList<String>()
    private lateinit var msg: TextView
    private lateinit var grid: RecyclerView
    private var accent = 0xFF6EA8FF.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accent = ThemeManager.current(this).accent
        val pad = Ui.dp(this, 40)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0F1A.toInt())
            setPadding(pad, Ui.dp(this@ImagePickerActivity, 20), pad, Ui.dp(this@ImagePickerActivity, 12))
        }
        root.addView(TextView(this).apply { text = "Choose a wallpaper"; textSize = 26f; setTextColor(Color.WHITE) })
        msg = TextView(this).apply {
            textSize = 14f; setTextColor(0xFFB8C2DD.toInt())
            setPadding(0, Ui.dp(this@ImagePickerActivity, 4), 0, Ui.dp(this@ImagePickerActivity, 8))
        }
        root.addView(msg)
        grid = RecyclerView(this)
        grid.layoutManager = GridLayoutManager(this, 5)
        grid.adapter = Adapter()
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            msg.text = "Allow storage access to see your photos..."
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 9)
        } else load()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) load()
        else msg.text = "Storage permission denied. Run on PC:  adb shell pm grant com.arun.aurora android.permission.READ_EXTERNAL_STORAGE"
    }

    override fun onResume() { super.onResume(); Overlay.apply(this) }

    @Suppress("DEPRECATION")
    private fun load() {
        msg.text = "Loading photos..."
        exec.execute {
            val found = ArrayList<String>()
            try {
                contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media.DATA), null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { c ->
                    while (c.moveToNext() && found.size < 250) {
                        val p = c.getString(0)
                        if (p != null && File(p).exists()) found.add(p)
                    }
                }
            } catch (_: Exception) {}
            runOnUiThread {
                paths.clear(); paths.addAll(found)
                grid.adapter?.notifyDataSetChanged()
                msg.text = if (found.isEmpty()) "No photos found. Copy JPG/PNG files to Pictures, Download or a USB drive, or use 'From file manager' in Settings."
                else "Pick a photo (${found.size} found)  -  OK to select"
                grid.post { grid.getChildAt(0)?.requestFocus() }
            }
        }
    }

    private fun choose(path: String) {
        msg.text = "Setting wallpaper..."
        exec.execute {
            val ok = WallpaperStore.saveFromPath(this, path)
            runOnUiThread {
                if (ok) {
                    ThemeManager.set(this, Theme.PHOTO)
                    Toast.makeText(this, "Wallpaper set", Toast.LENGTH_SHORT).show()
                    finish()
                } else msg.text = "Could not use that image."
            }
        }
    }

    override fun onDestroy() { exec.shutdownNow(); super.onDestroy() }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(val iv: ImageView) : RecyclerView.ViewHolder(iv)

        override fun onCreateViewHolder(parent: ViewGroup, t: Int): VH {
            val w = (resources.displayMetrics.widthPixels - Ui.dp(this@ImagePickerActivity, 80)) / 5 - Ui.dp(this@ImagePickerActivity, 8)
            val iv = ImageView(this@ImagePickerActivity)
            iv.layoutParams = ViewGroup.MarginLayoutParams(w, (w * 0.58f).toInt()).apply {
                val m = Ui.dp(this@ImagePickerActivity, 4); setMargins(m, m, m, m)
            }
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            val pd = Ui.dp(this@ImagePickerActivity, 4)
            iv.setPadding(pd, pd, pd, pd)
            iv.isFocusable = true
            iv.isClickable = true
            return VH(iv)
        }

        override fun getItemCount() = paths.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val path = paths[pos]
            h.iv.tag = path
            h.iv.setImageDrawable(null)
            h.iv.background = Ui.tileBg(this@ImagePickerActivity, accent, false)
            h.iv.setOnClickListener { choose(path) }
            h.iv.setOnFocusChangeListener { v, f ->
                v.background = Ui.tileBg(this@ImagePickerActivity, accent, f)
                val s = if (f) 1.06f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(140).start()
            }
            exec.execute {
                try {
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, o)
                    var s = 1
                    while (o.outWidth / s > 480) s *= 2
                    val d = BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565 }
                    val bmp = BitmapFactory.decodeFile(path, d)
                    if (bmp != null) runOnUiThread { if (h.iv.tag == path) h.iv.setImageBitmap(bmp) }
                } catch (_: Throwable) {}
            }
        }
    }
}
