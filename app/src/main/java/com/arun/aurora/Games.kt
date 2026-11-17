package com.arun.aurora

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

abstract class GameView(ctx: Context, val key: String) : View(ctx) {
    private val ui = Handler(Looper.getMainLooper())
    private var running = false
    var waiting = true
    var paused = false
    var level = 3
        set(v) { field = v.coerceIn(1, 5); onLevelChanged() }
    protected val p = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val rect = RectF()
    protected val path = Path()
    protected val rnd = Random()
    abstract val gameName: String
    open val usesSpeed = true
    open val speedLabel = "Speed"

    protected fun sf(): Float = floatArrayOf(0.6f, 0.8f, 1f, 1.3f, 1.7f)[level - 1]
    protected open fun onLevelChanged() {}
    protected abstract fun step()
    protected abstract fun nextDelay(): Long
    protected abstract fun restart()
    abstract fun onKey(code: Int): Boolean

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            if (!waiting && !paused) step()
            invalidate()
            ui.postDelayed(this, nextDelay())
        }
    }

    fun restartGame() { restart(); waiting = true; paused = false; invalidate() }

    fun handleKey(code: Int): Boolean {
        if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { if (!waiting) paused = !paused; invalidate(); return true }
        if (waiting) {
            if (isOk(code)) { waiting = false; invalidate(); return true }
            return false
        }
        if (paused) return false
        return onKey(code)
    }

    fun resume() { if (!running) { running = true; ui.postDelayed(loop, nextDelay()) } }
    fun pause() { running = false; ui.removeCallbacks(loop) }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); resume() }
    override fun onDetachedFromWindow() { pause(); super.onDetachedFromWindow() }

    protected fun isOk(code: Int) = code == KeyEvent.KEYCODE_DPAD_CENTER ||
        code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_BUTTON_A

    protected fun label(c: Canvas, s: String, x: Float, y: Float, size: Float,
                        color: Int = Color.WHITE, align: Paint.Align = Paint.Align.LEFT) {
        p.shader = null; p.style = Paint.Style.FILL
        p.color = color; p.textSize = size; p.textAlign = align; p.isFakeBoldText = true
        c.drawText(s, x, y, p)
        p.isFakeBoldText = false
    }

    protected fun panel(c: Canvas, title: String, sub: String) {
        val w = width.toFloat(); val h = height.toFloat()
        val pw = w * 0.44f; val ph = h * 0.32f
        rect.set((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f)
        p.shader = null; p.style = Paint.Style.FILL; p.color = 0xE8141B2D.toInt()
        c.drawRoundRect(rect, 40f, 40f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0x44FFFFFF
        c.drawRoundRect(rect, 40f, 40f, p)
        p.style = Paint.Style.FILL
        label(c, title, w / 2f, rect.top + ph * 0.48f, ph * 0.22f, Color.WHITE, Paint.Align.CENTER)
        label(c, sub, w / 2f, rect.top + ph * 0.76f, ph * 0.1f, 0xFFB8C2DD.toInt(), Paint.Align.CENTER)
    }

    protected fun hud(c: Canvas, s: String) {
        val txt = if (usesSpeed) "$s    $speedLabel $level/5" else s
        p.textSize = 26f; p.isFakeBoldText = true
        val w = p.measureText(txt)
        p.isFakeBoldText = false
        rect.set(20f, 16f, 20f + w + 40f, 66f)
        p.style = Paint.Style.FILL; p.color = 0x66000000
        c.drawRoundRect(rect, 25f, 25f, p)
        label(c, txt, 40f, 51f, 26f)
    }

    protected fun overlays(c: Canvas, dead: Boolean, deadTitle: String, deadSub: String, hint: String) {
        if (waiting) panel(c, gameName, hint)
        else if (paused) panel(c, "Paused", "Back = game menu")
        else if (dead) panel(c, deadTitle, deadSub)
    }
}

// ---------------------------------------------------------------- Snake
class SnakeView(ctx: Context) : GameView(ctx, "snake") {
    override val gameName = "Snake"
    private val cols = 32
    private val rows = 18
    private val sx = ArrayList<Int>()
    private val sy = ArrayList<Int>()
    private var dx = 1; private var dy = 0; private var ndx = 1; private var ndy = 0
    private var fx = 0; private var fy = 0
    private var score = 0; private var best = 0; private var dead = false

    init { restart() }

    override fun restart() {
        sx.clear(); sy.clear()
        for (i in 0 until 3) { sx.add(8 - i); sy.add(9) }
        dx = 1; dy = 0; ndx = 1; ndy = 0
        score = 0; dead = false
        placeFood()
    }

    private fun onSnake(x: Int, y: Int): Boolean {
        for (i in sx.indices) if (sx[i] == x && sy[i] == y) return true
        return false
    }

    private fun placeFood() { do { fx = rnd.nextInt(cols); fy = rnd.nextInt(rows) } while (onSnake(fx, fy)) }

    override fun nextDelay(): Long = max(40L, ((130L - score * 3L) / sf()).toLong())

    override fun step() {
        if (dead) return
        dx = ndx; dy = ndy
        val nx = (sx[0] + dx + cols) % cols
        val ny = (sy[0] + dy + rows) % rows
        val eating = nx == fx && ny == fy
        val limit = if (eating) sx.size else sx.size - 1
        for (i in 0 until limit) {
            if (sx[i] == nx && sy[i] == ny) { dead = true; if (score > best) best = score; return }
        }
        sx.add(0, nx); sy.add(0, ny)
        if (eating) { score++; placeFood() } else { sx.removeAt(sx.size - 1); sy.removeAt(sy.size - 1) }
    }

    override fun onKey(code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> if (dy == 0) { ndx = 0; ndy = -1 }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (dy == 0) { ndx = 0; ndy = 1 }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (dx == 0) { ndx = -1; ndy = 0 }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (dx == 0) { ndx = 1; ndy = 0 }
            else -> { if (isOk(code)) { if (dead) restart() } else return false }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFF08140E.toInt())
        val cell = min(width / cols.toFloat(), height / rows.toFloat())
        val bw = cell * cols; val bh = cell * rows
        val ox = (width - bw) / 2f; val oy = (height - bh) / 2f
        p.style = Paint.Style.FILL
        p.color = 0xFF0E2217.toInt()
        rect.set(ox, oy, ox + bw, oy + bh); c.drawRoundRect(rect, 24f, 24f, p)
        p.color = 0xFFFF5252.toInt()
        c.drawCircle(ox + fx * cell + cell / 2f, oy + fy * cell + cell / 2f, cell * 0.38f, p)
        for (i in sx.indices) {
            p.color = if (i == 0) 0xFFB9F6CA.toInt() else 0xFF2ECC71.toInt()
            val l = ox + sx[i] * cell + 1.5f; val t = oy + sy[i] * cell + 1.5f
            rect.set(l, t, l + cell - 3f, t + cell - 3f); c.drawRoundRect(rect, cell * 0.35f, cell * 0.35f, p)
        }
        hud(c, "Score $score    Best $best")
        overlays(c, dead, "Game Over", "Score $score  -  Press OK to retry", "Press OK to start  -  Back = menu")
    }
}

