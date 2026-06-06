package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GlucoseReading
import com.example.data.model.InsulinRecord
import com.example.data.model.Reminder
import com.example.data.model.UserProfile
import com.example.data.model.CartridgeRefillLog
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.GlucoViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoAppLayout(viewModel: GlucoViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val rawInsulinList by viewModel.insulinRecords.collectAsStateWithLifecycle()
    val rawGlucoseList by viewModel.glucoseReadings.collectAsStateWithLifecycle()
    val profilesState by viewModel.userProfile.collectAsStateWithLifecycle()

    var showInsulinDialog by remember { mutableStateOf(false) }
    var showGlucoseDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_navigation_bar"),
                tonalElevation = 6.dp
            ) {
                listOf(
                    NavigationItem("Home", Icons.Default.Home, Icons.Outlined.Home, AppScreen.HOME),
                    NavigationItem("Logs", Icons.Default.List, Icons.Outlined.List, AppScreen.HISTORY),
                    NavigationItem("Reminders", Icons.Default.Notifications, Icons.Outlined.Notifications, AppScreen.REMINDERS),
                    NavigationItem("Reports", Icons.Default.Assessment, Icons.Outlined.Assessment, AppScreen.REPORTS),
                    NavigationItem("Profile", Icons.Default.Person, Icons.Outlined.Person, AppScreen.PROFILE)
                ).forEach { item ->
                    val isSelected = currentScreen == item.screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.navigateTo(item.screen) },
                        label = { Text(item.title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.title
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.HOME -> HomeScreen(
                    viewModel = viewModel,
                    onLogInsulinClick = {
                        viewModel.resetInsulinForm()
                        showInsulinDialog = true
                    },
                    onLogGlucoseClick = {
                        viewModel.resetGlucoseForm()
                        showGlucoseDialog = true
                    }
                )
                AppScreen.HISTORY -> HistoryScreen(
                    viewModel = viewModel,
                    onEditInsulin = { record ->
                        viewModel.prepareEditInsulin(record)
                        showInsulinDialog = true
                    },
                    onEditGlucose = { reading ->
                        viewModel.prepareEditGlucose(reading)
                        showGlucoseDialog = true
                    }
                )
                AppScreen.REMINDERS -> RemindersScreen(
                    viewModel = viewModel,
                    onAddReminderClick = {
                        viewModel.resetReminderForm()
                        showReminderDialog = true
                    },
                    onEditReminder = { reminder ->
                        viewModel.prepareEditReminder(reminder)
                        showReminderDialog = true
                    }
                )
                AppScreen.REPORTS -> ReportsScreen(viewModel = viewModel)
                AppScreen.PROFILE -> ProfileScreen(viewModel = viewModel)
            }
        }
    }

    // Modal Forms
    if (showInsulinDialog) {
        InsulinFormDialog(
            viewModel = viewModel,
            onDismiss = { showInsulinDialog = false },
            onSave = {
                viewModel.saveInsulinRecord()
                showInsulinDialog = false
            }
        )
    }

    if (showGlucoseDialog) {
        GlucoseFormDialog(
            viewModel = viewModel,
            onDismiss = { showGlucoseDialog = false },
            onSave = {
                viewModel.saveGlucoseReading()
                showGlucoseDialog = false
            }
        )
    }

    if (showReminderDialog) {
        ReminderFormDialog(
            viewModel = viewModel,
            onDismiss = { showReminderDialog = false },
            onSave = {
                viewModel.saveReminder()
                showReminderDialog = false
            }
        )
    }
}

private data class NavigationItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val screen: AppScreen
)

