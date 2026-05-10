package com.anhnn.components.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anhnn.feedback.FeedbackScreen
import com.anhnn.privacy.PrivacyPolicyScreen
import com.anhnn.rate.RateDialog
import com.anhnn.rate.requestInAppReview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var screen by remember { mutableStateOf("home") }
                    var showRateDialog by remember { mutableStateOf(false) }

                    when (screen) {
                        "privacy" -> PrivacyPolicyScreen(
                            url = "https://www.google.com/policies/privacy/",
                            onBack = { screen = "home" }
                        )
                        "feedback" -> FeedbackScreen(
                            email = "support@example.com",
                            subject = "Demo App Feedback",
                            onBack = { screen = "home" }
                        )
                        else -> Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("anhnn-components Demo", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(32.dp))
                            Button(onClick = { screen = "privacy" }) { Text("Privacy Policy") }
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { screen = "feedback" }) { Text("Feedback") }
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = {
                                requestInAppReview(this@MainActivity) { showRateDialog = true }
                            }) { Text("Rate App") }
                        }
                    }

                    if (showRateDialog) {
                        RateDialog(
                            packageName = packageName,
                            onDismiss = { showRateDialog = false }
                        )
                    }
                }
            }
        }
    }
}
