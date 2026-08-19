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
    // Dependency history (two real breaks, two different causes — see
    // FfmpegTranscoder's DEPENDENCY_NOTE KDoc for the full incident
    // writeup before changing this line):
    // 1. `com.arthenica:ffmpeg-kit-full-gpl:6.0-2` (the original
    //    coordinate) is DEAD — arthenica retired FFmpegKit and Maven
    //    Central removed all `com.arthenica:*` binaries on 2025-04-01.
    // 2. `com.antonkarpenko:ffmpeg-kit-full-gpl` (the first replacement)
    //    resolved fine but broke the build a different way — CI failed
    //    with `Unresolved reference: arthenica` on every FFmpegKit class.
    //    Root cause: that coordinate is a Flutter plugin build
    //    (github.com/sk3llo/ffmpeg-kit-flutter), not a plain Android
    //    library — it doesn't expose the public Java API this app calls,
    //    regardless of a same-named class appearing in one of its crash
    //    logs (see DEPENDENCY_NOTE for why that "verification" didn't
    //    actually verify the right thing).
    //
    // Current: `io.github.jamaismagic.ffmpeg:ffmpeg-kit-lts-full-gpl-16kb`
    // — JamaisMagic/ffmpeg-kit-16KB, a genuine from-source fork of
    // arthenica/ffmpeg-kit (not a Flutter wrapper), rebuilt for Android's
    // 16KB page-size requirement. Its android/README.md is the unmodified
    // original arthenica Android docs — confirmed real drop-in, zero
    // import changes needed. Version `6.1.7` is a best-match against this
    // group's sibling artifacts, not read directly off this exact
    // artifact's own Maven page (see DEPENDENCY_NOTE's "VERSION NOTE" for
    // what to do if this specific line fails to resolve).
    implementation("io.github.jamaismagic.ffmpeg:ffmpeg-kit-lts-full-gpl-16kb:6.1.7")
    // fallback serving leftover artifacts from a previous, different
    // dependency declaration, not evidence this pin is actually valid.
    // Always verify the exact version against Maven Central's own
    // index, not against what a CI log's task list appears to reach.

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
