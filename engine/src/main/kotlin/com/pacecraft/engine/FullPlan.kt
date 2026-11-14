package com.pacecraft.engine

import java.time.*
import java.time.temporal.ChronoUnit

/** A forecast, built by assuming each prescription is completed. Never a run-history cache. */
data class FullPlan(
    val from: LocalDate, val through: LocalDate, val sessions: List<Session>,
    val weeks: List<PlanWeek>, val capped: Boolean, val hasFutureRace: Boolean,
)
data class PlanWeek(
    val start: LocalDate, val sessions: List<Session>, val completed: List<Run>,
    val phase: Phase, val downWeek: Boolean, val partial: Boolean,
) {
    val completedDistance get() = completed.sumOf { it.distance }
    val plannedDistance get() = sessions.sumOf { it.distance }
    val totalDistance get() = completedDistance + plannedDistance
    val trainingDistance get() = completedDistance + sessions.filter { it.kind != Kind.RACE }.sumOf { it.distance }
    val longestTrainingRun get() = sessions.filter { it.kind != Kind.RACE }.maxOfOrNull { it.distance } ?: 0.0
    val runCount get() = sessions.count { it.kind != Kind.REST }
}
object FullPlanProjector {
    const val MAXIMUM_DAYS = 210 // Same thirty-week bound as the iPhone full-plan view.
    const val OPEN_ENDED_DAYS = 14

    /** Keeps current fitness fixed; updates fatigue/budgets using assumed completions.
     * All virtual runs and records stay inside this call. Inputs are never mutated.
     */
    fun project(
        samples: List<Run>, goal: Goal, constraints: Constraints, profile: Profile,
        fitness: Fitness, initialLoad: Load, records: List<SessionRecord>,
        feedback: List<Feedback> = emptyList(), crossTraining: List<CrossTraining> = emptyList(),
        now: Instant, zone: ZoneId,
    ): FullPlan {
        val today = now.atZone(zone).toLocalDate()
        val futureRace = goal.raceDate?.takeIf { it >= today }
        val cap = today.plusDays(MAXIMUM_DAYS - 1L)
        val through = futureRace?.let { minOf(it, cap) } ?: today.plusDays(OPEN_ENDED_DAYS - 1L)
        val real = samples.filter { it.start <= now }.toList()
        val virtualSamples = real.toMutableList()
        val virtualHistory = CoachingEngine.reconcile(records, real, zone).toMutableList()
        val projected = mutableListOf<Session>()
        var cursor = now
        var load = initialLoad
        for (iteration in 0 until MAXIMUM_DAYS) {
            if (cursor.atZone(zone).toLocalDate() > through) break
            val next = Planner.next(virtualSamples,goal,constraints,fitness,load,virtualHistory,feedback,cursor,zone)
            // The planner can jump across unavailable days and full weeks. Check its chosen date.
            if (next.day > through || (projected.lastOrNull()?.day?.let { next.day <= it } == true)) break
            projected += next
            if (next.kind == Kind.REST) virtualHistory += SessionRecord(next)
            else {
                val assumed = Run("projection:${next.id}",next.day.atStartOfDay(zone).toInstant(),
                    next.distance,next.seconds,indoor=constraints.treadmillOnly,source="Projection")
                virtualSamples += assumed
                virtualHistory += SessionRecord(next,runId=assumed.id)
            }
            cursor = next.day.plusDays(1).atStartOfDay(zone).toInstant()
            load = LoadModel.evaluate(virtualSamples,crossTraining,fitness,profile,cursor,zone)
        }
        val weeks = mutableListOf<PlanWeek>()
        var monday = today.monday()
        while (monday <= through) {
            val end = monday.plusWeeks(1)
            val inWeek = projected.filter { it.day >= monday && it.day < end }
            val completed = real.filter { it.day(zone) >= monday && it.day(zone) < end }
            val phase = Phases.phase(goal,monday)
            weeks += PlanWeek(monday,inWeek,completed,phase,
                Phases.down(goal,monday) && phase !in setOf(Phase.TAPER,Phase.RACE_WEEK),
                monday < today || end.minusDays(1) > through || completed.isNotEmpty())
            monday = end
        }
        return FullPlan(today,through,projected.toList(),weeks.toList(),
            futureRace?.let { it > cap } == true,futureRace != null)
    }
}