// ---------------------------------------------------------------- Blocks
class BlocksView(ctx: Context) : GameView(ctx, "blocks") {
    override val gameName = "Blocks"
    private val cols = 10
    private val rows = 20
    private val grid = Array(rows) { IntArray(cols) }
    private val shapes = arrayOf(
        intArrayOf(0, 1, 1, 1, 2, 1, 3, 1), intArrayOf(1, 0, 2, 0, 1, 1, 2, 1),
        intArrayOf(1, 0, 0, 1, 1, 1, 2, 1), intArrayOf(1, 0, 2, 0, 0, 1, 1, 1),
        intArrayOf(0, 0, 1, 0, 1, 1, 2, 1), intArrayOf(0, 0, 0, 1, 1, 1, 2, 1),
        intArrayOf(2, 0, 0, 1, 1, 1, 2, 1)
    )
    private val sizes = intArrayOf(4, 2, 3, 3, 3, 3, 3)
    private val colors = intArrayOf(0xFF00E5FF.toInt(), 0xFFFFD600.toInt(), 0xFFB388FF.toInt(), 0xFF69F0AE.toInt(),
        0xFFFF5252.toInt(), 0xFF448AFF.toInt(), 0xFFFFAB40.toInt())
    private var cur = IntArray(8)
    private var type = 0; private var nextType = 0
    private var px = 0; private var py = 0
    private var score = 0; private var lines = 0; private var over = false

    init { restart() }

    override fun restart() {
        for (r in grid) r.fill(0)
        score = 0; lines = 0; over = false
        nextType = rnd.nextInt(7)
        spawn()
    }

    private fun spawn() {
        type = nextType; nextType = rnd.nextInt(7)
        cur = shapes[type].copyOf(); px = 3; py = 0
        if (hit(cur, px, py)) over = true
    }

    private fun hit(cells: IntArray, ox: Int, oy: Int): Boolean {
        var i = 0
        while (i < 8) {
            val x = cells[i] + ox; val y = cells[i + 1] + oy
            if (x < 0 || x >= cols || y >= rows) return true
            if (y >= 0 && grid[y][x] != 0) return true
            i += 2
        }
        return false
    }

    private fun rotate() {
        if (type == 1) return
        val n = sizes[type]
        val r = IntArray(8)
        var i = 0
        while (i < 8) { r[i] = n - 1 - cur[i + 1]; r[i + 1] = cur[i]; i += 2 }
        for (k in intArrayOf(0, -1, 1, -2, 2)) {
            if (!hit(r, px + k, py)) { cur = r; px += k; return }
        }
    }

    private fun lock() {
        var i = 0
        while (i < 8) {
            val x = cur[i] + px; val y = cur[i + 1] + py
            if (y >= 0) grid[y][x] = type + 1
            i += 2
        }
        var cleared = 0
        var r = rows - 1
        while (r >= 0) {
            if (grid[r].all { it != 0 }) {
                for (k in r downTo 1) grid[k] = grid[k - 1].copyOf()
                grid[0] = IntArray(cols)
                cleared++
            } else r--
        }
        lines += cleared
        score += when (cleared) { 1 -> 100; 2 -> 300; 3 -> 500; 4 -> 800; else -> 0 }
        spawn()
    }

    override fun nextDelay(): Long = max(60L, ((600L - (lines / 10) * 50L) / sf()).toLong())

    override fun step() {
        if (over) return
        if (!hit(cur, px, py + 1)) py++ else lock()
    }

