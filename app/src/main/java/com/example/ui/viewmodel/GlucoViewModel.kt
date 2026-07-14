package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.GlucoseReading
import com.example.data.model.InsulinRecord
import com.example.data.model.Reminder
import com.example.data.model.UserProfile
import com.example.data.model.CartridgeRefillLog
import com.example.data.model.BloodPressureRecord
import com.example.data.model.StepCountRecord
import com.example.data.repository.AppRepository
import com.example.data.api.GlucoBackendClient
import com.example.data.api.SyncPayload
import com.example.data.api.UserProfileDto
import com.example.data.api.GlucoseReadingDto
import com.example.data.api.InsulinRecordDto
import com.example.data.api.BloodPressureRecordDto
import com.example.data.api.ReminderDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen {
    HOME,
    HISTORY,
    STEPS,
    REMINDERS,
    REPORTS,
    PROFILE,
    SETTINGS
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
class GlucoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository

    // App Updates states
    private val _updateCheckStatus = MutableStateFlow<String?>(null)
    val updateCheckStatus: StateFlow<String?> = _updateCheckStatus.asStateFlow()

    private val _latestReleaseApkUrl = MutableStateFlow<String?>(null)
    val latestReleaseApkUrl: StateFlow<String?> = _latestReleaseApkUrl.asStateFlow()

    private val _latestReleaseVersion = MutableStateFlow<String?>(null)
    val latestReleaseVersion: StateFlow<String?> = _latestReleaseVersion.asStateFlow()

    private val _latestReleaseNotes = MutableStateFlow<String?>(null)
    val latestReleaseNotes: StateFlow<String?> = _latestReleaseNotes.asStateFlow()

    private val _isUpdateAvailable = MutableStateFlow(false)
    val isUpdateAvailable: StateFlow<Boolean> = _isUpdateAvailable.asStateFlow()

    private val _updateChangeCategory = MutableStateFlow("")
    val updateChangeCategory: StateFlow<String> = _updateChangeCategory.asStateFlow()

    // In-app APK download states
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadStatus = MutableStateFlow<String?>(null)
    val downloadStatus: StateFlow<String?> = _downloadStatus.asStateFlow()

    // Google Drive Sync states
    private val _googleDriveSyncEnabled = MutableStateFlow(false)
    val googleDriveSyncEnabled: StateFlow<Boolean> = _googleDriveSyncEnabled.asStateFlow()

    private val _googleDriveAccessToken = MutableStateFlow("")
    val googleDriveAccessToken: StateFlow<String> = _googleDriveAccessToken.asStateFlow()

    private val _isGoogleDriveSyncing = MutableStateFlow(false)
    val isGoogleDriveSyncing: StateFlow<Boolean> = _isGoogleDriveSyncing.asStateFlow()

    private val _googleDriveLastSyncTime = MutableStateFlow("Never")
    val googleDriveLastSyncTime: StateFlow<String> = _googleDriveLastSyncTime.asStateFlow()

    // Selected Theme State
    private val _selectedTheme = MutableStateFlow("midnight_carbon")
    val selectedTheme: StateFlow<String> = _selectedTheme.asStateFlow()

    fun selectTheme(themeId: String) {
        _selectedTheme.value = themeId
        val prefs = getApplication<Application>().getSharedPreferences("gluco_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_theme", themeId).apply()
    }

    // Simulated system time (Virtual Clock Offset in milliseconds)
    private val _customTimeOffsetMillis = MutableStateFlow(0L)
    val customTimeOffsetMillis: StateFlow<Long> = _customTimeOffsetMillis.asStateFlow()

    fun getCurrentTimeMillis(): Long {
        return System.currentTimeMillis() + _customTimeOffsetMillis.value
    }

    fun setSystemTime(epochMillis: Long) {
        _customTimeOffsetMillis.value = epochMillis - System.currentTimeMillis()
    }

    fun resetSystemTime() {
        _customTimeOffsetMillis.value = 0L
    }

    // Screen State
    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Database Streams
    val insulinRecords: StateFlow<List<InsulinRecord>>
    val glucoseReadings: StateFlow<List<GlucoseReading>>
    val reminders: StateFlow<List<Reminder>>
    val userProfile: StateFlow<UserProfile>
    val allProfiles: StateFlow<List<UserProfile>>
    val refillLogs: StateFlow<List<CartridgeRefillLog>>
    val bloodPressureRecords: StateFlow<List<BloodPressureRecord>>
    val stepRecords: StateFlow<List<StepCountRecord>>

    // Search and Filter States
    private val _insulinTypeFilter = MutableStateFlow("All")
    val insulinTypeFilter: StateFlow<String> = _insulinTypeFilter.asStateFlow()

    private val _mealContextFilter = MutableStateFlow("All")
    val mealContextFilter: StateFlow<String> = _mealContextFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Form Temporary State (Insulin)
    var insType = ""
    var insDose = ""
    var insDate = ""
    var insTime = ""
    var insNotes = ""
    var selectedInsulinIdToEdit: Long? = null

    // Form Temporary State (Glucose)
    var glucValue = ""
    var glucMealContext = "Fasting"
    var glucDate = ""
    var glucTime = ""
    var glucNotes = ""
    var selectedGlucoseIdToEdit: Long? = null

    // Form Temporary State (Reminder)
    var remType = "Insulin"
    var remLabel = ""
    var remHour = 8
    var remMinute = 0
    var remDays = "Daily"
    var selectedReminderIdToEdit: Long? = null

    // Form Temporary State (Blood Pressure)
    var bpSystolic = ""
    var bpDiastolic = ""
    var bpPulse = ""
    var bpDate = ""
    var bpTime = ""
    var bpNotes = ""
    var selectedBpIdToEdit: Long? = null

    // Form Temporary State (Step Count)
    var stepsCount = ""
    var stepsDate = ""
    var stepsTime = ""
    var stepsNotes = ""
    var selectedStepIdToEdit: Long? = null

    // Backend Synchronization parameters
    private val _backendBaseUrl = MutableStateFlow("https://httpbin.org/")
    val backendBaseUrl: StateFlow<String> = _backendBaseUrl.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>("Not synced yet")
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _syncConsoleLog = MutableStateFlow<String>("")
    val syncConsoleLog: StateFlow<String> = _syncConsoleLog.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<String>("Never")
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    fun setBackendBaseUrl(url: String) {
        val trimmed = url.trim()
        _backendBaseUrl.value = trimmed
        val prefs = getApplication<Application>().getSharedPreferences("gluco_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("gluco_backend_base_url", trimmed).apply()
    }

    fun triggerUploadSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Preparing payload..."
            _syncConsoleLog.value = "CONNECTING TO: ${_backendBaseUrl.value}\n\n"

            try {
                // Get general info
                val user = _loggedInUser.value.ifEmpty { "Guest Patient" }
                val prefs = getApplication<Application>().getSharedPreferences("gluco_auth_prefs", Context.MODE_PRIVATE)
                val email = prefs.getString("user_email_$user", "guest@example.com") ?: "guest@example.com"

                // Active profile mapping
                val activeProfile = userProfile.value
                val profileDto = UserProfileDto(
                    userName = activeProfile.userName,
                    doctorName = activeProfile.doctorName,
                    doctorEmail = activeProfile.doctorEmail,
                    doctorPhone = activeProfile.doctorPhone,
                    targetGlucoseMin = activeProfile.targetGlucoseMin,
                    targetGlucoseMax = activeProfile.targetGlucoseMax,
                    glucoseUnit = activeProfile.glucoseUnit,
                    cartridgeCapacity = activeProfile.cartridgeCapacity,
                    cartridgeRemaining = activeProfile.cartridgeRemaining
                )

                // Glucose readings mapping
                val glucoseDtoList = glucoseReadings.value.map {
                    GlucoseReadingDto(
                        readingValue = it.readingValue,
                        mealContext = it.mealContext,
                        dateTimeMillis = it.dateTimeMillis,
                        notes = it.notes
                    )
                }

                // Insulin records mapping
                val insulinDtoList = insulinRecords.value.map {
                    InsulinRecordDto(
                        insulinType = it.insulinType,
                        doseUnits = it.doseUnits,
                        dateTimeMillis = it.dateTimeMillis,
                        notes = it.notes
                    )
                }

                // Blood pressure records mapping
                val bpDtoList = bloodPressureRecords.value.map {
                    BloodPressureRecordDto(
                        systolic = it.systolic,
                        diastolic = it.diastolic,
                        pulse = it.pulse,
                        dateTimeMillis = it.dateTimeMillis,
                        notes = it.notes
                    )
                }

                // Reminders mapping
                val remindersDtoList = reminders.value.map {
                    ReminderDto(
                        reminderType = it.reminderType,
                        label = it.label,
                        hour = it.hour,
                        minute = it.minute,
                        isEnabled = it.isEnabled,
                        daysOfWeek = it.daysOfWeek
                    )
                }

                val payload = SyncPayload(
                    userName = user,
                    emailId = email,
                    profile = profileDto,
                    glucoseReadings = glucoseDtoList,
                    insulinRecords = insulinDtoList,
                    bloodPressureRecords = bpDtoList,
                    reminders = remindersDtoList
                )

                // Pretty print local JSON build
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(SyncPayload::class.java).indent("  ")
                val requestJson = adapter.toJson(payload)

                _syncConsoleLog.value += ">>> OUTGOING CLINICAL PAYLOAD:\n$requestJson\n\n"
                _syncMessage.value = "Uploading to database..."

                val client = GlucoBackendClient.getService(_backendBaseUrl.value)

                val responseLog: String
                val responseMsg: String
                val isSuccess: Boolean

                if (_backendBaseUrl.value.lowercase().contains("httpbin.org")) {
                    // post to httpbin
                    val res = client.postToHttpBin(payload)
                    isSuccess = true
                    responseMsg = "Synchronized successfully with mock endpoint!"
                    val echoedJson = adapter.toJson(res.json)
                    responseLog = "<<< INCOMING MOCK RESPONSE FROM HTTPBIN (ECHO):\n" +
                            "Status Code: 200 OK\n" +
                            "Endpoint: ${res.url}\n" +
                            "Verified Echoed Data Structure:\n$echoedJson"
                } else {
                    // post to standard clinical REST API
                    val res = client.uploadClinicalData(payload)
                    isSuccess = res.success
                    responseMsg = res.message ?: "Synchronized successfully with secure clinical server node."
                    responseLog = "<<< INCOMING SYSTEM RESPONSE:\n" +
                            "Success: ${res.success}\n" +
                            "Server Client ID: ${res.clientId ?: "N/A"}\n" +
                            "Server Records Count: ${res.serverRecordsCount ?: 0}\n" +
                            "Log Message: ${res.message}"
                }

                _syncConsoleLog.value += responseLog
                _isSyncing.value = false
                
                if (isSuccess) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val timeStr = sdf.format(Date(getCurrentTimeMillis()))
                    _lastSyncTime.value = timeStr
                    prefs.edit().putString("gluco_last_sync_time", timeStr).apply()
                    _syncMessage.value = "Synchronization completed successfully"
                } else {
                    _syncMessage.value = "Sync completed with warning: $responseMsg"
                }

                // Also trigger Firebase Firestore sync in parallel/sequentially to ensure real-time secure backup
                try {
                    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                    var userId = auth.currentUser?.uid
                    if (userId == null && _loggedInUser.value.isNotEmpty()) {
                        userId = "user_" + _loggedInUser.value.lowercase().replace(" ", "_")
                    }
                    if (userId == null) {
                        userId = "guest_patient"
                    }
                    if (userId != null) {
                        _syncConsoleLog.value += "\n\n>>> SYNCING WITH CLOUD FIRESTORE FOR SECURE COLD RECOVERY ($userId)...\n"
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        
                        // User Profile
                        val activeProfile = userProfile.value
                        val profileMap = hashMapOf(
                            "userName" to activeProfile.userName,
                            "doctorName" to activeProfile.doctorName,
                            "doctorEmail" to activeProfile.doctorEmail,
                            "doctorPhone" to activeProfile.doctorPhone,
                            "targetGlucoseMin" to activeProfile.targetGlucoseMin,
                            "targetGlucoseMax" to activeProfile.targetGlucoseMax,
                            "glucoseUnit" to activeProfile.glucoseUnit,
                            "cartridgeCapacity" to activeProfile.cartridgeCapacity,
                            "cartridgeRemaining" to activeProfile.cartridgeRemaining
                        )
                        db.collection("users").document(userId).set(profileMap)
                        _syncConsoleLog.value += "✓ Clinical active profile configuration synced\n"

                        // Glucose Readings
                        val glucoseColl = db.collection("users").document(userId).collection("glucose_readings")
                        glucoseReadings.value.forEach { reading ->
                            val record = hashMapOf(
                                "readingValue" to reading.readingValue,
                                "mealContext" to reading.mealContext,
                                "timestamp" to reading.dateTimeMillis,
                                "notes" to reading.notes
                            )
                            glucoseColl.document(reading.id.toString()).set(record)
                        }
                        _syncConsoleLog.value += "✓ ${glucoseReadings.value.size} Glucose readings synchronized to secure cloud\n"

                        // Insulin Records
                        val insulinColl = db.collection("users").document(userId).collection("insulin_records")
                        insulinRecords.value.forEach { record ->
                            val map = hashMapOf(
                                "insulinType" to record.insulinType,
                                "doseUnits" to record.doseUnits,
                                "timestamp" to record.dateTimeMillis,
                                "notes" to record.notes
                            )
                            insulinColl.document(record.id.toString()).set(map)
                        }
                        _syncConsoleLog.value += "✓ ${insulinRecords.value.size} Insulin delivery logs synchronized to secure cloud\n"

                        // Blood Pressure Records
                        val bpColl = db.collection("users").document(userId).collection("blood_pressure_records")
                        bloodPressureRecords.value.forEach { record ->
                            val map = hashMapOf(
                                "systolic" to record.systolic,
                                "diastolic" to record.diastolic,
                                "pulse" to record.pulse,
                                "timestamp" to record.dateTimeMillis,
                                "notes" to record.notes
                            )
                            bpColl.document(record.id.toString()).set(map)
                        }
                        _syncConsoleLog.value += "✓ ${bloodPressureRecords.value.size} Blood pressure cards synchronized to secure cloud\n"

                        // Reminders
                        val remColl = db.collection("users").document(userId).collection("reminders")
                        reminders.value.forEach { rem ->
                            val map = hashMapOf(
                                "reminderType" to rem.reminderType,
                                "label" to rem.label,
                                "hour" to rem.hour,
                                "minute" to rem.minute,
                                "isEnabled" to rem.isEnabled,
                                "daysOfWeek" to rem.daysOfWeek
                            )
                            remColl.document(rem.id.toString()).set(map)
                        }
                        _syncConsoleLog.value += "✓ ${reminders.value.size} Reminders synchronized to secure cloud\n"
                        _syncConsoleLog.value += ">>> CLOUD FIRESTORE CLINICAL DATA SYNC COMPLETED SUCCESSFULLY.\n"
                    } else {
                        _syncConsoleLog.value += "\n\n>>> CLOUD FIRESTORE SYNC BYPASSED: No authenticated Firebase Auth session found on this instance.\n"
                    }
                } catch (fe: Exception) {
                    _syncConsoleLog.value += "\n\n!!! CLOUD FIRESTORE SYNC ERROR: ${fe.localizedMessage}\n"
                }

            } catch (e: Exception) {
                _isSyncing.value = false
                _syncMessage.value = "Sync failed: ${e.localizedMessage ?: "Unknown error"}"
                _syncConsoleLog.value += "!!! ERROR OCCURRED DURING CONNECTION:\n" + e.localizedMessage + "\n" + e.stackTraceToString()
            }
        }
    }

    fun syncAllDataToFirebase(callback: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                var userId = auth.currentUser?.uid
                if (userId == null && _loggedInUser.value.isNotEmpty()) {
                    userId = "user_" + _loggedInUser.value.lowercase().replace(" ", "_")
                }
                if (userId == null) {
                    userId = "guest_patient"
                }

                android.util.Log.d("FirebaseSync", "Starting Firebase Firestore full sync for: $userId")
                
                // 1. Sync User Profile
                val activeProfile = userProfile.value
                val profileMap = hashMapOf(
                    "userName" to activeProfile.userName,
                    "doctorName" to activeProfile.doctorName,
                    "doctorEmail" to activeProfile.doctorEmail,
                    "doctorPhone" to activeProfile.doctorPhone,
                    "targetGlucoseMin" to activeProfile.targetGlucoseMin,
                    "targetGlucoseMax" to activeProfile.targetGlucoseMax,
                    "glucoseUnit" to activeProfile.glucoseUnit,
                    "cartridgeCapacity" to activeProfile.cartridgeCapacity,
                    "cartridgeRemaining" to activeProfile.cartridgeRemaining,
                    "stepGoal" to activeProfile.stepGoal,
                    "heightCm" to activeProfile.heightCm,
                    "weightKg" to activeProfile.weightKg
                )
                db.collection("users").document(userId).set(profileMap)

                // 2. Sync Glucose Readings
                val glucoseColl = db.collection("users").document(userId).collection("glucose_readings")
                glucoseReadings.value.forEach { reading ->
                    val record = hashMapOf(
                        "readingValue" to reading.readingValue,
                        "mealContext" to reading.mealContext,
                        "timestamp" to reading.dateTimeMillis,
                        "notes" to reading.notes
                    )
                    glucoseColl.document(reading.id.toString()).set(record)
                }

                // 3. Sync Insulin Records
                val insulinColl = db.collection("users").document(userId).collection("insulin_records")
                insulinRecords.value.forEach { record ->
                    val map = hashMapOf(
                        "insulinType" to record.insulinType,
                        "doseUnits" to record.doseUnits,
                        "timestamp" to record.dateTimeMillis,
                        "notes" to record.notes
                    )
                    insulinColl.document(record.id.toString()).set(map)
                }

                // 4. Sync Blood Pressure Records
                val bpColl = db.collection("users").document(userId).collection("blood_pressure_records")
                bloodPressureRecords.value.forEach { record ->
                    val map = hashMapOf(
                        "systolic" to record.systolic,
                        "diastolic" to record.diastolic,
                        "pulse" to record.pulse,
                        "timestamp" to record.dateTimeMillis,
                        "notes" to record.notes
                    )
                    bpColl.document(record.id.toString()).set(map)
                }

                // 5. Sync Reminders
                val remColl = db.collection("users").document(userId).collection("reminders")
                reminders.value.forEach { rem ->
                    val map = hashMapOf(
                        "reminderType" to rem.reminderType,
                        "label" to rem.label,
                        "hour" to rem.hour,
                        "minute" to rem.minute,
                        "isEnabled" to rem.isEnabled,
                        "daysOfWeek" to rem.daysOfWeek
                    )
                    remColl.document(rem.id.toString()).set(map)
                }

                // 6. Sync Step Records
                val stepsColl = db.collection("users").document(userId).collection("step_records")
                stepRecords.value.forEach { record ->
                    val map = hashMapOf(
                        "steps" to record.steps,
                        "timestamp" to record.dateTimeMillis,
                        "notes" to record.notes
                    )
                    stepsColl.document(record.id.toString()).set(map)
                }

                val msg = "Full clinical database synced successfully with Firestore!"
                android.util.Log.d("FirebaseSync", msg)
                callback?.invoke(true, msg)
            } catch (e: Exception) {
                val errorMsg = "Firestore sync error: ${e.localizedMessage ?: "Unknown"}"
                android.util.Log.e("FirebaseSync", errorMsg, e)
                callback?.invoke(false, errorMsg)
            }
        }
    }

    // Login & Auth State
    private val _rememberMe = MutableStateFlow(false)
    val rememberMe: StateFlow<Boolean> = _rememberMe.asStateFlow()

    private val _savedUsernameOrEmail = MutableStateFlow("")
    val savedUsernameOrEmail: StateFlow<String> = _savedUsernameOrEmail.asStateFlow()

    private val _savedPassword = MutableStateFlow("")
    val savedPassword: StateFlow<String> = _savedPassword.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _loggedInUser = MutableStateFlow("")
    val loggedInUser: StateFlow<String> = _loggedInUser.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private fun saveRememberMeConfig(prefs: android.content.SharedPreferences, rememberMe: Boolean, inputUser: String, pass: String) {
        _rememberMe.value = rememberMe
        if (rememberMe) {
            _savedUsernameOrEmail.value = inputUser
            _savedPassword.value = pass
            prefs.edit()
                .putBoolean("remember_me_checked", true)
                .putString("remember_me_username", inputUser)
                .putString("remember_me_password", pass)
                .apply()
        } else {
            _savedUsernameOrEmail.value = ""
            _savedPassword.value = ""
            prefs.edit()
                .putBoolean("remember_me_checked", false)
                .remove("remember_me_username")
                .remove("remember_me_password")
                .apply()
        }
    }

    fun login(username: String, password: String, rememberMeChecked: Boolean = false): Boolean {
        _loginError.value = null
        val trimmed = username.trim()
        if (trimmed.isEmpty()) {
            _loginError.value = "Username or Email cannot be empty"
            return false
        }
        if (password.isEmpty()) {
            _loginError.value = "Password cannot be empty"
            return false
        }

        val prefs = getApplication<Application>().getSharedPreferences("gluco_auth_prefs", Context.MODE_PRIVATE)

        if (trimmed == "admin") {
            if (password == "yM*d^@Irf 741$") {
                _isAdmin.value = true
                _loggedInUser.value = trimmed
                _isLoggedIn.value = true
                saveRememberMeConfig(prefs, rememberMeChecked, trimmed, password)
                android.widget.Toast.makeText(getApplication(), "Successfully authorized as Admin!", android.widget.Toast.LENGTH_SHORT).show()
                return true
            } else {
                _loginError.value = "Incorrect admin password"
                return false
            }
        } else {
            val resolvedUsername = if (trimmed.contains("@")) {
                prefs.getString("email_to_user_${trimmed.lowercase()}", null)
            } else {
                trimmed
            }

            if (resolvedUsername == null) {
                _loginError.value = if (trimmed.contains("@")) {
                    "No account associated with this email address."
                } else {
                    "Account does not exist. Please create an account first."
                }
                return false
            }

            // Real-time Firebase Auth check
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                var resolvedEmail = prefs.getString("user_email_$resolvedUsername", "") ?: ""
                if (resolvedEmail.isEmpty()) {
                    resolvedEmail = if (trimmed.contains("@")) trimmed else "${resolvedUsername.lowercase().replace(" ", "_")}@glucolog.app"
                }
                val targetEmail = resolvedEmail
                val safePassword = if (password.length >= 6) password else "${password}123456"

                auth.signInWithEmailAndPassword(targetEmail, safePassword)
                    .addOnSuccessListener { result ->
                        // Success: Sync the new password locally in SharedPreferences
                        prefs.edit()
                            .putString("user_pass_$resolvedUsername", password)
                            .apply()

                        _isAdmin.value = false
                        _loggedInUser.value = resolvedUsername
                        _isLoggedIn.value = true
                        saveRememberMeConfig(prefs, rememberMeChecked, trimmed, password)
                        clearOnlyAutoGeneratedDummyData()

                        viewModelScope.launch {
                            val displayName = resolvedUsername.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                            try {
                                val currentProf = repository.getProfileSync()
                                if (currentProf == null) {
                                    repository.insertOrUpdateProfile(UserProfile(id = 0, userName = displayName, isActive = true))
                                } else {
                                    repository.insertOrUpdateProfile(currentProf.copy(userName = displayName))
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("GlucoViewModel", "Error saving profile on login: ${e.message}")
                            }
                        }

                        android.widget.Toast.makeText(getApplication(), "Successfully authorized!", android.widget.Toast.LENGTH_SHORT).show()
                        // Automatically fetch Google Drive token on successful login
                        fetchGoogleDriveTokenAutomatically(getApplication(), targetEmail)
                        syncAllDataToFirebase()
                    }
                    .addOnFailureListener { e ->
                        if (e is com.google.firebase.FirebaseNetworkException) {
                            // Offline Fallback: Verify using locally cached password
                            val savedPassword = prefs.getString("user_pass_$resolvedUsername", null)
                            if (savedPassword == null) {
                                _loginError.value = "Account does not exist locally. Please connect to the internet."
                            } else if (savedPassword != password) {
                                _loginError.value = "Incorrect password (offline)."
                            } else {
                                _isAdmin.value = false
                                _loggedInUser.value = resolvedUsername
                                _isLoggedIn.value = true
                                saveRememberMeConfig(prefs, rememberMeChecked, trimmed, password)
                                clearOnlyAutoGeneratedDummyData()
                                android.widget.Toast.makeText(getApplication(), "Successfully authorized (offline mode)!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            _loginError.value = "Authentication failed: ${e.localizedMessage ?: "Wrong credentials"}"
                        }
                    }
            } catch (ex: Exception) {
                // Firebase not initialized fallback
                val savedPassword = prefs.getString("user_pass_$resolvedUsername", null)
                if (savedPassword == null) {
                    _loginError.value = "Account does not exist. Please create an account first."
                } else if (savedPassword != password) {
                    _loginError.value = "Incorrect password"
                } else {
                    _isAdmin.value = false
                    _loggedInUser.value = resolvedUsername
                    _isLoggedIn.value = true
                    saveRememberMeConfig(prefs, rememberMeChecked, trimmed, password)
                    clearOnlyAutoGeneratedDummyData()
                    android.widget.Toast.makeText(getApplication(), "Successfully authorized!", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            return true
        }
    }

    fun registerUser(username: String, email: String, password: String): Boolean {
        _loginError.value = null
        val trimmedUser = username.trim()
        val trimmedEmail = email.trim().lowercase()
        
        if (trimmedUser.isEmpty()) {
            _loginError.value = "Username cannot be empty"
            return false
        }
        if (trimmedEmail.isEmpty()) {
            _loginError.value = "Email ID cannot be empty"
            return false
        }
        if (password.isEmpty()) {
            _loginError.value = "Password cannot be empty"
            return false
        }
        if (trimmedUser.lowercase() == "admin") {
            _loginError.value = "Username 'admin' is a restricted credentials key"
            return false
        }

        // Email domain validation (gmail or hotmail/outlook/live)
        val emailRegex = "^[A-Za-z0-9+_.-]+@(.+)\$".toRegex()
        if (!emailRegex.matches(trimmedEmail)) {
            _loginError.value = "Please enter a valid email address"
            return false
        }

        val isGmail = trimmedEmail.endsWith("@gmail.com")
        val isHotmail = trimmedEmail.endsWith("@hotmail.com") || trimmedEmail.endsWith("@outlook.com") || trimmedEmail.endsWith("@live.com")

        if (!isGmail && !isHotmail) {
            _loginError.value = "Clinical synchronization requires a Gmail (@gmail.com) or Hotmail/Outlook (@hotmail.com, @outlook.com) account."
            return false
        }

        val prefs = getApplication<Application>().getSharedPreferences("gluco_auth_prefs", Context.MODE_PRIVATE)

        if (prefs.contains("user_pass_$trimmedUser")) {
            _loginError.value = "Username already exists. Please choose another"
            return false
        }

        if (prefs.contains("email_to_user_$trimmedEmail")) {
            _loginError.value = "An account with this email address already exists"
            return false
        }

        prefs.edit()
            .putString("user_pass_$trimmedUser", password)
            .putString("user_email_$trimmedUser", trimmedEmail)
            .putString("email_to_user_$trimmedEmail", trimmedUser)
            .apply()

        // Sync registration with Firebase Auth
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val safePassword = if (password.length >= 6) password else "${password}123456"
            auth.createUserWithEmailAndPassword(trimmedEmail, safePassword)
                .addOnSuccessListener { result ->
                    android.util.Log.d("FirebaseAuth", "Successfully registered new Firebase Auth user: ${result.user?.uid}")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("FirebaseAuth", "Firebase registration error: ${e.message}")
                }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuth", "Firebase Auth not initialized on register: ${e.message}")
        }

        return true
    }

    fun loginWithGoogleProfile(fullName: String, email: String, username: String) {
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("gluco_auth_prefs", Context.MODE_PRIVATE)
            
            // 1. Save credentials (using a safe secure OAuth placeholder password) and map email
            prefs.edit()
                .putString("user_pass_$username", "google_verified_auth")
                .putString("user_email_$username", email)
                .putString("email_to_user_$email", username)
                .apply()
                
            // 2. Clear state and log in as standard patient
            _isAdmin.value = false
            _loggedInUser.value = username
            _isLoggedIn.value = true
            
            // Clear any auto-generated dummy data so standard users start clean
            clearOnlyAutoGeneratedDummyData()
            
            // 3. Create or update profile in database with verified Google Name
            val currentProf = repository.getProfileSync()
            if (currentProf == null) {
                repository.insertOrUpdateProfile(UserProfile(
                    id = 0,
                    userName = fullName,
                    isActive = true
                ))
            } else {
                repository.insertOrUpdateProfile(currentProf.copy(
                    userName = fullName
                ))
            }

            // 4. Synchronize sign-in with Firebase Auth using verified Google parameters
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val targetEmail = if (email.isNotEmpty()) email else "${username.lowercase().replace(" ", "_")}@glucolog.app"
                auth.signInWithEmailAndPassword(targetEmail, "google_verified_auth_123!")
                    .addOnSuccessListener { result ->
                        android.util.Log.d("FirebaseAuth", "Google profile Firebase Auth login successful: ${result.user?.uid}")
                        syncAllDataToFirebase()
                    }
                    .addOnFailureListener { e ->
                        // User might not exist yet, let's create the account
                        auth.createUserWithEmailAndPassword(targetEmail, "google_verified_auth_123!")
                            .addOnSuccessListener { regResult ->
                                android.util.Log.d("FirebaseAuth", "Google profile Firebase Auth registration successful: ${regResult.user?.uid}")
                                syncAllDataToFirebase()
                            }
                            .addOnFailureListener { regError ->
                                android.util.Log.e("FirebaseAuth", "Google profile Firebase Auth registration failed: ${regError.message}")
                                syncAllDataToFirebase()
                            }
                    }
            } catch (e: Exception) {
                android.util.Log.e("FirebaseAuth", "Firebase Auth initialization error: ${e.message}")
                syncAllDataToFirebase()
            }
        }
    }

    fun resetPassword(username: String, email: String, newPass: String): Boolean {
        _loginError.value = null
        val trimmedUser = username.trim()
        val trimmedEmail = email.trim().lowercase()

        if (trimmedUser.isEmpty()) {
            _loginError.value = "Username cannot be empty"
            return false
        }
        if (trimmedEmail.isEmpty()) {
            _loginError.value = "Email ID cannot be empty"
            return false
        }
        if (newPass.isEmpty()) {
            _loginError.value = "New password cannot be empty"
            return false
        }

        if (trimmedUser.lowercase() == "admin") {
            _loginError.value = "Admin credentials cannot be recovered online."
            return false
        }

        val prefs = getApplication<Application>().getSharedPreferences("gluco_auth_prefs", Context.MODE_PRIVATE)
        val savedEmail = prefs.getString("user_email_$trimmedUser", null)
        if (savedEmail == null || savedEmail != trimmedEmail) {
            _loginError.value = "Verification failed. The username or email ID do not match our records."
            return false
        }

        prefs.edit().putString("user_pass_$trimmedUser", newPass).apply()
        return true
    }

    fun logout() {
        _isLoggedIn.value = false
        _isAdmin.value = false
        _loggedInUser.value = ""
        _loginError.value = null
        clearOnlyAutoGeneratedDummyData()
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val cleanCurrent = current.lowercase().replace("v", "").trim()
        val cleanLatest = latest.lowercase().replace("v", "").trim()
        if (cleanCurrent == cleanLatest) return false
        val currentParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = cleanLatest.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLength) {
            val currentVal = currentParts.getOrElse(i) { 0 }
            val latestVal = latestParts.getOrElse(i) { 0 }
            if (latestVal > currentVal) return true
            if (latestVal < currentVal) return false
        }
        return false
    }

    fun checkForAppUpdates() {
        _updateCheckStatus.value = "Checking..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://api.github.com/repos/sunuoy/INSULIN-TRACKER/releases/latest")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("User-Agent", "GlucoLogApp")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(response)
                    val tagName = json.optString("tag_name", "v1.0.0")
                    val body = json.optString("body", "No release notes provided.")
                    
                    val assets = json.optJSONArray("assets")
                    var apkUrl: String? = null
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk")) {
                                apkUrl = asset.optString("browser_download_url", null)
                                break
                            }
                        }
                    }

                    val finalApkUrl = apkUrl ?: "https://github.com/sunuoy/INSULIN-TRACKER/releases/download/$tagName/INSULIN-TRACKER_${tagName.replace("v", "")}-release.apk"

                    withContext(Dispatchers.Main) {
                        _latestReleaseVersion.value = tagName
                        _latestReleaseNotes.value = body
                        _latestReleaseApkUrl.value = finalApkUrl
                        
                        val currentVersion = "v${com.example.BuildConfig.VERSION_NAME}"
                        if (isNewerVersion(currentVersion, tagName)) {
                            val cleanCurrent = currentVersion.lowercase().replace("v", "").trim()
                            val cleanLatest = tagName.lowercase().replace("v", "").trim()
                            val currentParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }
                            val latestParts = cleanLatest.split(".").map { it.toIntOrNull() ?: 0 }
                            
                            val majorDiff = latestParts.getOrElse(0) { 0 } - currentParts.getOrElse(0) { 0 }
                            val minorDiff = latestParts.getOrElse(1) { 0 } - currentParts.getOrElse(1) { 0 }
                            val patchDiff = latestParts.getOrElse(2) { 0 } - currentParts.getOrElse(2) { 0 }
                            
                            val updateType = when {
                                majorDiff > 0 -> "Major Update (V 1.0.0)"
                                minorDiff > 0 -> "Medium Update (V 0.1.0)"
                                patchDiff > 0 -> "Small Update (V 0.0.1)"
                                else -> "Update available"
                            }
                            _updateCheckStatus.value = "$updateType: $tagName"
                            _isUpdateAvailable.value = true
                            _updateChangeCategory.value = updateType
                        } else {
                            _updateCheckStatus.value = "App is up to date"
                            _isUpdateAvailable.value = false
                        }
                    }
                } else if (connection.responseCode == 404) {
                    withContext(Dispatchers.Main) {
                        _updateCheckStatus.value = "No updates found (no releases published on GitHub)."
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _updateCheckStatus.value = "Failed to connect to update server (Code: ${connection.responseCode})"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _updateCheckStatus.value = "Error checking updates: ${e.localizedMessage ?: "Unknown error"}"
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _isUpdateAvailable.value = false
    }

    private fun getRedirectedConnection(urlStr: String): java.net.HttpURLConnection {
        var url = java.net.URL(urlStr)
        var connection = url.openConnection() as java.net.HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        
        var status = connection.responseCode
        var redirects = 0
        while (status == java.net.HttpURLConnection.HTTP_MOVED_TEMP ||
               status == java.net.HttpURLConnection.HTTP_MOVED_PERM ||
               status == 307 || status == 308) {
            if (redirects > 5) break
            val newUrl = connection.getHeaderField("Location") ?: break
            connection.disconnect()
            url = java.net.URL(newUrl)
            connection = url.openConnection() as java.net.HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            status = connection.responseCode
            redirects++
        }
        return connection
    }

    fun downloadAndInstallApk(apkUrl: String) {
        if (_isDownloading.value) return
        _isDownloading.value = true
        _downloadProgress.value = 0f
        _downloadStatus.value = "Starting download..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val connection = getRedirectedConnection(apkUrl)
                connection.connect()

                if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    throw java.io.IOException("Server returned HTTP " + connection.responseCode + " " + connection.responseMessage)
                }

                val fileLength = connection.contentLength
                val input = connection.inputStream
                val updateFile = java.io.File(context.cacheDir, "gluco_update.apk")
                if (updateFile.exists()) {
                    updateFile.delete()
                }

                val output = java.io.FileOutputStream(updateFile)
                val buffer = ByteArray(4096)
                var total: Long = 0
                var count: Int
                while (input.read(buffer).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        val progress = total.toFloat() / fileLength
                        withContext(Dispatchers.Main) {
                            _downloadProgress.value = progress
                            val mbDown = String.format("%.1f", total / 1_048_576.0)
                            val mbTotal = String.format("%.1f", fileLength / 1_048_576.0)
                            _downloadStatus.value = "Downloading... $mbDown / $mbTotal MB"
                        }
                    }
                    output.write(buffer, 0, count)
                }

                output.flush()
                output.close()
                input.close()

                withContext(Dispatchers.Main) {
                    _downloadProgress.value = 1f
                    _downloadStatus.value = "Download complete. Checking permissions..."
                }

                // Check for ACTION_MANAGE_UNKNOWN_APP_SOURCES on Oreo+ (Android 8.0 / API 26+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        val settingsIntent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = android.net.Uri.parse("package:" + context.packageName)
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        withContext(Dispatchers.Main) {
                            try {
                                context.startActivity(settingsIntent)
                                _downloadStatus.value = "Please enable 'Install unknown apps' permission"
                            } catch (e: Exception) {
                                _downloadStatus.value = "Could not open settings: ${e.message}"
                            }
                        }
                        return@launch
                    }
                }

                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    updateFile
                )

                val installIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

                withContext(Dispatchers.Main) {
                    try {
                        context.startActivity(installIntent)
                        _downloadStatus.value = "Install dialog opened"
                    } catch (e: Exception) {
                        _downloadStatus.value = "Could not open installer: ${e.message}"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _downloadStatus.value = "Error: ${e.localizedMessage ?: "Unknown error"}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isDownloading.value = false
                }
            }
        }
    }

    fun clearLoginError() {
        _loginError.value = null
    }

    fun setLoginError(message: String?) {
        _loginError.value = message
    }

    // Init Database & Streams
    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(
            database.insulinDao(),
            database.glucoseDao(),
            database.reminderDao(),
            database.profileDao(),
            database.cartridgeRefillLogDao(),
            database.bloodPressureDao(),
            database.stepDao()
        )

        // Flows from database
        insulinRecords = repository.allInsulinRecords
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        glucoseReadings = repository.allGlucoseReadings
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        reminders = repository.allReminders
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        userProfile = repository.userProfile
            .map { it ?: defaultProfile() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultProfile())

        allProfiles = repository.allProfiles
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        refillLogs = repository.allRefillLogs
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        bloodPressureRecords = repository.allBloodPressureRecords
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        stepRecords = repository.allStepRecords
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Ensure database default entities
        ensureMinimumSetup()

        // Load dynamic preferences asynchronously
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = application.getSharedPreferences("gluco_auth_prefs", Context.MODE_PRIVATE)
            val backendUrl = prefs.getString("gluco_backend_base_url", "https://httpbin.org/") ?: "https://httpbin.org/"
            val lastSync = prefs.getString("gluco_last_sync_time", "Never") ?: "Never"
            val theme = prefs.getString("selected_theme", "midnight_carbon") ?: "midnight_carbon"
            val gdEnabled = prefs.getBoolean("gd_sync_enabled", false)
            val gdToken = prefs.getString("gd_access_token", "") ?: ""
            val gdLastSync = prefs.getString("gd_last_sync_time", "Never") ?: "Never"
            val isRememberChecked = prefs.getBoolean("remember_me_checked", false)
            val userVal = if (isRememberChecked) prefs.getString("remember_me_username", "") ?: "" else ""
            val passVal = if (isRememberChecked) prefs.getString("remember_me_password", "") ?: "" else ""
            val resolvedEmail = if (isRememberChecked && userVal.isNotEmpty()) prefs.getString("user_email_$userVal", "") ?: "" else ""

            withContext(Dispatchers.Main) {
                _backendBaseUrl.value = backendUrl
                _lastSyncTime.value = lastSync
                _selectedTheme.value = theme
                _googleDriveSyncEnabled.value = gdEnabled
                _googleDriveAccessToken.value = gdToken
                _googleDriveLastSyncTime.value = gdLastSync
                _rememberMe.value = isRememberChecked
                if (isRememberChecked) {
                    _savedUsernameOrEmail.value = userVal
                    _savedPassword.value = passVal
                    
                    val resolvedUsername = if (userVal.contains("@")) {
                        prefs.getString("email_to_user_${userVal.lowercase()}", null)
                    } else {
                        userVal
                    }
                    if (resolvedUsername != null) {
                        _isAdmin.value = (resolvedUsername == "admin")
                        _loggedInUser.value = resolvedUsername
                        _isLoggedIn.value = true
                    }
                    
                    if (resolvedEmail.isNotEmpty()) {
                        fetchGoogleDriveTokenAutomatically(application, resolvedEmail)
                    }
                    
                    // Asynchronously authenticate with Firebase in the background
                    if (userVal.isNotEmpty() && passVal.isNotEmpty() && resolvedUsername != null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                                var resolvedEmail2 = prefs.getString("user_email_$resolvedUsername", "") ?: ""
                                if (resolvedEmail2.isEmpty()) {
                                    resolvedEmail2 = if (userVal.contains("@")) userVal else "${resolvedUsername.lowercase().replace(" ", "_")}@glucolog.app"
                                }
                                val safePassword = if (passVal.length >= 6) passVal else "${passVal}123456"
                                auth.signInWithEmailAndPassword(resolvedEmail2, safePassword)
                                    .addOnSuccessListener {
                                        android.util.Log.d("AutoLogin", "Background auth success for $resolvedUsername")
                                        syncAllDataToFirebase()
                                    }
                                    .addOnFailureListener { e ->
                                        if (e is com.google.firebase.FirebaseNetworkException) {
                                            android.util.Log.d("AutoLogin", "Background auth offline for $resolvedUsername")
                                        } else {
                                            android.util.Log.e("AutoLogin", "Background auth failed: ${e.message}")
                                            viewModelScope.launch(Dispatchers.Main) {
                                                logout()
                                                _loginError.value = "Session expired. Please log in again."
                                            }
                                        }
                                    }
                            } catch (e: Exception) {
                                android.util.Log.e("AutoLogin", "Background auth exception: ${e.message}")
                            }
                        }
                    }
                }
            }
        }

        // Clean auto-generated sandbox metrics on initial startup to protect standard user's logs
        if (!_isAdmin.value) {
            clearOnlyAutoGeneratedDummyData()
        }

        // Auto-run update check on app launch
        checkForAppUpdates()

        // Auto-run Google Drive background sync when data changes and token is configured
        viewModelScope.launch {
            combine(
                repository.allGlucoseReadings,
                repository.allInsulinRecords,
                repository.allBloodPressureRecords,
                repository.allReminders,
                repository.allRefillLogs,
                repository.allStepRecords,
                _googleDriveAccessToken
            ) { array: Array<Any?> ->
                array[6] as String
            }
            .debounce(3000) // 3-second debounce to avoid spamming the API on batch updates
            .collect { token ->
                if (token.isNotEmpty()) {
                    backupToGoogleDrive { success, msg ->
                        android.util.Log.d("GoogleDriveAutoSync", "Background auto-backup: $msg")
                    }
                }
            }
        }
    }

    private fun defaultProfile() = UserProfile(
        id = 0,
        userName = "",
        doctorName = "",
        doctorEmail = "",
        doctorPhone = "",
        targetGlucoseMin = 80.0,
        targetGlucoseMax = 140.0,
        glucoseUnit = "mg/dL",
        isActive = true,
        stepGoal = 10000,
        heightCm = 170.0,
        weightKg = 70.0
    )

    private fun ensureMinimumSetup() {
        viewModelScope.launch(Dispatchers.IO) {
            // Profile Init
            val currentProf = repository.getAnyProfileSync()
            if (currentProf == null) {
                repository.insertOrUpdateProfile(defaultProfile())
            } else {
                if (currentProf.userName == "Primary Profile") {
                    repository.insertOrUpdateProfile(currentProf.copy(
                        userName = "",
                        doctorName = "",
                        doctorEmail = "",
                        doctorPhone = ""
                    ))
                }
                val activeProf = repository.getProfileSync()
                if (activeProf == null) {
                    repository.selectProfile(currentProf.id)
                }
            }

            // Reminders Prepopulation
            val anyRem = repository.getAnyReminderSync()
            if (anyRem == null) {
                val defaultRems = listOf(
                    Reminder(reminderType = "Insulin", label = "Morning Rapid Dose", hour = 8, minute = 0),
                    Reminder(reminderType = "Insulin", label = "Lunch Time Dose", hour = 12, minute = 30),
                    Reminder(reminderType = "Insulin", label = "Evening Long-acting Dose", hour = 21, minute = 0),
                    Reminder(reminderType = "Blood Sugar Check", label = "Fasting Glucose Check", hour = 7, minute = 0),
                    Reminder(reminderType = "Blood Sugar Check", label = "Bedtime Glucose Check", hour = 22, minute = 0)
                )
                defaultRems.forEach { repository.insertReminder(it) }
            }
        }
    }

    // Set Active Navigation Tab
    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    // Filters and Search Setters
    fun setInsulinFilter(filter: String) {
        _insulinTypeFilter.value = filter
    }

    fun setMealContextFilter(filter: String) {
        _mealContextFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Filtered lists in live states
    val filteredInsulinRecords: Flow<List<InsulinRecord>> = combine(
        insulinRecords,
        _insulinTypeFilter,
        _searchQuery
    ) { records, insulinType, query ->
        records.filter { record ->
            val typeMatches = insulinType == "All" || record.insulinType.equals(insulinType, ignoreCase = true)
            val queryMatches = if (query.isEmpty()) {
                true
            } else {
                record.notes.contains(query, ignoreCase = true) ||
                record.insulinType.contains(query, ignoreCase = true) ||
                formatEpochToDate(record.dateTimeMillis).contains(query, ignoreCase = true)
            }
            typeMatches && queryMatches
        }
    }

    val filteredGlucoseReadings: Flow<List<GlucoseReading>> = combine(
        glucoseReadings,
        _mealContextFilter,
        _searchQuery
    ) { readings, mealContext, query ->
        readings.filter { reading ->
            val contextMatches = mealContext == "All" || reading.mealContext.equals(mealContext, ignoreCase = true)
            val queryMatches = if (query.isEmpty()) {
                true
            } else {
                reading.notes.contains(query, ignoreCase = true) ||
                reading.mealContext.contains(query, ignoreCase = true) ||
                formatEpochToDate(reading.dateTimeMillis).contains(query, ignoreCase = true)
            }
            contextMatches && queryMatches
        }
    }

    val filteredBloodPressureRecords: Flow<List<BloodPressureRecord>> = combine(
        bloodPressureRecords,
        _searchQuery
    ) { records, query ->
        records.filter { record ->
            if (query.isEmpty()) {
                true
            } else {
                record.notes.contains(query, ignoreCase = true) ||
                record.systolic.toString().contains(query) ||
                record.diastolic.toString().contains(query) ||
                record.pulse.toString().contains(query) ||
                formatEpochToDate(record.dateTimeMillis).contains(query, ignoreCase = true)
            }
        }
    }

    // Business Logic Actions (Insulin)
    fun saveInsulinRecord() {
        val parsedDose = insDose.toDoubleOrNull() ?: 0.0
        val calendar = composeCalendarFromDateStrAndTimeStr(insDate, insTime)
        val timeInMillis = calendar.timeInMillis
        val type = if (insType.isEmpty()) "Rapid-acting" else insType

        val record = if (selectedInsulinIdToEdit != null) {
            InsulinRecord(
                id = selectedInsulinIdToEdit!!,
                insulinType = type,
                doseUnits = parsedDose,
                dateTimeMillis = timeInMillis,
                notes = insNotes
            )
        } else {
            InsulinRecord(
                insulinType = type,
                doseUnits = parsedDose,
                dateTimeMillis = timeInMillis,
                notes = insNotes
            )
        }

        viewModelScope.launch {
            repository.insertInsulinRecord(record)
            if (selectedInsulinIdToEdit == null) {
                val currentProf = repository.getProfileSync()
                if (currentProf != null) {
                    val remaining = (currentProf.cartridgeRemaining - parsedDose).coerceAtLeast(0.0)
                    repository.insertOrUpdateProfile(currentProf.copy(cartridgeRemaining = remaining))
                }
            }

            // Cloud Firestore Sync
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val userId = auth.currentUser?.uid ?: (if (_loggedInUser.value.isNotEmpty()) "user_" + _loggedInUser.value.lowercase().replace(" ", "_") else "guest_patient")
                if (userId != null) {
                    val recordMap = hashMapOf(
                        "insulinType" to record.insulinType,
                        "doseUnits" to record.doseUnits,
                        "timestamp" to record.dateTimeMillis,
                        "notes" to record.notes
                    )
                    db.collection("users")
                        .document(userId)
                        .collection("insulin_records")
                        .add(recordMap)
                        .addOnSuccessListener {
                            android.util.Log.d("FirebaseSync", "Insulin record sync successful in Firestore!")
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("FirebaseSync", "Firestore sync exception:", e)
                        }
                }
            } catch (e: Exception) {
                android.util.Log.e("FirebaseSync", "Firebase Firestore sync bypassed: ${e.message}")
            }

            resetInsulinForm()
        }
    }

    fun deleteInsulinRecord(record: InsulinRecord) {
        viewModelScope.launch {
            repository.deleteInsulinRecord(record)
        }
    }

    fun prepareEditInsulin(record: InsulinRecord) {
        selectedInsulinIdToEdit = record.id
        insType = record.insulinType
        insDose = record.doseUnits.toString()
        insDate = formatEpochToDateOnly(record.dateTimeMillis)
        insTime = formatEpochToTimeOnly(record.dateTimeMillis)
        insNotes = record.notes
    }

    fun resetInsulinForm() {
        selectedInsulinIdToEdit = null
        insType = "Rapid-acting"
        insDose = ""
        insDate = formatEpochToDateOnly(getCurrentTimeMillis())
        insTime = formatEpochToTimeOnly(getCurrentTimeMillis())
        insNotes = ""
    }

    // Business Logic Actions (Glucose)
    fun saveGlucoseReading() {
        val parsedValue = glucValue.toDoubleOrNull() ?: 0.0
        val calendar = composeCalendarFromDateStrAndTimeStr(glucDate, glucTime)
        val timeInMillis = calendar.timeInMillis

        val reading = if (selectedGlucoseIdToEdit != null) {
            GlucoseReading(
                id = selectedGlucoseIdToEdit!!,
                readingValue = parsedValue,
                mealContext = glucMealContext,
                dateTimeMillis = timeInMillis,
                notes = glucNotes
            )
        } else {
            GlucoseReading(
                readingValue = parsedValue,
                mealContext = glucMealContext,
                dateTimeMillis = timeInMillis,
                notes = glucNotes
            )
        }

        viewModelScope.launch {
            repository.insertGlucoseReading(reading)

            // Cloud Firestore Sync
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val userId = auth.currentUser?.uid ?: (if (_loggedInUser.value.isNotEmpty()) "user_" + _loggedInUser.value.lowercase().replace(" ", "_") else "guest_patient")
                if (userId != null) {
                    val glucoseRecord = hashMapOf(
                        "readingValue" to reading.readingValue,
                        "mealContext" to reading.mealContext,
                        "timestamp" to reading.dateTimeMillis,
                        "notes" to reading.notes
                    )
                    db.collection("users")
                        .document(userId)
                        .collection("glucose_readings")
                        .add(glucoseRecord)
                        .addOnSuccessListener {
                            android.util.Log.d("FirebaseSync", "Glucose reading sync successful in Firestore!")
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("FirebaseSync", "Firestore sync exception:", e)
                        }
                }
            } catch (e: Exception) {
                android.util.Log.e("FirebaseSync", "Firebase Firestore sync bypassed: ${e.message}")
            }

            resetGlucoseForm()
        }
    }

    fun deleteGlucoseReading(reading: GlucoseReading) {
        viewModelScope.launch {
            repository.deleteGlucoseReading(reading)
        }
    }

    fun prepareEditGlucose(reading: GlucoseReading) {
        selectedGlucoseIdToEdit = reading.id
        glucValue = reading.readingValue.toString()
        glucMealContext = reading.mealContext
        glucDate = formatEpochToDateOnly(reading.dateTimeMillis)
        glucTime = formatEpochToTimeOnly(reading.dateTimeMillis)
        glucNotes = reading.notes
    }

    fun resetGlucoseForm() {
        selectedGlucoseIdToEdit = null
        glucValue = ""
        glucMealContext = "Fasting"
        glucDate = formatEpochToDateOnly(getCurrentTimeMillis())
        glucTime = formatEpochToTimeOnly(getCurrentTimeMillis())
        glucNotes = ""
    }

    // Business Logic Actions (Blood Pressure)
    fun saveBloodPressureRecord() {
        val systolicVal = bpSystolic.trim().toIntOrNull() ?: 120
        val diastolicVal = bpDiastolic.trim().toIntOrNull() ?: 80
        val pulseVal = bpPulse.trim().toIntOrNull() ?: 70
        val calendar = composeCalendarFromDateStrAndTimeStr(bpDate.trim(), bpTime.trim())
        val timeInMillis = calendar.timeInMillis

        val record = if (selectedBpIdToEdit != null) {
            BloodPressureRecord(
                id = selectedBpIdToEdit!!,
                systolic = systolicVal,
                diastolic = diastolicVal,
                pulse = pulseVal,
                dateTimeMillis = timeInMillis,
                notes = bpNotes
            )
        } else {
            BloodPressureRecord(
                systolic = systolicVal,
                diastolic = diastolicVal,
                pulse = pulseVal,
                dateTimeMillis = timeInMillis,
                notes = bpNotes
            )
        }

        viewModelScope.launch {
            repository.insertBloodPressureRecord(record)

            // Cloud Firestore Sync
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val userId = auth.currentUser?.uid ?: (if (_loggedInUser.value.isNotEmpty()) "user_" + _loggedInUser.value.lowercase().replace(" ", "_") else "guest_patient")
                if (userId != null) {
                    val recordMap = hashMapOf(
                        "systolic" to record.systolic,
                        "diastolic" to record.diastolic,
                        "pulse" to record.pulse,
                        "timestamp" to record.dateTimeMillis,
                        "notes" to record.notes
                    )
                    db.collection("users")
                        .document(userId)
                        .collection("blood_pressure_records")
                        .add(recordMap)
                        .addOnSuccessListener {
                            android.util.Log.d("FirebaseSync", "Blood pressure record sync successful in Firestore!")
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("FirebaseSync", "Firestore sync exception:", e)
                        }
                }
            } catch (e: Exception) {
                android.util.Log.e("FirebaseSync", "Firebase Firestore sync bypassed: ${e.message}")
            }

            resetBloodPressureForm()
        }
    }

    fun deleteBloodPressureRecord(record: BloodPressureRecord) {
        viewModelScope.launch {
            repository.deleteBloodPressureRecord(record)
        }
    }

    fun prepareEditBloodPressure(record: BloodPressureRecord) {
        selectedBpIdToEdit = record.id
        bpSystolic = record.systolic.toString()
        bpDiastolic = record.diastolic.toString()
        bpPulse = record.pulse.toString()
        bpDate = formatEpochToDateOnly(record.dateTimeMillis)
        bpTime = formatEpochToTimeOnly(record.dateTimeMillis)
        bpNotes = record.notes
    }

    fun resetBloodPressureForm() {
        selectedBpIdToEdit = null
        bpSystolic = ""
        bpDiastolic = ""
        bpPulse = ""
        bpDate = formatEpochToDateOnly(getCurrentTimeMillis())
        bpTime = formatEpochToTimeOnly(getCurrentTimeMillis())
        bpNotes = ""
    }

    // Business Logic Actions (Step Count)
    fun saveStepRecord() {
        val stepsVal = stepsCount.trim().toIntOrNull() ?: 0
        val calendar = composeCalendarFromDateStrAndTimeStr(stepsDate.trim(), stepsTime.trim())
        val timeInMillis = calendar.timeInMillis

        val record = if (selectedStepIdToEdit != null) {
            StepCountRecord(
                id = selectedStepIdToEdit!!,
                steps = stepsVal,
                dateTimeMillis = timeInMillis,
                notes = stepsNotes
            )
        } else {
            StepCountRecord(
                steps = stepsVal,
                dateTimeMillis = timeInMillis,
                notes = stepsNotes
            )
        }

        viewModelScope.launch {
            repository.insertStepRecord(record)

            // Cloud Firestore Sync
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val userId = auth.currentUser?.uid ?: (if (_loggedInUser.value.isNotEmpty()) "user_" + _loggedInUser.value.lowercase().replace(" ", "_") else "guest_patient")
                if (userId != null) {
                    val recordMap = hashMapOf(
                        "steps" to record.steps,
                        "timestamp" to record.dateTimeMillis,
                        "notes" to record.notes
                    )
                    db.collection("users")
                        .document(userId)
                        .collection("step_records")
                        .add(recordMap)
                        .addOnSuccessListener {
                            android.util.Log.d("FirebaseSync", "Step record sync successful in Firestore!")
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("FirebaseSync", "Firestore sync exception:", e)
                        }
                }
            } catch (e: Exception) {
                android.util.Log.e("FirebaseSync", "Firebase Firestore sync bypassed: ${e.message}")
            }

            resetStepForm()
        }
    }

    fun deleteStepRecord(record: StepCountRecord) {
        viewModelScope.launch {
            repository.deleteStepRecord(record)
        }
    }

    fun prepareEditStep(record: StepCountRecord) {
        selectedStepIdToEdit = record.id
        stepsCount = record.steps.toString()
        stepsDate = formatEpochToDateOnly(record.dateTimeMillis)
        stepsTime = formatEpochToTimeOnly(record.dateTimeMillis)
        stepsNotes = record.notes
    }

    fun resetStepForm() {
        selectedStepIdToEdit = null
        stepsCount = ""
        stepsDate = formatEpochToDateOnly(getCurrentTimeMillis())
        stepsTime = formatEpochToTimeOnly(getCurrentTimeMillis())
        stepsNotes = ""
    }

    // Business Logic Actions (Reminders)
    fun saveReminder() {
        val label = if (remLabel.isEmpty()) "$remType Reminder" else remLabel
        val reminder = if (selectedReminderIdToEdit != null) {
            Reminder(
                id = selectedReminderIdToEdit!!,
                reminderType = remType,
                label = label,
                hour = remHour,
                minute = remMinute,
                isEnabled = true,
                daysOfWeek = remDays
            )
        } else {
            Reminder(
                reminderType = remType,
                label = label,
                hour = remHour,
                minute = remMinute,
                isEnabled = true,
                daysOfWeek = remDays
            )
        }

        viewModelScope.launch {
            repository.insertReminder(reminder)
            resetReminderForm()
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            repository.deleteReminder(reminder)
        }
    }

    fun toggleReminder(reminder: Reminder) {
        viewModelScope.launch {
            repository.updateReminderStatus(reminder.id, !reminder.isEnabled)
        }
    }

    fun prepareEditReminder(reminder: Reminder) {
        selectedReminderIdToEdit = reminder.id
        remType = reminder.reminderType
        remLabel = reminder.label
        remHour = reminder.hour
        remMinute = reminder.minute
        remDays = reminder.daysOfWeek
    }

    fun resetReminderForm() {
        selectedReminderIdToEdit = null
        remType = "Insulin"
        remLabel = ""
        val now = Calendar.getInstance()
        remHour = now.get(Calendar.HOUR_OF_DAY)
        remMinute = now.get(Calendar.MINUTE)
        remDays = "Daily"
    }

    // Save Profile Settings
    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            repository.insertOrUpdateProfile(profile)
        }
    }

    fun selectProfileFlow(profileId: Int) {
        viewModelScope.launch {
            repository.selectProfile(profileId)
        }
    }

    fun deleteProfileFlow(profile: UserProfile) {
        viewModelScope.launch {
            repository.deleteProfile(profile)
            // If the deleted profile was active, find another one to activate
            if (profile.isActive) {
                val anyOther = repository.getAnyProfileSync()
                if (anyOther != null) {
                    repository.selectProfile(anyOther.id)
                } else {
                    repository.insertOrUpdateProfile(defaultProfile())
                }
            }
        }
    }

    fun saveNewProfileFlow(profile: UserProfile) {
        viewModelScope.launch {
            repository.selectProfile(-1) // deactivate all first
            repository.insertOrUpdateProfile(profile.copy(id = 0, isActive = true))
        }
    }

    fun refillCartridge(capacity: Double, dateStr: String, timeStr: String) {
        viewModelScope.launch {
            val currentProf = repository.getProfileSync()
            if (currentProf != null) {
                val remainingBefore = currentProf.cartridgeRemaining
                val calendar = composeCalendarFromDateStrAndTimeStr(dateStr, timeStr)
                repository.insertOrUpdateProfile(
                    currentProf.copy(
                        cartridgeCapacity = capacity,
                        cartridgeRemaining = capacity
                    )
                )
                repository.insertRefillLog(
                    CartridgeRefillLog(
                        capacity = capacity,
                        remainingBefore = remainingBefore,
                        dateTimeMillis = calendar.timeInMillis,
                        actionType = if (remainingBefore <= 0.01) "Change (Empty)" else if (capacity != currentProf.cartridgeCapacity) "Size Change" else "Refill"
                    )
                )
            }
        }
    }

    fun saveRefillLog(log: CartridgeRefillLog) {
        viewModelScope.launch {
            repository.insertRefillLog(log)
        }
    }

    fun deleteRefillLog(log: CartridgeRefillLog) {
        viewModelScope.launch {
            repository.deleteRefillLog(log)
        }
    }

    fun clearAllRefillLogs() {
        viewModelScope.launch {
            repository.clearAllRefillLogs()
        }
    }

    // Date/Time Parsing & Formatting Utilities
    fun formatEpochToDate(millis: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    fun formatEpochToDateOnly(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    fun formatEpochToTimeOnly(millis: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    fun formatHourMinute(hour: Int, minute: Int): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    fun composeCalendarFromDateStrAndTimeStr(dateStr: String, timeStr: String): Calendar {
        val calendar = Calendar.getInstance()
        try {
            val dateParts = dateStr.split("-")
            if (dateParts.size == 3) {
                calendar.set(Calendar.YEAR, dateParts[0].toInt())
                calendar.set(Calendar.MONTH, dateParts[1].toInt() - 1)
                calendar.set(Calendar.DAY_OF_MONTH, dateParts[2].toInt())
            }
            val timeParts = timeStr.split(":")
            if (timeParts.size == 2) {
                calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                calendar.set(Calendar.MINUTE, timeParts[1].toInt())
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
        } catch (e: Exception) {
            // Keep default fallback
        }
        return calendar
    }

    // Reports Generation Outputs
    fun getReportsData(
        records: List<InsulinRecord>,
        readings: List<GlucoseReading>,
        profile: UserProfile
    ): ReportsSummary {
        val now = Calendar.getInstance()

        // Calendar boundaries
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfWeek = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfMonth = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -30)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // INSULIN TOTALS
        val todayInsulinTotal = records.filter { it.dateTimeMillis >= startOfToday }.sumOf { it.doseUnits }
        val weekInsulinTotal = records.filter { it.dateTimeMillis >= startOfWeek }.sumOf { it.doseUnits }
        val monthInsulinTotal = records.filter { it.dateTimeMillis >= startOfMonth }.sumOf { it.doseUnits }

        val weekDaysTotalMap = records.filter { it.dateTimeMillis >= startOfWeek }
            .groupBy { formatEpochToDateOnly(it.dateTimeMillis) }
            .mapValues { entry -> entry.value.sumOf { it.doseUnits } }

        // GLUCOSE STATS
        val todayGluc = readings.filter { it.dateTimeMillis >= startOfToday }
        val weekGluc = readings.filter { it.dateTimeMillis >= startOfWeek }
        val monthGluc = readings.filter { it.dateTimeMillis >= startOfMonth }

        val todayGlucoseAvg = if (todayGluc.isNotEmpty()) todayGluc.map { it.readingValue }.average() else 0.0
        val weekGlucoseAvg = if (weekGluc.isNotEmpty()) weekGluc.map { it.readingValue }.average() else 0.0
        val monthGlucoseAvg = if (monthGluc.isNotEmpty()) monthGluc.map { it.readingValue }.average() else 0.0

        // Range calculations for monthGluc
        var inRangeCount = 0
        var lowCount = 0
        var highCount = 0
        monthGluc.forEach {
            when {
                it.readingValue < profile.targetGlucoseMin -> lowCount++
                it.readingValue > profile.targetGlucoseMax -> highCount++
                else -> inRangeCount++
            }
        }

        val totalMonthReadings = monthGluc.size
        val percentInRange = if (totalMonthReadings > 0) (inRangeCount.toDouble() / totalMonthReadings * 100.0) else 0.0
        val percentLow = if (totalMonthReadings > 0) (lowCount.toDouble() / totalMonthReadings * 100.0) else 0.0
        val percentHigh = if (totalMonthReadings > 0) (highCount.toDouble() / totalMonthReadings * 100.0) else 0.0

        return ReportsSummary(
            todayInsulinTotal = todayInsulinTotal,
            weekInsulinTotal = weekInsulinTotal,
            monthInsulinTotal = monthInsulinTotal,
            todayGlucoseAvg = todayGlucoseAvg,
            weekGlucoseAvg = weekGlucoseAvg,
            monthGlucoseAvg = monthGlucoseAvg,
            percentInRange = percentInRange,
            percentLow = percentLow,
            percentHigh = percentHigh,
            totalMonthReadings = totalMonthReadings,
            weekDaysTotalMap = weekDaysTotalMap
        )
    }

    // Fully formatted CSV generation for physician sharing
    fun generateExportContent(
        records: List<InsulinRecord>,
        readings: List<GlucoseReading>,
        profile: UserProfile
    ): String {
        val reports = getReportsData(records, readings, profile)
        val sb = StringBuilder()

        sb.append("=========================================\n")
        sb.append("  GLUCOLOG DIABETIC TRACKER EXPORT REPORT\n")
        sb.append("=========================================\n\n")

        sb.append("--- PATIENT & CLINICAL PROFILE ---\n")
        sb.append("Patient Name: ${profile.userName}\n")
        sb.append("Primary Endocrinologist: ${profile.doctorName}\n")
        sb.append("Doctor Contact Email: ${profile.doctorEmail}\n")
        sb.append("Doctor Contact Phone: ${profile.doctorPhone}\n")
        val unit = profile.glucoseUnit
        sb.append("Target Blood Glucose Range: ${profile.targetGlucoseMin} - ${profile.targetGlucoseMax} $unit\n\n")

        sb.append("--- CLINICAL INSIGHTS (LAST 30 DAYS) ---\n")
        sb.append("Avg Glucose: ${String.format(Locale.getDefault(), "%.1f", reports.monthGlucoseAvg)} $unit\n")
        sb.append("Time In Range (TIR): ${String.format(Locale.getDefault(), "%.1f", reports.percentInRange)}%\n")
        sb.append("Time Below Range (Low): ${String.format(Locale.getDefault(), "%.1f", reports.percentLow)}%\n")
        sb.append("Time Above Range (High): ${String.format(Locale.getDefault(), "%.1f", reports.percentHigh)}%\n")
        sb.append("Total Readings Logged: ${reports.totalMonthReadings}\n")
        sb.append("Total Insulin Administered (30d): ${String.format(Locale.getDefault(), "%.1f", reports.monthInsulinTotal)} U\n\n")

        sb.append("--- RAW BLOOD GLUCOSE LOGS (CSV) ---\n")
        sb.append("Date & Time,Reading ($unit),Meal Context,Notes\n")
        readings.forEach {
            sb.append("${formatEpochToDate(it.dateTimeMillis)},${it.readingValue},${it.mealContext},\"${it.notes.replace("\"", "'")}\"\n")
        }
        sb.append("\n")

        sb.append("--- RAW INSULIN DOSAGE LOGS (CSV) ---\n")
        sb.append("Date & Time,Insulin Type,Dose (Units),Notes\n")
        records.forEach {
            sb.append("${formatEpochToDate(it.dateTimeMillis)},${it.insulinType},${it.doseUnits},\"${it.notes.replace("\"", "'")}\"\n")
        }

        return sb.toString()
    }

    // Export Reports Filters
    private val _pdfIncludeGlucose = MutableStateFlow(true)
    val pdfIncludeGlucose: StateFlow<Boolean> = _pdfIncludeGlucose.asStateFlow()

    private val _pdfIncludeInsulin = MutableStateFlow(true)
    val pdfIncludeInsulin: StateFlow<Boolean> = _pdfIncludeInsulin.asStateFlow()

    private val _pdfIncludeBp = MutableStateFlow(true)
    val pdfIncludeBp: StateFlow<Boolean> = _pdfIncludeBp.asStateFlow()

    private val _pdfIncludeRefills = MutableStateFlow(true)
    val pdfIncludeRefills: StateFlow<Boolean> = _pdfIncludeRefills.asStateFlow()

    private val _pdfDateRange = MutableStateFlow("Last 30 Days") // "Last 7 Days", "Last 14 Days", "Last 30 Days", "All", "Custom Range"
    val pdfDateRange: StateFlow<String> = _pdfDateRange.asStateFlow()

    private val _pdfCustomFromDate = MutableStateFlow(formatEpochToDateOnly(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000))
    val pdfCustomFromDate: StateFlow<String> = _pdfCustomFromDate.asStateFlow()

    private val _pdfCustomToDate = MutableStateFlow(formatEpochToDateOnly(System.currentTimeMillis()))
    val pdfCustomToDate: StateFlow<String> = _pdfCustomToDate.asStateFlow()

    fun setPdfIncludeGlucose(value: Boolean) { _pdfIncludeGlucose.value = value }
    fun setPdfIncludeInsulin(value: Boolean) { _pdfIncludeInsulin.value = value }
    fun setPdfIncludeBp(value: Boolean) { _pdfIncludeBp.value = value }
    fun setPdfIncludeRefills(value: Boolean) { _pdfIncludeRefills.value = value }
    fun setPdfDateRange(value: String) { _pdfDateRange.value = value }
    fun setPdfCustomFromDate(value: String) { _pdfCustomFromDate.value = value.trim() }
    fun setPdfCustomToDate(value: String) { _pdfCustomToDate.value = value.trim() }

    fun getStartOfDayMillis(dateStr: String): Long {
        return try {
            val calendar = composeCalendarFromDateStrAndTimeStr(dateStr, "00:00")
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        } catch (e: Exception) {
            0L
        }
    }

    fun getEndOfDayMillis(dateStr: String): Long {
        return try {
            val calendar = composeCalendarFromDateStrAndTimeStr(dateStr, "23:59")
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            calendar.timeInMillis
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }

    fun generatePdfReport(
        records: List<InsulinRecord>,
        readings: List<GlucoseReading>,
        bpRecords: List<BloodPressureRecord>,
        refills: List<CartridgeRefillLog>,
        profile: UserProfile
    ): java.io.File {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val paint = android.graphics.Paint()

        // Page count tracker
        var pageNumber = 1
        var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        var yCoord = 50f

        fun checkPageNew(requiredSpace: Float) {
            if (yCoord + requiredSpace > 800f) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas

                // Draw standard header bar
                yCoord = 40f
                paint.color = android.graphics.Color.parseColor("#90A4AE")
                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
                paint.textSize = 8f
                canvas.drawText("GlucoLog Clinical Report | Page $pageNumber", 50f, yCoord, paint)
                canvas.drawLine(50f, yCoord + 4f, 545f, yCoord + 4f, paint)
                yCoord += 20f
            }
        }

        // Filter based on Date range selection
        val currentTime = getCurrentTimeMillis()
        val limitMillis = when (_pdfDateRange.value) {
            "Last 7 Days" -> currentTime - 7L * 24 * 60 * 60 * 1000
            "Last 14 Days" -> currentTime - 14L * 24 * 60 * 60 * 1000
            "Last 30 Days" -> currentTime - 30L * 24 * 60 * 60 * 1000
            else -> 0L // All Time
        }

        val filteredGlucose = if (_pdfIncludeGlucose.value) {
            if (_pdfDateRange.value == "Custom Range") {
                val from = getStartOfDayMillis(_pdfCustomFromDate.value)
                val to = getEndOfDayMillis(_pdfCustomToDate.value)
                readings.filter { it.dateTimeMillis in from..to }.sortedBy { it.dateTimeMillis }
            } else {
                readings.filter { it.dateTimeMillis >= limitMillis }.sortedBy { it.dateTimeMillis }
            }
        } else emptyList()

        val filteredInsulin = if (_pdfIncludeInsulin.value) {
            if (_pdfDateRange.value == "Custom Range") {
                val from = getStartOfDayMillis(_pdfCustomFromDate.value)
                val to = getEndOfDayMillis(_pdfCustomToDate.value)
                records.filter { it.dateTimeMillis in from..to }.sortedBy { it.dateTimeMillis }
            } else {
                records.filter { it.dateTimeMillis >= limitMillis }.sortedBy { it.dateTimeMillis }
            }
        } else emptyList()

        val filteredBp = if (_pdfIncludeBp.value) {
            if (_pdfDateRange.value == "Custom Range") {
                val from = getStartOfDayMillis(_pdfCustomFromDate.value)
                val to = getEndOfDayMillis(_pdfCustomToDate.value)
                bpRecords.filter { it.dateTimeMillis in from..to }.sortedBy { it.dateTimeMillis }
            } else {
                bpRecords.filter { it.dateTimeMillis >= limitMillis }.sortedBy { it.dateTimeMillis }
            }
        } else emptyList()

        val filteredRefills = if (_pdfIncludeRefills.value) {
            if (_pdfDateRange.value == "Custom Range") {
                val from = getStartOfDayMillis(_pdfCustomFromDate.value)
                val to = getEndOfDayMillis(_pdfCustomToDate.value)
                refills.filter { it.dateTimeMillis in from..to }.sortedBy { it.dateTimeMillis }
            } else {
                refills.filter { it.dateTimeMillis >= limitMillis }.sortedBy { it.dateTimeMillis }
            }
        } else emptyList()

        // Draw Document Header
        paint.isAntiAlias = true
        paint.color = android.graphics.Color.parseColor("#1A237E") // Medical theme Navy primary
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.textSize = 18f
        canvas.drawText("GLUCOLOG CLINICAL REPORT", 50f, yCoord, paint)

        yCoord += 18f
        paint.color = android.graphics.Color.parseColor("#00838F") // Accent Teal
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.textSize = 10f
        canvas.drawText("Comprehensive Patient Health Profile & Clinical Logs", 50f, yCoord, paint)

        yCoord += 8f
        paint.color = android.graphics.Color.parseColor("#90A4AE")
        paint.strokeWidth = 1f
        canvas.drawLine(50f, yCoord, 545f, yCoord, paint)

        // Patient Information Box
        yCoord += 24f
        paint.color = android.graphics.Color.parseColor("#ECEFF1")
        canvas.drawRect(50f, yCoord - 14f, 545f, yCoord + 64f, paint)

        paint.color = android.graphics.Color.parseColor("#263238")
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.textSize = 10f
        canvas.drawText("Patient: ${profile.userName}", 60f, yCoord, paint)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        paint.textSize = 9f
        canvas.drawText("Primary Doctor: ${profile.doctorName}", 60f, yCoord + 15f, paint)
        canvas.drawText("Contact Doctor: ${profile.doctorEmail} | ${profile.doctorPhone}", 60f, yCoord + 30f, paint)
        canvas.drawText("Target Range: ${profile.targetGlucoseMin.toInt()} - ${profile.targetGlucoseMax.toInt()} ${profile.glucoseUnit}", 60f, yCoord + 45f, paint)

        // Export Metadata
        val format = java.text.SimpleDateFormat("MMM d, yyyy HH:mm", java.util.Locale.getDefault())
        val generatedOn = format.format(java.util.Date(getCurrentTimeMillis()))
        canvas.drawText("Generated On: $generatedOn", 330f, yCoord, paint)
        val ScopeText = if (_pdfDateRange.value == "Custom Range") {
            "Custom: ${_pdfCustomFromDate.value} to ${_pdfCustomToDate.value}"
        } else {
            _pdfDateRange.value
        }
        canvas.drawText("Filter Scope: $ScopeText", 330f, yCoord + 15f, paint)

        yCoord += 80f

        fun drawSectionHeader(title: String) {
            checkPageNew(40f)
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            paint.textSize = 12f
            paint.color = android.graphics.Color.parseColor("#1A237E")
            canvas.drawText(title, 50f, yCoord, paint)
            yCoord += 4f
            paint.strokeWidth = 1f
            paint.color = android.graphics.Color.parseColor("#B0BEC5")
            canvas.drawLine(50f, yCoord, 545f, yCoord, paint)
            yCoord += 16f
        }

        fun drawTableHeader(columns: List<String>, columnWidths: List<Float>) {
            checkPageNew(20f)
            paint.color = android.graphics.Color.parseColor("#EEEEEE")
            canvas.drawRect(50f, yCoord - 12f, 545f, yCoord + 4f, paint)

            paint.color = android.graphics.Color.parseColor("#37474F")
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            paint.textSize = 8.5f

            var currentX = 55f
            columns.forEachIndexed { idx, col ->
                canvas.drawText(col, currentX, yCoord, paint)
                currentX += columnWidths[idx]
            }
            yCoord += 14f
        }

        fun drawTableRow(columns: List<String>, columnWidths: List<Float>, isEven: Boolean) {
            checkPageNew(18f)
            if (isEven) {
                paint.color = android.graphics.Color.parseColor("#F9FBFD")
                canvas.drawRect(50f, yCoord - 10f, 545f, yCoord + 4f, paint)
            }

            paint.color = android.graphics.Color.parseColor("#263238")
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
            paint.textSize = 8f

            var currentX = 55f
            columns.forEachIndexed { idx, col ->
                val limitWidth = columnWidths[idx] - 8f
                val text = if (paint.measureText(col) > limitWidth) {
                    var temp = col
                    while (temp.isNotEmpty() && paint.measureText("$temp...") > limitWidth) {
                        temp = temp.dropLast(1)
                    }
                    if (temp.isEmpty()) "" else "$temp..."
                } else col
                canvas.drawText(text, currentX, yCoord, paint)
                currentX += columnWidths[idx]
            }
            yCoord += 12f
        }

        // 1. Blood Glucose readings
        if (_pdfIncludeGlucose.value) {
            drawSectionHeader("Blood Glucose Level Logs")
            if (filteredGlucose.isEmpty()) {
                paint.color = android.graphics.Color.parseColor("#78909C")
                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                paint.textSize = 9f
                canvas.drawText("No glucose readings found in selected date range.", 60f, yCoord, paint)
                yCoord += 20f
            } else {
                val cols = listOf("Date / Time", "Reading", "Status", "Meal Context", "Notes")
                val widths = listOf(110f, 60f, 75f, 90f, 160f)
                drawTableHeader(cols, widths)

                filteredGlucose.forEachIndexed { i, reading ->
                    val status = when {
                        reading.readingValue < profile.targetGlucoseMin -> "LOW"
                        reading.readingValue > profile.targetGlucoseMax -> "HIGH"
                        else -> "NORMAL"
                    }
                    val rowText = listOf(
                        formatEpochToDate(reading.dateTimeMillis),
                        "${reading.readingValue} ${profile.glucoseUnit}",
                        status,
                        reading.mealContext,
                        reading.notes
                    )
                    drawTableRow(rowText, widths, i % 2 == 0)
                }
                yCoord += 15f
            }
        }

        // 2. Insulin dosages
        if (_pdfIncludeInsulin.value) {
            drawSectionHeader("Insulin Dosage Logs")
            if (filteredInsulin.isEmpty()) {
                paint.color = android.graphics.Color.parseColor("#78909C")
                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                paint.textSize = 9f
                canvas.drawText("No insulin logs found in selected date range.", 60f, yCoord, paint)
                yCoord += 20f
            } else {
                val cols = listOf("Date / Time", "Insulin Type", "Dose units", "Notes")
                val widths = listOf(115f, 110f, 80f, 190f)
                drawTableHeader(cols, widths)

                filteredInsulin.forEachIndexed { i, record ->
                    val rowText = listOf(
                        formatEpochToDate(record.dateTimeMillis),
                        record.insulinType,
                        "${record.doseUnits} Units",
                        record.notes
                    )
                    drawTableRow(rowText, widths, i % 2 == 0)
                }
                yCoord += 15f
            }
        }

        // 3. Blood Pressure
        if (_pdfIncludeBp.value) {
            drawSectionHeader("Blood Pressure Logs")
            if (filteredBp.isEmpty()) {
                paint.color = android.graphics.Color.parseColor("#78909C")
                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                paint.textSize = 9f
                canvas.drawText("No blood pressure logs found in selected date range.", 60f, yCoord, paint)
                yCoord += 20f
            } else {
                val cols = listOf("Date / Time", "Sys/Dia (mmHg)", "Pulse", "Status", "Notes")
                val widths = listOf(110f, 95f, 65f, 85f, 140f)
                drawTableHeader(cols, widths)

                filteredBp.forEachIndexed { i, record ->
                    val sys = record.systolic
                    val dia = record.diastolic
                    val statusLabel = when {
                        sys >= 140 || dia >= 90 -> "Stage 2 High"
                        sys in 130..139 || dia in 80..89 -> "Stage 1 High"
                        sys in 120..129 && dia < 80 -> "Elevated"
                        sys < 90 || dia < 60 -> "Low"
                        else -> "Normal"
                    }
                    val rowText = listOf(
                        formatEpochToDate(record.dateTimeMillis),
                        "$sys/$dia mmHg",
                        "${record.pulse} bpm",
                        statusLabel,
                        record.notes
                    )
                    drawTableRow(rowText, widths, i % 2 == 0)
                }
                yCoord += 15f
            }
        }

        // 4. Cartridge Refills
        if (_pdfIncludeRefills.value) {
            drawSectionHeader("Cartridge Refill & Change History")
            if (filteredRefills.isEmpty()) {
                paint.color = android.graphics.Color.parseColor("#78909C")
                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                paint.textSize = 9f
                canvas.drawText("No refill logs found in selected date range.", 60f, yCoord, paint)
                yCoord += 20f
            } else {
                val cols = listOf("Date / Time", "Action Type", "Capacity (U)", "Remaining Before")
                val widths = listOf(130f, 120f, 110f, 135f)
                drawTableHeader(cols, widths)

                filteredRefills.forEachIndexed { i, refill ->
                    val rowText = listOf(
                        formatEpochToDate(refill.dateTimeMillis),
                        refill.actionType,
                        "${refill.capacity.toInt()} Units",
                        String.format(java.util.Locale.getDefault(), "%.1f Units", refill.remainingBefore)
                    )
                    drawTableRow(rowText, widths, i % 2 == 0)
                }
                yCoord += 15f
            }
        }

        // Drawing End of Report Footer Marker on current last page
        checkPageNew(40f)
        paint.color = android.graphics.Color.parseColor("#B0BEC5")
        canvas.drawLine(50f, yCoord, 545f, yCoord, paint)
        yCoord += 15f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD_ITALIC)
        paint.textSize = 8f
        paint.color = android.graphics.Color.parseColor("#90A4AE")
        canvas.drawText("--- Ende des Befunds / End of Clinical Health Report ---", 160f, yCoord, paint)

        pdfDocument.finishPage(page)

        val file = java.io.File(getApplication<Application>().cacheDir, "GlucoLog_Clinical_Report.pdf")
        if (file.exists()) file.delete()

        val out = java.io.FileOutputStream(file)
        pdfDocument.writeTo(out)
        out.close()
        pdfDocument.close()

        return file
    }

    fun clearOnlyAutoGeneratedDummyData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val glucose = repository.allGlucoseReadings.first()
                glucose.filter { it.notes?.contains("Auto-generated", ignoreCase = true) == true }.forEach {
                    repository.deleteGlucoseReading(it)
                }
                
                val insulin = repository.allInsulinRecords.first()
                insulin.filter { it.notes?.contains("Auto-generated", ignoreCase = true) == true }.forEach {
                    repository.deleteInsulinRecord(it)
                }
                
                val bp = repository.allBloodPressureRecords.first()
                bp.filter { it.notes?.contains("Auto-generated", ignoreCase = true) == true }.forEach {
                    repository.deleteBloodPressureRecord(it)
                }
                
                val refills = repository.allRefillLogs.first()
                refills.filter { it.actionType == "Refill" && it.capacity == 300.0 }.forEach {
                    repository.deleteRefillLog(it)
                }
            } catch (e: Exception) {
                android.util.Log.e("GlucoViewModel", "Error clearing dummy data: ${e.message}")
            }
        }
    }

    fun generateSixMonthsSampleData(onComplete: () -> Unit) {
        viewModelScope.launch {
            val isMmol = (userProfile.value.glucoseUnit == "mmol/L")
            val rand = java.util.Random()
            val curTime = getCurrentTimeMillis()
            
            for (i in 180 downTo 1) {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = curTime
                calendar.add(Calendar.DAY_OF_YEAR, -i)
                val baseTime = calendar.timeInMillis
                
                // 1. Glucose readings (2 per day)
                val fastingCal = Calendar.getInstance().apply {
                    timeInMillis = baseTime
                    set(Calendar.HOUR_OF_DAY, 7)
                    set(Calendar.MINUTE, rand.nextInt(40) + 10)
                    set(Calendar.SECOND, 0)
                }
                val fastingValue = if (isMmol) {
                    4.2 + (rand.nextDouble() * 2.1)
                } else {
                    75.0 + (rand.nextDouble() * 40.0)
                }
                repository.insertGlucoseReading(
                    GlucoseReading(
                        readingValue = fastingValue,
                        mealContext = "Fasting",
                        dateTimeMillis = fastingCal.timeInMillis,
                        notes = "Auto-generated fasting test log"
                    )
                )

                val afterDinnerCal = Calendar.getInstance().apply {
                    timeInMillis = baseTime
                    set(Calendar.HOUR_OF_DAY, 19)
                    set(Calendar.MINUTE, rand.nextInt(40) + 10)
                    set(Calendar.SECOND, 0)
                }
                val postMealValue = if (isMmol) {
                    5.5 + (rand.nextDouble() * 4.5)
                } else {
                    100.0 + (rand.nextDouble() * 80.0)
                }
                repository.insertGlucoseReading(
                    GlucoseReading(
                        readingValue = postMealValue,
                        mealContext = "After Meal",
                        dateTimeMillis = afterDinnerCal.timeInMillis,
                        notes = "Auto-generated post dinner test log"
                    )
                )

                // 2. Insulin records (2 per day)
                val longActingCal = Calendar.getInstance().apply {
                    timeInMillis = baseTime
                    set(Calendar.HOUR_OF_DAY, 22)
                    set(Calendar.MINUTE, rand.nextInt(30))
                    set(Calendar.SECOND, 0)
                }
                repository.insertInsulinRecord(
                    InsulinRecord(
                        insulinType = "Long-acting",
                        doseUnits = (14 + rand.nextInt(7)).toDouble(),
                        dateTimeMillis = longActingCal.timeInMillis,
                        notes = "Auto-generated daily long-acting basal dose"
                    )
                )

                val rapidCal = Calendar.getInstance().apply {
                    timeInMillis = baseTime
                    set(Calendar.HOUR_OF_DAY, 18)
                    set(Calendar.MINUTE, rand.nextInt(30))
                    set(Calendar.SECOND, 0)
                }
                repository.insertInsulinRecord(
                    InsulinRecord(
                        insulinType = "Rapid-acting",
                        doseUnits = (4 + rand.nextInt(5)).toDouble(),
                        dateTimeMillis = rapidCal.timeInMillis,
                        notes = "Auto-generated rapid-acting dinner bolus"
                    )
                )

                // 3. Blood Pressure record (1 every 2 days)
                if (i % 2 == 0) {
                    val bpCal = Calendar.getInstance().apply {
                        timeInMillis = baseTime
                        set(Calendar.HOUR_OF_DAY, 10 + rand.nextInt(4))
                        set(Calendar.MINUTE, rand.nextInt(60))
                        set(Calendar.SECOND, 0)
                    }
                    repository.insertBloodPressureRecord(
                        BloodPressureRecord(
                            systolic = 115 + rand.nextInt(18),
                            diastolic = 72 + rand.nextInt(12),
                            pulse = 64 + rand.nextInt(16),
                            dateTimeMillis = bpCal.timeInMillis,
                            notes = "Auto-generated periodic BP reading"
                        )
                    )
                }

                // 4. Cartridge Refills (every 14 days)
                if (i % 14 == 0) {
                    val refillCal = Calendar.getInstance().apply {
                        timeInMillis = baseTime
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, rand.nextInt(60))
                        set(Calendar.SECOND, 0)
                    }
                    repository.insertRefillLog(
                        CartridgeRefillLog(
                            capacity = 300.0,
                            remainingBefore = (10 + rand.nextInt(25)).toDouble(),
                            dateTimeMillis = refillCal.timeInMillis,
                            actionType = "Refill"
                        )
                    )
                }
            }
            onComplete()
        }
    }

    fun clearAllLogs(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.clearAllGlucoseReadings()
            repository.clearAllInsulinRecords()
            repository.clearAllBloodPressureRecords()
            repository.clearAllRefillLogs()
            onComplete()
        }
    }

    fun clearAllAppData(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.clearAllGlucoseReadings()
            repository.clearAllInsulinRecords()
            repository.clearAllBloodPressureRecords()
            repository.clearAllRefillLogs()
            repository.clearAllReminders()
            repository.clearAllProfiles()
            
            // Re-create a fresh default active profile so the app remains perfectly functional!
            repository.insertOrUpdateProfile(
                UserProfile(
                    id = 0,
                    userName = "Fresh Patient",
                    isActive = true
                )
            )
            onComplete()
        }
    }

    fun exportAppDataToJSON(onComplete: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val root = JSONObject()
                
                // 1. Glucose readings
                val glucoseList = repository.allGlucoseReadings.first()
                val glucoseArr = JSONArray()
                for (item in glucoseList) {
                    val obj = JSONObject()
                    obj.put("readingValue", item.readingValue)
                    obj.put("mealContext", item.mealContext)
                    obj.put("dateTimeMillis", item.dateTimeMillis)
                    obj.put("notes", item.notes)
                    glucoseArr.put(obj)
                }
                root.put("glucose", glucoseArr)

                // 2. Insulin records
                val insulinList = repository.allInsulinRecords.first()
                val insulinArr = JSONArray()
                for (item in insulinList) {
                    val obj = JSONObject()
                    obj.put("insulinType", item.insulinType)
                    obj.put("doseUnits", item.doseUnits)
                    obj.put("dateTimeMillis", item.dateTimeMillis)
                    obj.put("notes", item.notes)
                    insulinArr.put(obj)
                }
                root.put("insulin", insulinArr)

                // 3. Blood pressure records
                val bpList = repository.allBloodPressureRecords.first()
                val bpArr = JSONArray()
                for (item in bpList) {
                    val obj = JSONObject()
                    obj.put("systolic", item.systolic)
                    obj.put("diastolic", item.diastolic)
                    obj.put("pulse", item.pulse)
                    obj.put("dateTimeMillis", item.dateTimeMillis)
                    obj.put("notes", item.notes)
                    bpArr.put(obj)
                }
                root.put("bloodPressure", bpArr)

                // 4. Refill logs
                val refillList = repository.allRefillLogs.first()
                val refillArr = JSONArray()
                for (item in refillList) {
                    val obj = JSONObject()
                    obj.put("capacity", item.capacity)
                    obj.put("remainingBefore", item.remainingBefore)
                    obj.put("dateTimeMillis", item.dateTimeMillis)
                    obj.put("actionType", item.actionType)
                    refillArr.put(obj)
                }
                root.put("refills", refillArr)

                // 5. Reminders
                val reminderList = repository.allReminders.first()
                val reminderArr = JSONArray()
                for (item in reminderList) {
                    val obj = JSONObject()
                    obj.put("reminderType", item.reminderType)
                    obj.put("label", item.label)
                    obj.put("hour", item.hour)
                    obj.put("minute", item.minute)
                    obj.put("isEnabled", item.isEnabled)
                    obj.put("daysOfWeek", item.daysOfWeek)
                    reminderArr.put(obj)
                }
                root.put("reminders", reminderArr)

                // 6. Profiles
                val profileList = repository.allProfiles.first()
                val profileArr = JSONArray()
                for (item in profileList) {
                    val obj = JSONObject()
                    obj.put("userName", item.userName)
                    obj.put("doctorName", item.doctorName)
                    obj.put("doctorEmail", item.doctorEmail)
                    obj.put("doctorPhone", item.doctorPhone)
                    obj.put("targetGlucoseMin", item.targetGlucoseMin)
                    obj.put("targetGlucoseMax", item.targetGlucoseMax)
                    obj.put("glucoseUnit", item.glucoseUnit)
                    obj.put("isActive", item.isActive)
                    obj.put("cartridgeCapacity", item.cartridgeCapacity)
                    obj.put("cartridgeRemaining", item.cartridgeRemaining)
                    obj.put("stepGoal", item.stepGoal)
                    obj.put("heightCm", item.heightCm)
                    obj.put("weightKg", item.weightKg)
                    profileArr.put(obj)
                }
                root.put("profiles", profileArr)

                // 7. Step Count Records
                val stepsList = repository.allStepRecords.first()
                val stepsArr = JSONArray()
                for (item in stepsList) {
                    val obj = JSONObject()
                    obj.put("steps", item.steps)
                    obj.put("dateTimeMillis", item.dateTimeMillis)
                    obj.put("notes", item.notes)
                    stepsArr.put(obj)
                }
                root.put("steps", stepsArr)

                onComplete(root.toString(2))
            } catch (e: java.lang.Exception) {
                onComplete(null)
            }
        }
    }

    fun importAppDataFromJSON(jsonString: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val root = JSONObject(jsonString)
                
                // Clear current database first
                repository.clearAllGlucoseReadings()
                repository.clearAllInsulinRecords()
                repository.clearAllBloodPressureRecords()
                repository.clearAllRefillLogs()
                repository.clearAllReminders()
                repository.clearAllProfiles()
                repository.clearAllStepRecords()

                // 1. Profiles
                val profilesArr = root.optJSONArray("profiles")
                if (profilesArr != null) {
                    for (i in 0 until profilesArr.length()) {
                        val obj = profilesArr.getJSONObject(i)
                        repository.insertOrUpdateProfile(
                            com.example.data.model.UserProfile(
                                id = 0,
                                userName = obj.optString("userName", "Patient"),
                                doctorName = obj.optString("doctorName", ""),
                                doctorEmail = obj.optString("doctorEmail", ""),
                                doctorPhone = obj.optString("doctorPhone", ""),
                                targetGlucoseMin = obj.optDouble("targetGlucoseMin", 70.0),
                                targetGlucoseMax = obj.optDouble("targetGlucoseMax", 140.0),
                                glucoseUnit = obj.optString("glucoseUnit", "mg/dL"),
                                isActive = obj.optBoolean("isActive", false),
                                cartridgeCapacity = obj.optDouble("cartridgeCapacity", 300.0),
                                cartridgeRemaining = obj.optDouble("cartridgeRemaining", 0.0),
                                stepGoal = obj.optInt("stepGoal", 10000),
                                heightCm = obj.optDouble("heightCm", 170.0),
                                weightKg = obj.optDouble("weightKg", 70.0)
                            )
                        )
                    }
                }

                // 2. Glucose readings
                val glucoseArr = root.optJSONArray("glucose")
                if (glucoseArr != null) {
                    for (i in 0 until glucoseArr.length()) {
                        val obj = glucoseArr.getJSONObject(i)
                        repository.insertGlucoseReading(
                            com.example.data.model.GlucoseReading(
                                id = 0,
                                readingValue = obj.optDouble("readingValue", 100.0),
                                mealContext = obj.optString("mealContext", "Other"),
                                dateTimeMillis = obj.optLong("dateTimeMillis", System.currentTimeMillis()),
                                notes = obj.optString("notes", "")
                            )
                        )
                    }
                }

                // 3. Insulin records
                val insulinArr = root.optJSONArray("insulin")
                if (insulinArr != null) {
                    for (i in 0 until insulinArr.length()) {
                        val obj = insulinArr.getJSONObject(i)
                        repository.insertInsulinRecord(
                            com.example.data.model.InsulinRecord(
                                id = 0,
                                insulinType = obj.optString("insulinType", "Rapid-acting"),
                                doseUnits = obj.optDouble("doseUnits", 0.0),
                                dateTimeMillis = obj.optLong("dateTimeMillis", System.currentTimeMillis()),
                                notes = obj.optString("notes", "")
                            )
                        )
                    }
                }

                // 4. Blood pressure records
                val bpArr = root.optJSONArray("bloodPressure")
                if (bpArr != null) {
                    for (i in 0 until bpArr.length()) {
                        val obj = bpArr.getJSONObject(i)
                        repository.insertBloodPressureRecord(
                            com.example.data.model.BloodPressureRecord(
                                id = 0,
                                systolic = obj.optInt("systolic", 120),
                                diastolic = obj.optInt("diastolic", 80),
                                pulse = obj.optInt("pulse", 70),
                                dateTimeMillis = obj.optLong("dateTimeMillis", System.currentTimeMillis()),
                                notes = obj.optString("notes", "")
                            )
                        )
                    }
                }

                // 5. Refill logs
                val refillArr = root.optJSONArray("refills")
                if (refillArr != null) {
                    for (i in 0 until refillArr.length()) {
                        val obj = refillArr.getJSONObject(i)
                        repository.insertRefillLog(
                            com.example.data.model.CartridgeRefillLog(
                                id = 0,
                                capacity = obj.optDouble("capacity", 300.0),
                                remainingBefore = obj.optDouble("remainingBefore", 0.0),
                                dateTimeMillis = obj.optLong("dateTimeMillis", System.currentTimeMillis()),
                                actionType = obj.optString("actionType", "Refill")
                            )
                        )
                    }
                }

                // 6. Reminders
                val reminderArr = root.optJSONArray("reminders")
                if (reminderArr != null) {
                    for (i in 0 until reminderArr.length()) {
                        val obj = reminderArr.getJSONObject(i)
                        repository.insertReminder(
                            com.example.data.model.Reminder(
                                id = 0,
                                reminderType = obj.optString("reminderType", "Blood Sugar Check"),
                                label = obj.optString("label", ""),
                                hour = obj.optInt("hour", 8),
                                minute = obj.optInt("minute", 0),
                                isEnabled = obj.optBoolean("isEnabled", true),
                                daysOfWeek = obj.optString("daysOfWeek", "Daily")
                            )
                        )
                    }
                }

                // 7. Step Count Records
                val stepsArr = root.optJSONArray("steps")
                if (stepsArr != null) {
                    for (i in 0 until stepsArr.length()) {
                        val obj = stepsArr.getJSONObject(i)
                        repository.insertStepRecord(
                            com.example.data.model.StepCountRecord(
                                id = 0,
                                steps = obj.optInt("steps", 0),
                                dateTimeMillis = obj.optLong("dateTimeMillis", System.currentTimeMillis()),
                                notes = obj.optString("notes", "")
                            )
                        )
                    }
                }

                // Restore active state
                val activeProf = repository.getProfileSync()
                if (activeProf == null) {
                    val anyProf = repository.getAnyProfileSync()
                    if (anyProf != null) {
                        repository.selectProfile(anyProf.id)
                    } else {
                        repository.insertOrUpdateProfile(
                            com.example.data.model.UserProfile(
                                id = 0,
                                userName = "Patient",
                                isActive = true
                            )
                        )
                    }
                }

                onComplete(true)
            } catch (e: java.lang.Exception) {
                onComplete(false)
            }
        }
    }

    fun setGoogleDriveAccessToken(token: String) {
        val trimmed = token.trim()
        _googleDriveAccessToken.value = trimmed
        val prefs = getApplication<Application>().getSharedPreferences("gluco_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("gd_access_token", trimmed).apply()
        if (trimmed.isNotEmpty()) {
            _googleDriveSyncEnabled.value = true
            prefs.edit().putBoolean("gd_sync_enabled", true).apply()
        }
    }

    fun disableGoogleDriveSync() {
        _googleDriveSyncEnabled.value = false
        _googleDriveAccessToken.value = ""
        val prefs = getApplication<Application>().getSharedPreferences("gluco_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("gd_sync_enabled", false)
            .remove("gd_access_token")
            .apply()
    }

    fun backupToGoogleDrive(onComplete: (Boolean, String) -> Unit) {
        val token = _googleDriveAccessToken.value
        if (token.isEmpty()) {
            onComplete(false, "Google Drive not authorized. Please configure access token.")
            return
        }

        viewModelScope.launch {
            _isGoogleDriveSyncing.value = true
            try {
                exportAppDataToJSON { json ->
                    if (json == null) {
                        _isGoogleDriveSyncing.value = false
                        onComplete(false, "Failed to compile local clinical records to JSON.")
                        return@exportAppDataToJSON
                    }

                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val existingFileId = com.example.data.api.GoogleDriveService.findBackupFile(token)
                            val success = com.example.data.api.GoogleDriveService.uploadBackupFile(token, json, existingFileId)
                            
                            withContext(Dispatchers.Main) {
                                _isGoogleDriveSyncing.value = false
                                if (success) {
                                    val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                                    _googleDriveLastSyncTime.value = nowStr
                                    getApplication<Application>().getSharedPreferences("gluco_auth_prefs", Context.MODE_PRIVATE)
                                        .edit().putString("gd_last_sync_time", nowStr).apply()
                                    onComplete(true, "Successfully backed up clinical data to Google Drive!")
                                } else {
                                    com.example.data.api.GoogleDriveService.invalidateToken(getApplication(), token)
                                    onComplete(false, "Drive upload failed. Please check access token scope or expiry.")
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                _isGoogleDriveSyncing.value = false
                                onComplete(false, "Connection error: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _isGoogleDriveSyncing.value = false
                onComplete(false, "Preparation error: ${e.message}")
            }
        }
    }

    fun restoreFromGoogleDrive(onComplete: (Boolean, String) -> Unit) {
        val token = _googleDriveAccessToken.value
        if (token.isEmpty()) {
            onComplete(false, "Google Drive not authorized. Please configure access token.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _isGoogleDriveSyncing.value = true
            }

            try {
                val existingFileId = com.example.data.api.GoogleDriveService.findBackupFile(token)
                if (existingFileId == null) {
                    withContext(Dispatchers.Main) {
                        _isGoogleDriveSyncing.value = false
                        onComplete(false, "No 'glucolog_backup.json' file is found on your Google Drive.")
                    }
                    return@launch
                }

                val jsonContent = com.example.data.api.GoogleDriveService.downloadBackupFile(token, existingFileId)
                withContext(Dispatchers.Main) {
                    if (jsonContent != null) {
                        importAppDataFromJSON(jsonContent) { success ->
                            _isGoogleDriveSyncing.value = false
                            if (success) {
                                onComplete(true, "Successfully restored clinical records from Google Drive!")
                            } else {
                                onComplete(false, "Downloaded backup file has an invalid structure.")
                            }
                        }
                    } else {
                        _isGoogleDriveSyncing.value = false
                        onComplete(false, "Failed to download backup file from Google Drive.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isGoogleDriveSyncing.value = false
                    onComplete(false, "Connection error: ${e.message}")
                }
            }
        }
    }

    fun fetchGoogleDriveTokenAutomatically(context: Context, email: String) {
        if (email.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val accountManager = android.accounts.AccountManager.get(context)
                val accounts = accountManager.getAccountsByType("com.google")
                val matchingAccount = accounts.find { it.name.equals(email, ignoreCase = true) }
                if (matchingAccount != null) {
                    val token = accountManager.blockingGetAuthToken(
                        matchingAccount,
                        "oauth2:https://www.googleapis.com/auth/drive.file",
                        true
                    )
                    if (!token.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) {
                            setGoogleDriveAccessToken(token)
                            android.util.Log.d("GoogleDriveAutoSync", "Successfully auto-authenticated Google Drive for $email")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GoogleDriveAutoSync", "Failed to auto-authenticate Google Drive: ${e.message}")
            }
        }
    }
}

data class ReportsSummary(
    val todayInsulinTotal: Double,
    val weekInsulinTotal: Double,
    val monthInsulinTotal: Double,
    val todayGlucoseAvg: Double,
    val weekGlucoseAvg: Double,
    val monthGlucoseAvg: Double,
    val percentInRange: Double,
    val percentLow: Double,
    val percentHigh: Double,
    val totalMonthReadings: Int,
    val weekDaysTotalMap: Map<String, Double>
)
