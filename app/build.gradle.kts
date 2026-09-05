plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    kotlin("plugin.serialization") version "2.4.0"
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.craftworks.music"
    compileSdk = 37

    androidResources {
        generateLocaleConfig = true
    }

    defaultConfig {
        applicationId = "com.craftworks.music"
        minSdk = 23
        targetSdk = 37
        versionCode = 311
        versionName = "1.31.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("keystore/chora.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            } else {
                initWith(getByName("debug"))
            }
        }
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
            isDebuggable = false
            isProfileable = true
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "edition"

    fun getDedicatedSourceFor(flavorName: String): String {
        val specific = project.findProperty("source_${flavorName}") as? String
        if (!specific.isNullOrBlank()) return specific.uppercase()

        val general = project.findProperty("dedicatedSource") as? String
        if (!general.isNullOrBlank()) return general.uppercase()

        return when (flavorName) {
            "sonora" -> "LOCAL"
            "lyra" -> "NAVIDROME"
            "aria" -> "EMBY"
            else -> "ALL"
        }
    }

    productFlavors {
        create("chora") {
            dimension = "edition"
            isDefault = true
            manifestPlaceholders["appLabel"] = "Chora"
            val source = getDedicatedSourceFor("chora")
            buildConfigField("String", "DEDICATED_SOURCE", "\"$source\"")
            buildConfigField("String", "APP_FLAVOR_NAME", "\"Chora\"")
        }
        create("sonora") {
            dimension = "edition"
            applicationIdSuffix = ".sonora"
            versionNameSuffix = "-sonora"
            manifestPlaceholders["appLabel"] = "Sonora"
            val source = getDedicatedSourceFor("sonora")
            buildConfigField("String", "DEDICATED_SOURCE", "\"$source\"")
            buildConfigField("String", "APP_FLAVOR_NAME", "\"Sonora\"")
        }
        create("lyra") {
            dimension = "edition"
            applicationIdSuffix = ".lyra"
            versionNameSuffix = "-lyra"
            manifestPlaceholders["appLabel"] = "Lyra"
            val source = getDedicatedSourceFor("lyra")
            buildConfigField("String", "DEDICATED_SOURCE", "\"$source\"")
            buildConfigField("String", "APP_FLAVOR_NAME", "\"Lyra\"")
        }
        create("aria") {
            dimension = "edition"
            applicationIdSuffix = ".aria"
            versionNameSuffix = "-aria"
            manifestPlaceholders["appLabel"] = "Aria"
            val source = getDedicatedSourceFor("aria")
            buildConfigField("String", "DEDICATED_SOURCE", "\"$source\"")
            buildConfigField("String", "APP_FLAVOR_NAME", "\"Aria\"")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts.add("META-INF/NOTICE.md")
            pickFirsts.add("META-INF/LICENSE.md")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += listOf(
            "MissingTranslation",
            "VectorRaster",
            "VectorPath",
            "ConfigurationScreenWidthHeight",
            "AcceptsUserCertificates",
            "InsecureBaseConfiguration",
            "ExportedService"
        )
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.reorderable)
    implementation(libs.androidx.media)

    implementation(libs.androidx.material.icons.core)

    implementation(libs.konsume.xml)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.org.snakeyaml)

    implementation(libs.coil.compose)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.mediarouter)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.composefadingedges)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.content.negotiation)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}