# Cobalt Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Kotlin Android app wrapping cobalt.tools in a full-screen WebView with share sheet, clipboard trigger, OkHttp download queue, blob chunking, Room history, and WorkManager retry — producing a sideloadable debug APK for Galaxy S22 Ultra (Android 14).

**Architecture:** Single-Activity app. CobaltWebView intercepts downloads via DownloadListener; https:// URLs go to DownloadService via OkHttp→MediaStore; blob: URLs go through a JS bridge with 2MB chunk transfer. Room persists history; WorkManager shows retry notifications on reconnect.

**Tech Stack:** Kotlin 1.9.23 · AGP 8.3.2 · Gradle 8.7 · Room 2.6.1 · WorkManager 2.9.0 · OkHttp 4.12.0 · Material 3 1.12.0 · View Binding · IBM Plex Mono (bundled TTF)

**Project root:** `D:\Projects\CobaltAndroid`
**Package:** `com.cobalt.android`

---

## File Map

```
CobaltAndroid/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew.bat
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/js/cobalt_bridge.js
│       ├── java/com/cobalt/android/
│       │   ├── CobaltApplication.kt
│       │   ├── MainActivity.kt
│       │   ├── CobaltWebView.kt
│       │   ├── CobaltJsBridge.kt
│       │   ├── download/
│       │   │   ├── DownloadRecord.kt        ← Entity + Status enum
│       │   │   ├── DownloadDao.kt
│       │   │   ├── DownloadDatabase.kt
│       │   │   ├── DownloadRepository.kt
│       │   │   ├── MediaStoreWriter.kt
│       │   │   ├── DownloadService.kt
│       │   │   └── RetryDownloadWorker.kt
│       │   └── ui/
│       │       ├── DownloadQueueViewModel.kt
│       │       ├── DownloadAdapter.kt
│       │       ├── DownloadQueueSheet.kt
│       │       └── SettingsSheet.kt
│       │   util/
│       │       ├── UrlMatcher.kt
│       │       ├── ClipboardHelper.kt
│       │       ├── NotificationHelper.kt
│       │       └── SettingsRepository.kt
│       └── res/
│           ├── layout/{activity_main, sheet_download_queue, item_download, sheet_settings}.xml
│           ├── values/{colors, themes, strings, dimens}.xml
│           ├── font/ibm_plex_mono_regular.ttf  ← downloaded in Task 1
│           ├── drawable/ic_queue.xml
│           └── xml/{network_security_config, shortcuts}.xml
└── src/test/java/com/cobalt/android/
    ├── UrlMatcherTest.kt
    ├── DownloadStatusTest.kt
    └── ChunkBufferTest.kt
```

---

## Task 1: Gradle Scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradlew.bat`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/res/font/` (download IBM Plex Mono)

- [ ] **Step 1: Download Gradle 8.7 and generate wrapper**

```powershell
cd D:\Projects\CobaltAndroid
New-Item -ItemType Directory -Force gradle\wrapper | Out-Null

$v = "8.7"
$zip = "$env:TEMP\gradle-$v-bin.zip"
Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$v-bin.zip" -OutFile $zip
Expand-Archive $zip -DestinationPath "$env:TEMP\gradle-extract-$v" -Force
& "$env:TEMP\gradle-extract-$v\gradle-$v\bin\gradle.bat" wrapper --gradle-version $v --distribution-type bin --project-dir D:\Projects\CobaltAndroid
Remove-Item -Recurse -Force "$env:TEMP\gradle-extract-$v"
Remove-Item -Force $zip
```

Expected: `gradle/wrapper/gradle-wrapper.jar` and `gradle/wrapper/gradle-wrapper.properties` created; `gradlew.bat` created in project root.

- [ ] **Step 2: Write settings.gradle.kts**

```kotlin
// D:\Projects\CobaltAndroid\settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "CobaltAndroid"
include(":app")
```

- [ ] **Step 3: Write root build.gradle.kts**

```kotlin
// D:\Projects\CobaltAndroid\build.gradle.kts
plugins {
    id("com.android.application") version "8.3.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    id("com.google.devtools.ksp") version "1.9.23-1.0.20" apply false
}
```

- [ ] **Step 4: Write gradle.properties**

```properties
# D:\Projects\CobaltAndroid\gradle.properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 5: Write app/build.gradle.kts**

```kotlin
// D:\Projects\CobaltAndroid\app\build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.cobalt.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cobalt.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures { viewBinding = true }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.23")
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```

- [ ] **Step 6: Write app/proguard-rules.pro**

```
# app/proguard-rules.pro
-keep class com.cobalt.android.CobaltJsBridge { *; }
-keepclassmembers class com.cobalt.android.CobaltJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
```

- [ ] **Step 7: Download IBM Plex Mono font**

```powershell
New-Item -ItemType Directory -Force "D:\Projects\CobaltAndroid\app\src\main\res\font" | Out-Null
Invoke-WebRequest `
  "https://github.com/IBM/plex/raw/master/IBM-Plex-Mono/fonts/complete/ttf/IBMPlexMono-Regular.ttf" `
  -OutFile "D:\Projects\CobaltAndroid\app\src\main\res\font\ibm_plex_mono_regular.ttf"
Invoke-WebRequest `
  "https://github.com/IBM/plex/raw/master/IBM-Plex-Mono/fonts/complete/ttf/IBMPlexMono-Medium.ttf" `
  -OutFile "D:\Projects\CobaltAndroid\app\src\main\res\font\ibm_plex_mono_medium.ttf"
```

- [ ] **Step 8: Verify Gradle can sync (empty app)**

Create a minimal placeholder so Gradle has something to compile:

```powershell
New-Item -ItemType Directory -Force "D:\Projects\CobaltAndroid\app\src\main\java\com\cobalt\android" | Out-Null
New-Item -ItemType Directory -Force "D:\Projects\CobaltAndroid\app\src\main\res\values" | Out-Null
New-Item -ItemType Directory -Force "D:\Projects\CobaltAndroid\app\src\main\res\layout" | Out-Null
New-Item -ItemType Directory -Force "D:\Projects\CobaltAndroid\app\src\main\assets\js" | Out-Null
New-Item -ItemType Directory -Force "D:\Projects\CobaltAndroid\app\src\main\res\xml" | Out-Null
New-Item -ItemType Directory -Force "D:\Projects\CobaltAndroid\app\src\main\res\drawable" | Out-Null
New-Item -ItemType Directory -Force "D:\Projects\CobaltAndroid\app\src\test\java\com\cobalt\android" | Out-Null
```

Write a minimal `app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Cobalt</string>
</resources>
```

Write a minimal `app/src/main/AndroidManifest.xml` (will be fully replaced in Task 2):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="@string/app_name" />
</manifest>
```

Write a placeholder `app/src/main/java/com/cobalt/android/Placeholder.kt`:

```kotlin
package com.cobalt.android
// placeholder
```

Run:
```powershell
cd D:\Projects\CobaltAndroid
.\gradlew.bat assembleDebug 2>&1 | Select-String -Pattern "BUILD|error:" | Select-Object -First 20
```

Expected output contains: `BUILD SUCCESSFUL`

- [ ] **Step 9: Commit**

```powershell
cd D:\Projects\CobaltAndroid
git add -A
git commit -m "feat: gradle scaffold, font, build verified"
```

---

## Task 2: Manifest + Network Security Config + Resources

**Files:**
- Replace: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/network_security_config.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values/dimens.xml`
- Replace: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/drawable/ic_queue.xml`

- [ ] **Step 1: Write AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <application
        android:name=".CobaltApplication"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:theme="@style/Theme.Cobalt"
        android:networkSecurityConfig="@xml/network_security_config"
        android:allowBackup="false"
        android:supportsRtl="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
            </intent-filter>
            <meta-data
                android:name="android.app.shortcuts"
                android:resource="@xml/shortcuts" />
        </activity>

        <service
            android:name=".download.DownloadService"
            android:foregroundServiceType="dataSync"
            android:exported="false" />

    </application>
</manifest>
```

- [ ] **Step 2: Write network_security_config.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/xml/network_security_config.xml -->
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

- [ ] **Step 3: Write colors.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/values/colors.xml -->
<resources>
    <color name="cobalt_background">#000000</color>
    <color name="cobalt_surface">#191919</color>
    <color name="cobalt_surface_sidebar">#131313</color>
    <color name="cobalt_surface_elevated">#282828</color>
    <color name="cobalt_text_primary">#E1E1E1</color>
    <color name="cobalt_text_secondary">#818181</color>
    <color name="cobalt_accent_blue">#2A7CE1</color>
    <color name="cobalt_success_green">#37AA42</color>
    <color name="cobalt_error_red">#ED2236</color>
    <color name="cobalt_input_border">#383838</color>
    <color name="cobalt_stroke">#0D000000</color>
</resources>
```

- [ ] **Step 4: Write themes.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/values/themes.xml -->
<resources>
    <style name="Theme.Cobalt" parent="Theme.Material3.Dark">
        <item name="colorPrimary">@color/cobalt_accent_blue</item>
        <item name="colorOnPrimary">@color/cobalt_text_primary</item>
        <item name="colorSurface">@color/cobalt_surface</item>
        <item name="colorOnSurface">@color/cobalt_text_primary</item>
        <item name="android:colorBackground">@color/cobalt_background</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLayoutInDisplayCutoutMode">shortEdges</item>
        <item name="bottomSheetDialogTheme">@style/Theme.Cobalt.BottomSheet</item>
    </style>

    <style name="Theme.Cobalt.BottomSheet" parent="Theme.Material3.Dark.BottomSheetDialog">
        <item name="android:colorBackground">@color/cobalt_surface_sidebar</item>
        <item name="colorSurface">@color/cobalt_surface_sidebar</item>
    </style>

    <style name="TextAppearance.Cobalt.Mono" parent="TextAppearance.Material3.BodyMedium">
        <item name="fontFamily">@font/ibm_plex_mono_regular</item>
        <item name="android:fontFamily">@font/ibm_plex_mono_regular</item>
        <item name="android:textColor">@color/cobalt_text_primary</item>
    </style>

    <style name="TextAppearance.Cobalt.Mono.Secondary">
        <item name="android:textColor">@color/cobalt_text_secondary</item>
        <item name="android:textSize">12sp</item>
    </style>
