package com.arun.aurora

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// Animated wallpapers drawn with plain Canvas at ~30 fps (light enough for a 1 GB TV).
// The visible scene lives in the top half; the bottom is covered by the app menu.
class LiveWallpaperView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var animated = true
    private var th = Theme.SNOW
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val rnd = Random(11)
    private val dens = resources.displayMetrics.density
    private var photo: Bitmap? = null
    private var bg: Shader? = null
    private var last = 0L
    private var tm = 0f

    // ---- night sky
    private val starN = 140
    private val ax = FloatArray(starN); private val ay = FloatArray(starN)
    private val ar = FloatArray(starN); private val aph = FloatArray(starN)
    private var shT = -1f; private var shX = 0f; private var shY = 0f; private var shNext = 3f
    private var moonGlow: Shader? = null
    private var blobs: Array<Shader> = emptyArray()
    private var blobR = 0f

    // ---- snow
    private val snowN = 70
    private val sx = FloatArray(snowN); private val sy = FloatArray(snowN)
    private val sr = FloatArray(snowN); private val sv = FloatArray(snowN); private val sd = FloatArray(snowN)

    // ---- flower garden
    private val hills = Array(3) { Path() }
    private val hillColors = intArrayOf(0xFF2A5E49.toInt(), 0xFF367A50.toInt(), 0xFF46944F.toInt())
    private val flN = 16
    private val flX = FloatArray(flN); private val flBase = FloatArray(flN); private val flH = FloatArray(flN)
    private val flPh = FloatArray(flN); private val flRot = FloatArray(flN); private val flCol = IntArray(flN)
    private val flowerPal = intArrayOf(0xFFFF6FA5.toInt(), 0xFFFFD24D.toInt(), 0xFFB388FF.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFF7043.toInt(), 0xFF64B5F6.toInt())
    private val bladeN = 170
    private val bX = FloatArray(bladeN); private val bY = FloatArray(bladeN)
    private val bH = FloatArray(bladeN); private val bPh = FloatArray(bladeN)
    private val pollN = 30
    private val poX = FloatArray(pollN); private val poY = FloatArray(pollN)
    private val poV = FloatArray(pollN); private val poR = FloatArray(pollN)
    private var sunGlow: Shader? = null

    // ---- waterfall
    private val fallPath = Path(); private val cliffL = Path(); private val cliffR = Path()
    private var fallShader: Shader? = null
    private val wsN = 70
    private val wsU = FloatArray(wsN); private val wsY = FloatArray(wsN); private val wsLen = FloatArray(wsN)
    private val wsV = FloatArray(wsN); private val wsA = IntArray(wsN)
    private val mistN = 30
    private val miU = FloatArray(mistN); private val miL = FloatArray(mistN); private val miMax = FloatArray(mistN)
    private val ffN = 14
    private val ffX = FloatArray(ffN); private val ffY = FloatArray(ffN); private val ffP = FloatArray(ffN)

    // ---- F1
    private val bokN = 18
    private val bkX = FloatArray(bokN); private val bkY = FloatArray(bokN)
    private val bkR = FloatArray(bokN); private val bkC = IntArray(bokN)
    private val strN = 16
    private val stX = FloatArray(strN); private val stY = FloatArray(strN); private val stV = FloatArray(strN)
    private val carX = FloatArray(3)
    private val carSpeed = floatArrayOf(620f, 760f, 900f)
    private val carCol = intArrayOf(0xFFD81E1E.toInt(), 0xFF1E4FD8.toInt(), 0xFF22A89A.toInt())
    private val carAcc = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFD600.toInt(), 0xFF111111.toInt())
    private val boardCols = intArrayOf(0xFFC62828.toInt(), 0xFF1565C0.toInt(), 0xFFF9A825.toInt(),
        0xFF2E7D32.toInt(), 0xFFEEEEEE.toInt(), 0xFF6A1B9A.toInt())
    private val flashT = FloatArray(4); private val flashX = FloatArray(4); private val flashY = FloatArray(4)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun ease(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }

    fun setTheme(t: Theme) {
        th = t
        if (t == Theme.PHOTO) loadPhoto()
        buildBackground()
        initState()
        invalidate()
    }

    fun setAnimatedFlag(v: Boolean) { animated = v; last = 0L; invalidate() }

    private fun loadPhoto() {
        val f = WallpaperStore.file(context)
        photo = if (f.exists()) {
            val o = BitmapFactory.Options().apply { inSampleSize = 1; inPreferredConfig = Bitmap.Config.RGB_565 }
            try { BitmapFactory.decodeFile(f.path, o) } catch (_: OutOfMemoryError) { null }
        } else null
    }

    private fun buildBackground() {
        val w = width.toFloat(); val h = height.toFloat()
        if (h == 0f) return
        val cl = Shader.TileMode.CLAMP
        bg = when (th) {
            Theme.AURORA, Theme.PHOTO -> LinearGradient(0f, 0f, 0f, h,
                intArrayOf(0xFF050818.toInt(), 0xFF141A44.toInt(), 0xFF2A2060.toInt()), floatArrayOf(0f, 0.6f, 1f), cl)
            Theme.SNOW -> LinearGradient(0f, 0f, 0f, h, 0xFF0A1830.toInt(), 0xFF24508C.toInt(), cl)
            Theme.GARDEN -> LinearGradient(0f, 0f, 0f, h * 0.42f,
                intArrayOf(0xFF111C44.toInt(), 0xFF7A4B86.toInt(), 0xFFF0A664.toInt()), floatArrayOf(0f, 0.55f, 1f), cl)
            Theme.WATER -> LinearGradient(0f, 0f, 0f, h, 0xFF08161F.toInt(), 0xFF0F3A40.toInt(), cl)
            Theme.F1 -> LinearGradient(0f, 0f, 0f, h, 0xFF04060E.toInt(), 0xFF1A1230.toInt(), cl)
        }
        blobR = h * 0.8f
        blobs = arrayOf(
            RadialGradient(0f, 0f, blobR, 0x556EA8FF, 0x006EA8FF, cl),
            RadialGradient(0f, 0f, blobR, 0x558A5CFF, 0x008A5CFF, cl),
            RadialGradient(0f, 0f, blobR, 0x5540E0D0, 0x0040E0D0, cl)
        )
        moonGlow = RadialGradient(0f, 0f, h * 0.25f, 0x55CFE3FF, 0x00CFE3FF, cl)
        sunGlow = RadialGradient(0f, 0f, h * 0.45f, 0xCCFFD27F.toInt(), 0x00FFD27F, cl)
        fallShader = LinearGradient(0f, h * 0.06f, 0f, h * 0.5f, 0xE6D6F4FF.toInt(), 0xE69AD6F2.toInt(), cl)

        // waterfall shapes
        fallPath.reset()
        fallPath.moveTo(w * 0.38f, h * 0.06f); fallPath.lineTo(w * 0.62f, h * 0.06f)
        fallPath.lineTo(w * 0.67f, h * 0.5f); fallPath.lineTo(w * 0.33f, h * 0.5f); fallPath.close()
        cliffL.reset()
        cliffL.moveTo(0f, 0f); cliffL.lineTo(w * 0.38f, 0f); cliffL.lineTo(w * 0.35f, h * 0.06f)
        cliffL.lineTo(w * 0.33f, h * 0.2f); cliffL.lineTo(w * 0.30f, h * 0.36f); cliffL.lineTo(w * 0.27f, h * 0.5f)
        cliffL.lineTo(w * 0.2f, h * 0.58f); cliffL.lineTo(0f, h * 0.6f); cliffL.close()
        cliffR.reset()
        cliffR.moveTo(w, 0f); cliffR.lineTo(w * 0.62f, 0f); cliffR.lineTo(w * 0.65f, h * 0.06f)
        cliffR.lineTo(w * 0.67f, h * 0.2f); cliffR.lineTo(w * 0.70f, h * 0.36f); cliffR.lineTo(w * 0.73f, h * 0.5f)
        cliffR.lineTo(w * 0.8f, h * 0.58f); cliffR.lineTo(w, h * 0.6f); cliffR.close()

        // garden hills
        for (k in 0 until 3) {
            val hp = hills[k]
            hp.reset(); hp.moveTo(0f, h)
            var x = 0f
            while (x <= w) { hp.lineTo(x, hillY(k, x)); x += 16f }
            hp.lineTo(w, h); hp.close()
        }
    }

    private fun hillY(k: Int, x: Float): Float {
        val h = height.toFloat()
        val base = floatArrayOf(0.40f, 0.46f, 0.52f)[k]
        return h * base + sin(x * 0.004f + k * 1.7f) * h * 0.022f + sin(x * 0.011f + k * 0.9f) * h * 0.010f
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        buildBackground()
        initState()
    }

    private fun initState() {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f) return
        for (i in 0 until starN) {
            ax[i] = rnd.nextFloat() * w; ay[i] = rnd.nextFloat() * h * 0.6f
            ar[i] = (0.6f + rnd.nextFloat() * 1.6f) * dens; aph[i] = rnd.nextFloat() * 6.28f
        }
        for (i in 0 until snowN) {
            sx[i] = rnd.nextFloat() * w; sy[i] = rnd.nextFloat() * h
            sr[i] = (1.2f + rnd.nextFloat() * 3.2f) * dens
            sv[i] = 30f * dens + sr[i] * 10f
            sd[i] = (8f + rnd.nextFloat() * 25f) * dens
        }
        for (i in 0 until flN) {
            flX[i] = w * (0.04f + 0.92f * rnd.nextFloat())
            val k = 1 + rnd.nextInt(2)
            flBase[i] = hillY(k, flX[i]) + 6f
            flH[i] = h * (0.12f + 0.10f * rnd.nextFloat())
            flPh[i] = rnd.nextFloat() * 18f
            flRot[i] = rnd.nextFloat() * 6.28f
            flCol[i] = flowerPal[rnd.nextInt(flowerPal.size)]
        }
        for (i in 0 until bladeN) {
            bX[i] = rnd.nextFloat() * w; bY[i] = hillY(2, bX[i]) + 4f
            bH[i] = (10f + rnd.nextFloat() * 22f) * dens; bPh[i] = rnd.nextFloat() * 6.28f
        }
        for (i in 0 until pollN) {
            poX[i] = rnd.nextFloat() * w; poY[i] = rnd.nextFloat() * h * 0.5f
            poV[i] = (10f + rnd.nextFloat() * 20f) * dens; poR[i] = (1.5f + rnd.nextFloat() * 2.5f) * dens
        }
        for (i in 0 until wsN) {
            wsU[i] = rnd.nextFloat(); wsY[i] = h * (0.06f + 0.44f * rnd.nextFloat())
            wsLen[i] = (30f + rnd.nextFloat() * 90f) * dens; wsV[i] = (280f + rnd.nextFloat() * 420f) * dens
            wsA[i] = 90 + rnd.nextInt(130)
        }
        for (i in 0 until mistN) { miMax[i] = 1.6f + rnd.nextFloat(); miL[i] = rnd.nextFloat() * miMax[i]; miU[i] = rnd.nextFloat() }
        for (i in 0 until ffN) {
            ffX[i] = if (i % 2 == 0) w * (0.05f + 0.22f * rnd.nextFloat()) else w * (0.73f + 0.22f * rnd.nextFloat())
            ffY[i] = h * (0.12f + 0.38f * rnd.nextFloat()); ffP[i] = rnd.nextFloat() * 6.28f
        }
        for (i in 0 until bokN) {
            bkX[i] = rnd.nextFloat() * w; bkY[i] = h * (0.04f + 0.14f * rnd.nextFloat())
            bkR[i] = (6f + rnd.nextFloat() * 16f) * dens
            bkC[i] = intArrayOf(0x33FFE0A0, 0x33FF8A80, 0x33A0C8FF, 0x33FFFFFF)[rnd.nextInt(4)]
        }
        for (i in 0 until strN) {
            stX[i] = rnd.nextFloat() * w; stY[i] = h * (0.37f + 0.16f * rnd.nextFloat())
            stV[i] = 900f + rnd.nextFloat() * 900f
        }
        carX[0] = w * 0.1f; carX[1] = w * 0.5f; carX[2] = w * 0.8f
        for (i in 0 until 4) flashT[i] = rnd.nextFloat()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f) return
        val now = System.nanoTime()
        val dt = if (!animated) 0f else if (last == 0L) 0.033f else ((now - last) / 1e9f).coerceIn(0f, 0.1f)
        last = now; tm += dt

        p.style = Paint.Style.FILL
        p.shader = bg
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        when (th) {
            Theme.AURORA -> drawNight(c, w, h, dt)
            Theme.SNOW -> drawSnow(c, w, h, dt)
            Theme.GARDEN -> drawGarden(c, w, h, dt)
            Theme.WATER -> drawWater(c, w, h, dt)
            Theme.F1 -> drawF1(c, w, h, dt)
            Theme.PHOTO -> drawPhoto(c, w, h)
        }
        if (animated) postInvalidateDelayed(33)
    }

    // ------------------------------------------------------------------ night sky
    private fun drawBlobs(c: Canvas, w: Float, h: Float) {
        if (blobs.isEmpty()) return
        for (i in 0 until 3) {
            val x = w * (0.5f + 0.35f * sin(tm * 0.15f + i * 2.1f))
            val y = h * (0.35f + 0.2f * cos(tm * 0.12f + i * 1.7f))
            c.save(); c.translate(x, y)
            p.shader = blobs[i]
            c.drawCircle(0f, 0f, blobR, p)
            c.restore()
        }
        p.shader = null
    }

    private fun drawNight(c: Canvas, w: Float, h: Float, dt: Float) {
        drawBlobs(c, w, h)
        c.save(); c.translate(w * 0.82f, h * 0.2f)
        p.shader = moonGlow; c.drawCircle(0f, 0f, h * 0.25f, p); p.shader = null
        p.color = 0xFFEFF4FF.toInt(); c.drawCircle(0f, 0f, h * 0.055f, p)
        p.color = 0x22000000; c.drawCircle(-h * 0.015f, -h * 0.01f, h * 0.012f, p)
        c.drawCircle(h * 0.012f, h * 0.014f, h * 0.008f, p)
        c.restore()
        for (i in 0 until starN) {
            val a = 0.35f + 0.65f * abs(sin(tm * 1.3f + aph[i]))
            p.color = Color.argb((a * 255f).toInt(), 255, 255, 255)
            c.drawCircle(ax[i], ay[i], ar[i], p)
        }
        shNext -= dt
        if (shT < 0f && shNext <= 0f) {
            shT = 0f; shX = w * (0.05f + 0.6f * rnd.nextFloat()); shY = h * (0.04f + 0.14f * rnd.nextFloat())
            shNext = 5f + rnd.nextFloat() * 6f
        }
        if (shT >= 0f) {
            shT += dt
            val hx = shX + shT * 900f * dens; val hy = shY + shT * 420f * dens
            p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f * dens
            p.color = Color.argb(((1f - shT / 0.9f) * 255f).toInt().coerceIn(0, 255), 255, 255, 255)
            c.drawLine(hx - 140f * dens, hy - 65f * dens, hx, hy, p)
            p.style = Paint.Style.FILL
            if (shT > 0.9f) shT = -1f
        }
    }

    // ------------------------------------------------------------------ snow
    private fun drawSnow(c: Canvas, w: Float, h: Float, dt: Float) {
        for (i in 0 until snowN) {
            sy[i] += sv[i] * dt
            sx[i] += sin(tm * 0.8f + i) * sd[i] * dt
            if (sy[i] > h + 10f) { sy[i] = -10f; sx[i] = rnd.nextFloat() * w }
            if (sx[i] > w + 10f) sx[i] = -10f else if (sx[i] < -10f) sx[i] = w + 10f
            p.color = Color.argb(minOf(255, (110 + sr[i] * 25f).toInt()), 255, 255, 255)
            c.drawCircle(sx[i], sy[i], sr[i], p)
        }
    }

    // ------------------------------------------------------------------ flower garden
    private fun drawGarden(c: Canvas, w: Float, h: Float, dt: Float) {
        c.save(); c.translate(w * 0.72f, h * 0.40f)
        p.shader = sunGlow; c.drawCircle(0f, 0f, h * 0.45f, p); p.shader = null
        p.color = 0xFFFFE9B0.toInt(); c.drawCircle(0f, 0f, h * 0.06f, p)
        c.restore()
        for (k in 0 until 3) { p.color = hillColors[k]; c.drawPath(hills[k], p) }

        for (i in 0 until flN) {
            val cyc = ((tm + flPh[i]) % 18f) / 18f
            val g = when {
                cyc < 0.2f -> ease(cyc / 0.2f)
                cyc < 0.88f -> 1f
                else -> 1f - ease((cyc - 0.88f) / 0.12f)
            }
            if (g <= 0.02f) continue
            val sway = sin(tm * 1.2f + flPh[i]) * flH[i] * 0.07f
            val bx = flX[i]; val by = flBase[i]
            val hx = bx + sway * g; val hy = by - flH[i] * g
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f * dens; p.strokeCap = Paint.Cap.ROUND
            p.color = 0xFF2E7D32.toInt()
            path.reset(); path.moveTo(bx, by); path.quadTo(bx - sway * 0.5f, (by + hy) / 2f, hx, hy)
            c.drawPath(path, p)
            p.style = Paint.Style.FILL
            p.color = 0xFF43A047.toInt()
            val lx = (bx + hx) / 2f; val ly = (by + hy) / 2f
            rect.set(lx, ly - 5f * dens * g, lx + 18f * dens * g, ly + 3f * dens * g)
            c.drawOval(rect, p)
            val r = flH[i] * 0.16f * (0.4f + 0.6f * g)
            p.color = flCol[i]
            for (k in 0 until 6) {
                val a = k * 1.0472f + flRot[i] + tm * 0.15f
                c.drawCircle(hx + cos(a) * r * 0.9f, hy + sin(a) * r * 0.9f, r * 0.55f, p)
            }
            p.color = 0xFFFFC107.toInt(); c.drawCircle(hx, hy, r * 0.5f, p)
        }

        p.style = Paint.Style.STROKE; p.strokeWidth = 2f * dens; p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until bladeN) {
            val sw = sin(tm * 1.6f + bPh[i] + bX[i] * 0.01f) * bH[i] * 0.3f
            p.color = if (i % 3 == 0) 0xFF66BB6A.toInt() else 0xFF388E3C.toInt()
            c.drawLine(bX[i], bY[i], bX[i] + sw, bY[i] - bH[i], p)
        }
        p.style = Paint.Style.FILL

        for (i in 0 until 3) {
            val bxp = w * (0.15f + 0.7f * ((tm * 0.025f + i / 3f) % 1f)) + sin(tm * 1.3f + i) * 50f * dens
            val byp = h * (0.2f + 0.06f * i) + cos(tm * 0.9f + i) * 30f * dens
            val flap = abs(sin(tm * 9f + i))
            val ws = 14f * dens
            p.color = if (i == 1) 0xFF64B5F6.toInt() else 0xFFFFA726.toInt()
            rect.set(bxp - ws * flap, byp - ws * 0.8f, bxp, byp + ws * 0.4f); c.drawOval(rect, p)
            rect.set(bxp, byp - ws * 0.8f, bxp + ws * flap, byp + ws * 0.4f); c.drawOval(rect, p)
            p.color = 0xFF222222.toInt()
            c.drawRect(bxp - 1.5f * dens, byp - ws * 0.5f, bxp + 1.5f * dens, byp + ws * 0.5f, p)
        }

        for (i in 0 until pollN) {
            poX[i] += poV[i] * dt; poY[i] += sin(tm + i) * 6f * dt + poV[i] * 0.15f * dt
            if (poX[i] > w) poX[i] = 0f
            if (poY[i] > h * 0.5f) poY[i] = 0f
            p.color = 0x66FFF3C4
            c.drawCircle(poX[i], poY[i], poR[i], p)
        }
    }

    // ------------------------------------------------------------------ waterfall
    private fun drawWater(c: Canvas, w: Float, h: Float, dt: Float) {
        val top = h * 0.06f; val bot = h * 0.5f
        // pool
        p.color = 0xFF0E4A5A.toInt(); c.drawRect(0f, bot, w, h, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f * dens; p.color = 0x33FFFFFF
        for (k in 0 until 5) {
            path.reset()
            val y0 = bot + h * (0.04f + 0.07f * k)
            var x = 0f
            path.moveTo(0f, y0)
            while (x <= w) { x += 32f; path.lineTo(x, y0 + sin(x * 0.02f - tm * (1.2f + k * 0.3f)) * 5f * dens) }
            c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL
        // cliffs + moss rim
        p.color = 0xFF0C1720.toInt(); c.drawPath(cliffL, p); c.drawPath(cliffR, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3f * dens; p.color = 0x884CAF50.toInt()
        c.drawPath(cliffL, p); c.drawPath(cliffR, p)
        p.style = Paint.Style.FILL
        // water body
        p.shader = fallShader; c.drawPath(fallPath, p); p.shader = null
        // falling streaks
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until wsN) {
            wsY[i] += wsV[i] * dt
            if (wsY[i] > bot) { wsY[i] = top - wsLen[i] * rnd.nextFloat(); wsU[i] = rnd.nextFloat() }
            val f = ((wsY[i] - top) / (bot - top)).coerceIn(0f, 1f)
            val xl = lerp(w * 0.38f, w * 0.33f, f); val xr = lerp(w * 0.62f, w * 0.67f, f)
            val x = lerp(xl, xr, wsU[i])
            p.strokeWidth = (1.5f + wsA[i] / 90f) * dens
            p.color = Color.argb(wsA[i], 255, 255, 255)
            c.drawLine(x, wsY[i], x, minOf(bot, wsY[i] + wsLen[i]), p)
        }
        p.style = Paint.Style.FILL
        // mist at the base
        for (i in 0 until mistN) {
            miL[i] -= dt
            if (miL[i] <= 0f) { miMax[i] = 1.6f + rnd.nextFloat(); miL[i] = miMax[i]; miU[i] = rnd.nextFloat() }
            val q = miL[i] / miMax[i]
            val x = lerp(w * 0.33f, w * 0.67f, miU[i])
            val y = bot - (1f - q) * 60f * dens
            p.color = Color.argb((q * 80f).toInt(), 255, 255, 255)
            c.drawCircle(x, y, (18f + (1f - q) * 46f) * dens, p)
        }
        // rocks at the base
        p.color = 0xFF0A141B.toInt()
        rect.set(w * 0.22f, bot - 8f * dens, w * 0.34f, bot + 18f * dens); c.drawOval(rect, p)
        rect.set(w * 0.66f, bot - 8f * dens, w * 0.78f, bot + 18f * dens); c.drawOval(rect, p)
        // fireflies
        for (i in 0 until ffN) {
            val x = ffX[i] + sin(tm * 0.4f + ffP[i]) * 50f * dens
            val y = ffY[i] + cos(tm * 0.5f + ffP[i] * 1.3f) * 28f * dens
            val a = 0.4f + 0.6f * abs(sin(tm * 1.5f + ffP[i]))
            p.color = Color.argb((a * 40f).toInt(), 200, 255, 120); c.drawCircle(x, y, 11f * dens, p)
            p.color = Color.argb((a * 90f).toInt(), 220, 255, 140); c.drawCircle(x, y, 5f * dens, p)
            p.color = Color.argb((a * 255f).toInt(), 255, 255, 200); c.drawCircle(x, y, 2f * dens, p)
        }
    }

    // ------------------------------------------------------------------ F1
    private fun drawF1(c: Canvas, w: Float, h: Float, dt: Float) {
        // bokeh lights drifting slowly
        for (i in 0 until bokN) {
            bkX[i] -= 14f * dens * dt
            if (bkX[i] < -40f) bkX[i] = w + 40f
            p.color = bkC[i]; c.drawCircle(bkX[i], bkY[i], bkR[i], p)
        }
        // grandstand with crowd dots and camera flashes
        p.color = 0xFF10131C.toInt(); c.drawRect(0f, h * 0.20f, w, h * 0.30f, p)
        for (row in 0 until 3) {
            val y = h * (0.225f + row * 0.027f)
            var x = (row * 9f) % 18f
            var n = 0
            while (x < w) {
                p.color = intArrayOf(0xFF3B4257.toInt(), 0xFF5A3B3B.toInt(), 0xFF3B5A4A.toInt(), 0xFF4A4A66.toInt())[(n + row) % 4]
                c.drawCircle(x, y, 3.2f * dens, p); x += 18f; n++
            }
        }
        for (i in 0 until 4) {
            flashT[i] -= dt
            if (flashT[i] <= 0f) { flashT[i] = 0.6f + rnd.nextFloat() * 1.8f; flashX[i] = rnd.nextFloat() * w; flashY[i] = h * (0.22f + 0.07f * rnd.nextFloat()) }
            if (flashT[i] > 0.5f) {
                p.color = Color.argb(((flashT[i] - 0.5f).coerceAtMost(0.3f) / 0.3f * 220f).toInt(), 255, 255, 255)
                c.drawCircle(flashX[i], flashY[i], 4f * dens, p)
            }
        }
        // advertising boards (scroll at medium speed)
        val bw = w * 0.12f
        val off = (tm * 160f * dens) % (bw * boardCols.size)
        val shift = (off / bw).toInt()
        val frac = off % bw
        for (i in -1..9) {
            val x = i * bw - frac
            p.color = boardCols[((i + shift) % boardCols.size + boardCols.size) % boardCols.size]
            c.drawRect(x + 2f, h * 0.30f, x + bw - 2f, h * 0.345f, p)
            p.color = 0x55FFFFFF
            c.drawRect(x + bw * 0.15f, h * 0.315f, x + bw * 0.7f, h * 0.322f, p)
        }
        // track
        p.shader = LinearGradient(0f, h * 0.345f, 0f, h * 0.56f, 0xFF1B1D26.toInt(), 0xFF0E0F14.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, h * 0.345f, w, h * 0.56f, p)
        p.shader = null
        // kerb
        val kw = 44f * dens
        val ko = (tm * 700f * dens) % (kw * 2f)
        var kx = -ko
        var kn = 0
        while (kx < w) {
            p.color = if (kn % 2 == 0) 0xFFD32F2F.toInt() else 0xFFF5F5F5.toInt()
            c.drawRect(kx, h * 0.345f, kx + kw, h * 0.365f, p)
            kx += kw; kn++
        }
        p.color = 0x99FFFFFF.toInt(); c.drawRect(0f, h * 0.552f, w, h * 0.558f, p)
        // speed streaks
        p.color = 0x33FFFFFF
        for (i in 0 until strN) {
            stX[i] -= stV[i] * dt
            if (stX[i] < -220f) { stX[i] = w + rnd.nextFloat() * 300f; stY[i] = h * (0.37f + 0.17f * rnd.nextFloat()) }
            c.drawRect(stX[i], stY[i], stX[i] + 180f * dens, stY[i] + 1.5f * dens, p)
        }
        // cars (back to front)
        val ys = floatArrayOf(0.435f, 0.485f, 0.54f)
        val sc = floatArrayOf(0.85f, 0.97f, 1.1f)
        for (i in 0 until 3) {
            val L = h * 0.30f * sc[i]
            carX[i] += carSpeed[i] * dens * dt
            if (carX[i] > w + L * 0.5f) carX[i] = -L * 1.6f
            // motion-blur trail
            for (k in 1..4) {
                p.color = Color.argb(46 - k * 9, 255, 255, 255)
                rect.set(carX[i] - L * 0.22f * k, h * ys[i] - L * 0.14f, carX[i] - L * 0.22f * (k - 1), h * ys[i] - L * 0.02f)
                c.drawRect(rect, p)
            }
            drawF1Car(c, carX[i], h * ys[i], L, carCol[i], carAcc[i])
        }
    }

    private fun drawF1Car(c: Canvas, x: Float, y: Float, L: Float, body: Int, accent: Int) {
        fun px(u: Float) = x + u * L
        fun py(v: Float) = y + v * L
        p.style = Paint.Style.FILL
        p.color = 0x66000000; rect.set(px(0.0f), py(-0.012f), px(1.04f), py(0.022f)); c.drawOval(rect, p)
        // rear wing
        p.color = 0xFF111111.toInt()
        c.drawRect(px(0.0f), py(-0.215f), px(0.075f), py(-0.2f), p)
        c.drawRect(px(0.02f), py(-0.2f), px(0.045f), py(-0.08f), p)
        p.color = accent; c.drawRect(px(0.0f), py(-0.222f), px(0.08f), py(-0.213f), p)
        // body
        path.reset()
        path.moveTo(px(0.06f), py(-0.03f)); path.lineTo(px(0.13f), py(-0.12f)); path.lineTo(px(0.22f), py(-0.16f))
        path.lineTo(px(0.40f), py(-0.12f)); path.lineTo(px(0.58f), py(-0.095f)); path.lineTo(px(0.82f), py(-0.062f))
        path.lineTo(px(1.0f), py(-0.048f)); path.lineTo(px(1.0f), py(-0.025f)); path.lineTo(px(0.06f), py(-0.015f)); path.close()
        p.color = body; c.drawPath(path, p)
        p.color = 0x55000000; c.drawRect(px(0.10f), py(-0.045f), px(1.0f), py(-0.018f), p)
        p.color = accent; c.drawRect(px(0.28f), py(-0.092f), px(0.82f), py(-0.078f), p)
        p.color = 0xCC000000.toInt(); rect.set(px(0.47f), py(-0.07f), px(0.58f), py(-0.04f)); c.drawRoundRect(rect, 4f, 4f, p)
        // cockpit
        p.color = 0xFFFFFFFF.toInt(); c.drawCircle(px(0.47f), py(-0.128f), 0.026f * L, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.008f * L; p.color = 0xFF222222.toInt()
        rect.set(px(0.40f), py(-0.165f), px(0.54f), py(-0.105f)); c.drawArc(rect, 200f, 140f, false, p)
        p.style = Paint.Style.FILL
        // front wing
        p.color = 0xFF111111.toInt(); c.drawRect(px(0.90f), py(-0.022f), px(1.06f), py(-0.012f), p)
        p.color = accent; c.drawRect(px(1.04f), py(-0.045f), px(1.06f), py(-0.01f), p)
        wheel(c, px(0.20f), py(-0.062f), 0.068f * L)
        wheel(c, px(0.80f), py(-0.052f), 0.058f * L)
    }

    private fun wheel(c: Canvas, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xFF0B0B0B.toInt(); c.drawCircle(cx, cy, r, p)
        p.color = 0xFF3A3A3A.toInt(); c.drawCircle(cx, cy, r * 0.55f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.12f; p.color = 0xFFBBBBBB.toInt()
        for (k in 0 until 4) {
            val a = tm * 40f + k * 1.5708f
            c.drawLine(cx, cy, cx + cos(a) * r * 0.5f, cy + sin(a) * r * 0.5f, p)
        }
        p.style = Paint.Style.FILL
    }

    // ------------------------------------------------------------------ photo (slow zoom)
    private fun drawPhoto(c: Canvas, w: Float, h: Float) {
        val b = photo
        if (b == null) { drawBlobs(c, w, h); return }
        val z = 1.04f + 0.04f * sin(tm * 0.1f)
        val sc = max(w / b.width, h / b.height) * z
        val dw = b.width * sc; val dh = b.height * sc
        c.drawBitmap(b, null, RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f), null)
        p.color = 0x44000000
        c.drawRect(0f, 0f, w, h, p)
    }
}
