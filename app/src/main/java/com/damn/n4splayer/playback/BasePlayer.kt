package com.damn.n4splayer.playback

import android.media.AudioTrack
import android.util.Log

abstract class BasePlayer(private val onEnd: () -> Unit) : IPlayer {

    private var thread: Thread? = null

    private var pause: Boolean = false

    override fun play() {
        stop()
        pause = false
        thread = Thread {
            tryLoop()
            thread = null
            onEnd()
        }.apply {
            start()
        }
    }

    private fun tryLoop() {
        try {
            loop()
        } catch (e: Exception) {
            Log.e("BasePlayer", "Exception occurred: ${e.message}", e)
        }
    }

    override fun stop() {
        thread?.apply {
            interrupt()
            join()
        }
    }

    override fun pause(pause: Boolean) {
        this.pause = pause
    }

    protected fun waitPause(track: AudioTrack) {
        if (!pause) return
        track.pause()
        try {
            while (pause) {
                Thread.sleep(100) // basic spin wait
            }
        } catch (e: InterruptedException) {
            thread?.interrupt() // set flag back
            return
        }
        track.play()
    }

    protected abstract fun loop()
}