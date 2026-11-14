package com.pacecraft.engine

import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.*

object Phases {
    fun taper(d: Double) = when { d < 10000 -> 7; d < HALF -> 10; d < MARATHON -> 14; else -> 21 }
    fun phase(g: Goal, day: LocalDate): Phase {
        val race = g.raceDate ?: return if (ChronoUnit.WEEKS.between(g.created, day) < 4) Phase.BASE else Phase.BUILD
        val days = ChronoUnit.DAYS.between(day, race)
        if (days < 0) {
            val recovery = (g.distance / 1609.34).roundToLong()
            return if (-days <= recovery) Phase.RECOVERY else if ((-days - recovery) < 28) Phase.BASE else Phase.BUILD
        }
        if (days <= 7) return Phase.RACE_WEEK
        if (days <= taper(g.distance)) return Phase.TAPER
        val elapsed = ChronoUnit.DAYS.between(g.created, day).toDouble() / max(1, ChronoUnit.DAYS.between(g.created, race))
        return when { elapsed < .45 -> Phase.BASE; elapsed < .75 -> Phase.BUILD; else -> Phase.PEAK }
    }
    fun down(g: Goal, day: LocalDate): Boolean {
        val weeks = ChronoUnit.WEEKS.between(g.created.monday(), day.monday())
        return weeks > 0 && weeks % 4 == 3L
    }
    fun shape(p: Phase, bias: Double): Map<Kind, Double> {
        fun blend(a: Double, b: Double) = a * (1 - bias) + b * bias
        return when (p) {
            Phase.BASE -> mapOf(Kind.EASY to blend(.65,.60), Kind.LONG to blend(.22,.30), Kind.STEADY to .06*(1-bias), Kind.MARATHON_PACE to .05*bias, Kind.THRESHOLD to blend(.07,.05))
            Phase.BUILD -> mapOf(Kind.EASY to blend(.55,.52), Kind.LONG to blend(.20,.26), Kind.STEADY to .05*(1-bias), Kind.MARATHON_PACE to .10*bias, Kind.THRESHOLD to blend(.12,.10), Kind.INTERVALS to blend(.08,.02))
            Phase.PEAK -> mapOf(Kind.EASY to blend(.50,.45), Kind.LONG to blend(.18,.28), Kind.STEADY to .05*(1-bias), Kind.MARATHON_PACE to .15*bias, Kind.THRESHOLD to blend(.15,.10), Kind.INTERVALS to blend(.12,.02))
            Phase.TAPER -> mapOf(Kind.EASY to .60, Kind.LONG to .20, Kind.STEADY to .08, Kind.THRESHOLD to .08, Kind.INTERVALS to .04)
            Phase.RACE_WEEK -> mapOf(Kind.EASY to .75, Kind.OPENERS to .10, Kind.THRESHOLD to .15)
            Phase.RECOVERY -> mapOf(Kind.RECOVERY to .60, Kind.EASY to .40)
        }
    }
}
object Planner {
    fun next(runs: List<Run>, goal: Goal, c: Constraints, f: Fitness, load: Load,
             records: List<SessionRecord>, feedback: List<Feedback>, now: Instant, zone: ZoneId): Session {
        val local = now.atZone(zone)
        var day = local.toLocalDate().plusDays(if (local.hour >= 20) 1 else 0)
        fun inWeek(d: LocalDate) = runs.filter { it.day(zone) >= d.monday() && it.day(zone) < d.monday().plusWeeks(1) }
        fun spoken(d: LocalDate) = runs.any { it.day(zone) == d } || records.any { it.session.day == d && it.skipped }
        fun consecutive(d: LocalDate): Int {
            var n = 0
            while (n < 7 && runs.any { it.day(zone) == d.minusDays(n + 1L) }) n++
            return n
        }
        var found = false
        for (i in 0..13) {
            val used = inWeek(day).size
            val slots = c.sessions.coerceIn(2,7) - used
            val remainingDays = 8 - day.dayOfWeek.value
            if (c.available(day) && !spoken(day) && slots > 0 && !(remainingDays > slots && consecutive(day) >= 2)) { found = true; break }
            day = day.plusDays(1)
        }
        val phase = Phases.phase(goal, day)
        val reasons = mutableListOf<String>()
        fun make(kind: Kind, steps: List<Step>, target: Double): Session {
            val identity = "$ENGINE_VERSION|$day|$kind|$target|$steps|$reasons"
            return Session(UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8)).toString(), day, kind, phase, steps, target, reasons.toList())
        }
        if (!found) { reasons += "No available training day in the next fortnight. Update your availability."; return make(Kind.REST, emptyList(), 0.0) }
        val complete = LoadModel.calendar(runs, day, zone, 7).dropLast(1)
        val last = complete.lastOrNull() ?: 0.0
        // Step over a recent down/lost week; a long absence progressively lowers the restart.
        val established = complete.takeLast(4).maxOrNull() ?: last
        val returnFactor = if (load.layoffWeeks >= 4 && load.weeksBack < 2) .65 else 1.0
        val rampBase = max(last, established * returnFactor)
        val ramp = if (load.flag == Flag.GREEN) c.ramp * (1 - load.crossShare) else 0.0
        val fraction = when (phase) { Phase.BASE -> .85; Phase.BUILD -> .95; Phase.RECOVERY -> .70; else -> 1.0 }
        val arrived = goal.arrivedWeekly ?: LoadModel.calendar(runs, goal.created, zone, 5).dropLast(1).maxOrNull() ?: established
        val destination = max(Demands.weekly(goal.distance), c.maxWeekly ?: 0.0) * fraction
        val ceiling = max(destination, arrived)
        var target = if (established >= ceiling) max(ceiling, rampBase) else min(max(8000.0, rampBase) * (1 + ramp), ceiling)
        target = min(target, c.maxWeekly ?: Double.POSITIVE_INFINITY)
        if (phase == Phase.TAPER || phase == Phase.RACE_WEEK) {
            val days = goal.raceDate?.let { ChronoUnit.DAYS.between(day, it).coerceAtLeast(0) } ?: 0
            val factor = .55 + .45 * (1 - exp(-days / (Phases.taper(goal.distance) / 2.5)))
            target = min(target, established * factor); reasons += "Taper: less volume, familiar pace."
        } else if (Phases.down(goal, day)) { target *= .72; reasons += "Down week: space to absorb the work." }
        val weekRuns = inWeek(day)
        val remaining = max(0.0, target - weekRuns.sumOf { it.distance })
        val completed = records.filter { it.runId != null && it.session.day >= day.monday() && it.session.day < day.monday().plusWeeks(1) }
        val longDone = completed.any { it.session.kind == Kind.LONG } || weekRuns.any { it.distance >= max(8000.0, target * .30) }
        val qualityCap = when (c.intensity.coerceIn(1,5)) { 1 -> if (phase == Phase.PEAK) 1 else 0; 2 -> 1; 3 -> if (phase == Phase.BASE) 1 else 2; else -> if (phase == Phase.BASE) 2 else 3 }
        val qualityLeft = qualityCap - completed.count { it.session.kind.quality }
        val shape = Phases.shape(phase, Demands.bias(goal.distance))
        val candidates = mutableSetOf(Kind.REST, Kind.RECOVERY, Kind.EASY, Kind.LONG, Kind.STEADY, Kind.MARATHON_PACE, Kind.THRESHOLD, Kind.INTERVALS, Kind.HILLS, Kind.PROGRESSION, Kind.STRIDES)
        val recentHard = runs.any { it.start < day.atStartOfDay(zone).toInstant() &&
            Duration.between(it.start, day.atStartOfDay(zone).toInstant()).toHours() < 48 &&
            (it.speed >= f.cs * .85 || records.any { rec -> rec.runId == it.id && rec.session.kind.quality }) }
        val recentLong = completed.any { it.session.kind == Kind.LONG && ChronoUnit.DAYS.between(it.session.day, day) < 1 }
        val recentFeedback = feedback.any { it.at <= now && it.at.ageDays(day.atStartOfDay(zone).toInstant()) < 2 && (it.niggle || it.soreness >= 4 || it.energy <= 1) }
        val returning = load.layoffWeeks >= 4 && load.weeksBack < 2
        if (recentHard || recentLong || qualityLeft <= 0 || recentFeedback || returning) candidates.removeAll { it.quality || it==Kind.STRIDES }
        if (recentFeedback) reasons += "Your check-in asks for easy running while things settle."
        if (returning) reasons += "Rebuilding after time away; hard sessions can wait."
        if (load.flag == Flag.AMBER) { candidates.removeAll(setOf(Kind.INTERVALS, Kind.THRESHOLD, Kind.HILLS)); reasons += load.reasons }
        if (load.flag == Flag.RED) {
            candidates.retainAll(if (runs.any { it.day(zone) == day.minusDays(1) }) setOf(Kind.REST) else setOf(Kind.REST,Kind.RECOVERY))
            reasons += load.reasons
        }
        if(load.flag!=Flag.GREEN) candidates.remove(Kind.STRIDES)
        if(c.treadmillOnly) candidates.remove(Kind.HILLS)
        if (phase == Phase.RECOVERY) candidates.retainAll(setOf(Kind.REST,Kind.RECOVERY,Kind.EASY))
        if (remaining < 3000) candidates.retainAll(setOf(Kind.REST))
        val raceDays = goal.raceDate?.let { ChronoUnit.DAYS.between(day, it) }
        // Race/openers never override overload or feedback limits.
        if (raceDays != null && raceDays in 0..2 && load.flag != Flag.RED && !recentFeedback) {
            candidates.clear(); candidates += when(raceDays) { 0L -> Kind.RACE; 1L -> Kind.OPENERS; else -> Kind.REST }
        }
        if (longDone || (shape[Kind.LONG] ?: 0.0) == 0.0) candidates.remove(Kind.LONG)
        val slotsLeft = c.sessions.coerceIn(2,7) - weekRuns.size
        val daysLeft = 7 - day.dayOfWeek.value
        val laterLongDay = (1..daysLeft).any { n -> val d = day.plusDays(n.toLong()); c.available(d) && !spoken(d) && (Phases.shape(Phases.phase(goal,d),Demands.bias(goal.distance))[Kind.LONG] ?: 0.0) > 0 }
        if (Kind.LONG in candidates && (slotsLeft <= 1 || !laterLongDay)) candidates.retainAll(setOf(Kind.LONG))
        fun stale(k: Kind): Double {
            val lastDay = records.filter { it.runId != null && it.session.kind == k && it.session.day < day }.maxOfOrNull { it.session.day }
            return lastDay?.let { (ChronoUnit.DAYS.between(it,day) / (if(k == Kind.LONG) 7.0 else 10.0)).coerceIn(0.0,2.0) } ?: 2.0
        }
        fun score(k: Kind): Double = when(k) {
            Kind.LONG -> when { day.dayOfWeek == c.longDay -> 110.0
                slotsLeft >= 2 && day.dayOfWeek.value < c.longDay.value && c.longDay !in c.blackout -> 20.0
                daysLeft <= 2 -> 100.0; else -> 40 + (shape[k] ?: 0.0) * 100 * stale(k) }
            Kind.STEADY,Kind.MARATHON_PACE,Kind.THRESHOLD,Kind.INTERVALS -> 600 * (shape[k] ?: 0.0) * stale(k)
            Kind.HILLS -> if(phase in setOf(Phase.BASE,Phase.BUILD) && c.intensity>=4 && qualityLeft>0) 35*stale(k) else 0.0
            Kind.PROGRESSION -> if(phase in setOf(Phase.BUILD,Phase.PEAK) && qualityLeft>0) 30*stale(k) else 0.0
            Kind.STRIDES -> 20 + 18*stale(k)
            Kind.WALK -> 0.0
            Kind.EASY -> 30 + if(target > 0) remaining/target*20 else 0.0
            Kind.RECOVERY -> if(load.balance < -25) 70.0 else 10.0
            Kind.REST -> if(remaining < 3000 || consecutive(day) >= 5) 100.0 else 5.0
            Kind.OPENERS -> 90.0; Kind.RACE -> 120.0
        }
        var kind = candidates.sortedBy { it.name }.maxByOrNull(::score) ?: Kind.REST
        val recent = runs.filter { it.start.ageDays(now) <= 28 }
        val longest = recent.maxOfOrNull { it.distance } ?: 5000.0
        val weekLongest = weekRuns.maxOfOrNull { it.distance } ?: 0.0
        val longLimit = min(min(longest * 1.10, longest + 2000), target * Demands.longShare(goal.distance, c.sessions))
        var distance = when(kind) {
            Kind.REST -> 0.0; Kind.RACE -> goal.distance; Kind.OPENERS -> 3000.0
            Kind.RECOVERY -> min(5000.0, remaining / max(1,slotsLeft))
            Kind.LONG -> min(Demands.long(goal.distance), longLimit)
            else -> {
                val reserve = if(!longDone) longLimit else 0.0
                min((remaining - reserve).coerceAtLeast(0.0) / max(1,slotsLeft - if(longDone) 0 else 1), max(weekLongest,longLimit) * .80)
            }
        }
        if (kind != Kind.RACE) {
            distance = min(distance, min(remaining, longest * 1.35))
            c.maxMinutes?.let { distance = min(distance, it * 60 * f.cs * (kind.low + kind.high) / 2) }
            if (kind == Kind.RECOVERY) distance = min(distance, 2400 * f.cs * .67)
        }
        val floor = when(kind) { Kind.LONG -> 8000; Kind.RECOVERY,Kind.OPENERS -> 3000; Kind.REST -> 0; else -> 4000 }
        if (kind != Kind.RACE && distance < floor) {
            kind = if(distance >= 3000) Kind.RECOVERY else Kind.REST
            if(kind == Kind.REST) distance = 0.0
            reasons += "Keeping the session inside your available time and volume."
        }
        // Round down, so presentation never pushes a hard ceiling upwards.
        if (kind != Kind.RACE) distance = floor(distance / 100) * 100
        val structurePhase = if(recentFeedback || recentHard || returning || load.flag != Flag.GREEN) Phase.BASE else phase
        var steps = structure(kind,distance,f,structurePhase)
        // Interval bookends are included in all totals; a session that cannot fit becomes easy.
        if (kind != Kind.RACE && (steps.sumOf { it.distance } > distance + 1 || c.maxMinutes?.let { steps.sumOf { s -> s.seconds } > it*60 } == true)) {
            kind = if(distance >= 3000) Kind.EASY else Kind.REST
            steps = structure(kind,distance,f,structurePhase)
            if(c.maxMinutes?.let { steps.sumOf { s -> s.seconds } > it*60 } == true) {
                distance = floor(c.maxMinutes * 60 * f.cs * .77 / 100) * 100
                kind = if(distance >= 3000) Kind.EASY else Kind.REST
                steps = structure(kind,distance,f,structurePhase)
            }
        }
        if (kind.quality && steps.all { !it.kind.quality }) kind = Kind.EASY
        if (reasons.isEmpty()) reasons += when(kind) {
            Kind.LONG -> "Time on feet builds the durability your race needs."
            Kind.THRESHOLD -> "Controlled threshold work develops sustainable speed."
            Kind.INTERVALS -> "Short, measured efforts build speed; keep the recoveries easy."
            Kind.REST -> "Recovery is part of the training."
            else -> "Build consistency without adding unnecessary fatigue."
        }
        return make(kind,steps,target)
    }
    fun structure(kind: Kind, distance: Double, f: Fitness, phase: Phase): List<Step> {
        fun step(label: String, d: Double, k: Kind): Step = Step(label,d / (f.cs * (k.low+k.high)/2),d,k)
        fun timed(label: String, seconds: Double, k: Kind): Step = Step(label,seconds,seconds*f.cs*(k.low+k.high)/2,k)
        if(kind == Kind.REST || distance <= 0) return emptyList()
        if(kind == Kind.LONG && distance >= 12000 && phase in setOf(Phase.BUILD,Phase.PEAK))
            return listOf(step("Easy running",distance*2/3,Kind.LONG),step("Steady finish",distance/3,Kind.STEADY))
        if(kind == Kind.PROGRESSION) return listOf(step("Easy",distance/3,Kind.EASY),step("Steady",distance/3,Kind.STEADY),step("Threshold finish",distance/3,Kind.THRESHOLD))
        if(kind in setOf(Kind.OPENERS,Kind.STRIDES)) {
            val stride = timed("Relaxed stride",20.0,Kind.OPENERS)
            val jog = timed("Easy jog",60.0,Kind.RECOVERY)
            val easyDistance = distance - 4*(stride.distance+jog.distance)
            if(easyDistance < 1500) return listOf(step("Easy shakeout",distance,Kind.EASY))
            return listOf(step("Easy shakeout",easyDistance,Kind.EASY)) + (1..4).flatMap { listOf(stride.copy(label="Stride $it of 4"),jog) }
        }
        if(!kind.quality || kind == Kind.RACE) return listOf(step(kind.label,distance,kind))
        val warm = timed("Warm up",600.0,Kind.EASY); val cool = timed("Cool down",300.0,Kind.EASY)
        val available = distance - warm.distance - cool.distance
        if(kind in setOf(Kind.INTERVALS,Kind.HILLS)) {
            if(kind==Kind.INTERVALS && phase==Phase.PEAK) {
                val durations=listOf(120.0,180.0,240.0,180.0,120.0)
                val blocks=durations.flatMap { t->listOf(timed("${(t/60).toInt()} min hard",t,kind),timed("Jog recovery",t,Kind.RECOVERY)) }
                if(blocks.sumOf{it.distance}<=available) return listOf(warm)+blocks+step("Cool down",distance-warm.distance-blocks.sumOf{it.distance},Kind.EASY)
            }
            val work = timed(if(kind==Kind.HILLS) "Uphill · controlled effort" else "Work",if(kind==Kind.HILLS) 60.0 else 180.0,kind)
            val recovery = timed(if(kind==Kind.HILLS) "Easy downhill recovery" else "Jog recovery",if(kind==Kind.HILLS) 90.0 else 120.0,Kind.RECOVERY)
            val count = min(8, floor((available + recovery.distance) / (work.distance+recovery.distance)).toInt())
            if(count < 2) return listOf(step("Easy running",distance,Kind.EASY))
            val blocks = (0 until count).flatMap { listOf(work.copy(label="Effort ${it+1} of $count")) + if(it<count-1) listOf(recovery) else emptyList() }
            val extra = distance - warm.distance - cool.distance - blocks.sumOf { it.distance }
            return listOf(warm) + blocks + step("Cool down",cool.distance+max(0.0,extra),Kind.EASY)
        }
        val maxWork = if(kind == Kind.THRESHOLD) 2700.0 else 3600.0
        val work = step(kind.label,max(0.0,available),kind)
        if(work.seconds < 600) return listOf(step("Easy running",distance,Kind.EASY))
        if(kind==Kind.THRESHOLD && work.seconds>1800) {
            val rep=timed("Cruise interval",480.0,Kind.THRESHOLD); val recovery=timed("Jog recovery",90.0,Kind.RECOVERY)
            val reps=min(5,floor((available+recovery.distance)/(rep.distance+recovery.distance)).toInt())
            if(reps>=2) {
                val blocks=(0 until reps).flatMap { listOf(rep.copy(label="Cruise interval ${it+1} of $reps")) + if(it<reps-1) listOf(recovery) else emptyList() }
                return listOf(warm)+blocks+step("Cool down",distance-warm.distance-blocks.sumOf { it.distance },Kind.EASY)
            }
        }
        val actual = if(work.seconds>maxWork) timed(kind.label,maxWork,kind) else work
        return listOf(warm,actual,step("Cool down",distance-warm.distance-actual.distance,Kind.EASY))
    }
}
object CoachingEngine {
    /** All time and timezone inputs are explicit. No I/O, ambient clock or random IDs. */
    fun replan(samples: List<Run>, goal: Goal, constraints: Constraints = Constraints(), profile: Profile = Profile(),
               records: List<SessionRecord> = emptyList(), feedback: List<Feedback> = emptyList(),
               crossTraining: List<CrossTraining> = emptyList(), now: Instant, zone: ZoneId, starter: StarterProfile? = null): Result {
        val runs = Cleaner.clean(samples.filter { it.start <= now && it.start.ageDays(now) <= 182 },zone,profile.mergeDuplicates,allowRunWalk=starter!=null)
        val measured=runs.filter(Cleaner::usable)
        val suff = Sufficiency(measured.size, measured.firstOrNull()?.start?.ageDays(now)?.div(7) ?: 0.0,measured.sumOf { it.distance })
        if(!suff.sufficient && starter!=null) return StarterEngine.recommend(runs,goal,constraints,profile,records,feedback,crossTraining,starter,now,zone)
        if(!suff.sufficient) return Result(runs,suff,comments=listOf("Establishing your base. Race predictions need at least 8 runs, 6 weeks of history and 40 km."))
        val f = FitnessModel.estimate(FitnessModel.extract(runs,profile,now,zone),now)
            ?: return if(starter!=null) StarterEngine.recommend(runs,goal,constraints,profile,records,feedback,crossTraining,starter,now,zone)
            else Result(runs,suff,comments=listOf("No suitable outdoor efforts yet. Indoor and hilly runs count towards load, but cannot anchor a fitness estimate."))
        val load = LoadModel.evaluate(runs,if(profile.countsCrossTraining) crossTraining else emptyList(),f,profile,now,zone)
        val weekly = LoadModel.rolling(runs,now,4).average()
        val longest = runs.filter { it.start.ageDays(now) <= 56 }.maxOfOrNull { it.distance } ?: 0.0
        val prediction = Predictor.predict(goal.distance,f,longest,weekly)
        val reconciled = reconcile(records,runs,zone)
        val next = Planner.next(runs,goal,constraints,f,load,reconciled,feedback,now,zone)
        val comments = buildList {
            addAll(next.reasons)
            if(load.ratio < .8 && !Phases.down(goal,now.atZone(zone).toLocalDate()) && next.phase !in setOf(Phase.TAPER,Phase.RACE_WEEK,Phase.RECOVERY))
                add("You have done less than usual recently. That alone is not an overload warning.")
            goal.targetSeconds?.let { target ->
                val gap = prediction.seconds / target - 1
                add(when { prediction.beyondEvidence -> "Build supporting volume before judging the target time."
                    gap <= 0 -> "Your current estimate supports your target. Keep training consistent."
                    gap <= .05 -> "Your target is within reach of the current estimate; watch the trend."
                    else -> "Your target is ahead of current fitness. Consider more time or a revised target." })
            }
            addAll(prediction.caveats)
        }
        return Result(runs,suff,f,load,prediction,next,comments.distinct())
    }
    fun reconcile(records: List<SessionRecord>, runs: List<Run>, zone: ZoneId): List<SessionRecord> {
        val used = records.mapNotNull { it.runId }.toMutableSet()
        return records.map { rec ->
            if(rec.skipped || rec.runId != null || rec.session.kind == Kind.REST) rec else {
                val match = runs.filter { it.id !in used && it.day(zone) == rec.session.day }
                    .minByOrNull { abs(it.distance - rec.session.distance) }
                if(match != null && abs(match.distance-rec.session.distance) <= max(1000.0,rec.session.distance*.30)) {
                    used += match.id; rec.copy(runId=match.id)
                } else rec
            }
        }
    }
}
