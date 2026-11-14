package com.pacecraft.engine

import java.time.*
import kotlin.math.*

object Cleaner {
    fun usable(r: Run) = r.distance.isFinite() && r.duration.isFinite() && r.distance >= 1000 && r.duration > 0 && r.speed in 1.6..7.0
    fun effort(r: Run) = usable(r) && !r.indoor && (r.ascent ?: 0.0) / (r.distance / 1000) <= 20
    fun same(a: Run, b: Run, zone: ZoneId): Boolean {
        if (a.day(zone) != b.day(zone)) return false
        val overlap = Duration.between(maxOf(a.start, b.start), minOf(a.end, b.end)).toMillis() / 1000.0
        return overlap > 0 && overlap / min(a.duration, b.duration) >= .90
    }
    private fun richness(r: Run) = (if (r.heartRate != null) 2 else 0) + (if (r.ascent != null) 1 else 0) + (if (r.movingDuration != null) 1 else 0)
    fun clean(runs: List<Run>, zone: ZoneId, merge: Boolean = true): List<Run> {
        val kept = mutableListOf<Run>()
        runs.filter(::usable).sortedWith(compareBy<Run> { it.start }.thenBy { it.id }).forEach { r ->
            val last = kept.lastOrNull()
            if (merge && last != null && same(last, r, zone)) {
                if (r.distance > last.distance * 1.1 || (last.distance <= r.distance * 1.1 && richness(r) > richness(last))) kept[kept.lastIndex] = r
            } else kept += r
        }
        return kept
    }
}
data class Point(val x: Double, val y: Double, val w: Double)
data class Fit(val slope: Double, val intercept: Double, val r2: Double, val residual: Double, val count: Int)
fun regression(points: List<Point>): Fit? {
    val p = points.filter { it.x.isFinite() && it.y.isFinite() && it.w.isFinite() && it.w > 0 }
    if (p.size < 2) return null
    val sw = p.sumOf { it.w }; val mx = p.sumOf { it.w * it.x } / sw; val my = p.sumOf { it.w * it.y } / sw
    val sxx = p.sumOf { it.w * (it.x - mx).pow(2) }
    if (sxx <= 1e-12) return null
    val slope = p.sumOf { it.w * (it.x - mx) * (it.y - my) } / sxx
    val intercept = my - slope * mx
    val res = p.sumOf { it.w * (it.y - (slope * it.x + intercept)).pow(2) }
    val tot = p.sumOf { it.w * (it.y - my).pow(2) }
    return Fit(slope, intercept, if (tot > 1e-12) 1 - res / tot else 1.0,
        if (abs(my) > 1e-9) sqrt(res / sw) / abs(my) else 0.0, p.size)
}
data class Effort(val date: Instant, val distance: Double, val duration: Double, val maximal: Boolean) {
    val speed get() = distance / duration
    fun weight(now: Instant) = .5.pow(max(0.0, date.ageDays(now)) / 42) * if (maximal) 1.0 else .6
}
object FitnessModel {
    fun frontier(efforts: List<Effort>) = efforts.distinct().filter { e -> efforts.none {
        it != e && it.distance >= e.distance && it.speed >= e.speed &&
            (it.distance > e.distance || it.speed > e.speed || it.date > e.date || (it.date == e.date && it.maximal && !e.maximal))
    } }.sortedBy { it.duration }
    fun extract(runs: List<Run>, profile: Profile, now: Instant, zone: ZoneId): List<Effort> {
        val hr = profile.maxHeartRate(now, zone)
        return frontier(runs.filter { Cleaner.effort(it) && it.start.ageDays(now) in 0.0..182.0 }
            .map { Effort(it.start, it.distance, it.duration, hr != null && (it.heartRate ?: 0.0) / hr >= .88) })
    }
    fun estimate(efforts: List<Effort>, now: Instant): Fitness? {
        val best = efforts.maxByOrNull { it.speed } ?: return null
        val window = efforts.filter { it.duration in 120.0..1800.0 }
        val anchor = window.maxOfOrNull { it.date } ?: best.date
        val stale = max(0.0, anchor.ageDays(now) / 7)
        val maximal = efforts.filter { it.maximal }
        val exponent = if (maximal.size >= 3 && maximal.maxOf { it.distance } / maximal.minOf { it.distance } >= 3)
            regression(maximal.map { Point(ln(it.distance), ln(it.duration), it.weight(now)) })?.slope?.coerceIn(1.01, 1.15) ?: 1.06 else 1.06
        val fit = regression(window.map { Point(it.duration, it.distance, it.weight(now)) })
        if (fit != null && fit.slope > 0 && fit.intercept in 50.0..500.0 && fit.r2 >= .95) {
            val confidence = when {
                fit.count >= 4 && fit.r2 >= .98 && stale <= 6 -> Confidence.HIGH
                fit.count >= 3 && fit.r2 >= .96 && stale <= 12 -> Confidence.MODERATE
                else -> Confidence.LOW
            }
            return Fitness(fit.slope, fit.intercept, exponent, fit.count, fit.r2, fit.residual, stale, confidence)
        }
        return Fitness(max(.5, (best.distance - 180) / best.duration), 180.0, exponent, efforts.size, 0.0, .06, stale, Confidence.LOW)
    }
}
object Demands {
    fun bias(d: Double) = if (d <= 10000) 0.0 else if (d >= MARATHON) 1.0 else ln(d / 10000) / ln(MARATHON / 10000)
    fun weekly(d: Double) = if (d > 0) 758 * d.pow(.41) else 0.0
    fun long(d: Double) = weekly(d) * (.35 + .15 * bias(d))
    fun longShare(d: Double, runs: Int) = max(.35 + .15 * bias(d), 1.15 / (1 + .8 * max(1, runs - 1)))
}
object Predictor {
    fun predict(d: Double, f: Fitness, longest: Double, weekly: Double): Prediction {
        require(d > 0 && d.isFinite())
        val base = if (d <= HALF) max(1.0, (d - f.dPrime) / f.cs)
            else (HALF - f.dPrime) / f.cs * (d / HALF).pow(f.exponent)
        val ld = max(0.0, Demands.long(d) - longest) / Demands.long(d)
        val vd = max(0.0, Demands.weekly(d) - weekly) / Demands.weekly(d)
        val raw = 1 + .14 * ld + .10 * vd
        val penalty = min(raw, 1.20)
        val caveats = buildList {
            if (ld > .25) add("Your long runs do not yet support this distance.")
            if (vd > .25) add("Weekly volume is below the demand for this race.")
            if (raw > 1.20) add("Beyond current evidence: treat this as a hypothesis, not a race prediction.")
            if (d >= MARATHON) add("Marathon outcomes depend on fuelling and race experience.")
            if (f.effortCount < 3) add("Few distinct best efforts; the estimate is uncertain.")
            if (f.staleWeeks > 8) add("The fitness evidence is more than eight weeks old.")
        }
        val sigma = min(.25, f.residual + .010 * max(0.0, f.staleWeeks) + (if (f.effortCount < 3) .030 else 0.0) + (if (d > HALF) .035 else 0.0))
        val t = base * penalty
        return Prediction(t, t * (1 - 1.28 * sigma), t * (1 + 1.28 * sigma), penalty,
            if (raw > 1.20) Confidence.LOW else f.confidence, caveats, raw > 1.20)
    }
}
object LoadModel {
    fun pace(distance: Double, duration: Double, cs: Double) = if (duration > 0 && cs > 0) duration / 3600 * (distance / duration / cs).pow(2) * 100 else 0.0
    fun trimp(duration: Double, hr: Double, rest: Double, max: Double): Double {
        if (max <= rest || duration <= 0) return 0.0
        val reserve = ((hr - rest) / (max - rest)).coerceIn(0.0, 1.0)
        return duration / 60 * reserve * .75 * exp(1.795 * reserve)
    }
    fun session(r: Run, f: Fitness, p: Profile, now: Instant, zone: ZoneId): Double {
        if (!r.indoor) return pace(r.distance, r.duration, f.cs)
        val maxHr = p.maxHeartRate(now, zone)
        return if (r.heartRate != null && p.restingHeartRate != null && maxHr != null)
            .7 * trimp(r.duration, r.heartRate, p.restingHeartRate, maxHr) else r.distance / 1000 * 6.5
    }
    fun cross(c: CrossTraining, p: Profile, now: Instant, zone: ZoneId): Double {
        val hr = p.maxHeartRate(now, zone)
        return if (c.heartRate != null && p.restingHeartRate != null && hr != null)
            .7 * trimp(c.duration, c.heartRate, p.restingHeartRate, hr) else c.duration / 3600 * if (c.strength) 30 else 40
    }
    fun rolling(runs: List<Run>, now: Instant, weeks: Int): List<Double> = (weeks - 1 downTo 0).map { offset ->
        val end = now.minusSeconds(offset * 7L * 86400); val start = end.minusSeconds(7 * 86400L)
        runs.filter { it.start > start && it.start <= end }.sumOf { it.distance }
    }
    fun calendar(runs: List<Run>, day: LocalDate, zone: ZoneId, weeks: Int): List<Double> = (weeks - 1 downTo 0).map { offset ->
        val start = day.monday().minusWeeks(offset.toLong()); val end = start.plusWeeks(1)
        runs.filter { it.day(zone) >= start && it.day(zone) < end }.sumOf { it.distance }
    }
    fun evaluate(runs: List<Run>, cross: List<CrossTraining>, f: Fitness, p: Profile, now: Instant, zone: ZoneId): Load {
        val today = now.atZone(zone).toLocalDate()
        val dayLoads = mutableMapOf<LocalDate, Double>()
        runs.forEach { dayLoads.merge(it.day(zone), session(it, f, p, now, zone), Double::plus) }
        val validCross = cross.filter { it.duration in 600.0..21600.0 && it.start <= now }
        validCross.forEach { dayLoads.merge(it.start.atZone(zone).toLocalDate(), cross(it, p, now, zone), Double::plus) }
        val series = (41 downTo 0).map { dayLoads[today.minusDays(it.toLong())] ?: 0.0 }
        val chronic = series.sum() / 42
        val acute = (0..2).map { offset -> series.subList(max(0, series.size - offset - 7), series.size - offset).sum() / 7 }.average()
        val ratio = if (chronic > 1e-6) acute / chronic else 1.0
        val complete = calendar(runs, today, zone, 7).dropLast(1)
        val base = complete.dropLast(1).takeLast(4).maxOrNull() ?: 0.0
        val ramp = if (base > 1000) (complete.last() - base) / base else 0.0
        var flag = Flag.GREEN; val reasons = mutableListOf<String>()
        fun raise(level: Flag, reason: String) { flag = maxOf(flag, level); reasons += reason }
        when { ratio >= 1.5 -> raise(Flag.RED, "Recent training load is a long way ahead of your base.")
            ratio >= 1.3 -> raise(Flag.AMBER, "Recent training load is running ahead of your base.") }
        when { ramp >= .20 -> raise(Flag.RED, "Weekly distance has jumped too far in one step.")
            ramp >= .12 -> raise(Flag.AMBER, "Weekly distance is climbing quickly.") }
        val weeks = calendar(runs, today, zone, 20).dropLast(1).reversed()
        val back = weeks.takeWhile { it >= 5000 }.size
        val away = weeks.drop(back).takeWhile { it < 5000 }.size
        val recentCross = validCross.filter { it.start.ageDays(now) <= 7 }.sumOf { cross(it, p, now, zone) }
        val total = series.takeLast(7).sum()
        return Load(acute, chronic, ratio, ramp, flag, reasons, rolling(runs, now, 5),
            if (total > 0) (recentCross / total).coerceIn(0.0, 1.0) else 0.0, away, back)
    }
}
