<div align="center">
<img width="1200" height="475" alt="GHBanner" src="---" />
</div>



Application Feature Overview & Capabilities
The application is a comprehensive Personal Diatetic & Clinical Health Tracker designed with Material Design 3 and responsive Jetpack Compose components. It offers a secure, highly organized visual ecosystem for tracking diabetic health indices, blood pressure, insulin administration, and system synchronization.

1. 🏠 Home Dashboard (Home)
The central hub provides an intuitive overview of your real-time health metrics and action shortcuts:
Insulin Cartridge Tracker: Visually displays remaining insulin units with a color-coded circular progress gauge and capacity indicator.
Quick Logging Buttons: Compact actions to log Insulin Delivery, Glucose Level, Blood Pressure, and records of Cartridge Refill directly from the main view.
Target Ranges at a Glance: Direct feedback comparing your recent metrics against target thresholds setup by your clinician.
2. 📋 Comprehensive Logs (Logs)
A historical database featuring precise category separation, viewing, editing, and deletion capabilities to manage all logged data:
Glucose Readings: Logs numeric sugar readings, customizable meal contexts (e.g., Before Breakfast, Post Dinner), timestamps, and optional clinical notes.
Insulin Records: Keeps track of rapid-acting or long-acting insulin doses (in Units), delivery timestamps, and insulin types.
Blood Pressure Cards: Tracks systolic, diastolic, and pulse rate logs to monitor cardiac rhythm alongside diabetic trends.
Cartridge Refills: Registers cartridge replacements or volume changes to keep insulin capacity calculations highly accurate.
3. 🔔 Medical Reminders (Reminders)
An integrated, reliable clinical scheduler to make sure you never miss an critical check:
Schedule Alerts: Set customized timers for blood glucose sampling, insulin dosages, or clinical consultations.
Easy Toggle Controls: Turn specific alarms on or off instantly with dynamic state updates.
Configurable Recurrence: Edit existing reminders, change times, adjust status messages, and categorize logs efficiently.
4. 📊 Clinical Reports & Visualization (Reports)
Dynamic visual rendering that translates numerical logs into clean, visual insights:
Average Metrics: Computes automatic clinical averages for blood sugar levels, blood pressure readings (systolic/diastolic ratios), and total insulin units consumed.
Dynamic Trend Charts: Plots blood glucose history on sequential timeline charts, helping you easily inspect spikes, dips, and patterns relative to fasting or postprandial meals.
Statistics Card Grid: Shows overall metrics designed to simplify reporting during physician consultations.
5. 👥 Clinical Profile (Profile)
A screen dedicated to clinical tracking rules and professional care coordinator information:
Aesthetic Configuration Fields: Modify target blood glucose thresholds (Minimum/Maximum boundaries) and insulin cartridge capacity limits.
Physician Details: Safely input and save name, contact telephone, and email coordinates for your primary clinician.
Quick-Switch Profiles: Compact visual styling lets you adjust configuration profiles on the fly.
6. ⚙️ App Settings & Configuration (Settings)
The underlying customization board overseeing appearance, database security, and cloud synchronization:
Theme Selection: Dynamic visual modes (such as Midnight Carbon theme) ensure eye-safe navigation during night readings.
Secure Cloud Firestore Backup:
Integrates modern Firebase Authentication (secure username, email, and password registration/login, including verified Google identity protocols).
Behind-the-Scenes Syncing: Auto-synchronizes your user profile parameters, historical glucose levels, insulin deliveries, and heart pressure cards securely in Firestore collections upon sign-on or when general actions are initiated.
Google Drive Integration: Toggleable backup link allowing direct exporting and importing of records to secure cloud storage files if requested.
Local Database Maintenance: Action items to manually refresh cache, test network latency, or wipe local databases securely when changing devices.
