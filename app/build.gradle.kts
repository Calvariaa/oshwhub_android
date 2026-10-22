plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// ── 版本号：tag 驱动（语义化）+ 日期式 versionCode ────────────────────────────
// versionName（tag 形如 v1.4.0 / v2.0.0-rc.1）:
//   HEAD 正好在 tag 上   → 1.4.0
//   tag 之后有 N 个提交  → 1.4.1-dev.N+g<sha7>   （自动抬 patch，表示“下一个版本”）
//   仓库还没有任何 tag   → 0.0.0-dev.N+g<sha7>
// versionCode = HEAD 提交日期 yyyyMMdd（用提交自带的时区，跨机器可复现；
//   同一天发两次需要显式覆盖，见下）。当前日期在 2100000000 以内，不会撞 AGP 上限。
// 覆盖（CI / 本地都可用）:
//   -Poshwhub.versionName=1.4.0 -Poshwhub.versionCode=20261022
fun gitOut(vararg args: String): String? = try {
    val exec = providers.exec {
        commandLine(listOf("git", "-C", rootDir.absolutePath) + args.toList())
        isIgnoreExitValue = true
    }
    val text = exec.standardOutput.asText.get().trim()
    if (exec.result.get().exitValue == 0 && text.isNotEmpty()) text else null
} catch (_: Throwable) {
    null // 不是 git 工作区（源码包 / 导出目录）时静默降级
}

val autoVersionCode: Int = run {
    val stamp = gitOut("log", "-1", "--format=%cd", "--date=short") ?: ""
    stamp.replace("-", "").toIntOrNull()?.takeIf { it > 0 } ?: 1
}

val autoVersionName: String = run {
    val describe = gitOut("describe", "--tags", "--long", "--match", "v[0-9]*")
    val m = describe?.let {
        Regex("""^(?<tag>.+)-(?<ahead>\d+)-g(?<sha>[0-9a-f]{7,})$""").find(it)
    }
    if (m == null) {
        val n = gitOut("rev-list", "--count", "HEAD") ?: "0"
        val sha = gitOut("rev-parse", "--short=7", "HEAD") ?: "nogit"
        "0.0.0-dev.$n+g$sha"
    } else {
        val tag = m.groups["tag"]!!.value.removePrefix("v")
        val ahead = m.groups["ahead"]!!.value.toIntOrNull() ?: 0
        val sha = m.groups["sha"]!!.value
        if (ahead == 0) {
            tag
        } else {
            val next = Regex("""^(\d+)\.(\d+)\.(\d+)$""").find(tag)?.let { base ->
                val (major, minor, patch) = base.destructured
                "$major.$minor.${patch.toInt() + 1}"
            } ?: tag
            "$next-dev.$ahead+g$sha"
        }
    }
}

val appVersionName: String =
    providers.gradleProperty("oshwhub.versionName").getOrElse(autoVersionName)
val appVersionCode: Int =
    providers.gradleProperty("oshwhub.versionCode").getOrElse(autoVersionCode.toString())
        .toIntOrNull() ?: autoVersionCode

android {
    namespace = "io.github.Calvariaa.oshwhub"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.Calvariaa.oshwhub"
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowsize)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.bouncycastle)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
