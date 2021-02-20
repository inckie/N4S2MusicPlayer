package com.damn.n4splayer

import android.media.AudioTrack
import java.io.Closeable

class CloseableAudioTrack(
    streamType: Int,
    sampleRateInHz: Int,
    channelConfig: Int,
    audioFormat: Int,
    bufferSizeInBytes: Int,
    mode: Int
) : AudioTrack(streamType, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, mode), Closeable {
    override fun close() {
        release()
    }

}