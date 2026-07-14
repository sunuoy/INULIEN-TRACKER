package com.example.data.api

import com.example.data.model.BloodPressureRecord
import com.example.data.model.GlucoseReading
import com.example.data.model.InsulinRecord
import com.example.data.model.Reminder
import com.example.data.model.UserProfile
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class SyncPayload(
    @Json(name = "user_name") val userName: String,
    @Json(name = "email_id") val emailId: String,
    @Json(name = "profile") val profile: UserProfileDto?,
    @Json(name = "glucose_readings") val glucoseReadings: List<GlucoseReadingDto>,
    @Json(name = "insulin_records") val insulinRecords: List<InsulinRecordDto>,
    @Json(name = "blood_pressure_records") val bloodPressureRecords: List<BloodPressureRecordDto>,
    @Json(name = "reminders") val reminders: List<ReminderDto>
)

@JsonClass(generateAdapter = true)
data class UserProfileDto(
    @Json(name = "userName") val userName: String,
    @Json(name = "doctorName") val doctorName: String,
    @Json(name = "doctorEmail") val doctorEmail: String,
    @Json(name = "doctorPhone") val doctorPhone: String,
    @Json(name = "targetGlucoseMin") val targetGlucoseMin: Double,
    @Json(name = "targetGlucoseMax") val targetGlucoseMax: Double,
    @Json(name = "glucoseUnit") val glucoseUnit: String,
    @Json(name = "cartridgeCapacity") val cartridgeCapacity: Double,
    @Json(name = "cartridgeRemaining") val cartridgeRemaining: Double
)

@JsonClass(generateAdapter = true)
data class GlucoseReadingDto(
    @Json(name = "readingValue") val readingValue: Double,
    @Json(name = "mealContext") val mealContext: String,
    @Json(name = "dateTimeMillis") val dateTimeMillis: Long,
    @Json(name = "notes") val notes: String
)

@JsonClass(generateAdapter = true)
data class InsulinRecordDto(
    @Json(name = "insulinType") val insulinType: String,
    @Json(name = "doseUnits") val doseUnits: Double,
    @Json(name = "dateTimeMillis") val dateTimeMillis: Long,
    @Json(name = "notes") val notes: String
)

@JsonClass(generateAdapter = true)
data class BloodPressureRecordDto(
    @Json(name = "systolic") val systolic: Int,
    @Json(name = "diastolic") val diastolic: Int,
    @Json(name = "pulse") val pulse: Int,
    @Json(name = "dateTimeMillis") val dateTimeMillis: Long,
    @Json(name = "notes") val notes: String
)

@JsonClass(generateAdapter = true)
data class ReminderDto(
    @Json(name = "reminderType") val reminderType: String,
    @Json(name = "label") val label: String,
    @Json(name = "hour") val hour: Int,
    @Json(name = "minute") val minute: Int,
    @Json(name = "isEnabled") val isEnabled: Boolean,
    @Json(name = "daysOfWeek") val daysOfWeek: String,
    @Json(name = "tone") val tone: String = "Default"
)

@JsonClass(generateAdapter = true)
data class SyncResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String?,
    @Json(name = "synced_timestamp") val syncedTimestamp: Long,
    @Json(name = "client_id") val clientId: String?,
    @Json(name = "server_records_count") val serverRecordsCount: Int?
)

interface GlucoBackendService {
    @POST("sync/upload")
    suspend fun uploadClinicalData(@Body payload: SyncPayload): SyncResponse

    // Fallback/standard debug HTTP post endpoint compatibility for raw servers and postman
    @POST("post")
    suspend fun postToHttpBin(@Body payload: SyncPayload): HttpBinResponse
}

@JsonClass(generateAdapter = true)
data class HttpBinResponse(
    @Json(name = "json") val json: SyncPayload?,
    @Json(name = "url") val url: String?
)

object GlucoBackendClient {
    private var currentUrl: String = ""
    private var serviceInstance: GlucoBackendService? = null

    fun getService(baseUrl: String): GlucoBackendService {
        val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (serviceInstance == null || currentUrl != sanitizedUrl) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()

            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl(sanitizedUrl)
                    .addConverterFactory(MoshiConverterFactory.create())
                    .client(okHttpClient)
                    .build()
                currentUrl = sanitizedUrl
                serviceInstance = retrofit.create(GlucoBackendService::class.java)
            } catch (e: Exception) {
                // If invalid URL structure, fallback to a safe base URL
                val retrofitFallback = Retrofit.Builder()
                    .baseUrl("https://httpbin.org/")
                    .addConverterFactory(MoshiConverterFactory.create())
                    .client(okHttpClient)
                    .build()
                currentUrl = "https://httpbin.org/"
                serviceInstance = retrofitFallback.create(GlucoBackendService::class.java)
            }
        }
        return serviceInstance!!
    }
}
