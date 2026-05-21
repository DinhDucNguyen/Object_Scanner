package com.duc.objectlanguage.utils

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class AudioPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun playMp3(bytes: ByteArray, onError: () -> Unit) {
        val audioFile = File.createTempFile("tts_", ".mp3", context.cacheDir)
        var player: MediaPlayer? = null

        try {
            audioFile.writeBytes(bytes)
            release()

            player = MediaPlayer()
            player.setDataSource(audioFile.absolutePath)
            player.prepare()
            player.setOnCompletionListener { completedPlayer ->
                completedPlayer.release()
                if (mediaPlayer === completedPlayer) mediaPlayer = null
                audioFile.delete()
            }
            player.setOnErrorListener { failedPlayer, _, _ ->
                failedPlayer.release()
                if (mediaPlayer === failedPlayer) mediaPlayer = null
                audioFile.delete()
                onError()
                true
            }
            mediaPlayer = player
            player.start()
        } catch (_: Exception) {
            player?.release()
            if (mediaPlayer === player) mediaPlayer = null
            audioFile.delete()
            onError()
        }
    }

    fun playUrl(audioUrl: String, onError: () -> Unit) {
        val cachedFile = getAudioCacheFile(audioUrl)
        if (cachedFile.exists()) {
            playFile(cachedFile, onError)
            return
        }
        Thread {
            try {
                val bytes = downloadBytes(audioUrl)
                writeCacheFile(cachedFile, bytes)
                mainHandler.post { playFile(cachedFile, onError) }
            } catch (_: Exception) {
                mainHandler.post { onError() }
            }
        }.start()
    }

    private fun getAudioCacheFile(url: String): File {
        val dir = File(context.cacheDir, "audio").apply { mkdirs() }
        return File(dir, "${url.hashCode().toUInt()}.mp3")
    }

    private fun downloadBytes(audioUrl: String): ByteArray {
        val connection = (URL(audioUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = URL_CONNECT_TIMEOUT_MS
            readTimeout = URL_READ_TIMEOUT_MS
        }
        return try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun writeCacheFile(target: File, bytes: ByteArray) {
        val temp = File.createTempFile("audio_", ".tmp", target.parentFile)
        try {
            temp.writeBytes(bytes)
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                target.writeBytes(bytes)
                temp.delete()
            }
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    private fun playFile(file: File, onError: () -> Unit) {
        var player: MediaPlayer? = null
        try {
            release()
            player = MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.prepare()
            player.setOnCompletionListener { p ->
                p.release()
                if (mediaPlayer === p) mediaPlayer = null
            }
            player.setOnErrorListener { p, _, _ ->
                p.release()
                if (mediaPlayer === p) mediaPlayer = null
                onError()
                true
            }
            mediaPlayer = player
            player.start()
        } catch (_: Exception) {
            player?.release()
            if (mediaPlayer === player) mediaPlayer = null
            onError()
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    companion object {
        private const val URL_CONNECT_TIMEOUT_MS = 5_000
        private const val URL_READ_TIMEOUT_MS = 8_000
    }
}
