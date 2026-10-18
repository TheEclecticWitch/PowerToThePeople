#!/bin/sh
# Builds the signed Mac App Store package. Rod runs this in Terminal ON THE MAC (signing needs his unlocked keychain,
# which an SSH session from Windows can't use):
#     sh ~/PowerToThePeople/tools/mac-appstore-build.sh <build number>
# The build number goes up by one for every upload (1, 2, 3...). Claude sends the project over and uploads the result.
# Copied from Consciousness Theories.
set -e
BUILD="${1:?Give the build number, for example: sh tools/mac-appstore-build.sh 1}"
cd "$(dirname "$0")/.."
export JAVA_HOME="$HOME/tools/jdk21/Contents/Home"
# --no-daemon: a Gradle daemon started earlier over SSH would sign from that session, which has no keychain access
# (codesign then fails with errSecInternalComponent).
./gradlew :desktopApp:packagePkg -PmacAppStore -PmacBuild="$BUILD" --no-daemon --console=plain -q
ls -la desktopApp/build/compose/binaries/main/pkg/
echo "Done. Tell Claude the Mac App Store package is built."
