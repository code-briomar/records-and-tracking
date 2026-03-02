# CourtTrack – Claude Session Context

## Current State
- Latest release: **v0.1.10** (2026-02-27) — live on GitHub
- Previous release: v0.1.9 — MSI base for update system test (per-user install)
- Update system is fully working: direct JAR and MSI installs on Windows and Linux

## Recent Work
1. Fixed release notes dialog firing on fresh installs showing wrong (latest remote) version
2. Fixed update not replacing the original JAR — new version launched from temp dir, old shortcut stayed on old version
3. Fixed Windows JAR replacement — JVM holds a read lock on the running JAR; rename/overwrite both fail
4. Solution: batch-script trampoline (`do-update.bat`) stages new JAR in `~/.courttrack/updates/`, exits JVM, bat waits ~3 s for lock release, copies JAR to original location, relaunches
5. MSI/jpackage support: detects native EXE launcher via `ProcessHandle.current().info().command()`, relaunches through it so bundled JRE and Start Menu shortcut keep working
6. Console window now closes after update — added `exit` to end of batch script
7. Release notes dialog now works correctly — stripped `v` prefix from version before saving to pending file so it matches `AppVersion.getVersion()`
8. MSI staging fixed — staged to `~/.courttrack/updates/` (always writable) instead of next to the JAR (may be in Program Files)
9. Added `<winPerUserInstall>true</winPerUserInstall>` to jpackage config — MSI now installs to `%LOCALAPPDATA%\Records and Tracking\` (writable without elevation)

## Release Checklist (every release)
1. Bump `pom.xml`, `src/main/resources/app.properties`, `RELEASE_NOTES.md`
2. Commit → `git tag vX.Y.Z` → `git push origin master && git push origin vX.Y.Z`
3. `mvn clean package -q` (run from project root)
4. `cd target && zip records-and-tracking-X.Y.Z.zip records-and-tracking-X.Y.Z.jar`
5. `gh release create vX.Y.Z target/records-and-tracking-X.Y.Z.{jar,zip} --title "vX.Y.Z" --notes "..." --latest`

## Update System (fully working)
- `UpdateChecker.java` — hits GitHub releases API, prefers `.zip`, falls back to `.jar`
- `UpdateDownloader.java`:
  - **Windows**: batch-script trampoline — stages to `~/.courttrack/updates/<name>.update`, writes `do-update.bat`, exits; bat waits 3 s, copies JAR, relaunches via native EXE (jpackage) or `javaw -jar`
  - **Linux/macOS**: direct `Files.copy` over running JAR (no lock), relaunches via `java -jar`
  - Saves `~/.courttrack/pending-release-notes.properties` before exit (version without `v` prefix)
  - Logs to `~/.courttrack/logs/update-relaunch.log`
- `UpdateNotificationBar.java` — passes `UpdateInfo` to `launchInstallerAndExit`
- `App.checkAndShowReleaseNotes()` — reads pending file on startup; shows dialog only after a real update, not on fresh install; deletes file after showing
- Files to bump on every version: `pom.xml`, `src/main/resources/app.properties`, `RELEASE_NOTES.md`

## MSI Build Notes
- jpackage config in `pom.xml` — `<winPerUserInstall>true</winPerUserInstall>` installs to `%LOCALAPPDATA%`
- Build MSI: `mvn clean package jpackage:jpackage` (run on Windows)
- MSI ends up in `target/jpackage/`

## Key Paths
- Update logic: `src/main/java/com/courttrack/update/`
- Version util: `src/main/java/com/courttrack/util/AppVersion.java`
- Version prefs: `src/main/java/com/courttrack/util/VersionPreferences.java`
- GitHub repo: `code-briomar/records-and-tracking`