    override fun onKey(code: Int): Boolean {
        if (over) {
            if (isOk(code)) { restart(); invalidate(); return true }
            return false
        }
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (!hit(cur, px - 1, py)) px--
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (!hit(cur, px + 1, py)) px++
            KeyEvent.KEYCODE_DPAD_DOWN -> if (!hit(cur, px, py + 1)) { py++; score++ }
            KeyEvent.KEYCODE_DPAD_UP -> rotate()
            else -> { if (isOk(code)) { while (!hit(cur, px, py + 1)) py++; lock() } else return false }
        }
        invalidate()
        return true
    }

    private fun block(c: Canvas, l: Float, t: Float, cell: Float, color: Int) {
        p.style = Paint.Style.FILL; p.color = color
        rect.set(l + 1.5f, t + 1.5f, l + cell - 1.5f, t + cell - 1.5f)
        c.drawRoundRect(rect, cell * 0.25f, cell * 0.25f, p)
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFF0A0F1C.toInt())
        val cell = height * 0.92f / rows
        val bw = cell * cols; val bh = cell * rows
        val ox = (width - bw) / 2f - cell * 3f; val oy = (height - bh) / 2f
        p.style = Paint.Style.FILL; p.color = 0xFF121A2E.toInt()
        rect.set(ox - 8f, oy - 8f, ox + bw + 8f, oy + bh + 8f); c.drawRoundRect(rect, 28f, 28f, p)
        for (y in 0 until rows) for (x in 0 until cols) {
            val v = grid[y][x]
            if (v != 0) block(c, ox + x * cell, oy + y * cell, cell, colors[v - 1])
        }
        if (!over) {
            var i = 0
            while (i < 8) { block(c, ox + (cur[i] + px) * cell, oy + (cur[i + 1] + py) * cell, cell, colors[type]); i += 2 }
        }
        val sx0 = ox + bw + cell * 1.5f
        p.style = Paint.Style.FILL; p.color = 0x66000000
        rect.set(sx0, oy, sx0 + cell * 6f, oy + cell * 9f); c.drawRoundRect(rect, 28f, 28f, p)
        label(c, "Score", sx0 + 24f, oy + cell * 1.2f, cell * 0.6f, 0xFFB8C2DD.toInt())
        label(c, "$score", sx0 + 24f, oy + cell * 2.2f, cell * 0.9f)
        label(c, "Lines $lines", sx0 + 24f, oy + cell * 3.4f, cell * 0.6f, 0xFFB8C2DD.toInt())
        label(c, "Next", sx0 + 24f, oy + cell * 4.6f, cell * 0.6f, 0xFFB8C2DD.toInt())
        val small = cell * 0.7f
        var j = 0
        while (j < 8) {
            block(c, sx0 + 30f + shapes[nextType][j] * small, oy + cell * 5.1f + shapes[nextType][j + 1] * small, small, colors[nextType])
            j += 2
        }
        label(c, "Speed $level/5", sx0 + 24f, oy + cell * 8.3f, cell * 0.5f, 0xFFB8C2DD.toInt())
        overlays(c, over, "Game Over", "Score $score  -  Press OK to retry", "D-pad move  -  Up rotate  -  OK drop")
    }
}

// ---------------------------------------------------------------- Car Race / Highway Rush
class CarView(ctx: Context, private val highway: Boolean) : GameView(ctx, if (highway) "highway" else "cars") {
    override val gameName = if (highway) "Highway Rush" else "Car Race"
    private val lanes = if (highway) 4 else 3
    private class Obs(var lane: Int, var y: Float, val truck: Boolean, val color: Int)
    private val obs = ArrayList<Obs>()
    private var lane = 1
    private var pxLane = 1f
    private var dist = 0f; private var speed = 300f; private var spawnCool = 0.5f
    private var dash = 0f; private var scenery = 0f
    private var nitroT = 0f; private var nitroCd = 0f
    private var dead = false; private var best = 0; private var lastLane = -1
    private val dt = 1f / 30f
    private val cols = intArrayOf(0xFF42A5F5.toInt(), 0xFFFFCA28.toInt(), 0xFF66BB6A.toInt(), 0xFFAB47BC.toInt(), 0xFFECEFF1.toInt(), 0xFFFF7043.toInt())

    init { restart() }

    override fun restart() {
        lane = lanes / 2; pxLane = lane.toFloat()
        obs.clear(); dist = 0f; speed = 300f; spawnCool = 0.5f; dash = 0f; scenery = 0f
        nitroT = 0f; nitroCd = 0f; dead = false; lastLane = -1
    }

    private fun roadW() = min(height * 0.9f, width * 0.55f)
    private fun laneW() = roadW() / lanes
    private fun carW() = laneW() * 0.56f
    private fun carH() = carW() * 1.95f
    private fun score() = (dist / 25f).toInt()
    override fun nextDelay(): Long = 33L

    override fun step() {
        if (dead || height == 0) return
        val k = sf()
        pxLane += (lane - pxLane) * 0.3f
        if (nitroT > 0f) nitroT -= dt
        if (nitroCd > 0f) nitroCd -= dt
        val boost = if (nitroT > 0f) 1.6f else 1f
        speed = (300f + dist / 60f).coerceAtMost(700f) * k * boost
        dist += speed * dt
        dash = (dash + speed * dt) % 90f
        scenery = (scenery + speed * dt) % 150f
        spawnCool -= dt
        val ch = carH()
        val free = obs.none { it.y < ch * 2.8f }
        if (free && spawnCool <= 0f) {
            var l = rnd.nextInt(lanes)
            if (l == lastLane && rnd.nextInt(3) != 0) l = (l + 1 + rnd.nextInt(lanes - 1)) % lanes
            lastLane = l
            val truck = highway && rnd.nextInt(4) == 0
            obs.add(Obs(l, -ch * (if (truck) 1.9f else 1f), truck, cols[rnd.nextInt(cols.size)]))
            spawnCool = 0.15f + rnd.nextFloat() * 0.5f
        }
        val top = height - ch - 36f
        val iter = obs.iterator()
        while (iter.hasNext()) {
            val o = iter.next()
            o.y += speed * 0.62f * dt
            val oh = if (o.truck) ch * 1.9f else ch
            if (o.y > height + 30f) { iter.remove(); continue }
            if (abs(pxLane - o.lane) < 0.62f && o.y + oh > top + ch * 0.12f && o.y < top + ch * 0.88f) {
                dead = true
                if (score() > best) best = score()
            }
        }
    }

    override fun onKey(code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> lane = max(0, lane - 1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> lane = min(lanes - 1, lane + 1)
            else -> {
                if (isOk(code)) {
                    if (dead) restart() else if (highway && nitroCd <= 0f) { nitroT = 1.4f; nitroCd = 6f }
                } else return false
            }
        }
        return true
    }

    private fun tree(c: Canvas, x: Float, y: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xFF14532D.toInt(); c.drawCircle(x, y, 26f, p)
        p.color = 0xFF1E7A43.toInt(); c.drawCircle(x - 4f, y - 4f, 16f, p)
    }

