package com.example.ui

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

object AudioSynthesizer {
    private const val TAG = "AudioSynthesizer"
    private const val SAMPLE_RATE = 22050
    
    // Global variable, easily mutable through settings
    var isSoundEnabled = true

    /**
     * Natively play nice retro tones by writing raw 16-bit PCM wave samples to an AudioTrack.
     */
    fun playTone(frequencies: List<Float>, durationMs: Int, volume: Float = 0.35f) {
        if (!isSoundEnabled) return
        
        CoroutineScope(Dispatchers.Default).launch {
            var audioTrack: AudioTrack? = null
            try {
                val numSamples = (durationMs * SAMPLE_RATE) / 1000
                val sample = DoubleArray(numSamples)
                val generatedSnd = ByteArray(2 * numSamples)

                if (frequencies.isEmpty()) return@launch

                if (frequencies.size == 1) {
                    val freq = frequencies[0].toDouble()
                    for (i in 0 until numSamples) {
                        sample[i] = sin(2 * Math.PI * i / (SAMPLE_RATE / freq))
                    }
                } else {
                    // Play a sequence of notes
                    val numSub = numSamples / frequencies.size
                    for (fIdx in frequencies.indices) {
                        val freq = frequencies[fIdx].toDouble()
                        for (i in 0 until numSub) {
                            val globalIdx = fIdx * numSub + i
                            if (globalIdx < numSamples) {
                                sample[globalIdx] = sin(2 * Math.PI * i / (SAMPLE_RATE / freq))
                            }
                        }
                    }
                }

                // Convert to signed 16-bit PCM with tiny envelope smoothing (fade-in, fade-out to prevent pops)
                var idx = 0
                for (i in 0 until numSamples) {
                    val dVal = sample[i]
                    val envelopeFactor = when {
                        i < numSamples * 0.1 -> i.toDouble() / (numSamples * 0.1) // Fade-in
                        i > numSamples * 0.85 -> (numSamples - i).toDouble() / (numSamples * 0.15) // Fade-out
                        else -> 1.0
                    }
                    val finalVal = dVal * envelopeFactor
                    val valShort = (finalVal * 32767 * volume).toInt().coerceIn(-32768, 32767).toShort()
                    
                    generatedSnd[idx++] = (valShort.toInt() and 0x00ff).toByte()
                    generatedSnd[idx++] = ((valShort.toInt() and 0xff00) ushr 8).toByte()
                }

                @Suppress("DEPRECATION")
                audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    generatedSnd.size,
                    AudioTrack.MODE_STATIC
                )
                audioTrack.write(generatedSnd, 0, generatedSnd.size)
                audioTrack.play()
                
                // Keep coroutine alive while playing
                kotlinx.coroutines.delay(durationMs + 50L)
            } catch (e: Exception) {
                Log.e(TAG, "Failed playing tone", e)
            } finally {
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (ex: Exception) {
                    // Ignore
                }
            }
        }
    }

    fun playSuccess() {
        playTone(listOf(523.25f, 659.25f), 220, volume = 0.3f) // C5 -> E5
    }

    fun playFailure() {
        playTone(listOf(220.00f, 174.61f), 300, volume = 0.4f) // A3 -> F3
    }

    fun playClick() {
        playTone(listOf(1000.00f), 35, volume = 0.2f) // Sharp snap
    }

    fun playFanfare() {
        playTone(listOf(523.25f, 659.25f, 783.99f, 1046.50f), 550, volume = 0.4f) // C5 -> E5 -> G5 -> C6
    }
}