</resources>
```

- [ ] **Step 5: Write strings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Cobalt</string>
    <string name="download_from_clipboard">download from clipboard?</string>
    <string name="not_a_supported_link">that doesn\'t look like a supported link</string>
    <string name="merging_locally">merging locally — may take a moment</string>
    <string name="local_merge_failed">local merge failed</string>
    <string name="not_enough_storage">not enough storage</string>
    <string name="check_cobalt_url">check your cobalt instance URL in settings</string>
    <string name="shortcut_paste_download">Paste &amp; Download</string>
    <string name="shortcut_open_queue">Open Queue</string>
    <string name="tab_active">active</string>
    <string name="tab_history">history</string>
    <string name="action_open">open</string>
    <string name="action_share">share</string>
    <string name="action_retry">retry</string>
    <string name="action_cancel">cancel</string>
    <string name="settings_cobalt_url">cobalt instance</string>
    <string name="settings_audio_only">audio-only mode</string>
    <string name="settings_clipboard_trigger">clipboard trigger</string>
    <string name="settings_battery">battery optimization</string>
    <string name="settings_clear_history">clear history</string>
    <string name="battery_dialog_title">Background Downloads</string>
    <string name="battery_dialog_message">Allow Cobalt to complete downloads when the app is in the background?</string>
    <string name="allow">Allow</string>
    <string name="not_now">Not now</string>
    <string name="channel_downloads_name">Downloads</string>
    <string name="channel_downloads_desc">Download progress and completion</string>
    <string name="retry_notification_title">Ready to retry</string>
</resources>
```

- [ ] **Step 6: Write dimens.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <dimen name="border_radius">11dp</dimen>
    <dimen name="padding_default">12dp</dimen>
    <dimen name="fab_size">42dp</dimen>
    <dimen name="progress_bar_height">2dp</dimen>
</resources>
```

- [ ] **Step 7: Write ic_queue.xml (vector drawable)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/drawable/ic_queue.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@color/cobalt_text_primary">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M4,6H20V8H4ZM4,11H20V13H4ZM4,16H14V18H4Z"/>
</vector>
```

- [ ] **Step 8: Write shortcuts.xml placeholder (full content in Task 14)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/xml/shortcuts.xml -->
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 9: Verify build still succeeds**

```powershell
cd D:\Projects\CobaltAndroid
.\gradlew.bat assembleDebug 2>&1 | Select-String -Pattern "BUILD|error:" | Select-Object -First 20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```powershell
git add -A && git commit -m "feat: manifest, resources, design tokens"
```

---

## Task 3: UrlMatcher + Unit Tests

**Files:**
- Create: `app/src/main/java/com/cobalt/android/util/UrlMatcher.kt`
- Create: `app/src/test/java/com/cobalt/android/UrlMatcherTest.kt`

- [ ] **Step 1: Write UrlMatcher.kt**

```kotlin
// app/src/main/java/com/cobalt/android/util/UrlMatcher.kt
package com.cobalt.android.util

object UrlMatcher {
    private val SUPPORTED_HOSTS = setOf(
        "youtube.com", "www.youtube.com", "youtu.be", "m.youtube.com",
        "tiktok.com", "www.tiktok.com", "vm.tiktok.com",
        "twitter.com", "www.twitter.com", "x.com", "www.x.com",
        "instagram.com", "www.instagram.com",
        "reddit.com", "www.reddit.com", "old.reddit.com", "redd.it",
        "soundcloud.com", "www.soundcloud.com",
        "vimeo.com", "www.vimeo.com",
        "twitch.tv", "www.twitch.tv", "clips.twitch.tv",
        "dailymotion.com", "www.dailymotion.com",
        "bilibili.com", "www.bilibili.com",
        "pinterest.com", "www.pinterest.com",
        "tumblr.com"
    )

    fun isSupportedUrl(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return false
        return try {
            val host = android.net.Uri.parse(trimmed).host?.lowercase() ?: return false
            SUPPORTED_HOSTS.any { host == it || host.endsWith(".$it") }
        } catch (e: Exception) {
            false
        }
    }

    fun extractUrl(text: String?): String? {
        if (!isSupportedUrl(text)) return null
        return text?.trim()
    }
}
```

- [ ] **Step 2: Write UrlMatcherTest.kt**

```kotlin
// app/src/test/java/com/cobalt/android/UrlMatcherTest.kt
package com.cobalt.android

import com.cobalt.android.util.UrlMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlMatcherTest {

    @Test fun youtubeFullUrl() =
        assertTrue(UrlMatcher.isSupportedUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))

    @Test fun youtubeShortUrl() =
        assertTrue(UrlMatcher.isSupportedUrl("https://youtu.be/dQw4w9WgXcQ"))

    @Test fun tiktokUrl() =
        assertTrue(UrlMatcher.isSupportedUrl("https://www.tiktok.com/@user/video/123456789"))

    @Test fun twitterUrl() =
        assertTrue(UrlMatcher.isSupportedUrl("https://twitter.com/user/status/123"))

    @Test fun xUrl() =
        assertTrue(UrlMatcher.isSupportedUrl("https://x.com/user/status/123"))

    @Test fun instagramUrl() =
        assertTrue(UrlMatcher.isSupportedUrl("https://www.instagram.com/p/ABC123/"))

    @Test fun redditUrl() =
        assertTrue(UrlMatcher.isSupportedUrl("https://www.reddit.com/r/videos/comments/abc"))

    @Test fun soundcloudUrl() =
        assertTrue(UrlMatcher.isSupportedUrl("https://soundcloud.com/artist/track"))

    @Test fun vimeoUrl() =
        assertTrue(UrlMatcher.isSupportedUrl("https://vimeo.com/123456789"))

    @Test fun randomText() =
        assertFalse(UrlMatcher.isSupportedUrl("hello world"))

    @Test fun mailtoLink() =
        assertFalse(UrlMatcher.isSupportedUrl("mailto:user@example.com"))

    @Test fun unsupportedSite() =
        assertFalse(UrlMatcher.isSupportedUrl("https://google.com/search?q=test"))

    @Test fun nullInput() =
        assertFalse(UrlMatcher.isSupportedUrl(null))

    @Test fun emptyInput() =
        assertFalse(UrlMatcher.isSupportedUrl(""))

    @Test fun bareHostNoScheme() =
        assertFalse(UrlMatcher.isSupportedUrl("youtube.com/watch?v=abc"))
}
```

- [ ] **Step 3: Run tests**

```powershell
cd D:\Projects\CobaltAndroid
.\gradlew.bat :app:testDebugUnitTest --tests "com.cobalt.android.UrlMatcherTest" 2>&1 | Select-String -Pattern "BUILD|PASSED|FAILED|tests"
```

Expected: `BUILD SUCCESSFUL`, `15 tests completed`

- [ ] **Step 4: Commit**

```powershell
git add -A && git commit -m "feat: UrlMatcher with unit tests"
```

---

## Task 4: Room Database

**Files:**
- Create: `app/src/main/java/com/cobalt/android/download/DownloadRecord.kt`
- Create: `app/src/main/java/com/cobalt/android/download/DownloadDao.kt`
- Create: `app/src/main/java/com/cobalt/android/download/DownloadDatabase.kt`
- Create: `app/src/test/java/com/cobalt/android/DownloadStatusTest.kt`

- [ ] **Step 1: Write DownloadRecord.kt**

```kotlin
// app/src/main/java/com/cobalt/android/download/DownloadRecord.kt
package com.cobalt.android.download

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

enum class DownloadStatus { QUEUED, DOWNLOADING, COMPLETE, FAILED, FAILED_NETWORK }

class StatusConverters {
    @TypeConverter fun fromStatus(s: DownloadStatus): String = s.name
    @TypeConverter fun toStatus(s: String): DownloadStatus = DownloadStatus.valueOf(s)
}

@Entity(tableName = "downloads")
@TypeConverters(StatusConverters::class)
data class DownloadRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalUrl: String = "",
    val cobaltUrl: String = "",
    val filename: String = "",
    val mimeType: String = "application/octet-stream",
    val cookies: String = "",
    val userAgent: String = "",
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val timestamp: Long = System.currentTimeMillis(),
    val isBlobDownload: Boolean = false,
    val tempFilePath: String = "",
    val retryCount: Int = 0
)
```

- [ ] **Step 2: Write DownloadDao.kt**

```kotlin
// app/src/main/java/com/cobalt/android/download/DownloadDao.kt
package com.cobalt.android.download

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DownloadRecord): Long

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus)

    @Query("UPDATE downloads SET bytesDownloaded = :bytes, totalBytes = :total, status = :status WHERE id = :id")
    suspend fun updateProgress(id: Long, bytes: Long, total: Long, status: DownloadStatus)

    @Query("UPDATE downloads SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllLive(): LiveData<List<DownloadRecord>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','DOWNLOADING','FAILED_NETWORK') ORDER BY timestamp DESC")
    fun getActiveLive(): LiveData<List<DownloadRecord>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadRecord?

    @Query("DELETE FROM downloads WHERE status = 'COMPLETE' OR status = 'FAILED'")
    suspend fun clearHistory()
}
```

- [ ] **Step 3: Write DownloadDatabase.kt**

```kotlin
// app/src/main/java/com/cobalt/android/download/DownloadDatabase.kt
package com.cobalt.android.download

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [DownloadRecord::class], version = 1, exportSchema = false)
@TypeConverters(StatusConverters::class)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile private var INSTANCE: DownloadDatabase? = null

        fun getInstance(context: Context): DownloadDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "cobalt_downloads.db"
                ).build().also { INSTANCE = it }
            }
    }
}
```

- [ ] **Step 4: Write DownloadStatusTest.kt**

```kotlin
// app/src/test/java/com/cobalt/android/DownloadStatusTest.kt
package com.cobalt.android

