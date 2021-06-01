package com.damn.n4splayer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.damn.n4splayer.DocFile
import com.damn.n4splayer.R
import com.damn.n4splayer.Track
import com.damn.n4splayer.databinding.ActivityMainBinding
import com.damn.n4splayer.gps.GPSLocationTracker
import com.damn.n4splayer.loadTracks
import com.damn.n4splayer.state.Speed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus


class MainActivity : AppCompatActivity() {

    private lateinit var mBinding: ActivityMainBinding

    private val mTracks: MutableLiveData<List<Track>> = MutableLiveData()

    private val requestPermissionLauncher = createGPSPermissionLauncher {
        if (it) setGPSEnabled(true)
        else mBinding.rbInput.check(R.id.btn_debug)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        val selectDir = registerForActivityResult(
            object : ActivityResultContracts.OpenDocumentTree() {
                override fun createIntent(context: Context, input: Uri?): Intent =
                    super.createIntent(context, input)
                        .setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            },
            ActivityResultCallback { uri ->
                if (null == uri)
                    return@ActivityResultCallback
                grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                reloadTrack(uri)
                sharedPreferences()
                    .edit()
                    .putString(PREF_KEY_DIR, uri.toString())
                    .apply()
            })

        mBinding.btnOpenDirectory.setOnClickListener {
            val uri = sharedPreferences()
                .getString(PREF_KEY_DIR, null)
                ?.let { Uri.parse(it) }
            selectDir.launch(uri)
        }
        // hm, no ktx helper yet?
        mBinding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) =
                EventBus.getDefault().post(Speed(progress.toFloat()))

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        sharedPreferences()
            .getString(PREF_KEY_DIR, null)?.let {
                reloadTrack(Uri.parse(it))
            }
        mBinding.rbInput.addOnButtonCheckedListener { _, checkedId, isChecked ->
            when (checkedId) {
                R.id.btn_debug -> toggleDebug(isChecked)
                R.id.btn_gps -> toggleGps(isChecked)
            }
        }
        mBinding.rbInput.check(R.id.btn_debug)
    }

    private fun toggleGps(checked: Boolean) {
        if (!checked)
            setGPSEnabled(false)
        else {
            if (GPSLocationTracker.hasPermissions(this))
                setGPSEnabled(true)
            else
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun setGPSEnabled(enabled: Boolean) {
        // todo: set preference flag
    }

    private fun toggleDebug(checked: Boolean) {
        mBinding.seekBar.visibility = if (checked) View.VISIBLE else View.GONE
    }

    private fun sharedPreferences() =
        PreferenceManager.getDefaultSharedPreferences(applicationContext)

    private fun reloadTrack(directoryUri: Uri) {
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                DocumentFile.fromTreeUri(application, directoryUri)?.apply {
                    val childDocuments =
                        listFiles().filter { it.isFile && null != it.name }.map { DocFile(it) }
                    val tracks = loadTracks(contentResolver, childDocuments)
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
        private const val PREF_KEY_DIR = "tracksDir"
    }
}