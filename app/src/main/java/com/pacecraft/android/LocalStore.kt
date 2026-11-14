package com.pacecraft.android

import android.content.Context
import com.pacecraft.engine.*
import org.json.*
import java.time.*

data class SavedState(val runs: List<Run> = emptyList(), val cross: List<CrossTraining> = emptyList(),
    val goal: Goal = Goal(created=LocalDate.now()), val constraints: Constraints = Constraints(),
    val profile: Profile = Profile(), val feedback: List<Feedback> = emptyList(),
    val records: List<SessionRecord> = emptyList(), val imperial: Boolean = false, val lastSync: Instant? = null)
private fun JSONObject.str(key: String) = if(isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
private fun JSONObject.num(key: String) = if(isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
class LocalStore(context: Context) {
    private val file = java.io.File(context.filesDir,"pacecraft-v1.json")
    private fun runJson(r: Run) = JSONObject().put("id",r.id).put("start",r.start.toString()).put("distance",r.distance)
        .put("duration",r.duration).put("hr",r.heartRate).put("ascent",r.ascent).put("indoor",r.indoor).put("source",r.source)
    private fun readRun(j: JSONObject) = Run(j.getString("id"),Instant.parse(j.getString("start")),j.getDouble("distance"),j.getDouble("duration"),j.num("hr"),j.num("ascent"),j.optBoolean("indoor"),j.getString("source"))
    private fun sessionJson(s: Session) = JSONObject().put("id",s.id).put("day",s.day.toString()).put("kind",s.kind.name).put("phase",s.phase.name)
        .put("target",s.weeklyTarget).put("reasons",JSONArray(s.reasons)).put("steps",JSONArray(s.steps.map { JSONObject().put("label",it.label).put("seconds",it.seconds).put("distance",it.distance).put("kind",it.kind.name) }))
    private fun readSession(j: JSONObject) = Session(j.getString("id"),LocalDate.parse(j.getString("day")),Kind.valueOf(j.getString("kind")),Phase.valueOf(j.getString("phase")),
        j.getJSONArray("steps").objects().map { Step(it.getString("label"),it.getDouble("seconds"),it.getDouble("distance"),Kind.valueOf(it.getString("kind"))) },j.getDouble("target"),
        j.getJSONArray("reasons").let { a -> (0 until a.length()).map { a.getString(it) } })
    fun read(): SavedState {
        if(!file.exists()) return SavedState()
        // A damaged store must be reported, never silently overwritten with an empty one.
        val j = JSONObject(file.readText()); require(j.getInt("schema") == 1) { "Unsupported local data version" }
        val g = j.getJSONObject("goal"); val c = j.getJSONObject("constraints"); val p = j.optJSONObject("profile") ?: JSONObject()
        return SavedState(
            j.getJSONArray("runs").objects().map(::readRun),
            j.getJSONArray("cross").objects().map { CrossTraining(it.getString("id"),Instant.parse(it.getString("start")),it.getDouble("duration"),it.optBoolean("strength"),it.num("hr")) },
            Goal(g.getDouble("distance"),g.str("date")?.let(LocalDate::parse),g.num("target"),LocalDate.parse(g.getString("created")),g.num("baseline"),g.num("arrived")),
            Constraints(c.getInt("sessions"),c.getInt("intensity"),c.getJSONArray("blackout").let { a -> (0 until a.length()).map { DayOfWeek.of(a.getInt(it)) }.toSet() },
                longDay=DayOfWeek.of(c.getInt("longDay")),maxWeekly=c.num("maxWeekly"),maxMinutes=c.num("maxMinutes")?.toInt(),treadmillOnly=c.optBoolean("treadmill")),
            Profile(p.num("birthYear")?.toInt(),p.num("resting"),p.num("maxHr"),p.optBoolean("merge",true)),
            j.getJSONArray("feedback").objects().map { Feedback(Instant.parse(it.getString("at")),it.getInt("soreness"),it.getInt("energy"),it.getBoolean("niggle"),it.optString("note")) },
            j.getJSONArray("records").objects().map { SessionRecord(readSession(it.getJSONObject("session")),it.optBoolean("skipped"),it.str("runId")) },
            j.optBoolean("imperial"),j.str("sync")?.let(Instant::parse))
    }
    fun write(s: SavedState) {
        val g = s.goal; val c = s.constraints; val p = s.profile
        val j = JSONObject().put("schema",1).put("engine",ENGINE_VERSION).put("runs",JSONArray(s.runs.map(::runJson)))
            .put("cross",JSONArray(s.cross.map { JSONObject().put("id",it.id).put("start",it.start.toString()).put("duration",it.duration).put("strength",it.strength).put("hr",it.heartRate) }))
            .put("goal",JSONObject().put("distance",g.distance).put("date",g.raceDate?.toString()).put("target",g.targetSeconds).put("created",g.created.toString()).put("baseline",g.baselineSeconds).put("arrived",g.arrivedWeekly))
            .put("constraints",JSONObject().put("sessions",c.sessions).put("intensity",c.intensity).put("blackout",JSONArray(c.blackout.map { it.value })).put("longDay",c.longDay.value)
                .put("maxWeekly",c.maxWeekly).put("maxMinutes",c.maxMinutes).put("treadmill",c.treadmillOnly))
            .put("profile",JSONObject().put("birthYear",p.birthYear).put("resting",p.restingHeartRate).put("maxHr",p.observedMaxHeartRate).put("merge",p.mergeDuplicates))
            .put("feedback",JSONArray(s.feedback.map { JSONObject().put("at",it.at.toString()).put("soreness",it.soreness).put("energy",it.energy).put("niggle",it.niggle).put("note",it.note) }))
            .put("records",JSONArray(s.records.map { JSONObject().put("session",sessionJson(it.session)).put("skipped",it.skipped).put("runId",it.runId) }))
            .put("imperial",s.imperial).put("sync",s.lastSync?.toString())
        val temp = java.io.File(file.parentFile,"pacecraft-v1.tmp")
        temp.outputStream().use { it.write(j.toString().toByteArray()); it.fd.sync() }
        check(temp.renameTo(file)) { "Could not save your data" }
    }
    fun erase() { if(file.exists()) check(file.delete()) { "Could not clear local data" } }
}
