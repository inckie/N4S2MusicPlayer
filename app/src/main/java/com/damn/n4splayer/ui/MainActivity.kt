package com.damn.n4splayer.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.damn.n4splayer.R
import com.damn.n4splayer.Track
import com.damn.n4splayer.loadTracks
import com.damn.n4splayer.state.Speed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus


class MainActivity : AppCompatActivity() {

    private val mTracks: MutableLiveData<List<Track>> = MutableLiveData()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.btn_open_directory).setOnClickListener { openDirectory() }
        // hm, no ktx helper yet?
        findViewById<SeekBar>(R.id.seek_bar).setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) =
                EventBus.getDefault().post(Speed(progress.toFloat()))

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        sharedPreferences()
            .getString(PREF_KEY_DIR, null)?.let {
                reloadTrack(Uri.parse(it))
            }
    }

    private fun openDirectory() {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(intent, OPEN_DIRECTORY_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            OPEN_DIRECTORY_REQUEST_CODE -> {
                if (resultCode != Activity.RESULT_OK)
                    return
                val directoryUri = data?.data ?: return
                grantUriPermission(
                    packageName,
                    directoryUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                contentResolver.takePersistableUriPermission(
                    directoryUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                reloadTrack(directoryUri)
                sharedPreferences()
                    .edit()
                    .putString(PREF_KEY_DIR, directoryUri.toString())
                    .apply()
            }
        }
    }

    private fun sharedPreferences() =
        PreferenceManager.getDefaultSharedPreferences(applicationContext)

    private fun reloadTrack(directoryUri: Uri) {
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                DocumentFile.fromTreeUri(application, directoryUri)?.apply {
                    val childDocuments = listFiles().filter { it.isFile && null != it.name }
                    val tracks = loadTracks(childDocuments)
                    mTracks.postValue(tracks)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@MainActivity,
                    "Failed to load tracks: " + e.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun getTracks(): LiveData<List<Track>> = mTracks

    companion object {
        private const val OPEN_DIRECTORY_REQUEST_CODE = 101
        private const val PREF_KEY_DIR = "tracksDir"

        init {
            System.loadLibrary("native-lib")
        }
    }
}