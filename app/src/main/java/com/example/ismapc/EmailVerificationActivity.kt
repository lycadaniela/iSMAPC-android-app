package com.example.ismapc

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ismapc.ui.theme.ISMAPCTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

class EmailVerificationActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var userEmail: String = ""
    private var userPassword: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        
        // Get email and password from intent
        userEmail = intent.getStringExtra("email") ?: ""
        userPassword = intent.getStringExtra("password") ?: ""

        setContent {
            ISMAPCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EmailVerificationScreen(
                        email = userEmail,
                        password = userPassword,
                        onVerificationComplete = {
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        },
                        onBackToLogin = {
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        },
                        onError = { message ->
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailVerificationScreen(
    email: String,
    password: String,
    onVerificationComplete: () -> Unit,
    onBackToLogin: () -> Unit,
    onError: (String) -> Unit
) {
    var remainingSeconds by remember { mutableStateOf(60) }
    var canResend by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isCheckingEnabled by remember { mutableStateOf(false) }
    var retryAttempts by remember { mutableStateOf(0) }
    val auth = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()

    // Start countdown timer
    LaunchedEffect(Unit) {
        object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = (millisUntilFinished / 1000).toInt()
            }
            override fun onFinish() {
                canResend = true
                remainingSeconds = 0
            }
        }.start()
    }

    // Handle verification check with retries
    LaunchedEffect(isCheckingEnabled) {
        if (isCheckingEnabled) {
            try {
                // Check if user is already signed in
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    // Just reload and check verification
                    currentUser.reload().addOnCompleteListener { reloadTask ->
                        if (reloadTask.isSuccessful) {
                            if (currentUser.isEmailVerified) {
                                isLoading = true
                                // First, check if this is a child account by querying Firestore
                                FirebaseFirestore.getInstance()
                                    .collection("users")
                                    .document("child")
                                    .collection(currentUser.uid)
                                    .document("profile")
                                    .get()
                                    .addOnSuccessListener { childDoc ->
                                        val userType = if (childDoc.exists()) "child" else "parents"
                                        val userRef = FirebaseFirestore.getInstance()
                                            .collection("users")
                                            .document(userType)
                                            .collection(currentUser.uid)
                                            .document("profile")

                                        // First check if document exists
                                        userRef.get()
                                            .addOnSuccessListener { document ->
                                                if (document.exists()) {
                                                    // Document exists, update it
                                                    FirebaseFirestore.getInstance()
                                                        .collection("users")
                                                        .document(userType)
                                                        .collection(currentUser.uid)
                                                        .document("profile")
                                                        .update("isEmailVerified", true)
                                                        .addOnSuccessListener {
                                                            onVerificationComplete()
                                                        }
                                                        .addOnFailureListener { e ->
                                                            handleFirebaseError(e, onError)
                                                            isCheckingEnabled = false
                                                            isLoading = false
                                                        }
                                                } else {
                                                    // Document doesn't exist, create it
                                                    val userData = hashMapOf(
                                                        "email" to currentUser.email,
                                                        "displayName" to currentUser.displayName,
                                                        "isEmailVerified" to true,
                                                        "createdAt" to com.google.firebase.Timestamp.now(),
                                                        "userType" to userType
                                                    )
                                                    FirebaseFirestore.getInstance()
                                                        .collection("users")
                                                        .document(userType)
                                                        .collection(currentUser.uid)
                                                        .document("profile")
                                                        .set(userData)
                                                        .addOnSuccessListener {
                                                            onVerificationComplete()
                                                        }
                                                        .addOnFailureListener { e ->
                                                            handleFirebaseError(e, onError)
                                                            isCheckingEnabled = false
                                                            isLoading = false
                                                        }
                                                }
                                            }
                                            .addOnFailureListener { e ->
                                                handleFirebaseError(e, onError)
                                                isCheckingEnabled = false
                                                isLoading = false
                                            }
                                    }
                                    .addOnFailureListener { e ->
                                        handleFirebaseError(e, onError)
                                        isCheckingEnabled = false
                                        isLoading = false
                                    }
                            } else {
                                onError("Email is not verified yet. Please check your email and click the verification link.")
                                isCheckingEnabled = false
                                isLoading = false
                            }
                        } else {
                            handleFirebaseError(reloadTask.exception, onError)
                            isCheckingEnabled = false
                            isLoading = false
                        }
                    }
                } else {
                    onError("Session expired. Please go back to login and try again.")
                    isCheckingEnabled = false
                    isLoading = false
                }
            } catch (e: Exception) {
                handleFirebaseError(e, onError)
                isCheckingEnabled = false
                isLoading = false
            }
            delay(5000) // Delay between checks
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Verify Your Email",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "We've sent a verification email to:",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "1. Open your email\n2. Click the verification link\n3. Come back here and click the button below",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { 
                        scope.launch {
                            isLoading = true
                            isCheckingEnabled = true 
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading
                ) {
                    Text("I've verified my email")
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!canResend) {
                    Text(
                        text = "Resend email in $remainingSeconds seconds",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val currentUser = auth.currentUser
                                if (currentUser != null) {
                                    currentUser.sendEmailVerification()
                                        .addOnSuccessListener {
                                            onError("Verification email sent!")
                                            canResend = false
                                            remainingSeconds = 60
                                            object : CountDownTimer(60000, 1000) {
                                                override fun onTick(millisUntilFinished: Long) {
                                                    remainingSeconds = (millisUntilFinished / 1000).toInt()
                                                }
                                                override fun onFinish() {
                                                    canResend = true
                                                    remainingSeconds = 0
                                                }
                                            }.start()
                                        }
                                        .addOnFailureListener { e ->
                                            handleFirebaseError(e, onError)
                                        }
                                } else {
                                    onError("Session expired. Please go back to login and try again.")
                                }
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Text("Resend verification email")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedButton(
                    onClick = {
                        auth.signOut()
                        onBackToLogin()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Back to Login")
                }
            }
        }
    }
}

private fun handleFirebaseError(error: Throwable?, onError: (String) -> Unit) {
    val errorMessage = when {
        error?.message?.contains("blocked all requests") == true ->
            "Your device is temporarily blocked. Please close the app, wait 5-10 minutes, and try again."
        error?.message?.contains("Too many unsuccessful login attempts") == true ->
            "Too many attempts. Please wait 5-10 minutes and try again."
        error?.message?.contains("permission-denied") == true -> 
            "Session expired. Please go back to login and try again."
        error?.message?.contains("PERMISSION_DENIED") == true -> 
            "Session expired. Please go back to login and try again."
        error?.message?.contains("supplied auth credential is invalid") == true ->
            "Session expired. Please go back to login and try again."
        else -> "Error: ${error?.message}"
    }
    onError(errorMessage)
}