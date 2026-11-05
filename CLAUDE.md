# Cloudly: decisions and conventions

Cloudly is an Android app that shows a live word cloud on a TV behind podcast hosts: a live reflection of the
conversation's topics, with a record of past episodes. It must be hands-free during recording: nobody should need
to watch or manage the phone. Keep future work consistent with this file.

## Non-negotiables
- **Audio never leaves the phone.** Transcription is on-device. The only network use: the one-time Whisper model
  download, and (from Milestone B) small text-only Claude Haiku calls with the host's own API key. Without a key or
  network the app runs fully in local-only mode (display + matching). No analytics, crash reporting or Play
  Services fonts.
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
  - `Matcher`: normalise (Hans→Hant via injected converter, ICU `Transliterator("Hans-Hant")` on Android; NFC;
    lowercase; Hebrew points stripped). Chinese = substring match; other scripts = whole word; Hebrew also allows
    up to two prefix letters (ו ה ב כ ל מ ש). Overlaps count once, longest term wins.
  - `SessionTranscript`: in-memory only. `CloudController.ingest(chunk)` stores and counts; `backfill(id)` sets
    count = max(current, mentions in transcript) on add, rename and Listen-for edits.
- `ui/CloudView`: measures each word once at a 100sp reference size, then draws scaled, so animating size never
  re-measures. Each word animates independently towards its own target (so a dragged word never disturbs the
  others): Calm = 2000 ms tween (`MOVE_DURATION_MS`), Follow (being dragged) = snap on the phone / slow spring on
  the TV, Push (making way for a dragged word) = spring, Drift (returning after a drag) = 3000 ms tween. New words fade and grow in place. Emphasis follows the
  fingers, then springs back (damping 0.55, stiffness 40, about 1 s). `TextDirection.Content` handles Hebrew RTL.
  Always pass `drawStyle = Fill` when drawing words so a draw style never leaks between draws of a layout.
- `AppGraph` (in `CloudlyApp`) holds the singletons shared by the phone UI and the TV Presentation: the controller,
  settings (SharedPreferences), the episode store (JSON in filesDir/episodes), a 1 s auto-release ticker, and a
  debounced autosave.
- `tv/TvPresenter`: Android Presentation API. When a `DISPLAY_CATEGORY_PRESENTATION` display exists, the TV shows
  only `TvCloud` and the phone shows controls. With no presentation display, the overflow menu offers
  "Full-screen cloud (mirroring)": the phone shows the cloud full screen in landscape (`SENSOR_LANDSCAPE`,
  immersive), with controls in a pull-out `ModalNavigationDrawer`. A connected presentation display overrides
  mirror mode.
- Keep-awake: `FLAG_KEEP_SCREEN_ON` while a presentation display is showing, while mirroring, or while listening.

## Gestures (all on the 16:9 preview, which maps 1:1 to the TV)
- Tap: lock in the centre (see below). Single taps fire after `DOUBLE_TAP_WINDOW_MS` (220 ms, shorter than
  Android's 300 ms) so they can be told apart from double-taps without moving anything on camera first.
- Double-tap: rename in place (overlay text field at the word). Keeps id, size, count and position; the old name
  becomes a Listen-for variant; then backfill. Duplicate names are refused with a snackbar.
- Hold (system long-press timeout): menu with Listen-for spellings, Hide from TV, Reset count.
- Drag (sandbox): `CloudState.pins` stores the spot as a fraction of the viewport relative to its centre.
  `CloudLayout.layout(fixed = …)` places pinned words exactly there and packs everything else around them, so
  other words get pushed out of the way while dragging (Push motion = velocity-keeping spring). After
  `driftBackMs` (5 s, owner's choice) hands-off the pin is removed in `tick()` and the word drifts home over 3 s.
  No outline/shadow (owner removed it). Pins are not persisted. Drag end runs in `finally`, so a cancelled
  gesture can never leave a word stuck as "dragging".
- Drag to bin: a bin appears at the bottom of the preview only while dragging; dropping deletes with Undo.
- Pinch out: display-only emphasis.
- Word list rows are display only. A row is tappable only when its word isn't on the cloud (hidden, or past the
  visible limit), and then opens the hold menu. Add word stays a button.
- Gesture recognition is covered by Robolectric tests (`CloudGestureTest`) using real touch injection.

## Behaviour decisions
- Centre = locked word, else the most recently mentioned word, else the most recently active visible word.
- Tap a word on the preview to lock it in the centre. Only the locking tap counts as a mention (when
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

## Milestones (redesign)
1. Display and manual controls (done, v0.1).
A. Gestures on the preview + matcher/transcript/backfill core (done, v0.2).
L. Listening: whisper.cpp via JNI (NDK), VAD gating so silence never reaches Whisper, 5–10 s chunks, initial
   prompt = word list + variants, feeding `CloudController.ingest`. Models downloaded on first use, not bundled.
   Small English by default; multilingual selectable.
B. Claude layers (Haiku, text only, key in EncryptedSharedPreferences): prep word list from notes; live topic
   suggestions every 30–60 s with guardrails (two consecutive suggestions, max one new topic per 2 min, enters
   small at the edge, block list); after-recording timeline, chapters, show notes. Failures skipped silently.
C. Archive, series background, Teach button, full settings. Episode format v2 with a migration test from v1
   (`EpisodeMigrationTest` pins the v1 format already).

## Workflow
- One branch per milestone → PR → CI (`.github/workflows/ci.yml`: unit tests + debug build) → squash auto-merge.
- Pushing to `main` runs `release.yml`: tests, signed release APK (`versionCode` = run number,
  `versionName` = `cloudlyVersionPrefix` + `.` + run number), and a GitHub Release `v<versionName>` with the APK
  attached (for Obtainium). Signing needs one secret, `CLOUDLY_KEYSTORE_BASE64`; the password
  (`cloudly-release`) and alias (`cloudly`) are fixed in `release.yml` to keep setup to a single paste for a
  non-technical owner. Never replace the keystore, or installed copies stop accepting updates. Bump `cloudlyVersionPrefix` per milestone (0.1 → 0.2 …).
- Run `./gradlew testDebugUnitTest` before pushing. Behind a rate-limited Maven Central, add
  `-ProbolectricRepo=<mirror>` (Robolectric downloads Android jars at test time). Add regression tests in `app/src/test` for any core change.
