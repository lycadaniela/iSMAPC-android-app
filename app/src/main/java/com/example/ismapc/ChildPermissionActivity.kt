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

class ChildPermissionActivity : ComponentActivity() {
    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS,
        Manifest.permission.PACKAGE_USAGE_STATS,
        Manifest.permission.SYSTEM_ALERT_WINDOW,
        Manifest.permission.REQUEST_DELETE_PACKAGES,
        Manifest.permission.QUERY_ALL_PACKAGES
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        checkAllPermissionsAndFinish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChildPermissionScreen(
                        onRequestPermissions = { requestPermissions() },
                        onOpenSettings = { 
                            openAppSettings()
                            // Check permissions after returning from settings
                            checkAllPermissionsAndFinish()
                        },
                        onOpenUsageAccess = { 
                            openUsageAccessSettings()
                            // Check permissions after returning from settings
                            checkAllPermissionsAndFinish()
                        },
                        onOpenOverlaySettings = { 
                            openOverlaySettings()
                            // Check permissions after returning from settings
                            checkAllPermissionsAndFinish()
                        }
                    )
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest)
        } else {
            checkAllPermissionsAndFinish()
        }
    }

    private fun checkAllPermissionsAndFinish() {
        // Check if all runtime permissions are granted
        val allRuntimePermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        // Check special permissions
        val usageStatsGranted = checkUsageStatsPermission()
        val overlayGranted = checkOverlayPermission()

        // If all permissions are granted, finish the activity
        if (allRuntimePermissionsGranted && usageStatsGranted && overlayGranted) {
            finish()
        }
    }

    private fun checkUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            startActivity(this)
        }
    }

    private fun openUsageAccessSettings() {
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            startActivity(this)
        }
    }

    private fun openOverlaySettings() {
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
            startActivity(this)
        }
    }

    override fun onResume() {
        super.onResume()
        // Check permissions when returning from settings
        checkAllPermissionsAndFinish()
    }
}

@Composable
fun ChildPermissionScreen(
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Required Permissions",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        PermissionItem(
            icon = Icons.Default.LocationOn,
            title = "Location Access",
            description = "Required to track your location for safety purposes"
        )

        PermissionItem(
            icon = Icons.Default.Phone,
            title = "Phone State & Call Log",
            description = "Required to monitor call usage and screen time"
        )

        PermissionItem(
            icon = Icons.Default.Email,
            title = "SMS Access",
            description = "Required to monitor message usage"
        )

        PermissionItem(
            icon = Icons.Default.Info,
            title = "Screen Time Tracking",
            description = "Required to monitor and manage your device usage"
        )

        PermissionItem(
            icon = Icons.Default.Lock,
            title = "App Lock Control",
            description = "Required to lock and unlock apps for parental control"
        )

        PermissionItem(
            icon = Icons.Default.Refresh,
            title = "Background Operation",
            description = "Required to keep the app running in background for continuous monitoring"
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Grant Permissions")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Grant Permissions"
            )
        }

        TextButton(
            onClick = onOpenUsageAccess,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Open Usage Access Settings")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Open Usage Access Settings"
            )
        }

        TextButton(
            onClick = onOpenOverlaySettings,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Open Overlay Settings")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Open Overlay Settings"
            )
        }

        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Open App Settings")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Open App Settings"
            )
        }
    }
}

@Composable
fun PermissionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
} 