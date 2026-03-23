import java.util.Properties
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        load(localPropsFile.inputStream())
    }
}

android {
    namespace = "com.rallytrax.app"
    compileSdk = 35

    signingConfigs {
        create("release") {
            // CI: keystore decoded from base64 secret into a temp file
            // Local: keystore file lives at app/release.jks
            val ciKeystorePath = System.getenv("KEYSTORE_FILE")
            val keystoreFile = if (!ciKeystorePath.isNullOrBlank()) {
                file(ciKeystorePath)
            } else {
                file("release.jks")
            }

            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: localProperties.getProperty("KEYSTORE_PASSWORD", "rallytrax2024")
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: localProperties.getProperty("KEY_ALIAS", "rallytrax")
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: localProperties.getProperty("KEY_PASSWORD", "rallytrax2024")
            }
        }
    }

    defaultConfig {
        applicationId = "com.rallytrax.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 37
        versionName = "1.2.14"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Maps API key: local.properties → env var → empty fallback
        val mapsApiKey = localProperties.getProperty("MAPS_API_KEY")
            ?: System.getenv("MAPS_API_KEY")
            ?: ""
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")

        // Google Sign-In Web Client ID: local.properties → env var → google-services.json
        val webClientId = localProperties.getProperty("WEB_CLIENT_ID")
            ?: System.getenv("WEB_CLIENT_ID")
            ?: run {
                val gsFile = file("google-services.json")
                if (gsFile.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val json = JsonSlurper().parseText(gsFile.readText()) as Map<String, Any>
                    val clients = json["client"] as? List<Map<String, Any>> ?: emptyList()
                    clients.firstNotNullOfOrNull { client ->
                        val oauthClients = client["oauth_client"] as? List<Map<String, Any>> ?: emptyList()
                        oauthClients.firstOrNull { (it["client_type"] as? Number)?.toInt() == 3 }
                            ?.get("client_id") as? String
                    }
                } else null
            }
            ?: ""
        buildConfigField("String", "WEB_CLIENT_ID", "\"$webClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
            )
        }
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    lint {
        // NonNullableMutableLiveDataDetector crashes with Kotlin 2.1+ analysis APIs
        // (IncompatibleClassChangeError). Safe to disable — project uses StateFlow, not LiveData.
        disable += "NullSafeMutableLiveData"
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")

    // Google Sign-In (Credential Manager)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    // Google Drive API
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    implementation(libs.google.http.client.gson)

    // Image loading (avatars)
    implementation(libs.coil.compose)

    // ML Kit Barcode Scanning (VIN scanner)
    implementation(libs.mlkit.barcode.scanning)

    // CameraX (VIN scanner preview)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Serialization (for type-safe navigation)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Google Maps
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)

    // OpenStreetMap (fallback when Google Maps API key is not configured)
    implementation(libs.osmdroid.android)

    // Location
    implementation(libs.play.services.location)

    // Lifecycle Service
    implementation(libs.androidx.lifecycle.service)

    // DataStore Preferences
    implementation(libs.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