import com.cobalt.android.download.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadStatusTest {

    @Test fun defaultStatusIsQueued() {
        val record = com.cobalt.android.download.DownloadRecord()
        assertEquals(DownloadStatus.QUEUED, record.status)
    }

    @Test fun statusEnumRoundTrip() {
        val converters = com.cobalt.android.download.StatusConverters()
        DownloadStatus.values().forEach { status ->
            assertEquals(status, converters.toStatus(converters.fromStatus(status)))
        }
    }

    @Test fun allStatusesPresent() {
        assertEquals(5, DownloadStatus.values().size)
    }
}
```

- [ ] **Step 5: Run tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.cobalt.android.DownloadStatusTest" 2>&1 | Select-String "BUILD|PASSED|FAILED"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```powershell
git add -A && git commit -m "feat: Room database, DownloadRecord, DAO"
```

---

## Task 5: Utilities (SettingsRepository, ClipboardHelper, NotificationHelper)

**Files:**
- Create: `app/src/main/java/com/cobalt/android/util/SettingsRepository.kt`
- Create: `app/src/main/java/com/cobalt/android/util/ClipboardHelper.kt`
- Create: `app/src/main/java/com/cobalt/android/util/NotificationHelper.kt`

- [ ] **Step 1: Write SettingsRepository.kt**

```kotlin
// app/src/main/java/com/cobalt/android/util/SettingsRepository.kt
package com.cobalt.android.util

import android.content.Context

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("cobalt_settings", Context.MODE_PRIVATE)

    var cobaltInstanceUrl: String
        get() = prefs.getString("cobalt_url", "https://cobalt.tools") ?: "https://cobalt.tools"
        set(v) = prefs.edit().putString("cobalt_url", v).apply()

    var audioOnlyMode: Boolean
        get() = prefs.getBoolean("audio_only", false)
        set(v) = prefs.edit().putBoolean("audio_only", v).apply()

    var clipboardTriggerEnabled: Boolean
        get() = prefs.getBoolean("clipboard_trigger", true)
        set(v) = prefs.edit().putBoolean("clipboard_trigger", v).apply()

    var firstLaunchDone: Boolean
        get() = prefs.getBoolean("first_launch_done", false)
        set(v) = prefs.edit().putBoolean("first_launch_done", v).apply()
}
```

- [ ] **Step 2: Write ClipboardHelper.kt**

```kotlin
// app/src/main/java/com/cobalt/android/util/ClipboardHelper.kt
package com.cobalt.android.util

import android.content.ClipboardManager
import android.content.Context

object ClipboardHelper {
    fun getSupportedUrl(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        return UrlMatcher.extractUrl(text)
    }
}
```

- [ ] **Step 3: Write NotificationHelper.kt**

```kotlin
// app/src/main/java/com/cobalt/android/util/NotificationHelper.kt
package com.cobalt.android.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.cobalt.android.MainActivity
import com.cobalt.android.R

class NotificationHelper(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "cobalt_downloads"
        const val FOREGROUND_ID = 1
        private const val BASE_ID = 1000
    }

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_downloads_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_downloads_desc)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Downloading…")
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun updateProgress(recordId: Long, bytes: Int, total: Int) {
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading…")
            .setProgress(total.coerceAtLeast(1), bytes, total <= 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        manager.notify((BASE_ID + recordId).toInt(), notif)
    }

    fun showComplete(recordId: Long, filename: String, uri: Uri) {
        val openIntent = PendingIntent.getActivity(
            context, recordId.toInt(),
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(filename)
            .setContentText("Saved to Downloads/Cobalt")
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.action_open), openIntent)
            .build()
        manager.notify((BASE_ID + recordId).toInt(), notif)
    }

    fun showFailed(recordId: Long, filename: String) {
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(filename)
            .setContentText("Download failed")
            .setAutoCancel(true)
            .build()
        manager.notify((BASE_ID + recordId).toInt(), notif)
    }

    fun showStorageFull() {
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.not_enough_storage))
            .setOngoing(true)
            .build()
        manager.notify(BASE_ID - 1, notif)
    }

    fun showRetryReady(recordId: Long, originalUrl: String, filename: String) {
        val tapIntent = PendingIntent.getActivity(
            context, recordId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, originalUrl)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.retry_notification_title))
            .setContentText(filename.ifBlank { originalUrl })
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()
        manager.notify((BASE_ID + recordId).toInt(), notif)
    }

    fun cancel(recordId: Long) = manager.cancel((BASE_ID + recordId).toInt())
}
```

- [ ] **Step 4: Commit**

```powershell
git add -A && git commit -m "feat: SettingsRepository, ClipboardHelper, NotificationHelper"
```

---

## Task 6: JS Bridge + cobalt_bridge.js

**Files:**
- Create: `app/src/main/assets/js/cobalt_bridge.js`
- Create: `app/src/main/java/com/cobalt/android/CobaltJsBridge.kt`

- [ ] **Step 1: Write cobalt_bridge.js**

```javascript
// app/src/main/assets/js/cobalt_bridge.js
(function () {
    'use strict';

    // 1. Intercept fetch to capture the URL being submitted to cobalt's API
    var _origFetch = window.fetch.bind(window);
    window.fetch = function (resource, options) {
        try {
            if (options && options.body) {
                var body = JSON.parse(options.body);
                if (body && typeof body.url === 'string' && body.url.length > 0) {
                    window.CobaltBridge && window.CobaltBridge.onUrlSubmitted(body.url);
                }
            }
        } catch (e) { /* ignore parse errors */ }
        return _origFetch(resource, options);
    };

    // 2. Inject a URL into cobalt's input and submit
    window._cobaltInjectUrl = function (url, audioOnly) {
        try {
            // Find cobalt's URL input - Svelte requires the native setter trick
            var input = document.querySelector('input[type="text"]') ||
                        document.querySelector('input[type="url"]') ||
                        document.querySelector('input:not([type])');
            if (!input) throw new Error('input not found');

            var nativeSetter = Object.getOwnPropertyDescriptor(
                window.HTMLInputElement.prototype, 'value'
            ).set;
            nativeSetter.call(input, url);
            input.dispatchEvent(new Event('input', { bubbles: true, cancelable: true }));
            input.dispatchEvent(new Event('change', { bubbles: true, cancelable: true }));

            if (audioOnly) {
                // Try to click the "audio" mode button in cobalt's UI
                try {
                    var buttons = document.querySelectorAll('button');
                    for (var i = 0; i < buttons.length; i++) {
                        if (buttons[i].textContent.trim().toLowerCase() === 'audio') {
                            buttons[i].click();
                            break;
                        }
                    }
                } catch (e2) { /* non-fatal */ }
            }

            // Submit after a short delay to allow Svelte state to update
            setTimeout(function () {
                try {
                    var submitBtn = document.querySelector('button[type="submit"]') ||
                        document.querySelector('form button') ||
                        document.querySelector('.download-button');
                    if (submitBtn) submitBtn.click();
                } catch (e3) { /* non-fatal */ }
            }, 150);

            window.CobaltBridge && window.CobaltBridge.onInjectComplete('true');
        } catch (e) {
            window.CobaltBridge && window.CobaltBridge.onInjectComplete('false');
        }
    };

    // 3. Blob download: read as ArrayBuffer, send to native in 1MB chunks
    window._cobaltDownloadBlob = function (blobUrl, filename, mimeType) {
        fetch(blobUrl)
            .then(function (r) { return r.arrayBuffer(); })
            .then(function (buffer) {
                var bytes = new Uint8Array(buffer);
                var CHUNK = 1024 * 1024; // 1MB
                var total = bytes.length;

                window.CobaltBridge.onBlobStart(filename, mimeType, total);

                var offset = 0;
                function sendChunk() {
                    if (offset >= total) {
                        window.CobaltBridge.onBlobComplete();
                        return;
                    }
                    var end = Math.min(offset + CHUNK, total);
                    var slice = bytes.subarray(offset, end);
                    var binary = '';
                    for (var i = 0; i < slice.length; i++) {
                        binary += String.fromCharCode(slice[i]);
                    }
                    window.CobaltBridge.onBlobChunk(btoa(binary));
                    offset = end;
                    setTimeout(sendChunk, 0); // yield to browser between chunks
                }
                sendChunk();
            })
            .catch(function (e) {
                window.CobaltBridge.onBlobError(e.message || 'blob read failed');
            });
    };
})();
```

- [ ] **Step 2: Write CobaltJsBridge.kt**

```kotlin
// app/src/main/java/com/cobalt/android/CobaltJsBridge.kt
package com.cobalt.android

import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import java.io.File
import java.io.FileOutputStream

