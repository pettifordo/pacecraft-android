package com.pacecraft.engine

import java.time.*
import java.time.temporal.TemporalAdjusters
import kotlin.math.*

const val ENGINE_VERSION = "android-0.1.0"
const val HALF = 21097.5
const val MARATHON = 42195.0
internal fun Instant.ageDays(now: Instant) = Duration.between(this, now).toMillis() / 86400000.0
fun LocalDate.monday(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

data class Run(
    val id: String, val start: Instant, val distance: Double, val duration: Double,
    val heartRate: Double? = null, val ascent: Double? = null, val indoor: Boolean = false,
    val source: String = "Manual", val movingDuration: Double? = null,
) {
    val speed get() = if (duration > 0) distance / duration else 0.0
    val end: Instant get() = start.plusMillis((duration * 1000).toLong())
    fun day(zone: ZoneId): LocalDate = start.atZone(zone).toLocalDate()
}
data class CrossTraining(val id: String, val start: Instant, val duration: Double,
                         val strength: Boolean = false, val heartRate: Double? = null)
data class Profile(val birthYear: Int? = null, val restingHeartRate: Double? = null,
                   val observedMaxHeartRate: Double? = null, val mergeDuplicates: Boolean = true) {
    fun maxHeartRate(now: Instant, zone: ZoneId): Double? {
        val estimated = birthYear?.let { 211 - 0.64 * (now.atZone(zone).year - it).coerceAtLeast(0) }
        return listOfNotNull(estimated, observedMaxHeartRate).maxOrNull()
    }
}
data class Goal(val distance: Double = 10000.0, val raceDate: LocalDate? = null,
                val targetSeconds: Double? = null, val created: LocalDate,
                val baselineSeconds: Double? = null, val arrivedWeekly: Double? = null)
data class Constraints(val sessions: Int = 4, val intensity: Int = 3,
                       val blackout: Set<DayOfWeek> = emptySet(), val blackoutDates: Set<LocalDate> = emptySet(),
                       val longDay: DayOfWeek = DayOfWeek.SUNDAY, val maxWeekly: Double? = null,
                       val maxMinutes: Int? = null, val treadmillOnly: Boolean = false) {
    val ramp get() = listOf(.04, .05, .07, .08, .08)[intensity.coerceIn(1, 5) - 1]
    fun available(day: LocalDate) = day.dayOfWeek !in blackout && day !in blackoutDates
}
data class Feedback(val at: Instant, val soreness: Int = 0, val energy: Int = 3, val niggle: Boolean = false,
                    val note: String = "")
enum class Confidence { LOW, MODERATE, HIGH }
enum class Flag { GREEN, AMBER, RED }
enum class Phase { BASE, BUILD, PEAK, TAPER, RACE_WEEK, RECOVERY }
enum class Kind(val label: String, val low: Double, val high: Double, val quality: Boolean = false) {
    REST("Rest", 0.0, 0.0), RECOVERY("Recovery", .62, .72), EASY("Easy run", .72, .82),
    LONG("Long run", .72, .82), STEADY("Steady run", .85, .90, true),
    MARATHON_PACE("Marathon pace", .88, .94, true), THRESHOLD("Threshold", .97, 1.03, true),
    INTERVALS("VO₂ intervals", 1.05, 1.15, true), OPENERS("Race openers", 1.15, 1.28), RACE("Race", .95, 1.10)
}
data class Step(val label: String, val seconds: Double, val distance: Double, val kind: Kind)
data class Session(val id: String, val day: LocalDate, val kind: Kind, val phase: Phase,
                   val steps: List<Step>, val weeklyTarget: Double, val reasons: List<String>) {
    val distance get() = steps.sumOf { it.distance }
    val seconds get() = steps.sumOf { it.seconds }
}
data class SessionRecord(val session: Session, val skipped: Boolean = false, val runId: String? = null)
data class Sufficiency(val count: Int, val weeks: Double, val distance: Double) {
    val sufficient get() = count >= 8 && weeks >= 6 && distance >= 40000
}
data class Fitness(val cs: Double, val dPrime: Double, val exponent: Double, val effortCount: Int,
                   val r2: Double, val residual: Double, val staleWeeks: Double, val confidence: Confidence)
data class Load(val acute: Double, val chronic: Double, val ratio: Double, val ramp: Double,
                val flag: Flag, val reasons: List<String>, val weekly: List<Double>,
                val crossShare: Double, val layoffWeeks: Int, val weeksBack: Int) {
    val balance get() = chronic - acute
}
data class Prediction(val seconds: Double, val low: Double, val high: Double, val penalty: Double,
                      val confidence: Confidence, val caveats: List<String>, val beyondEvidence: Boolean)
data class Result(val runs: List<Run>, val sufficiency: Sufficiency, val fitness: Fitness? = null,
                  val load: Load? = null, val prediction: Prediction? = null, val next: Session? = null,
                  val comments: List<String> = emptyList())
object Display {
    fun time(seconds: Double): String {
        val s = seconds.roundToInt().coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
               else "%d:%02d".format(s / 60, s % 60)
    }
    fun pace(speed: Double, imperial: Boolean = false) = if (speed > 0) time((if (imperial) 1609.344 else 1000.0) / speed) else "—"
    fun distance(metres: Double, imperial: Boolean = false): String = "%.1f".format(java.util.Locale.UK, metres / if (imperial) 1609.344 else 1000.0)
}
