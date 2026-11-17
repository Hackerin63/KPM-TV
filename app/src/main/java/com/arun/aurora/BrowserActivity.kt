package com.arun.aurora

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class BrowserActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var urlBox: EditText
    private lateinit var bmBtn: TextView
    private var acc = 0xFF6EA8FF.toInt()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acc = ThemeManager.current(this).accent
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0F1A.toInt())
        }
        fun row(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@BrowserActivity, 16), Ui.dp(this@BrowserActivity, 4),
                Ui.dp(this@BrowserActivity, 16), Ui.dp(this@BrowserActivity, 4))
        }
        fun btn(label: String, onClick: () -> Unit) = Ui.button(this, label, acc, onClick).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = Ui.dp(this@BrowserActivity, 8) }
        }

        urlBox = EditText(this).apply {
            hint = "Search Google or type a website"
            setHintTextColor(0x88FFFFFF.toInt())
            setTextColor(Color.WHITE)
            textSize = 15f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(Ui.dp(this@BrowserActivity, 18), Ui.dp(this@BrowserActivity, 10),
                Ui.dp(this@BrowserActivity, 18), Ui.dp(this@BrowserActivity, 10))
            background = GradientDrawable().apply {
                cornerRadius = Ui.dp(this@BrowserActivity, 22).toFloat()
                setColor(0x33FFFFFF)
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginEnd = Ui.dp(this@BrowserActivity, 8) }
            setOnEditorActionListener { _, actionId, ev ->
                if (actionId == EditorInfo.IME_ACTION_GO || (ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    go(text.toString()); true
                } else false
            }
        }

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.builtInZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, u: String) {
                    this@BrowserActivity.urlBox.setText(u)
                    BookmarkStore.addHistory(this@BrowserActivity, view.title ?: u, u)
                    refreshBookmarkButton()
                }
            }
        }

        val r1 = row()
        r1.addView(btn("Back") { if (web.canGoBack()) web.goBack() })
        r1.addView(btn("Next") { if (web.canGoForward()) web.goForward() })
        r1.addView(btn("Reload") { web.reload() })
        r1.addView(btn("Google") { web.loadUrl("https://www.google.com") })
        bmBtn = btn("Bookmark") { toggleBookmark() }
        r1.addView(bmBtn)
        r1.addView(btn("Bookmarks") { showBookmarks() })
        r1.addView(btn("History") { showHistory() })

        val r2 = row()
        r2.addView(urlBox)
        r2.addView(btn("Go") { go(urlBox.text.toString()) })

        root.addView(r1)
        root.addView(r2)
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        val start = intent.getStringExtra("url")
        web.loadUrl(if (start.isNullOrBlank()) "https://www.google.com" else start)
        web.requestFocus()
    }

    override fun onResume() { super.onResume(); Overlay.apply(this) }

    private fun go(q: String) {
        val s = q.trim()
        if (s.isEmpty()) return
        val target = when {
            s.startsWith("http://") || s.startsWith("https://") -> s
            s.contains(" ") || !s.contains(".") -> "https://www.google.com/search?q=" + Uri.encode(s)
            else -> "https://$s"
        }
        web.loadUrl(target)
        web.requestFocus()
    }

    private fun refreshBookmarkButton() {
        val u = web.url ?: return
        bmBtn.text = if (BookmarkStore.isBookmarked(this, u)) "Saved" else "Bookmark"
    }

    // Adds / removes the page; saved pages also appear as tiles on the KPM TV home screen
    private fun toggleBookmark() {
        val u = web.url ?: return
        val added = BookmarkStore.toggle(this, web.title ?: u, u)
        Toast.makeText(this, if (added) "Added to Home screen" else "Removed from Home screen", Toast.LENGTH_SHORT).show()
        refreshBookmarkButton()
    }

    private fun showBookmarks() {
        val l = BookmarkStore.bookmarks(this)
        if (l.isEmpty()) { Toast.makeText(this, "No bookmarks yet", Toast.LENGTH_SHORT).show(); return }
        RoundDialog.show(this, "Bookmarks", l.map { it.title.take(48) }, acc) { web.loadUrl(l[it].url) }
    }

    private fun showHistory() {
        val l = BookmarkStore.history(this)
        if (l.isEmpty()) { Toast.makeText(this, "History is empty", Toast.LENGTH_SHORT).show(); return }
        val shown = l.take(30)
        RoundDialog.show(this, "History", listOf("Clear history") + shown.map { it.title.take(48) }, acc) {
            if (it == 0) { BookmarkStore.clearHistory(this); Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show() }
            else web.loadUrl(shown[it - 1].url)
        }
    }

    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
