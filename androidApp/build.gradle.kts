import groovy.json.JsonSlurper
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

/**
 * Firebase, for push notifications (ADR 0023 in the web repository), set up from
 * google-services.json: the resources Google's plugin would write, written here
 * instead (Firebase's "without the plugin" setup), so the build does not wait on
 * the plugin keeping up with the Android Gradle plugin. The file is not in git;
 * CI writes it from the GOOGLE_SERVICES_JSON secret. Without it there is no push.
 */
val firebase: Map<String, String> = file("google-services.json").takeIf { it.exists() }?.let { json ->
    @Suppress("UNCHECKED_CAST")
    val root = JsonSlurper().parse(json) as Map<String, Any?>
    val project = root["project_info"] as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    val client = (root["client"] as List<Map<String, Any?>>).first {
        val info = it["client_info"] as Map<String, Any?>
        (info["android_client_info"] as Map<String, Any?>)["package_name"] == "ng.heynaija.app"
    }
    @Suppress("UNCHECKED_CAST")
    val apiKey = (client["api_key"] as List<Map<String, Any?>>).first()["current_key"] as String
    mapOf(
        "google_app_id" to (client["client_info"] as Map<String, Any?>)["mobilesdk_app_id"] as String,
        "gcm_defaultSenderId" to project["project_number"] as String,
        "project_id" to project["project_id"] as String,
        "google_api_key" to apiKey,
        "google_storage_bucket" to (project["storage_bucket"] as String? ?: ""),
    )
} ?: emptyMap()

/** The version people see in the stores, the same as the iOS app's: one place to change it. */
val appVersion: String = rootProject.file("iosApp/Configuration/Config.xcconfig").readLines()
    .firstNotNullOfOrNull { Regex("""^MARKETING_VERSION\s*=\s*(\S+)""").find(it)?.groupValues?.get(1) }
    ?: error("MARKETING_VERSION is missing from iosApp/Configuration/Config.xcconfig")

/**
 * The upload key a release is signed with for Google Play, which then signs it
 * again with the app's own key (Play App Signing; docs/release.md). None of it is
 * in git: CI writes the keystore from the UPLOAD_KEYSTORE secret and passes the
 * rest in the environment; on a computer they can go in ~/.gradle/gradle.properties
 * as heynaija.upload.* instead. Without them a release build is unsigned.
 */
fun upload(name: String): String? =
    System.getenv("HEYNAIJA_UPLOAD_${name.uppercase()}")?.takeIf { it.isNotBlank() }
        ?: (findProperty("heynaija.upload.$name") as String?)?.takeIf { it.isNotBlank() }
val uploadKeystore = upload("keystore")?.let(::file)?.takeIf { it.exists() }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "ng.heynaija.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ng.heynaija.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Each upload to Play needs a higher versionCode: CI passes its run number (-PversionCode).
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = appVersion
        firebase.forEach { (name, value) -> resValue("string", name, value) }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (uploadKeystore != null) {
            create("upload") {
                storeFile = uploadKeystore
                storePassword = upload("store_password")
                keyAlias = upload("key_alias")
                keyPassword = upload("key_password")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        resValues = true
    }
}