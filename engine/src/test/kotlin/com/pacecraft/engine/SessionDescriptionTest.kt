package com.pacecraft.engine

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class SessionDescriptionTest {
    private val f=Fitness(4.0,180.0,1.06,4,.99,.01,0.0,Confidence.HIGH)
    private fun session(kind: Kind, distance: Double=8000.0, phase: Phase=Phase.BUILD)=
        Session("test",LocalDate.of(2026,6,15),kind,phase,Planner.structure(kind,distance,f,phase),35000.0,emptyList())

    @Test fun continuousRunShowsItsActualPaceBand() {
        assertEquals(listOf(PrescriptionPace("Pace","5:05–5:47 min/km")),SessionDescription.paces(session(Kind.EASY),f))
        assertTrue(SessionDescription.paces(session(Kind.REST),f).isEmpty())
    }
    @Test fun intervalsSeparateWorkAndRecoveryPaces() {
        val s=session(Kind.INTERVALS)
        val paces=SessionDescription.paces(s,f)
        assertEquals(PrescriptionPace("Work","3:37–3:58 min/km"),paces[0])
        assertEquals(PrescriptionPace("Jog recovery","5:47–6:43 min/km"),paces[1])
        val reps=s.steps.count { it.kind == Kind.INTERVALS }
        assertEquals("$reps × 3:00 work · 2:00 jog between reps",SessionDescription.repetitions(s))
        assertEquals(reps-1,s.steps.count { it.kind==Kind.RECOVERY })
    }
    @Test fun imperialConvertsBothWorkAndRecovery() {
        val paces=SessionDescription.paces(session(Kind.INTERVALS),f,true)
        assertEquals("3:37–3:58 min/km",SessionDescription.paces(session(Kind.INTERVALS),f)[0].text)
        assertEquals("5:50–6:23 min/mi",paces[0].text)
        assertEquals("9:19–10:49 min/mi",paces[1].text)
    }
    @Test fun fastFinishHasSeparateEasyAndFinishBands() {
        val paces=SessionDescription.paces(session(Kind.LONG,15000.0),f)
        assertEquals(listOf("Easy","Steady finish"),paces.map { it.label })
        assertNotEquals(paces[0].text,paces[1].text)
    }
    @Test fun stridesIncludeTheActualFinalJog() {
        assertEquals("4 × 0:20 strides · 1:00 jog after each rep",SessionDescription.repetitions(session(Kind.OPENERS,3000.0)))
    }
    @Test fun intervalBookendsAndQualityCapsRemainPartOfTotals() {
        val s=session(Kind.INTERVALS,18000.0)
        assertEquals("Warm up",s.steps.first().label)
        assertEquals("Cool down",s.steps.last().label)
        assertEquals(18000.0,s.distance,.01)
        assertTrue(s.steps.filter { it.kind==Kind.INTERVALS }.sumOf { it.seconds }<=24*60)
        assertEquals(s.steps.sumOf { it.seconds },s.seconds,.01)
    }
    @Test fun shortRaceBlockActuallySchedulesIntervalsAfterBase() {
        val zone=ZoneId.of("Europe/London")
        for(stamp in listOf("2026-06-17T10:00:00Z","2026-11-14T18:00:00Z","2026-11-14T21:00:00Z")) for(demo in listOf(false,true)) {
            val now=Instant.parse(stamp); val today=now.atZone(zone).toLocalDate()
            val goal=Goal(distance=10000.0,created=today,raceDate=today.plusWeeks(12))
            val history=(1..42).map { n ->
                val d=if(n%4==0) 12000.0 else if(demo && n%3==0) 5000.0 else 7000.0
                Run("real$n",now.minusSeconds(n*2L*86400),d,d/if(demo && n%3==0) 3.4 else 2.8)
            }
            val r=CoachingEngine.replan(history,goal,now=now,zone=zone)
            val plan=FullPlanProjector.project(r.runs,goal,Constraints(),Profile(),r.fitness!!,r.load!!,emptyList(),now=now,zone=zone)
            val intervals=plan.sessions.filter { it.kind==Kind.INTERVALS }
            assertTrue("A supported 10K build should contain intervals ($stamp demo=$demo)",intervals.isNotEmpty())
            assertTrue(intervals.none { it.phase==Phase.BASE || it.phase==Phase.RECOVERY })
            assertTrue(intervals.all { it.steps.count { s -> s.kind==Kind.INTERVALS }>=2 })
        }
    }
}