    private fun car(c: Canvas, cx: Float, top: Float, w: Float, h: Float, color: Int, truck: Boolean, tilt: Float, flame: Boolean) {
        c.save(); c.rotate(tilt, cx, top + h / 2f)
        p.style = Paint.Style.FILL
        p.color = 0x55000000; rect.set(cx - w / 2f - 4f, top + 6f, cx + w / 2f + 4f, top + h + 6f); c.drawRoundRect(rect, w * 0.3f, w * 0.3f, p)
        if (flame) {
            path.reset(); path.moveTo(cx - w * 0.25f, top + h); path.lineTo(cx, top + h + h * 0.55f); path.lineTo(cx + w * 0.25f, top + h); path.close()
            p.color = 0xFFFF9800.toInt(); c.drawPath(path, p)
            path.reset(); path.moveTo(cx - w * 0.12f, top + h); path.lineTo(cx, top + h + h * 0.3f); path.lineTo(cx + w * 0.12f, top + h); path.close()
            p.color = 0xFFFFF59D.toInt(); c.drawPath(path, p)
        }
        p.color = 0xFF0A0A0A.toInt()
        val wys = if (truck) floatArrayOf(0.08f, 0.45f, 0.8f) else floatArrayOf(0.14f, 0.72f)
        for (wy in wys) {
            rect.set(cx - w / 2f - 5f, top + h * wy, cx - w / 2f + 5f, top + h * (wy + 0.14f)); c.drawRoundRect(rect, 4f, 4f, p)
            rect.set(cx + w / 2f - 5f, top + h * wy, cx + w / 2f + 5f, top + h * (wy + 0.14f)); c.drawRoundRect(rect, 4f, 4f, p)
        }
        p.color = color; rect.set(cx - w / 2f, top, cx + w / 2f, top + h); c.drawRoundRect(rect, w * 0.28f, w * 0.28f, p)
        if (truck) {
            p.color = 0xFFE0E0E0.toInt(); rect.set(cx - w / 2f + 3f, top + h * 0.34f, cx + w / 2f - 3f, top + h - 3f); c.drawRoundRect(rect, w * 0.12f, w * 0.12f, p)
            p.color = 0xCC1A2233.toInt(); rect.set(cx - w * 0.38f, top + h * 0.06f, cx + w * 0.38f, top + h * 0.16f); c.drawRoundRect(rect, 6f, 6f, p)
        } else {
            p.color = 0xCC1A2233.toInt()
            rect.set(cx - w * 0.36f, top + h * 0.22f, cx + w * 0.36f, top + h * 0.42f); c.drawRoundRect(rect, 8f, 8f, p)
            rect.set(cx - w * 0.36f, top + h * 0.68f, cx + w * 0.36f, top + h * 0.82f); c.drawRoundRect(rect, 8f, 8f, p)
            p.color = 0x33FFFFFF; rect.set(cx - w * 0.3f, top + h * 0.44f, cx + w * 0.3f, top + h * 0.66f); c.drawRoundRect(rect, 8f, 8f, p)
        }
        p.color = 0xFFFFF59D.toInt()
        c.drawCircle(cx - w * 0.32f, top + 5f, 4f, p); c.drawCircle(cx + w * 0.32f, top + 5f, 4f, p)
        p.color = 0xFFE53935.toInt()
        c.drawRect(cx - w * 0.4f, top + h - 6f, cx - w * 0.2f, top + h - 2f, p)
        c.drawRect(cx + w * 0.2f, top + h - 6f, cx + w * 0.4f, top + h - 2f, p)
        c.restore()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(0xFF0B1A14.toInt())
        val rw = roadW(); val cx = w / 2f; val left = cx - rw / 2f; val laneW = laneW()
        var i = -1
        while (i * 150f < h + 150f) {
            val y = i * 150f + scenery
            tree(c, left * 0.55f, y); tree(c, left * 0.2f, y + 75f)
            tree(c, w - left * 0.55f, y + 40f); tree(c, w - left * 0.2f, y + 110f)
            i++
        }
        p.style = Paint.Style.FILL
        p.color = 0xFF262A33.toInt(); c.drawRect(left, 0f, left + rw, h, p)
        p.color = 0xFFDDDDDD.toInt()
        c.drawRect(left + 4f, 0f, left + 10f, h, p); c.drawRect(left + rw - 10f, 0f, left + rw - 4f, h, p)
        p.color = 0x99FFFFFF.toInt()
        for (l in 1 until lanes) {
            var y = -90f + dash
            while (y < h) { c.drawRect(left + laneW * l - 3f, y, left + laneW * l + 3f, y + 45f, p); y += 90f }
        }
        val ch = carH()
        for (o in obs) car(c, left + laneW * (o.lane + 0.5f), o.y, carW(), if (o.truck) ch * 1.9f else ch, o.color, o.truck, 0f, false)
        car(c, left + laneW * (pxLane + 0.5f), h - ch - 36f, carW(), ch, 0xFFFF4D4D.toInt(), false, (lane - pxLane) * 14f, nitroT > 0f)
        hud(c, "Score ${score()}    Best $best    ${(speed / 4f).toInt()} km/h")
        if (highway) {
            val bar = if (nitroCd <= 0f) 1f else 1f - nitroCd / 6f
            p.style = Paint.Style.FILL; p.color = 0x66000000; rect.set(w - 260f, 20f, w - 20f, 52f); c.drawRoundRect(rect, 16f, 16f, p)
            p.color = if (nitroCd <= 0f) 0xFFFFB300.toInt() else 0xFF607D8B.toInt()
            rect.set(w - 254f, 26f, w - 254f + 228f * bar, 46f); c.drawRoundRect(rect, 10f, 10f, p)
            label(c, "OK = Nitro", w - 250f, 80f, 20f, 0xFFB8C2DD.toInt())
        }
        overlays(c, dead, "Crash!", "Score ${score()}  -  Press OK to retry",
            if (highway) "Left / Right steer  -  OK nitro" else "Left / Right to steer")
    }
}

// ---------------------------------------------------------------- Maze
class MazeView(ctx: Context) : GameView(ctx, "maze") {
    override val gameName = "Maze"
    override val speedLabel = "Size"
    private var gw = 0; private var gh = 0
    private var wall = BooleanArray(0)
    private var seen = BooleanArray(0)
    private var px = 1; private var py = 1
    private var time = 0f; private var cleared = 0; private var won = false

    init { restart() }

