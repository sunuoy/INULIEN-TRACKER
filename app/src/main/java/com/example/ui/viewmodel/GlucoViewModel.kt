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
import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen {
    HOME,
    HISTORY,
    REMINDERS,
    REPORTS,
    PROFILE,
    SETTINGS
}

class GlucoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository

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

            } catch (e: Exception) {
                _isSyncing.value = false
                _syncMessage.value = "Sync failed: ${e.localizedMessage ?: "Unknown error"}"
                _syncConsoleLog.value += "!!! ERROR OCCURRED DURING CONNECTION:\n" + e.localizedMessage + "\n" + e.stackTraceToString()
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
                return true
            } else {
                _loginError.value = "Incorrect admin password"
                return false
            }
        } else {
            // General registered user local login
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

            val savedPassword = prefs.getString("user_pass_$resolvedUsername", null)
            if (savedPassword == null) {
                _loginError.value = "Account does not exist. Please create an account first."
                return false
            } else if (savedPassword != password) {
                _loginError.value = "Incorrect password"
                return false
            } else {
                _isAdmin.value = false
                _loggedInUser.value = resolvedUsername
                _isLoggedIn.value = true
                saveRememberMeConfig(prefs, rememberMeChecked, trimmed, password)

                // Sync sign-in with Firebase Auth
                try {
                    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                    val resolvedEmail = prefs.getString("user_email_$resolvedUsername", "") ?: ""
                    if (resolvedEmail.isNotEmpty()) {
                        auth.signInWithEmailAndPassword(resolvedEmail, password)
                            .addOnSuccessListener { result ->
                                android.util.Log.d("FirebaseAuth", "Successfully authenticated with Firebase: ${result.user?.uid}")
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.e("FirebaseAuth", "Firebase login error: ${e.message}")
                            }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FirebaseAuth", "Firebase Auth not initialized: ${e.message}")
                }

                return true
            }
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
            auth.createUserWithEmailAndPassword(trimmedEmail, password)
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
            database.bloodPressureDao()
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

        // Ensure database default entities
        ensureMinimumSetup()

        // Load dynamic backend preferences
        val prefs = application.getSharedPreferences("gluco_auth_prefs", Context.MODE_PRIVATE)
        _backendBaseUrl.value = prefs.getString("gluco_backend_base_url", "https://httpbin.org/") ?: "https://httpbin.org/"
        _lastSyncTime.value = prefs.getString("gluco_last_sync_time", "Never") ?: "Never"

        // Load remember me preferences
        val isRememberChecked = prefs.getBoolean("remember_me_checked", false)
        _rememberMe.value = isRememberChecked
        if (isRememberChecked) {
            _savedUsernameOrEmail.value = prefs.getString("remember_me_username", "") ?: ""
            _savedPassword.value = prefs.getString("remember_me_password", "") ?: ""
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
        isActive = true
    )

    private fun ensureMinimumSetup() {
        viewModelScope.launch {
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
            repository.allReminders.first().let { currentReminders ->
                if (currentReminders.isEmpty()) {
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
                val userId = auth.currentUser?.uid
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
                    profileArr.put(obj)
                }
                root.put("profiles", profileArr)

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
                                cartridgeRemaining = obj.optDouble("cartridgeRemaining", 0.0)
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
