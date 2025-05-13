package com.example.ismapc

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.ui.graphics.Color
import android.app.AppOpsManager
import android.content.Context
import android.provider.Settings
import android.app.usage.UsageStatsManager
import android.app.Activity
import android.os.Build
import com.google.firebase.firestore.FieldValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush

class MainActivity : ComponentActivity() {
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
                            // No need to start background services for parents
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
                                        try {
                                            // Start all background services for child users
                                            startChildServices()
                                        } catch (e: Exception) {
                                            Log.e("MainActivity", "Error starting services: ${e.message}")
                                        }
                                    } else {
                                        // User not found in either collection
                                        Log.e("MainActivity", "User not found in either collection")
                                        Toast.makeText(this, "User type not found. Please sign in again.", Toast.LENGTH_LONG).show()
                                        auth.signOut()
                                        startActivity(Intent(this, LoginActivity::class.java))
                                        finish()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("MainActivity", "Error checking child collection: ${e.message}")
                                    Log.e("MainActivity", "Error details", e)
                                    Toast.makeText(this, "Error checking user type: ${e.message}", Toast.LENGTH_LONG).show()
                                    auth.signOut()
                                    startActivity(Intent(this, LoginActivity::class.java))
                                    finish()
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("MainActivity", "Error checking parent collection: ${e.message}")
                        Log.e("MainActivity", "Error details", e)
                        Toast.makeText(this, "Error checking user type: ${e.message}", Toast.LENGTH_LONG).show()
                        auth.signOut()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
            } else {
                // No user logged in
                Log.d("MainActivity", "No user logged in")
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
        }

        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (userType) {
                        "parent" -> ParentMainScreen(
                            onLogout = {
                                auth.signOut()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                        )
                        "child" -> ChildMainScreen(
                            onLogout = {
                                auth.signOut()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                        )
                        else -> {
                            // Show loading state
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

    private fun startChildServices() {
        try {
            Log.d("MainActivity", "Starting child services")
            
            // Get child profile first
            firestore.collection(USERS_COLLECTION)
                .document(CHILD_COLLECTION)
                .collection("profile")
                .document(auth.currentUser?.uid ?: "")
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val childName = document.getString("fullName") ?: "Child"
                        val childId = auth.currentUser?.uid ?: ""
                        
                        try {
                            // Start the InstalledAppsService
                            startService(Intent(this, InstalledAppsService::class.java))

                            // Start the AppLockService
                            startAppLockService()

                            // Start the DeviceLockService
                            val deviceLockServiceIntent = Intent(this, DeviceLockService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(deviceLockServiceIntent)
                            } else {
                                startService(deviceLockServiceIntent)
                            }

                            // Start location service
                            val locationServiceIntent = Intent(this, LocationService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(locationServiceIntent)
                            } else {
                                startService(locationServiceIntent)
                            }
                            
                            // Start screen time service
                            val screenTimeServiceIntent = Intent(this, ScreenTimeService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(screenTimeServiceIntent)
                            } else {
                                startService(screenTimeServiceIntent)
                            }

                            // Start content filtering service
                            startContentFilteringService(childId, childName)

                            // Start browser content monitor service
                            val browserMonitorIntent = Intent(this, BrowserContentMonitorService::class.java).apply {
                                putExtra("childId", childId)
                            }
                            startService(browserMonitorIntent)

                            Log.d("MainActivity", "All child services started successfully")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error starting services", e)
                            Toast.makeText(this, "Error starting services: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Log.e("MainActivity", "Child profile not found")
                        Toast.makeText(this, "Error: Child profile not found", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("MainActivity", "Error getting child profile", e)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in startChildServices", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startAppLockService() {
        try {
            val serviceIntent = Intent(this, AppLockService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting AppLockService", e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            // Only restart services for child users
            if (userType == "child") {
                Log.d("MainActivity", "Restarting child services in onResume")
                startChildServices()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onResume: ${e.message}")
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentMainScreen(onLogout: () -> Unit) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    Log.d("AuthCheck", "User email: ${currentUser?.email}")
    var parentData by remember { mutableStateOf<Map<String, Any>?>(null) }
    var childrenData by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val profilePictureManager = remember { ProfilePictureManager(context) }
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }

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
                                                put("documentId", doc.id)  // Add the document ID to the data map
                                            }
                                        }
                                        childrenData = filteredChildren
                                        Log.d("ParentMainScreen", "Children data updated. Found ${filteredChildren.size} children")
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
            .background(Color.White)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { /* TODO: Handle trash can click */ },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Main Content
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        // Top Section with Theme Background
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                                    clip = false
                                )
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFFE0852D), // ISMAPC Orange
                                            Color(0xFFFFB27D)  // ISMAPC LightOrange
                                        )
                                    ),
                                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                                )
                                .padding(vertical = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Top Bar Row with Info and Action Icons
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Info Icon with curved white background
                                    Box(
                                        modifier = Modifier
                                            .width(64.dp)
                                            .height(48.dp)
                                            .offset(x = (-16).dp)
                                            .background(
                                                color = Color.White,
                                                shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                                            )
                                            .clickable { /* TODO: Handle info click */ },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Information",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }

                                    // Settings and Logout Icons
                                    Row(
                                        modifier = Modifier.padding(end = 16.dp)
                                    ) {
                                        IconButton(
                                            onClick = { /* TODO: Handle settings click */ }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Settings",
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = onLogout
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ExitToApp,
                                                contentDescription = "Logout",
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }

                                // Parent Profile Section
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Profile Picture
                                    Box(
                                        modifier = Modifier
                                            .size(160.dp)
                                            .border(
                                                width = 4.dp,
                                                color = Color.White,
                                                shape = CircleShape
                                            )
                                            .clip(CircleShape)
                                            .background(Color(0xFFD6D7D3)), // LightGray background
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
                                                tint = Color.Black,
                                                modifier = Modifier.size(80.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Parent Name
                                    parentData?.get("fullName")?.let { name ->
                                        Text(
                                            text = name.toString(),
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = Color.White
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Parent Email
                                    parentData?.get("email")?.let { email ->
                                        Text(
                                            text = email.toString(),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Parent and Phone Number Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Parent",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = Color.White
                                        )
                                        
                                        VerticalDivider(
                                            modifier = Modifier
                                                .height(24.dp)
                                                .padding(horizontal = 16.dp),
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                        
                                        parentData?.get("phoneNumber")?.let { phone ->
                                            Text(
                                                text = phone.toString(),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Children Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Spacer(modifier = Modifier.height(24.dp))

                            // Add Child Button Section
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Button(
                                    onClick = { /* TODO: Handle add child click */ },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD6D7D3) // LightGray from theme
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .width(200.dp)
                                        .shadow(
                                            elevation = 4.dp,
                                            shape = RoundedCornerShape(24.dp),
                                            clip = false
                                        ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add Child",
                                            tint = Color.Black,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }

                            // Children List
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(childrenData) { child ->
                                    ChildProfileCard(
                                        childProfile = child,
                                        onClick = {
                                            val intent = Intent(context, ChildDetailsActivity::class.java).apply {
                                                // Get the document ID which contains the child's UID
                                                val childDocId = child["documentId"] as? String
                                                if (childDocId != null) {
                                                    putExtra("childId", childDocId)
                                                    putExtra("childName", child["fullName"] as? String)
                                                    Log.d("ParentMainScreen", "Starting ChildDetailsActivity with childId: $childDocId")
                                                } else {
                                                    Log.e("ParentMainScreen", "Child document ID is null")
                                                    Toast.makeText(context, "Error: Child ID not found", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            context.startActivity(intent)
                                        }
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

@Composable
fun ChildProfileCard(
    childProfile: Map<String, Any>,
    onClick: () -> Unit
) {
    var isLocked by remember { mutableStateOf(false) }
    var isUpdating by remember { mutableStateOf(false) }
    val firestore = FirebaseFirestore.getInstance()
    val childId = childProfile["documentId"] as? String ?: ""
    val context = LocalContext.current

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
            // Profile picture or placeholder
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .border(
                        width = 2.dp,
                        color = Color.White,
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .background(Color(0xFFD6D7D3)), // LightGray background
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Child profile picture",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Child info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = childProfile["fullName"] as? String ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black
                )
            }

            // Lock device button
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
                                isUpdating = false
                                Toast.makeText(
                                    context,
                                    if (!isLocked) "Device unlocked successfully" else "Device locked successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .addOnFailureListener { e ->
                                Log.e("ChildProfileCard", "Error updating device lock state", e)
                                isUpdating = false
                                Toast.makeText(
                                    context,
                                    "Error: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
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
                            imageVector = Icons.Default.Lock,
                            contentDescription = if (isLocked) "Unlock Device" else "Lock Device",
                            tint = if (isLocked) Color.White else Color.Black
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
        }
    }
}