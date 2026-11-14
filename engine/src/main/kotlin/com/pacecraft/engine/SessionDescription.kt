package com.pacecraft.engine

import kotlin.math.abs

/** Presentation of the prescription, never an average pace across work and recovery. */
data class PrescriptionPace(val label: String, val text: String)
object SessionDescription {
    fun pace(kind: Kind, fitness: Fitness, imperial: Boolean = false, scale: Double = 1.0): String {
        if (kind == Kind.REST) return ""
        val unit = if (imperial) "min/mi" else "min/km"
        return "${Display.pace(fitness.cs * kind.high * scale,imperial)}–${Display.pace(fitness.cs * kind.low * scale,imperial)} $unit"
    }

    fun paces(session: Session, fitness: Fitness, imperial: Boolean = false): List<PrescriptionPace> {
        if (session.kind == Kind.REST || session.steps.isEmpty()) return emptyList()
        val work = session.steps.filter { it.kind == if(session.kind==Kind.STRIDES) Kind.OPENERS else session.kind }
        val recovery = session.steps.firstOrNull { it.kind == Kind.RECOVERY }
        if(session.kind==Kind.PROGRESSION || session.steps.any{it.kind==Kind.WALK})
            return session.steps.distinctBy{it.kind to it.paceScale}.map{PrescriptionPace(it.kind.label,pace(it.kind,fitness,imperial,it.paceScale))}
        if (session.kind in setOf(Kind.INTERVALS,Kind.HILLS,Kind.THRESHOLD,Kind.OPENERS,Kind.STRIDES) && work.size>1) {
            return listOf(PrescriptionPace("Work",pace(work.first().kind,fitness,imperial,work.first().paceScale))) +
                listOfNotNull(recovery?.let { PrescriptionPace("Jog recovery",pace(it.kind,fitness,imperial,it.paceScale)) })
        }
        if (session.kind == Kind.LONG && session.steps.any { it.kind == Kind.STEADY }) {
            return session.steps.map{PrescriptionPace(if(it.kind==Kind.LONG)"Easy" else "Steady finish",pace(it.kind,fitness,imperial,it.paceScale))}
        }
        val main = work.firstOrNull() ?: session.steps.first()
        return listOf(PrescriptionPace(if(session.kind.quality) "Work" else "Pace",pace(main.kind,fitness,imperial,main.paceScale)))
    }

    fun repetitions(session: Session): String? {
        if (session.kind !in setOf(Kind.INTERVALS,Kind.HILLS,Kind.THRESHOLD,Kind.OPENERS,Kind.STRIDES)) return null
        val work = session.steps.filter { it.kind == if(session.kind==Kind.STRIDES) Kind.OPENERS else session.kind }
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
