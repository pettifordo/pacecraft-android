package com.pacecraft.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class FullPlanTest {
    private val now=Instant.parse("2026-06-17T10:00:00Z")
    private val zone=ZoneId.of("Europe/London")
    private val today=now.atZone(zone).toLocalDate()
    private val goal=Goal(distance=10000.0,created=today,raceDate=today.plusWeeks(12))
    private val runs=(1..42).map { n ->
        val d=if(n%4==0) 12000.0 else 7000.0
        Run("real$n",now.minusSeconds(n*2L*86400),d,d/2.8)
    }
    private fun project(g: Goal=goal,c: Constraints=Constraints(),records: List<SessionRecord> = emptyList()): Pair<Result,FullPlan> {
        val r=CoachingEngine.replan(runs,g,c,records=records,now=now,zone=zone)
        return r to FullPlanProjector.project(r.runs,g,c,Profile(),r.fitness!!,r.load!!,records,now=now,zone=zone)
    }
    @Test fun firstEntryIsActualPrescription() {
        val (result,plan)=project()
        assertEquals(result.next,plan.sessions.first())
    }
    @Test fun reachesRaceHorizonWithoutOvershooting() {
        val (_,plan)=project()
        assertEquals(goal.raceDate,plan.through)
        assertTrue(plan.sessions.size>10)
        assertTrue(plan.sessions.all { it.day<=goal.raceDate!! })
        assertTrue(plan.sessions.zipWithNext().all { (a,b) -> b.day>a.day })
    }
    @Test fun noDateShowsFortnightLikeIphone() {
        val (_,plan)=project(goal.copy(raceDate=null))
        assertEquals(today.plusDays(13),plan.through)
        assertFalse(plan.hasFutureRace)
        assertTrue(plan.sessions.all { it.day<=plan.through })
    }
    @Test fun farRaceIsCappedAtThirtyWeeks() {
        val (_,plan)=project(goal.copy(raceDate=today.plusYears(1)))
        assertEquals(today.plusDays(209),plan.through)
        assertTrue(plan.capped)
        assertTrue(plan.sessions.size<=210)
    }
    @Test fun projectionsNeverModifyRealHistoryOrRecords() {
        val before=runs.toList(); val records=emptyList<SessionRecord>(); val (_,plan)=project(records=records)
        assertEquals(before,runs); assertTrue(records.isEmpty())
        assertTrue(runs.none { it.id.startsWith("projection:") })
        assertTrue(plan.weeks.flatMap { it.completed }.all { it.id.startsWith("real") })
    }
    @Test fun calendarWeeksIncludeActualDistanceOnlyOnce() {
        val (_,plan)=project()
        val first=plan.weeks.first()
        val expected=runs.filter { it.day(zone)>=today.monday() }.sumOf { it.distance }
        assertEquals(expected,first.completedDistance,1e-6)
        assertEquals(first.completedDistance+first.sessions.sumOf { it.distance },first.totalDistance,1e-6)
        assertTrue(first.partial)
    }
    @Test fun projectedSessionsRespectAvailabilityAndTimeCaps() {
        val c=Constraints(sessions=3,blackout=setOf(DayOfWeek.MONDAY,DayOfWeek.FRIDAY),maxMinutes=45,maxWeekly=35000.0)
        val (_,plan)=project(c=c)
        assertTrue(plan.sessions.isNotEmpty())
        plan.sessions.filter { it.kind!=Kind.RACE }.forEach {
            assertTrue(it.day.dayOfWeek !in c.blackout)
            assertTrue(it.seconds<=2700.01)
        }
        plan.weeks.forEach { week ->
            assertTrue(week.sessions.count { it.kind!=Kind.REST && it.kind!=Kind.RACE }+week.completed.size<=3)
        }
    }
    @Test fun forecastDoesNotCreateBackToBackQuality() {
        val (_,plan)=project(c=Constraints(intensity=5))
        val hard=plan.sessions.filter { it.kind.quality }
        assertTrue(hard.zipWithNext().all { (a,b) -> java.time.temporal.ChronoUnit.DAYS.between(a.day,b.day)>=2 })
    }
    @Test fun impossibleAvailabilityTerminates() {
        val (_,plan)=project(c=Constraints(blackout=DayOfWeek.entries.toSet()))
        assertTrue(plan.sessions.size<=15)
        assertTrue(plan.sessions.all { it.kind==Kind.REST })
    }
    @Test fun skippedNextDayMovesEntireForecast() {
        val (r,a)=project()
        val (_,b)=project(records=listOf(SessionRecord(r.next!!,skipped=true)))
        assertTrue(b.sessions.first().day>a.sessions.first().day)
    }
}
