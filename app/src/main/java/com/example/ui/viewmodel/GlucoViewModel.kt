package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.GlucoseReading
import com.example.data.model.InsulinRecord
import com.example.data.model.Reminder
import com.example.data.model.UserProfile
import com.example.data.model.CartridgeRefillLog
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

    // Init Database & Streams
    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(
            database.insulinDao(),
            database.glucoseDao(),
            database.reminderDao(),
            database.profileDao(),
            database.cartridgeRefillLogDao()
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
        insDate = formatEpochToDateOnly(System.currentTimeMillis())
        insTime = formatEpochToTimeOnly(System.currentTimeMillis())
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
        glucDate = formatEpochToDateOnly(System.currentTimeMillis())
        glucTime = formatEpochToTimeOnly(System.currentTimeMillis())
        glucNotes = ""
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

    fun refillCartridge(capacity: Double) {
        viewModelScope.launch {
            val currentProf = repository.getProfileSync()
            if (currentProf != null) {
                val remainingBefore = currentProf.cartridgeRemaining
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
                        dateTimeMillis = System.currentTimeMillis(),
                        actionType = if (remainingBefore <= 0.01) "Change (Empty)" else if (capacity != currentProf.cartridgeCapacity) "Size Change" else "Refill"
                    )
                )
            }
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

    private fun composeCalendarFromDateStrAndTimeStr(dateStr: String, timeStr: String): Calendar {
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
