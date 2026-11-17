package com.arun.aurora

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

// Synthesized waterfall noise (no audio file needed): filtered white noise, looped seamlessly.
object WaterSound {
    private var track: AudioTrack? = null
    private var cache: ShortArray? = null
    @Volatile private var starting = false
    @Volatile private var cancelled = false

    @Synchronized
    fun start() {
        if (track != null || starting) return
        starting = true
        cancelled = false
        Thread {
            try {
                val buf = cache ?: generate().also { cache = it }
                if (cancelled) return@Thread
                val t = AudioTrack(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(22050).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                    buf.size * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE)
                t.write(buf, 0, buf.size)
                t.setLoopPoints(0, buf.size, -1)
                t.setVolume(0.55f)
                synchronized(this) {
                    if (cancelled) { t.release() } else { track = t; t.play() }
                }
            } catch (_: Exception) {
            } finally {
                starting = false
            }
        }.start()
    }

    @Synchronized
    fun stop() {
        cancelled = true
        track?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        track = null
    }

    private fun generate(): ShortArray {
        val rate = 22050
        val m = rate * 5
        val xf = rate / 2
        val raw = ShortArray(m + xf)
        val r = java.util.Random(7)
        var l1 = 0f; var l2 = 0f; var l3 = 0f
        for (i in raw.indices) {
            val w = r.nextFloat() * 2f - 1f
            l1 += (w - l1) * 0.28f
            l2 += (w - l2) * 0.04f
            l3 += (w - l3) * 0.006f
            val mod = 0.85f + 0.15f * sin(i * 2.0 * PI / (rate * 2.5)).toFloat()
            val v = (l1 * 0.35f + l2 * 1.6f + l3 * 3.0f) * mod
            raw[i] = (v * 9000f).coerceIn(-32000f, 32000f).toInt().toShort()
        }
        val out = ShortArray(m)
        for (i in 0 until m) out[i] = raw[i]
        // blend the head with the continuation of the tail so the loop point is seamless
        for (i in 0 until xf) {
            val a = i / xf.toFloat()
            out[i] = (raw[i] * a + raw[m + i] * (1f - a)).toInt().toShort()
        }
        return out
    }
}
