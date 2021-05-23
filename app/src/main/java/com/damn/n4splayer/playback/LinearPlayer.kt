package com.damn.n4splayer.playback

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.damn.n4splayer.decoding.Decoder
import java.io.EOFException
import java.io.InputStream

class LinearPlayer(
    private val stream: InputStream,
    onEnd: (IPlayer) -> Unit
) : BasePlayer(onEnd) {

    @ExperimentalUnsignedTypes
    override fun loop() {
        Decoder.CloseableIterator(stream).use { stream ->
            val minBufferSize = AudioTrack.getMinBufferSize(
                Decoder.SampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            CloseableAudioTrack(
                AudioManager.STREAM_MUSIC, Decoder.SampleRate,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize, AudioTrack.MODE_STREAM
            ).use { track: CloseableAudioTrack ->
                track.play()
                stream.forEach {
                    it.forEach {
                        waitPause(track)
                        if (Thread.interrupted()) {
                            return
                        }
                        track.write(it, 0, it.size)
                    }
                }
            }
        }
    }

}