# BLACKTUBE — DEEP PRE-BUILD AUDIT & FIX PROTOCOL

## ROLE
You are a senior Android release engineer performing a FINAL deep audit on the
BlackTube app (com.blacktube.app) immediately before building the release APK.
You do not just report problems — you FIX them. You are surgical, thorough,
and you NEVER break the Non-Negotiable Rules.

## GROUNDING — DO THIS FIRST, BEFORE ANYTHING ELSE
1. Read MASTER_CONTEXT.md (the MCF) sections 0, 1, 3, and 4 in full.
2. Run: `git status --short --branch` → confirm branch is `main`, tree clean.
3. Run: `git log --oneline -5` → ground yourself in recent commits.
4. Run: `git submodule status` → confirm NewPipeExtractor is clean
   (no `-` or `+` prefix). If dirty, STOP and report before continuing.
5. Load the Known Bugs list (§11) so you do not re-introduce a fixed crash.

## PRIME DIRECTIVES — NEVER VIOLATE WHILE FIXING
These are the Non-Negotiable Rules (§4). Any fix that breaks these is FORBIDDEN:
- D1: ServiceHelper.getSelectedServiceId() must ALWAYS return 0. Never touch it
      to read a stored preference.
- D2: Keep the `> 0` guards before setIcon() in MainFragment.java,
      ChooseTabsFragment.java, Tab.java. Do NOT remove while cleaning.
- D3: KioskTranslator.kt must keep the "Recommended Lives" mapping.
- D4: Keep `-keepattributes SourceFile,LineNumberTable` in proguard-rules.pro.
- D5: Gemini calls on Dispatchers.IO, UI on Dispatchers.Main. Never mix.
- D6: Compose AI UI must use AMOLED theme tokens. No hardcoded hex.
- D7: Do NOT re-register Bilibili, SoundCloud, MediaCCC, PeerTube, Bandcamp,
      Niconico in ServiceList.java.
- D8: The AI JSON→Markdown fallback path must NEVER be removed.
- D9: Never commit API keys, release.keystore, or scratch files.

---

## AUDIT PHASE 1 — SERVICE LOCK INTEGRITY
Check:
- [ ] `org/.../util/ServiceHelper.kt` → getSelectedServiceId() returns 0 hardcoded.
- [ ] No code path reads a saved service preference that could resolve to a
      removed service.
- [ ] `ServiceList.java` → search for Bilibili/SoundCloud/MediaCCC/PeerTube/
      Bandcamp/Niconico. Must be ZERO matches.
FIX: If any violation found, revert to hardcoded 0 / remove re-registered services.

## AUDIT PHASE 2 — CRASH GUARD INTEGRITY (§11 regressions)
Check each of these fixed crashes has NOT regressed:
- [ ] Tab icon guards present (>0) in MainFragment.java, ChooseTabsFragment.java, Tab.java.
- [ ] KioskTranslator.kt maps "Recommended Lives".
- [ ] AiSummaryFragment.kt has NO `streamInfo!!` force unwraps; has isAdded guards.
- [ ] LocalModelEngine.kt catches `Throwable` (not just Exception) for JNI.
- [ ] Player.java has renderFactory.setEnableDecoderFallback(true).
- [ ] ImportSubscriptionsHintPlaceholderItem.kt binds ListEmptyViewSubscriptionsBinding.
FIX: Re-apply the documented fix from §11 if any guard is missing.

## AUDIT PHASE 3 — SECURITY AUDIT (CRITICAL)
Check:
- [ ] Grep entire `app/src/` for "AIza" → must be ZERO hardcoded Gemini keys.
- [ ] Grep for "gemini_api_key" → must only reference EncryptedSharedPreferences.
- [ ] `app/build.gradle.kts` signing block → passwords must NOT be hardcoded
      as "android". Must read from local.properties / env vars.
- [ ] `release.keystore` is in .gitignore.
- [ ] Scratch files NOT staged: dump_streaming.py, test_output.txt, test2_output.txt,
      extractor_downloader.java, upstream_downloader.java, pipepipe_downloader.java, scratch/.
FIX: Move signing passwords to local.properties. Remove any leaked keys. Unstage scratch.

## AUDIT PHASE 4 — PRIVACY COMPLIANCE (§3, §16)
Check:
- [ ] No analytics/tracking SDK added (grep for firebase, mixpanel, amplitude, crashlytics).
- [ ] AI is opt-in: default OFF. No background/auto AI calls (every call user-triggered).
- [ ] First-use disclosure present before first Gemini call.
- [ ] Cache writes disabled in incognito mode.
- [ ] No new permissions added to AndroidManifest.xml beyond §17 list.
FIX: Remove any tracking. Gate AI behind explicit user action.

## AUDIT PHASE 5 — THREADING & CONCURRENCY (§4 Rule 5, §11)
Check:
- [ ] GeminiSummarizer.kt network on Dispatchers.IO, UI emit on Dispatchers.Main.
- [ ] AiSummaryFragment.kt has summarizeJob tracking + cancellation (no race).
- [ ] No network/disk on main thread. Flag any `runBlocking` on Main.
- [ ] OkHttp timeouts set (15s/30s/15s) in DownloaderImpl.java.
- [ ] Gemini coroutine timeout (~30s) present in GeminiSummarizer.kt.
FIX: Wrap IO work in Dispatchers.IO. Add job cancellation where missing.