class CobaltJsBridge(
    private val cacheDir: File,
    private val listener: Listener
) {
    interface Listener {
        fun onUrlSubmitted(originalUrl: String)
        fun onInjectComplete(success: Boolean)
        fun onBlobReady(tempFile: File, filename: String, mimeType: String)
        fun onBlobError(message: String)
    }

    private var blobFilename = ""
    private var blobMimeType = ""
    private var blobTempFile: File? = null
    private var blobOutputStream: FileOutputStream? = null

    @JavascriptInterface
    fun onUrlSubmitted(url: String) {
        listener.onUrlSubmitted(url)
    }

    @JavascriptInterface
    fun onInjectComplete(successStr: String) {
        listener.onInjectComplete(successStr == "true")
    }

    @JavascriptInterface
    fun onBlobStart(filename: String, mimeType: String, totalSize: Int) {
        blobFilename = filename.ifBlank { "cobalt_download" }
        blobMimeType = mimeType
        blobTempFile?.delete()
        blobTempFile = File(cacheDir, "cobalt_blob_${System.currentTimeMillis()}.tmp")
        blobOutputStream = FileOutputStream(blobTempFile)
    }

    @JavascriptInterface
    fun onBlobChunk(base64: String) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            blobOutputStream?.write(bytes)
        } catch (e: Exception) {
            Log.e("CobaltJsBridge", "Chunk write failed", e)
            cleanupBlob()
            listener.onBlobError("chunk write failed: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onBlobComplete() {
        blobOutputStream?.flush()
        blobOutputStream?.close()
        blobOutputStream = null
        val file = blobTempFile ?: run {
            listener.onBlobError("temp file missing")
            return
        }
        listener.onBlobReady(file, blobFilename, blobMimeType)
        blobTempFile = null
    }

    @JavascriptInterface
    fun onBlobError(message: String) {
        cleanupBlob()
        listener.onBlobError(message)
    }

    private fun cleanupBlob() {
        blobOutputStream?.close()
        blobOutputStream = null
        blobTempFile?.delete()
        blobTempFile = null
    }
}
```

- [ ] **Step 3: Commit**

```powershell
git add -A && git commit -m "feat: JS bridge and cobalt_bridge.js"
```

---

## Task 7: DownloadRepository + MediaStoreWriter

**Files:**
- Create: `app/src/main/java/com/cobalt/android/download/DownloadRepository.kt`
- Create: `app/src/main/java/com/cobalt/android/download/MediaStoreWriter.kt`

- [ ] **Step 1: Write DownloadRepository.kt**

```kotlin
// app/src/main/java/com/cobalt/android/download/DownloadRepository.kt
package com.cobalt.android.download

import android.content.Context
import androidx.lifecycle.LiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadRepository(context: Context) {
    private val dao = DownloadDatabase.getInstance(context).downloadDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    val allDownloads: LiveData<List<DownloadRecord>> = dao.getAllLive()
    val activeDownloads: LiveData<List<DownloadRecord>> = dao.getActiveLive()

    suspend fun insert(record: DownloadRecord): Long = dao.insert(record)

    fun updateStatusAsync(id: Long, status: DownloadStatus) =
        scope.launch { dao.updateStatus(id, status) }

    suspend fun updateStatus(id: Long, status: DownloadStatus) =
        dao.updateStatus(id, status)

    suspend fun updateProgress(id: Long, bytes: Long, total: Long) =
        dao.updateProgress(id, bytes, total, DownloadStatus.DOWNLOADING)

    suspend fun incrementRetry(id: Long) = dao.incrementRetry(id)

    suspend fun getById(id: Long): DownloadRecord? = dao.getById(id)

    suspend fun clearHistory() = dao.clearHistory()
}
```

- [ ] **Step 2: Write MediaStoreWriter.kt**

```kotlin
// app/src/main/java/com/cobalt/android/download/MediaStoreWriter.kt
package com.cobalt.android.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream

class MediaStoreWriter(private val context: Context) {

    data class OpenedFile(val uri: Uri, val stream: OutputStream)

    fun open(filename: String, mimeType: String): OpenedFile? {
        val safeFilename = filename.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeFilename)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Cobalt")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
        ) ?: return null
        val stream = context.contentResolver.openOutputStream(uri) ?: run {
            context.contentResolver.delete(uri, null, null)
            return null
        }
        return OpenedFile(uri, stream)
    }

    fun finalize(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    fun delete(uri: Uri) {
        context.contentResolver.delete(uri, null, null)
    }
}
```

- [ ] **Step 3: Commit**

```powershell
git add -A && git commit -m "feat: DownloadRepository and MediaStoreWriter"
```

---

## Task 8: DownloadService + RetryDownloadWorker

**Files:**
- Create: `app/src/main/java/com/cobalt/android/download/DownloadService.kt`
- Create: `app/src/main/java/com/cobalt/android/download/RetryDownloadWorker.kt`

- [ ] **Step 1: Write DownloadService.kt**

```kotlin
// app/src/main/java/com/cobalt/android/download/DownloadService.kt
package com.cobalt.android.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.cobalt.android.util.NotificationHelper
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val activeCount = AtomicInteger(0)
    private lateinit var repository: DownloadRepository
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var mediaStoreWriter: MediaStoreWriter

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        repository = DownloadRepository(this)
        notificationHelper = NotificationHelper(this)
        mediaStoreWriter = MediaStoreWriter(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HTTPS -> {
                val record = DownloadRecord(
                    originalUrl = intent.getStringExtra(EXTRA_ORIGINAL_URL) ?: "",
                    cobaltUrl = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY,
                    filename = intent.getStringExtra(EXTRA_FILENAME) ?: "download",
                    mimeType = intent.getStringExtra(EXTRA_MIME_TYPE) ?: "application/octet-stream",
                    cookies = intent.getStringExtra(EXTRA_COOKIES) ?: "",
                    userAgent = intent.getStringExtra(EXTRA_USER_AGENT) ?: ""
                )
                enqueue { processHttps(it) }
                scope.launch {
                    val id = repository.insert(record)
                    processHttps(record.copy(id = id))
                }
            }
            ACTION_BLOB -> {
                val tempPath = intent.getStringExtra(EXTRA_TEMP_PATH) ?: return START_NOT_STICKY
                val record = DownloadRecord(
                    originalUrl = intent.getStringExtra(EXTRA_ORIGINAL_URL) ?: "",
                    filename = intent.getStringExtra(EXTRA_FILENAME) ?: "download",
                    mimeType = intent.getStringExtra(EXTRA_MIME_TYPE) ?: "application/octet-stream",
                    isBlobDownload = true,
                    tempFilePath = tempPath
                )
                scope.launch {
                    val id = repository.insert(record)
                    processBlob(record.copy(id = id))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun enqueue(block: suspend (DownloadRecord) -> Unit) {
        // No-op: actual launch happens in onStartCommand; this method can be used for tracking
    }

    private suspend fun processHttps(record: DownloadRecord) {
        activeCount.incrementAndGet()
        startForeground()
        try {
            repository.updateStatus(record.id, DownloadStatus.DOWNLOADING)
            val request = Request.Builder()
                .url(record.cobaltUrl)
                .apply { if (record.cookies.isNotBlank()) header("Cookie", record.cookies) }
                .apply { if (record.userAgent.isNotBlank()) header("User-Agent", record.userAgent) }
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty body")
                val contentLength = body.contentLength()
                val opened = mediaStoreWriter.open(record.filename, record.mimeType)
                    ?: throw IOException("MediaStore open failed")

                try {
                    opened.stream.use { out ->
                        val buffer = ByteArray(16 * 1024)
                        var totalRead = 0L
                        var lastUpdate = 0L
                        body.byteStream().use { input ->
                            var n: Int
                            while (input.read(buffer).also { n = it } != -1) {
                                out.write(buffer, 0, n)
                                totalRead += n
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 500) {
                                    lastUpdate = now
                                    repository.updateProgress(record.id, totalRead, contentLength)
                                    notificationHelper.updateProgress(record.id, totalRead.toInt(), contentLength.toInt())
                                }
                            }
                        }
                    }
                    mediaStoreWriter.finalize(opened.uri)
                    repository.updateStatus(record.id, DownloadStatus.COMPLETE)
                    notificationHelper.showComplete(record.id, record.filename, opened.uri)
                } catch (e: Exception) {
                    mediaStoreWriter.delete(opened.uri)
                    throw e
                }
            }
        } catch (e: UnknownHostException) {
            handleNetworkFail(record)
        } catch (e: IOException) {
            if (e.message?.contains("ENOSPC") == true) {
                notificationHelper.showStorageFull()
                repository.updateStatus(record.id, DownloadStatus.FAILED)
            } else {
                handleNetworkFail(record)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            repository.updateStatus(record.id, DownloadStatus.FAILED)
            notificationHelper.showFailed(record.id, record.filename)
        } finally {
            if (activeCount.decrementAndGet() == 0) stopSelf()
        }
    }

    private suspend fun processBlob(record: DownloadRecord) {
        activeCount.incrementAndGet()
        startForeground()
        try {
            repository.updateStatus(record.id, DownloadStatus.DOWNLOADING)
            val tempFile = File(record.tempFilePath)
            if (!tempFile.exists()) throw IOException("Temp file missing")

            val opened = mediaStoreWriter.open(record.filename, record.mimeType)
                ?: throw IOException("MediaStore open failed")
            try {
                opened.stream.use { out ->
                    tempFile.inputStream().use { input -> input.copyTo(out) }
                }
                mediaStoreWriter.finalize(opened.uri)
                repository.updateStatus(record.id, DownloadStatus.COMPLETE)
                notificationHelper.showComplete(record.id, record.filename, opened.uri)
            } catch (e: Exception) {
                mediaStoreWriter.delete(opened.uri)
                throw e
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Blob download failed", e)
            repository.updateStatus(record.id, DownloadStatus.FAILED)
            notificationHelper.showFailed(record.id, record.filename)
        } finally {
            if (activeCount.decrementAndGet() == 0) stopSelf()
        }
    }

    private suspend fun handleNetworkFail(record: DownloadRecord) {
        repository.updateStatus(record.id, DownloadStatus.FAILED_NETWORK)
        val current = repository.getById(record.id) ?: return
        if (current.retryCount < 3) {
            repository.incrementRetry(record.id)
            RetryDownloadWorker.schedule(this, record.id, record.originalUrl, record.filename)
        } else {
            notificationHelper.showFailed(record.id, record.filename)
        }
    }

    private fun startForeground() {
        val notif = notificationHelper.buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NotificationHelper.FOREGROUND_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NotificationHelper.FOREGROUND_ID, notif)
        }
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DownloadService"
        const val ACTION_HTTPS = "com.cobalt.android.HTTPS"
        const val ACTION_BLOB = "com.cobalt.android.BLOB"
        const val EXTRA_URL = "url"
        const val EXTRA_ORIGINAL_URL = "originalUrl"
        const val EXTRA_FILENAME = "filename"
        const val EXTRA_MIME_TYPE = "mimeType"
        const val EXTRA_COOKIES = "cookies"
        const val EXTRA_USER_AGENT = "userAgent"
        const val EXTRA_TEMP_PATH = "tempPath"

        fun startHttps(ctx: Context, cobaltUrl: String, filename: String,
                       mimeType: String, cookies: String, userAgent: String, originalUrl: String) {
            ctx.startForegroundService(Intent(ctx, DownloadService::class.java).apply {
                action = ACTION_HTTPS
                putExtra(EXTRA_URL, cobaltUrl)
                putExtra(EXTRA_ORIGINAL_URL, originalUrl)
                putExtra(EXTRA_FILENAME, filename)
                putExtra(EXTRA_MIME_TYPE, mimeType)
                putExtra(EXTRA_COOKIES, cookies)
                putExtra(EXTRA_USER_AGENT, userAgent)
            })
        }

        fun startBlob(ctx: Context, tempPath: String, filename: String,
                      mimeType: String, originalUrl: String) {
            ctx.startForegroundService(Intent(ctx, DownloadService::class.java).apply {
                action = ACTION_BLOB
                putExtra(EXTRA_TEMP_PATH, tempPath)
                putExtra(EXTRA_ORIGINAL_URL, originalUrl)
                putExtra(EXTRA_FILENAME, filename)
                putExtra(EXTRA_MIME_TYPE, mimeType)
            })
        }
    }
}
```

- [ ] **Step 2: Write RetryDownloadWorker.kt**

```kotlin
// app/src/main/java/com/cobalt/android/download/RetryDownloadWorker.kt
package com.cobalt.android.download

