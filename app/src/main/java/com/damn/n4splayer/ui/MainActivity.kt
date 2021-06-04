package com.damn.n4splayer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.damn.n4splayer.DocFile
import com.damn.n4splayer.R
import com.damn.n4splayer.Track
import com.damn.n4splayer.databinding.ActivityMainBinding
import com.damn.n4splayer.gps.GPSLocationTracker
import com.damn.n4splayer.loadTracks
import com.damn.n4splayer.state.Settings
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

        val preferences = Settings.sharedPreferences(this)

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
                preferences
                    .edit()
                    .putString(Settings.KEY_TRACKS_DIR, uri.toString())
                    .apply()
            })

        mBinding.btnOpenDirectory.setOnClickListener {
            val uri = preferences
                .getString(Settings.KEY_TRACKS_DIR, null)
                ?.let { Uri.parse(it) }
            selectDir.launch(uri)
        }

        mBinding.seekBar.onProgressChanged { progress, _ ->
            EventBus.getDefault().post(Speed(progress.toFloat()))
        }

        mBinding.speedMultiplier.progress =
            (100 * preferences.getFloat(Settings.KEY_SPEED_SCALE, 1.0f)).toInt()
        mBinding.speedMultiplier.onProgressChanged { progress, _ ->
            preferences.edit().putFloat(Settings.KEY_SPEED_SCALE, 100.0f / progress.coerceAtLeast(10)).apply()
        }

        mBinding.rbInput.addOnButtonCheckedListener { _, checkedId, isChecked ->
            when (checkedId) {
                R.id.btn_debug -> toggleDebug(isChecked)
                R.id.btn_gps -> toggleGps(isChecked)
            }
        }

        mBinding.rbInput.check(
            if (preferences.getBoolean(Settings.KEY_GPS, false))
                R.id.btn_gps else R.id.btn_debug
        )

        preferences.getString(Settings.KEY_TRACKS_DIR, null)?.let {
            reloadTrack(Uri.parse(it))
        }
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
        Settings.sharedPreferences(this).edit().putBoolean(Settings.KEY_GPS, enabled).apply()
        mBinding.speedMultiplier.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun toggleDebug(checked: Boolean) {
        mBinding.seekBar.visibility = if (checked) View.VISIBLE else View.GONE
    }

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

}

