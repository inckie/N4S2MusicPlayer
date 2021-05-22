package com.damn.n4splayer.playback

abstract class BasePlayer : IPlayer {
    private var thread: Thread? = null
    override fun play() {
        stop()
        thread = Thread {
            loop()
            thread = null
        }.apply {
            start()
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