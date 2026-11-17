package com.arun.aurora

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.min

interface PageSource {
    fun prepare(w: Int, h: Int)
    fun pageCount(): Int
    fun render(i: Int, w: Int, h: Int): Bitmap
    fun close() {}
    val resizable: Boolean get() = false
    fun changeTextSize(delta: Int) {}
}

class PdfSource(private val file: File, private val paper: Int) : PageSource {
    private var pfd: ParcelFileDescriptor? = null
    private var r: PdfRenderer? = null
    private var count = 0

    @Synchronized
    override fun prepare(w: Int, h: Int) {
        if (r == null) {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd = fd
            val rr = PdfRenderer(fd)
            r = rr
            count = rr.pageCount
        }
    }

    override fun pageCount() = count

    @Synchronized
    override fun render(i: Int, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(paper)
        val rr = r ?: return bmp
        val page = rr.openPage(i)
        try {
            val s = min(w / page.width.toFloat(), h / page.height.toFloat())
            val m = Matrix()
            m.postScale(s, s)
            m.postTranslate((w - page.width * s) / 2f, (h - page.height * s) / 2f)
            page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        } finally { page.close() }
        return bmp
    }

    @Synchronized
    override fun close() { try { r?.close(); pfd?.close() } catch (_: Exception) {} }
}

// Word (.docx) and plain text: the text is paginated into book pages
class TextSource(private val ctx: Context, private val text: String, private val paper: Int, private val ink: Int) : PageSource {
    private var layout: StaticLayout? = null
    private val starts = ArrayList<Int>()
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var sizeSp = 17f
    private var margin = 0
    private var availW = 0

    override val resizable: Boolean get() = true

    @Synchronized
    override fun changeTextSize(delta: Int) { sizeSp = (sizeSp + delta * 1.5f).coerceIn(12f, 32f) }

    @Synchronized
    override fun prepare(w: Int, h: Int) {
        val d = ctx.resources.displayMetrics
        tp.textSize = sizeSp * d.density
        tp.typeface = Typeface.SERIF
        tp.color = ink
        margin = (w * 0.09f).toInt()
        availW = w - 2 * margin
        val body = if (text.isBlank()) "(This document has no readable text)" else text
        val l = StaticLayout.Builder.obtain(body, 0, body.length, tp, availW)
            .setLineSpacing(0f, 1.25f).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        layout = l
        starts.clear(); starts.add(0)
        val availH = h - margin - (margin * 1.3f).toInt()
        var pageTop = l.getLineTop(0)
        for (i in 0 until l.lineCount) {
            if (l.getLineBottom(i) - pageTop > availH) { starts.add(i); pageTop = l.getLineTop(i) }
        }
    }

    override fun pageCount() = max(1, starts.size)

    @Synchronized
    override fun render(i: Int, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(paper)
        val l = layout ?: return bmp
        if (i < 0 || i >= starts.size) return bmp
        val s = starts[i]
        val e = if (i + 1 < starts.size) starts[i + 1] else l.lineCount
        val c = Canvas(bmp)
        val top = l.getLineTop(s).toFloat()
        val bottom = l.getLineBottom(e - 1).toFloat()
        c.save()
        c.translate(margin.toFloat(), margin - top)
        c.clipRect(0f, top, availW.toFloat(), bottom)
        l.draw(c)
        c.restore()
        val np = TextPaint(Paint.ANTI_ALIAS_FLAG)
        np.color = 0x99000000.toInt(); np.textSize = 12f * ctx.resources.displayMetrics.density
        np.textAlign = Paint.Align.CENTER
        c.drawText("${i + 1}", w / 2f, h - margin * 0.5f, np)
        return bmp
    }

    companion object {
        fun docxText(f: File): String {
            val sb = StringBuilder()
            ZipFile(f).use { z ->
                val e = z.getEntry("word/document.xml") ?: return ""
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                parser.setInput(z.getInputStream(e), "UTF-8")
                var ev = parser.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "t" -> sb.append(parser.nextText())
                            "tab" -> sb.append('\t')
                            "br" -> sb.append('\n')
                        }
                    } else if (ev == XmlPullParser.END_TAG && parser.name == "p") {
                        sb.append("\n\n")
                    }
                    ev = parser.next()
                }
            }
            return sb.toString().replace(Regex("\n{3,}"), "\n\n")
        }
    }
}
