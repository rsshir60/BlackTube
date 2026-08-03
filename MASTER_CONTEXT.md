# BlackTube — Master Context File (MCF)

> **Single source of truth for all AI coding sessions.**
> Last updated: 2026-08-03
> Maintained by: LO + ENI
> Version: 1.1 (Signed Release Package Complete)

---

## 0. HOW TO USE THIS FILE

Before touching a single line of code, read sections 0 through 4. Then jump to the section
relevant to your task. Keep this file updated as features ship.

Session start checklist:
1. `git status --short --branch` — confirm branch is `main` and working tree is clean.
2. `git log --oneline -5` — ground yourself in the last few commits.
3. Read §4 (Non-Negotiable Rules) before touching ServiceHelper, Tab.java, KioskTranslator.kt, or settings XML.
4. Read §11 (Known Bugs Fixed) so you don't re-introduce a crash already chased down.
5. Check §19 (Progress Tracker) to see what is already done.

---

## 1. Project Identity

| Field             | Value                                          |
|-------------------|------------------------------------------------|
| App Name          | BlackTube                                      |
| Application ID    | com.blacktube.app                              |
| Android NS        | org.schabi.newpipe (inherited, intentional)    |
| Version Code      | 1009                                           |
| Version Name      | 1.0.0-BlackTube                                |
| Min SDK           | 33 (Android 13) — deliberate scope narrowing   |
| Target SDK        | 35                                             |
| Compile SDK       | 36                                             |
| License           | GPL-3.0-or-later                               |
| Repo              | https://github.com/rsshir60/BlackTube.git      |
| Branch            | main                                           |
| Maintained by     | LO + ENI                                       |

### Positioning

BlackTube is a privacy-respecting, YouTube-only Android video client with optional AI-powered
video summaries. No ads. No analytics. No tracking. AI features are opt-in BYOK (Bring Your Own Key).

---

## 2. Fork Provenance

BlackTube is forked from PipePipe (not directly NewPipe). The NewPipe namespace is inherited.

| Item                  | Value |
|-----------------------|-------|
| Upstream              | https://github.com/InfinityLoop1308/PipePipe |
| Fork base tag         | PipePipeExtractor @ b974fde |
| Extractor             | PipePipeExtractor @ b974fde |
| Extractor commit      | b974fde (Git submodule setup) |
| Extractor setup       | Local Git submodule included build via settings.gradle.kts -> ./NewPipeExtractor/ |

WARNING: NewPipeExtractor/ is a Git submodule (`.gitmodules`). Track submodule status with `git submodule status`.

Key recent commits:
  a61924c  chore: convert NewPipeExtractor to git submodule (PipePipeExtractor @ b974fde) and add F-Droid metadata
  8e93a6d  chore: prep F-Droid submission (conditional signing, metadata, screenshots)
  3949c8b  docs: add app screenshots to README
  0378c55  Fix 'What's New' feed refresh bug
  aaf3d5e  feat: complete AI prompt integration and UI polish

---

## 3. Product Principles (Immutable)

1. YouTube only. ServiceHelper.getSelectedServiceId() must always return 0. No exceptions.
2. Privacy first. No ads, no analytics, no tracking added by BlackTube.
3. AI is opt-in BYOK. No developer-owned Gemini key bundled. No background calls.
4. Narrow additions over broad rewrites. Treat NewPipe/PipePipe behavior as the stable base.
5. Never commit API keys. Not in code, not in strings.xml, not in gradle.properties.
6. Local storage and user control. Prefer on-device over server-side.
7. Predictability over novelty. A polished open-source app is remembered less for flashy features than for how predictable, consistent, and stable it feels. Every screen must look intentional, and every common action must work without surprises.

---

## 4. Non-Negotiable Rules (Real Bugs Already Hit)

Breaking any of these re-introduces a known crash or product violation.

1. ServiceHelper.getSelectedServiceId() must ALWAYS return 0 (YouTube). Never read a stored pref
   that could resolve to a removed service.

2. Guard getTabIconRes() with > 0 before calling setIcon(). Both FeedGroupTab and KioskTab have
   shipped crashes from deserialized icon ID = 0. Guards live in MainFragment.java,
   ChooseTabsFragment.java, Tab.java — do not remove while refactoring.

