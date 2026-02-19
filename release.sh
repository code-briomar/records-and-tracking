#!/bin/bash
# Build and release script for Fedora/RHEL
# Usage: ./release.sh [version]
set -e

cd "$(dirname "${BASH_SOURCE[0]}")"

# Get version from argument or prompt
if [ -z "$1" ]; then
    echo "Usage: ./release.sh <version>"
    echo "Example: ./release.sh 0.1.2"
    exit 1
fi

VERSION="$1"
ARTIFACT_ID="records-and-tracking"
DIST_NAME="records-and-tracking-linux"
ZIP="${DIST_NAME}.zip"
TAG="v${VERSION}"

echo ">>> Building version ${VERSION}..."

echo ">>> Running jpackage to create app image..."
./package-linux.sh

echo ">>> Extracting jpackage output for RPM..."
rm -rf "rpmbuild/BUILD/${ARTIFACT_ID}"
mkdir -p "rpmbuild/BUILD"
unzip -q -o "${ZIP}" -d "rpmbuild/BUILD/"

echo ">>> Creating RPM build directory structure..."
rm -rf rpmbuild/{BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}
mkdir -p rpmbuild/{BUILD,RPMS/x86_64,SPECS,SOURCES}

cp "SPECS/${ARTIFACT_ID}.spec" rpmbuild/SPECS/

# Create source tarball
cd rpmbuild/BUILD
rm -rf "${ARTIFACT_ID}-${VERSION}" records-and-tracking-tmp
mv records-and-tracking "${ARTIFACT_ID}-${VERSION}"
tar -czvf "../SOURCES/${ARTIFACT_ID}-${VERSION}.tar.gz" "${ARTIFACT_ID}-${VERSION}/"
cd ../..

echo ">>> Building RPM..."
rpmbuild --define "_topdir $(pwd)/rpmbuild" \
         --define "version ${VERSION}" \
         --define "dist .fc40" \
         --define "debug_package %{nil}" \
         -bb rpmbuild/SPECS/${ARTIFACT_ID}.spec

echo ">>> RPM created successfully!"
ls -la rpmbuild/RPMS/x86_64/
echo ""

echo ">>> Checking if tag ${TAG} exists..."
if git rev-parse "${TAG}" >/dev/null 2>&1; then
    echo "Tag ${TAG} already exists. Deleting and recreating..."
    git tag -d "${TAG}"
    git push origin ":refs/tags/${TAG}" || true
fi

echo ">>> Creating git tag..."
git add -A
git commit -m "Release ${VERSION}" || echo "Nothing to commit"
git tag -a "${TAG}" -m "Release ${VERSION}"
git push origin "${TAG}"

echo ">>> Creating GitHub release..."
# Extract release notes for this version from RELEASE_NOTES.md
RELEASE_NOTES=$(sed -n "/^## ${TAG}/,/^## /p" RELEASE_NOTES.md | sed '1d;/^## /d' | head -n -2)
if [ -z "$RELEASE_NOTES" ]; then
    RELEASE_NOTES="Release ${VERSION}"
fi

gh release create "${TAG}" \
    --title "${TAG}" \
    --notes "${RELEASE_NOTES}" \
    "${ZIP}" \
    "rpmbuild/RPMS/x86_64/${ARTIFACT_ID}-${VERSION}-1.fc40.x86_64.rpm"

echo ""
echo ">>> Release ${TAG} created successfully!"
echo ">>> Files uploaded:"
echo "   - ${ZIP}"
echo "   - ${ARTIFACT_ID}-${VERSION}-1.fc40.x86_64.rpm"
echo ""
echo "Install RPM with: sudo dnf install rpmbuild/RPMS/x86_64/${ARTIFACT_ID}-${VERSION}-1.fc40.x86_64.rpm"
