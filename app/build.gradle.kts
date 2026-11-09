import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The upload key's details live in keystore.properties, which is git-ignored and never
// committed. Without it (a fresh clone, CI without secrets) release builds fall back to
// the debug key so they still build — but such a build can't be uploaded to Play.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}
val hasUploadKey = keystorePropertiesFile.exists()

// AdMob IDs. They aren't secret: they ship inside every copy of the app.
// Debug builds always use Google's test IDs. Never tap your own live ads: AdMob bans accounts for it.
val admobAppId = "ca-app-pub-8476254864158495~5173450877"        // AdMob › Apps › App settings › App ID
val admobBannerUnitId = "ca-app-pub-8476254864158495/1888811982" // AdMob › Apps › Ad units › "Notes list banner"
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerUnitId = "ca-app-pub-3940256099942544/9214589741"

android {
    namespace = "com.holymanzion.simplenotepro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.holymanzion.simplenotepro"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Installs beside the release app, so tests never touch real notes.
            applicationIdSuffix = ".debug"
            manifestPlaceholders["admobAppId"] = testAdmobAppId
            buildConfigField("String", "BANNER_AD_UNIT_ID", "\"$testBannerUnitId\"")
        }
        release {
            isMinifyEnabled = true
            // Blank IDs (not set up yet) fall back to test ads; checkAdmobIds stops those reaching Play.
            manifestPlaceholders["admobAppId"] = admobAppId.ifBlank { testAdmobAppId }
            buildConfigField("String", "BANNER_AD_UNIT_ID", "\"${admobBannerUnitId.ifBlank { testBannerUnitId }}\"")
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName(if (hasUploadKey) "upload" else "debug")
            // Native crash reports in Play Console stay readable (Compose ships native code).
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
        }
    }

    // One APK per processor type instead of one carrying all four. The text-in-photos
    // reader is ~11 MB of native code per type, so a universal APK would be ~46 MB; a
    // phone only needs its own (almost always arm64-v8a, ~14 MB total). This only affects
    // APKs you install directly: the Play bundle (.aab) is split by Play automatically.
    splits {
        abi {
            // Play splits the .aab itself, and AGP fails if APK splits are on while bundling
            // (issuetracker.google.com/402800800), so build the bundle and the APKs in separate runs.
            isEnable = gradle.startParameter.taskNames.none { it.contains("bundle", ignoreCase = true) }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * Keeps the published privacy policy identical to the one bundled in the app.
 * `app/src/main/assets` is the single source of truth; `docs/` is what GitHub Pages serves.
 * Running this on every build means the public URL can't drift from what the app shows.
 */
val syncPrivacyPolicy by tasks.registering(Copy::class) {
    from(layout.projectDirectory.dir("src/main/assets")) { include("privacy_policy.html") }
    into(rootProject.layout.projectDirectory.dir("docs"))
}
tasks.named("preBuild") { dependsOn(syncPrivacyPolicy) }

// The migration test reads the exported schemas to build old database versions.
android.sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.zip4j)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// A Play upload with Google's test ad IDs would show only test ads and earn nothing.
val checkAdmobIds by tasks.registering {
    doLast {
        check(admobAppId.startsWith("ca-app-pub-") && "~" in admobAppId && admobBannerUnitId.startsWith("ca-app-pub-") && "/" in admobBannerUnitId) {
            "Set admobAppId and admobBannerUnitId at the top of app/build.gradle.kts before building a release."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkAdmobIds) }
