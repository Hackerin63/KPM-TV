package com.arun.aurora

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.sin

// Two-page open book with a page-turn animation (page folds over the spine)
class BookView(ctx: Context) : View(ctx) {
    var source: PageSource? = null
    var paper: Int = 0xFFFDFBF5.toInt()
    var onError: ((String) -> Unit)? = null
    var onSpreadChanged: ((Int) -> Unit)? = null
    var spread = 0

    private val cache = LruCache<Int, Bitmap>(8)
    private val exec = Executors.newSingleThreadExecutor()
    private val pending = HashSet<Int>()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val m = Matrix()
    private val src8 = FloatArray(8)
    private val dst8 = FloatArray(8)
    private val rect = RectF()
    private val path = Path()
    private var pw = 0
    private var ph = 0
    private var ready = false
    private var gen = 0
    private var animating = false
    private var animA = 0
    private var animT = 0f
    private var anim: ValueAnimator? = null
    private var leftShadow: Shader? = null
    private var rightShadow: Shader? = null

    fun totalSpreads(): Int = ((source?.pageCount() ?: 0) + 1) / 2

    fun openSource(s: PageSource, startSpread: Int) {
        source = s
        spread = startSpread
        ready = false
        startPrepare()
    }

    private fun startPrepare() {
        val s = source ?: return
        if (pw == 0) return
        val g = ++gen
        val w = pw; val h = ph
        exec.execute {
            try {
                s.prepare(w, h)
                post {
                    if (g == gen) {
                        ready = true
                        spread = spread.coerceIn(0, max0(totalSpreads() - 1))
                        cache.evictAll(); pending.clear()
                        prefetch(); invalidate()
                        onSpreadChanged?.invoke(spread)
                    }
                }
            } catch (e: Exception) {
                post { onError?.invoke(e.message ?: "Cannot open this file") }
            }
        }
    }

    private fun max0(v: Int) = if (v < 0) 0 else v

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        var hh = (h * 0.9f).toInt()
        var ww = (hh * 0.72f).toInt()
        if (ww * 2 > w * 0.96f) { ww = (w * 0.48f).toInt(); hh = (ww / 0.72f).toInt() }
        pw = ww; ph = hh
        val cx = w / 2f
        leftShadow = LinearGradient(cx - pw * 0.1f, 0f, cx, 0f, 0x00000000, 0x66000000, Shader.TileMode.CLAMP)
        rightShadow = LinearGradient(cx, 0f, cx + pw * 0.1f, 0f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
        if (source != null) { cache.evictAll(); pending.clear(); ready = false; startPrepare() }
    }

    fun changeTextSize(delta: Int) {
        val s = source ?: return
        if (!s.resizable) return
        val ratio = if (totalSpreads() > 0) spread / totalSpreads().toFloat() else 0f
        ready = false; invalidate()
        val g = ++gen
        exec.execute {
            s.changeTextSize(delta)
            try { s.prepare(pw, ph) } catch (_: Exception) {}
            post {
                if (g == gen) {
                    ready = true
                    spread = (ratio * totalSpreads()).toInt().coerceIn(0, max0(totalSpreads() - 1))
                    cache.evictAll(); pending.clear(); prefetch(); invalidate()
                    onSpreadChanged?.invoke(spread)
                }
            }
        }
    }

    private fun page(i: Int): Bitmap? {
        val s = source ?: return null
        if (!ready || i < 0 || i >= s.pageCount()) return null
        val b = cache.get(i)
        if (b != null) return b
        if (pending.add(i)) {
            val g = gen
            val w = pw; val h = ph
            exec.execute {
                try {
                    val bmp = s.render(i, w, h)
                    post { if (g == gen) { cache.put(i, bmp); pending.remove(i); invalidate() } }
                } catch (e: Exception) { post { pending.remove(i) } }
            }
        }
        return null
    }

    private fun prefetch() { for (i in 2 * spread - 2..2 * spread + 5) page(i) }

