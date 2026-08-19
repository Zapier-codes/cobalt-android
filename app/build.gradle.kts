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
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug { isDebuggable = true }
        release {
            isMinifyEnabled = false
            // proguardFiles not needed while minification is disabled
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("androidx.vectordrawable:vectordrawable:1.2.0")

    // --- Phase 2 (Shorts feed) additions ---
    // ViewPager2 backs the vertical swipeable Shorts feed. This was already
    // used in fragment_shorts.xml before this phase but the dependency itself
    // was missing, so the module did not actually compile.
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    // Media3/ExoPlayer plays the real per-item video streams (progressive,
    // HLS and DASH — Innertube/Invidious return a mix of these) in the
    // TikTok/Shorts-style feed. VideoView (previously in item_short_video.xml)
    // cannot handle HLS/DASH so it's replaced by androidx.media3.ui.PlayerView.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    // NewPipeExtractor is one of the three merged Shorts sources (the other
    // two — direct Innertube calls and Invidious public instances — use
    // OkHttp directly and need no extra dependency). NewPipeExtractor needs a
    // JSON parser (nanojson) and a JS engine for YouTube's cipher (Rhino) as
    // transitive dependencies, both pulled automatically from its POM.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.24.6")

    // --- Phase 15 (FFmpeg-based dynamic quality/format transcoding) ---
    // `full-gpl` (not `full` or `min-gpl`) is the only prebuilt ffmpeg-kit
    // variant that bundles x264/x265 (H.264/H.265 encode — needed for
    // TranscodeProfile.ALL_VIDEO's MP4 tiers) alongside lame/opus/vorbis/
    // flac (all already free of GPL entanglement on their own, bundled
    // here anyway since full-gpl is a superset). Picking a smaller variant
    // would silently drop the H.264 video tiers, which are the ones most
    // players actually support.
    //
    // `com.arthenica:ffmpeg-kit-full-gpl:6.0-2` (the original coordinate)
    // is DEAD — arthenica retired FFmpegKit and Maven Central removed all
    // `com.arthenica:*` binaries on 2025-04-01; the CI failure that led
    // here was `Could not find com.arthenica:ffmpeg-kit-full-gpl:6.0-2`,
    // not a transient resolution issue. See FfmpegTranscoder's
    // DEPENDENCY_NOTE KDoc for the full incident writeup.
    //
    // Replaced with `com.antonkarpenko:ffmpeg-kit-full-gpl` (sk3llo's
    // actively-maintained fork, github.com/sk3llo/ffmpeg_kit_flutter —
    // underscores, not hyphens; the hyphenated form is a dead link, see
    // FfmpegTranscoder's DEPENDENCY_NOTE for how this was confirmed —
    // 126+ stars, real users, releases through Jul 2026), rebuilt against
    // FFmpeg v8.1.1 (vs the dead original's 6.0). Verified before pinning:
    // (1) POM declares dual GPL-3.0/LGPL-3.0 licensing and the README
    // explicitly lists x264/x265/vid.stab/xvidcore as included GPL
    // libraries — confirms this IS the true full-gpl variant, not a
    // stripped-down "min" rebrand; (2) a real production crash log from
    // this fork's own issue tracker (sk3llo/ffmpeg_kit_flutter#71) shows
    // a stack trace running through `com.arthenica.ffmpegkit.NativeLoader`
    // and `com.arthenica.ffmpegkit.FFmpegKitConfig` — proof the fork kept
    // the ORIGINAL Java package namespace and only republished under new
    // Maven coordinates, so this is a true drop-in swap: zero import
    // changes needed anywhere in this codebase.
    implementation("com.antonkarpenko:ffmpeg-kit-full-gpl:2.2.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
