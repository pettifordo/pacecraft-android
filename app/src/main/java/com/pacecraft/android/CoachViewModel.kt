package com.pacecraft.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import com.pacecraft.engine.*
import kotlinx.coroutines.*
import java.time.*
import java.util.UUID

class CoachViewModel(application: Application): AndroidViewModel(application) {
    private val store = LocalStore(application)
    val health = HealthHistory(application)
    var saved by mutableStateOf(SavedState()); private set
    var result by mutableStateOf<Result?>(null); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var demo by mutableStateOf(false); private set
    var fullPlan by mutableStateOf<FullPlan?>(null); private set
    var projecting by mutableStateOf(false); private set
    private var projectionJob: Job? = null
    private var projectionGeneration = 0
    private var planRequested = false
    private var replanTime = Instant.now()
    val effectiveGoal: Goal get() {
        val goal = saved.goal
        val race = goal.raceDate
        return if(demo && (race == null || race < LocalDate.now()))
            goal.copy(created=LocalDate.now(),raceDate=LocalDate.now().plusWeeks(12)) else goal
    }
    private var demoRuns = emptyList<Run>()
    private var healthyStore = true
    init {
        try { saved = store.read(); DailyCoach.schedule(application); recalculate() }
        catch(e: Exception) { healthyStore = false; message = "Local data could not be read. Clear local data in Settings to start again. ${e.message}" }
    }
    private fun recalculate() {
        val now = Instant.now(); val zone = ZoneId.systemDefault()
        replanTime = now
        projectionJob?.cancel(); projectionGeneration++; projecting=false; fullPlan=null
        val runs = if(demo) demoRuns else saved.runs
        val reconciled = CoachingEngine.reconcile(saved.records,runs,zone)
        val raw = CoachingEngine.replan(runs,effectiveGoal,saved.constraints,saved.trainingProfile(now),reconciled,saved.feedback,
            if(demo) emptyList() else saved.cross,now,zone,starter=if(demo) null else saved.starter)
        val override=saved.override?.takeIf{saved.overrideBase==raw.next?.id && !demo}
        val computed=if(override!=null) raw.copy(next=override,comments=raw.comments+"Your session adjustment is active.") else raw
        var records = reconciled
        computed.next?.let { next ->
            records = records.filter { it.runId != null || it.skipped || it.session.day < LocalDate.now() } + SessionRecord(next)
        }
        result = computed
        if(!demo && healthyStore) {
            val today=now.atZone(zone).toLocalDate()
            val drift=if(computed.prediction!=null&&computed.fitness!=null&&computed.load!=null)
                ProgressAnalysis.evaluate(effectiveGoal,computed.prediction!!,computed.load!!,records,computed.runs,computed.fitness!!,now,zone,saved.driftingChecks).status else Track.ON_TRACK
            val count=if(saved.progressDay==today)saved.driftingChecks else if(drift in setOf(Track.DRIFTING,Track.OFF_TRACK))saved.driftingChecks+1 else 0
            saved = store.update{it.copy(records=records.takeLast(500),override=override,overrideBase=if(override!=null) raw.next?.id else null,progressDay=today,driftingChecks=count)}
        }
        if(planRequested) ensurePlan()
    }
    fun ensurePlan() {
        planRequested=true
        val current=result ?: return
        val fitness=current.fitness ?: return
        val load=current.load ?: return
        if(fullPlan!=null || projecting) return
        val snapshot=saved; val goal=effectiveGoal; val clock=replanTime; val isDemo=demo
        val generation=++projectionGeneration
        projecting=true
        projectionJob=viewModelScope.launch {
            try {
                val projected=withContext(Dispatchers.Default) {
                    FullPlanProjector.project(current.runs,goal,snapshot.constraints,snapshot.trainingProfile(clock),
                        fitness,load,snapshot.records,snapshot.feedback,if(isDemo) emptyList() else snapshot.cross,
                        clock,ZoneId.systemDefault(),starter=if(isDemo || !current.bootstrapMode) null else snapshot.starter,firstOverride=current.next)
                }
                if(isActive && generation==projectionGeneration) fullPlan=projected
            } catch(e: CancellationException) { throw e }
              catch(e: Exception) { message="Could not project the plan: ${e.message}" }
            finally { if(generation==projectionGeneration) projecting=false }
        }
    }
    private fun change(transform: (SavedState)->SavedState) {
        try { check(healthyStore) { "Clear the unreadable local store first." }; saved=store.update(transform); recalculate() }
        catch(e: Exception) { message="Could not save changes: ${e.message}" }
    }
    fun refresh() {
        if(busy || demo) return
        viewModelScope.launch {
            busy=true
            try {
                saved=store.read()
                withTimeoutOrNull(3000){runCatching{WearInbox.pull(getApplication())}}
                WearInbox.import(getApplication())
                saved=store.read()
                val granted = health.granted()
                val exercise = androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.ExerciseSessionRecord::class)
                val distance = androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.DistanceRecord::class)
                if(exercise !in granted || distance !in granted) {
                    result=null
                    // Revoked permissions must stop use of cached Health Connect data.
                    change { it.copy(runs=it.runs.filterNot { r -> r.id.startsWith("hc:") },cross=emptyList(),lastSync=null,importedRHR=null,importedHRV=null) }
                    return@launch
                }
                val previouslyMatched=saved.records.mapNotNull{it.runId}.toSet()
                val now=Instant.now()
                val imported=withContext(Dispatchers.IO) { health.read(now) }
                change { state -> state.copy(runs=state.runs.filterNot { it.id.startsWith("hc:") } + imported.runs,cross=imported.cross,lastSync=now,importedRHR=imported.rhrChange,importedHRV=imported.hrvChange) }
                if(saved.reminders && saved.records.any{it.runId!=null&&it.runId !in previouslyMatched}) DailyCoach.notify(getApplication(),2,"Run recorded","Your history is linked and your next session has been updated.")
                message = buildString {
                    append("Imported ${imported.runs.size} runs.")
                    if(!imported.historyAccess) append(" Older history may be limited to 30 days. Allow history access if available, or add older runs manually.")
                    if(imported.missingDistances>0) append(" ${imported.missingDistances} sessions had no distance from their recording app and were skipped.")
                }
            } catch(e: Exception) { message="History refresh failed: ${e.message ?: "check Health Connect access"}. Last successful import is retained." }
            finally { busy=false }
        }
    }
    fun reminders(enabled:Boolean)=change{it.copy(reminders=enabled)}
    fun clearMessage() { message=null }
    fun showDemo(enable: Boolean) {
        demo=enable
        if(enable) {
            val now=Instant.now()
            demoRuns=(1..42).map { n ->
                val d=if(n%4==0) 12000.0 else if(n%3==0) 5000.0 else 7000.0
                Run("demo:$n",now.minusSeconds(n*2L*86400),d,d/(if(n%3==0) 3.4 else 2.8),if(n%3==0) 172.0 else 143.0,source="Demo")
            }
        }
        try { recalculate() } catch(e: Exception) { message=e.message }
    }
    fun startFromProfile(profile:StarterProfile)=change{it.copy(starter=profile,constraints=it.constraints.copy(sessions=profile.runsPerWeek),override=null,overrideBase=null)}
    fun resetAdjustment()=change{it.copy(override=null,overrideBase=null)}
    fun adjust(preview:AdjustmentPreview) { if(preview.allowed) { val base=result?.next?:return;change{it.copy(override=preview.session,overrideBase=it.overrideBase?:base.id)} } }
    fun unavailableDates(dates:Set<LocalDate>)=change{it.copy(constraints=it.constraints.copy(blackoutDates=dates),override=null,overrideBase=null)}
    fun saveGoal(goal: Goal) = change { state ->
        val current=result?.fitness?.let { fitness ->
            val now=Instant.now()
            val longest=state.runs.filter { java.time.Duration.between(it.start,now).toDays() in 0..56 }.maxOfOrNull { it.distance } ?: 0.0
            Predictor.predict(goal.distance,fitness,longest,LoadModel.rolling(state.runs,now,4).average()).seconds
        }
        val volume=LoadModel.calendar(state.runs,LocalDate.now(),ZoneId.systemDefault(),5).dropLast(1).maxOrNull()
        state.copy(override=null,overrideBase=null,progressDay=null,driftingChecks=0,goal=goal.copy(created=LocalDate.now(),baselineSeconds=current,arrivedWeekly=volume),records=state.records.filter { it.runId!=null || it.skipped })
    }
    fun saveConstraints(c: Constraints, imperial: Boolean, profile: Profile) = change { it.copy(constraints=c.copy(blackoutDates=it.constraints.blackoutDates),imperial=imperial,profile=profile,override=null,overrideBase=null) }
    fun addRun(day: LocalDate, time: LocalTime, metres: Double, seconds: Double, indoor: Boolean) = change {
        it.copy(runs=it.runs+Run("manual:${UUID.randomUUID()}",day.atTime(time).atZone(ZoneId.systemDefault()).toInstant(),metres,seconds,indoor=indoor))
    }
    fun deleteRun(id: String) = change { it.copy(runs=it.runs.filterNot { r -> r.id==id },records=it.records.map { r -> if(r.runId==id) r.copy(runId=null) else r }) }
    fun checkIn(soreness: Int, energy: Int, niggle: Boolean, note: String) = change { it.copy(feedback=(it.feedback+Feedback(Instant.now(),soreness.coerceIn(0,5),energy.coerceIn(1,5),niggle,note)).takeLast(100)) }
    fun skip() { result?.next?.let { next -> change { s -> s.copy(records=s.records.filterNot { it.session.id==next.id } + SessionRecord(next,skipped=true)) } } }
    fun erase() {
        try { store.erase(); WearInbox.clear(getApplication()); healthyStore=true; saved=SavedState(); demo=false; result=null; message="Local history and settings cleared. Health Connect originals are untouched."; recalculate() }
        catch(e: Exception) { message=e.message }
    }
}
