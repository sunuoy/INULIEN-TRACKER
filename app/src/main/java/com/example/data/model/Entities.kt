package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "insulin_records")
data class InsulinRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val insulinType: String, // e.g. "Rapid-acting", "Long-acting", "Intermediate", "Short-acting"
    val doseUnits: Double,
    val dateTimeMillis: Long,
    val notes: String = ""
)

@Entity(tableName = "glucose_readings")
data class GlucoseReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val readingValue: Double, // in mg/dL or mmol/L depending on profile preference
    val mealContext: String, // "Fasting", "Before Meal", "After Meal", "Bedtime", "Other"
    val dateTimeMillis: Long,
    val notes: String = ""
)

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderType: String, // "Insulin", "Blood Sugar Check", "Medication"
    val label: String,        // e.g., "Morning Insulin", "After Dinner blood sugar"
    val hour: Int,            // 0 - 23
    val minute: Int,          // 0 - 59
    val isEnabled: Boolean = true,
    val daysOfWeek: String = "Daily" // Comma-separated list like "Mon,Tue,Wed,Thu,Fri,Sat,Sun"
)

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userName: String = "",
    val doctorName: String = "",
    val doctorEmail: String = "",
    val doctorPhone: String = "",
    val targetGlucoseMin: Double = 70.0,
    val targetGlucoseMax: Double = 140.0,
    val glucoseUnit: String = "mg/dL", // mg/dL or mmol/L
    val isActive: Boolean = false,
    val cartridgeCapacity: Double = 300.0,
    val cartridgeRemaining: Double = 0.0,
    val stepGoal: Int = 10000,
    val heightCm: Double = 170.0,
    val weightKg: Double = 70.0
)

@Entity(tableName = "cartridge_refill_logs")
data class CartridgeRefillLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capacity: Double,
    val remainingBefore: Double,
    val dateTimeMillis: Long,
    val actionType: String // e.g. "Refill" or "New Cartridge"
)

@Entity(tableName = "blood_pressure_records")
data class BloodPressureRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int,
    val dateTimeMillis: Long,
    val notes: String = ""
)

@Entity(tableName = "step_count_records")
data class StepCountRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val steps: Int,
    val dateTimeMillis: Long,
    val notes: String = ""
)

