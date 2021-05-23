package com.damn.n4splayer.playback

interface IPlayer {
    fun play()
    fun stop()
    fun pause(pause: Boolean) // pause/resume
}