import android.content.Context
import androidx.work.*
import com.cobalt.android.util.NotificationHelper
import java.util.concurrent.TimeUnit

class RetryDownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val recordId = inputData.getLong(KEY_RECORD_ID, -1L)
        val originalUrl = inputData.getString(KEY_ORIGINAL_URL) ?: return Result.failure()
        val filename = inputData.getString(KEY_FILENAME) ?: ""

        NotificationHelper(applicationContext).showRetryReady(recordId, originalUrl, filename)
        return Result.success()
    }

    companion object {
        private const val KEY_RECORD_ID = "recordId"
        private const val KEY_ORIGINAL_URL = "originalUrl"
        private const val KEY_FILENAME = "filename"

        fun schedule(ctx: Context, recordId: Long, originalUrl: String, filename: String) {
            val data = workDataOf(
                KEY_RECORD_ID to recordId,
                KEY_ORIGINAL_URL to originalUrl,
                KEY_FILENAME to filename
            )
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<RetryDownloadWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("retry_$recordId")
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "retry_$recordId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
```

- [ ] **Step 3: Commit**

```powershell
git add -A && git commit -m "feat: DownloadService and RetryDownloadWorker"
```

---

## Task 9: CobaltWebView

**Files:**
- Create: `app/src/main/java/com/cobalt/android/CobaltWebView.kt`

- [ ] **Step 1: Write CobaltWebView.kt**

```kotlin
// app/src/main/java/com/cobalt/android/CobaltWebView.kt
package com.cobalt.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.util.AttributeSet
import android.webkit.*
import com.cobalt.android.download.DownloadService
import java.io.File

class CobaltWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    interface Listener {
        fun onUrlSubmitted(originalUrl: String)
        fun onBlobDownloadReady(tempFile: File, filename: String, mimeType: String)
        fun onBlobError(message: String)
        fun onPageError(url: String, isCustomInstance: Boolean)
        fun onPageLoaded()
        fun onLocalProcessingDetected()
    }

    var listener: Listener? = null
    var pendingUrl: String? = null
    var audioOnlyMode: Boolean = false
    private var currentOriginalUrl: String = ""
    private val bridgeJs: String by lazy {
        context.assets.open("js/cobalt_bridge.js").bufferedReader().readText()
    }

    private val jsBridge = CobaltJsBridge(
        cacheDir = context.cacheDir,
        listener = object : CobaltJsBridge.Listener {
            override fun onUrlSubmitted(originalUrl: String) {
                currentOriginalUrl = originalUrl
                listener?.onUrlSubmitted(originalUrl)
            }
            override fun onInjectComplete(success: Boolean) {
                if (!success && pendingUrl != null) {
                    // Fallback: reload with URL in fragment
                    val url = pendingUrl ?: return
                    pendingUrl = null
                    post { loadUrl("https://cobalt.tools/#$url") }
                }
            }
            override fun onBlobReady(tempFile: File, filename: String, mimeType: String) {
                post { listener?.onBlobDownloadReady(tempFile, filename, mimeType) }
            }
            override fun onBlobError(message: String) {
                post { listener?.onBlobError(message) }
            }
        }
    )

    init {
        configure()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        addJavascriptInterface(jsBridge, "CobaltBridge")

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                injectBridgeJs()
                val urlToInject = pendingUrl
                if (urlToInject != null) {
                    pendingUrl = null
                    postDelayed({ injectUrl(urlToInject, audioOnlyMode) }, 500)
                }
                listener?.onPageLoaded()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    val isCustom = !request.url.host.equals("cobalt.tools", ignoreCase = true) &&
                                   !request.url.host.equals("www.cobalt.tools", ignoreCase = true)
                    listener?.onPageError(request.url.toString(), isCustom)
                }
            }
        }

        webChromeClient = object : WebChromeClient() {}

        setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            if (url.startsWith("blob:")) {
                listener?.onLocalProcessingDetected()
                val filename = extractFilename(contentDisposition, mimetype)
                evaluateJavascript(
                    "window._cobaltDownloadBlob('$url', '${filename.replace("'", "\\'")}', '${mimetype.replace("'", "\\'")}');",
                    null
                )
            } else {
                val filename = extractFilename(contentDisposition, mimetype)
                val cookies = CookieManager.getInstance().getCookie(url) ?: ""
                DownloadService.startHttps(
                    context = context,
                    cobaltUrl = url,
                    filename = filename,
                    mimeType = mimetype,
                    cookies = cookies,
                    userAgent = userAgent,
                    originalUrl = currentOriginalUrl
                )
            }
        }
    }

    fun submitUrl(url: String, audioOnly: Boolean) {
        audioOnlyMode = audioOnly
        if (url2 != null) return // already pending
        pendingUrl = url
        if (progress >= 100) {
            pendingUrl = null
            injectUrl(url, audioOnly)
        }
    }

    fun injectUrl(url: String, audioOnly: Boolean) {
        val escaped = url.replace("'", "\\'")
        evaluateJavascript("window._cobaltInjectUrl('$escaped', ${audioOnly});", null)
    }

    private fun injectBridgeJs() {
        evaluateJavascript("(function(){${bridgeJs.replace("\\", "\\\\")}})()", null)
    }

    private fun extractFilename(contentDisposition: String, mimeType: String): String {
        val fromDisp = contentDisposition
            .split(";")
            .firstOrNull { it.trim().startsWith("filename") }
            ?.substringAfter("=")
            ?.trim()
            ?.trim('"')
        if (!fromDisp.isNullOrBlank()) return fromDisp
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "mp4"
        return "cobalt_${System.currentTimeMillis()}.$ext"
    }
}
```

Note: fix the typo `url2` → remove that guard; `submitUrl` should be:

```kotlin
    fun submitUrl(url: String, audioOnly: Boolean) {
        audioOnlyMode = audioOnly
        pendingUrl = url
        if (url.isNotBlank()) {
            post {
                if (progress >= 100) {
                    pendingUrl = null
                    injectUrl(url, audioOnly)
                }
                // else onPageFinished will pick up pendingUrl
            }
        }
    }
```

- [ ] **Step 2: Commit**

```powershell
git add -A && git commit -m "feat: CobaltWebView with JS injection and download listener"
```

---

## Task 10: UI — Layouts

**Files:**
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/layout/sheet_download_queue.xml`
- Create: `app/src/main/res/layout/item_download.xml`
- Create: `app/src/main/res/layout/sheet_settings.xml`

- [ ] **Step 1: Write activity_main.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/layout/activity_main.xml -->
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/cobalt_background">

    <com.cobalt.android.CobaltWebView
        android:id="@+id/webView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- Settings button — top right, floated over WebView -->
    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnSettings"
        style="@style/Widget.Material3.Button.TextButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="top|end"
        android:layout_marginTop="48dp"
        android:layout_marginEnd="12dp"
        android:text="⋯"
        android:textColor="@color/cobalt_text_secondary"
        android:textSize="20sp"
        android:padding="8dp"
        app:backgroundTint="@color/cobalt_surface"
        app:cornerRadius="@dimen/border_radius" />

    <!-- Queue FAB — bottom right -->
    <FrameLayout
        android:id="@+id/fabQueue"
        android:layout_width="@dimen/fab_size"
        android:layout_height="@dimen/fab_size"
        android:layout_gravity="bottom|end"
        android:layout_marginBottom="24dp"
        android:layout_marginEnd="16dp"
        android:background="@drawable/bg_fab"
        android:clickable="true"
        android:focusable="true">

        <ImageView
            android:layout_width="20dp"
            android:layout_height="20dp"
            android:layout_gravity="center"
            android:src="@drawable/ic_queue"
            android:contentDescription="Queue" />

        <TextView
            android:id="@+id/tvBadge"
            android:layout_width="16dp"
            android:layout_height="16dp"
            android:layout_gravity="top|end"
            android:layout_marginTop="4dp"
            android:layout_marginEnd="4dp"
            android:background="@drawable/bg_badge"
            android:gravity="center"
            android:textColor="@android:color/white"
            android:textSize="9sp"
            android:visibility="gone"
            android:fontFamily="@font/ibm_plex_mono_regular" />

    </FrameLayout>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