    fun turn(dir: Int) {
        val total = totalSpreads()
        if (animating || !ready || total == 0) return
        val target = spread + dir
        if (target < 0 || target >= total) return
        animating = true
        animA = if (dir > 0) spread else spread - 1
        prefetch()
        val va = ValueAnimator.ofFloat(0f, 1f)
        va.duration = 560
        va.interpolator = AccelerateDecelerateInterpolator()
        va.addUpdateListener {
            val f = it.animatedValue as Float
            animT = if (dir > 0) f else 1f - f
            invalidate()
        }
        va.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) {
                spread = target; animating = false; anim = null
                prefetch(); invalidate(); onSpreadChanged?.invoke(spread)
            }
        })
        anim = va
        va.start()
    }

    private fun drawPageAt(c: Canvas, idx: Int, left: Float, top: Float) {
        val s = source ?: return
        if (idx < 0 || idx >= s.pageCount()) return
        p.style = Paint.Style.FILL; p.shader = null; p.color = paper
        c.drawRect(left, top, left + pw, top + ph, p)
        val b = page(idx)
        if (b != null) c.drawBitmap(b, left, top, null)
    }

    private fun quad(c: Canvas, idx: Int, x0: Float, y0: Float, x1: Float, y1: Float,
                     x2: Float, y2: Float, x3: Float, y3: Float, shade: Float) {
        val b = page(idx)
        path.reset()
        path.moveTo(x0, y0); path.lineTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close()
        p.style = Paint.Style.FILL; p.shader = null; p.color = paper
        c.drawPath(path, p)
        if (b != null) {
            src8[0] = 0f; src8[1] = 0f; src8[2] = b.width.toFloat(); src8[3] = 0f
            src8[4] = b.width.toFloat(); src8[5] = b.height.toFloat(); src8[6] = 0f; src8[7] = b.height.toFloat()
            dst8[0] = x0; dst8[1] = y0; dst8[2] = x1; dst8[3] = y1; dst8[4] = x2; dst8[5] = y2; dst8[6] = x3; dst8[7] = y3
            m.setPolyToPoly(src8, 0, dst8, 0, 4)
            c.drawBitmap(b, m, null)
        }
        p.color = Color.argb((shade * 255f).toInt().coerceIn(0, 255), 0, 0, 0)
        c.drawPath(path, p)
    }

    private fun drawFlip(c: Canvas, a: Int, t: Float, cx: Float, top: Float) {
        drawPageAt(c, 2 * a, cx - pw, top)
        drawPageAt(c, 2 * a + 3, cx, top)
        val th = t * Math.PI.toFloat()
        val cs = cos(th); val sn = sin(th)
        val lift = ph * 0.05f * sn
        val shade = 0.35f * sn
        if (cs >= 0f) {
            val w1 = pw * cs
            if (w1 > 2f) quad(c, 2 * a + 1, cx, top, cx + w1, top + lift, cx + w1, top + ph - lift, cx, top + ph, shade)
        } else {
            val w2 = pw * -cs
            if (w2 > 2f) quad(c, 2 * a + 2, cx - w2, top + lift, cx, top, cx, top + ph, cx - w2, top + ph - lift, shade)
        }
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFF15110E.toInt())
        val s = source
        if (s == null || !ready) {
            p.shader = null; p.style = Paint.Style.FILL; p.color = 0xFFB8C2DD.toInt(); p.textSize = 30f
            p.textAlign = Paint.Align.CENTER
            c.drawText("Opening book...", width / 2f, height / 2f, p)
            return
        }
        val cx = width / 2f
        val top = (height - ph) / 2f
        p.style = Paint.Style.FILL; p.shader = null; p.color = 0xFF3A2A1E.toInt()
        rect.set(cx - pw - 12f, top - 8f, cx + pw + 12f, top + ph + 8f)
        c.drawRoundRect(rect, 14f, 14f, p)
        if (animating) {
            drawFlip(c, animA, animT, cx, top)
        } else {
            drawPageAt(c, 2 * spread, cx - pw, top)
            drawPageAt(c, 2 * spread + 1, cx, top)
        }
        p.shader = leftShadow; rect.set(cx - pw * 0.1f, top, cx, top + ph); c.drawRect(rect, p)
        p.shader = rightShadow; rect.set(cx, top, cx + pw * 0.1f, top + ph); c.drawRect(rect, p)
        p.shader = null
        val total = s.pageCount()
        val a = 2 * spread + 1; val b = minOf(2 * spread + 2, total)
        val label = if (a == b) "$a / $total" else "$a-$b / $total"
        p.textSize = 22f; p.textAlign = Paint.Align.CENTER
        val tw = p.measureText(label)
        p.color = 0x88000000.toInt()
        rect.set(cx - tw / 2f - 20f, height - 46f, cx + tw / 2f + 20f, height - 12f)
        c.drawRoundRect(rect, 17f, 17f, p)
        p.color = Color.WHITE
        c.drawText(label, cx, height - 22f, p)
    }

    fun release() { anim?.cancel(); exec.shutdownNow(); source?.close() }
}
