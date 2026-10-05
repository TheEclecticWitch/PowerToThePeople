# App Store listing: P2P: Power to the People 1.0

Everything App Store Connect asks for on the version page, ready to paste, plus where the pictures are.
The Google Play listing text is in `store-setup.md`; this is the same app described the same way.

## Pictures and videos

| What | Size | Folder |
|---|---|---|
| iPhone screenshots (the "6.9-inch" slot; Apple scales them for smaller iPhones) | 1320 x 2868 | `docs/store/apple/iphone-6.9/` |
| iPad screenshots (the "13-inch" slot) | 2064 x 2752 | `docs/store/apple/ipad-13/` |
| Mac screenshots | 2880 x 1800 | `docs/store/apple/mac/` |
| iPhone App Preview video (optional, 29 seconds) | 886 x 1920 | `docs/store/apple/video/app-preview-iphone.mp4` |
| Full walk-through video (97 seconds), for App Review | 1080 x 2346 | `docs/store/apple/video/walkthrough.mp4` |

The videos aren't kept in git (they're large); `python tools/app_preview.py` makes them again from a
recording. A copy of everything is in the Desktop folder **P2P App Store images**.

All pictures use Independence Hall's address (Philadelphia), never a real reader's. How they were made:
`sh tools/store-shots.sh testShots` (iPhone), `... testShots "iPad Pro 13-inch (M5)"` (iPad),
`... testVideo` (the recording), and for the Mac `gradlew :shared:desktopTest --tests "*MacStoreShots*"
-PstoreShots=<folder>` then `python tools/mac_frames.py <folder> docs/store/apple/mac`.

## Version page text

**Promotional text** (170 characters max; can be changed any time without a new review)

> Election Day is coming. See who is on your ballot, when to register and vote in your state, and how your members of Congress have voted, all from official sources.

**Description**: the same full description as Google Play, in `store-setup.md` under "Full description".

**Keywords** (100 characters max; words already in the name and subtitle count anyway, so they aren't repeated)

> congress,senator,representative,civics,constitution,bills,voting,election,citizenship test,politics

**Support URL**: https://00theeclecticwitch00.com/power-to-the-people/

**Marketing URL**: https://00theeclecticwitch00.com/power-to-the-people/

**Copyright**: 2026 The Eclectic Witch

**Subtitle** (on the App Information page, 30 characters max): Your government in plain words

## Other pages

- **App Information:** Category **Education**; secondary category **Reference**. Content rights: the app shows
  third-party content (public government records and official portraits, which are public domain or openly
  licensed), so answer **Yes, it contains third-party content, and I have the rights to use it**.
- **Age rating:** answer **None** / **No** to everything. The app has no web browser inside it (links open
  Safari), no chat, no user content shown to others, no gambling, no ads. Result: **4+**.
- **App Privacy:** already answered for TestFlight (see `store-setup.md`).
- **Pricing and Availability:** Free. It's a U.S. civics app, so **United States** only is sensible, but
  everywhere is fine too.
- **App Review Information:** sign-in required **No**. Notes:

  > No account or sign-in. To see your own officials, tap "Set my location" on the first screen and enter any
  > U.S. address (for example 520 Chestnut St, Philadelphia, PA 19106). All information comes from public U.S.
  > government sources, each linked in the app (More > Where our information comes from). The app is
  > independent and not affiliated with any government body or political party; it says so in the app and the
  > description. A video of the app is attached.
