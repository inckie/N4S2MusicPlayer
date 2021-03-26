package com.damn.n4splayer

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

class InteractivePlayer(
    private val map: MapDecoder.MapFile,
    private val sections: List<List<ByteArray>>
) {

    private var thread: Thread? = null

    fun play() {
        stop()
        thread = Thread {
            loop()
            thread = null
        }.apply {
            start()
        }
    }

    fun stop() {
        thread?.apply {
            interrupt()
            join()
        }
    }

    private fun loop() {
        try {
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
                var section = map.sections[map.startSection]
                while (!Thread.interrupted()) {
                    for (block in sections[map.sections.indexOf(section)]) {
                        if(Thread.interrupted())
                            return
                        val bytes = ADPCMDecoder.decode(block)
                        track.write(bytes, 0, bytes.size)
                    }
                    section = map.sections[nextSectionIdx(section)]
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loop failed", e)
        }
    }

    private fun nextSectionIdx(section: MapDecoder.Section): Int =
        section.section.msdRecords[1].bNextSection

    companion object {
        private const val TAG: String = "InteractivePlayer"
    }

}