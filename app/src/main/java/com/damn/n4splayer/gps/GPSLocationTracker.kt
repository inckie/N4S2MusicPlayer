package com.damn.n4splayer.gps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class GPSLocationTracker(
    private val mContext: Context,
    private val mListener: IListener
) :
    LocationListener {

    interface IListener {
        fun positionUpdated(location: Location)
    }

    private val mLM = mContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
    var lastLocation: Location? = null
        private set
    private var isActive = false

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (!hasPermissions(mContext)) return false
        try {
            mLM?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_MIN_TIME,
                UPDATE_MIN_DIST,
                this
            )
            isActive = true
            return true
        } catch (e: Exception) {
            Log.e(TAG, "AndroidLocationTracker failed to start", e)
        }
        return false
    }

    fun stop() {
        try {
            mLM?.removeUpdates(this)
        } catch (ignored: SecurityException) {
        }
        isActive = false
    }

    override fun onLocationChanged(location: Location) {
        lastLocation = location
        mListener.positionUpdated(location)
    }

    companion object {
        private const val TAG = "AndroidLocationTracker"
        private const val UPDATE_MIN_TIME = 0L
        private const val UPDATE_MIN_DIST = 0.0f

        @JvmStatic
        fun hasPermissions(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) return false
            }
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

    }

}