// ==========================================
// HOME SCREEN VIEW
// ==========================================
@Composable
fun HomeScreen(
    viewModel: GlucoViewModel,
    onLogInsulinClick: () -> Unit,
    onLogGlucoseClick: () -> Unit
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val rawInsulin by viewModel.insulinRecords.collectAsStateWithLifecycle()
    val rawGlucose by viewModel.glucoseReadings.collectAsStateWithLifecycle()
    val remindersList by viewModel.reminders.collectAsStateWithLifecycle()

    val reportsData = viewModel.getReportsData(rawInsulin, rawGlucose, profile)

    var showRefillDialog by remember { mutableStateOf(false) }
    var customRefillAmount by remember { mutableStateOf("300") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Banner Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Clean Stable Health",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hello, ${profile.userName.ifEmpty { "Health Champion" }}!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Target Range: ${profile.targetGlucoseMin.toInt()}-${profile.targetGlucoseMax.toInt()} ${profile.glucoseUnit}. All entries persist locally.",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Action Buttons Grid-break
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Log Insulin Button Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(115.dp)
                        .clickable { onLogInsulinClick() }
                        .testTag("home_add_insulin_button"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Vaccines, contentDescription = "Insulin Icon", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Insulin Dose", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Record Units taken", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(0.7f))
                        }
                    }
                }

                // Log Glucose Button Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(115.dp)
                        .clickable { onLogGlucoseClick() }
                        .testTag("home_add_glucose_button"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.WaterDrop, contentDescription = "Glucose Icon", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Glucose Level", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Log blood sugar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(0.7f))
                        }
                    }
                }
            }
        }

        // Low Cartridge / Change Refill Below 25 Units Reminder
        if (profile.cartridgeRemaining < 25.0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("low_cartridge_alert_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Critical Warning",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "CRITICAL REMINDER",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Insulin Cartridge Low!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Remaining level is ${profile.cartridgeRemaining.toInt()} Units (less than 25U threshold). Refill or change now to continue logged dosage safely.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { showRefillDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refill icon", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Change & Refill Cartridge", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Insulin Cartridge Balance Tracker Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.Vaccines,
                                contentDescription = "Syringe Cartridge Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Insulin Cartridge Balance",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Refill button
                        Button(
                            onClick = { showRefillDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refill",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Refill / Change", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val capacity = if (profile.cartridgeCapacity > 0) profile.cartridgeCapacity else 300.0
                    val remaining = profile.cartridgeRemaining.coerceIn(0.0, capacity)
                    val percent = (remaining / capacity).coerceIn(0.0, 1.0)

                    val barColor = when {
                        percent < 0.20 -> Color(0xFFE53935) // Red warning
                        percent < 0.40 -> Color(0xFFFB8C00) // Orange warning
                        else -> MaterialTheme.colorScheme.primary
                    }

                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(percent.toFloat())
                                .fillMaxHeight()
                                .background(barColor, RoundedCornerShape(5.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${remaining.toInt()} / ${capacity.toInt()} Units Left",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (percent < 0.20) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface
                        )

                        val statusText = when {
                            percent >= 0.95 -> "Full Cartridge"
                            percent < 0.15 -> "Critical! Change Cartridge"
                            percent < 0.35 -> "Low Balance"
                            else -> "Healthy Level"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (percent < 0.20) Color(0xFFE53935) else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Dialog for choosing refill size or change cartridge
            if (showRefillDialog) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showRefillDialog = false }
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                "Change / Refill Cartridge",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Select a preset pen/pump cartridge capacity size or enter dynamic capacity to reset your insulin remaining balance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )

                            // Quick choices
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("100", "150", "300").forEach { preset ->
                                    val isSelected = customRefillAmount == preset
                                    Button(
                                        onClick = { customRefillAmount = preset },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        Text("${preset}U", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Custom Input field
                            OutlinedTextField(
                                value = customRefillAmount,
                                onValueChange = { customRefillAmount = it.filter { char -> char.isDigit() } },
                                label = { Text("Custom Capacity (Units)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                trailingIcon = { Text("Units", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp)) },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { showRefillDialog = false },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Cancel", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        val amt = customRefillAmount.toDoubleOrNull() ?: 300.0
                                        viewModel.refillCartridge(amt)
                                        showRefillDialog = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Refill Now", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Today's Circular Dashboard Stats
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Today's Overview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Average Glucose Ring representation
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(90.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(80.dp)) {
                                    drawCircle(
                                        color = Color.LightGray.copy(alpha = 0.3f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx())
                                    )
                                    drawArc(
                                        color = if (reportsData.todayGlucoseAvg > profile.targetGlucoseMax || reportsData.todayGlucoseAvg < profile.targetGlucoseMin) Color(0xFFFF9800) else Color(0xFF4CAF50),
                                        startAngle = -90f,
                                        sweepAngle = (reportsData.todayGlucoseAvg.coerceIn(0.0, 300.0) / 300.0 * 360.0).toFloat(),
                                        useCenter = false,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx())
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val avgText = if (reportsData.todayGlucoseAvg > 0) String.format(Locale.getDefault(), "%.0f", reportsData.todayGlucoseAvg) else "N/A"
                                    Text(avgText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                    Text(profile.glucoseUnit, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Avg Glucose", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }

                        Divider(modifier = Modifier
                            .height(60.dp)
                            .width(1.dp))

                        // Total Insulin Dosage taken
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(90.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(80.dp)) {
                                    drawCircle(
                                        color = Color.LightGray.copy(alpha = 0.3f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx())
                                    )
                                    drawArc(
                                        color = Color(0xFF2196F3),
                                        startAngle = -90f,
                                        sweepAngle = (reportsData.todayInsulinTotal.coerceIn(0.0, 100.0) / 100.0 * 360.0).toFloat(),
                                        useCenter = false,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx())
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(String.format(Locale.getDefault(), "%.1f", reportsData.todayInsulinTotal), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                    Text("Units", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Total Insulin Today", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Active Reminders Highlights List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Today's Reminders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { viewModel.navigateTo(AppScreen.REMINDERS) }) {
                    Text("View All", fontSize = 13.sp)
                }
            }
        }

        val enabledReminders = remindersList.filter { it.isEnabled }.take(3)
        if (enabledReminders.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.NotificationsOff, contentDescription = "No alerts", tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("No active reminders for today", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        } else {
            items(enabledReminders) { rem ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (rem.reminderType == "Insulin") Icons.Default.Vaccines else Icons.Default.WaterDrop,
                                contentDescription = "Alert Type Icon",
                                tint = if (rem.reminderType == "Insulin") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(rem.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(rem.reminderType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Text(
                            text = viewModel.formatHourMinute(rem.hour, rem.minute),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// LOGS & HISTORY SCREEN VIEW
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: GlucoViewModel,
    onEditInsulin: (InsulinRecord) -> Unit,
    onEditGlucose: (GlucoseReading) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Insulin, 1 = Glucose, 2 = Refills

    val insulinList by viewModel.filteredInsulinRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    val glucoseList by viewModel.filteredGlucoseReadings.collectAsStateWithLifecycle(initialValue = emptyList())
    val refillLogs by viewModel.refillLogs.collectAsStateWithLifecycle(initialValue = emptyList())
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()

    val insulinFilter by viewModel.insulinTypeFilter.collectAsStateWithLifecycle()
    val glucoseFilter by viewModel.mealContextFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val filteredRefillLogs = remember(refillLogs, searchQuery) {
        if (searchQuery.isBlank()) {
            refillLogs
        } else {
            refillLogs.filter { log ->
                log.actionType.contains(searchQuery, ignoreCase = true) ||
                log.capacity.toString().contains(searchQuery)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("history_screen")
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Clinical Track Logs", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        // Search Bar Search Filter
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("history_search_input"),
            placeholder = { Text("Search logs by notes or date...", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear text")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp)
        )

        // Navigation Tabs for Log types
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Insulin Logs", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Glucose Logs", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Refill Logs", fontWeight = FontWeight.Bold) }
            )
        }

        // SubFilter category chips based on active tab
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedTab == 2) {
                Spacer(modifier = Modifier.weight(1f))
                if (filteredRefillLogs.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.clearAllRefillLogs() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Clear All Logs", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All History", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text("Filters:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)

                if (selectedTab == 0) {
                    // Insulin Types Filters
                    listOf("All", "Rapid-acting", "Long-acting", "Intermediate", "Short-acting").forEach { filter ->
                        val active = insulinFilter == filter
                        FilterChip(
                            selected = active,
                            onClick = { viewModel.setInsulinFilter(filter) },
                            label = { Text(filter, fontSize = 11.sp) }
                        )
                    }
                } else {
                    // Glucose Meal Contexts Filters
                    listOf("All", "Fasting", "Before Meal", "After Meal", "Bedtime").forEach { filter ->
                        val active = glucoseFilter == filter
                        FilterChip(
                            selected = active,
                            onClick = { viewModel.setMealContextFilter(filter) },
                            label = { Text(filter, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // Data lists
        if (selectedTab == 0) {
            // Insulin Records List
            if (insulinList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, contentDescription = "Empty Log", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(0.5f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No matching insulin entries found.", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(insulinList) { rec ->
                        InsulinRecordCard(
                            rec = rec,
                            onEdit = { onEditInsulin(rec) },
                            onDelete = { viewModel.deleteInsulinRecord(rec) }
                        )
                    }
                }
            }
        } else if (selectedTab == 1) {
            // Glucose Readings List
            if (glucoseList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.WaterDrop, contentDescription = "Empty Glucose Log", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(0.5f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No matching sugar entries logged.", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(glucoseList) { reading ->
                        GlucoseReadingCard(
                            reading = reading,
                            profile = profile,
                            onEdit = { onEditGlucose(reading) },
                            onDelete = { viewModel.deleteGlucoseReading(reading) }
                        )
                    }
                }
            }
        } else {
            // Refill Logs List
            if (filteredRefillLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Vaccines, contentDescription = "Empty Refill Log", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(0.4f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No cartridge refills or changes logged.", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredRefillLogs) { log ->
                        RefillLogCard(
                            log = log,
                            onDelete = { viewModel.deleteRefillLog(log) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RefillLogCard(
    log: CartridgeRefillLog,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Icon styling based on type
                val isRefill = log.actionType.contains("Refill", ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isRefill) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRefill) Icons.Default.Vaccines else Icons.Default.Refresh,
                        contentDescription = "Refill Type Icon",
                        tint = if (isRefill) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Details Text
                Column {
                    Text(
                        text = log.actionType,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Capacity: ${log.capacity.toInt()}U (Previous: ${log.remainingBefore.toInt()}U remaining)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                    )
                    
                    val sdf = remember { SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault()) }
                    Text(
                        text = sdf.format(Date(log.dateTimeMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Refill Log Entry",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


@Composable
fun InsulinRecordCard(
    rec: InsulinRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${rec.doseUnits.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(rec.insulinType, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(rec.dateTimeMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (rec.notes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = rec.notes,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit record", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete record", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun GlucoseReadingCard(
    reading: GlucoseReading,
    profile: UserProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isLow = reading.readingValue < profile.targetGlucoseMin
    val isHigh = reading.readingValue > profile.targetGlucoseMax
    val statusColor = when {
        isLow -> Color(0xFF2196F3)    // Blue for Hypoglycemia
        isHigh -> Color(0xFFE91E63)   // Soft Red/Pink for Hyperglycemia
        else -> Color(0xFF4CAF50)     // Green for healthy in-range target!
    }
    val statusText = when {
        isLow -> "Low"
        isHigh -> "High"
        else -> "In Target"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color badge indicating target range category status
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .background(statusColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${reading.readingValue.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(reading.mealContext, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(statusText, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = statusColor)
                        }
                    }
                    Text(
                        text = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(reading.dateTimeMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (reading.notes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = reading.notes,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit glucose record", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete glucose record", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ==========================================
// REMINDERS SCREEN VIEW
// ==========================================
@Composable
fun RemindersScreen(
    viewModel: GlucoViewModel,
    onAddReminderClick: () -> Unit,
    onEditReminder: (Reminder) -> Unit
) {
    val remindersList by viewModel.reminders.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddReminderClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("reminders_add_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add reminder icon")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("reminders_screen")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AccessAlarms, contentDescription = "Alarms header", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Daily Reminders", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Enable timers for dose checks and levels", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.outline)
                }
            }

            if (remindersList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AlarmOff, contentDescription = "No Alerts set", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(0.4f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No reminders set yet. Tap '+' below.", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(remindersList) { alert ->
                        ReminderCard(
                            target = alert,
                            onToggle = { viewModel.toggleReminder(alert) },
                            onEdit = { onEditReminder(alert) },
                            onDelete = { viewModel.deleteReminder(alert) },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReminderCard(
    target: Reminder,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    viewModel: GlucoViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (target.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (target.isEnabled) {
                                if (target.reminderType == "Insulin") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.outline.copy(0.15f)
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (target.reminderType == "Insulin") Icons.Default.Vaccines else Icons.Default.WaterDrop,
                        contentDescription = "Alert type",
                        tint = if (target.isEnabled) {
                            if (target.reminderType == "Insulin") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = target.label,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (target.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.outline.copy(0.12f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(target.reminderType, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(target.daysOfWeek, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = viewModel.formatHourMinute(target.hour, target.minute),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (target.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit reminder", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete reminder", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Switch(
                    checked = target.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}

// ==========================================
// REPORTS SCREEN VIEW
// ==========================================
@Composable
fun ReportsScreen(viewModel: GlucoViewModel) {
    val rawInsulin by viewModel.insulinRecords.collectAsStateWithLifecycle()
    val rawGlucose by viewModel.glucoseReadings.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val summary = viewModel.getReportsData(rawInsulin, rawGlucose, profile)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reports_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text("Clinical Analytics", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Daily, weekly, and monthly summaries", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.outline)
            }
        }

        // 30 Days Summary Stats Overview (Clinical Standard)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("30d Glucose Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ReportStatBox(
                            label = "Average Glucose",
                            value = if (summary.monthGlucoseAvg > 0) String.format(Locale.getDefault(), "%.1f", summary.monthGlucoseAvg) else "N/A",
                            unit = profile.glucoseUnit,
                            modifier = Modifier.weight(1f)
                        )
                        ReportStatBox(
                            label = "Insulin Dose (30d)",
                            value = String.format(Locale.getDefault(), "%.1f", summary.monthInsulinTotal),
                            unit = "Units",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("TIR / Time In Range Summary", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(6.dp))

                    // TIR Canvas Graphic representing Low % / TIR % / High %
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        val spaceWidth = size.width

                        // Low fraction, target fraction, high fraction
                        val rawTotal = summary.percentLow + summary.percentInRange + summary.percentHigh
                        val pctLow = if (rawTotal > 0) summary.percentLow / 100f else 0.05f
                        val pctIn = if (rawTotal > 0) summary.percentInRange / 100f else 0.90f
                        val pctHigh = if (rawTotal > 0) summary.percentHigh / 100f else 0.05f

                        val wLow = spaceWidth * pctLow.toFloat()
                        val wIn = spaceWidth * pctIn.toFloat()
                        val wHigh = spaceWidth * pctHigh.toFloat()

                        // Draw low segment (Blue)
                        drawRect(
                            color = Color(0xFF2196F3),
                            topLeft = Offset(0f, 0f),
                            size = Size(wLow, size.height)
                        )
                        // Draw safe target segment (Emerald/Green)
                        drawRect(
                            color = Color(0xFF4CAF50),
                            topLeft = Offset(wLow, 0f),
                            size = Size(wIn, size.height)
                        )
                        // Draw high segment (Orange/Pink)
                        drawRect(
                            color = Color(0xFFE91E63),
                            topLeft = Offset(wLow + wIn, 0f),
                            size = Size(wHigh, size.height)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TirLegendItem(color = Color(0xFF2196F3), label = "Low", value = "${summary.percentLow.toInt()}%")
                        TirLegendItem(color = Color(0xFF4CAF50), label = "In-Range (${profile.targetGlucoseMin.toInt()}-${profile.targetGlucoseMax.toInt()})", value = "${summary.percentInRange.toInt()}%")
                        TirLegendItem(color = Color(0xFFE91E63), label = "High", value = "${summary.percentHigh.toInt()}%")
                    }
                }
            }
        }

        // Weekly Insulin totals bar chart (Custom Compose Graphics)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Weekly Insulin Chart", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${summary.weekInsulinTotal.toInt()} Units total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Generate last 7 days keys
                        val cal = Calendar.getInstance()
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val labelSdf = SimpleDateFormat("E", Locale.getDefault())
                        val dayKeys = mutableListOf<Pair<String, String>>()
                        for (i in 6 downTo 0) {
                            val temp = Calendar.getInstance()
                            temp.add(Calendar.DAY_OF_YEAR, -i)
                            dayKeys.add(Pair(sdf.format(temp.time), labelSdf.format(temp.time)))
                        }

                        val maxInsulinDay = summary.weekDaysTotalMap.values.maxOrNull() ?: 1.0
                        val upperBoundary = if (maxInsulinDay > 0) maxInsulinDay else 20.0

                        dayKeys.forEach { pair ->
                            val value = summary.weekDaysTotalMap[pair.first] ?: 0.0
                            val fraction = if (value > 0) value / upperBoundary else 0.0

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (value > 0) "${value.toInt()}" else "",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight(0.7f)
                                        .width(14.dp)
                                        .background(
                                            if (value > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                        )
                                        .fillMaxHeight(fraction.toFloat()) // Dynamic fraction mapping height!
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(pair.second, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }

        // Export Report & Share Details
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reports_share_doctor_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share dataIcon",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Share with your Doctor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Generates a comprehensive clinical profile including TIR, averages, daily logs, and dosage CSV configurations to send directly via email or messaging.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val payload = viewModel.generateExportContent(rawInsulin, rawGlucose, profile)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "GlucoLog Diabetic Tracking Clinical Report - ${profile.userName}")
                                putExtra(Intent.EXTRA_TEXT, payload)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Clinical Logs"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Export Log & Share Report")
                    }
                }
            }
        }
    }
}

@Composable
fun ReportStatBox(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(6.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(4.dp))
            Text(unit, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
fun TirLegendItem(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text("$label: ", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
        Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ==========================================
// PROFILE SCREEN VIEW
// ==========================================
@Composable
fun ProfileScreen(viewModel: GlucoViewModel) {
    val currentProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val savedProfiles by viewModel.allProfiles.collectAsStateWithLifecycle()

    var uName by remember { mutableStateOf(currentProfile.userName) }
    var dName by remember { mutableStateOf(currentProfile.doctorName) }
    var dMail by remember { mutableStateOf(currentProfile.doctorEmail) }
    var dPhone by remember { mutableStateOf(currentProfile.doctorPhone) }
    var tMin by remember { mutableStateOf(currentProfile.targetGlucoseMin.toString()) }
    var tMax by remember { mutableStateOf(currentProfile.targetGlucoseMax.toString()) }
    var gUnit by remember { mutableStateOf(currentProfile.glucoseUnit) }

    // Synchronize to profile changes
    LaunchedEffect(currentProfile) {
        uName = currentProfile.userName
        dName = currentProfile.doctorName
        dMail = currentProfile.doctorEmail
        dPhone = currentProfile.doctorPhone
        tMin = currentProfile.targetGlucoseMin.toString()
        tMax = currentProfile.targetGlucoseMax.toString()
        gUnit = currentProfile.glucoseUnit
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("profile_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Headline
        item {
            Column {
                Text(
                    text = "Clinical Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Quick-switch and edit profiles compactly",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // Horizontal Saved Profiles List
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Saved Profiles List",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                if (savedProfiles.isEmpty()) {
                    Text(
                        text = "No saved profiles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        items(savedProfiles) { profile ->
                            val isActive = profile.isActive || profile.id == currentProfile.id
                            Card(
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(98.dp)
                                    .clickable { viewModel.selectProfileFlow(profile.id) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    width = if (isActive) 2.dp else 1.dp,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            ) {
                                Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                                    Column(
                                        modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(0.85f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        // Patient Name
                                        Text(
                                            text = profile.userName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground
                                        )
                                        // Doctor Info
                                        Text(
                                            text = "Dr: ${profile.doctorName.ifEmpty { "None" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline
                                        )
                                        // Limits
                                        Text(
                                            text = "Range: ${profile.targetGlucoseMin.toInt()}-${profile.targetGlucoseMax.toInt()} ${profile.glucoseUnit}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    // Active indicator or Delete button
                                    Row(
                                        modifier = Modifier.align(Alignment.BottomEnd),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isActive) {
                                            Box(
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Active",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else {
                                            IconButton(
                                                onClick = { viewModel.deleteProfileFlow(profile) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Profile",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Compact Profiles Form Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Edit Active Credentials",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Patient and Doctor names side-by-side! (Double-column)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = uName,
                            onValueChange = { uName = it },
                            label = { Text("Patient Name") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = dName,
                            onValueChange = { dName = it },
                            label = { Text("Doctor Name") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }

                    // Doctor email and Doctor Phone side-by-side! (Double-column)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = dMail,
                            onValueChange = { dMail = it },
                            label = { Text("Doctor Email") },
                            modifier = Modifier.weight(1.1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = dPhone,
                            onValueChange = { dPhone = it },
                            label = { Text("Doctor Phone") },
                            modifier = Modifier.weight(0.9f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }

                    // Low and High limits + Unit selector side-by-side! (Triple-column)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = tMin,
                            onValueChange = { tMin = it },
                            label = { Text("Low Limit") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = tMax,
                            onValueChange = { tMax = it },
                            label = { Text("High Limit") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        // Segmented Control for Units (mg/dL vs mmol/L)
                        Column(modifier = Modifier.weight(1.2f)) {
                            Text(
                                "Unit",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(2.dp)
                            ) {
                                val units = listOf("mg/dL", "mmol/L")
                                units.forEach { unit ->
                                    val isSelected = gUnit == unit
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { gUnit = unit },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = unit,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Buttons Group (Save Changes & Save as New Profile)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Outlined Save as New
                        OutlinedButton(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                val pMin = tMin.toDoubleOrNull() ?: 70.0
                                val pMax = tMax.toDoubleOrNull() ?: 140.0
                                viewModel.saveNewProfileFlow(
                                    UserProfile(
                                        id = 0,
                                        userName = uName.ifEmpty { "Patient" },
                                        doctorName = dName,
                                        doctorEmail = dMail,
                                        doctorPhone = dPhone,
                                        targetGlucoseMin = pMin,
                                        targetGlucoseMax = pMax,
                                        glucoseUnit = gUnit,
                                        isActive = true
                                    )
                                )
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "New Profile Logo",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save as New", fontSize = 12.sp, maxLines = 1)
                        }

                        // Filled Save Changes
                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("profile_save_button"),
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                val pMin = tMin.toDoubleOrNull() ?: 70.0
                                val pMax = tMax.toDoubleOrNull() ?: 140.0
                                viewModel.saveProfile(
                                    UserProfile(
                                        id = currentProfile.id,
                                        userName = uName.ifEmpty { "Patient" },
                                        doctorName = dName,
                                        doctorEmail = dMail,
                                        doctorPhone = dPhone,
                                        targetGlucoseMin = pMin,
                                        targetGlucoseMax = pMax,
                                        glucoseUnit = gUnit,
                                        isActive = true
                                    )
                                )
                            }
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = "Save Profile Logo",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Active", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ==========================================
// FORM DIALOGS (CONTAINED MODALS)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsulinFormDialog(
    viewModel: GlucoViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var type by remember { mutableStateOf(viewModel.insType.ifEmpty { "Rapid-acting" }) }
    var dose by remember { mutableStateOf(viewModel.insDose) }
    var date by remember { mutableStateOf(viewModel.insDate.ifEmpty { viewModel.formatEpochToDateOnly(System.currentTimeMillis()) }) }
    var time by remember { mutableStateOf(viewModel.insTime.ifEmpty { viewModel.formatEpochToTimeOnly(System.currentTimeMillis()) }) }
    var notes by remember { mutableStateOf(viewModel.insNotes) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("insulin_dialog"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (viewModel.selectedInsulinIdToEdit != null) "Edit Insulin Dose" else "Add Insulin Dose",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // Segments selection for Insulin type
                Text("Insulin Type", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val types = listOf("Rapid-acting", "Long-acting", "Intermediate", "Short-acting")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            types.take(2).forEach { t ->
                                val active = type == t
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = { type = t },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text(t, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            types.drop(2).forEach { t ->
                                val active = type == t
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = { type = t },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text(t, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                // Dose units textfield
                OutlinedTextField(
                    value = dose,
                    onValueChange = { dose = it },
                    label = { Text("Dose Intake (Units)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("insulin_dose_units_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = { Text("Time (HH:MM)") },
                        modifier = Modifier.weight(0.8f)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (e.g., Post lunch snack, pre workout)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.insType = type
                            viewModel.insDose = dose
                            viewModel.insDate = date
                            viewModel.insTime = time
                            viewModel.insNotes = notes
                            onSave()
                        },
                        enabled = dose.toDoubleOrNull() != null
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseFormDialog(
    viewModel: GlucoViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var value by remember { mutableStateOf(viewModel.glucValue) }
    var context by remember { mutableStateOf(viewModel.glucMealContext.ifEmpty { "Fasting" }) }
    var date by remember { mutableStateOf(viewModel.glucDate.ifEmpty { viewModel.formatEpochToDateOnly(System.currentTimeMillis()) }) }
    var time by remember { mutableStateOf(viewModel.glucTime.ifEmpty { viewModel.formatEpochToTimeOnly(System.currentTimeMillis()) }) }
    var notes by remember { mutableStateOf(viewModel.glucNotes) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("glucose_dialog"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (viewModel.selectedGlucoseIdToEdit != null) "Edit Glucose Reading" else "Add Glucose Reading",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Glucose Level Reading") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("glucose_value_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Text("Meal Context", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)

                // Custom context selection chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val contextOptionList = listOf("Fasting", "Before Meal", "After Meal", "Bedtime", "Other")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        contextOptionList.take(3).forEach { contextVal ->
                            val active = context == contextVal
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { context = contextVal },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (active) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(contextVal, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        contextOptionList.drop(3).forEach { contextVal ->
                            val active = context == contextVal
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { context = contextVal },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (active) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(contextVal, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                        // placeholder balance
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = { Text("Time (HH:MM)") },
                        modifier = Modifier.weight(0.8f)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (e.g., Checked with fingerstick)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.glucValue = value
                            viewModel.glucMealContext = context
                            viewModel.glucDate = date
                            viewModel.glucTime = time
                            viewModel.glucNotes = notes
                            onSave()
                        },
                        enabled = value.toDoubleOrNull() != null
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderFormDialog(
    viewModel: GlucoViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var type by remember { mutableStateOf(viewModel.remType.ifEmpty { "Insulin" }) }
    var label by remember { mutableStateOf(viewModel.remLabel) }
    var hour by remember { mutableIntStateOf(viewModel.remHour) }
    var minute by remember { mutableIntStateOf(viewModel.remMinute) }
    var days by remember { mutableStateOf(viewModel.remDays.ifEmpty { "Daily" }) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("reminder_dialog"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (viewModel.selectedReminderIdToEdit != null) "Edit Reminder Alert" else "Add Reminder Alert",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text("Reminder Type", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val alertTypes = listOf("Insulin", "Blood Sugar Check", "Medication")
                    alertTypes.forEach { t ->
                        val active = type == t
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { type = t },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(t, fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Reminder Label (e.g. Afternoon check)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reminder_label_input")
                )

                // Inline Slider adjustment for hour and minute (robust and clickable!)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hour: $hour (0-23)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { if (hour > 0) hour-- }, modifier = Modifier.size(36.dp), contentPadding = PaddingValues(0.dp)) {
                                Text("-")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { if (hour < 23) hour++ }, modifier = Modifier.size(36.dp), contentPadding = PaddingValues(0.dp)) {
                                Text("+")
                            }
                        }
                    }
                    Slider(
                        value = hour.toFloat(),
                        onValueChange = { hour = it.toInt() },
                        valueRange = 0f..23f,
                        steps = 22
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Minute: $minute (0-59)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { if (minute > 0) minute -= 5 else if (minute == 0) minute = 55 }, modifier = Modifier.size(36.dp), contentPadding = PaddingValues(0.dp)) {
                                Text("-")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = { if (minute < 55) minute += 5 else if (minute == 55) minute = 0 }, modifier = Modifier.size(36.dp), contentPadding = PaddingValues(0.dp)) {
                                Text("+")
                            }
                        }
                    }
                    Slider(
                        value = minute.toFloat(),
                        onValueChange = { minute = it.toInt() },
                        valueRange = 0f..59f,
                        steps = 58
                    )
                }

                OutlinedTextField(
                    value = days,
                    onValueChange = { days = it },
                    label = { Text("Days Scheduled (e.g. Daily, Mon-Fri)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.remType = type
                            viewModel.remLabel = label
                            viewModel.remHour = hour
                            viewModel.remMinute = minute
                            viewModel.remDays = days
                            onSave()
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
