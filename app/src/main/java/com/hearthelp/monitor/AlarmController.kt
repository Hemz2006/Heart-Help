package com.hearthelp.monitor

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.hearthelp.R
import com.hearthelp.ui.AlarmActivity

/** Loud looping alarm on the alarm stream (max volume, restored afterwards), vibration, full-screen screen. */
class AlarmController(private val ctx: Context) {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        ctx.getSystemService(VibratorManager::class.java).defaultVibrator
    } else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
    private var player: MediaPlayer? = null
    private var savedVolume: Int? = null

    fun start() {
        showScreen()
        if (player != null) return
        runCatching {
            savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(ctx, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0))
    }

    fun silence() {
        player?.run { runCatching { stop() }; release() }
        player = null
        savedVolume?.let { v -> runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, v, 0) } }
        savedVolume = null
        vibrator.cancel()
    }

    fun showScreen() {
        val intent = Intent(ctx, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(ctx, 1, intent, PendingIntent.FLAG_IMMUTABLE)
        Notifications.notify(ctx, Notifications.ALARM_ID,
            NotificationCompat.Builder(ctx, Notifications.CH_ALARM)
                .setSmallIcon(R.drawable.ic_heart)
                .setContentTitle("Heart-rate alert")
                .setContentText("Tap to respond")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .build())
        // If the app is in the foreground the full-screen intent only shows a heads-up; open directly too.
        runCatching { ctx.startActivity(intent) }
    }

    fun clear() {
        silence()
        Notifications.cancel(ctx, Notifications.ALARM_ID)
    }
}
