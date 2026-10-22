plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.dialtone.phone"
    // Material 3 Expressive (1.5 alpha) requires compileSdk 37 and AGP 9.1+.
    compileSdk = 37

    defaultConfig {
        applicationId = "app.dialtone.phone"
        minSdk = 29 // RoleManager.ROLE_DIALER
        targetSdk = 36
        // CI passes the run number / tag so every build installs over the previous one.
        versionCode = System.getenv("DIALTONE_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("DIALTONE_VERSION_NAME") ?: "0.1.0"

        // On-device voicemail transcription (transcribe.cpp + Moonshine) ships for 64-bit ARM,
        // which covers every phone GrapheneOS supports. Elsewhere the switch is hidden.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                // ggml is unusably slow without optimizations, even in debug builds.
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    androidResources {
        // The speech model is memory-mapped after extraction; store it uncompressed so
        // copying it out of the APK is a straight read (it doesn't compress anyway).
        noCompress += listOf("gguf")
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

// --- Moonshine speech model ---------------------------------------------------
// The 35 MB model is downloaded at build time (Hugging Face, pinned by size and SHA-256)
// and bundled in the APK's assets, so the app never fetches anything at runtime.
val moonshineModel = mapOf(
    "url" to "https://huggingface.co/handy-computer/moonshine-tiny-gguf/resolve/main/moonshine-tiny-Q8_0.gguf",
    "file" to "moonshine-tiny-Q8_0.gguf",
    "size" to "35466912",
    // Pinned after the first verified download; empty = report the hash instead of checking it.
    "sha256" to "",
)

abstract class DownloadModelTask : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val fileName: Property<String>
    @get:Input abstract val expectedSize: Property<Long>
    @get:Input abstract val expectedSha256: Property<String>
    @get:Internal abstract val cacheDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val cached = cacheDir.get().file(fileName.get()).asFile
        if (!cached.isFile || !verify(cached, quiet = true)) {
            cached.parentFile.mkdirs()
            logger.lifecycle("Downloading ${url.get()}")
            val client = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build()
            val request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url.get())).GET().build()
            val tmp = File(cached.path + ".part")
            val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofFile(tmp.toPath()))
            if (response.statusCode() != 200) throw GradleException("Model download failed: HTTP ${response.statusCode()}")
            if (!verify(tmp, quiet = false)) throw GradleException("Downloaded model failed verification")
            tmp.renameTo(cached)
        }
        val dest = outputDir.get().dir("models").file(fileName.get()).asFile
        dest.parentFile.mkdirs()
        cached.copyTo(dest, overwrite = true)
    }

    private fun verify(file: File, quiet: Boolean): Boolean {
        if (file.length() != expectedSize.get()) {
            if (!quiet) logger.error("Model size ${file.length()} != expected ${expectedSize.get()}")
            return false
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        val expected = expectedSha256.get()
        if (expected.isEmpty()) {
            logger.lifecycle("MOONSHINE_MODEL_SHA256=$actual (not pinned yet)")
            return true
        }
        if (!actual.equals(expected, ignoreCase = true)) {
            if (!quiet) logger.error("Model SHA-256 $actual != pinned $expected")
            return false
        }
        return true
    }
}

val downloadMoonshineModel = tasks.register<DownloadModelTask>("downloadMoonshineModel") {
    url.set(moonshineModel.getValue("url"))
    fileName.set(moonshineModel.getValue("file"))
    expectedSize.set(moonshineModel.getValue("size").toLong())
    expectedSha256.set(moonshineModel.getValue("sha256"))
    cacheDir.set(File(gradle.gradleUserHomeDir, "caches/dialtone-models"))
    outputDir.set(layout.buildDirectory.dir("generated/moonshine"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(downloadMoonshineModel, DownloadModelTask::outputDir)
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
