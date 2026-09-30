#!/bin/sh
# Copy the project to the Mac, build it for the iOS simulator, and run it there.
# Run from the project root on the Windows machine:  sh tools/ios-run.sh [screenshot.png]
#
# The Mac was set up for this during the Book of Shadows work: key auth as `rod`, a JDK in
# ~/tools/jdk21, XcodeGen in ~/tools/xcodegen. The build copy is ~/PowerToThePeople - only ever
# a build copy: everything is edited on Windows and sent over.
set -e
MAC="${MAC:-rod@10.0.0.85}"   # the Mac has no fixed address - check it if this fails to connect
KEY="$HOME/.ssh/mac_book"
SIM="${SIM:-iPhone 17}"
APP_ID="com.theeclecticwitch.powertothepeople"
DIR="PowerToThePeople"

echo "--- sending the project to the Mac ---"
# Source folders are cleared first so a file deleted or renamed here is gone there too; build
# folders and Gradle caches are kept so the build stays quick. The working tree is sent, so
# uncommitted changes go too.
tar cf - --exclude=./.gradle --exclude=./.idea --exclude=./.git --exclude=build --exclude=local.properties \
    --exclude=.kotlin --exclude=DerivedData --exclude='*.xcodeproj' . |
  ssh -i "$KEY" -o BatchMode=yes "$MAC" "
    mkdir -p ~/$DIR && cd ~/$DIR &&
    rm -rf shared/src androidApp/src desktopApp/src iosApp/iosApp tools &&
    tar xf - && sed -i '' 's/\r\$//' gradlew && chmod +x gradlew"

echo "--- building ---"
ssh -i "$KEY" -o BatchMode=yes "$MAC" "
  set -e
  cd ~/$DIR
  export JAVA_HOME=~/tools/jdk21/Contents/Home
  ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 :shared:assembleIosSimulatorArm64MainResources --console=plain -q
  ~/tools/xcodegen/bin/xcodegen generate --spec iosApp/project.yml > /dev/null
  cd iosApp
  xcodebuild -project PowerToThePeople.xcodeproj -scheme PowerToThePeople \
    -sdk iphonesimulator -destination 'platform=iOS Simulator,name=$SIM' \
    -derivedDataPath ~/$DIR/iosApp/DerivedData build > /tmp/pttp-build.log 2>&1 ||
    { grep -E 'error' /tmp/pttp-build.log | grep -v '^ *export ' | head -20; echo 'BUILD FAILED - see /tmp/pttp-build.log on the Mac'; exit 1; }
  echo '** BUILD SUCCEEDED **'
"

echo "--- running ---"
ssh -i "$KEY" -o BatchMode=yes "$MAC" "
  xcrun simctl boot '$SIM' 2>/dev/null || true
  open -a Simulator
  xcrun simctl install booted ~/$DIR/iosApp/DerivedData/Build/Products/Debug-iphonesimulator/PowerToThePeople.app
  xcrun simctl terminate booted $APP_ID 2>/dev/null || true
  xcrun simctl launch booted $APP_ID > /dev/null
  sleep 8
  pgrep -q -f 'PowerToThePeople.app/PowerToThePeople' && echo 'STILL RUNNING' || echo 'CRASHED - see ~/Library/Logs/DiagnosticReports'
  xcrun simctl io booted screenshot ~/pttp_shot.png > /dev/null 2>&1
"
scp -q -i "$KEY" -o BatchMode=yes "$MAC:~/pttp_shot.png" "${1:-./ios_shot.png}"
echo "screenshot: ${1:-./ios_shot.png}"
