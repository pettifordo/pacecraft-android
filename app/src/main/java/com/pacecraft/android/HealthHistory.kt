package com.pacecraft.android

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.records.metadata.DataOrigin
import com.pacecraft.engine.*
import java.time.*

data class ImportedHistory(val runs: List<Run>, val cross: List<CrossTraining>, val historyAccess: Boolean, val missingDistances: Int)
class HealthHistory(private val context: Context) {
    fun status() = HealthConnectClient.getSdkStatus(context)
    private val client get() = HealthConnectClient.getOrCreate(context)
    val basicPermissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ElevationGainedRecord::class),
    )
    fun permissions(): Set<String> = basicPermissions + if (historySupported()) setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY) else emptySet()
    fun historySupported() = status() == HealthConnectClient.SDK_AVAILABLE && client.features.getFeatureStatus(
        HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    suspend fun granted(): Set<String> = if(status() == HealthConnectClient.SDK_AVAILABLE) client.permissionController.getGrantedPermissions() else emptySet()
    suspend fun read(now: Instant): ImportedHistory {
        val granted = granted()
        check(HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted && HealthPermission.getReadPermission(DistanceRecord::class) in granted) {
            "Allow exercise and distance access in Health Connect to import your runs."
        }
        val hasHistory = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted
        // Older history requires the optional history permission. No fabricated data when access is limited.
        val range = TimeRangeFilter.between(now.minusSeconds(182L*86400),now)
        val sessions = mutableListOf<ExerciseSessionRecord>()
        var token: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class,timeRangeFilter=range,pageToken=token,pageSize=500))
            sessions += response.records; token = response.pageToken
        } while(token != null)
        val runs = mutableListOf<Run>(); val cross = mutableListOf<CrossTraining>(); var missing = 0
        for (s in sessions) {
            val duration = Duration.between(s.startTime,s.endTime).toMillis()/1000.0
            if(duration <= 0 || s.endTime > now) continue
            val type = s.exerciseType
            val running = type == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING || type == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
            val strength = type in setOf(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING)
            val aerobic = type in setOf(ExerciseSessionRecord.EXERCISE_TYPE_BIKING,ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
                ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
                ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL,ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE)
            if(!running && !strength && !aerobic) continue
            val filter = TimeRangeFilter.between(s.startTime,s.endTime)
            val metrics = buildSet {
                if(running) add(DistanceRecord.DISTANCE_TOTAL)
                if(HealthPermission.getReadPermission(HeartRateRecord::class) in granted) add(HeartRateRecord.BPM_AVG)
                if(running && HealthPermission.getReadPermission(ElevationGainedRecord::class) in granted) add(ElevationGainedRecord.ELEVATION_GAINED_TOTAL)
            }
            // Match the session's source: summing all writers would double-count watch + app copies.
            val aggregate = if(metrics.isNotEmpty()) client.aggregate(AggregateRequest(metrics,filter,setOf(DataOrigin(s.metadata.dataOrigin.packageName)))) else null
            val hr = aggregate?.get(HeartRateRecord.BPM_AVG)?.toDouble()
            if(running) {
                val metres = aggregate?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters
                if(metres == null || metres <= 0) { missing++; continue }
                runs += Run("hc:${s.metadata.id}",s.startTime,metres,duration,hr,
                    aggregate[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters,
                    type == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,s.metadata.dataOrigin.packageName)
            } else cross += CrossTraining("hc:${s.metadata.id}",s.startTime,duration,strength,hr)
        }
        val uniqueCross = mutableListOf<CrossTraining>()
        cross.sortedWith(compareBy<CrossTraining> { it.start }.thenBy { it.id }).forEach { candidate ->
            val prior = uniqueCross.lastOrNull()
            val overlap = if(prior == null) 0.0 else Duration.between(maxOf(prior.start,candidate.start),
                minOf(prior.start.plusMillis((prior.duration*1000).toLong()),candidate.start.plusMillis((candidate.duration*1000).toLong()))).toMillis()/1000.0
            if(prior != null && overlap / minOf(prior.duration,candidate.duration) >= .9) {
                if(candidate.duration > prior.duration) uniqueCross[uniqueCross.lastIndex]=candidate
            } else uniqueCross += candidate
        }
        return ImportedHistory(runs,uniqueCross,hasHistory,missing)
    }
}
