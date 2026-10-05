#!/bin/sh
# The Mac half of tools/store-shots.sh: runs one StoreTour test on one simulator.
#   sh tools/store-tour-mac.sh <test> <simulator>
# Pictures (and for testVideo, screen.mp4 plus keep-seconds: where the walk-through starts and ends
# in it) go to ~/ptp-tour.
set -e
TEST="$1"
SIM="$2"
OUT="$HOME/ptp-tour"
LOG=/tmp/pttp-tour.log
cd "$HOME/PowerToThePeople"
export JAVA_HOME="$HOME/tools/jdk21/Contents/Home"
"$HOME/tools/xcodegen/bin/xcodegen" generate --spec iosApp/project.yml > /dev/null
rm -rf "$OUT" && mkdir -p "$OUT"

xcrun simctl boot "$SIM" 2>/dev/null || true
# The same tidy status bar Apple uses in its own pictures.
xcrun simctl status_bar "$SIM" override --time 9:41 --dataNetwork wifi --wifiBars 3 --cellularBars 4 \
  --batteryState discharging --batteryLevel 100
# Dark, like the Play pictures: the flag background reads best that way.
xcrun simctl ui "$SIM" appearance dark

cd iosApp
set -- -project PowerToThePeople.xcodeproj -scheme StoreTour \
  -destination "platform=iOS Simulator,name=$SIM" -derivedDataPath "$HOME/PowerToThePeople/iosApp/DerivedData"
xcodebuild build-for-testing "$@" > $LOG 2>&1 ||
  { grep -E 'error' $LOG | head -30; echo "BUILD FAILED - see $LOG on the Mac"; exit 1; }

REC=""
if [ "$TEST" = testVideo ]; then
  # The screen is recorded for the whole run; the tour marks where the part to keep begins and ends.
  xcrun simctl io "$SIM" recordVideo --codec=h264 --force "$OUT/screen.mp4" 2>/dev/null &
  REC=$!
  sleep 2
  date +%s > "$OUT/recording-began"
fi

OK=1
TEST_RUNNER_TOUR_OUT="$OUT" xcodebuild test-without-building "$@" -only-testing:StoreTour/StoreTour/$TEST >> $LOG 2>&1 || OK=0

if [ -n "$REC" ]; then
  kill -INT $REC; wait $REC 2>/dev/null || true
  if [ -f "$OUT/start" ] && [ -f "$OUT/end" ]; then
    B=$(cat "$OUT/recording-began")
    echo "$(( $(stat -f %m "$OUT/start") - B )) $(( $(stat -f %m "$OUT/end") - B ))" > "$OUT/keep-seconds"
  fi
  rm -f "$OUT/start" "$OUT/end" "$OUT/recording-began"
fi

if [ $OK = 0 ]; then
  grep -E 'error|failed|XCTAssert' $LOG | head -30
  echo "TOUR FAILED - see $LOG on the Mac"
  exit 1
fi
echo '** TOUR DONE **'
