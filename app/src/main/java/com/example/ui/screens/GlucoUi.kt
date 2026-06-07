package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.util.Calendar
import com.example.data.model.GlucoseReading
import com.example.data.model.InsulinRecord
import com.example.data.model.Reminder
import com.example.data.model.UserProfile
import com.example.data.model.CartridgeRefillLog
import com.example.data.model.BloodPressureRecord
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.GlucoViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoAppLayout(viewModel: GlucoViewModel) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()

    if (!isLoggedIn) {
        LoginScreen(viewModel = viewModel)
    } else {
        val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
        val rawInsulinList by viewModel.insulinRecords.collectAsStateWithLifecycle()
    val rawGlucoseList by viewModel.glucoseReadings.collectAsStateWithLifecycle()
    val profilesState by viewModel.userProfile.collectAsStateWithLifecycle()

    var showInsulinDialog by remember { mutableStateOf(false) }
    var showGlucoseDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showRefillFormDialog by remember { mutableStateOf(false) }
    var editingRefillLog by remember { mutableStateOf<CartridgeRefillLog?>(null) }
    var showBloodPressureDialog by remember { mutableStateOf(false) }

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
                    },
                    onRefillClick = {
                        editingRefillLog = null
                        showRefillFormDialog = true
                    },
                    onLogBloodPressureClick = {
                        viewModel.resetBloodPressureForm()
                        showBloodPressureDialog = true
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
                    },
                    onEditRefill = { log ->
                        editingRefillLog = log
                        showRefillFormDialog = true
                    },
                    onEditBloodPressure = { record ->
                        viewModel.prepareEditBloodPressure(record)
                        showBloodPressureDialog = true
                    },
                    onAddInsulinClick = {
                        viewModel.resetInsulinForm()
                        showInsulinDialog = true
                    },
                    onAddGlucoseClick = {
                        viewModel.resetGlucoseForm()
                        showGlucoseDialog = true
                    },
                    onAddRefillClick = {
                        editingRefillLog = null
                        showRefillFormDialog = true
                    },
                    onAddBloodPressureClick = {
                        viewModel.resetBloodPressureForm()
                        showBloodPressureDialog = true
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

     if (showRefillFormDialog) {
         RefillFormDialog(
             viewModel = viewModel,
             editingLog = editingRefillLog,
             onDismiss = { showRefillFormDialog = false },
             onSave = { showRefillFormDialog = false }
         )
     }

     if (showBloodPressureDialog) {
         BloodPressureFormDialog(
             viewModel = viewModel,
             onDismiss = { showBloodPressureDialog = false },
             onSave = {
                 viewModel.saveBloodPressureRecord()
                 showBloodPressureDialog = false
             }
         )
     }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: GlucoViewModel) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var isRegisterMode by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = {
                Text(
                    text = "Request Password Reset",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "For medical database integrity and HIPAA conformity, password recoveries require system authentication.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Patient accounts: Please contact your clinical supervisor or diabetes care team to set up a new credential key.\n" +
                               "• Admin accounts: Please verify environment key assets or secure database settings directly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .testTag("login_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Clinic clinical brand icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRegisterMode) Icons.Default.AccountBox else Icons.Default.Lock,
                        contentDescription = "Clinician Secure Log",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isRegisterMode) "Create Account" else "GlucoLog Portal",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isRegisterMode) "Register to track clinical metrics" else "Diabetes Tracking System",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { 
                        username = it
                        viewModel.clearLoginError()
                    },
                    label = { Text(if (isRegisterMode) "Username" else "Username or Email ID") },
                    placeholder = { Text(if (isRegisterMode) "Enter username" else "Enter username or email") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = "Username field icon")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_username_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    shape = RoundedCornerShape(12.dp)
                )

                if (isRegisterMode) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { 
                            email = it
                            viewModel.clearLoginError()
                        },
                        label = { Text("Email ID (Gmail or Hotmail)") },
                        placeholder = { Text("yourname@gmail.com / hotmail.com") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = "Email field icon")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_email_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            password = it
                            viewModel.clearLoginError()
                        },
                        label = { Text("Password") },
                        placeholder = { Text("Enter password") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = "Password field icon")
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Toggle Password Visibility" else "Toggle Password Visibility"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_password_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (!isRegisterMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showForgotPasswordDialog = true },
                                modifier = Modifier.testTag("forgot_password_button"),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    "Forgot Password?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (isRegisterMode) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { 
                            confirmPassword = it
                            viewModel.clearLoginError()
                        },
                        label = { Text("Confirm Password") },
                        placeholder = { Text("Re-enter password") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = "Confirm password field icon")
                        },
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (confirmPasswordVisible) "Toggle Password Visibility" else "Toggle Password Visibility"
                                )
                            }
                        },
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_confirm_password_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (loginError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = loginError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("login_error_message")
                        )
                    }
                }

                Button(
                    onClick = {
                        if (isRegisterMode) {
                            if (password != confirmPassword) {
                                viewModel.setLoginError("Passwords do not match!")
                            } else {
                                val success = viewModel.registerUser(username, email, password)
                                if (success) {
                                    android.widget.Toast.makeText(context, "Account created successfully! You can now log in.", android.widget.Toast.LENGTH_LONG).show()
                                    isRegisterMode = false
                                    confirmPassword = ""
                                    email = ""
                                }
                            }
                        } else {
                            val success = viewModel.login(username, password)
                            if (success) {
                                android.widget.Toast.makeText(context, "Successfully authorized!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("login_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isRegisterMode) "Create Account" else "Secure Login",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Switch between login and signup modes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRegisterMode) "Already have an account?" else "Don't have an account?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = {
                            isRegisterMode = !isRegisterMode
                            viewModel.clearLoginError()
                            confirmPassword = ""
                            email = ""
                        },
                        modifier = Modifier.testTag("toggle_register_mode_button"),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (isRegisterMode) "Sign In" else "Create Account",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Guide credentials card (without admin details)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Portal Guidelines:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Divider(
                            modifier = Modifier.fillMaxWidth(),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "• Patient Access: Create an account or log in under your credentials as a standard patient.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
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
    onLogGlucoseClick: () -> Unit,
    onRefillClick: () -> Unit,
    onLogBloodPressureClick: () -> Unit
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val rawInsulin by viewModel.insulinRecords.collectAsStateWithLifecycle()
    val rawGlucose by viewModel.glucoseReadings.collectAsStateWithLifecycle()
    val remindersList by viewModel.reminders.collectAsStateWithLifecycle()

    val reportsData = viewModel.getReportsData(rawInsulin, rawGlucose, profile)

    var showProfileEditDialog by remember { mutableStateOf(false) }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Clean Stable Health",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                        IconButton(
                            onClick = { showProfileEditDialog = true },
                            modifier = Modifier.size(36.dp).testTag("home_edit_profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile Quick-Action",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
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

        // Action Buttons Row 2 (Blood Pressure & Cartridge Refill)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Log Blood Pressure Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(115.dp)
                        .clickable { onLogBloodPressureClick() }
                        .testTag("home_add_bp_button"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
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
                            Icon(Icons.Default.Favorite, contentDescription = "Blood Pressure Icon", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Blood Pressure", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Systolic & Diastolic", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                        }
                    }
                }

                // Log Cartridge Refill Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(115.dp)
                        .clickable { onRefillClick() }
                        .testTag("home_add_refill_button"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
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
                                .background(MaterialTheme.colorScheme.secondary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refill Icon", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Cartridge Refill", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Change capacity size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(0.7f))
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
                                onClick = { onRefillClick() },
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
                            onClick = { onRefillClick() },
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

    if (showProfileEditDialog) {
        ProfileEditDialog(
            currentProfile = profile,
            onDismiss = { showProfileEditDialog = false },
            onSave = { updatedProfile ->
                viewModel.saveProfile(updatedProfile)
                showProfileEditDialog = false
            }
        )
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
    onEditGlucose: (GlucoseReading) -> Unit,
    onEditRefill: (CartridgeRefillLog) -> Unit,
    onEditBloodPressure: (BloodPressureRecord) -> Unit,
    onAddInsulinClick: () -> Unit,
    onAddGlucoseClick: () -> Unit,
    onAddRefillClick: () -> Unit,
    onAddBloodPressureClick: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Insulin, 1 = Glucose, 2 = BP, 3 = Refills

    val insulinList by viewModel.filteredInsulinRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    val glucoseList by viewModel.filteredGlucoseReadings.collectAsStateWithLifecycle(initialValue = emptyList())
    val bpList by viewModel.filteredBloodPressureRecords.collectAsStateWithLifecycle(initialValue = emptyList())
    val refillLogs by viewModel.refillLogs.collectAsStateWithLifecycle(initialValue = emptyList())
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()

    val insulinFilter by viewModel.insulinTypeFilter.collectAsStateWithLifecycle()
    val glucoseFilter by viewModel.mealContextFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var bpFilter by remember { mutableStateOf("All") }
    val filteredBpList = remember(bpList, bpFilter) {
        when (bpFilter) {
            "Normal" -> bpList.filter { it.systolic < 120 && it.diastolic < 80 }
            "Elevated" -> bpList.filter { (it.systolic in 120..129) && it.diastolic < 80 }
            "High BP" -> bpList.filter { it.systolic >= 130 || it.diastolic >= 80 }
            "Low BP" -> bpList.filter { it.systolic < 90 || it.diastolic < 60 }
            else -> bpList
        }
    }

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

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (selectedTab) {
                        0 -> onAddInsulinClick()
                        1 -> onAddGlucoseClick()
                        2 -> onAddBloodPressureClick()
                        3 -> onAddRefillClick()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("history_add_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = when (selectedTab) {
                        0 -> "Add Insulin Log"
                        1 -> "Add Glucose Reading"
                        2 -> "Add Blood Pressure"
                        else -> "Add Cartridge Refill"
                    }
                )
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
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
                text = { Text("BP Logs", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Refills", fontWeight = FontWeight.Bold) }
            )
        }

        // SubFilter category chips based on active tab
        if (selectedTab == 3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(16.dp))
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
                } else if (selectedTab == 1) {
                    // Glucose Meal Contexts Filters
                    listOf("All", "Fasting", "Before Meal", "After Meal", "Bedtime").forEach { filter ->
                        val active = glucoseFilter == filter
                        FilterChip(
                            selected = active,
                            onClick = { viewModel.setMealContextFilter(filter) },
                            label = { Text(filter, fontSize = 11.sp) }
                        )
                    }
                } else if (selectedTab == 2) {
                    // BP Level Filters
                    listOf("All", "Normal", "Elevated", "High BP", "Low BP").forEach { filter ->
                        val active = bpFilter == filter
                        FilterChip(
                            selected = active,
                            onClick = { bpFilter = filter },
                            label = { Text(filter, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
        }

        // Data lists
        if (selectedTab == 0) {
            // Insulin Records List
            if (insulinList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .testTag("insulin_empty_state_card"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Vaccines,
                                    contentDescription = "Empty Insulin Log",
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "No Insulin Logs",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Keep track of your injected rapid, short, or long-acting insulin doses to manage glucose levels effectively.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                            Button(
                                onClick = onAddInsulinClick,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Log Insulin Dose",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Log Insulin Dose",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .testTag("glucose_empty_state_card"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WaterDrop,
                                    contentDescription = "Empty Glucose Log",
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "No Glucose Readings",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Logging sugar levels (fasting, pre/post-meal, bedtime) provides vital clarity to evaluate your stable health progress.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                            Button(
                                onClick = onAddGlucoseClick,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Log Sugar Levels",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Log Sugar Level",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
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
        } else if (selectedTab == 2) {
            // Blood Pressure Logs List
            if (filteredBpList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .testTag("bp_empty_state_card"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Empty BP Logs",
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "No BP Records",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Logging blood pressure readings and heart rate helps keep track of cardiovascular health trends alongside sugar logs.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                            Button(
                                onClick = onAddBloodPressureClick,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Log Blood Pressure",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Log Blood Pressure",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredBpList) { record ->
                        BloodPressureRecordCard(
                            record = record,
                            onEdit = { onEditBloodPressure(record) },
                            onDelete = { viewModel.deleteBloodPressureRecord(record) }
                        )
                    }
                }
            }
        } else {
            // Refill Logs List
            if (filteredRefillLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .testTag("refills_empty_state_card"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Empty Refill Logs",
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "No Refill History",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Record cartridge replacements or pen refills to ensure accurate remaining-insulin capacity readings.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                            Button(
                                onClick = onAddRefillClick,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Log Refill",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Log Cartridge Refill",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
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
                            onEdit = { onEditRefill(log) },
                            onDelete = { viewModel.deleteRefillLog(log) }
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun BloodPressureRecordCard(
    record: BloodPressureRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sys = record.systolic
    val dia = record.diastolic
    val (statusLabel, statusColor) = when {
        sys >= 140 || dia >= 90 -> "High (Stage 2)" to Color(0xFFE53935)
        sys in 130..139 || dia in 80..89 -> "High (Stage 1)" to Color(0xFFFB8C00)
        sys in 120..129 && dia < 80 -> "Elevated" to Color(0xFFFDD835)
        sys < 90 || dia < 60 -> "Low" to Color(0xFF00ACC1)
        else -> "Normal" to Color(0xFF4CAF50)
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(statusColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "BP Status Indicator Log",
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "$sys/$dia",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "mmHg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Card(
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.15f))
                        ) {
                            Text(
                                text = statusLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "Pulse: ${record.pulse} bpm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                    )

                    if (record.notes.isNotEmpty()) {
                        Text(
                            text = record.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    val sdf = remember { SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault()) }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sdf.format(Date(record.dateTimeMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_bp_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit BP Record",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete BP Record",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RefillLogCard(
    log: CartridgeRefillLog,
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

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_refill_log_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Refill Log",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
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
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val rawInsulin by viewModel.insulinRecords.collectAsStateWithLifecycle()
    val rawGlucose by viewModel.glucoseReadings.collectAsStateWithLifecycle()
    val rawBp by viewModel.bloodPressureRecords.collectAsStateWithLifecycle()
    val rawRefills by viewModel.refillLogs.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()

    val pdfIncludeGlucose by viewModel.pdfIncludeGlucose.collectAsStateWithLifecycle()
    val pdfIncludeInsulin by viewModel.pdfIncludeInsulin.collectAsStateWithLifecycle()
    val pdfIncludeBp by viewModel.pdfIncludeBp.collectAsStateWithLifecycle()
    val pdfIncludeRefills by viewModel.pdfIncludeRefills.collectAsStateWithLifecycle()
    val pdfDateRange by viewModel.pdfDateRange.collectAsStateWithLifecycle()
    val pdfCustomFromDate by viewModel.pdfCustomFromDate.collectAsStateWithLifecycle()
    val pdfCustomToDate by viewModel.pdfCustomToDate.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val summary = viewModel.getReportsData(rawInsulin, rawGlucose, profile)

    fun showDatePicker(initialDateStr: String, onDateSelected: (String) -> Unit) {
        val calendar = java.util.Calendar.getInstance()
        val parts = initialDateStr.split("-")
        if (parts.size == 3) {
            try {
                calendar.set(java.util.Calendar.YEAR, parts[0].toInt())
                calendar.set(java.util.Calendar.MONTH, parts[1].toInt() - 1)
                calendar.set(java.util.Calendar.DAY_OF_MONTH, parts[2].toInt())
            } catch (e: Exception) {}
        }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedDate = String.format(java.util.Locale.getDefault(), "%d-%02d-%02d", year, month + 1, dayOfMonth)
                onDateSelected(selectedDate)
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

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
                    .testTag("reports_custom_export_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Icon",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text(
                                "Clinical Report Builder",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Configure your logs filter and export format",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Divider()

                    // Filter Date Range section
                    Text(
                        "Report Date Range:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val ranges = listOf("Last 7 Days", "Last 14 Days", "Last 30 Days", "All", "Custom Range")
                        ranges.forEach { range ->
                            FilterChip(
                                selected = pdfDateRange == range,
                                onClick = { viewModel.setPdfDateRange(range) },
                                label = { Text(range, fontSize = 11.sp) },
                                leadingIcon = {
                                    if (pdfDateRange == range) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = pdfDateRange == "Custom Range",
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Manual Date Duration Selection:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = pdfCustomFromDate,
                                    onValueChange = { viewModel.setPdfCustomFromDate(it) },
                                    label = { Text("From (YYYY-MM-DD)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("pdf_custom_from_date"),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            showDatePicker(pdfCustomFromDate) {
                                                viewModel.setPdfCustomFromDate(it)
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = "Select Start Date"
                                            )
                                        }
                                    }
                                )

                                OutlinedTextField(
                                    value = pdfCustomToDate,
                                    onValueChange = { viewModel.setPdfCustomToDate(it) },
                                    label = { Text("To (YYYY-MM-DD)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("pdf_custom_to_date"),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            showDatePicker(pdfCustomToDate) {
                                                viewModel.setPdfCustomToDate(it)
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = "Select End Date"
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Choose Log categories to include
                    Text(
                        "Include Health Tracker Logs:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = pdfIncludeGlucose,
                                onClick = { viewModel.setPdfIncludeGlucose(!pdfIncludeGlucose) },
                                label = { Text("Glucose", fontSize = 11.sp) },
                                leadingIcon = {
                                    if (pdfIncludeGlucose) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = pdfIncludeInsulin,
                                onClick = { viewModel.setPdfIncludeInsulin(!pdfIncludeInsulin) },
                                label = { Text("Insulin", fontSize = 11.sp) },
                                leadingIcon = {
                                    if (pdfIncludeInsulin) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = pdfIncludeBp,
                                onClick = { viewModel.setPdfIncludeBp(!pdfIncludeBp) },
                                label = { Text("Blood Pressure", fontSize = 11.sp) },
                                leadingIcon = {
                                    if (pdfIncludeBp) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = pdfIncludeRefills,
                                onClick = { viewModel.setPdfIncludeRefills(!pdfIncludeRefills) },
                                label = { Text("Cartridge Refills", fontSize = 11.sp) },
                                leadingIcon = {
                                    if (pdfIncludeRefills) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Main action buttons
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val file = viewModel.generatePdfReport(
                                        records = rawInsulin,
                                        readings = rawGlucose,
                                        bpRecords = rawBp,
                                        refills = rawRefills,
                                        profile = profile
                                    )
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_SUBJECT, "GlucoLog Clinical PDF Report - ${profile.userName}")
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Clinical PDF Report"))
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Error producing PDF: ${e.localizedMessage}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("export_pdf_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "PDF Icon",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export Clinical Report (PDF)")
                        }

                        OutlinedButton(
                            onClick = {
                                val payload = viewModel.generateExportContent(rawInsulin, rawGlucose, profile)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "GlucoLog CSV Data Log - ${profile.userName}")
                                    putExtra(Intent.EXTRA_TEXT, payload)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share CSV Plain Text"))
                            },
                            modifier = Modifier.fillMaxWidth().testTag("export_csv_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "CSV Text Icon",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share CSV / Text Format")
                        }
                    }
                }
            }
        }

        if (isAdmin) {
            // Sandbox & Demo Data Controller Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reports_sandbox_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Demo Sandbox Icon",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Column {
                                Text(
                                    "Evaluation Sandbox",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Populate or reset records for 6-month visual analysis",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Divider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            var isGenerating by remember { mutableStateOf(false) }

                            Button(
                                onClick = {
                                    isGenerating = true
                                    viewModel.generateSixMonthsSampleData {
                                        isGenerating = false
                                        android.widget.Toast.makeText(context, "6 Months comprehensive sample logs added successfully!", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                },
                                enabled = !isGenerating,
                                modifier = Modifier.weight(1.3f).testTag("generate_sample_data_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isGenerating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onTertiary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Populating...", fontSize = 11.sp, maxLines = 1)
                                } else {
                                    Icon(Icons.Default.Add, contentDescription = "Add Data", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Load 6m Demo Data", fontSize = 11.sp, maxLines = 1)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.clearAllLogs {
                                        android.widget.Toast.makeText(context, "All historical logs cleared successfully.", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("clear_sample_data_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear Data", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear Logs", fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.error)
                            }
                        }
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
    val loggedInUser by viewModel.loggedInUser.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()

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

        // Authentication details (Admin credentials log out card)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_auth_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Logged in as:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = loggedInUser.ifEmpty { "Guest Patient" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (isAdmin) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "ADMIN",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.testTag("logout_button"),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Sign Out Icon",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Log Out", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
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

        // Simulated System Clock Dashboard
        item {
            val customOffset by viewModel.customTimeOffsetMillis.collectAsStateWithLifecycle()
            var currentVirtualTime by remember { mutableStateOf(viewModel.getCurrentTimeMillis()) }
            var showSetTimeDialog by remember { mutableStateOf(false) }

            LaunchedEffect(customOffset) {
                while (true) {
                    currentVirtualTime = viewModel.getCurrentTimeMillis()
                    delay(1000L)
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("simulated_time_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Simulated Time Icon",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Simulated System Clock",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        if (customOffset != 0L) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Offset Active",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(
                        text = "Current App Time: ${viewModel.formatEpochToDate(currentVirtualTime)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (customOffset != 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Shift or fast-forward the application's clock to simulate future or past events (e.g., checking medication log history or reminder schedules).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    // Presets
                    Text(
                        text = "Preset Time Shifters:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { viewModel.setSystemTime(viewModel.getCurrentTimeMillis() + 3600000L) }, // +1 Hour
                            label = { Text("+1 Hour") }
                        )
                        AssistChip(
                            onClick = { viewModel.setSystemTime(viewModel.getCurrentTimeMillis() + 10800000L) }, // +3 Hours
                            label = { Text("+3 Hours") }
                        )
                        AssistChip(
                            onClick = { viewModel.setSystemTime(viewModel.getCurrentTimeMillis() + 86400000L) }, // +1 Day
                            label = { Text("+1 Day") }
                        )
                        AssistChip(
                            onClick = { viewModel.setSystemTime(viewModel.getCurrentTimeMillis() + 604800000L) }, // +7 Days
                            label = { Text("+7 Days") }
                        )
                        AssistChip(
                            onClick = { viewModel.setSystemTime(viewModel.getCurrentTimeMillis() - 86400000L) }, // -1 Day
                            label = { Text("-1 Day") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.resetSystemTime() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            enabled = customOffset != 0L
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Clock")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset clock", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { showSetTimeDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = "Specify Time")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Custom...", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (showSetTimeDialog) {
                var inputDate by remember { mutableStateOf(viewModel.formatEpochToDateOnly(viewModel.getCurrentTimeMillis())) }
                var inputTime by remember { mutableStateOf(viewModel.formatEpochToTimeOnly(viewModel.getCurrentTimeMillis())) }

                Dialog(onDismissRequest = { showSetTimeDialog = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Specify Custom Date & Time",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = inputDate,
                                onValueChange = { inputDate = it },
                                label = { Text("Date (YYYY-MM-DD)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = inputTime,
                                onValueChange = { inputTime = it },
                                label = { Text("Time (HH:MM)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showSetTimeDialog = false }) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val calendar = viewModel.composeCalendarFromDateStrAndTimeStr(inputDate, inputTime)
                                        viewModel.setSystemTime(calendar.timeInMillis)
                                        showSetTimeDialog = false
                                    }
                                ) {
                                    Text("Apply")
                                }
                            }
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
    var date by remember { mutableStateOf(viewModel.insDate.ifEmpty { viewModel.formatEpochToDateOnly(viewModel.getCurrentTimeMillis()) }) }
    var time by remember { mutableStateOf(viewModel.insTime.ifEmpty { viewModel.formatEpochToTimeOnly(viewModel.getCurrentTimeMillis()) }) }
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
                            viewModel.insType = type.trim()
                            viewModel.insDose = dose.trim()
                            viewModel.insDate = date.trim()
                            viewModel.insTime = time.trim()
                            viewModel.insNotes = notes.trim()
                            onSave()
                        },
                        enabled = dose.trim().toDoubleOrNull() != null
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
    var date by remember { mutableStateOf(viewModel.glucDate.ifEmpty { viewModel.formatEpochToDateOnly(viewModel.getCurrentTimeMillis()) }) }
    var time by remember { mutableStateOf(viewModel.glucTime.ifEmpty { viewModel.formatEpochToTimeOnly(viewModel.getCurrentTimeMillis()) }) }
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
                            viewModel.glucValue = value.trim()
                            viewModel.glucMealContext = context.trim()
                            viewModel.glucDate = date.trim()
                            viewModel.glucTime = time.trim()
                            viewModel.glucNotes = notes.trim()
                            onSave()
                        },
                        enabled = value.trim().toDoubleOrNull() != null
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
fun BloodPressureFormDialog(
    viewModel: GlucoViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var systolic by remember { mutableStateOf(viewModel.bpSystolic) }
    var diastolic by remember { mutableStateOf(viewModel.bpDiastolic) }
    var pulse by remember { mutableStateOf(viewModel.bpPulse.ifEmpty { "72" }) }
    var date by remember { mutableStateOf(viewModel.bpDate.ifEmpty { viewModel.formatEpochToDateOnly(viewModel.getCurrentTimeMillis()) }) }
    var time by remember { mutableStateOf(viewModel.bpTime.ifEmpty { viewModel.formatEpochToTimeOnly(viewModel.getCurrentTimeMillis()) }) }
    var notes by remember { mutableStateOf(viewModel.bpNotes) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("blood_pressure_dialog"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (viewModel.selectedBpIdToEdit != null) "Edit BP Record" else "Add BP Record",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = systolic,
                        onValueChange = { systolic = it },
                        label = { Text("Systolic (mmHg)") },
                        placeholder = { Text("120") },
                        modifier = Modifier.weight(1f).testTag("bp_systolic_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = diastolic,
                        onValueChange = { diastolic = it },
                        label = { Text("Diastolic (mmHg)") },
                        placeholder = { Text("80") },
                        modifier = Modifier.weight(1f).testTag("bp_diastolic_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                OutlinedTextField(
                    value = pulse,
                    onValueChange = { pulse = it },
                    label = { Text("Pulse / Heart Rate (bpm)") },
                    placeholder = { Text("72") },
                    modifier = Modifier.fillMaxWidth().testTag("bp_pulse_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
                    label = { Text("Notes (e.g., Sitting, resting)") },
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
                            viewModel.bpSystolic = systolic.trim()
                            viewModel.bpDiastolic = diastolic.trim()
                            viewModel.bpPulse = pulse.trim()
                            viewModel.bpDate = date.trim()
                            viewModel.bpTime = time.trim()
                            viewModel.bpNotes = notes.trim()
                            onSave()
                        },
                        enabled = systolic.trim().toIntOrNull() != null && diastolic.trim().toIntOrNull() != null
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditDialog(
    currentProfile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var uName by remember { mutableStateOf(currentProfile.userName) }
    var dName by remember { mutableStateOf(currentProfile.doctorName) }
    var dMail by remember { mutableStateOf(currentProfile.doctorEmail) }
    var dPhone by remember { mutableStateOf(currentProfile.doctorPhone) }
    var tMin by remember { mutableStateOf(currentProfile.targetGlucoseMin.toString()) }
    var tMax by remember { mutableStateOf(currentProfile.targetGlucoseMax.toString()) }
    var gUnit by remember { mutableStateOf(currentProfile.glucoseUnit) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("profile_edit_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Edit Active Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = uName,
                    onValueChange = { uName = it },
                    label = { Text("Patient Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = dName,
                    onValueChange = { dName = it },
                    label = { Text("Doctor Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = dMail,
                        onValueChange = { dMail = it },
                        label = { Text("Doc Email") },
                        modifier = Modifier.weight(1.1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dPhone,
                        onValueChange = { dPhone = it },
                        label = { Text("Doc Phone") },
                        modifier = Modifier.weight(0.9f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = tMin,
                        onValueChange = { tMin = it },
                        label = { Text("Low Target") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = tMax,
                        onValueChange = { tMax = it },
                        label = { Text("High Target") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Column(modifier = Modifier.weight(1.1f)) {
                        Text(
                            "Unit",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
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
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val pMin = tMin.toDoubleOrNull() ?: 70.0
                            val pMax = tMax.toDoubleOrNull() ?: 140.0
                            onSave(
                                currentProfile.copy(
                                    userName = uName.ifEmpty { "Patient" },
                                    doctorName = dName,
                                    doctorEmail = dMail,
                                    doctorPhone = dPhone,
                                    targetGlucoseMin = pMin,
                                    targetGlucoseMax = pMax,
                                    glucoseUnit = gUnit
                                )
                            )
                        }
                    ) {
                        Text("Save Profile")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefillFormDialog(
    viewModel: GlucoViewModel,
    editingLog: CartridgeRefillLog? = null,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var capacity by remember { mutableStateOf(editingLog?.capacity?.toInt()?.toString() ?: "300") }
    var date by remember { mutableStateOf(if (editingLog != null) viewModel.formatEpochToDateOnly(editingLog.dateTimeMillis) else viewModel.formatEpochToDateOnly(viewModel.getCurrentTimeMillis())) }
    var time by remember { mutableStateOf(if (editingLog != null) viewModel.formatEpochToTimeOnly(editingLog.dateTimeMillis) else viewModel.formatEpochToTimeOnly(viewModel.getCurrentTimeMillis())) }
    var actionType by remember { mutableStateOf(editingLog?.actionType ?: "Refill") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("refill_form_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (editingLog != null) "Edit Cartridge Log" else "Refill / Change Cartridge",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Specify size capacity, date and time of the insulin cartridge change/refill.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                // Quick choices
                if (editingLog == null) {
                    Text("Preset Sizes", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("100", "150", "300").forEach { preset ->
                            val isSelected = capacity == preset
                            Button(
                                onClick = { capacity = preset },
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
                }

                // Capacity Input
                OutlinedTextField(
                    value = capacity,
                    onValueChange = { capacity = it.filter { char -> char.isDigit() } },
                    label = { Text("Capacity Size (Units)") },
                    modifier = Modifier.fillMaxWidth().testTag("refill_capacity_input"),
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = { Text("Units", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // Date & Time inputs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1.2f).testTag("refill_date_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = { Text("Time (HH:MM)") },
                        modifier = Modifier.weight(0.8f).testTag("refill_time_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Action Type Selector
                var showActionDropdown by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = actionType,
                        onValueChange = { actionType = it },
                        label = { Text("Log Event Title / Action") },
                        modifier = Modifier.fillMaxWidth().testTag("refill_action_type_input"),
                        shape = RoundedCornerShape(10.dp),
                        trailingIcon = {
                            IconButton(onClick = { showActionDropdown = !showActionDropdown }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = showActionDropdown,
                        onDismissRequest = { showActionDropdown = false }
                    ) {
                        listOf("Refill", "Change (Empty)", "Size Change", "Pen Inserted").forEach { title ->
                            DropdownMenuItem(
                                text = { Text(title) },
                                onClick = {
                                    actionType = title
                                    showActionDropdown = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val capacityVal = capacity.toDoubleOrNull() ?: 300.0
                            if (editingLog != null) {
                                val calendar = viewModel.composeCalendarFromDateStrAndTimeStr(date, time)
                                val updatedLog = editingLog.copy(
                                    capacity = capacityVal,
                                    dateTimeMillis = calendar.timeInMillis,
                                    actionType = actionType
                                )
                                viewModel.saveRefillLog(updatedLog)
                            } else {
                                viewModel.refillCartridge(capacityVal, date, time)
                            }
                            onSave()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (editingLog != null) "Update Log" else "Update Cartridge")
                    }
                }
            }
        }
    }
}

