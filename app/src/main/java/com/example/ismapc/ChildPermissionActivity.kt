package com.example.ismapc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ismapc.ui.theme.ISMAPCTheme
import android.app.AppOpsManager
import android.content.Context
import android.widget.Toast
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import android.util.Log

class ChildPermissionActivity : ComponentActivity() {
    val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.FOREGROUND_SERVICE_LOCATION,
        Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
    )

    var currentPermissionStep = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        checkLocationPermissionAndProceed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChildPermissionScreen(this)
                }
            }
        }
    }

    fun requestPermissions() {
        currentPermissionStep = 0
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest)
        } else {
            checkLocationPermissionAndProceed()
        }
    }

    private fun checkLocationPermissionAndProceed() {
        val allLocationPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allLocationPermissionsGranted) {
            currentPermissionStep = 1
        }
    }

    private fun checkUsageStatsAndProceed() {
        if (checkUsageStatsPermission()) {
            currentPermissionStep = 2
        }
    }

    private fun checkOverlayAndProceed() {
        if (checkOverlayPermission()) {
            currentPermissionStep = 3
        }
    }

    private fun checkAccessibilityAndProceed() {
        if (checkAccessibilityPermission()) {
            finish()
        }
    }

    private fun checkAllPermissionsAndFinish() {
        try {
            // Check if all runtime permissions are granted
            val allRuntimePermissionsGranted = requiredPermissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }

            // Check special permissions
            val usageStatsGranted = checkUsageStatsPermission()
            val overlayGranted = checkOverlayPermission()
            val accessibilityGranted = checkAccessibilityPermission()

            // If all permissions are granted, finish the activity
            if (allRuntimePermissionsGranted && usageStatsGranted && overlayGranted && accessibilityGranted) {
                Log.d("ChildPermissionActivity", "All permissions granted, finishing activity")
                finish()
            } else {
                Log.d("ChildPermissionActivity", "Not all permissions granted yet")
            }
        } catch (e: Exception) {
            Log.e("ChildPermissionActivity", "Error checking permissions: ${e.message}")
        }
    }

    private fun checkUsageStatsPermission(): Boolean {
        try {
            val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.e("ChildPermissionActivity", "Error checking usage stats: ${e.message}")
            return false
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return try {
            Settings.canDrawOverlays(this)
        } catch (e: Exception) {
            Log.e("ChildPermissionActivity", "Error checking overlay permission: ${e.message}")
            false
        }
    }

    private fun checkAccessibilityPermission(): Boolean {
        try {
            val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
        } catch (e: Exception) {
            Log.e("ChildPermissionActivity", "Error checking accessibility permission: ${e.message}")
            return false
        }
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            startActivity(this)
        }
    }

    fun openUsageAccessSettings() {
        currentPermissionStep = 1
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            startActivity(this)
        }
    }

    fun openOverlaySettings() {
        currentPermissionStep = 2
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
            startActivity(this)
        }
    }

    fun openAccessibilitySettings() {
        currentPermissionStep = 3
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            startActivity(this)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            when (currentPermissionStep) {
                0 -> {
                    checkLocationPermissionAndProceed()
                    // Update composable state
                    setContent {
                        ISMAPCTheme {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                ChildPermissionScreen(this)
                            }
                        }
                    }
                }
                1 -> {
                    checkUsageStatsAndProceed()
                    // Update composable state
                    setContent {
                        ISMAPCTheme {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                ChildPermissionScreen(this)
                            }
                        }
                    }
                }
                2 -> {
                    checkOverlayAndProceed()
                    // Update composable state
                    setContent {
                        ISMAPCTheme {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                ChildPermissionScreen(this)
                            }
                        }
                    }
                }
                3 -> {
                    checkAccessibilityAndProceed()
                    // Update composable state
                    setContent {
                        ISMAPCTheme {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                ChildPermissionScreen(this)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChildPermissionActivity", "Error in onResume: ${e.message}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildPermissionScreen(activity: ChildPermissionActivity) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(0) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var hasUsagePermission by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var hasAccessibilityPermission by remember { mutableStateOf(false) }

    // Update current step when activity's step changes
    LaunchedEffect(activity.currentPermissionStep) {
        currentStep = activity.currentPermissionStep
    }

    // Check permissions when the screen is first loaded and when returning from settings
    LaunchedEffect(Unit) {
        try {
            // Check location permissions
            hasLocationPermission = activity.requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

            // Check usage stats permission
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
            hasUsagePermission = mode == AppOpsManager.MODE_ALLOWED

            // Check overlay permission
            hasOverlayPermission = Settings.canDrawOverlays(context)

            // Check accessibility permission
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            hasAccessibilityPermission = enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
        } catch (e: Exception) {
            Log.e("ChildPermissionScreen", "Error checking permissions: ${e.message}")
        }
    }

    // Add a LaunchedEffect to check permissions when returning from settings
    LaunchedEffect(currentStep) {
        try {
            when (currentStep) {
                0 -> {
                    hasLocationPermission = activity.requiredPermissions.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                }
                1 -> {
                    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                    val mode = appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.packageName
                    )
                    hasUsagePermission = mode == AppOpsManager.MODE_ALLOWED
                }
                2 -> {
                    hasOverlayPermission = Settings.canDrawOverlays(context)
                }
                3 -> {
                    val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                    val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    hasAccessibilityPermission = enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
                }
            }
        } catch (e: Exception) {
            Log.e("ChildPermissionScreen", "Error checking permissions in step $currentStep: ${e.message}")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress indicator
        LinearProgressIndicator(
            progress = (currentStep + 1) / 4f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            color = MaterialTheme.colorScheme.primary
        )

        // Header
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Required Permissions",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Please grant the following permissions to enable all features",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Permission Steps
        when (currentStep) {
            0 -> LocationPermissionStep(
                isGranted = hasLocationPermission,
                onGrant = {
                    activity.requestPermissions()
                }
            )
            1 -> UsageAccessStep(
                isGranted = hasUsagePermission,
                onGrant = {
                    activity.openUsageAccessSettings()
                }
            )
            2 -> OverlayPermissionStep(
                isGranted = hasOverlayPermission,
                onGrant = {
                    activity.openOverlaySettings()
                }
            )
            3 -> AccessibilityPermissionStep(
                isGranted = hasAccessibilityPermission,
                onGrant = {
                    activity.openAccessibilitySettings()
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Navigation Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentStep > 0) {
                Button(
                    onClick = { currentStep-- },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Previous")
                }
            } else {
                Spacer(modifier = Modifier.width(80.dp))
            }

            Button(
                onClick = { currentStep++ },
                enabled = when (currentStep) {
                    0 -> hasLocationPermission
                    1 -> hasUsagePermission
                    2 -> hasOverlayPermission
                    3 -> hasAccessibilityPermission
                    else -> false
                }
            ) {
                Text(if (currentStep == 3) "Finish" else "Next")
            }
        }
    }
}

@Composable
fun LocationPermissionStep(
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    PermissionStep(
        title = "Location Access",
        description = "This permission is required to track your location for safety purposes. Your parents can see where you are to ensure your safety.",
        icon = Icons.Default.LocationOn,
        isGranted = isGranted,
        onGrant = onGrant
    )
}

@Composable
fun UsageAccessStep(
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    PermissionStep(
        title = "Usage Access",
        description = "This permission helps monitor your app usage and screen time. It allows your parents to see how much time you spend on different apps.",
        icon = Icons.Outlined.Info,
        isGranted = isGranted,
        onGrant = onGrant
    )
}

@Composable
fun OverlayPermissionStep(
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    PermissionStep(
        title = "Display Over Other Apps",
        description = "This permission is needed to show important notifications and app lock screens. It helps ensure you're using apps safely.",
        icon = Icons.Outlined.Settings,
        isGranted = isGranted,
        onGrant = onGrant
    )
}

@Composable
fun AccessibilityPermissionStep(
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    PermissionStep(
        title = "Accessibility Service",
        description = "This permission helps monitor browser content to ensure safe browsing. It helps protect you from inappropriate content.",
        icon = Icons.Outlined.Lock,
        isGranted = isGranted,
        onGrant = onGrant
    )
}

@Composable
fun PermissionStep(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (isGranted) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = if (isGranted) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = if (isGranted) 
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onGrant,
                enabled = !isGranted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(if (isGranted) "Granted" else "Grant Permission")
            }
        }
    }
} 