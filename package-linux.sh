#!/bin/bash
# Full Linux release build using jpackage.
# Usage: ./package-linux.sh [release-tag]
#   release-tag defaults to v0.1.1
#
# Output: records-and-tracking-linux.zip  (in project root)
# Test:   target/jpackage/records-and-tracking/bin/records-and-tracking
set -e

cd "$(dirname "${BASH_SOURCE[0]}")"

ARTIFACT_ID="records-and-tracking"
VERSION="0.1.0"
SNAPSHOT_JAR="target/${ARTIFACT_ID}-${VERSION}-SNAPSHOT.jar"
JFX_VERSION="21.0.2"
M2_REPO="$HOME/.m2/repository/org/openjfx"
RELEASE_TAG="${1:-v0.1.1}"
ZIP="${ARTIFACT_ID}-linux.zip"
REPO="code-briomar/records-and-tracking"

# ── 1. Build ────────────────────────────────────────────────────────────────
echo ">>> Building..."
mvn clean package -DskipTests

# ── 2. Stage input JARs (thin project JAR + all deps) ───────────────────────
echo ">>> Staging input JARs..."
mkdir -p target/libs
cp "$SNAPSHOT_JAR" target/libs/

# ── 3. Collect Linux-specific JavaFX JARs (contain the native .so files) ────
echo ">>> Staging JavaFX Linux platform modules..."
mkdir -p target/javafx-mods
for mod in javafx-base javafx-graphics javafx-controls; do
    src="$M2_REPO/$mod/$JFX_VERSION/$mod-$JFX_VERSION-linux.jar"
    if [ ! -f "$src" ]; then
        echo "ERROR: $src not found. Run 'mvn package' once to populate ~/.m2." >&2
        exit 1
    fi
    cp "$src" target/javafx-mods/
done

# ── 4. jpackage → app-image ─────────────────────────────────────────────────
echo ">>> Running jpackage..."
rm -rf target/jpackage

# Comma-separated list must be a single shell token — no line breaks inside
MODULES="javafx.base,javafx.graphics,javafx.controls,java.sql,java.naming,java.management,java.security.sasl,java.security.jgss,jdk.unsupported,jdk.crypto.ec"

JPACKAGE_TMP="$(pwd)/target/jpackage-tmp"
mkdir -p "$JPACKAGE_TMP"

# /tmp is nearly at its per-user tmpfs quota (~24 MB left).
# Redirect ALL temp-file usage (jpackage, jlink, objcopy) to home partition.
export TMPDIR="$JPACKAGE_TMP"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$JPACKAGE_TMP"

jpackage \
    --input        target/libs \
    --name         "$ARTIFACT_ID" \
    --main-jar     "${ARTIFACT_ID}-${VERSION}-SNAPSHOT.jar" \
    --main-class   com.courttrack.AppLauncher \
    --type         app-image \
    --module-path  target/javafx-mods \
    --add-modules  "$MODULES" \
    --app-version  "$VERSION" \
    --description  "Kenyan Court Records and Tracking System" \
    --java-options "-Djava.net.preferIPv4Stack=true" \
    --temp         "$JPACKAGE_TMP" \
    --dest         target/jpackage

# ── 5. Zip ──────────────────────────────────────────────────────────────────
echo ">>> Creating $ZIP..."
rm -f "$ZIP"
cd target/jpackage
zip -r "../../$ZIP" "$ARTIFACT_ID/"
cd ../..
echo "Created: $ZIP"

# # ── 6. Upload to GitHub release ──────────────────────────────────────────────
# echo ">>> Uploading to GitHub release $RELEASE_TAG..."
# # Delete ALL existing .zip assets so stale old-named builds never accumulate.
# gh release view "$RELEASE_TAG" --repo "$REPO" --json assets \
#     --jq '.assets[] | select(.name | endswith(".zip")) | .name' 2>/dev/null \
#   | xargs -r -I{} gh release delete-asset "$RELEASE_TAG" {} --repo "$REPO" --yes 2>/dev/null || true
# gh release upload "$RELEASE_TAG" "$ZIP" --repo "$REPO"

# # ── 7. Smoke-test ────────────────────────────────────────────────────────────
# echo ">>> Smoke-testing app-image..."
# # Force-kill any stale instances so they can't print dying output to this terminal.
# pkill -9 -f "bin/$ARTIFACT_ID" 2>/dev/null || true
# sleep 1
# rm -rf ~/.courttrack/
# # Unset build-time env vars so they don't affect the running app
# unset JAVA_TOOL_OPTIONS TMPDIR
# # Redirect app output to a log file to keep the build terminal clean
# mkdir -p ~/.courttrack
# "target/jpackage/$ARTIFACT_ID/bin/$ARTIFACT_ID" > ~/.courttrack/app.log 2>&1 &
# echo "App launched (PID $!). Log: ~/.courttrack/app.log"
