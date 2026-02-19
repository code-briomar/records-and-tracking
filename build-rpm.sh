#!/bin/bash
# Build RPM package for Fedora/RHEL
set -e

cd "$(dirname "${BASH_SOURCE[0]}")"

ARTIFACT_ID="records-and-tracking"
VERSION="0.1.0"
DIST_NAME="records-and-tracking-linux"
ZIP="${DIST_NAME}.zip"

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

# Create source tarball - properly this time
cd rpmbuild/BUILD
rm -rf "${ARTIFACT_ID}-${VERSION}" records-and-tracking-tmp
# Rename to proper format for tarball
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
echo "Install with: sudo rpm -i rpmbuild/RPMS/x86_64/${ARTIFACT_ID}-${VERSION}-1.fc40.x86_64.rpm"
echo "Or: sudo dnf install rpmbuild/RPMS/x86_64/${ARTIFACT_ID}-${VERSION}-1.fc40.x86_64.rpm"
