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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import android.util.Log
import com.anhnn.ads.AdFormat
import com.anhnn.ads.Ads
import com.anhnn.ads.AdsConfig
import com.anhnn.ads.BannerAd
import com.anhnn.ads.NativeAd
import com.anhnn.analytics.Analytics
import com.anhnn.analytics.AnalyticsConfig
import com.anhnn.analytics.AnalyticsSink
import com.anhnn.feedback.FeedbackScreen
import com.anhnn.privacy.PrivacyPolicyScreen
import com.anhnn.rate.RateDialog
import com.anhnn.rate.requestInAppReview

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Cấu hình ads demo: toàn bộ dùng test unit của Google ---
        Ads.init(
            AdsConfig(
                adsEnabled = { true },
                adUnitId = { name ->
                    when (DemoAds.formatOf(name)) {
                        AdFormat.INTERSTITIAL -> "ca-app-pub-3940256099942544/1033173712"
                        AdFormat.NATIVE -> "ca-app-pub-3940256099942544/2247696110"
                        AdFormat.BANNER -> "ca-app-pub-3940256099942544/6300978111"
                        null -> ""
                    }
                },
                adFormat = { name -> DemoAds.formatOf(name) },
            )
        )
        // Consent + init SDK xong thì preload sẵn để demo hiện tức thì.
        Ads.start(this) {
            Ads.preload(this, DemoAds.INTER, DemoAds.NATIVE)
        }

        // Analytics demo: app demo không có google-services.json nên tắt Firebase, dùng sink Logcat.
        Analytics.init(
            this,
            AnalyticsConfig(
                firebaseEnabled = false,
                extraSinks = listOf(AnalyticsSink { name, params ->
                    Log.d("AnalyticsDemo", "$name $params")
                }),
            )
        )

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
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("anhnn-components Demo", style = MaterialTheme.typography.headlineSmall)
                            Button(onClick = { screen = "privacy" }) { Text("Privacy Policy") }
                            Button(onClick = { screen = "feedback" }) { Text("Feedback") }
                            Button(onClick = {
                                requestInAppReview(this@MainActivity) { showRateDialog = true }
                            }) { Text("Rate App") }

                            Spacer(Modifier.height(16.dp))
                            Text("Analytics", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = {
                                Analytics.logEvent("demo_button_click", mapOf("source" to "home"))
                            }) { Text("Log Event (xem Logcat)") }

                            Spacer(Modifier.height(16.dp))
                            Text("Ads", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = {
                                Ads.showInterstitial(this@MainActivity, DemoAds.INTER) { /* tiếp tục */ }
                            }) { Text("Show Interstitial") }

                            // Native ad đã preload -> hiện ngay.
                            NativeAd(adName = DemoAds.NATIVE)
                            BannerAd(adName = DemoAds.BANNER)
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

/** Khai báo các vị trí ad của demo + định dạng từng vị trí (app tự quản, module không cần biết). */
private object DemoAds {
    const val INTER = "demo_inter"
    const val NATIVE = "demo_native"
    const val BANNER = "demo_banner"

    fun formatOf(name: String): AdFormat? = when (name) {
        INTER -> AdFormat.INTERSTITIAL
        NATIVE -> AdFormat.NATIVE
        BANNER -> AdFormat.BANNER
        else -> null
    }
}
