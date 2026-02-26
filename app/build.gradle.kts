import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Files
import java.nio.file.Paths

// --------------------------------------------------------------------------
// Helper to read plain‑text credential files from app/keystore
// --------------------------------------------------------------------------
fun readCredential(fileName: String): String? {
    val path = Paths.get(rootProject.projectDir.path, "app", "keystore", fileName)
    return if (Files.isRegularFile(path)) {
        Files.readString(path).trim()
    } else null
}

/* -------------------------------------------------------------------------- */
/*  Plugins                                                                  */
/* -------------------------------------------------------------------------- */
plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

/* -------------------------------------------------------------------------- */
/*  Android configuration                                                     */
/* -------------------------------------------------------------------------- */
android {
    compileSdk = 36 // Revert back to 36

    defaultConfig {
        applicationId = project.property("APP_ID").toString()
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = 36 // Revert back to 36

        val versionBase = project.findProperty("VERSION_BASE")?.toString() ?: "0.9"
        val buildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 0
        versionName = "$versionBase.$buildNumber"
        versionCode = if (buildNumber > 0) buildNumber else 1
        setProperty("archivesBaseName", "launcher-$versionCode")

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        // OpenWeatherMap API key (set project property 'openWeatherApiKey' in gradle.properties for local testing)
        val openWeatherApiKeyValue = project.findProperty("openWeatherApiKey") ?: ""
        buildConfigField("String", "OPENWEATHER_API_KEY", "\"$openWeatherApiKeyValue\"")
    }

    /* --------------------------- signingConfigs --------------------------- */
    signingConfigs {
        create("release") {
            // 1️⃣ Prefer CI environment variables (used by GitHub Actions)
            val storeFileEnv = System.getenv("SIGNING_STORE_FILE")
            if (storeFileEnv != null) {
                storeFile = file(storeFileEnv)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            } else {
                // 2️⃣ Fallback to local text‑file credentials
                storeFile = file("keystore/myrelease.jks")
                storePassword = readCredential("storePassword.txt")
                ?: throw GradleException("Missing storePassword.txt in app/keystore")
                keyAlias = readCredential("keyAlias.txt")
                ?: throw GradleException("Missing keyAlias.txt in app/keystore")
                keyPassword = readCredential("keyPassword.txt")
                ?: throw GradleException("Missing keyPassword.txt in app/keystore")
            }

            enableV1Signing = true
            enableV2Signing = true
        }
    }
    /* -------------------------------------------------------------------- */

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                          "proguard-rules.pro"
            )
            // Always apply the signing config; it will be populated either from CI env vars
            // or from the local text‑file fallback defined above.
            signingConfig = signingConfigs.getByName("release")
        }
    }

    flavorDimensions.add("variants")
    productFlavors {
        register("foss")
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
    }

    compileOptions {
        val javaVer = JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = javaVer
        targetCompatibility = javaVer
    }

    // ----------------------------------------------------------------------
    // Locale generation removed – the Android plugin will no longer try to
    // create a resources.properties file, eliminating the
    // `extractFossReleaseSupportedLocales` failure.
    // ----------------------------------------------------------------------

    tasks.withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(
            JvmTarget.fromTarget(project.libs.versions.app.build.kotlinJVMTarget.get())
        )
    }

    namespace = project.property("APP_ID").toString()

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        lintConfig = rootProject.file("lint.xml")
    }

    // The bundle language split is no longer needed
    bundle {
        language {
            enableSplit = false
        }
    }
}

/* --------------------------- Detekt configuration ------------------------ */
detekt {
    baseline = file("detekt-baseline.xml")
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}

/* ------------------------------- Dependencies ----------------------------- */
dependencies {
    testImplementation(kotlin("test"))

    detektPlugins(libs.compose.detekt)

    implementation("com.google.android.material:material:1.12.0")

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation("androidx.compose.material:material") // For Swipeable
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation(libs.compose.ui.tooling)

    // LiquidGlass backdrop effect
    implementation(libs.backdrop)
    implementation(libs.shapes)

    // Coil for image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // WorkManager for background prewarm
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    // Serialization for data persistence
    implementation(libs.kotlinx.serialization.json)
}
