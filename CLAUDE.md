# CourtTrack – Claude Session Context

## Current State
- Latest release: **v0.1.9** (2026-02-27) — live on GitHub (update system test target)
- Previous release: v0.1.8 (2026-02-27) — first release with fully working Windows updater

## Recent Work
1. Fixed release notes dialog firing on fresh installs showing wrong (latest remote) version
2. Fixed update not replacing the original JAR — new version launched from temp dir, old shortcut stayed on old version
3. Fixed Windows JAR replacement — JVM holds a read lock on the running JAR; rename/overwrite both fail
4. Solution: batch-script trampoline (`do-update.bat`) stages new JAR, exits JVM, bat waits ~3 s for lock release, copies JAR in place, relaunches
5. MSI/jpackage support: detects native EXE launcher via `ProcessHandle.current().info().command()`, relaunches through it so bundled JRE and Start Menu shortcut keep working
6. Release notes now driven by `~/.courttrack/pending-release-notes.properties` written by updater before exit; dialog only shows after a real in-app update, not on fresh install

## Release Checklist (every release)
1. Commit changed files
2. `git tag vX.Y.Z` → `git push origin master && git push origin vX.Y.Z`
3. `mvn clean package -q` (run from project root)
4. `cd target && zip records-and-tracking-X.Y.Z.zip records-and-tracking-X.Y.Z.jar`
5. `gh release create vX.Y.Z target/records-and-tracking-X.Y.Z.{jar,zip} --title "vX.Y.Z" --notes "..." --latest`

## Update System
- `UpdateChecker.java` — hits GitHub releases API, prefers `.zip`, falls back to `.jar`
- `UpdateDownloader.java` — downloads asset; on Windows uses batch trampoline to replace locked JAR; on Linux copies directly; logs to `~/.courttrack/logs/update-relaunch.log`
- `UpdateNotificationBar.java` — UI bar; passes `UpdateInfo` to `launchInstallerAndExit`
- `App.checkAndShowReleaseNotes()` — reads `~/.courttrack/pending-release-notes.properties`; only shows dialog if file exists and version matches current (i.e. after a real update)
- App reads version from `app.properties` (`app.version`)
- Files to bump on every version: `pom.xml`, `src/main/resources/app.properties`, `RELEASE_NOTES.md`

## Key Paths
- Update logic: `src/main/java/com/courttrack/update/`
- Version util: `src/main/java/com/courttrack/util/AppVersion.java`
- Version prefs: `src/main/java/com/courttrack/util/VersionPreferences.java`
- GitHub repo: `code-briomar/records-and-tracking`
