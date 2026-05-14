package com.duc.objectlanguage.utils

import android.content.Context
import android.media.MediaPlayer
import java.io.File

class AudioPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

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
        var player: MediaPlayer? = null

        try {
            release()

            player = MediaPlayer()
            player.setDataSource(audioUrl)
            player.setOnPreparedListener { preparedPlayer ->
                preparedPlayer.start()
            }
            player.setOnCompletionListener { completedPlayer ->
                completedPlayer.release()
                if (mediaPlayer === completedPlayer) mediaPlayer = null
            }
            player.setOnErrorListener { failedPlayer, _, _ ->
                failedPlayer.release()
                if (mediaPlayer === failedPlayer) mediaPlayer = null
                onError()
                true
            }
            mediaPlayer = player
            player.prepareAsync()
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
}
