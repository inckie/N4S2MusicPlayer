package com.damn.n4splayer.playback

import android.util.Log

abstract class BasePlayer(private val onEnd: () -> Unit) : IPlayer {

    private var thread: Thread? = null

    override fun play() {
        stop()
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

    protected abstract fun loop()
}