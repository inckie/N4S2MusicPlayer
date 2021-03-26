package com.damn.n4splayer

import Decoder
import android.app.Activity
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import loggersoft.kotlin.streams.toBinaryBufferedStream
import java.io.InputStream


class MainActivity : AppCompatActivity() {

    private var btn: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btn = findViewById(R.id.btn_toggle_playback)
        findViewById<View>(R.id.btn_open_directory).setOnClickListener { openDirectory() }
        toReady()
    }

    private fun openDirectory() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, OPEN_DIRECTORY_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            PICK_FILE_RESULT_CODE -> if (resultCode == -1) {
                data?.data?.let {
                    contentResolver.openInputStream(it)?.let { stream: InputStream ->
                        val player = PlayerRunnable(stream)
                        Thread(player, it.lastPathSegment ?: "").start()
                    }
                }
            }
            OPEN_DIRECTORY_REQUEST_CODE -> {
                if (resultCode != Activity.RESULT_OK)
                    return
                val directoryUri = data?.data ?: return
                val documentsTree = DocumentFile.fromTreeUri(application, directoryUri) ?: return
                val childDocuments =
                    documentsTree.listFiles().filter { it.isFile && null != it.name }
                val tracks = loadTracks(childDocuments)
                tracks.firstOrNull { it.name.contains("himtech", true) }?.let {
                    playTrack(it)
                }
            }
        }
    }

    private fun playTrack(track: Track) {
        val (map, sections) = parseTrack(contentResolver, track)
        val player = InteractivePlayer(map, sections)
        player.play()
        btn?.setOnClickListener {
            player.stop()
            btn?.post { toReady() }
        }
        btn?.setText(R.string.btn_stop)
    }

    // temporary ugly class (does not handle activity lifecycle, hacky, and so on)
    inner class PlayerRunnable(private val stream: InputStream) : Runnable {

        override fun run() {
            try {
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
                        var isRunning = true
                        btn?.post {
                            btn?.setOnClickListener { isRunning = false }
                            btn?.setText(R.string.btn_stop)
                        }
                        stream.forEach {
                            it.forEach {
                                if (!isRunning) {
                                    return
                                }
                                track.write(it, 0, it.size)
                            }
                        }
                    }
                }
            } finally {
                btn?.post { toReady() }
            }
        }
    }

    private fun toReady() {
        btn?.setText(R.string.btn_play)
        btn?.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(intent, PICK_FILE_RESULT_CODE)
        }
    }

    companion object {
        const val PICK_FILE_RESULT_CODE = 1
        private const val OPEN_DIRECTORY_REQUEST_CODE = 101

        init {
            System.loadLibrary("native-lib")
        }
    }
}