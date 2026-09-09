import com.android.build.api.variant.FilterConfiguration
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val courseVersionCode = versionProperties.getProperty("VERSION_CODE").toInt()
val courseVersionName = versionProperties.getProperty("VERSION_NAME")
val arm64Only = providers.gradleProperty("courseTableArm64Only").orNull == "true"

android {
    namespace = "com.coursetable.app"
    compileSdk = 37

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProperties.containsKey("storeFile")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("preview") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            versionNameSuffix = "-preview"
            matchingFallbacks += listOf("release")
        }
    }

    defaultConfig {
        applicationId = "com.coursetable.app"
        minSdk = 26
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = courseVersionCode
        versionName = courseVersionName
    }

    splits {
        abi {
            isEnable = true
            reset()
            if (arm64Only) {
                include("arm64-v8a")
            } else {
                include("armeabi-v7a", "arm64-v8a")
            }
            isUniversalApk = !arm64Only
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
                ?: "universal"
            output.outputFileName.set(
                output.versionName.map { versionName ->
                    "CourseTable-v$versionName-$abi-release.apk"
                }
            )
        }
    }
    onVariants(selector().withBuildType("preview")) { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
                ?: "universal"
            output.outputFileName.set(
                output.versionName.map { versionName ->
                    "CourseTable-v$versionName-b$courseVersionCode-$abi-preview.apk"
                }
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.backdrop)
    implementation(libs.kyant.shapes)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.pdfbox.android)
    implementation(libs.mlkit.text.recognition.chinese)

    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
