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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen {
    HOME,
    HISTORY,
    REMINDERS,
    REPORTS,
    PROFILE
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

    // Login & Auth State
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _loggedInUser = MutableStateFlow("")
    val loggedInUser: StateFlow<String> = _loggedInUser.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    fun login(username: String, password: String): Boolean {
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

        if (trimmed == "admin") {
            if (password == "yM*d^@Irf 741$") {
                _isAdmin.value = true
                _loggedInUser.value = trimmed
                _isLoggedIn.value = true
                return true
            } else {
                _loginError.value = "Incorrect admin password"
                return false
            }
        } else {
            // General registered user login (can use username or registered email)
            val prefs = getApplication<Application>().getSharedPreferences("gluco_auth_prefs", Context.MODE_PRIVATE)
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
    }

    private fun defaultProfile() = UserProfile(
        id = 0,
        userName = "Primary Profile",
        doctorName = "Dr. Smith",
        doctorEmail = "doctor@example.com",
        doctorPhone = "+1 (555) 123-4567",
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
