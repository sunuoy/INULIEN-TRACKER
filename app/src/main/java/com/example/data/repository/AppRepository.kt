package com.example.data.repository

import com.example.data.dao.GlucoseDao
import com.example.data.dao.InsulinDao
import com.example.data.dao.ProfileDao
import com.example.data.dao.ReminderDao
import com.example.data.dao.CartridgeRefillLogDao
import com.example.data.dao.BloodPressureDao
import com.example.data.model.GlucoseReading
import com.example.data.model.InsulinRecord
import com.example.data.model.Reminder
import com.example.data.model.UserProfile
import com.example.data.model.CartridgeRefillLog
import com.example.data.model.BloodPressureRecord
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val insulinDao: InsulinDao,
    private val glucoseDao: GlucoseDao,
    private val reminderDao: ReminderDao,
    private val profileDao: ProfileDao,
    private val cartridgeRefillLogDao: CartridgeRefillLogDao,
    private val bloodPressureDao: BloodPressureDao
) {
    // Insulin Doses
    val allInsulinRecords: Flow<List<InsulinRecord>> = insulinDao.getAllInsulinRecords()

    suspend fun insertInsulinRecord(record: InsulinRecord) {
        insulinDao.insertInsulinRecord(record)
    }

    suspend fun deleteInsulinRecord(record: InsulinRecord) {
        insulinDao.deleteInsulinRecord(record)
    }

    suspend fun deleteInsulinRecordById(id: Long) {
        insulinDao.deleteInsulinRecordById(id)
    }

    suspend fun clearAllInsulinRecords() {
        insulinDao.clearAllInsulinRecords()
    }

    // Glucose Readings
    val allGlucoseReadings: Flow<List<GlucoseReading>> = glucoseDao.getAllGlucoseReadings()

    suspend fun insertGlucoseReading(reading: GlucoseReading) {
        glucoseDao.insertGlucoseReading(reading)
    }

    suspend fun deleteGlucoseReading(reading: GlucoseReading) {
        glucoseDao.deleteGlucoseReading(reading)
    }

    suspend fun deleteGlucoseReadingById(id: Long) {
        glucoseDao.deleteGlucoseReadingById(id)
    }

    suspend fun clearAllGlucoseReadings() {
        glucoseDao.clearAllGlucoseReadings()
    }

    // Reminders
    val allReminders: Flow<List<Reminder>> = reminderDao.getAllReminders()

    suspend fun insertReminder(reminder: Reminder) {
        reminderDao.insertReminder(reminder)
    }

    suspend fun deleteReminder(reminder: Reminder) {
        reminderDao.deleteReminder(reminder)
    }

    suspend fun deleteReminderById(id: Long) {
        reminderDao.deleteReminderById(id)
    }

    suspend fun updateReminderStatus(id: Long, isEnabled: Boolean) {
        reminderDao.updateReminderStatus(id, isEnabled)
    }

    // Profile Settings
    val userProfile: Flow<UserProfile?> = profileDao.getProfile()
    val allProfiles: Flow<List<UserProfile>> = profileDao.getAllProfiles()

    suspend fun getProfileSync(): UserProfile? {
        return profileDao.getProfileSync()
    }

    suspend fun getAnyProfileSync(): UserProfile? {
        return profileDao.getAnyProfileSync()
    }

    suspend fun insertOrUpdateProfile(profile: UserProfile) {
        profileDao.insertOrUpdateProfile(profile)
    }

    suspend fun deleteProfile(profile: UserProfile) {
        profileDao.deleteProfile(profile)
    }

    suspend fun selectProfile(id: Int) {
        profileDao.deactivateAll()
        profileDao.activateProfile(id)
    }

    // Cartridge Refill Logs
    val allRefillLogs: Flow<List<CartridgeRefillLog>> = cartridgeRefillLogDao.getAllRefillLogs()

    suspend fun insertRefillLog(log: CartridgeRefillLog) {
        cartridgeRefillLogDao.insertRefillLog(log)
    }

    suspend fun deleteRefillLog(log: CartridgeRefillLog) {
        cartridgeRefillLogDao.deleteRefillLog(log)
    }

    suspend fun deleteRefillLogById(id: Long) {
        cartridgeRefillLogDao.deleteRefillLogById(id)
    }

    suspend fun clearAllRefillLogs() {
        cartridgeRefillLogDao.clearAllRefillLogs()
    }

    // Blood Pressure Records
    val allBloodPressureRecords: Flow<List<BloodPressureRecord>> = bloodPressureDao.getAllBloodPressureRecords()

    suspend fun insertBloodPressureRecord(record: BloodPressureRecord) {
        bloodPressureDao.insertBloodPressureRecord(record)
    }

    suspend fun deleteBloodPressureRecord(record: BloodPressureRecord) {
        bloodPressureDao.deleteBloodPressureRecord(record)
    }

    suspend fun deleteBloodPressureRecordById(id: Long) {
        bloodPressureDao.deleteBloodPressureRecordById(id)
    }

    suspend fun clearAllBloodPressureRecords() {
        bloodPressureDao.clearAllBloodPressureRecords()
    }

    suspend fun clearAllReminders() {
        reminderDao.clearAllReminders()
    }

    suspend fun clearAllProfiles() {
        profileDao.clearAllProfiles()
    }
}
