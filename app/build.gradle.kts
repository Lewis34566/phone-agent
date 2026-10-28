// app 模块构建脚本
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)        // Compose 编译器插件（Kotlin 2.0 起独立）
    alias(libs.plugins.kotlin.serialization)  // JSON 序列化（LLM 工具调用解析用）
    alias(libs.plugins.ksp)                   // Room 注解处理器
}

android {
    namespace = "com.lewis.phoneagent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lewis.phoneagent"
        minSdk = 26          // Android 8.0，覆盖约 98% 设备
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false   // MVP 阶段不开混淆，方便排查问题
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
}

dependencies {
    // AndroidX 基础
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    // Compose（BOM 统一版本）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // 导航（聊天页 / 设置页）
    implementation(libs.androidx.navigation.compose)
    // Room（历史记录 + 用户自定义规则）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // 协程
    implementation(libs.kotlinx.coroutines.android)
    // 网络（阶段④：SSE 流式调用大模型）
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.serialization.json)
    // 本地 OCR（拍图识字，离线识别不调云）
    implementation(libs.mlkit.text.chinese)

    // 本地单元测试（意图匹配规则、格式化等纯逻辑，不依赖设备）
    testImplementation(libs.junit)
}
