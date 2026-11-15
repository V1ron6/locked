# Changelog

Newest first. Every release heading must look like `## <version> (<versionCode>) — <YYYY-MM-DD>`;
`tools/update_landing.py` reads these to build the download page.
Versions follow `MAJOR.MINOR.PATCH`: new features bump MINOR, fixes bump PATCH.
Bump `appVersionName` and `appVersionCode` in `app/build.gradle.kts` together with a new entry here.

## 0.2.0 (2) — 2026-11-15
- Global block list: build one list of distracting apps (Settings → Block lists) and reuse it everywhere.
- Every schedule, and "Start now", can lock either the Global list or its own Custom list.
- Editing the global list updates all schedules that use it, including ones already saved.
- Schedule cards show which list they use; new schedules default to the global list once you have one.
- Existing schedules keep their own app list, so nothing changes until you switch them over.
- APKs are now named `locked-v<version>-debug.apk`, and older builds stay in `dist/`.
- Added a download page (`index.html`) that lists every build, newest first.

## 0.1.0 (1) — 2026-11-15
- First release: multiple schedules with days, start/end times (overnight supported) and per-schedule app lists.
- Schedules start automatically, with a 1-minute heads-up notification.
- Pomodoro inside every session: 25 minutes locked, 5-minute break with apps unlocked.
- Accessibility-based blocking with a block screen and a live countdown notification.
- Always-allowed apps, "Start now" quick sessions, and end-early protection (hold, then puzzle or phrase).
- Optional Settings blocking during focus and notification snoozing until breaks.