Add drawables for fab and badge backgrounds:

`app/src/main/res/drawable/bg_fab.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <solid android:color="@color/cobalt_surface" />
    <stroke android:width="1dp" android:color="#0DFFFFFF" />
</shape>
```

`app/src/main/res/drawable/bg_badge.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <solid android:color="@color/cobalt_accent_blue" />
</shape>
```

- [ ] **Step 2: Write sheet_download_queue.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/layout/sheet_download_queue.xml -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:background="@color/cobalt_surface_sidebar"
    android:minHeight="200dp">

    <!-- Drag handle -->
    <View
        android:layout_width="40dp"
        android:layout_height="4dp"
        android:layout_gravity="center_horizontal"
        android:layout_marginTop="12dp"
        android:layout_marginBottom="8dp"
        android:background="@color/cobalt_input_border"
        android:layout_marginStart="0dp" />

    <!-- Tabs -->
    <com.google.android.material.tabs.TabLayout
        android:id="@+id/tabLayout"
        android:layout_width="match_parent"
        android:layout_height="40dp"
        app:tabGravity="fill"
        app:tabMode="fixed"
        app:tabTextColor="@color/cobalt_text_secondary"
        app:tabSelectedTextColor="@color/cobalt_text_primary"
        app:tabIndicatorColor="@color/cobalt_accent_blue"
        app:tabBackground="@android:color/transparent"
        android:background="@color/cobalt_surface_sidebar"
        app:tabTextAppearance="@style/TextAppearance.Cobalt.Mono" />

    <!-- Download list -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerView"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:maxHeight="500dp"
        android:clipToPadding="false"
        android:paddingBottom="16dp" />

    <!-- Empty state -->
    <TextView
        android:id="@+id/tvEmpty"
        android:layout_width="match_parent"
        android:layout_height="80dp"
        android:gravity="center"
        android:text="no downloads yet"
        android:textColor="@color/cobalt_text_secondary"
        android:fontFamily="@font/ibm_plex_mono_regular"
        android:textSize="13sp"
        android:visibility="gone" />

</LinearLayout>
```

- [ ] **Step 3: Write item_download.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/layout/item_download.xml -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingHorizontal="16dp"
    android:paddingVertical="12dp">

    <!-- Filename -->
    <TextView
        android:id="@+id/tvFilename"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textColor="@color/cobalt_text_primary"
        android:fontFamily="@font/ibm_plex_mono_regular"
        android:textSize="14sp"
        android:ellipsize="middle"
        android:singleLine="true" />

    <!-- Status line -->
    <TextView
        android:id="@+id/tvStatus"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="2dp"
        android:textColor="@color/cobalt_text_secondary"
        android:fontFamily="@font/ibm_plex_mono_regular"
        android:textSize="12sp" />

    <!-- Progress bar -->
    <ProgressBar
        android:id="@+id/progressBar"
        style="@style/Widget.AppCompat.ProgressBar.Horizontal"
        android:layout_width="match_parent"
        android:layout_height="@dimen/progress_bar_height"
        android:layout_marginTop="6dp"
        android:progressTint="@color/cobalt_accent_blue"
        android:progressBackgroundTint="@color/cobalt_input_border"
        android:max="100"
        android:visibility="gone" />

    <!-- Action buttons -->
    <LinearLayout
        android:id="@+id/layoutActions"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:orientation="horizontal"
        android:gravity="start">

        <Button
            android:id="@+id/btnOpen"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:layout_width="wrap_content"
            android:layout_height="32dp"
            android:text="@string/action_open"
            android:textColor="@color/cobalt_text_primary"
            android:textSize="12sp"
            android:fontFamily="@font/ibm_plex_mono_regular"
            android:paddingHorizontal="12dp"
            android:visibility="gone" />

        <Button
            android:id="@+id/btnRetry"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:layout_width="wrap_content"
            android:layout_height="32dp"
            android:layout_marginStart="8dp"
            android:text="@string/action_retry"
            android:textColor="@color/cobalt_error_red"
            android:textSize="12sp"
            android:fontFamily="@font/ibm_plex_mono_regular"
            android:paddingHorizontal="12dp"
            android:visibility="gone" />

        <Button
            android:id="@+id/btnCancel"
            style="@style/Widget.Material3.Button.TextButton"
            android:layout_width="wrap_content"
            android:layout_height="32dp"
            android:layout_marginStart="8dp"
            android:text="@string/action_cancel"
            android:textColor="@color/cobalt_text_secondary"
            android:textSize="12sp"
            android:fontFamily="@font/ibm_plex_mono_regular"
            android:paddingHorizontal="12dp"
            android:visibility="gone" />

    </LinearLayout>

</LinearLayout>
```

- [ ] **Step 4: Write sheet_settings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/layout/sheet_settings.xml -->
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@color/cobalt_surface_sidebar">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <!-- Handle -->
        <View
            android:layout_width="40dp"
            android:layout_height="4dp"
            android:layout_gravity="center_horizontal"
            android:layout_marginBottom="16dp"
            android:background="@color/cobalt_input_border" />

        <!-- Cobalt instance URL -->
        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/settings_cobalt_url"
            android:textColor="@color/cobalt_text_secondary"
            android:fontFamily="@font/ibm_plex_mono_regular"
            android:textSize="11sp"
            android:layout_marginBottom="4dp" />

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/etCobaltUrl"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="textUri"
            android:textColor="@color/cobalt_text_primary"
            android:fontFamily="@font/ibm_plex_mono_regular"
            android:textSize="13sp"
            android:background="@color/cobalt_surface_elevated"
            android:padding="10dp" />

        <!-- Audio-only mode -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginTop="16dp"
            android:gravity="center_vertical">

            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/settings_audio_only"
                android:textColor="@color/cobalt_text_primary"
                android:fontFamily="@font/ibm_plex_mono_regular"
                android:textSize="14sp" />

            <com.google.android.material.materialswitch.MaterialSwitch
                android:id="@+id/switchAudioOnly"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                app:trackTint="@color/cobalt_input_border"
                app:thumbTint="@color/cobalt_text_primary" />

        </LinearLayout>

        <!-- Clipboard trigger -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:layout_marginTop="12dp"
            android:gravity="center_vertical">

            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/settings_clipboard_trigger"
                android:textColor="@color/cobalt_text_primary"
                android:fontFamily="@font/ibm_plex_mono_regular"
                android:textSize="14sp" />

            <com.google.android.material.materialswitch.MaterialSwitch
                android:id="@+id/switchClipboard"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                app:trackTint="@color/cobalt_input_border"
                app:thumbTint="@color/cobalt_text_primary" />

        </LinearLayout>

        <!-- Battery optimization -->
        <Button
            android:id="@+id/btnBattery"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="@string/settings_battery"
            android:textColor="@color/cobalt_text_primary"
            android:fontFamily="@font/ibm_plex_mono_regular"
            app:strokeColor="@color/cobalt_input_border" />

        <!-- Clear history -->
        <Button
            android:id="@+id/btnClearHistory"
            style="@style/Widget.Material3.Button.OutlinedButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/settings_clear_history"
            android:textColor="@color/cobalt_error_red"
            android:fontFamily="@font/ibm_plex_mono_regular"
            app:strokeColor="@color/cobalt_error_red" />

        <View android:layout_width="0dp" android:layout_height="24dp" />

    </LinearLayout>
</ScrollView>
```

- [ ] **Step 5: Commit**

```powershell
git add -A && git commit -m "feat: all UI layouts"
```

---

## Task 11: ViewModel, Adapter, Bottom Sheets

**Files:**
- Create: `app/src/main/java/com/cobalt/android/ui/DownloadQueueViewModel.kt`
- Create: `app/src/main/java/com/cobalt/android/ui/DownloadAdapter.kt`
- Create: `app/src/main/java/com/cobalt/android/ui/DownloadQueueSheet.kt`
- Create: `app/src/main/java/com/cobalt/android/ui/SettingsSheet.kt`

- [ ] **Step 1: Write DownloadQueueViewModel.kt**

```kotlin
// app/src/main/java/com/cobalt/android/ui/DownloadQueueViewModel.kt
package com.cobalt.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.cobalt.android.download.DownloadRecord
import com.cobalt.android.download.DownloadRepository
import kotlinx.coroutines.launch

class DownloadQueueViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DownloadRepository(app)

    val allDownloads: LiveData<List<DownloadRecord>> = repo.allDownloads
    val activeDownloads: LiveData<List<DownloadRecord>> = repo.activeDownloads

    fun clearHistory() = viewModelScope.launch { repo.clearHistory() }
}
```

- [ ] **Step 2: Write DownloadAdapter.kt**

```kotlin
// app/src/main/java/com/cobalt/android/ui/DownloadAdapter.kt
package com.cobalt.android.ui

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cobalt.android.databinding.ItemDownloadBinding
import com.cobalt.android.download.DownloadRecord
import com.cobalt.android.download.DownloadStatus

