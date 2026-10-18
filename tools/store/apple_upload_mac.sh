#!/bin/sh
# Uploads the Mac App Store package (built on the Mac by Rod with tools/mac-appstore-build.sh) to App Store Connect,
# where it appears under TestFlight after Apple processes it. Sends nothing for review.
# Copied from Calculator Magic. Run from Windows:  sh tools/store/apple_upload_mac.sh
set -e
ssh -i "$HOME/.ssh/mac_book" -o BatchMode=yes rod@10.0.0.85 '
  cd ~/PowerToThePeople/desktopApp/build/compose/binaries/main/pkg
  ls -la
  xcrun altool --upload-app -f "Power to the People-1.0.pkg" -t macos \
    --apiKey WLH623L7AL --apiIssuer 622bc0fb-8578-4937-8137-87f1944b56d1 2>&1 | tail -15
'
