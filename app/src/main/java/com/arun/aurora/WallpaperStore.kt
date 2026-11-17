package com.arun.aurora

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

// Stores the wallpaper picked from the device (scaled to <= 1920 px wide) inside the app
object WallpaperStore {
    fun file(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")

    fun saveFromPath(ctx: Context, path: String): Boolean = save(ctx) { FileInputStream(path) }
    fun saveFromUri(ctx: Context, uri: Uri): Boolean = save(ctx) { ctx.contentResolver.openInputStream(uri) }

    private fun save(ctx: Context, open: () -> InputStream?): Boolean {
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open()?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (o.outWidth / s > 2000) s *= 2
            val d = BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565 }
            val bmp = open()?.use { BitmapFactory.decodeStream(it, null, d) } ?: return false
            FileOutputStream(file(ctx)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
            true
        } catch (e: Exception) { false }
    }
}