class DownloadAdapter(
    private val onRetry: (DownloadRecord) -> Unit
) : ListAdapter<DownloadRecord, DownloadAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemDownloadBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val record = getItem(position)
        with(holder.binding) {
            tvFilename.text = record.filename.ifBlank { "downloading…" }

            when (record.status) {
                DownloadStatus.QUEUED -> {
                    tvStatus.text = "queued"
                    progressBar.visibility = View.VISIBLE
                    progressBar.isIndeterminate = true
                    btnOpen.visibility = View.GONE
                    btnRetry.visibility = View.GONE
                    btnCancel.visibility = View.VISIBLE
                }
                DownloadStatus.DOWNLOADING -> {
                    val pct = if (record.totalBytes > 0)
                        (record.bytesDownloaded * 100 / record.totalBytes).toInt() else 0
                    val mb = record.bytesDownloaded / 1_048_576.0
                    tvStatus.text = if (record.totalBytes > 0)
                        "%.1f / %.1f MB".format(mb, record.totalBytes / 1_048_576.0)
                    else "%.1f MB".format(mb)
                    progressBar.visibility = View.VISIBLE
                    progressBar.isIndeterminate = record.totalBytes <= 0
                    progressBar.progress = pct
                    btnOpen.visibility = View.GONE
                    btnRetry.visibility = View.GONE
                    btnCancel.visibility = View.VISIBLE
                }
                DownloadStatus.COMPLETE -> {
                    val mb = record.totalBytes / 1_048_576.0
                    tvStatus.text = if (record.totalBytes > 0) "%.1f MB · saved" .format(mb) else "saved"
                    progressBar.visibility = View.GONE
                    btnOpen.visibility = View.VISIBLE
                    btnRetry.visibility = View.GONE
                    btnCancel.visibility = View.GONE
                    btnOpen.setOnClickListener { openFile(root.context, record) }
                }
                DownloadStatus.FAILED, DownloadStatus.FAILED_NETWORK -> {
                    tvStatus.text = if (record.status == DownloadStatus.FAILED_NETWORK) "network error" else "failed"
                    progressBar.visibility = View.GONE
                    btnOpen.visibility = View.GONE
                    btnRetry.visibility = View.VISIBLE
                    btnCancel.visibility = View.GONE
                    btnRetry.setOnClickListener { onRetry(record) }
                }
            }
        }
    }

    private fun openFile(context: Context, record: DownloadRecord) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(record.cobaltUrl), record.mimeType)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) { /* file may have been moved */ }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<DownloadRecord>() {
            override fun areItemsTheSame(a: DownloadRecord, b: DownloadRecord) = a.id == b.id
            override fun areContentsTheSame(a: DownloadRecord, b: DownloadRecord) = a == b
        }
    }
}
```

- [ ] **Step 3: Write DownloadQueueSheet.kt**

```kotlin
// app/src/main/java/com/cobalt/android/ui/DownloadQueueSheet.kt
package com.cobalt.android.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.cobalt.android.databinding.SheetDownloadQueueBinding
import com.cobalt.android.download.DownloadRecord
import com.cobalt.android.download.DownloadStatus
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout

class DownloadQueueSheet : BottomSheetDialogFragment() {

    private var _binding: SheetDownloadQueueBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DownloadQueueViewModel by activityViewModels()
    private lateinit var adapter: DownloadAdapter

    var onRetry: ((DownloadRecord) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetDownloadQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = DownloadAdapter { record -> onRetry?.invoke(record) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(com.cobalt.android.R.string.tab_active)))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(com.cobalt.android.R.string.tab_history)))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = updateList(tab.position == 0)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        viewModel.allDownloads.observe(viewLifecycleOwner) { _ -> updateList(binding.tabLayout.selectedTabPosition == 0) }
        viewModel.activeDownloads.observe(viewLifecycleOwner) { _ -> updateList(binding.tabLayout.selectedTabPosition == 0) }
    }

    private fun updateList(activeTab: Boolean) {
        val source = if (activeTab) viewModel.activeDownloads.value else viewModel.allDownloads.value?.filter {
            it.status == DownloadStatus.COMPLETE || it.status == DownloadStatus.FAILED
        }
        val list = source ?: emptyList()
        adapter.submitList(list)
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DownloadQueueSheet"
        fun newInstance() = DownloadQueueSheet()
    }
}
```

- [ ] **Step 4: Write SettingsSheet.kt**

```kotlin
// app/src/main/java/com/cobalt/android/ui/SettingsSheet.kt
package com.cobalt.android.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.cobalt.android.databinding.SheetSettingsBinding
import com.cobalt.android.util.SettingsRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SettingsSheet : BottomSheetDialogFragment() {

    private var _binding: SheetSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var settings: SettingsRepository

    var onUrlChanged: ((String) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        settings = SettingsRepository(requireContext())

        binding.etCobaltUrl.setText(settings.cobaltInstanceUrl)
        binding.switchAudioOnly.isChecked = settings.audioOnlyMode
        binding.switchClipboard.isChecked = settings.clipboardTriggerEnabled

        binding.switchAudioOnly.setOnCheckedChangeListener { _, checked ->
            settings.audioOnlyMode = checked
        }
        binding.switchClipboard.setOnCheckedChangeListener { _, checked ->
            settings.clipboardTriggerEnabled = checked
        }
        binding.btnBattery.setOnClickListener {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            })
        }
        binding.btnClearHistory.setOnClickListener {
            // Trigger via ViewModel — find it through parent
            (parentFragment as? DownloadQueueSheet)?.let { }
            // Direct db clear via settings repo approach
            val repo = com.cobalt.android.download.DownloadRepository(requireContext())
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                repo.clearHistory()
            }
            dismiss()
        }
    }

    override fun onStop() {
        super.onStop()
        val url = binding.etCobaltUrl.text?.toString()?.trim() ?: return
        if (url.isNotBlank() && url != settings.cobaltInstanceUrl) {
            settings.cobaltInstanceUrl = url
            onUrlChanged?.invoke(url)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingsSheet"
        fun newInstance() = SettingsSheet()
    }
}
```

- [ ] **Step 5: Commit**

```powershell
git add -A && git commit -m "feat: ViewModel, DownloadAdapter, DownloadQueueSheet, SettingsSheet"
```

---

## Task 12: MainActivity + CobaltApplication + App Shortcuts

**Files:**
- Create: `app/src/main/java/com/cobalt/android/CobaltApplication.kt`
- Create: `app/src/main/java/com/cobalt/android/MainActivity.kt`
- Replace: `app/src/main/res/xml/shortcuts.xml`
- Delete: `app/src/main/java/com/cobalt/android/Placeholder.kt`

- [ ] **Step 1: Write CobaltApplication.kt**

```kotlin
// app/src/main/java/com/cobalt/android/CobaltApplication.kt
package com.cobalt.android

import android.app.Application
import androidx.work.Configuration
import com.cobalt.android.util.NotificationHelper

class CobaltApplication : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper(this).createChannel()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()
}
```

- [ ] **Step 2: Write MainActivity.kt**

```kotlin
// app/src/main/java/com/cobalt/android/MainActivity.kt
package com.cobalt.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.cobalt.android.databinding.ActivityMainBinding
import com.cobalt.android.download.DownloadService
import com.cobalt.android.ui.DownloadQueueSheet
import com.cobalt.android.ui.DownloadQueueViewModel
import com.cobalt.android.ui.SettingsSheet
import com.cobalt.android.util.ClipboardHelper
import com.cobalt.android.util.NotificationHelper
import com.cobalt.android.util.SettingsRepository
import com.cobalt.android.util.UrlMatcher
import com.google.android.material.snackbar.Snackbar
import java.io.File

class MainActivity : AppCompatActivity(), CobaltWebView.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsRepository
    private val queueViewModel: DownloadQueueViewModel by viewModels()
    private var currentOriginalUrl: String = ""

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, app works either way */ }

    companion object {
        const val EXTRA_SHORTCUT_PASTE = "shortcut_paste"
        const val EXTRA_SHORTCUT_QUEUE = "shortcut_queue"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = SettingsRepository(this)

        setupWebView()
        setupFab()
        setupSettingsButton()
        handleFirstLaunch()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (settings.clipboardTriggerEnabled) checkClipboard()
    }

    private fun setupWebView() {
        binding.webView.listener = this
        binding.webView.loadUrl(settings.cobaltInstanceUrl)

        queueViewModel.activeDownloads.observe(this) { list ->
            val count = list.size
            binding.tvBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
            binding.tvBadge.text = count.toString()
        }
    }

    private fun setupFab() {
        binding.fabQueue.setOnClickListener {
            val sheet = DownloadQueueSheet.newInstance().also { s ->
                s.onRetry = { record -> submitUrl(record.originalUrl) }
            }
            sheet.show(supportFragmentManager, DownloadQueueSheet.TAG)
        }
    }

    private fun setupSettingsButton() {
        binding.btnSettings.setOnClickListener {
            val sheet = SettingsSheet.newInstance().also { s ->
                s.onUrlChanged = { newUrl ->
                    binding.webView.loadUrl(newUrl)
                }
            }
            sheet.show(supportFragmentManager, SettingsSheet.TAG)
        }
    }

    private fun handleIntent(intent: Intent?) {
        when {
            intent?.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val url = UrlMatcher.extractUrl(text)
                if (url != null) {
                    submitUrl(url)
                } else {
                    Snackbar.make(binding.root, getString(R.string.not_a_supported_link), Snackbar.LENGTH_SHORT).show()
                }
            }
            intent?.getBooleanExtra(EXTRA_SHORTCUT_QUEUE, false) == true -> {
                binding.fabQueue.performClick()
            }
            intent?.getBooleanExtra(EXTRA_SHORTCUT_PASTE, false) == true -> {
                val url = ClipboardHelper.getSupportedUrl(this)
                if (url != null) submitUrl(url)
            }
        }
    }

    private fun checkClipboard() {
        val url = ClipboardHelper.getSupportedUrl(this) ?: return
        Snackbar.make(binding.root, getString(R.string.download_from_clipboard), Snackbar.LENGTH_LONG)
            .setAction("download") { submitUrl(url) }
            .setBackgroundTint(getColor(R.color.cobalt_surface))
            .setTextColor(getColor(R.color.cobalt_text_primary))
            .setActionTextColor(getColor(R.color.cobalt_accent_blue))
            .show()
    }

    private fun submitUrl(url: String) {
        currentOriginalUrl = url
        binding.webView.submitUrl(url, settings.audioOnlyMode)
    }

    private fun handleFirstLaunch() {
        if (settings.firstLaunchDone) return
        settings.firstLaunchDone = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.battery_dialog_title))
                .setMessage(getString(R.string.battery_dialog_message))
                .setPositiveButton(getString(R.string.allow)) { _, _ ->
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                }
                .setNegativeButton(getString(R.string.not_now), null)
                .show()
        }
    }

    // CobaltWebView.Listener
    override fun onUrlSubmitted(originalUrl: String) {
        currentOriginalUrl = originalUrl
    }

    override fun onBlobDownloadReady(tempFile: File, filename: String, mimeType: String) {
        Toast.makeText(this, getString(R.string.merging_locally), Toast.LENGTH_SHORT).show()
        DownloadService.startBlob(this, tempFile.absolutePath, filename, mimeType, currentOriginalUrl)
    }

    override fun onBlobError(message: String) {
        Toast.makeText(this, getString(R.string.local_merge_failed), Toast.LENGTH_SHORT).show()
    }

    override fun onPageError(url: String, isCustomInstance: Boolean) {
        val errorHtml = buildErrorPage(isCustomInstance)
        binding.webView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
    }

    override fun onPageLoaded() { /* no-op */ }

    override fun onLocalProcessingDetected() {
        Toast.makeText(this, getString(R.string.merging_locally), Toast.LENGTH_SHORT).show()
    }

    private fun buildErrorPage(isCustomInstance: Boolean): String {
        val hint = if (isCustomInstance) "<p style='color:#818181;font-size:12px'>${getString(R.string.check_cobalt_url)}</p>" else ""
        return """<!DOCTYPE html><html><body style='background:#000;color:#e1e1e1;font-family:monospace;display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;margin:0;padding:24px;box-sizing:border-box;text-align:center'>
<p style='font-size:16px'>can't reach cobalt</p>
$hint
<button onclick='location.reload()' style='margin-top:16px;background:#191919;color:#e1e1e1;border:1px solid #383838;border-radius:11px;padding:8px 20px;font-family:monospace;font-size:14px;cursor:pointer'>retry</button>
</body></html>"""
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) binding.webView.goBack()
        else super.onBackPressed()
    }
}
```

- [ ] **Step 3: Write shortcuts.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/res/xml/shortcuts.xml -->
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">

    <shortcut
        android:shortcutId="paste_download"
        android:enabled="true"
        android:icon="@drawable/ic_queue"
        android:shortcutShortLabel="@string/shortcut_paste_download"
        android:shortcutLongLabel="@string/shortcut_paste_download">
        <intent
            android:action="android.intent.action.MAIN"
            android:targetPackage="com.cobalt.android"
            android:targetClass="com.cobalt.android.MainActivity">
            <extra android:name="shortcut_paste" android:value="true" />
        </intent>
    </shortcut>

    <shortcut
        android:shortcutId="open_queue"
        android:enabled="true"
        android:icon="@drawable/ic_queue"
        android:shortcutShortLabel="@string/shortcut_open_queue"
        android:shortcutLongLabel="@string/shortcut_open_queue">
        <intent
            android:action="android.intent.action.MAIN"
            android:targetPackage="com.cobalt.android"
            android:targetClass="com.cobalt.android.MainActivity">
            <extra android:name="shortcut_queue" android:value="true" />
        </intent>
    </shortcut>

</shortcuts>
```