    override fun onLevelChanged() { gen() }
    override fun restart() { cleared = 0; gen() }
    override fun nextDelay(): Long = 50L
    override fun step() { if (!won) time += 0.05f }

    private fun gen() {
        val cw = 6 + level * 3
        val ch = max(5, (cw * 0.56f).toInt())
        gw = cw * 2 + 1; gh = ch * 2 + 1
        wall = BooleanArray(gw * gh) { true }
        seen = BooleanArray(gw * gh)
        val stack = ArrayList<Int>()
        fun idx(x: Int, y: Int) = y * gw + x
        wall[idx(1, 1)] = false
        stack.add(idx(1, 1))
        val dirs = arrayOf(intArrayOf(2, 0), intArrayOf(-2, 0), intArrayOf(0, 2), intArrayOf(0, -2))
        while (stack.isNotEmpty()) {
            val cur = stack[stack.size - 1]
            val x = cur % gw; val y = cur / gw
            val opts = ArrayList<IntArray>()
            for (d in dirs) {
                val nx = x + d[0]; val ny = y + d[1]
                if (nx in 1 until gw - 1 && ny in 1 until gh - 1 && wall[idx(nx, ny)]) opts.add(d)
            }
            if (opts.isEmpty()) stack.removeAt(stack.size - 1)
            else {
                val d = opts[rnd.nextInt(opts.size)]
                wall[idx(x + d[0] / 2, y + d[1] / 2)] = false
                wall[idx(x + d[0], y + d[1])] = false
                stack.add(idx(x + d[0], y + d[1]))
            }
        }
        px = 1; py = 1; seen[idx(1, 1)] = true; time = 0f; won = false
    }

    override fun onKey(code: Int): Boolean {
        if (won) {
            if (isOk(code)) { gen(); return true }
            return false
        }
        var dx = 0; var dy = 0
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> dy = -1
            KeyEvent.KEYCODE_DPAD_DOWN -> dy = 1
            KeyEvent.KEYCODE_DPAD_LEFT -> dx = -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> dx = 1
            else -> return false
        }
        val nx = px + dx; val ny = py + dy
        if (nx in 0 until gw && ny in 0 until gh && !wall[ny * gw + nx]) {
            px = nx; py = ny; seen[py * gw + px] = true
            if (px == gw - 2 && py == gh - 2) { won = true; cleared++ }
        }
        invalidate()
        return true
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFF080B1A.toInt())
        if (gw == 0) return
        val cell = min(width * 0.96f / gw, height * 0.86f / gh)
        val ox = (width - cell * gw) / 2f; val oy = (height - cell * gh) / 2f + 22f
        p.style = Paint.Style.FILL; p.color = 0xFF0E1330.toInt()
        rect.set(ox - 6f, oy - 6f, ox + cell * gw + 6f, oy + cell * gh + 6f); c.drawRoundRect(rect, 22f, 22f, p)
        for (y in 0 until gh) for (x in 0 until gw) {
            val l = ox + x * cell; val t = oy + y * cell
            if (wall[y * gw + x]) {
                p.color = 0xFF3949AB.toInt()
                rect.set(l, t, l + cell, t + cell); c.drawRoundRect(rect, cell * 0.25f, cell * 0.25f, p)
            } else if (seen[y * gw + x]) {
                p.color = 0x3300E5FF
                c.drawCircle(l + cell / 2f, t + cell / 2f, cell * 0.2f, p)
            }
        }
        val pulse = 0.8f + 0.2f * sin(time * 6f + 1f)
        p.color = 0x5500E676; c.drawCircle(ox + (gw - 2) * cell + cell / 2f, oy + (gh - 2) * cell + cell / 2f, cell * 0.6f * pulse, p)
        p.color = 0xFF00E676.toInt(); c.drawCircle(ox + (gw - 2) * cell + cell / 2f, oy + (gh - 2) * cell + cell / 2f, cell * 0.32f, p)
        p.color = 0x55FFD54F; c.drawCircle(ox + px * cell + cell / 2f, oy + py * cell + cell / 2f, cell * 0.62f, p)
        p.color = 0xFFFFD54F.toInt(); c.drawCircle(ox + px * cell + cell / 2f, oy + py * cell + cell / 2f, cell * 0.36f, p)
        hud(c, "Cleared $cleared    Time ${time.toInt()}s")
        overlays(c, won, "Maze cleared!", "Time ${time.toInt()}s  -  Press OK for a new maze", "Reach the green goal  -  D-pad to move")
    }
}

// ---------------------------------------------------------------- Breakout
class BreakoutView(ctx: Context) : GameView(ctx, "breakout") {
    override val gameName = "Breakout"
    private val cols = 10
    private val rows = 6
    private val alive = BooleanArray(cols * rows)
    private val rowCols = intArrayOf(0xFFFF5252.toInt(), 0xFFFF9800.toInt(), 0xFFFFEB3B.toInt(),
        0xFF66BB6A.toInt(), 0xFF42A5F5.toInt(), 0xFFAB47BC.toInt())
    private var padX = 0.5f
    private var bx = 0f; private var by = 0f; private var vx = 0f; private var vy = 0f
    private var serve = false; private var needReset = true
    private var lives = 3; private var score = 0; private var stage = 1; private var dead = false
    private val dt = 1f / 30f

    init { restart() }

    override fun restart() {
        score = 0; lives = 3; stage = 1; dead = false
        for (i in alive.indices) alive[i] = true
        padX = 0.5f; serve = false; needReset = true
    }

    override fun nextDelay(): Long = 33L

    private fun launch() {
        val h = height.toFloat()
        vx = (if (rnd.nextBoolean()) 1f else -1f) * h * 0.25f
        vy = -h * 0.55f * (1f + 0.06f * (stage - 1))
        serve = false
    }

