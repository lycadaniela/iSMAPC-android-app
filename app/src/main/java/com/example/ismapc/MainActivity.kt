package com.example.ismapc

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.shadow
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.WriteBatch
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit
import android.app.AppOpsManager
import android.content.Context
import android.provider.Settings
import android.app.usage.UsageStatsManager
import android.app.Activity
import android.os.Build

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ChildMainScreen(onLogout: () -> Unit) {
        val context = LocalContext.current
        var hasUsagePermission by remember { mutableStateOf(false) }
        var serviceStarted by remember { mutableStateOf(false) }
        var locationServiceStarted by remember { mutableStateOf(false) }

        // Check permissions when the screen is first loaded
        LaunchedEffect(Unit) {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
            hasUsagePermission = mode == android.app.AppOpsManager.MODE_ALLOWED

            if (!hasUsagePermission || !Settings.canDrawOverlays(context)) {
                // Navigate to permission screen if permissions are not granted
                (context as? Activity)?.let { activity ->
                    activity.startActivity(Intent(context, ChildPermissionActivity::class.java))
                }
            } else {
                // Start LocationService if not already started
                if (!locationServiceStarted) {
                    val locationIntent = Intent(context, LocationService::class.java)
                    context.startService(locationIntent)
                    locationServiceStarted = true
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                (context as? Activity)?.let { activity ->
                                    activity.startActivity(Intent(context, ChildPermissionActivity::class.java))
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Check Permissions"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onLogout) {
                            Icon(
                                imageVector = Icons.Filled.ExitToApp,
                                contentDescription = "Logout"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Success icon
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Success",
                    modifier = Modifier
                        .size(64.dp)
                        .padding(bottom = 16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                // Connected successfully text
                Text(
                    text = "Connected Successfully",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Spacer(modifier = Modifier.height(32.dp))

                // Dashboard button
                Button(
                    onClick = {
                        context.startActivity(Intent(context, ChildDashboardActivity::class.java))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 32.dp)
                ) {
                    Text("Open My Dashboard")
                }
            }
        }
    }

    companion object {
        const val USERS_COLLECTION = "users"
        const val PARENTS_COLLECTION = "parents"
        const val CHILD_COLLECTION = "child"
        const val PROFILE_DOCUMENT = "profile"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var userType by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        enableEdgeToEdge()

        try {
            // Check user type first before starting any services
            val currentUser = auth.currentUser
            if (currentUser != null) {
                Log.d("MainActivity", "Current user: ${currentUser.uid}, email: ${currentUser.email}")
                // First check parent collection
                firestore.collection(USERS_COLLECTION)
                    .document(PARENTS_COLLECTION)
                    .collection(currentUser.uid)
                    .document(PROFILE_DOCUMENT)
                    .get()
                    .addOnSuccessListener { parentDoc ->
                        Log.d("MainActivity", "Checking parent collection: ${parentDoc.exists()}")
                        if (parentDoc.exists()) {
                            userType = "parent"
                            Log.d("MainActivity", "User is a parent")
                            // Stop any running child services when switching to parent
                            stopChildServices()
                            // Initialize UI
                            initializeUI()
                        } else {
                            // If not found in parent, check child collection
                            Log.d("MainActivity", "Checking child collection")
                            firestore.collection(USERS_COLLECTION)
                                .document(CHILD_COLLECTION)
                                .collection("profile")
                                .document(currentUser.uid)
                                .get()
                                .addOnSuccessListener { childDoc ->
                                    Log.d("MainActivity", "Child doc exists: ${childDoc.exists()}")
                                    if (childDoc.exists()) {
                                        userType = "child"
                                        Log.d("MainActivity", "User is a child")
                                        // Initialize UI first
                                        initializeUI()
                                        // Schedule content suggestion worker
                                        scheduleContentSuggestionWorker()
                                        // Then check permissions before starting services
                                        checkAndRequestPermissions()
                                    } else {
                                        // User not found in either collection
                                        handleUserNotFound()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    handleError("Error checking child collection", e)
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        handleError("Error checking parent collection", e)
                    }
            } else {
                // No user logged in
                Log.d("MainActivity", "No user logged in")
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        } catch (e: Exception) {
            handleError("Error in onCreate", e)
        }
    }

    private fun initializeUI() {
        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (userType) {
                        "parent" -> ParentMainScreen(
                            onLogout = {
                                stopChildServices()
                                auth.signOut()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                        )
                        "child" -> ChildMainScreen(
                            onLogout = {
                                stopChildServices()
                                auth.signOut()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                        )
                        else -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        val hasUsagePermission = mode == AppOpsManager.MODE_ALLOWED
        val hasOverlayPermission = Settings.canDrawOverlays(this)

        if (!hasUsagePermission || !hasOverlayPermission) {
            // Navigate to permission screen
            startActivity(Intent(this, ChildPermissionActivity::class.java))
        } else {
            // All permissions granted, start services
            startChildServices()
        }
    }

    private fun handleUserNotFound() {
        Log.e("MainActivity", "User not found in either collection")
        Toast.makeText(this, "User type not found. Please sign in again.", Toast.LENGTH_LONG).show()
        auth.signOut()
        GoogleSignIn.getClient(this, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build())
            .signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun handleError(message: String, e: Exception) {
        Log.e("MainActivity", "$message: ${e.message}")
        Log.e("MainActivity", "Error details", e)
        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        auth.signOut()
        GoogleSignIn.getClient(this, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build())
            .signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun stopChildServices() {
        try {
            Log.d("MainActivity", "Stopping child services")

            // Stop AppUsageService
            stopService(Intent(this, AppUsageService::class.java))

            // Stop other existing services...
            stopService(Intent(this, AppLockService::class.java))
            stopService(Intent(this, ScreenTimeService::class.java))
            stopService(Intent(this, ContentFilteringService::class.java))

        } catch (e: Exception) {
            Log.e("MainActivity", "Error stopping child services", e)
        }
    }

    private fun startChildServices() {
        try {
            Log.d("MainActivity", "Starting child services")

            // Start AppUsageService
            startAppUsageService()

            // Start other existing services...
            // For example:
            startService(Intent(this, AppLockService::class.java))
            startService(Intent(this, ScreenTimeService::class.java))
            startService(Intent(this, ContentFilteringService::class.java))
            startService(Intent(this, InstalledAppsService::class.java))

            // Start DeviceLockService for device lock/unlock functionality
            val deviceLockIntent = Intent(this, DeviceLockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(deviceLockIntent)
            } else {
                startService(deviceLockIntent)
            }
            Log.d("MainActivity", "DeviceLockService started")

        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting child services", e)
        }
    }

    private fun startAppUsageService() {
        try {
            Log.d("MainActivity", "Starting AppUsageService")
            val serviceIntent = Intent(this, AppUsageService::class.java)

            // Start as a foreground service to ensure it keeps running
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting AppUsageService", e)
        }
    }

    override fun onResume() {
        super.onResume()

        // Check if user is a child and restart services if needed
        if (userType == "child") {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )

            // Only start services if we have permissions
            if (mode == AppOpsManager.MODE_ALLOWED) {
                Log.d("MainActivity", "Child account resumed, ensuring services are running")
                startAppUsageService()
                startService(Intent(this, InstalledAppsService::class.java))

                // Ensure DeviceLockService is running
                val deviceLockIntent = Intent(this, DeviceLockService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(deviceLockIntent)
                } else {
                    startService(deviceLockIntent)
                }
                Log.d("MainActivity", "DeviceLockService restarted in onResume")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Stop services when activity is destroyed
            if (userType == "child") {
                Log.d("MainActivity", "Stopping child services in onDestroy")
                stopService(Intent(this, LocationService::class.java))
                stopService(Intent(this, ScreenTimeService::class.java))
                stopService(Intent(this, AppLockService::class.java))
                stopService(Intent(this, InstalledAppsService::class.java))
                stopService(Intent(this, DeviceLockService::class.java))
                stopService(Intent(this, ContentFilteringService::class.java))
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onDestroy: ${e.message}")
        }
    }

    private fun startContentFilteringService(childId: String, childName: String) {
        try {
            val serviceIntent = Intent(this, ContentFilteringService::class.java).apply {
                putExtra("childId", childId)
                putExtra("childName", childName)
                putExtra("parentId", FirebaseAuth.getInstance().currentUser?.uid)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting ContentFilteringService", e)
        }
    }

    private fun stopContentFilteringService() {
        stopService(Intent(this, ContentFilteringService::class.java))
    }

    private fun scheduleContentSuggestionWorker() {
        try {
            Log.d("MainActivity", "Scheduling content suggestion worker")
            
            val workManager = WorkManager.getInstance(applicationContext)
            val workRequest = PeriodicWorkRequestBuilder<ContentSuggestionWorker>(
                24, TimeUnit.HOURS,
                1, TimeUnit.HOURS
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

            workManager.enqueueUniquePeriodicWork(
                "content_suggestion_worker",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
            Log.d("MainActivity", "Content suggestion worker scheduled")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error scheduling content suggestion worker", e)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentMainScreen(onLogout: () -> Unit) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    var parentData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var childrenData by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isDeleteMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val profilePictureManager = remember { ProfilePictureManager(context) }
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showNotificationsMenu by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }

    // Function to delete parent account and associated child accounts
    fun deleteParentAccount() {
        isDeletingAccount = true
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val parentEmail = currentUser?.email

        if (currentUser != null && parentEmail != null) {
            // First, get all child accounts associated with this parent
            firestore.collection(MainActivity.USERS_COLLECTION)
                .document(MainActivity.CHILD_COLLECTION)
                .collection("profile")
                .whereEqualTo("parentEmail", parentEmail)
                .get()
                .addOnSuccessListener { childDocuments ->
                    val batch = firestore.batch()

                    // Create a deletion request document
                    val deletionRequest = hashMapOf(
                        "parentId" to currentUser.uid,
                        "parentEmail" to parentEmail,
                        "requestedAt" to FieldValue.serverTimestamp(),
                        "status" to "pending",
                        "type" to "account_deletion",
                        // Add verification data
                        "verificationData" to hashMapOf(
                            "parentName" to (parentData?.get("fullName") as? String ?: "Unknown"),
                            "parentPhone" to (parentData?.get("phoneNumber") as? String ?: "Unknown"),
                            "childAccounts" to childDocuments.map { doc ->
                                hashMapOf(
                                    "childId" to doc.id,
                                    "childName" to (doc.getString("fullName") ?: "Unknown"),
                                    "childEmail" to (doc.getString("email") ?: "Unknown")
                                )
                            },
                            "requestSource" to "parent_dashboard",
                            "deviceInfo" to hashMapOf(
                                "model" to Build.MODEL,
                                "manufacturer" to Build.MANUFACTURER,
                                "androidVersion" to Build.VERSION.RELEASE
                            )
                        )
                    )

                    // Add the deletion request to a special collection
                    firestore.collection("deletionRequests")
                        .document(currentUser.uid)
                        .set(deletionRequest)
                        .addOnSuccessListener {
                            Log.d("AccountDeletion", "Deletion request created successfully for parent: $parentEmail")
                            Log.d("AccountDeletion", "Associated child accounts: ${childDocuments.map { it.id }}")

                            // Delete all child accounts and their data
                            for (childDoc in childDocuments) {
                                val childId = childDoc.id

                                // Delete child's profile
                                batch.delete(childDoc.reference)

                                // Delete child's data from other collections
                                val collectionsToDelete = listOf(
                                    "screenTime",
                                    "installedApps",
                                    "lockedApps",
                                    "contentFiltering",
                                    "contentToFilter",
                                    "deviceLocks",
                                    "locations"
                                )

                                collectionsToDelete.forEach { collectionName ->
                                    // Delete documents where childId matches
                                    firestore.collection(collectionName)
                                        .whereEqualTo("childId", childId)
                                        .get()
                                        .addOnSuccessListener { docs ->
                                            docs.forEach { doc ->
                                                batch.delete(doc.reference)
                                            }
                                        }

                                    // Also delete documents where the document ID is the childId
                                    batch.delete(firestore.collection(collectionName).document(childId))
                                }
                            }

                            // Delete parent's profile
                            batch.delete(
                                firestore.collection(MainActivity.USERS_COLLECTION)
                                    .document(MainActivity.PARENTS_COLLECTION)
                                    .collection(currentUser.uid)
                                    .document(MainActivity.PROFILE_DOCUMENT)
                            )

                            // Commit all deletions
                            batch.commit()
                                .addOnSuccessListener {
                                    isDeletingAccount = false
                                    Toast.makeText(context, "Account deletion request submitted. Your account will be permanently deleted within 24 hours.", Toast.LENGTH_LONG).show()
                                    // Sign out and navigate to login
                                    auth.signOut()
                                    (context as? Activity)?.let { activity ->
                                        activity.startActivity(Intent(context, LoginActivity::class.java))
                                        activity.finish()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    isDeletingAccount = false
                                    Toast.makeText(context, "Error deleting data: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                        .addOnFailureListener { e ->
                            isDeletingAccount = false
                            Toast.makeText(context, "Error submitting deletion request: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { e ->
                    isDeletingAccount = false
                    Toast.makeText(context, "Error finding child accounts: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            isDeletingAccount = false
            Toast.makeText(context, "Error: User not found", Toast.LENGTH_SHORT).show()
        }
    }

    var childLocations by remember { mutableStateOf(mapOf<String, List<Map<String, Any>>>()) }

    // Function to fetch location data for a child
    fun fetchChildLocations(childId: String) {
        val firestore = FirebaseFirestore.getInstance()

        // Listen to location history for this child
        firestore.collection("locations")
            .document(childId)
            .collection("history")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)  // Limit to last 100 locations
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ParentMainScreen", "Error fetching locations for child $childId", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val locations = snapshot.documents.mapNotNull { doc ->
                        doc.data?.toMutableMap()?.apply {
                            put("documentId", doc.id)
                        }
                    }

                    // Update the locations map
                    childLocations = childLocations.toMutableMap().apply {
                        put(childId, locations)
                    }.toMap()
                    Log.d("ParentMainScreen", "Fetched ${locations.size} locations for child $childId")
                }
            }
    }

    // Fetch parent data and children data from Firestore using real-time listeners
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            Log.d("ParentMainScreen", "Starting data fetch for parent ID: $uid")

            // Real-time listener for parent data
            val parentListener = FirebaseFirestore.getInstance()
                .collection(MainActivity.USERS_COLLECTION)
                .document(MainActivity.PARENTS_COLLECTION)
                .collection(uid)
                .document(MainActivity.PROFILE_DOCUMENT)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("ParentMainScreen", "Error listening to parent data", error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        parentData = snapshot.data
                        Log.d("ParentMainScreen", "Parent profile updated")

                        // After getting parent data, set up real-time listener for children
                        val parentEmail = snapshot.getString("email")
                        if (parentEmail != null) {
                            Log.d("ParentMainScreen", "Setting up children listener for parent email: $parentEmail")

                            // Real-time listener for children data
                            val childrenListener = FirebaseFirestore.getInstance()
                                .collectionGroup(MainActivity.PROFILE_DOCUMENT)
                                .whereEqualTo("parentEmail", parentEmail)
                                .addSnapshotListener { querySnapshot, error ->
                                    if (error != null) {
                                        Log.e("ParentMainScreen", "Error listening to children data", error)
                                        return@addSnapshotListener
                                    }

                                    if (querySnapshot != null) {
                                        val filteredChildren = querySnapshot.documents.mapNotNull { doc ->
                                            doc.data?.toMutableMap()?.apply {
                                                put("documentId", doc.id)
                                            }
                                        }
                                        childrenData = filteredChildren
                                        Log.d("ParentMainScreen", "Children data updated. Found ${filteredChildren.size} children")

                                        // Start fetching locations for each child
                                        filteredChildren.forEach { child ->
                                            val childId = child["documentId"] as? String
                                            if (childId != null) {
                                                fetchChildLocations(childId)
                                            }
                                        }

                                        isLoading = false
                                    }
                                }
                        } else {
                            Log.e("ParentMainScreen", "Parent email is null")
                            isLoading = false
                        }
                    } else {
                        Log.e("ParentMainScreen", "No parent profile found for user: $uid")
                        isLoading = false
                    }
                }

            // Load profile picture
            profileBitmap = profilePictureManager.getProfilePictureBitmap(uid)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { },
                    actions = {
                        // Notifications Icon
                        Box {
                            IconButton(onClick = { showNotificationsMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = Color(0xFF4A4A4A)
                                )
                            }
                            DropdownMenu(
                                expanded = showNotificationsMenu,
                                onDismissRequest = { showNotificationsMenu = false },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline,
                                        shape = MaterialTheme.shapes.medium
                                    )
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text("No new notifications")
                                        }
                                    },
                                    onClick = {
                                        showNotificationsMenu = false
                                    }
                                )
                            }
                        }
                        // Settings Icon
                        Box {
                            IconButton(onClick = { showSettingsMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = Color(0xFF4A4A4A)
                                )
                            }
                            DropdownMenu(
                                expanded = showSettingsMenu,
                                onDismissRequest = { showSettingsMenu = false },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline,
                                        shape = MaterialTheme.shapes.medium
                                    )
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text("Change Password")
                                        }
                                    },
                                    onClick = {
                                        showSettingsMenu = false
                                        val intent = Intent(context, ChangePasswordActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text("About")
                                        }
                                    },
                                    onClick = {
                                        context.startActivity(Intent(context, AboutActivity::class.java))
                                        showSettingsMenu = false
                                    }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = Color.Red
                                            )
                                            Text(
                                                "Delete Account",
                                                color = Color.Red
                                            )
                                        }
                                    },
                                    onClick = {
                                        showSettingsMenu = false
                                        showDeleteAccountDialog = true
                                    }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ExitToApp,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text("Logout")
                                        }
                                    },
                                    onClick = {
                                        showSettingsMenu = false
                                        onLogout()
                                    }
                                )
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Profile Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Profile Picture
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        width = 2.dp,
                                        color = Color(0xFFE0852D),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (profileBitmap != null) {
                                    Image(
                                        bitmap = profileBitmap!!.asImageBitmap(),
                                        contentDescription = "Profile Picture",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Profile Picture",
                                        tint = Color(0xFFE0852D),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Parent Info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = parentData?.get("fullName")?.toString() ?: "Parent",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = parentData?.get("email")?.toString() ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF4A4A4A)
                                )
                                Text(
                                    text = parentData?.get("phoneNumber")?.toString() ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF4A4A4A)
                                )
                            }

                            // Edit Profile Button
                            IconButton(
                                onClick = {
                                    val intent = Intent(context, ParentEditProfileActivity::class.java)
                                    intent.putExtra("parentId", currentUser?.uid)
                                    intent.putExtra("parentName", parentData?.get("fullName")?.toString())
                                    intent.putExtra("parentEmail", parentData?.get("email")?.toString())
                                    intent.putExtra("parentPhone", parentData?.get("phoneNumber")?.toString())
                                    context.startActivity(intent)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Profile",
                                    tint = Color(0xFFE0852D)
                                )
                            }
                        }
                    }

                    // Divider between sections
                    Divider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Quick Actions Section
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF4A4A4A),
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Add Child Button
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val intent = Intent(context, ChildSignUpActivity::class.java)
                                    intent.putExtra("parentEmail", parentData?.get("email")?.toString())
                                    context.startActivity(intent)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = "Add Child",
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Add Child",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White
                                    )
                                )
                            }
                        }

                        // FAQ Button
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val intent = Intent(context, InfoActivity::class.java)
                                    context.startActivity(intent)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "FAQ",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "FAQ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Children Section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Children",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Delete Mode Toggle
                        IconButton(
                            onClick = { isDeleteMode = !isDeleteMode }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = if (isDeleteMode) "Exit Delete Mode" else "Enter Delete Mode",
                                tint = if (isDeleteMode) Color.Red else Color(0xFFE0852D)
                            )
                        }
                    }

                    if (childrenData.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonOff,
                                    contentDescription = "No Children",
                                    tint = Color(0xFF4A4A4A),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No children added yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF4A4A4A)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Add a child to start monitoring their device",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF4A4A4A).copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        childrenData.forEach { child ->
                            var isLocked by remember { mutableStateOf(false) }
                            var isUpdating by remember { mutableStateOf(false) }

                            // Set up real-time listener for device lock state
                            LaunchedEffect(child["documentId"] as String) {
                                val childId = child["documentId"] as String
                                if (childId.isNotBlank()) {
                                    FirebaseFirestore.getInstance().collection("deviceLocks")
                                        .document(childId)
                                        .addSnapshotListener { snapshot, error ->
                                            if (error != null) {
                                                Log.e("ParentMainScreen", "Error listening to device lock state", error)
                                                return@addSnapshotListener
                                            }

                                            if (snapshot != null && snapshot.exists()) {
                                                isLocked = snapshot.getBoolean("isLocked") ?: false
                                            } else {
                                                isLocked = false
                                            }
                                        }
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!isDeleteMode) {
                                            val intent = Intent(context, ChildDetailsActivity::class.java)
                                            intent.putExtra("childId", child["documentId"] as String)
                                            intent.putExtra("childName", child["fullName"] as String)
                                            context.startActivity(intent)
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF5F5F5)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Child Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = (child["fullName"] as String).firstOrNull()?.toString()?.uppercase() ?: "?",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color(0xFF4A4A4A)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    // Child Info
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = child["fullName"] as String,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    if (isDeleteMode) {
                                        // Delete Button
                                        IconButton(
                                            onClick = {
                                                val childId = child["documentId"] as String
                                                val batch = FirebaseFirestore.getInstance().batch()

                                                // Delete child's profile
                                                val profileRef = FirebaseFirestore.getInstance()
                                                    .collection(MainActivity.USERS_COLLECTION)
                                                    .document(MainActivity.CHILD_COLLECTION)
                                                    .collection("profile")
                                                    .document(childId)
                                                batch.delete(profileRef)

                                                // Delete child's data from other collections
                                                val collectionsToDelete = listOf(
                                                    "screenTime",
                                                    "locations",
                                                    "installedApps",
                                                    "lockedApps",
                                                    "contentFiltering",
                                                    "contentToFilter",
                                                    "deviceLocks"
                                                )

                                                collectionsToDelete.forEach { collectionName ->
                                                    // Delete documents where childId matches
                                                    FirebaseFirestore.getInstance().collection(collectionName)
                                                        .whereEqualTo("childId", childId)
                                                        .get()
                                                        .addOnSuccessListener { docs ->
                                                            docs.forEach { doc ->
                                                                batch.delete(doc.reference)
                                                            }
                                                        }

                                                    // Also delete documents where the document ID is the childId
                                                    batch.delete(FirebaseFirestore.getInstance().collection(collectionName).document(childId))
                                                }

                                                // Commit all deletions
                                                batch.commit()
                                                    .addOnSuccessListener {
                                                        Toast.makeText(context, "Child account deleted successfully", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Toast.makeText(context, "Error deleting child account: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Child",
                                                tint = Color.Red
                                            )
                                        }
                                    } else {
                                        // Lock/Unlock Button
                                        IconButton(
                                            onClick = {
                                                if (!isUpdating) {
                                                    isUpdating = true
                                                    val childId = child["documentId"] as String
                                                    val updates = hashMapOf(
                                                        "isLocked" to !isLocked,
                                                        "lastUpdated" to FieldValue.serverTimestamp()
                                                    )

                                                    FirebaseFirestore.getInstance().collection("deviceLocks")
                                                        .document(childId)
                                                        .set(updates)
                                                        .addOnSuccessListener {
                                                            isUpdating = false
                                                        }
                                                        .addOnFailureListener { e ->
                                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                            isUpdating = false
                                                        }
                                                }
                                            },
                                            enabled = !isUpdating
                                        ) {
                                            if (isUpdating) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                                    contentDescription = if (isLocked) "Unlock Device" else "Lock Device",
                                                    tint = if (isLocked) Color.Red else MaterialTheme.colorScheme.primary
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
    }

    // Delete Account Dialog
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingAccount) {
                    showDeleteAccountDialog = false
                }
            },
            title = {
                Text(
                    "Delete Account",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column {
                    Text(
                        "Are you sure you want to delete your account? This action cannot be undone and will:",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "• Delete your parent account",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "• Delete all associated child accounts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "• Permanently delete all data",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Note: Your account will be permanently deleted within 24 hours after submitting the request.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteParentAccount()
                    },
                    enabled = !isDeletingAccount
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            "Delete Account",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isDeletingAccount) {
                            showDeleteAccountDialog = false
                        }
                    },
                    enabled = !isDeletingAccount
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ChildProfileCard(
    childProfile: Map<String, Any>,
    onClick: () -> Unit,
    isTrashMode: Boolean,
    onDelete: (String) -> Unit
) {
    var isLocked by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }
    val firestore = FirebaseFirestore.getInstance()
    val childId = childProfile["documentId"] as? String ?: ""
    val context = LocalContext.current
    val profilePictureManager = remember { ProfilePictureManager(context) }
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Load profile picture
    LaunchedEffect(childId) {
        profileBitmap = profilePictureManager.getProfilePictureBitmap(childId)
    }

    // Set up real-time listener for device lock state
    LaunchedEffect(childId) {
        if (childId.isNotBlank()) {
            firestore.collection("deviceLocks")
                .document(childId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("ChildProfileCard", "Error listening to device lock state", error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        isLocked = snapshot.getBoolean("isLocked") ?: false
                    } else {
                        isLocked = false
                    }
                }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                clip = false
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile picture with white border
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (profileBitmap != null) {
                    Image(
                        bitmap = profileBitmap!!.asImageBitmap(),
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile Picture",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Child name
            Text(
                text = childProfile["fullName"] as? String ?: "Unknown Child",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            // Lock/Delete Button
            if (isTrashMode) {
                // Delete Button
                Button(
                    onClick = { onDelete(childId) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Child",
                            tint = Color.White
                        )
                        Text(
                            text = "Delete",
                            color = Color.White
                        )
                    }
                }
            } else {
                // Lock Button
                Button(
                    onClick = {
                        if (!isUpdating) {
                            isUpdating = true
                            val updates = hashMapOf(
                                "isLocked" to !isLocked,
                                "lastUpdated" to FieldValue.serverTimestamp()
                            )

                            firestore.collection("deviceLocks")
                                .document(childId)
                                .set(updates)
                                .addOnSuccessListener {
                                    Log.d("ChildProfileCard", "Successfully updated device lock state")
                                    isUpdating = false
                                }
                                .addOnFailureListener { e ->
                                    Log.e("ChildProfileCard", "Error updating device lock state", e)
                                    Toast.makeText(
                                        context,
                                        "Error: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    isUpdating = false
                                }
                        }
                    },
                    enabled = !isUpdating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLocked)
                            Color.Red
                        else
                            Color.White
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = if (isLocked) Color.White else Color.Black
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (isLocked) "Unlock Device" else "Lock Device",
                                tint = if (isLocked) Color.Red else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isLocked) "Unlock" else "Lock",
                                color = if (isLocked) Color.White else Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildMainScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    var hasUsagePermission by remember { mutableStateOf(false) }
    var serviceStarted by remember { mutableStateOf(false) }
    var locationServiceStarted by remember { mutableStateOf(false) }

    // Check permissions when the screen is first loaded
    LaunchedEffect(Unit) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        hasUsagePermission = mode == android.app.AppOpsManager.MODE_ALLOWED

        if (!hasUsagePermission || !Settings.canDrawOverlays(context)) {
            // Navigate to permission screen if permissions are not granted
            (context as? Activity)?.let { activity ->
                activity.startActivity(Intent(context, ChildPermissionActivity::class.java))
            }
        } else {
            // Start LocationService if not already started
            if (!locationServiceStarted) {
                val locationIntent = Intent(context, LocationService::class.java)
                context.startService(locationIntent)
                locationServiceStarted = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            (context as? Activity)?.let { activity ->
                                activity.startActivity(Intent(context, ChildPermissionActivity::class.java))
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Check Permissions"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Filled.ExitToApp,
                            contentDescription = "Logout"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Success icon
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Success",
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = 16.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            // Connected successfully text
            Text(
                text = "Connected Successfully",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Location service status
            Text(
                text = if (locationServiceStarted) "Location tracking: Active" else "Location tracking: Inactive",
                style = MaterialTheme.typography.bodyMedium,
                color = if (locationServiceStarted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Dashboard button
            Button(
                onClick = {
                    context.startActivity(Intent(context, ChildDashboardActivity::class.java))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 32.dp)
            ) {
                Text("Open My Dashboard")
            }
        }
    }
}
}