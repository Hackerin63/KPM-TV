package com.arun.aurora

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

// Simple book shelf: browse storage / USB and open PDF, DOCX or TXT files
class ReaderLibraryActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var pathLabel: TextView
    private var dir: File? = null
    private var accent = 0xFF6EA8FF.toInt()
    private val exts = setOf("pdf", "docx", "txt")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accent = ThemeManager.current(this).accent
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0F1A.toInt())
            setPadding(Ui.dp(this@ReaderLibraryActivity, 48), Ui.dp(this@ReaderLibraryActivity, 24),
                Ui.dp(this@ReaderLibraryActivity, 48), Ui.dp(this@ReaderLibraryActivity, 16))
        }
        root.addView(TextView(this).apply { text = "Books  -  PDF, Word (.docx), Text"; textSize = 26f; setTextColor(Color.WHITE) })
        pathLabel = TextView(this).apply {
            textSize = 13f; setTextColor(0xFFB8C2DD.toInt())
            setPadding(0, Ui.dp(this@ReaderLibraryActivity, 4), 0, Ui.dp(this@ReaderLibraryActivity, 8))
        }
        root.addView(pathLabel)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sv = ScrollView(this)
        sv.addView(list)
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 7)
        }
        show(null)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        show(dir)
    }

    override fun onResume() { super.onResume(); Overlay.apply(this) }

    private fun roots(): List<Pair<String, File>> {
        val out = ArrayList<Pair<String, File>>()
        val internal = Environment.getExternalStorageDirectory()
        out.add("Internal storage" to internal)
        for (n in listOf("Download", "Documents", "Books")) {
            val f = File(internal, n)
            if (f.isDirectory) out.add(n to f)
        }
        File("/storage").listFiles()?.forEach { f ->
            if (f.isDirectory && f.name != "emulated" && f.name != "self" && f.canRead()) out.add("USB / SD  (${f.name})" to f)
        }
        return out
    }

    private fun add(label: String, onClick: () -> Unit) {
        list.addView(Ui.button(this, label, accent, onClick), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = Ui.dp(this@ReaderLibraryActivity, 8) })
    }

    private fun show(d: File?) {
        dir = d
        list.removeAllViews()
        if (d == null) {
            pathLabel.text = "Choose a location"
            for ((name, f) in roots()) add(name) { show(f) }
        } else {
            pathLabel.text = d.absolutePath
            add("..  (back)") { up() }
            val items = d.listFiles()?.filter { !it.name.startsWith(".") } ?: emptyList()
            val dirs = items.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
            val files = items.filter { it.isFile && it.extension.lowercase() in exts }.sortedBy { it.name.lowercase() }
            for (f in dirs) add("[folder]  ${f.name}") { show(f) }
            for (f in files) add(f.name) {
                startActivity(Intent(this, ReaderActivity::class.java).putExtra("path", f.absolutePath))
            }
            if (dirs.isEmpty() && files.isEmpty()) {
                list.addView(TextView(this).apply {
                    text = "No PDF / DOCX / TXT files here.\nIf this folder looks empty, allow storage access:\nadb shell pm grant com.arun.aurora android.permission.READ_EXTERNAL_STORAGE"
                    setTextColor(0xFFB8C2DD.toInt()); textSize = 14f
                })
            }
        }
        list.getChildAt(0)?.requestFocus()
    }

    private fun up() {
        val d = dir ?: return
        val rs = roots().map { it.second.absolutePath }
        if (d.absolutePath in rs) show(null) else show(d.parentFile)
    }

    override fun onBackPressed() { if (dir != null) up() else super.onBackPressed() }
}