    override fun step() {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f) return
        val r = h * 0.017f; val padW = w * 0.14f; val padY = h * 0.92f; val pc = padX * w
        if (needReset) { bx = pc; by = padY - r - 2f; launch(); needReset = false }
        if (serve) { bx = pc; by = padY - r - 2f; return }
        val k = sf()
        bx += vx * dt * k; by += vy * dt * k
        if (bx < r) { bx = r; vx = abs(vx) } else if (bx > w - r) { bx = w - r; vx = -abs(vx) }
        if (by < r) { by = r; vy = abs(vy) }
        if (vy > 0f && by + r >= padY && by + r <= padY + h * 0.05f && abs(bx - pc) <= padW / 2f + r) {
            vy = -abs(vy)
            vx = ((bx - pc) / (padW / 2f)).coerceIn(-1f, 1f) * h * 0.55f
        }
        val bw = w * 0.92f / cols; val bh = h * 0.045f; val ox = w * 0.04f; val oy = h * 0.14f
        for (i in alive.indices) {
            if (!alive[i]) continue
            val x0 = ox + (i % cols) * bw; val y0 = oy + (i / cols) * bh
            if (bx + r > x0 && bx - r < x0 + bw && by + r > y0 && by - r < y0 + bh) {
                alive[i] = false; score += 10 * stage
                val ovx = min(bx + r - x0, x0 + bw - (bx - r)); val ovy = min(by + r - y0, y0 + bh - (by - r))
                if (ovx < ovy) vx = -vx else vy = -vy
                break
            }
        }
        if (alive.none { it }) { stage++; for (i in alive.indices) alive[i] = true; serve = true }
        if (by - r > h) { lives--; if (lives <= 0) dead = true else serve = true }
    }

    override fun onKey(code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> padX = (padX - 0.045f).coerceIn(0.08f, 0.92f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> padX = (padX + 0.045f).coerceIn(0.08f, 0.92f)
            else -> {
                if (isOk(code)) { if (dead) restart() else if (serve) launch() } else return false
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(0xFF090D1F.toInt())
        val bw = w * 0.92f / cols; val bh = h * 0.045f; val ox = w * 0.04f; val oy = h * 0.14f
        p.style = Paint.Style.FILL
        for (i in alive.indices) {
            if (!alive[i]) continue
            p.color = rowCols[i / cols]
            val x0 = ox + (i % cols) * bw; val y0 = oy + (i / cols) * bh
            rect.set(x0 + 2f, y0 + 2f, x0 + bw - 2f, y0 + bh - 2f); c.drawRoundRect(rect, 10f, 10f, p)
        }
        val padW = w * 0.14f; val padY = h * 0.92f; val pc = padX * w
        p.color = 0xFF90CAF9.toInt(); rect.set(pc - padW / 2f, padY, pc + padW / 2f, padY + h * 0.022f); c.drawRoundRect(rect, 14f, 14f, p)
        p.color = Color.WHITE; c.drawCircle(bx, by, h * 0.017f, p)
        hud(c, "Score $score    Lives $lives    Stage $stage")
        if (serve && !waiting && !dead) label(c, "Press OK to serve", w / 2f, h * 0.8f, 26f, 0xFFB8C2DD.toInt(), Paint.Align.CENTER)
        overlays(c, dead, "Game Over", "Score $score  -  Press OK to retry", "Left / Right move paddle  -  OK serve")
    }
}

// ---------------------------------------------------------------- Pong
class PongView(ctx: Context) : GameView(ctx, "pong") {
    override val gameName = "Pong"
    private var py = 0.5f; private var ay = 0.5f
    private var bx = 0f; private var by = 0f; private var vx = 0f; private var vy = 0f
    private var ps = 0; private var asc = 0; private var over = false; private var needReset = true
    private val dt = 1f / 30f

    init { restart() }

    override fun restart() { ps = 0; asc = 0; over = false; needReset = true; py = 0.5f; ay = 0.5f }
    override fun nextDelay(): Long = 33L

    override fun step() {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f) return
        val r = h * 0.02f; val padH = h * 0.2f; val padW = w * 0.012f; val margin = w * 0.04f
        if (needReset) {
            bx = w / 2f; by = h / 2f
            vx = (if (rnd.nextBoolean()) 1f else -1f) * w * 0.3f
            vy = (rnd.nextFloat() - 0.5f) * h * 0.5f
            needReset = false
        }
        val k = sf()
        bx += vx * dt * k; by += vy * dt * k
        if (by < r) { by = r; vy = abs(vy) } else if (by > h - r) { by = h - r; vy = -abs(vy) }
        val aiMax = 0.55f * dt
        ay += (by / h - ay).coerceIn(-aiMax, aiMax)
        ay = ay.coerceIn(0.1f, 0.9f)
        val leftX = margin + padW
        if (vx < 0f && bx - r <= leftX && bx > margin && abs(by - py * h) <= padH / 2f + r) {
            vx = abs(vx) * 1.05f; vy += (by - py * h) / (padH / 2f) * h * 0.3f
        }
        val rightX = w - margin - padW
        if (vx > 0f && bx + r >= rightX && bx < w - margin && abs(by - ay * h) <= padH / 2f + r) {
            vx = -abs(vx) * 1.05f; vy += (by - ay * h) / (padH / 2f) * h * 0.3f
        }
        vy = vy.coerceIn(-h * 0.9f, h * 0.9f)
        if (bx < 0f) { asc++; needReset = true }
        if (bx > w) { ps++; needReset = true }
        if (ps >= 7 || asc >= 7) over = true
    }

    override fun onKey(code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> py = (py - 0.05f).coerceIn(0.1f, 0.9f)
            KeyEvent.KEYCODE_DPAD_DOWN -> py = (py + 0.05f).coerceIn(0.1f, 0.9f)
            else -> { if (isOk(code)) { if (over) restart() } else return false }
        }
        invalidate()
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(0xFF080C18.toInt())
        p.style = Paint.Style.FILL; p.color = 0x33FFFFFF
        var y = 10f
        while (y < h) { rect.set(w / 2f - 3f, y, w / 2f + 3f, y + 24f); c.drawRoundRect(rect, 3f, 3f, p); y += 44f }
        val padH = h * 0.2f; val padW = w * 0.012f; val margin = w * 0.04f
        p.color = 0xFF80DEEA.toInt()
        rect.set(margin, py * h - padH / 2f, margin + padW, py * h + padH / 2f); c.drawRoundRect(rect, 10f, 10f, p)
        p.color = 0xFFFF8A80.toInt()
        rect.set(w - margin - padW, ay * h - padH / 2f, w - margin, ay * h + padH / 2f); c.drawRoundRect(rect, 10f, 10f, p)
        p.color = Color.WHITE; c.drawCircle(bx, by, h * 0.02f, p)
        label(c, "$ps", w * 0.4f, h * 0.15f, 56f, 0xFF80DEEA.toInt(), Paint.Align.CENTER)
        label(c, "$asc", w * 0.6f, h * 0.15f, 56f, 0xFFFF8A80.toInt(), Paint.Align.CENTER)
        hud(c, "First to 7 wins")
        overlays(c, over, if (ps >= 7) "You win!" else "Computer wins", "$ps - $asc  -  Press OK to play again", "Up / Down move paddle")
    }
}

