import java.io.File

plugins {
    id("com.android.application")
}

android {
    namespace = "com.gongkao.checkin"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.gongkao.checkin"
        minSdk = 26
        targetSdk = 36
        /*
         * 版本号只有一个来源：versionName。versionCode 一律由它推导，
         * 公式与 CI（.github/workflows/build.yml）里的完全一致：
         *   major*10000 + minor*100 + patch
         * 否则本地 1.1.0 算出 2、CI 的 1.1.0 算出 10100，
         * 更新弹层就会出现「发现新版本 v1.1.0 / 当前 v1.1.0」这种自相矛盾的提示。
         */
        val vName = System.getenv("VERSION_NAME") ?: "1.1.0"
        val parts = vName.split('.').map { it.toIntOrNull() ?: 0 }
        versionName = vName
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull()
            ?: (parts.getOrElse(0) { 0 } * 10000 +
                parts.getOrElse(1) { 0 } * 100 +
                parts.getOrElse(2) { 0 })
    }

    /*
     * 正式签名。keystore 不进仓库：
     *   - CI 上由 workflow 解 base64 落到 KEYSTORE_PATH
     *   - 本地从 F:\Claude\gongkao-release.jks 读
     * 两边都找不到时回落到 debug 签名，保证 clone 下来就能构建。
     *
     * 注意 debug keystore 每台机器都不一样，所以**必须**用固定的 release key，
     * 否则 CI 产物和本地产物签名不一致，装不上更新。
     */
    val keystoreFile = (System.getenv("KEYSTORE_PATH")
        ?: rootProject.file("../gongkao-release.jks").absolutePath).let(::File)
    val hasKeystore = keystoreFile.exists()

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "gongkao2026"
                keyAlias = System.getenv("KEY_ALIAS") ?: "gongkao"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "gongkao2026"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (hasKeystore) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("找不到 release keystore，回落到 debug 签名（此产物无法用于线上更新）")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    // ViewPager1 而非 2：页面视图要常驻（计时器不能被回收重建），
    // PagerAdapter 允许直接返回已有的 Page.view 实例，ViewPager2 的 RecyclerView 回收做不到。
    implementation("androidx.viewpager:viewpager:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