3. YouTube kiosk ID in PipePipeExtractor is "Recommended Lives", not "Trending".
   KioskTranslator.kt must map this explicitly or it throws.

4. R8 minification is on. Keep -keepattributes SourceFile,LineNumberTable in proguard-rules.pro.

5. Gemini network calls on Dispatchers.IO. UI updates on Dispatchers.Main. Do not mix in ViewModels.

6. Compose AI UI must pull colors from existing AMOLED theme tokens. No hardcoded hex values.

7. Do NOT re-register removed services (Bilibili, SoundCloud, MediaCCC, PeerTube, Bandcamp,
   Niconico) through any extractor merge or update. Check ServiceList.java after any upstream sync.

8. AI JSON from Gemini can fail to parse. The fallback path must remain intact. Never remove it.

9. Be cautious reintroducing Material Chip or newer Material components in older inherited XML
   layouts — prior breakage on record.

---

## 5. Repository Layout

```
BlackTube/
  app/
    build.gradle.kts            <- app-level build config (versions, deps, signing)
    proguard-rules.pro
    lint.xml
    src/
      main/
        AndroidManifest.xml
        java/
          com/blacktube/app/
            ai/                 <- BlackTube AI package (Gemini, Prompts)
            player/             <- BlackTube player additions (SleepTimer)
          org/schabi/newpipe/   <- Inherited NewPipe/PipePipe codebase
            App.kt
            MainActivity.java
            RouterActivity.java
            fragments/
              detail/           <- VideoDetailFragment, AiSummaryFragment
              list/
            player/             <- Player, PlayerService, gesture, mediasession
            settings/           <- All settings fragments incl. AiFeaturesSettingsFragment
            local/              <- Subscriptions, feed, playlists, history
            download/           <- Download manager
            database/           <- Room database
            util/               <- ServiceHelper, KioskTranslator, SponsorBlockHelper, etc.
        res/
      debug/
      test/
      androidTest/
  NewPipeExtractor/             <- Nested Git repo — local extractor (PipePipeExtractor fork)
  buildSrc/                     <- Gradle build logic helpers
  gradle/
    libs.versions.toml          <- Version catalog
  assets/                       <- README icon, screenshots
  checkstyle/
  fastlane/
  doc/
  settings.gradle.kts           <- includeBuild for local extractor
  build.gradle.kts              <- Root Gradle
  gradle.properties
  release.keystore              <- WARNING: DO NOT COMMIT to public repos
  MASTER_CONTEXT.md             <- THIS FILE
  blueprint.md                  <- Architecture blueprint (detailed)
  claude.md                     <- AI session memory (older, superseded by this MCF)
  README.md                     <- Public-facing README
```

Scratch files (do NOT commit): dump_streaming.py, test_output.txt, test2_output.txt,
extractor_downloader.java, upstream_downloader.java, pipepipe_downloader.java, scratch/

---

## 6. Build System

| Tool                    | Version     |
|-------------------------|-------------|
| Kotlin                  | 2.3.10      |
| Android Gradle Plugin   | 8.13.2      |
| Java Toolchain          | JDK 17      |
| KSP                     | 2.3.6       |
| ktlint                  | 1.8.0       |
| Checkstyle              | 13.2.0      |

### Commands (Windows PowerShell)
```
.\gradlew.bat assembleDebug       # debug APK -> app/build/outputs/apk/debug/
.\gradlew.bat assembleRelease     # release APK -> app/build/outputs/apk/release/
.\gradlew.bat installDebug        # install on connected device
.\gradlew.bat clean
.\gradlew.bat test                # unit tests
.\gradlew.bat lint                # lint check
```

### Debug vs Release
- Debug:   applicationId = com.blacktube.app.debug   app_name = "BlackTube Debug"
- Release: applicationId = com.blacktube.app          app_name = "BlackTube"

### Local Extractor Wiring (settings.gradle.kts)
```kotlin
includeBuild("./NewPipeExtractor") {
    dependencySubstitution {
        substitute(module("com.github.TeamNewPipe:NewPipeExtractor"))
            .using(project(":extractor"))
    }
}
```

### Build Features Enabled
- View Binding
- BuildConfig
- Resource Values
- Jetpack Compose (enabled; most UI still XML/View-based)

