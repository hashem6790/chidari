import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

/**
 * نام نسخه: اگر متغیر محیطی APP_VERSION_NAME تنظیم باشد (توسط GitHub Actions
 * از روی تگ)، همان استفاده می‌شود؛ وگرنه مقدار پیش‌فرض برای ساخت محلی.
 */
val appVersionName: String = System.getenv("APP_VERSION_NAME")?.trim()?.removePrefix("v")
    ?.takeIf { it.isNotBlank() } ?: "3.0"

/**
 * کد نسخه به‌صورت عددی از روی نام نسخه ساخته می‌شود:
 *   2.0   → 20000
 *   2.1.3 → 20103
 * همیشه صعودی است، پس اندروید نصب روی نسخه قبلی را می‌پذیرد.
 */
val appVersionCode: Int = appVersionName
    .split(".", "-")
    .mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
    .let { p ->
        (p.getOrElse(0) { 0 } * 10000) + (p.getOrElse(1) { 0 } * 100) + p.getOrElse(2) { 0 }
    }
    .coerceAtLeast(11)   // هرگز پایین‌تر از آخرین نسخه منتشرشده نرود

android {
    namespace = "ir.chidari"
    compileSdk = 34

    /**
     * کلیدهای Supabase از فایل secrets.properties خوانده می‌شوند که در گیت نیست.
     * برای ساخت پروژه، فایل secrets.properties.example را کپی و مقادیر خود را وارد کنید.
     * اگر فایل نباشد، برنامه در «حالت مهمان» ساخته می‌شود (بدون ثبت‌نام).
     */
    val secrets = Properties().apply {
        val f = rootProject.file("secrets.properties")
        if (f.exists()) load(FileInputStream(f))
    }

    /**
     * اطلاعات کلید امضای نسخه انتشار.
     *
     * امضای ثابت حیاتی است: اندروید فقط نسخه‌ای را روی نسخه قبلی نصب می‌کند
     * که با **همان کلید** امضا شده باشد. اگر کلید عوض شود، کاربر مجبور است
     * برنامه را حذف و از نو نصب کند (و داده‌هایش پاک می‌شود).
     *
     * مقادیر از keystore.properties (محلی) یا متغیرهای محیطی (CI) خوانده می‌شوند.
     */
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) load(FileInputStream(f))
    }
    fun signingValue(key: String, env: String): String? =
        keystoreProps.getProperty(key) ?: System.getenv(env)

    defaultConfig {
        applicationId = "ir.chidari"
        minSdk = 24
        targetSdk = 34
        // ---------- نسخه ----------
        // در CI، نسخه از خودِ تگ گیت خوانده می‌شود (مثلاً v2.1 → 2.1).
        // این کار جلوی ناهماهنگی نام تگ با نسخه داخل برنامه را می‌گیرد؛
        // ناهماهنگی باعث می‌شد به‌روزرسان همیشه فکر کند نسخه تازه‌ای هست.
        versionName = appVersionName
        versionCode = appVersionCode

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField(
            "String", "SUPABASE_URL",
            "\"" + (secrets.getProperty("SUPABASE_URL") ?: "") + "\""
        )
        buildConfigField(
            "String", "SUPABASE_ANON_KEY",
            "\"" + (secrets.getProperty("SUPABASE_ANON_KEY") ?: "") + "\""
        )
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("storeFile", "KEYSTORE_FILE")
            if (storePath != null && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // فقط اگر کلید موجود باشد امضا می‌شود؛ وگرنه build بدون امضا ادامه می‌یابد
            val ks = signingValue("storeFile", "KEYSTORE_FILE")
            if (ks != null && file(ks).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room (پایگاه داده محلی)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore برای تنظیمات کاربر (استان/شهر/حالت فروشنده)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // موقعیت مکانی گوگل (GPS)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // اسکن بارکد محصولات (دوربین + تشخیص روی دستگاه)
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    // تشخیص بارکد به‌صورت bundled: مدل داخل خود APK است.
    // حجم بیشتر می‌شود ولی روی گوشی‌های بدون Google Play هم کار می‌کند
    // و اولین اسکن بدون انتظار انجام می‌شود.
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.compose.ui:ui-viewbinding")

    // شبکه (احراز هویت Supabase)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // خواندن چرخش EXIF عکس‌های دوربین
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // بارگذاری تصاویر
    implementation("io.coil-kt:coil-compose:2.6.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
