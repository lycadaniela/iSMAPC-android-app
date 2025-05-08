package com.example.ismapc

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

class DeviceLockScreenActivity : ComponentActivity() {
    private val TAG = "DeviceLockScreenActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Activity onCreate")
        
        // Keep screen on and show over lock screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        
        setContent {
            ISMAPCTheme {
                DeviceLockScreen(
                    onUnlocked = { finish() }
                )
            }
        }
    }

    override fun onBackPressed() {
        // Prevent back button from closing the activity
        Log.d(TAG, "Back button pressed - ignoring")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Activity onPause")
        // Don't restart the activity on pause
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity onDestroy")
        // Don't restart the activity on destroy
    }
}

@Composable
fun DeviceLockScreen(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    var isChecking by remember { mutableStateOf(false) }

    // Check lock state periodically
    LaunchedEffect(Unit) {
        while (true) {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                firestore.collection("deviceLocks")
                    .document(currentUser.uid)
                    .get()
                    .addOnSuccessListener { document ->
                        if (!document.exists() || !(document.getBoolean("isLocked") ?: false)) {
                            // If device is unlocked, notify parent composable
                            onUnlocked()
                        }
                    }
            }
            delay(1000) // Check every second
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Device Locked",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Device is Locked",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This device has been locked by a parent.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    isChecking = true
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        firestore.collection("deviceLocks")
                            .document(currentUser.uid)
                            .get()
                            .addOnSuccessListener { document ->
                                isChecking = false
                                if (!document.exists() || !(document.getBoolean("isLocked") ?: false)) {
                                    onUnlocked()
                                }
                            }
                            .addOnFailureListener {
                                isChecking = false
                            }
                    }
                },
                enabled = !isChecking
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Check Lock Status")
                }
            }
        }
    }
} 