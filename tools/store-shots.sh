#!/bin/sh
# App Store screenshots and preview video, taken on the Mac's simulators by the StoreTour UI test
# (iosApp/StoreTour). Run from the project root on Windows:
#
#   sh tools/store-shots.sh [test] [simulator]      e.g.  sh tools/store-shots.sh testShots "iPad Pro 13-inch (M5)"
#
# The tests are testShots (the screenshots) and testVideo (the App Preview video; tools/app_preview.py
# then trims and sizes it). The pictures land in ~/ptp-tour on the Mac and are copied to docs/store/apple/raw/<simulator>/.
# The simulators used here are only for this: the app in them is set to Independence Hall's address,
# never Rod's (the plain "iPhone 17" simulator has his, so it isn't used).
set -e
MAC="${MAC:-rod@10.0.0.85}"
KEY="$HOME/.ssh/mac_book"
TEST="${1:-testShots}"
SIM="${2:-iPhone 17 Pro Max}"
DIR="PowerToThePeople"
OUT="docs/store/apple/raw/$SIM"

echo "--- sending the project to the Mac ---"
tar cf - --exclude=./.gradle --exclude=./.idea --exclude=./.git --exclude=build --exclude=local.properties \
    --exclude=.kotlin --exclude=DerivedData --exclude='*.xcodeproj' --exclude=./docs . |
  ssh -i "$KEY" -o BatchMode=yes "$MAC" "
    mkdir -p ~/$DIR && cd ~/$DIR &&
    rm -rf shared/src androidApp/src desktopApp/src iosApp/iosApp iosApp/StoreTour tools &&
    tar xf - && sed -i '' 's/\r\$//' gradlew && chmod +x gradlew"

echo "--- running $TEST on $SIM ---"
STATUS=0
ssh -i "$KEY" -o BatchMode=yes "$MAC" "sh ~/$DIR/tools/store-tour-mac.sh '$TEST' '$SIM'" || STATUS=1
# Whatever the tour got to is copied back even when it fails part way, to see where it stopped.
mkdir -p "$OUT"
scp -q -i "$KEY" -o BatchMode=yes "$MAC:ptp-tour/*" "$OUT/" 2>/dev/null || true
ls "$OUT"
exit $STATUS
