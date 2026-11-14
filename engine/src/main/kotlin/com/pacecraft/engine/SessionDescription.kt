package com.pacecraft.engine

import kotlin.math.abs

/** Presentation of the prescription, never an average pace across work and recovery. */
data class PrescriptionPace(val label: String, val text: String)
object SessionDescription {
    fun pace(kind: Kind, fitness: Fitness, imperial: Boolean = false): String {
        if (kind == Kind.REST) return ""
        val unit = if (imperial) "min/mi" else "min/km"
        return "${Display.pace(fitness.cs * kind.high,imperial)}–${Display.pace(fitness.cs * kind.low,imperial)} $unit"
    }

    fun paces(session: Session, fitness: Fitness, imperial: Boolean = false): List<PrescriptionPace> {
        if (session.kind == Kind.REST || session.steps.isEmpty()) return emptyList()
        val work = session.steps.filter { it.kind == session.kind }
        val recovery = session.steps.firstOrNull { it.kind == Kind.RECOVERY }
        if (session.kind in setOf(Kind.INTERVALS,Kind.OPENERS) && work.isNotEmpty()) {
            return listOf(PrescriptionPace("Work",pace(work.first().kind,fitness,imperial))) +
                listOfNotNull(recovery?.let { PrescriptionPace("Jog recovery",pace(it.kind,fitness,imperial)) })
        }
        if (session.kind == Kind.LONG && session.steps.any { it.kind == Kind.STEADY }) {
            return listOf(PrescriptionPace("Easy",pace(Kind.LONG,fitness,imperial)),
                PrescriptionPace("Steady finish",pace(Kind.STEADY,fitness,imperial)))
        }
        val main = work.firstOrNull() ?: session.steps.first()
        return listOf(PrescriptionPace(if(session.kind.quality) "Work" else "Pace",pace(main.kind,fitness,imperial)))
    }

    fun repetitions(session: Session): String? {
        if (session.kind !in setOf(Kind.INTERVALS,Kind.OPENERS)) return null
        val work = session.steps.filter { it.kind == session.kind }
        if (work.size < 2) return null
        val recovery = session.steps.filter { it.kind == Kind.RECOVERY }
        val equalWork = work.all { abs(it.seconds-work.first().seconds) < .01 }
        val workText = if(equalWork) "${work.size} × ${Display.time(work.first().seconds)} ${if(session.kind == Kind.OPENERS) "strides" else "work"}"
            else "${work.size} reps (${work.joinToString(" / ") { Display.time(it.seconds) }})"
        if (recovery.isEmpty()) return workText
        val equalRecovery = recovery.all { abs(it.seconds-recovery.first().seconds) < .01 }
        val recoveryText = if(equalRecovery) "${Display.time(recovery.first().seconds)} jog" else "variable jog recoveries"
        val placement = when(recovery.size) {
            work.size-1 -> " between reps"
            work.size -> " after each rep"
            else -> " (${recovery.size} recoveries)"
        }
        return "$workText · $recoveryText$placement"
    }
}
