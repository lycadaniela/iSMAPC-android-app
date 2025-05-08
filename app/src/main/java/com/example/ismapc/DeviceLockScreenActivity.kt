package com.example.ismapc

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.view.View
import android.view.Window
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
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
    private val handler = Handler(Looper.getMainLooper())
    private val checkForegroundRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing) {
                checkAndBringToFront()
                handler.postDelayed(this, 50) // Check every 50ms
            }
        }
    }

    private fun checkAndBringToFront() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val tasks = am.getRunningTasks(1)
        if (tasks.isNotEmpty() && tasks[0].topActivity?.packageName != packageName) {
            // If our app is not in foreground, bring it back
            val intent = Intent(this, DeviceLockScreenActivity::class.java)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Activity onCreate")
        
        // Set window flags to prevent system UI access and app minimization
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_SECURE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        // Hide system UI elements
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LOW_PROFILE
        )

        // Set window type to TYPE_APPLICATION_OVERLAY with highest priority
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            window.attributes = window.attributes.apply {
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                flags = flags or WindowManager.LayoutParams.FLAG_SECURE
                flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                flags = flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                flags = flags or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                flags = flags or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                flags = flags or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                flags = flags or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
            }
        } else {
            window.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
        }

        // Set window to fullscreen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        setContent {
            ISMAPCTheme {
                DeviceLockScreen(
                    onUnlocked = {
                        // Clean up and close the app
                        handler.removeCallbacks(checkForegroundRunnable)
                        finish()
                        moveTaskToBack(true)
                    }
                )
            }
        }

        // Start checking if app is in foreground
        handler.post(checkForegroundRunnable)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-hide system UI elements when window gains focus
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LOW_PROFILE
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-apply window flags and system UI visibility
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_SECURE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LOW_PROFILE
        )
    }

    override fun onBackPressed() {
        // Prevent back button from closing the activity
        Log.d(TAG, "Back button pressed - ignoring")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Activity onPause")
        // Only bring activity back to front if we're not finishing
        if (!isFinishing) {
            val intent = Intent(this, DeviceLockScreenActivity::class.java)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity onDestroy")
        handler.removeCallbacks(checkForegroundRunnable)
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