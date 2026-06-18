plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")
}

android {
    namespace = "com.anhnn.analytics"
    compileSdk = 36

    defaultConfig { minSdk = 24 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures { compose = true }
    publishing { singleVariant("release") { withSourcesJar() } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.runtime:runtime")
    // NavController + OnDestinationChangedListener cho helper TrackScreenViews.
    implementation("androidx.navigation:navigation-compose:2.8.9")

    // Firebase Analytics (sink mặc định). api để app dùng được hằng số nếu cần.
    api(platform("com.google.firebase:firebase-bom:33.7.0"))
    api("com.google.firebase:firebase-analytics")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId    = "com.github.anhngocnguyen1034"
                artifactId = "anhnn-components-analytics"
                version    = "unspecified"
            }
        }
    }
}
