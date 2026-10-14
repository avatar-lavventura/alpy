package com.alper.nagger

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Serializable
enum class Every(val unit: ChronoUnit?) {
    ONCE(null), MINUTES(ChronoUnit.MINUTES), HOURS(ChronoUnit.HOURS), DAYS(ChronoUnit.DAYS),
    WEEKS(ChronoUnit.WEEKS), MONTHS(ChronoUnit.MONTHS), YEARS(ChronoUnit.YEARS)
}

enum class Fire { PRE, MAIN, NAG }

private const val MIN = 60_000L

@Serializable
data class Reminder(
    val id: Int = 0,
    val title: String = "",
    val notes: String = "",
    val category: String = "",
    val sound: String? = null,
    val fullScreen: Boolean = true,
    val enabled: Boolean = true,
    val start: Long = 0,                 // first occurrence, epoch ms
    val rule: Every = Every.ONCE,
    val every: Int = 1,
    val weekdays: Set<Int> = emptySet(), // 1=Mon..7=Sun, empty = any day
    val windowFrom: Int? = null,         // minute of day; DayMinder window for MINUTES/HOURS
    val windowTo: Int? = null,
    val preMin: Int = 0,
    val nag: Boolean = false,
    val nagEveryMin: Int = 5,
    val nagTimes: Int = 0,               // 0 = until done
    val nagForMin: Int = 0,              // 0 = until done
    // runtime state
    val nextAt: Long? = null,            // next main occurrence
    val preFired: Boolean = false,       // pre-alarm for nextAt already shown
    val nagAt: Long? = null,             // next nag or snooze re-alert
    val nagCount: Int = 0,
    val activeSince: Long? = null,       // fired and waiting for Done
)

/** First occurrence strictly after [after], or null when there is none. */
fun Reminder.occurrenceAfter(after: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
    val s = LocalDateTime.ofInstant(Instant.ofEpochMilli(start), zone).withSecond(0).withNano(0)
    val a = LocalDateTime.ofInstant(Instant.ofEpochMilli(after), zone)
    fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    fun dayOk(t: LocalDateTime) = weekdays.isEmpty() || t.dayOfWeek.value in weekdays
    val unit = rule.unit ?: return if (s > a) ms(s) else null
    val n = every.coerceAtLeast(1).toLong()

    if (windowFrom != null && windowTo != null && rule in setOf(Every.MINUTES, Every.HOURS)) {
        val step = Duration.of(n, unit)
        var d = if (a.toLocalDate() > s.toLocalDate()) a.toLocalDate() else s.toLocalDate()
        for (i in 0..7) {
            if (dayOk(d.atStartOfDay())) {
                var t = d.atTime(windowFrom / 60, windowFrom % 60)
                val end = d.atTime(windowTo / 60, windowTo % 60)
                while (!t.isAfter(end)) {
                    if (t > a && t >= s) return ms(t)
                    t += step
                }
            }
            d = d.plusDays(1)
        }
        return null
    }

    // Step from the anchor (not from the previous hit) so months keep their day and nothing drifts.
    var k = maxOf(0L, unit.between(s, a) / n)
    repeat(20_000) {
        val t = s.plus(k * n, unit)
        if (t > a && dayOk(t)) return ms(t)
        k++
    }
    return null
}

/** Next time this reminder needs an alarm. */
fun Reminder.fireAt(): Long? = if (!enabled) null else listOfNotNull(
    nextAt?.takeIf { preMin > 0 && !preFired }?.minus(preMin * MIN), nagAt, nextAt
).minOrNull()

/** Handles the event due at [now]; returns the new state and what to show. */
fun Reminder.fire(now: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Reminder, Fire?> {
    val na = nextAt
    val nagDue = nagAt?.let { it <= now } == true
    val (r, ev) = when {
        na != null && na <= now -> copy(
            nextAt = occurrenceAfter(maxOf(na, now), zone), preFired = false,
            activeSince = now, nagCount = 0, nagAt = if (nag) now + nagEveryMin * MIN else null,
        ) to Fire.MAIN
        nagDue -> {
            val count = nagCount + 1
            val stop = !nag || (nagTimes > 0 && count >= nagTimes) ||
                (nagForMin > 0 && now - (activeSince ?: now) >= nagForMin * MIN)
            copy(nagCount = count, nagAt = if (stop) null else now + nagEveryMin * MIN) to Fire.NAG
        }
        na != null && preMin > 0 && !preFired && na - preMin * MIN <= now -> copy(preFired = true) to Fire.PRE
        else -> this to null
    }
    return r.copy(enabled = r.nextAt != null || r.nagAt != null) to ev
}

fun Reminder.done() = copy(nagAt = null, nagCount = 0, activeSince = null, enabled = nextAt != null)

fun Reminder.rescheduled(now: Long) = copy(
    nextAt = occurrenceAfter(now), preFired = false, nagAt = null, nagCount = 0, activeSince = null,
).let { it.copy(enabled = it.nextAt != null) }

@Serializable
data class Step(val sec: Long = 300, val msg: String = "")

@Serializable
data class Timer(
    val id: Int = 0,
    val name: String = "",
    val category: String = "",
    val sound: String? = null,
    val steps: List<Step> = listOf(Step()),
    val gapSec: Long = 0,
    val countUp: Boolean = false,
    // runtime state
    val idx: Int = 0,
    val runningSince: Long? = null,      // may be in the future during a queue gap
    val elapsedMs: Long = 0,
)

fun Timer.stepMs() = steps[idx].sec * 1000
fun Timer.elapsed(now: Long) = elapsedMs + (runningSince?.let { now - it } ?: 0)
fun Timer.endAt(): Long? = if (countUp) null else runningSince?.let { it + stepMs() - elapsedMs }
fun Timer.start(now: Long) = if (runningSince != null) this else copy(runningSince = now)
fun Timer.pause(now: Long) = copy(elapsedMs = elapsed(now), runningSince = null)
fun Timer.reset() = copy(idx = 0, runningSince = null, elapsedMs = 0)
fun Timer.finishStep(now: Long) =
    if (idx + 1 < steps.size) copy(idx = idx + 1, elapsedMs = 0, runningSince = now + gapSec * 1000) else reset()

@Serializable
data class Data(
    val nextId: Int = 1,
    val reminders: List<Reminder> = emptyList(),
    val timers: List<Timer> = emptyList(),
    val snoozeMin: Int = 10,
    val silenceMin: Int = 5,
)