// ---------------------------------------------------------------- 2048
class Game2048View(ctx: Context) : GameView(ctx, "g2048") {
    override val gameName = "2048"
    override val usesSpeed = false
    private val b = IntArray(16)
    private var score = 0; private var best = 0; private var dead = false

    init { restart() }

    override fun restart() { b.fill(0); score = 0; dead = false; addTile(); addTile() }
    override fun nextDelay(): Long = 200L
    override fun step() {}

    private fun addTile() {
        val empty = b.indices.filter { b[it] == 0 }
        if (empty.isEmpty()) return
        b[empty[rnd.nextInt(empty.size)]] = if (rnd.nextInt(10) == 0) 4 else 2
    }

    private fun slide(a: IntArray): Boolean {
        val vals = a.filter { it != 0 }
        val out = ArrayList<Int>()
        var i = 0
        while (i < vals.size) {
            if (i + 1 < vals.size && vals[i] == vals[i + 1]) { out.add(vals[i] * 2); score += vals[i] * 2; i += 2 }
            else { out.add(vals[i]); i++ }
        }
        while (out.size < 4) out.add(0)
        var changed = false
        for (k in 0..3) { if (a[k] != out[k]) changed = true; a[k] = out[k] }
        return changed
    }

    private fun move(dir: Int): Boolean {
        var moved = false
        val tmp = IntArray(4)
        for (r in 0..3) {
            for (k in 0..3) tmp[k] = when (dir) { 0 -> b[r * 4 + k]; 1 -> b[r * 4 + 3 - k]; 2 -> b[k * 4 + r]; else -> b[(3 - k) * 4 + r] }
            if (slide(tmp)) moved = true
            for (k in 0..3) {
                when (dir) {
                    0 -> b[r * 4 + k] = tmp[k]
                    1 -> b[r * 4 + 3 - k] = tmp[k]
                    2 -> b[k * 4 + r] = tmp[k]
                    else -> b[(3 - k) * 4 + r] = tmp[k]
                }
            }
        }
        return moved
    }

    private fun canMove(): Boolean {
        for (i in 0 until 16) {
            if (b[i] == 0) return true
            if (i % 4 < 3 && b[i] == b[i + 1]) return true
            if (i < 12 && b[i] == b[i + 4]) return true
        }
        return false
    }

    override fun onKey(code: Int): Boolean {
        val dir = when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> 0
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            KeyEvent.KEYCODE_DPAD_UP -> 2
            KeyEvent.KEYCODE_DPAD_DOWN -> 3
            else -> -1
        }
        if (dir < 0) {
            if (isOk(code) && dead) { restart(); invalidate(); return true }
            return false
        }
        if (!dead && move(dir)) {
            addTile()
            if (score > best) best = score
            if (!canMove()) dead = true
        }
        invalidate()
        return true
    }

    private fun tileColor(v: Int): Int = when (v) {
        0 -> 0x22FFFFFF
        2 -> 0xFFEEE4DA.toInt(); 4 -> 0xFFEDE0C8.toInt(); 8 -> 0xFFF2B179.toInt(); 16 -> 0xFFF59563.toInt()
        32 -> 0xFFF67C5F.toInt(); 64 -> 0xFFF65E3B.toInt(); 128 -> 0xFFEDCF72.toInt(); 256 -> 0xFFEDCC61.toInt()
        512 -> 0xFFEDC850.toInt(); 1024 -> 0xFFEDC53F.toInt(); else -> 0xFFEDC22E.toInt()
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFF0F1220.toInt())
        val cell = height * 0.19f; val gap = cell * 0.08f
        val size = cell * 4 + gap * 5
        val ox = (width - size) / 2f; val oy = (height - size) / 2f
        p.style = Paint.Style.FILL; p.color = 0x33FFFFFF
        rect.set(ox, oy, ox + size, oy + size); c.drawRoundRect(rect, 28f, 28f, p)
        for (i in 0 until 16) {
            val x = ox + gap + (i % 4) * (cell + gap); val y = oy + gap + (i / 4) * (cell + gap)
            p.color = tileColor(b[i]); p.style = Paint.Style.FILL
            rect.set(x, y, x + cell, y + cell); c.drawRoundRect(rect, 18f, 18f, p)
            if (b[i] != 0) {
                val s = b[i].toString()
                val ts = if (s.length <= 2) cell * 0.5f else if (s.length == 3) cell * 0.4f else cell * 0.32f
                label(c, s, x + cell / 2f, y + cell / 2f + ts * 0.35f, ts, if (b[i] <= 4) 0xFF5A4B3C.toInt() else Color.WHITE, Paint.Align.CENTER)
            }
        }
        hud(c, "Score $score    Best $best")
        overlays(c, dead, "No moves left", "Score $score  -  Press OK to retry", "D-pad slides the tiles  -  merge to 2048")
    }
}

