package com.damn.n4splayer

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.*
import androidx.media.session.MediaButtonReceiver
import com.damn.n4splayer.gps.GPSLocationTracker
import com.damn.n4splayer.playback.IPlayer
import com.damn.n4splayer.playback.InteractivePlayer
import com.damn.n4splayer.playback.LinearPlayer
import com.damn.n4splayer.state.Settings
import com.damn.n4splayer.state.Speed
import com.damn.n4splayer.ui.MainActivity
import org.greenrobot.eventbus.EventBus
import java.io.IOException


class PlayerService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreate() {
        super.onCreate()
        createChannels()
        mediaSession = MediaSessionCompat(this, TAG)
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onStop() {
                super.onStop()
                stopSelf()
            }

            override fun onPause() {
                super.onPause()
                player?.pause(true)
                notifyState(false)
            }

            override fun onPlay() {
                super.onPlay()
                player?.pause(false)
                notifyState(true)
            }

            private fun notifyState(playing: Boolean) {
                mediaSession.setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setState(
                            if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                            0,
                            1.0f
                        )
                        .setActions(PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PLAY_PAUSE)
                        .build()
                )
                notification.apply {
                    clearActions()
                    addAction(
                        action(
                            R.drawable.ic_baseline_stop_24,
                            R.string.btn_stop,
                            PlaybackStateCompat.ACTION_STOP
                        )
                    )
                    addAction(
                        action(
                            if (playing) R.drawable.ic_baseline_pause_24 else R.drawable.ic_baseline_play_arrow_24,
                            if (playing) R.string.btn_pause else R.string.btn_play,
                            PlaybackStateCompat.ACTION_PLAY_PAUSE
                        )
                    )
                }
                NotificationManagerCompat.from(this@PlayerService)
                    .notify(NOTIFICATION_ID, notification.build())
            }
        })
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )

        GPSTracker = GPSLocationTracker(this,
            object : GPSLocationTracker.IListener {
                override fun positionUpdated(location: Location) =
                    EventBus.getDefault().post(Speed(location.speed * multiplier * MS_TO_KH))
            })

        Settings.sharedPreferences(this).apply {
            registerOnSharedPreferenceChangeListener(this@PlayerService)
            multiplier = getFloat(Settings.KEY_SPEED_SCALE, multiplier)
            toggleGPS(getBoolean(Settings.KEY_GPS, false))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (null != MediaButtonReceiver.handleIntent(mediaSession, intent))
            return START_STICKY
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
        notification = buildNotification(track)
        startForeground(NOTIFICATION_ID, notification.build())
        tryPlay(track)
        return START_STICKY
    }

    private fun tryPlay(track: Track) {
        player?.let {
            player = null
            it.stop()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(afChangeListener)
                .setAcceptsDelayedFocusGain(false) // todo: support it
                .setAudioAttributes(attrs)
                .build()
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val result = am.requestAudioFocus(audioFocusRequest)
            if (AudioManager.AUDIOFOCUS_REQUEST_GRANTED != result) {
                return // todo
            }
        }

        try {
            player = when (track.map) {
                null -> LinearPlayer(contentResolver.openInputStream(track.track)!!) { p -> if (p == player) stopSelf() }
                else -> {
                    val (map, sections) = parseTrack(contentResolver, track)
                    InteractivePlayer(map, sections) { p -> if (p == player) stopSelf() }
                }
            }.apply {
                play()
            }

            val meta = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.name)

            track.trackInfo?.let {
                // looks like it can not resolve content urls on its own, so we load the bitmaps
                loadBitmap(it.icon)?.let {
                    meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                }
            }
            mediaSession.setMetadata(meta.build())
            mediaSession.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                    .setActions(PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PLAY_PAUSE)
                    .build()
            )
            mediaSession.isActive = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play ${track.name}", e)
            Toast.makeText(this, "Failed to play ${track.name}: ${e.message}", Toast.LENGTH_LONG)
                .show()
            stopSelf()
        }
    }

    private fun loadBitmap(uri: Uri?): Bitmap? {
        if (null == uri) return null
        return try {
            contentResolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: IOException) {
            null
        }
    }

    override fun onDestroy() {
        Settings.sharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this)
        player?.stop()
        super.onDestroy()
        mediaSession.release()
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

    private fun buildNotification(track: Track): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(this, sCHANNEL_PLAYER).apply {
            setOngoing(true)
            setContentTitle(track.name)
            setSmallIcon(R.mipmap.ic_launcher)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setOnlyAlertOnce(true)
            setVibrate(null)
            setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this@PlayerService,
                    PlaybackStateCompat.ACTION_STOP
                )
            )
            addAction(
                action(
                    R.drawable.ic_baseline_stop_24,
                    R.string.btn_stop,
                    PlaybackStateCompat.ACTION_STOP
                )
            )
            addAction(
                action(
                    R.drawable.ic_baseline_pause_24,
                    R.string.btn_pause,
                    PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
            )
            setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
        }

        track.trackInfo?.let {
            loadBitmap(it.icon)?.let {
                builder.setLargeIcon(it)
            }
        }

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val contentIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.setContentIntent(contentIntent)
        return builder
    }

    private fun action(@DrawableRes icon: Int, @StringRes name: Int, action: Long) =
        NotificationCompat.Action(
            icon, getString(name),
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, action)
        )

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String
    ) {
        sharedPreferences.apply {
            when (key) {
                Settings.KEY_GPS -> toggleGPS(getBoolean(Settings.KEY_GPS, false))
                Settings.KEY_SPEED_SCALE -> {
                    multiplier = getFloat(Settings.KEY_SPEED_SCALE, multiplier)
                    GPSTracker.lastLocation?.let {
                        EventBus.getDefault().post(Speed(it.speed * multiplier * MS_TO_KH))
                    }
                }
            }
        }
    }

    private fun toggleGPS(gps: Boolean) {
        if (gps) GPSTracker.start()
        else GPSTracker.stop()
    }

    inner class LocalBinder : Binder() {
        val service: PlayerService = this@PlayerService
    }

    private lateinit var notification: NotificationCompat.Builder
    private lateinit var mediaSession: MediaSessionCompat
    private var player: IPlayer? = null

    private val afChangeListener: OnAudioFocusChangeListener =
        // todo: check if it was playing before resuming
        OnAudioFocusChangeListener { focusChange -> player?.pause(focusChange != AudioManager.AUDIOFOCUS_GAIN) }

    private lateinit var GPSTracker: GPSLocationTracker

    private var multiplier: Float = 1.0f

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

        private const val MS_TO_KH = 3.6f
    }

}