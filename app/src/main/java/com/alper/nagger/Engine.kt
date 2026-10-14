package com.alper.nagger

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

const val TIMER_NOTIF = 100_000
private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
private const val ICON = android.R.drawable.ic_lock_idle_alarm

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
            NotificationChannel("alerts", "Alerts (full screen)", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) },
            NotificationChannel("reminders", "Reminders (heads-up)", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel("timers", "Running timers", NotificationManager.IMPORTANCE_LOW),
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
        .setData(Uri.parse("nagger://$kind/$id")).apply { if (min != null) putExtra("min", min) }, FLAGS)

private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

/** Every state change (alarm, notification button, alert screen, UI) goes through here. */
fun act(ctx: Context, action: String, kind: String, id: Int, min: Int = Store.data.value.snoozeMin) {
    val now = System.currentTimeMillis()
    if (action in setOf("done", "snooze", "silence", "restart")) {
        AlertService.stop()
        nm(ctx).cancel(id)
    }
    if (kind == "r") {
        var ev: Fire? = null
        val r = Store.editReminder(id) {
            when (action) {
                "fire" -> it.fire(now).let { (r, e) -> ev = e; r }
                "done" -> it.done()
                "snooze" -> it.copy(nagAt = now + min * 60_000L, enabled = true)
                "on" -> it.rescheduled(now)
                "off" -> it.copy(enabled = false, nagAt = null, activeSince = null)
                else -> it
            }
        } ?: return
        Alarms.sync(ctx, r)
        when (ev) {
            Fire.PRE -> Alerts.show(ctx, "r", id, "Soon: ${r.title}", "In ${r.preMin} min", r.sound, r.fullScreen)
            Fire.MAIN, Fire.NAG -> Alerts.show(ctx, "r", id, r.title, r.notes, r.sound, r.fullScreen)
            null -> {}
        }
    } else {
        var fired: Timer? = null
        val t = Store.editTimer(id) {
            when (action) {
                "fire" -> it.endAt()?.takeIf { e -> e <= now + 1000 }?.let { _ -> fired = it; it.finishStep(now) } ?: it
                "restart" -> it.reset().start(now)
                "start" -> it.start(now)
                "pause" -> it.pause(now)
                "reset" -> it.reset()
                else -> it
            }
        } ?: return
        Alarms.sync(ctx, t)
        fired?.let { f ->
            val step = if (f.steps.size > 1) " (${f.idx + 1}/${f.steps.size})" else ""
            Alerts.show(ctx, "t", id, f.name, f.steps[f.idx].msg.ifBlank { "Time's up" } + step, f.sound, true)
        }
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

    fun sync(ctx: Context, t: Timer) {
        set(ctx, "t", t.id, t.endAt())
        Alerts.timer(ctx, t)
    }

    fun syncAll(ctx: Context) = Store.data.value.let { d ->
        d.reminders.forEach { sync(ctx, it) }
        d.timers.forEach { sync(ctx, it) }
    }

    fun cancelAll(ctx: Context) = Store.data.value.let { d ->
        d.reminders.forEach { set(ctx, "r", it.id, null) }
        d.timers.forEach { set(ctx, "t", it.id, null) }
        nm(ctx).cancelAll()
    }
}

object Alerts {
    private fun action(ctx: Context, title: String, a: String, kind: String, id: Int, min: Int? = null) =
        Notification.Action.Builder(null as Icon?, title, broadcast(ctx, a, kind, id, min)).build()

    fun show(ctx: Context, kind: String, id: Int, title: String, text: String, sound: String?, full: Boolean) {
        val screen = PendingIntent.getActivity(ctx, id, Intent(ctx, AlertActivity::class.java)
            .putExtra("kind", kind).putExtra("id", id).putExtra("title", title).putExtra("text", text), FLAGS)
        val snooze = Store.data.value.snoozeMin
        val n = Notification.Builder(ctx, if (full) "alerts" else "reminders")
            .setSmallIcon(ICON).setContentTitle(title).setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM).setContentIntent(screen)
            .apply { if (full) setFullScreenIntent(screen, true) }
            .apply {
                if (kind == "r") {
                    addAction(action(ctx, "Done", "done", kind, id))
                    addAction(action(ctx, "Snooze $snooze min", "snooze", kind, id, snooze))
                } else {
                    addAction(action(ctx, "Stop", "silence", kind, id))
                    addAction(action(ctx, "Restart", "restart", kind, id))
                }
            }.build()
        if (full) {
            try {
                ctx.startForegroundService(Intent(ctx, AlertService::class.java)
                    .putExtra("id", id).putExtra("sound", sound).putExtra("n", n))
                return
            } catch (e: Exception) {
                // Background start refused (e.g. inexact fallback alarm): plain notification below.
            }
        }
        nm(ctx).notify(id, n)
    }

    fun timer(ctx: Context, t: Timer) {
        val nid = TIMER_NOTIF + t.id
        if (t.runningSince == null && t.elapsedMs == 0L) return nm(ctx).cancel(nid)
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), FLAGS)
        val b = Notification.Builder(ctx, "timers").setSmallIcon(ICON).setContentTitle(t.name)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
        if (t.steps.size > 1 && !t.countUp) b.setSubText("${t.idx + 1}/${t.steps.size}")
        val rs = t.runningSince
        if (rs != null) {
            b.setUsesChronometer(true).setShowWhen(true)
            if (t.countUp) b.setWhen(rs - t.elapsedMs) else b.setWhen(t.endAt()!!).setChronometerCountDown(true)
            b.addAction(action(ctx, "Pause", "pause", "t", t.id))
        } else {
            val ms = if (t.countUp) t.elapsedMs else t.stepMs() - t.elapsedMs
            b.setContentText("Paused at ${fmt(ms)}")
            b.addAction(action(ctx, "Resume", "start", "t", t.id))
        }
        b.addAction(action(ctx, "Reset", "reset", "t", t.id))
        nm(ctx).notify(nid, b.build())
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
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
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

fun fmt(ms: Long): String {
    if (ms < 0) return "-" + fmt(-ms)
    val s = (ms + 999) / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
