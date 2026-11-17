package com.arun.aurora

import android.content.Context

data class WebEntry(val title: String, val url: String)

object BookmarkStore {
    private fun sp(c: Context) = c.getSharedPreferences("aurora_web", Context.MODE_PRIVATE)

    private fun decode(s: String): List<WebEntry> = s.split("\n").filter { it.contains("\t") }.map {
        val i = it.indexOf('\t'); WebEntry(it.substring(0, i), it.substring(i + 1))
    }

    private fun encode(l: List<WebEntry>) = l.joinToString("\n") { it.title.replace('\t', ' ').replace('\n', ' ') + "\t" + it.url }

    fun bookmarks(c: Context): List<WebEntry> = decode(sp(c).getString("bm", "") ?: "")
    fun history(c: Context): List<WebEntry> = decode(sp(c).getString("hist", "") ?: "")
    fun isBookmarked(c: Context, url: String) = bookmarks(c).any { it.url == url }

    // returns true when added, false when removed
    fun toggle(c: Context, title: String, url: String): Boolean {
        val l = bookmarks(c).toMutableList()
        val had = l.removeAll { it.url == url }
        if (!had) l.add(WebEntry(title.ifBlank { url }, url))
        sp(c).edit().putString("bm", encode(l)).apply()
        return !had
    }

    fun remove(c: Context, url: String) {
        sp(c).edit().putString("bm", encode(bookmarks(c).filter { it.url != url })).apply()
    }

    fun addHistory(c: Context, title: String, url: String) {
        if (url.startsWith("about:")) return
        val l = history(c).toMutableList()
        l.removeAll { it.url == url }
        l.add(0, WebEntry(title.ifBlank { url }, url))
        sp(c).edit().putString("hist", encode(l.take(100))).apply()
    }

    fun clearHistory(c: Context) { sp(c).edit().remove("hist").apply() }
}
