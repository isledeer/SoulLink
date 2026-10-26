plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.isledeer.soullink"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.isledeer.soullink"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0-native"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    // ---------- Compose ----------
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---------- Lifecycle / 导航（对齐 RikkaHub：Navigation Compose） ----------
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // ---------- DI：Koin ----------
    implementation("io.insert-koin:koin-android:4.0.0")
    implementation("io.insert-koin:koin-androidx-compose:4.0.0")

    // ---------- 设置存储：DataStore ----------
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ---------- 网络：OkHttp（SSE 已在 NativeSSE.kt） ----------
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0") // 文档解析（PDF 文本提取）

    // ---------- 协程 ----------
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ---------- Room（消息落盘） ----------
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ------------------------------------------------------------------
    // 液态玻璃：两条路都要升工具链，暂不启用（详见 ui/Glass.kt 注释）
    //
    // A. Cloudy（有 Modifier.liquidGlass）：
    //    implementation("com.github.skydoves:cloudy:1.0.0-alpha01")
    //    要求 Kotlin ≥2.4.0 / Compose ≥1.11.4 / minSdk 31（RenderEffect）
    //
    // B. Kyant0 backdrop（只有 drawBackdrop/layerBackdrop）：
    //    implementation("io.github.kyant0:backdrop:2.0.1")
    //    要求 Kotlin ≥2.4.10 / Compose ≥1.12.0，底层 RuntimeShader → API 33+
    // ------------------------------------------------------------------
}

// ------------------------------------------------------------------
// React dist 同步：把仓库根的 ../dist 复制进 assets（WebView 内核）。
// - 每次 preBuild 自动执行
// - **dist 不存在时直接失败**：静默跳过会把占位页打进包（踩过一次）
// ------------------------------------------------------------------
val webDistDir = rootProject.file("../dist")

val syncDist by tasks.registering(Copy::class) {
    group = "soullink"
    description = "把 ../dist 复制进 assets（React 内核）"
    from(webDistDir)
    into(layout.projectDirectory.dir("src/main/assets"))
    doFirst {
        if (!webDistDir.isDirectory) {
            throw GradleException(
                "找不到 ${webDistDir.absolutePath}。\n" +
                    "请先在仓库根目录执行 pnpm build 生成 dist，再打包原生壳。"
            )
        }
        delete(layout.projectDirectory.dir("src/main/assets"))
    }
}

tasks.named("preBuild") {
    dependsOn(syncDist)
}