NOTE: Pre-build checks (checkstyle, ktlint) are currently commented out for faster iteration.
Re-enable before any release via the afterEvaluate block in app/build.gradle.kts.

---

## 7. Key Source Files — Quick Reference

### BlackTube-Owned Files (com.blacktube.app)
| File | Purpose |
|------|---------|
| com/blacktube/app/ai/GeminiSummarizer.kt      | Gemini API calls, transcript handling, summary cache |
| com/blacktube/app/ai/PromptLibrary.kt         | Prompt storage, categories, active prompt |
| com/blacktube/app/ai/PromptLibraryActivity.kt | Prompt library UI activity |
| com/blacktube/app/ai/PromptLibraryAdapter.kt  | RecyclerView adapter for prompts |
| com/blacktube/app/ai/PromptEditorDialog.kt    | Prompt create/edit dialog |
| com/blacktube/app/player/SleepTimerManager.kt | Sleep timer feature |

### Critical Inherited Files (Modified by BlackTube)
| File | Why It Matters |
|------|---------------|
| org/.../App.kt                              | App entry — BlackTube init hooks |
| org/.../MainActivity.java                   | Launcher activity, nav drawer |
| org/.../util/ServiceHelper.kt               | Locked to YouTube (Service ID = 0) |
| org/.../util/KioskTranslator.kt             | Maps "Recommended Lives" kiosk ID |
| org/.../util/SponsorBlockHelper.java        | SponsorBlock segment logic |
| org/.../util/ThirdPartyApiHelper.java       | RYD API integration |
| org/.../fragments/detail/VideoDetailFragment.java | Main video page — AI button, RYD |
| org/.../fragments/detail/AiSummaryFragment.kt     | AI summary tab UI |
| org/.../fragments/MainFragment.java         | Tab icon guard (Rule #2) |
| org/.../settings/AiFeaturesSettingsFragment.kt    | AI settings screen |
| org/.../settings/SponsorBlockSettingsFragment.java | SponsorBlock settings |
| org/.../settings/ReturnYouTubeDislikeSettingsFragment.java | RYD settings |
| org/.../player/Player.java                  | Core player (~114KB — touch carefully) |
| org/.../player/PlayerService.java           | Media3 MediaSessionService |

---

## 8. AI Summary System — Full Spec

Package: com.blacktube.app.ai

### Gemini Model Configuration
| Setting           | Value                    |
|-------------------|--------------------------|
| Model             | gemini-3.1-flash-lite    |
| Temperature       | 0.2                      |
| Max Output Tokens | 4096                     |
| Response MIME     | application/json         |
| API Key Storage   | EncryptedSharedPreferences, key: gemini_api_key |

IMPORTANT: Model string is in ONE place only: GeminiSummarizer.kt. Route all references through
that constant. Gemini model lines have changed without warning before.

### User Flow
1. User opens video detail page.
2. Taps AI summary tab -> AiSummaryFragment loads.
3. Fragment checks if Gemini API key is configured.
4. GeminiSummarizer checks cache (blacktube_ai_cache, key: summary_{videoId}_v3).
5. On cache miss: fetches transcript (prefer English; fallback to first available; limit 8000 chars).
6. Builds prompt: active PromptLibrary prompt OR default structured JSON prompt.
7. Calls Gemini API on Dispatchers.IO.
8. Parses JSON response -> fallback to Markdown text on parse failure.
9. Renders in UI on Dispatchers.Main. Writes to cache (TTL: 7 days).
10. Incognito mode: cache reads allowed, writes disabled.

### Structured Summary Schema
{
  "title": "",
  "channel": "",
  "category": "",
  "categoryEmoji": "",
  "corePurpose": "",
  "vibeEmoji": "",
  "culturalImpact": "",
  "chapters": [
    { "startSeconds": 0, "endSeconds": 120, "summary": "", "emoji": "" }
  ]
}

### Cache
| Key                   | Value |
|-----------------------|-------|
| Preference file       | blacktube_ai_cache |
| Key pattern           | summary_{videoId}_v3 |
| TTL                   | 7 days |
| Incognito write       | Disabled |

### Error Handling (Required)
| Error                      | Required Behavior |
|----------------------------|-------------------|
| Missing/invalid API key    | Inline error state in AI card, not a crash |
| Rate limit (429)           | Back off, show "try again in a moment", no retry loop |
| Timeout                    | Cap request time — spinner that never resolves is worse than no feature |
| Empty/malformed transcript | Skip AI call entirely |
| JSON parse failure         | Fall back to Markdown — NEVER remove this path |

---

## 9. Prompt Library System

### Storage
| Key               | Value |
|-------------------|-------|
| Preference file   | blacktube_prompt_library |
| user_prompts      | User-created/duplicated prompts (JSON) |
| favorites         | Set of favorited prompt IDs |
| active_prompt_id  | Currently selected prompt ID |

### Built-In Categories
YouTube, Documents, Code, Research, Writing, Language, Learning

### Key Layouts
res/layout/activity_prompt_library.xml
res/layout/item_prompt_card.xml
res/layout/item_prompt_category_header.xml
res/layout/dialog_prompt_editor.xml

---

## 10. Settings Architecture

### AI Settings
settings/AiFeaturesSettingsFragment.kt
res/xml/ai_features_settings.xml
res/layout/preference_ai_status_header.xml
res/layout/preference_ai_connection_status.xml
res/layout/preference_ai_active_prompt.xml

Controls: enable/disable AI, API key entry, status display, active prompt, Prompt Library entry, clear cache.

### SponsorBlock Settings
settings/SponsorBlockSettingsFragment.java
settings/SponsorBlockCategoriesSettingsFragment.java
res/xml/sponsor_block_settings.xml
res/xml/sponsor_block_category_settings.xml

### Return YouTube Dislike Settings
settings/ReturnYouTubeDislikeSettingsFragment.java
res/xml/return_youtube_dislikes_settings.xml

### Settings Keys & Strings
res/values/settings_keys.xml    <- preference key constants
res/values/strings.xml          <- all user-facing strings

---

## 11. Known Bugs Fixed (Changelog)

| Bug | Fix Applied |
|-----|-------------|
| Resource ID #0x0 crash on tab icons | Null-safe guards in MainFragment.java, ChooseTabsFragment.java, Tab.java |
| PipePipe kiosk "Recommended Lives" unmapped -> crash | Added mapping in KioskTranslator.kt |
| Media notification had no transport controls | Wired PlaybackState actions to MediaSessionCompat in MediaSessionPlayerUi |
| AMOLED theme not enforced | Locked ThemeHelper to always return black theme |
| Material Chip crash in AI summary UI | Replaced Chip with compatible component (commit 9798802) |
| SponsorBlock/RYD wiring broken | Restored (commit f6c8253) |
| 'What's New' feed refresh bug | Fixed feed updating and sync issue (commit 0378c55) |
| NullPointerException on empty subscriptions tab | Fixed ViewBinding mismatch in ImportSubscriptionsHintPlaceholderItem.kt by binding ListEmptyViewSubscriptionsBinding |
| RecyclerView scroll jank & frame drops | Enabled setHasFixedSize(true) and setItemViewCacheSize(20) in BaseListFragment.java |
| AI Summary UX details & copy button | Added Copy Summary action, aligned button bar, smooth scrolling, and enhanced loading animation |
| Settings screen layout & grouping | Restructured main_settings.xml into 4 PreferenceCategory sections with descriptions, aligned icons, and non-clipping titles |
| Alignment & micro-spacing audit | Fixed 4dp/8dp margin mismatch on prompt card buttons and verified edge padding & icon alignment across all core layouts |
| Final Visual Consistency Audit | Standardized 12dp card corner radii, 24dp pill action buttons, 24dp icon frames, and equal card spacing across all 8 core app screens |
| Background task race condition fix | Added summarizeJob tracking and cancellation in AiSummaryFragment.kt to prevent concurrent request overwrites |
| API Reliability Audit & Timeouts | Configured explicit OkHttp connect/read/write timeouts (15s/30s/15s) in DownloaderImpl.java & 30s coroutine timeout in GeminiSummarizer.kt |
| Code Quality & Cleanup Audit | Audited TODO/FIXME/HACK flags; replaced raw e.printStackTrace() with Log.e(TAG, ...) in Player.java |
| Final 20-Point Regression Testing | Verified all 12 core features (Home feed, Search, Video, Background, Downloads, AI, SponsorBlock, RYD, Settings, Notifications, Rotation, Offline) |

Add new entries here as bugs are found and fixed.

---

## 12. Dependency Map

### AI
com.google.ai.client.generativeai:generativeai:0.9.0

### Media Playback (Media3 v1.3.1)
androidx.media3:media3-exoplayer
androidx.media3:media3-exoplayer-dash / hls / smoothstreaming
androidx.media3:media3-ui
androidx.media3:media3-session
androidx.media3:media3-database / datasource

### Core AndroidX
appcompat:1.7.1, fragment:1.8.9, preference:1.2.1
recyclerview:1.4.0, room:2.7.2, work-runtime
lifecycle-livedata/viewmodel, security-crypto, webkit

### Networking & Parsing
OkHttp:5.3.2, Jsoup:1.22.1, NanoJSON
NewPipeExtractor (local PipePipeExtractor @ 1c43d28)

### UI
material:1.11.0, constraintlayout:2.2.1, viewpager2
groupie:2.10.1, coil:3.3.0, markwon:4.6.2
Compose BOM:2024.06.00 + material3 + activity-compose:1.9.0

### Async
RxJava3:3.1.12, RxAndroid:3.0.2, RxBinding:4.0.0
Kotlin Coroutines (Gemini calls)

### Debug Only
LeakCanary:2.14, Stetho:1.6.0

---

## 13. App Icon Assets

| Asset             | Path |
|-------------------|------|
| Background layer  | C:\Users\SUPRIYA\Desktop\BlackTube\app_icon\background.png |
| Foreground layer  | C:\Users\SUPRIYA\Desktop\BlackTube\app_icon\foreground.png |
| Design reference  | C:\Users\SUPRIYA\Desktop\BlackTube\app_icon\icon_readme.png |

- Format: Adaptive icon (ic_launcher_background + ic_launcher_foreground)
- Target densities: mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi + -round variants
- Monochrome (Android 13+): ic_launcher_monochrome — single path, fill #FFFFFF
- Always DOWNSCALE from source PNGs. Never scale up from smaller density.
- Use generate_icons.py in scratch dir for batch resizing.

---

## 14. Design System

| Token         | Value |
|---------------|-------|
| Theme         | AMOLED Black (true #000000 background) |
| Light Mode    | Disabled — always black (Theme preference removed from settings) |
| Accent        | YouTube Red #FF0000 or softer #E53935 |
| Font          | JetBrains Mono (bundled `.ttf` in `res/font/`) |
| Card Radius   | 12dp (standardized across containers & status cards) |
| Button Radius | 24dp (pill action style) / 8dp (input shapes) |
| Components    | Material 3 (Material You where available) |

### UI Mix
- XML Layouts — inherited player, navigation, detail views
- Preference XML — settings screens
- View Binding — bridging XML to code
- Jetpack Compose — AI summary card, prompt library panels

### BlackTube-Specific Drawables
res/drawable/bg_ai_chip.xml
res/drawable/bg_ai_chip_active.xml
res/drawable/bg_ai_status_active.xml
res/drawable/bg_ai_status_disabled.xml
res/drawable/bg_ai_status_error.xml

---

## 15. Remote API Surface

| API | Trigger | Privacy Note |
|-----|---------|-------------|
| YouTube (via extractor) | Any video/channel load | No login required by default |
| SponsorBlock API | Video load when enabled | Sends video ID |
| Return YouTube Dislike API | Video load when enabled | Sends video ID |
| Google Gemini API | User-triggered "Summarize" tap | Sends title, description, transcript — user's own key |

BlackTube does NOT have its own server. All remote calls go directly to listed third-party endpoints.

---

## 16. Privacy & AI Contract

- AI is opt-in. Off by default.
- First use shows one-time disclosure: what data leaves the device, that it goes to Google.
- No automatic/background AI calls — every call is user-triggered.
- Cache per video ID to minimize repeat API calls.
- Comment Sentiment Summary and Search Query Enhancement are lowest priority (most call-heavy).

---

## 17. Permissions (AndroidManifest.xml)

INTERNET, WAKE_LOCK, ACCESS_NETWORK_STATE, WRITE_EXTERNAL_STORAGE (compat),
SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK,
FOREGROUND_SERVICE_DATA_SYNC, POST_NOTIFICATIONS

---

## 18. Engineering Risks & Gotchas

| Risk | Mitigation |
|------|-----------|
| NewPipeExtractor/ nested Git repo appearing dirty | Never git add . blindly — check git status |
| Mojibake in older source comments | Prefer UTF-8; audit before adding Unicode |
| Scratch files (test_output.txt, dump_streaming.py, etc.) | Never commit |
| Service lock breaking upstream tabs/kiosk assumptions | Test home feed + drawer after service/kiosk/tab changes |
| Gemini JSON parse failure | Fallback path is non-negotiable (Rule #8) |
| Material Component compat in inherited XML layouts | Test AI/settings UI after any Material version bump |
| minSdk 33 cuts large share of Android devices | Confirm target audience before release |
| Gemini model deprecation — happens without much warning | Model string in ONE constant: GeminiSummarizer.kt |

---

## 19. Feature Roadmap & Progress

### Implemented
- [x] BlackTube fork, package rename (com.blacktube.app)
- [x] YouTube-only service lock (ServiceHelper.kt)
- [x] AMOLED black theme enforced
- [x] SponsorBlock wiring (settings + skip logic)
- [x] Return YouTube Dislike wiring (settings + display)
- [x] Gemini AI summarizer (GeminiSummarizer.kt)
- [x] AI summary UI (AiSummaryFragment.kt)
- [x] Prompt Library (full CRUD + categories)
- [x] AI settings screen (AiFeaturesSettingsFragment.kt)
- [x] Summary cache (7-day TTL, incognito-aware)
- [x] Structured JSON + Markdown fallback rendering
- [x] Extractor submodule conversion (NewPipeExtractor Git submodule @ b974fde)
- [x] F-Droid submission prep (fdroid_metadata.yml & conditional signing)
- [x] App screenshots & documentation update
- [x] Fix 'What's New' feed refresh bug
- [x] Sleep timer (SleepTimerManager.kt)
- [x] Release keystore + signing config

### Pending / In Progress
- [ ] App icon — finalize adaptive icon across all density buckets
- [ ] Show Original Titles toggle (show_original_title pref)
- [ ] Smart Chapter Detection (AI-generated chapters when none exist)
- [ ] Comment Sentiment Summary (opt-in, lowest-priority AI feature)
- [ ] Search Query Enhancement (opt-in, lowest-priority)
- [ ] Live Chat Danmaku overlay
- [ ] AV1/VP9 codec priority setting (Media3 track selector)
- [ ] Batch playlist download
- [ ] Formal contributor guide
- [ ] Release signing documentation (move passwords out of build.gradle.kts)
- [ ] Unit tests for prompt persistence and summary fallback parsing
- [ ] Mojibake audit — normalize encoding across older source files

---

## 20. Signing & Release

Release keystore: release.keystore (project root — DO NOT commit to public repos)

Signing config in app/build.gradle.kts:
  storeFile = file("../release.keystore")
  storePassword = "android"
  keyAlias = "release"
  keyPassword = "android"

WARNING: Passwords are hardcoded — move to local.properties or env vars before public distribution.

### Release Checklist
- [ ] Verify versionCode and versionName
- [ ] Confirm extractor source commit
- [ ] Run .\gradlew.bat test lint assembleRelease
- [ ] Re-enable pre-build checks (checkstyle, ktlint) in app/build.gradle.kts
- [ ] Document AI feature status, known third-party API behavior, any breaking changes
- [ ] Publish APK to GitHub Releases

---

## 21. Glossary

| Term | Definition |
|------|-----------|
| RYD | ReturnYouTubeDislike — restores hidden dislike counts via third-party API |
| SponsorBlock | Community-driven API for skipping sponsored/filler segments |
| BYOK | Bring Your Own Key — user provides their own Gemini API key |
| AMOLED black | True #000000 background, saves OLED battery |
| Danmaku | Scrolling bullet-comment overlay (Niconico/Bilibili style) |
| SABR | YouTube playback-authorization protocol; PipePipe patches around it |
| PipePipeExtractor | The extractor library (PipePipe fork of NewPipeExtractor) used by BlackTube |
| Media3 | AndroidX successor to ExoPlayer — what BlackTube uses for playback |
| MCF | This file — Master Context File |

---

Keep this file updated. An outdated MCF is worse than no MCF.
