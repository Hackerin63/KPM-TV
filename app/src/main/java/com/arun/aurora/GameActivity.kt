package com.arun.aurora

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager

class GameActivity : Activity() {
    private var game: GameView? = null
    private var menuOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val g: GameView = when (intent.getStringExtra("game")) {
            "snake" -> SnakeView(this)
            "blocks" -> BlocksView(this)
            "highway" -> CarView(this, true)
            "maze" -> MazeView(this)
            "breakout" -> BreakoutView(this)
            "g2048" -> Game2048View(this)
            "pong" -> PongView(this)
            "shooter" -> ShooterView(this)
            else -> CarView(this, false)
        }
        g.level = Prefs.gameLevel(this, g.key)
        game = g
        setContentView(g)
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN) {
            when (e.keyCode) {
                KeyEvent.KEYCODE_MEDIA_STOP -> { finish(); return true }
                KeyEvent.KEYCODE_MENU -> { showPauseMenu(); return true }
                KeyEvent.KEYCODE_BACK -> {}
                else -> if (!menuOpen && game?.handleKey(e.keyCode) == true) return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    // Back opens the pause menu: Resume / Restart / Speed -/+ / Quit
    override fun onBackPressed() { showPauseMenu() }

    private fun showPauseMenu() {
        val g = game ?: return
        if (menuOpen) return
        menuOpen = true
        g.paused = true
        g.invalidate()
        val labels = ArrayList<String>()
        labels.add("Resume"); labels.add("Restart")
        if (g.usesSpeed || g.speedLabel == "Size") {
            labels.add("${g.speedLabel} -   (now ${g.level}/5)")
            labels.add("${g.speedLabel} +")
        }
        labels.add("Quit game")
        val accent = ThemeManager.current(this).accent
        RoundDialog.show(this, "${g.gameName}  -  Paused", labels, accent, onCancel = { closeMenu() }) { i ->
            val l = labels[i]
            when {
                l == "Resume" -> closeMenu()
                l == "Restart" -> { closeMenu(); g.restartGame() }
                l.startsWith(g.speedLabel + " -") -> { setLevel(g, g.level - 1); menuOpen = false; showPauseMenu() }
                l.startsWith(g.speedLabel + " +") -> { setLevel(g, g.level + 1); menuOpen = false; showPauseMenu() }
                else -> finish()
            }
        }
    }

    private fun setLevel(g: GameView, v: Int) {
        g.level = v
        Prefs.setGameLevel(this, g.key, g.level)
    }

    private fun closeMenu() { menuOpen = false; game?.paused = false; game?.invalidate() }

    override fun onResume() { super.onResume(); game?.resume(); Overlay.apply(this) }
    override fun onPause() { game?.pause(); super.onPause() }
}