- [ ] **Step 4: Delete placeholder**

```powershell
Remove-Item "D:\Projects\CobaltAndroid\app\src\main\java\com\cobalt\android\Placeholder.kt"
```

- [ ] **Step 5: Add missing launcher icons (required to compile)**

Android requires `@mipmap/ic_launcher` referenced in the manifest. Create minimal adaptive icon resources:

```powershell
New-Item -ItemType Directory -Force "D:\Projects\CobaltAndroid\app\src\main\res\mipmap-mdpi" | Out-Null
New-Item -ItemType Directory -Force "D:\Projects\CobaltAndroid\app\src\main\res\mipmap-hdpi" | Out-Null
New-Item -ItemType Directory -Force "D:\Projects\CobaltAndroid\app\src\main\res\mipmap-xhdpi" | Out-Null
New-Item -ItemType Directory -Force "D:\Projects\CobaltAndroid\app\src\main\res\mipmap-xxhdpi" | Out-Null
New-Item -ItemType Directory -Force "D:\Projects\CobaltAndroid\app\src\main\res\mipmap-xxxhdpi" | Out-Null
New-Item -ItemType Directory -Force "D:\Projects\CobaltAndroid\app\src\main\res\mipmap-anydpi-v26" | Out-Null
```

Write `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/cobalt_background" />
    <foreground android:drawable="@drawable/ic_queue" />
</adaptive-icon>
```

Write `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` with the same content.

For older APIs, copy ic_queue.xml as a PNG-replacement — or simply point to the vector in each density bucket. Add to `app/build.gradle.kts` inside `android {}`:

```kotlin
    vectorDrawables { useSupportLibrary = true }
```

And add this dependency:
```kotlin
    implementation("androidx.vectordrawable:vectordrawable:1.2.0")
```

Also add to each `mipmap-*/ic_launcher.xml` (non-adaptive fallback):
```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@color/cobalt_background"/>
</layer-list>
```

- [ ] **Step 6: Full compile check**

```powershell
cd D:\Projects\CobaltAndroid
.\gradlew.bat assembleDebug 2>&1 | Tee-Object -FilePath build_output.txt
Select-String -Path build_output.txt -Pattern "error:|BUILD"
```

Expected: `BUILD SUCCESSFUL`. If there are errors, fix them before proceeding — common causes:
- Missing import statements (add `import` lines to the affected .kt files)
- `R.color.cobalt_error_red` not found (check colors.xml has the key)
- View binding class names not matching layout filenames

- [ ] **Step 7: Commit**

```powershell
git add -A && git commit -m "feat: MainActivity, CobaltApplication, shortcuts — full app wired"
```

---

## Task 13: Build Debug APK

- [ ] **Step 1: Run full debug build**

```powershell
cd D:\Projects\CobaltAndroid
.\gradlew.bat assembleDebug 2>&1 | Select-String "BUILD|apk|error:"
```

Expected output includes:
```
BUILD SUCCESSFUL
```

- [ ] **Step 2: Locate APK**

```powershell
Get-ChildItem -Recurse -Filter "*.apk" "D:\Projects\CobaltAndroid\app\build\outputs\"
```

Expected: `app-debug.apk` in `app\build\outputs\apk\debug\`

- [ ] **Step 3: Verify APK is valid**

```powershell
$apk = "D:\Projects\CobaltAndroid\app\build\outputs\apk\debug\app-debug.apk"
$sdkBuildTools = "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0"
& "$sdkBuildTools\aapt.exe" dump badging $apk | Select-String "package|launchable-activity|uses-permission"
```

Expected: lines showing `package: name='com.cobalt.android'` and `launchable-activity: name='com.cobalt.android.MainActivity'`

- [ ] **Step 4: Final commit + tag**

```powershell
git add -A
git commit -m "feat: complete Cobalt Android v1.0 debug APK"
git tag v1.0-debug
```

- [ ] **Step 5: Install on device (optional — requires USB debugging enabled)**

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb install -r "D:\Projects\CobaltAndroid\app\build\outputs\apk\debug\app-debug.apk"
```

Expected: `Success`

If no device is connected, transfer the APK to the S22 Ultra via USB or Google Drive and install manually (Settings → Install unknown apps must be enabled for the file manager or browser you use to open it).

---

---

## Post-Review Corrections (apply during implementation)

### Correction 1: DownloadRecord needs mediaStoreUriString field

In `DownloadRecord.kt`, add this field:
```kotlin
val mediaStoreUriString: String = ""
```

In `DownloadDao.kt`, add:
```kotlin
@Query("UPDATE downloads SET mediaStoreUriString = :uri WHERE id = :id")
suspend fun updateMediaStoreUri(id: Long, uri: String)
```

In `DownloadRepository.kt`, add:
```kotlin
suspend fun updateMediaStoreUri(id: Long, uri: String) = dao.updateMediaStoreUri(id, uri)
```

In `DownloadService.kt`, after `mediaStoreWriter.finalize(opened.uri)`, add:
```kotlin
repository.updateMediaStoreUri(record.id, opened.uri.toString())
```

In `DownloadAdapter.kt`, replace `Uri.parse(record.cobaltUrl)` with:
```kotlin
val uri = if (record.mediaStoreUriString.isNotBlank())
    Uri.parse(record.mediaStoreUriString)
else return
```

### Correction 2: Shortcut extras are strings, not booleans

In `shortcuts.xml`, add `android:valueType="boolean"` to each `<extra>` or leave as-is.

In `MainActivity.kt`, replace `getBooleanExtra` checks:
```kotlin
// Replace:
intent?.getBooleanExtra(EXTRA_SHORTCUT_QUEUE, false) == true
// With:
intent?.getStringExtra(EXTRA_SHORTCUT_QUEUE) == "true" ||
    intent?.getBooleanExtra(EXTRA_SHORTCUT_QUEUE, false) == true
```
And for EXTRA_SHORTCUT_PASTE the same pattern.

### Correction 3: CobaltWebView.submitUrl — use only the second version

In `CobaltWebView.kt` Task 9, delete the first `submitUrl` implementation entirely (the one containing `url2`). Keep only:
```kotlin
fun submitUrl(url: String, audioOnly: Boolean) {
    audioOnlyMode = audioOnly
    pendingUrl = url
    if (url.isNotBlank()) {
        post {
            if (progress >= 100) {
                pendingUrl = null
                injectUrl(url, audioOnly)
            }
        }
    }
}
```

---

## Sideloading Instructions (S22 Ultra)

1. On the S22 Ultra: **Settings → Apps → Special app access → Install unknown apps**
2. Enable "My Files" (or your browser) to install unknown apps
3. Transfer `app-debug.apk` to the phone (USB, Drive, or AirDrop equivalent)
4. Open the APK in My Files → tap Install
5. Grant the app permissions it requests on first launch (notifications, battery optimization)
6. Test smoke test #1: share a YouTube URL → cobalt pre-fills → file saves to Downloads/Cobalt/
