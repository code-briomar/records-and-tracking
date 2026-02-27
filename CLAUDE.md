# CourtTrack – Claude Session Context

## Current State
- Latest release: **v0.1.5** (2026-02-25) — live on GitHub
- Previous release: v0.1.4 (also live, `.zip` asset added retroactively so old clients can update)

## Recent Work (this session)
1. Cut release v0.1.4 — bumped version, committed, tagged, built fat JAR, pushed, released
2. Discovered `UpdateChecker` only looked for `.zip` assets — old clients couldn't detect updates
3. Fixed `UpdateChecker` to accept `.jar` as fallback after `.zip` (`UpdateChecker.java:66`)
4. Added `.zip` to v0.1.4 release so pre-0.1.4 clients can still update
5. Cut release v0.1.5 with the fixed checker — both `.jar` and `.zip` attached

## Release Checklist (every release)
1. Commit changed files
2. `git tag vX.Y.Z <commit>` → `git push origin master && git push origin vX.Y.Z`
3. `mvn clean package -q` (run from project root)
4. `cd target && zip records-and-tracking-X.Y.Z.zip records-and-tracking-X.Y.Z.jar`
5. `gh release create vX.Y.Z target/records-and-tracking-X.Y.Z.{jar,zip} --title "vX.Y.Z" --notes "..." --latest`

## Update System
- `UpdateChecker.java` — hits GitHub releases API, prefers `.zip`, falls back to `.jar`
- `UpdateDownloader.java` — downloads asset, relaunches; logs to `~/.courttrack/logs/update-relaunch.log`
- App reads version from `app.properties` (`app.version`)
- Files to bump on every version: `pom.xml`, `src/main/resources/app.properties`, `RELEASE_NOTES.md`

## Key Paths
- Update logic: `src/main/java/com/courttrack/update/`
- Version util: `src/main/java/com/courttrack/util/AppVersion.java`
- GitHub repo: `code-briomar/records-and-tracking`
