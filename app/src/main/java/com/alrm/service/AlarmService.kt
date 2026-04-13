package com.alrm.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Vibrator
import com.alrm.R
import com.alrm.alarm.AlarmDatabase
import com.alrm.receiver.AlarmReceiver
import com.alrm.ui.AlarmFireActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmService : Service() {

    companion object {
        const val CHANNEL_ID = "alrm_alarm_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_DISMISS = "com.alrm.ACTION_DISMISS"
        private const val MAX_DURATION_MS = 5 * 60 * 1000L
        private const val API_LOLLIPOP = 21
        private const val API_M = 23
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var alarmId: Int = -1
    private val autoDismiss = Runnable { stopAlarm() }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) { stopAlarm(); return START_NOT_STICKY }

        alarmId = intent?.getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1) ?: -1
        if (alarmId == -1) { stopSelf(); return START_NOT_STICKY }

        // Must call startForeground() immediately (within 5 s of startForegroundService()).
        // Use a placeholder notification; update it once the DB query returns.
        startForeground(NOTIFICATION_ID, buildNotification(""))

        CoroutineScope(Dispatchers.IO).launch {
            val alarm = AlarmDatabase.getInstance(this@AlarmService)
                .getAlarmDao().getAlarmById(alarmId) ?: run { stopSelf(); return@launch }

            // Refresh notification with real label and full-screen intent.
            // The full-screen intent causes AlarmFireActivity to appear automatically —
            // no direct startActivity() needed (and it would be blocked on API 29+).
            @Suppress("DEPRECATION")
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(alarm.label))

            playRingtone(alarm.ringtoneUri, alarm.volume)
            if (alarm.vibrate) startVibration()
            handler.postDelayed(autoDismiss, MAX_DURATION_MS)
        }
        return START_STICKY
    }

    private fun playRingtone(uriString: String, volume: Int) {
        val uri: Uri = if (uriString.isNotEmpty()) Uri.parse(uriString)
        else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        mediaPlayer = MediaPlayer().apply {
            if (Build.VERSION.SDK_INT >= API_LOLLIPOP) {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                setAudioStreamType(AudioManager.STREAM_ALARM)
            }
            setDataSource(this@AlarmService, uri)
            isLooping = true
            if (volume >= 0) { val v = volume / 100f; setVolume(v, v) }
            prepare()
            start()
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 500, 500, 500, 500, 500, 1000)
        vibrator?.vibrate(pattern, 0)
    }

    @Suppress("DEPRECATION")
    fun stopAlarm() {
        handler.removeCallbacks(autoDismiss)
        mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null
        vibrator?.cancel(); vibrator = null
        stopForeground(true)
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(label: String): Notification {
        val fullScreenIntent = Intent(this, AlarmFireActivity::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val flags = if (Build.VERSION.SDK_INT >= API_M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val fullScreenPi = PendingIntent.getActivity(this, alarmId, fullScreenIntent, flags)

        val dismissIntent = Intent(this, AlarmService::class.java).apply { action = ACTION_DISMISS }
        val dismissPi = PendingIntent.getService(this, 0, dismissIntent, flags)

        return Notification.Builder(this)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(if (label.isNotEmpty()) label else getString(R.string.alarm_ringing))
            .setContentText(getString(R.string.tap_to_dismiss))
            .setFullScreenIntent(fullScreenPi, true)
            .addAction(R.drawable.ic_dismiss, getString(R.string.dismiss), dismissPi)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_ALARM)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { stopAlarm(); super.onDestroy() }
}
