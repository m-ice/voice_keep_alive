package com.mice.voice_keep_alive.audio

import android.media.*
import java.util.concurrent.atomic.AtomicBoolean

public class SilentAudioPlayer {

    private var audioTrack: AudioTrack? = null
    private val playing = AtomicBoolean(false)
    private var playThread: Thread? = null

    fun start() {
        if (playing.get()) return

        val sampleRate = 16000
        val minBufferSize = try {
            AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
        if (minBufferSize <= 0) return

        try {
            val attrBuilder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            } else null

            val formatBuilder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            } else null

            audioTrack = if (attrBuilder != null && formatBuilder != null) {
                AudioTrack(
                    attrBuilder,
                    formatBuilder,
                    minBufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
            } else {
                // 兼容 4.4–5.1
                AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                audioTrack?.release()
                audioTrack = null
                return
            }

            audioTrack?.play()
            playing.set(true)

            playThread = Thread {
                val silent = ByteArray(minBufferSize)
                while (playing.get()) {
                    try {
                        audioTrack?.write(silent, 0, silent.size)
                    } catch (_: Exception) {
                        break
                    }
                }
            }.apply { start() }

        } catch (e: Exception) {
            e.printStackTrace()
            audioTrack = null
            playing.set(false)
        }
    }

    fun stop() {
        playing.set(false)
        try {
            playThread?.interrupt()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        playThread = null
        audioTrack = null
    }
}
