package com.damn.n4splayer.playback

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.damn.n4splayer.decoding.ADPCMDecoder
import com.damn.n4splayer.decoding.Decoder
import com.damn.n4splayer.decoding.MapDecoder
import com.damn.n4splayer.state.Speed
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe


class InteractivePlayer(
    private val map: MapDecoder.MapFile,
    private val sections: List<List<ByteArray>>,
    onEnd: (IPlayer) -> Unit
) : BasePlayer(onEnd) {

    @Volatile
    private var speed: Speed? = null

    @ExperimentalUnsignedTypes
    override fun loop() {
        EventBus.getDefault().register(this);
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
                        waitPause(track)
                        if (Thread.interrupted())
                            return
                        val bytes = ADPCMDecoder.decode(block)
                        track.write(bytes, 0, bytes.size)
                    }
                    section = map.sections[nextSectionIdx(section.section)]
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loop failed", e)
        } finally {
            EventBus.getDefault().unregister(this);
        }
    }

    @Subscribe
    fun onMessageEvent(event: Speed) {
        this.speed = event
    }

    @ExperimentalUnsignedTypes
    private fun nextSectionIdx(section: MapDecoder.MAPSectionDef): Int {
        if (section.bNumRecords <= 1)
            return section.msdRecords[0].bNextSection
        else {
            speed?.let { s: Speed ->
                val rec = section.msdRecords.firstOrNull { it.bMin <= s.speed && s.speed <= it.bMax }
                if(null != rec)
                    return rec.bNextSection
            }
            return section.msdRecords[1].bNextSection
        }
    }

    companion object {
        private const val TAG: String = "InteractivePlayer"
    }

}