# Release Notes

## v0.1.13 - 2026-03-09

### Bug Fixes
- **Windows MSI login**: Fixed "Account not setup" error on Windows MSI/EXE by using consistent data directory (%LOCALAPPDATA%\CourtTrack)
- **Database location**: Now uses %LOCALAPPDATA%\CourtTrack\ on Windows for database and preferences
- **Debug logging**: Added startup logging showing OS, user.home, and data directory paths

---

## v0.1.12 - 2026-03-09

### Features
- **Pagination caching**: Pages are now cached for instant navigation (keeps last 5 pages)
- **Background preloading**: Next page loads automatically in background when viewing a page
- **Page size selector**: Choose items per page (5-100) from dropdown
- **Row numbers**: Added "No." column showing row numbers across all tables

### Bug Fixes
- **Navigation**: Fixed back button not working after viewing case/person details
- **Scrolling**: Fixed choppy table scrolling by simplifying cell factories
- **Placeholders**: Tables now properly show "No cases"/"No offenders" instead of stuck "Loading"
- **Windows display**: App now starts maximized to fill the screen properly

### Performance
- Instant case detail view (no unnecessary background refresh)
- Smoother scrolling on large tables

---

## v0.1.11 - 2026-03-03
 Fixed
- CSS styles now apply correctly on app startup (fixed theme loading in auto-login)
- Replaced looked-up color variable with direct hex color in app.css
 Changed
- Replaced "Charge" column with "Sentence" in cases table
- Removed verdict column, added tooltip to show full sentence on hover
- Added status colors for all case statuses (OPEN, CLOSED, ADJOURNED, DISMISSED, SETTLED)
- Increased update check timeout to 30 seconds and added User-Agent header
- Applied async loading for all views to fix tab switch lag:
  - Dashboard: stats and recent cases now load in background
  - Cases: async loading with pagination (5 records per page)
  - Offenders: async loading with pagination (5 records per page)

## v0.1.10 - 2026-02-27

### Changed
- Version bump for update system test

---

## v0.1.9 - 2026-02-27

### Changed
- Version bump for update system test

---

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
