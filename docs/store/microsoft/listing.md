# Microsoft Store listing: P2P: Power to the People (the reserved name, as on Apple and Google)

Copy each part into Partner Center (Store listings > English (United States)). Pictures are in this folder
(a copy is in the Desktop folder **P2P Microsoft Store images**).

## Description
The whole of `docs/store/desktop-description.txt` (the Google Play text, with the not-a-government notice first and
every source listed, as Play required; one line changed because a computer shows alerts in the app).

## Product features (one per line)
Your senators, representative, governor and state legislators from your address
Every vote in Congress in plain words, and how you would have voted
Search and follow any bill in Congress or in your state
Alerts when your members vote or a bill you follow moves
This Week in Congress: a short spoken briefing every Saturday
Election Day countdown, your state's voting dates, and every candidate shown equally
The Constitution and Bill of Rights, with photos of the originals
How Our Government Works in plain words, and a practice citizenship test
Strictly neutral: every number links to its official source
No accounts, no ads, no tracking

## Short description
Your government's public records in plain words: your officials, every vote in Congress, bills and the Constitution.

## Search terms (up to 7)
congress, civics, constitution, voting record, elections, citizenship test, senators

## Other fields
- Category: Education.
- Privacy policy URL: https://00theeclecticwitch00.com/power-to-the-people-privacy-policy/
- Website: https://00theeclecticwitch00.com/power-to-the-people/
- Support contact: support@00theeclecticwitch00.com
- Copyright: © 2026 The Eclectic Witch
- Pricing: Free. Markets: all.

## Pictures
- Screenshots (Desktop), 2560 x 1520, in order: screenshots/1-overview.png ... 8-constitution.png. Independence
  Hall's address (Philadelphia), never a real reader's. Made with
  `gradlew :shared:desktopTest --tests "*MacStoreShots*" -PstoreShots=<folder>` (the same pictures, framed, are the Mac App Store ones).
- Store logos: box-art-2160.png (1:1 Box art), poster-art-1440x2160.png (2:3 Poster art). Made by tools/icon/desktop_icons.py.

## Notes for certification (Submission options)
Power to the People is a desktop app (Kotlin / Compose Multiplatform) packaged as MSIX. The runFullTrust
capability is required because it is a full-trust desktop application. No account or sign-in. To see your own
officials, click "Set my location" on the first screen and enter any U.S. address (for example 520 Chestnut St,
Philadelphia, PA 19106). All information comes from public U.S. government sources, each linked in the app
(More > Where our information comes from). The app is independent and not affiliated with any government body or
political party; it says so in the app and at the top of the description.
