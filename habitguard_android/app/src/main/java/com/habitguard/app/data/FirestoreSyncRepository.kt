package com.habitguard.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

data class FirestoreSyncResult(
    val uploaded: Boolean,
    val message: String,
)

class FirestoreSyncRepository(
    private val context: Context,
    private val database: HabitGuardDatabase,
    private val gateway: FirestoreGateway = FirestoreRestGateway(context),
) {
    private val prefs = context.getSharedPreferences("habitguard_firestore_sync", Context.MODE_PRIVATE)

    val isConsentEnabled: Boolean
        get() = prefs.getBoolean(KEY_CONSENT, false)

    fun setConsentEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONSENT, enabled).apply()
    }

    suspend fun syncConsentedSummary(userId: String = "local-device"): FirestoreSyncResult {
        if (!isConsentEnabled) {
            return FirestoreSyncResult(false, "사용자가 Firestore 동기화에 동의하지 않았습니다.")
        }

        val activeGoal = database.userGoalDao().active()
        val rules = database.ruleDao().enabledRules()
        val weekly = database.usageDailyDao().recent(7).reversed()
        val payload = mapOf(
            "schemaVersion" to 1,
            "syncedAt" to System.currentTimeMillis(),
            "date" to LocalDate.now().toString(),
            "privacyMode" to "summary_only_no_raw_app_usage_logs",
            "activeGoal" to activeGoal?.let {
                mapOf(
                    "targetName" to it.targetName,
                    "targetPackage" to it.targetPackage,
                    "timeRange" to it.timeRange,
                    "limitMinutes" to it.limitMinutes,
                    "intensity" to it.intensity,
                    "missionType" to it.missionType,
                )
            },
            "restrictionRules" to rules.map {
                mapOf(
                    "targetPackage" to it.targetPackage,
                    "targetName" to it.targetName,
                    "limitMinutes" to it.limitMinutes,
                    "startHour" to it.startHour,
                    "endHour" to it.endHour,
                    "restrictionMode" to it.restrictionMode,
                    "missionType" to it.missionType,
                    "unlockMinutes" to it.unlockMinutes,
                    "enabled" to it.enabled,
                )
            },
            "weeklySummary" to weekly.map {
                mapOf(
                    "date" to it.date,
                    "totalScreenTimeMinutes" to it.totalScreenTimeMinutes,
                    "nightMinutes" to it.nightMinutes,
                    "unlockCountEstimate" to it.unlockCountEstimate,
                    "topAppName" to it.topAppName,
                    "topAppMinutes" to it.topAppMinutes,
                )
            },
        )
        return gateway.uploadDocument(
            collectionPath = "users/$userId/habitguardSummaries",
            documentId = LocalDate.now().toString(),
            data = payload,
        )
    }

    companion object {
        private const val KEY_CONSENT = "consent_enabled"
    }
}

interface FirestoreGateway {
    suspend fun uploadDocument(collectionPath: String, documentId: String, data: Map<String, Any?>): FirestoreSyncResult
}

class FirestoreRestGateway(private val context: Context) : FirestoreGateway {
    private val prefs = context.getSharedPreferences("habitguard_firestore_sync", Context.MODE_PRIVATE)
    private val secureTokenStore = SecureTokenStore(context)

    override suspend fun uploadDocument(
        collectionPath: String,
        documentId: String,
        data: Map<String, Any?>,
    ): FirestoreSyncResult {
        val projectId = prefs.getString(KEY_PROJECT_ID, null).orEmpty()
        val bearerToken = secureTokenStore.getString(KEY_BEARER_TOKEN).orEmpty()
        if (projectId.isBlank() || bearerToken.isBlank()) {
            return FirestoreSyncResult(
                uploaded = false,
                message = "Firestore projectId 또는 OAuth bearer token이 설정되지 않아 업로드를 건너뜁니다.",
            )
        }

        val encodedPath = "$collectionPath/$documentId"
            .split("/")
            .joinToString("/") { segment -> java.net.URLEncoder.encode(segment, "UTF-8") }
        val url = URL("https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/$encodedPath")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $bearerToken")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val body = JSONObject().put("fields", toFirestoreFields(data)).toString()
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
        val code = connection.responseCode
        return if (code in 200..299) {
            FirestoreSyncResult(true, "Firestore 요약 동기화 완료")
        } else {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            FirestoreSyncResult(false, "Firestore 업로드 실패 HTTP $code $error")
        }
    }

    private fun toFirestoreFields(data: Map<String, Any?>): JSONObject {
        val fields = JSONObject()
        data.forEach { (key, value) -> fields.put(key, toFirestoreValue(value)) }
        return fields
    }

    private fun toFirestoreValue(value: Any?): JSONObject =
        when (value) {
            null -> JSONObject().put("nullValue", JSONObject.NULL)
            is Boolean -> JSONObject().put("booleanValue", value)
            is Int -> JSONObject().put("integerValue", value.toString())
            is Long -> JSONObject().put("integerValue", value.toString())
            is Float -> JSONObject().put("doubleValue", value.toDouble())
            is Double -> JSONObject().put("doubleValue", value)
            is Map<*, *> -> JSONObject().put("mapValue", JSONObject().put("fields", toFirestoreFields(value.asStringKeyMap())))
            is List<*> -> JSONObject().put(
                "arrayValue",
                JSONObject().put("values", JSONArray(value.map { toFirestoreValue(it) })),
            )
            else -> JSONObject().put("stringValue", value.toString())
        }

    private fun Map<*, *>.asStringKeyMap(): Map<String, Any?> =
        entries.associate { it.key.toString() to it.value }

    companion object {
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_BEARER_TOKEN = "bearer_token"
    }
}