// ---------------------------------------------------------------- Space Shooter
class ShooterView(ctx: Context) : GameView(ctx, "shooter") {
    override val gameName = "Space Shooter"
    private class Obj(var x: Float, var y: Float, var a: Float = 0f, var b: Float = 0f)
    private val bullets = ArrayList<Obj>()
    private val enemies = ArrayList<Obj>()
    private val sparks = ArrayList<FloatArray>()
    private val starX = FloatArray(50); private val starY = FloatArray(50); private val starV = FloatArray(50)
    private var sxPos = 0.5f
    private var fireT = 0f; private var spawnT = 0.8f
    private var lives = 3; private var score = 0; private var best = 0; private var dead = false
    private var time = 0f
    private val dt = 1f / 30f

    init {
        for (i in 0 until 50) { starX[i] = rnd.nextFloat(); starY[i] = rnd.nextFloat(); starV[i] = 0.05f + rnd.nextFloat() * 0.2f }
        restart()
    }

    override fun restart() {
        bullets.clear(); enemies.clear(); sparks.clear()
        sxPos = 0.5f; fireT = 0f; spawnT = 0.8f; lives = 3; score = 0; dead = false
    }

    override fun nextDelay(): Long = 33L

    private fun boom(x: Float, y: Float) {
        for (i in 0 until 10) {
            val a = rnd.nextFloat() * 6.28f; val v = 60f + rnd.nextFloat() * 160f
            sparks.add(floatArrayOf(x, y, kotlin.math.cos(a) * v, kotlin.math.sin(a) * v, 0.5f))
        }
    }

    override fun step() {
        val w = width.toFloat(); val h = height.toFloat()
        if (dead || w == 0f) return
        val k = sf()
        time += dt
        for (i in 0 until 50) { starY[i] += starV[i] * dt * k; if (starY[i] > 1f) { starY[i] = 0f; starX[i] = rnd.nextFloat() } }
        val shipY = h * 0.88f; val shipX = sxPos * w
        fireT -= dt
        if (fireT <= 0f) { bullets.add(Obj(shipX, shipY - 30f)); fireT = 0.28f / k }
        spawnT -= dt
        if (spawnT <= 0f) {
            val x0 = w * (0.08f + 0.84f * rnd.nextFloat())
            enemies.add(Obj(x0, -30f, rnd.nextFloat() * 6.28f, x0))
            spawnT = max(0.3f, 1.1f - score / 500f) / k
        }
        val bi = bullets.iterator()
        while (bi.hasNext()) { val b = bi.next(); b.y -= 700f * dt; if (b.y < -20f) bi.remove() }
        val ei = enemies.iterator()
        while (ei.hasNext()) {
            val e = ei.next()
            e.y += (90f + min(score * 0.2f, 120f)) * dt * k
            e.x = e.b + sin(e.a + e.y * 0.02f) * 60f
            if (abs(e.x - shipX) < 38f && abs(e.y - shipY) < 30f) {
                boom(e.x, e.y); ei.remove(); lives--
                if (lives <= 0) { dead = true; if (score > best) best = score }
                continue
            }
            if (e.y > h + 20f) { ei.remove(); lives--; if (lives <= 0) { dead = true; if (score > best) best = score }; continue }
            var hitIt = false
            val b2 = bullets.iterator()
            while (b2.hasNext()) {
                val b = b2.next()
                if (abs(b.x - e.x) < 28f && abs(b.y - e.y) < 24f) { b2.remove(); hitIt = true; break }
            }
            if (hitIt) { boom(e.x, e.y); ei.remove(); score += 10 }
        }
        val si = sparks.iterator()
        while (si.hasNext()) { val s = si.next(); s[0] += s[2] * dt; s[1] += s[3] * dt; s[4] -= dt; if (s[4] <= 0f) si.remove() }
    }

    override fun onKey(code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> sxPos = (sxPos - 0.04f).coerceIn(0.05f, 0.95f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> sxPos = (sxPos + 0.04f).coerceIn(0.05f, 0.95f)
            else -> { if (isOk(code)) { if (dead) restart() } else return false }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(0xFF05060F.toInt())
        p.style = Paint.Style.FILL; p.color = 0xAAFFFFFF.toInt()
        for (i in 0 until 50) c.drawCircle(starX[i] * w, starY[i] * h, 1.5f + starV[i] * 6f, p)
        for (e in enemies) {
            p.color = 0xFF7C4DFF.toInt(); rect.set(e.x - 30f, e.y - 10f, e.x + 30f, e.y + 14f); c.drawOval(rect, p)
            p.color = 0xFF80D8FF.toInt(); rect.set(e.x - 14f, e.y - 22f, e.x + 14f, e.y + 2f); c.drawOval(rect, p)
            p.color = 0xFFFFEB3B.toInt(); c.drawCircle(e.x - 18f, e.y + 4f, 3f, p); c.drawCircle(e.x + 18f, e.y + 4f, 3f, p)
        }
        p.color = 0xFFFFF176.toInt()
        for (b in bullets) { rect.set(b.x - 3f, b.y - 12f, b.x + 3f, b.y + 8f); c.drawRoundRect(rect, 3f, 3f, p) }
        for (s in sparks) { p.color = Color.argb((s[4] * 400f).toInt().coerceIn(0, 255), 255, 170, 60); c.drawCircle(s[0], s[1], 4f, p) }
        if (!dead) {
            val sx = sxPos * w; val sy = h * 0.88f
            p.color = 0xFFFF9800.toInt()
            path.reset(); path.moveTo(sx - 10f, sy + 24f); path.lineTo(sx, sy + 24f + 14f + 5f * sin(time * 30f)); path.lineTo(sx + 10f, sy + 24f); path.close(); c.drawPath(path, p)
            p.color = 0xFF90CAF9.toInt()
            path.reset(); path.moveTo(sx, sy - 30f); path.lineTo(sx + 28f, sy + 24f); path.lineTo(sx, sy + 12f); path.lineTo(sx - 28f, sy + 24f); path.close(); c.drawPath(path, p)
            p.color = 0xFF1565C0.toInt(); c.drawCircle(sx, sy, 7f, p)
        }
        hud(c, "Score $score    Lives $lives    Best $best")
        overlays(c, dead, "Game Over", "Score $score  -  Press OK to retry", "Left / Right to move  -  auto-fire")
    }
}