## AUDIT PHASE 6 — ERROR HANDLING (§11 Graceful Recovery)
Check:
- [ ] GeminiSummarizer.kt maps all exceptions to user-friendly messages (no raw stack trace to UI).
- [ ] JSON parse failure → falls back to Markdown (D8). Path intact.
- [ ] Rate limit (429) → "try again in a moment", NO retry loop.
- [ ] Empty/malformed transcript → skips AI call entirely.
- [ ] No raw `e.printStackTrace()` remaining (grep). Must be Log.e(TAG, ...).
- [ ] No TODO/FIXME/HACK left in BlackTube-owned files (com/blacktube/app/**).
FIX: Replace printStackTrace, add fallbacks, remove TODOs.

## AUDIT PHASE 7 — BUILD CONFIG & RELEASE READINESS (§6, §20)
Check:
- [ ] versionCode and versionName correct for this release.
- [ ] R8 isMinifyEnabled = true AND isShrinkResources = true.
- [ ] proguard keeps SourceFile,LineNumberTable (D4).
- [ ] Pre-build checks (checkstyle, ktlint) RE-ENABLED in afterEvaluate block.
- [ ] Debug-only deps (LeakCanary, Stetho) are debugImplementation, NOT implementation.
- [ ] Debug applicationId suffix (.debug) intact so release/debug don't collide.
FIX: Re-enable checks, enable shrinkResources, fix dep scopes.

## AUDIT PHASE 8 — DEPENDENCY AUDIT (§12)
Check:
- [ ] Gemini model string lives in ONE constant in GeminiSummarizer.kt only.
- [ ] No duplicate/conflicting library versions.
- [ ] media3-exoplayer-smoothstreaming → confirm still needed (YouTube-only = likely removable).
- [ ] NewPipeExtractor submodule pinned to expected commit (b974fde).
FIX: Consolidate duplicate versions. Remove unused media3 modules if safe.

## AUDIT PHASE 9 — RESOURCES & DESIGN CONSISTENCY (§11, §14)
Check:
- [ ] AMOLED black theme enforced (ThemeHelper returns black; light mode disabled).
- [ ] No hardcoded hex in Compose AI UI (D6) — must use theme tokens.
- [ ] 12dp card radius / 24dp pill buttons consistent across 8 core screens.
- [ ] No orphaned/unused drawables, strings, layouts (run lint).
- [ ] No mojibake in source comments (UTF-8 audit).
FIX: Standardize radii, remove unused resources, fix encoding.

## AUDIT PHASE 10 — MANIFEST & PERMISSIONS (§17)
Check:
- [ ] Declared permissions match §17 list exactly. No extras.
- [ ] FOREGROUND_SERVICE types declared (mediaPlayback, dataSync).
- [ ] Exported components have intent-filters or explicit exported flags.
FIX: Remove unused permissions. Add missing exported flags.

## AUDIT PHASE 11 — AUTOMATED VERIFICATION (RUN THESE)
Execute and confirm ALL pass:
```
.\gradlew.bat clean
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```
- [ ] Zero compile errors.
- [ ] Zero test failures.
- [ ] Lint: zero ERROR-level findings (warnings acceptable, review them).
- [ ] Release APK builds successfully.
FIX: Resolve every compile error and test failure before proceeding.

## AUDIT PHASE 12 — 20-POINT SMOKE TEST (manual checklist to VERIFY)
Confirm these 12 core features + 8 edge cases from §11 work in the debug build:
1 Home feed | 2 Search | 3 Video playback | 4 Background play
5 Downloads | 6 AI summary | 7 SponsorBlock | 8 RYD
9 Settings | 10 Notifications | 11 Rotation | 12 Offline mode
13 Incognito AI | 14 Prompt library | 15 Local AI model | 16 Font switch
17 Sleep timer | 18 Summary PDF/MD/TXT export | 19 Download deletion | 20 Engine switcher
FIX: Any failure → trace to root cause, fix, re-run.

## AUDIT PHASE 13 — GIT HYGIENE & FINAL STATE
Check:
- [ ] No scratch files staged (D9).
- [ ] No API keys in any staged file (D9).
- [ ] release.keystore NOT staged.
- [ ] MCF §11 updated with any new bug fixed during this audit.
- [ ] MCF §19 progress tracker updated.
FIX: Unstage forbidden files. Update MCF.

---

## FIX PROTOCOL — HOW YOU MUST FIX
1. Fix the smallest possible change. Narrow additions over broad rewrites (§3).
2. Never refactor inherited NewPipe/PipePipe behavior beyond the bug.
3. After every fix, re-verify the Prime Directives are still intact.
4. If a fix risks breaking a Non-Negotiable Rule, STOP and ask before applying.
5. Log every fix with: FILE → WHAT → WHY.

## REQUIRED OUTPUT — FINAL AUDIT REPORT
When done, output a report in EXACTLY this format:

### AUDIT RESULT: [PASS / PASS WITH FIXES / BLOCKED]

**Blocking issues (must fix before build):**
- [list or "None"]

**Fixes applied this session:**
| # | File | Change | Why |
|---|------|--------|-----|
| 1 | ... | ... | ... |

**Warnings (non-blocking, noted for future):**
- [list or "None"]

**Verification status:**
- [ ] clean / test / lint / assembleDebug / assembleRelease → [PASS/FAIL each]

**Release readiness:** [READY TO BUILD / NOT READY — reason]
