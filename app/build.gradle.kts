plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.dialtone"
    // Material 3 Expressive (1.5 alpha) requires compileSdk 37 and AGP 9.1+.
    compileSdk = 37

    defaultConfig {
        applicationId = "app.dialtone"
        minSdk = 29 // RoleManager.ROLE_DIALER
        targetSdk = 36
        // CI passes the run number / tag so every build installs over the previous one.
        versionCode = System.getenv("DIALTONE_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("DIALTONE_VERSION_NAME") ?: "0.1.0"
    }

    // Release signing comes from the environment (GitHub secrets in CI). Every build CI ships
    // is an optimized release build signed with this key, so each one installs as an update.
    val keystorePath = System.getenv("DIALTONE_KEYSTORE_FILE")
    if (keystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("DIALTONE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DIALTONE_KEY_ALIAS")
                keyPassword = System.getenv("DIALTONE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: shrink, optimize and obfuscate; drop unused resources.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
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
            excludes += setOf(
                "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES",
                "META-INF/{AL2.0,LGPL2.1}",
            )
        }
    }
}

// AGP 9 compiles Kotlin itself ("built-in Kotlin"); JVM target follows compileOptions.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Visual voicemail engine
    implementation(libs.apache.mime4j.core)
    implementation(libs.apache.mime4j.dom)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
