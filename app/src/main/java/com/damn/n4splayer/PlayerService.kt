package com.damn.n4splayer

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.damn.n4splayer.playback.IPlayer
import com.damn.n4splayer.playback.InteractivePlayer
import com.damn.n4splayer.playback.LinearPlayer
import com.damn.n4splayer.ui.MainActivity


class PlayerService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (null != intent) {
            val cmd = intent.getStringExtra(CMD_NAME)
            if (null != cmd) {
                if (cmd == CMD_STOP) stopSelf()
                return START_NOT_STICKY
            }
        }

        val track = intent?.getParcelableExtra<Track>(ARG_TRACK)
        if (null == track) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification(track.name))
        tryPlay(track)
        return START_STICKY
    }

    private fun tryPlay(track: Track) {
        player?.stop()
        player = null
        try {
            player = when (track.map) {
                null -> LinearPlayer(contentResolver.openInputStream(track.track)!!) { stopSelf() }
                else -> {
                    val (map, sections) = parseTrack(contentResolver, track)
                    InteractivePlayer(map, sections) { stopSelf() }
                }
            }.apply {
                play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play ${track.name}", e)
            Toast.makeText(this, "Failed to play ${track.name}: ${e.message}", Toast.LENGTH_LONG)
                .show()
            stopSelf()
        }
    }

    override fun onDestroy() {
        player?.stop()
        super.onDestroy()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val defaultChannel = NotificationChannel(
            sCHANNEL_PLAYER,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setShowBadge(true)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        NotificationManagerCompat.from(this).createNotificationChannel(defaultChannel)
    }

    private fun buildNotification(name: String): Notification {
        val builder = NotificationCompat.Builder(this, sCHANNEL_PLAYER).apply {
            setOngoing(true)
            setContentTitle(name)
            setSmallIcon(R.mipmap.ic_launcher)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setOnlyAlertOnce(true)
            setVibrate(null)
        }

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val contentIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.setContentIntent(contentIntent)

        val stopIntent = PendingIntent.getService(
            this,
            R.string.btn_stop,
            Intent(this, PlayerService::class.java).putExtra(CMD_NAME, CMD_STOP),
            0
        )
        builder.addAction(R.drawable.ic_baseline_stop_24, getString(R.string.btn_stop), stopIntent)
        return builder.build()
    }

    inner class LocalBinder : Binder() {
        val service: PlayerService = this@PlayerService
    }

    private var player: IPlayer? = null
    private val binder: IBinder = LocalBinder()

    override fun onBind(intent: Intent): IBinder = binder

    companion object {

        fun play(context: Context, track: Track) {
            context.startService(
                Intent(context, PlayerService::class.java).putExtra(
                    ARG_TRACK,
                    track
                )
            )
        }

        private const val TAG = "PlayerService"

        private const val NOTIFICATION_ID = 1
        private const val sCHANNEL_PLAYER = "CHANNEL_DEFAULT"
        private const val CMD_NAME = "CMD_NAME"
        private const val CMD_STOP = "CMD_STOP"

        private const val ARG_TRACK = "ARG_TRACK"
    }
}