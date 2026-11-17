package com.arun.aurora

import android.content.Context

enum class Theme(val key: String, val title: String, val accent: Int) {
    AURORA("aurora", "Night Sky", 0xFF8AA4FF.toInt()),
    SNOW("snow", "Snowfall", 0xFF9AD4FF.toInt()),
    GARDEN("garden", "Flower Garden", 0xFFFF8FB1.toInt()),
    WATER("water", "Waterfall", 0xFF5FD0E8.toInt()),
    F1("f1", "F1 Racing", 0xFFFF3B3B.toInt()),
    PHOTO("photo", "My Photo", 0xFFFFD166.toInt())
}

object ThemeManager {
    fun current(ctx: Context): Theme {
        val k = ctx.getSharedPreferences("aurora", Context.MODE_PRIVATE).getString("theme", "snow")
        return Theme.values().firstOrNull { it.key == k } ?: Theme.SNOW
    }

    fun set(ctx: Context, t: Theme) {
        ctx.getSharedPreferences("aurora", Context.MODE_PRIVATE).edit().putString("theme", t.key).apply()
    }
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("aurora", Context.MODE_PRIVATE)
    fun animated(c: Context) = sp(c).getBoolean("anim", true)
    fun setAnimated(c: Context, v: Boolean) = sp(c).edit().putBoolean("anim", v).apply()
    fun sounds(c: Context) = sp(c).getBoolean("sounds", false)
    fun setSounds(c: Context, v: Boolean) = sp(c).edit().putBoolean("sounds", v).apply()
    fun intro(c: Context) = sp(c).getBoolean("intro", true)
    fun setIntro(c: Context, v: Boolean) = sp(c).edit().putBoolean("intro", v).apply()
    fun themeSound(c: Context) = sp(c).getBoolean("themesound", true)
    fun setThemeSound(c: Context, v: Boolean) = sp(c).edit().putBoolean("themesound", v).apply()
    fun reading(c: Context) = sp(c).getBoolean("reading", false)
    fun setReading(c: Context, v: Boolean) = sp(c).edit().putBoolean("reading", v).apply()
    fun brightness(c: Context) = sp(c).getInt("brightness", 100)
    fun setBrightness(c: Context, v: Int) = sp(c).edit().putInt("brightness", v.coerceIn(30, 100)).apply()
    fun gameLevel(c: Context, game: String) = sp(c).getInt("lv_$game", 3)
    fun setGameLevel(c: Context, game: String, v: Int) = sp(c).edit().putInt("lv_$game", v).apply()
    fun bookPos(c: Context, path: String) = sp(c).getInt("book_" + path.hashCode(), 0)
    fun setBookPos(c: Context, path: String, v: Int) = sp(c).edit().putInt("book_" + path.hashCode(), v).apply()
}
