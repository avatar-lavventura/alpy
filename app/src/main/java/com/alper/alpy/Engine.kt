package com.alper.alpy

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
private const val ICON = android.R.drawable.ic_lock_idle_alarm

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
            NotificationChannel("alerts", "Alerts (full screen)", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) },
            NotificationChannel("reminders", "Reminders (heads-up)", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel("quiet", "Reminders (silent)", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) },
        ))
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val u = i.data ?: return
        act(ctx, i.action ?: "fire", u.host!!, u.lastPathSegment!!.toInt(), i.getIntExtra("min", Store.data.value.snoozeMin))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) = Alarms.syncAll(ctx)
}

private fun broadcast(ctx: Context, action: String, kind: String, id: Int, min: Int? = null) =
    PendingIntent.getBroadcast(ctx, min ?: 0, Intent(ctx, AlarmReceiver::class.java).setAction(action)
        .setData(Uri.parse("alpy://$kind/$id")).apply { if (min != null) putExtra("min", min) }, FLAGS)

private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

/** Every state change (alarm, notification button, alert screen, UI) goes through here. */
fun act(ctx: Context, action: String, kind: String, id: Int, min: Int = Store.data.value.snoozeMin) {
    val now = System.currentTimeMillis()
    if (action in setOf("done", "check", "snooze")) {
        AlertService.stop()
        nm(ctx).cancel(id)
    }
    var ev: Fire? = null
    val r = Store.editReminder(id) {
        when (action) {
            "fire" -> it.fire(now).let { (r, e) -> ev = e; r }
            "done" -> it.done()
            "check" -> it.check()
            "uncheck" -> it.copy(done = false).rescheduled(now)
            "adjust" -> it.adjust(min * 60_000L, now)
            "snooze" -> it.copy(nagAt = now + min * 60_000L, enabled = true)
            "on" -> it.copy(enabled = true).rescheduled(now)
            "off" -> it.copy(enabled = false, nagAt = null, activeSince = null)
            else -> it
        }
    } ?: return
    Alarms.sync(ctx, r)
    when (ev) {
        Fire.PRE -> Alerts.show(ctx, "r", id, "Soon: ${r.title}", "In ${leadLabel(r.preMin).lowercase()}", r.preSound ?: r.sound, r.fullScreen, r.snoozeMin, !r.soundOn)
        Fire.MAIN, Fire.NAG -> Alerts.show(ctx, "r", id, r.title, r.notes, r.sound, r.fullScreen, r.snoozeMin, !r.soundOn)
        null -> {}
    }
}

object Alarms {
    fun set(ctx: Context, kind: String, id: Int, at: Long?) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = broadcast(ctx, "fire", kind, id)
        if (at == null) return am.cancel(pi)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), FLAGS)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        }
    }

    fun sync(ctx: Context, r: Reminder) = set(ctx, "r", r.id, r.fireAt())

    fun syncAll(ctx: Context) = Store.data.value.reminders.forEach { sync(ctx, it) }

    fun cancelAll(ctx: Context) {
        Store.data.value.reminders.forEach { set(ctx, "r", it.id, null) }
        nm(ctx).cancelAll()
    }
}

object Alerts {
    private fun action(ctx: Context, title: String, a: String, kind: String, id: Int, min: Int? = null) =
        Notification.Action.Builder(null as Icon?, title, broadcast(ctx, a, kind, id, min)).build()

    fun show(
        ctx: Context, kind: String, id: Int, title: String, text: String, sound: String?, full: Boolean,
        snooze: Int = Store.data.value.snoozeMin, quiet: Boolean = false,
    ) {
        val screen = PendingIntent.getActivity(ctx, id, Intent(ctx, AlertActivity::class.java)
            .putExtra("kind", kind).putExtra("id", id).putExtra("title", title).putExtra("text", text), FLAGS)
        val n = Notification.Builder(ctx, if (full) "alerts" else if (quiet) "quiet" else "reminders")
            .setSmallIcon(ICON).setContentTitle(title).setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM).setContentIntent(screen)
            .apply { if (full) setFullScreenIntent(screen, true) }
            .addAction(action(ctx, "Done", "done", kind, id))
            .addAction(action(ctx, "Snooze $snooze min", "snooze", kind, id, snooze))
            .build()
        if (full) {
            try {
                ctx.startForegroundService(Intent(ctx, AlertService::class.java)
                    .putExtra("id", id).putExtra("sound", sound).putExtra("quiet", quiet).putExtra("n", n))
                return
            } catch (e: Exception) {
                // Background start refused (e.g. inexact fallback alarm): plain notification below.
            }
        }
        nm(ctx).notify(id, n)
    }
}

/** Loops the alarm sound and vibration for the current full-screen alert, then goes quiet. */
class AlertService : Service() {
    private var ringtone: Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())
    private val vibrator get() = getSystemService(Vibrator::class.java)

    override fun onBind(i: Intent?) = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (i == null) return START_NOT_STICKY
        current = this
        @Suppress("DEPRECATION") val n = i.getParcelableExtra<Notification>("n")!!
        startForeground(i.getIntExtra("id", 0), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        ringtone?.stop()
        val uri = i.getStringExtra("sound")?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = if (i.getBooleanExtra("quiet", false)) null else RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            isLooping = true
            play()
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0))
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stop() }, Store.data.value.silenceMin * 60_000L)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ringtone?.stop()
        vibrator.cancel()
        handler.removeCallbacksAndMessages(null)
        if (current == this) current = null
    }

    companion object {
        private var current: AlertService? = null

        // ponytail: one shared sound for all alerts; any Done/Snooze silences whatever is ringing.
        fun stop() = current?.run {
            stopForeground(STOP_FOREGROUND_DETACH) // keep the notification so Done is still reachable
            stopSelf()
        }
    }
}
