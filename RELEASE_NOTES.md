# Release Notes

## v0.1.8 - 2026-02-27

### Fixed
- Update on Windows now works for both direct JAR and MSI (jpackage) installs
- Uses a batch-script trampoline to swap the JAR after the JVM exits and releases its file lock
- Relaunches via the native EXE launcher when installed via MSI, preserving the Start Menu shortcut

---

## v0.1.7 - 2026-02-27

 ### Changed
 - Version bump for update system test

 ---


## v0.1.6 - 2026-02-27

 ### Fixed
 - Update now replaces the original JAR in-place so re-launching the app always uses the new version
 - Release notes dialog no longer appears on fresh installs or shows the wrong version

 ---


## v0.1.5 - 2026-02-25

### Fixed
- Update checker now detects releases with `.jar` assets (previously only found `.zip`)

---

## v0.1.4 - 2026-02-25

### Changed
- Login page now shows the app icon (app.ico) instead of the unicode scales character

---

## v0.1.1 - 2026-02-19

### Added
- Release notes support for GitHub releases

### Changed
- Updated version to 0.1.1

---

## v0.1.0 - 2026-02-19

### Added
- Initial release of the Kenyan Court Records and Tracking System
- Desktop application for managing court records
- Built with JavaFX 21 and Atlantic FX
