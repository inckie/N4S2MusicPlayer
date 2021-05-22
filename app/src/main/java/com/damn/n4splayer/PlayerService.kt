package com.damn.n4splayer

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.damn.n4splayer.playback.InteractivePlayer
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
        val (map, sections) = parseTrack(contentResolver, track)
        player?.stop()
        player = InteractivePlayer(map, sections).apply {
            play()
        }

        return START_STICKY
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
        )
        defaultChannel.setShowBadge(true)
        defaultChannel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        NotificationManagerCompat.from(this).createNotificationChannel(defaultChannel)
    }

    private fun buildNotification(name: String): Notification {
        val builder = NotificationCompat.Builder(this, sCHANNEL_PLAYER).apply {
            setOngoing(true)
            setContentTitle(getString(R.string.app_name))
            setContentText(name)
            setSmallIcon(R.mipmap.ic_launcher)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setOnlyAlertOnce(true)
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

    class LocalBinder : Binder() {
        val service: LocalBinder
            get() = this@LocalBinder
    }

    private var player: InteractivePlayer? = null
    private val mBinder: IBinder = LocalBinder()

    override fun onBind(intent: Intent): IBinder = mBinder

    companion object {

        fun play(context: Context, track: Track) {
            context.startService(
                Intent(context, PlayerService::class.java).putExtra(
                    ARG_TRACK,
                    track
                )
            )
        }

        private const val NOTIFICATION_ID = 1
        private const val sCHANNEL_PLAYER = "CHANNEL_DEFAULT"
        private const val CMD_NAME = "CMD_NAME"
        private const val CMD_STOP = "CMD_STOP"

        private const val ARG_TRACK = "ARG_TRACK"
    }
}