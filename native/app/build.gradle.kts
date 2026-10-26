plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.isledeer.soullink"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.isledeer.soullink"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0-native"
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
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

// ------------------------------------------------------------------
// React dist 同步：把仓库根的 ../dist 复制进 assets（WebView 内核）。
// - 每次 preBuild 自动执行，./gradlew assembleDebug 无需手动步骤
// - **dist 不存在时直接失败**：静默跳过会把 1878 字节的占位页打进包，
//   装到手机上就是「内核未同步」——这个坑踩过一次，必须防呆
// - dist 存在时先清空 assets 再复制，避免残留旧哈希文件
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