# Cloudly: decisions and conventions

Cloudly is an Android app that shows a live word cloud on a TV behind podcast hosts. Hosts type words
on the phone, and each word grows as the phone hears it mentioned. Keep future work consistent with this file.

## Non-negotiables
- **Everything runs on the phone.** No cloud services, no API keys, no network calls, except the one-time
  Whisper model download (Milestone 2). Do not add analytics, crash reporting or Google Play Services fonts.
- **The transcript is in memory only.** Never write it to disk, logs or saved episodes. Clear it when the session
  ends or on Clear all. Saved episodes and the autosave (`current_words.json`) contain words, variants, counts
  and hidden flags only.
- **Pinch emphasis is display only.** It never changes counts.
- Kotlin + Jetpack Compose, minSdk 29, compileSdk/targetSdk 35. applicationId `io.github.tobijeh.cloudly`
  (never change it, or updates stop installing). Debug builds use the `.debug` suffix.

## Architecture
- `core/` is pure Kotlin with no Android imports, and is fully unit-tested:
  - `CloudController`: every state change (add, edit, mention, tap/lock, auto-release `tick`, hide, reset,
    delete/restore, clear, load, emphasis). Clock is injected for tests.
  - `CloudLayout`: deterministic layout. The centre word is at `CENTRE_HEIGHT_FRACTION` (20%) of viewport height,
    whatever its count (shrunk only if too wide). Other words get `relativeSize` (square-root of count / max count,
    22–60% of centre size) and are packed on an elliptical spiral (aspect = screen aspect) with ~10% gaps, no
    overlap. If the cloud doesn't fit, a binary search scales **all surrounding words together** (the centre stays
    fixed) until it does. Each word's spiral starts at an angle derived from its id, which keeps positions stable.
  - `Scripts`: font choice. Any Han character → Noto Serif TC, any Hebrew → Frank Ruhl Libre, else Fraunces.
- `ui/CloudView`: measures each word once at a 100sp reference size, then draws scaled, so animating size never
  re-measures. Movement: 2000 ms tween with a calm ease (`MOVE_DURATION_MS`). New words fade and grow in place.
  Emphasis follows the fingers, then springs back (damping 0.55, stiffness 40, about 1 s).
  `TextDirection.Content` handles Hebrew RTL and vowel points.
- `AppGraph` (in `CloudlyApp`) holds the singletons shared by the phone UI and the TV Presentation: the controller,
  settings (SharedPreferences), the episode store (JSON in filesDir/episodes), a 1 s auto-release ticker, and a
  debounced autosave.
- `tv/TvPresenter`: Android Presentation API. When a `DISPLAY_CATEGORY_PRESENTATION` display exists, the TV shows
  only `TvCloud` and the phone shows controls. With no presentation display, the overflow menu offers
  "Full-screen cloud (mirroring)": the phone shows the cloud full screen in landscape (`SENSOR_LANDSCAPE`,
  immersive), with controls in a pull-out `ModalNavigationDrawer`. A connected presentation display overrides
  mirror mode.
- Keep-awake: `FLAG_KEEP_SCREEN_ON` while a presentation display is showing, while mirroring, or while listening.

## Behaviour decisions
- Centre = locked word, else the most recently mentioned word, else the most recently active visible word.
- Tap a word (list row or preview) to lock it in the centre. Only the locking tap counts as a mention (when
  "count taps as mentions" is on, the default); the release tap does not. Tapping another word moves the lock.
  "Auto" or tapping again releases. After release, the released word stays in the centre until the next mention.
- Auto-release options: Off / 1 / 2 / 5 min (default Off). Relocking restarts the timer.
- Visible words: the centre plus the top N−1 non-hidden words by count, then by recency. Default 20, range 10–30.
- Word list order: most recent activity (add or mention) first.
- Adding a word whose display text matches an existing word (case-insensitive) returns the existing word.
  Variants are trimmed, de-duplicated, and exclude the display text (which is always listened for).
- Delete shows an Undo snackbar for 5 s (`UNDO_WINDOW_MS`). Undo restores the same id, count, variants and lock.
- Loading an episode asks "With counts" or "Words only", and replaces the current words.
- Hidden words keep their count. Hiding a locked word releases the lock.

## Fonts (bundled, OFL; licences in `licenses/`)
Fraunces (variable: wght 500, opsz 72, SOFT 50), Frank Ruhl Libre (wght 500), Noto Serif TC (wght 500, ~16 MB:
the main APK size cost). Background `#0E1014`. Soft per-word colours from `wordColor(id)`.

## Milestones
1. Display and manual controls (done).
2. Listening: whisper.cpp via JNI (NDK), VAD gating so silence never reaches Whisper, 5–10 s chunks, initial
   prompt = word list + variants. Normalise chunks (lowercase; `android.icu.text.Transliterator` "Hans-Hant"),
   whole-word match for spaced scripts, substring match for Chinese. Each match → `CloudController.mention`.
   Backfill on add/edit from the in-memory session transcript. Models are downloaded on first use, not bundled.
   Small English by default; multilingual is selectable.
3. Teach button, languages, full settings (see the original brief in the first PR).

## Workflow
- One branch per milestone → PR → CI (`.github/workflows/ci.yml`: unit tests + debug build) → squash auto-merge.
- Pushing to `main` runs `release.yml`: tests, signed release APK (`versionCode` = run number,
  `versionName` = `cloudlyVersionPrefix` + `.` + run number), and a GitHub Release `v<versionName>` with the APK
  attached (for Obtainium). Signing secrets: `CLOUDLY_KEYSTORE_BASE64`, `CLOUDLY_KEYSTORE_PASSWORD`,
  `CLOUDLY_KEY_ALIAS`, `CLOUDLY_KEY_PASSWORD`. Bump `cloudlyVersionPrefix` per milestone (0.1 → 0.2 …).
- Run `./gradlew testDebugUnitTest` before pushing. Add regression tests in `app/src/test` for any core change.
