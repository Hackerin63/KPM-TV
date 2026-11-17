package com.arun.aurora

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File

class ReaderActivity : Activity() {
    private lateinit var book: BookView
    private var path = ""
    private lateinit var hint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        path = intent.getStringExtra("path") ?: ""
        val file = File(path)
        val reading = Prefs.reading(this)
        val paper = if (reading) 0xFFF1E4C8.toInt() else 0xFFFDFBF5.toInt()
        val ink = 0xFF2B2B2B.toInt()

        book = BookView(this)
        book.paper = paper
        book.onError = { msg ->
            Toast.makeText(this, "Cannot open: $msg", Toast.LENGTH_LONG).show()
            finish()
        }
        book.onSpreadChanged = { Prefs.setBookPos(this, path, it) }

        val root = FrameLayout(this)
        root.addView(book, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        hint = TextView(this).apply {
            text = "Left / Right = turn page     Up / Down = text size     Back = close"
            setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
            setPadding(Ui.dp(this@ReaderActivity, 20), Ui.dp(this@ReaderActivity, 8), Ui.dp(this@ReaderActivity, 20), Ui.dp(this@ReaderActivity, 8))
            background = GradientDrawable().apply { cornerRadius = Ui.dp(this@ReaderActivity, 20).toFloat(); setColor(0xAA000000.toInt()) }
        }
        root.addView(hint, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            .apply { topMargin = Ui.dp(this@ReaderActivity, 10) })
        hint.animate().alpha(0f).setStartDelay(5000).setDuration(600).start()
        setContentView(root)

        val start = Prefs.bookPos(this, path)
        Thread {
            try {
                val name = file.name.lowercase()
                val src: PageSource = when {
                    name.endsWith(".pdf") -> PdfSource(file, paper)
                    name.endsWith(".docx") -> TextSource(this, TextSource.docxText(file), paper, ink)
                    name.endsWith(".txt") -> TextSource(this, file.readText(), paper, ink)
                    else -> throw IllegalArgumentException("Unsupported file type (use PDF, DOCX or TXT)")
                }
                runOnUiThread { book.openSource(src, start) }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Cannot open: ${e.message}", Toast.LENGTH_LONG).show(); finish() }
            }
        }.start()
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN) {
            when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT -> { book.turn(1); return true }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { book.turn(-1); return true }
                KeyEvent.KEYCODE_DPAD_UP -> { book.changeTextSize(1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { book.changeTextSize(-1); return true }
            }
        }
        return super.dispatchKeyEvent(e)
    }

    override fun onResume() { super.onResume(); Overlay.apply(this) }
    override fun onDestroy() { book.release(); super.onDestroy() }
}
