package com.pacecraft.engine

import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.*

enum class Ability(val label: String,val score: Double) {
    WALK30("I can walk for 30 minutes",20.0), JOG5("I can jog for 5 minutes",35.0),
    RUN20("I can run for 20 minutes",50.0), RUN5K("I can run 5 km",65.0), RUN10K("I regularly run 10 km or more",80.0)
}
data class StarterProfile(val ability: Ability, val experience: Int=0, val runsPerWeek: Int=3,
    val minutes: Int=20, val created: LocalDate)
object StarterEngine {
    fun load(runs:List<Run>,cross:List<CrossTraining>,f:Fitness,p:Profile,starter:StarterProfile,now:Instant,zone:ZoneId):Load {
        val duration=starter.minutes.coerceIn(10,120)*60.0
        val walking=starter.ability in setOf(Ability.WALK30,Ability.JOG5)
        val speed=f.cs*(if(walking) .55 else .77)
        val prior=LoadModel.session(Run("load-prior",now,duration*speed,duration),f,p,now,zone)*starter.runsPerWeek.coerceIn(2,7)/7
        return LoadModel.evaluate(runs,if(p.countsCrossTraining) cross else emptyList(),f,p,now,zone,prior)
    }
    fun fitness(starter: StarterProfile,real: Fitness?,count: Int): Fitness {
        val seed=Fitness(max(1.2,1.4+.03*starter.ability.score),180.0,1.06,0,0.0,.08,0.0,Confidence.LOW)
        if(real==null) return seed
        val w=when { count==0->0.0;count<=2->.2;count<=5->.5;count<=7->.8;else->1.0 }
        fun mix(a:Double,b:Double)=a*(1-w)+b*w
        return seed.copy(cs=mix(seed.cs,real.cs),dPrime=mix(seed.dPrime,real.dPrime),exponent=mix(seed.exponent,real.exponent),
            effortCount=real.effortCount,r2=real.r2,residual=max(seed.residual,real.residual),staleWeeks=real.staleWeeks)
    }
    fun recommend(runs:List<Run>,goal:Goal,c:Constraints,p:Profile,records:List<SessionRecord>,feedback:List<Feedback>,
                  cross:List<CrossTraining>,starter:StarterProfile,now:Instant,zone:ZoneId):Result {
        val measured=runs.filter(Cleaner::usable)
        val f=fitness(starter,FitnessModel.estimate(FitnessModel.extract(runs,p,now,zone),now),runs.count(Cleaner::effort))
        val load=load(runs,cross,f,p,starter,now,zone)
        val next=next(runs,goal,c,p,f,load,records,feedback,starter,now,zone)
        return Result(runs,Sufficiency(measured.size,measured.firstOrNull()?.start?.ageDays(now)?.div(7)?:0.0,measured.sumOf{it.distance}),
            f,load,next=next,comments=listOf("Starting from your self-reported ability. Paces are provisional until your history is sufficient.")+next.reasons,bootstrapMode=true)
    }
    fun next(runs:List<Run>,goal:Goal,c:Constraints,p:Profile,f:Fitness,load:Load,records:List<SessionRecord>,feedback:List<Feedback>,
             starter:StarterProfile,now:Instant,zone:ZoneId):Session {
        val limit=c.copy(sessions=min(c.sessions,starter.runsPerWeek.coerceIn(2,7)))
        val selected=Planner.next(runs,goal,limit,f,load,records,feedback,now,zone)
        val day=selected.day
        val recentFeedback=feedback.any { it.at<=now && it.at.ageDays(day.atStartOfDay(zone).toInstant())<2 && (it.niggle || it.soreness>=4 || it.energy<=1) }
        // The general planner may rest for a small distance budget. Starting schedules use time,
        // but must still respect unavailable dates, occupied days, session count and recovery.
        if(records.any{it.session.day==day && it.skipped} || runs.any{it.day(zone)==day}) return selected.copy(kind=Kind.REST,steps=emptyList())
        if(load.flag==Flag.RED || recentFeedback || !c.available(day)) return selected.copy(kind=Kind.REST,steps=emptyList(),reasons=listOf("Rest while you recover; the starting schedule never overrides your check-in or load flags."))
        val elapsed=max(0,ChronoUnit.WEEKS.between(starter.created.monday(),day.monday()).toInt())
        val cycle=elapsed%4
        val used=runs.filter { it.day(zone)>=day.monday() && it.day(zone)<day.monday().plusWeeks(1) }
        val harder=cycle==2 && used.isEmpty()
        val minutes=min(starter.minutes.coerceIn(10,120).toDouble()*(if(cycle==1) 1.10 else if(cycle==3) .72 else 1.0),c.maxMinutes?.toDouble()?:Double.MAX_VALUE)
        var seconds=minutes*60
        val steps=mutableListOf<Step>()
        fun add(label:String,t:Double,k:Kind) { if(t>0) steps+=Step(label,t,t*f.cs*(k.low+k.high)/2,k) }
        val beginner=starter.ability in setOf(Ability.WALK30,Ability.JOG5)
        var kind=Kind.EASY
        if(beginner) {
            val warm=min(300.0,seconds/4); add("Walk warm-up",warm,Kind.WALK)
            var remaining=seconds-2*warm
            val run=when(starter.ability) { Ability.WALK30 -> if(harder) 90.0 else 60.0;else -> if(harder) 300.0 else 180.0 }
            val walk=if(harder) 90.0 else 120.0
            while(remaining>0) { val t=min(run,remaining);add("Run",t,Kind.EASY);remaining-=t;val w=min(walk,remaining);add("Walk recovery",w,Kind.WALK);remaining-=w }
            add("Walk cool-down",warm,Kind.WALK)
        } else {
            kind=if(harder && starter.ability>=Ability.RUN5K && load.flag==Flag.GREEN) Kind.PROGRESSION else Kind.EASY
            val distance=seconds*f.cs*(kind.low+kind.high)/2
            steps+=Planner.structure(kind,distance,f,Phase.BASE)
        }
        val duration=steps.sumOf{it.seconds}
        if(duration>seconds) for(i in steps.indices) steps[i]=steps[i].copy(seconds=steps[i].seconds*seconds/duration,distance=steps[i].distance*seconds/duration)
        val weekly=starter.runsPerWeek*starter.minutes*60*f.cs*.77
        val remainingCap=c.maxWeekly?.minus(used.sumOf { it.distance }) ?: Double.MAX_VALUE
        val distance=steps.sumOf{it.distance}
        if(remainingCap<distance) {
            val factor=(remainingCap/max(1.0,distance)).coerceIn(0.0,1.0)
            for(i in steps.indices) steps[i]=steps[i].copy(seconds=steps[i].seconds*factor,distance=steps[i].distance*factor)
        }
        if(steps.sumOf{it.seconds}<300) {kind=Kind.REST;steps.clear()}
        val reasons=listOf(if(beginner) "Run/walk intervals build consistency without assuming continuous running." else "A starting schedule based on the ability and duration you reported.",
            if(cycle==3) "Recovery week: less volume to absorb the work." else if(harder) "One controlled progression slot in the third week." else "Keep the effort comfortable.")
        val id=UUID.nameUUIDFromBytes("starter|$day|$steps|$ENGINE_VERSION".toByteArray()).toString()
        return Session(id,day,kind,if(cycle==3) Phase.RECOVERY else Phase.BASE,steps.toList(),min(weekly,c.maxWeekly?:weekly),reasons)
    }
}
