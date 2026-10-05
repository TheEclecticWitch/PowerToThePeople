# Store setup: Google Play and Apple TestFlight

Everything needed to put Power to the People in testers' hands. Answers here match what the app actually does
(checked against the code on 2026-10-03); if the app starts sending anything new, update this file and
`privacy-policy.html` first.

- App name: **Power to the People** (home-screen name: P2P)
- Package / bundle ID: `com.theeclecticwitch.powertothepeople`
- Version: 1.0 (Android versionCode 1, iOS build 1)
- Category: **Education** (not News: Google Play's News category brings extra publisher requirements)
- Price: Free (News will be an in-app subscription later)
- Privacy policy URL: https://00theeclecticwitch00.com/power-to-the-people-privacy-policy/

## Store listing text

**Short description (Google Play, 80 characters max)**

> Officials, votes, bills, elections and the Constitution, in plain words.

**Subtitle (Apple, 30 characters max)**

> Your government in plain words

**Full description (Google Play): use `docs/store/play-description.txt`.** Play rejected the text below twice
(2026-10-05, Misleading Claims, "Insufficient Sources"): the word "including" implied sources it didn't list. The Play
text puts the not-government notice first and lists every source the app uses, government and not. If the app gains
a source, add it there.

**Full description (Apple, and the old Play text)**

> Keeping up with what the government is doing shouldn't take hours of searching. Power to the People puts it in
> one place, with the facts and where each one came from, so you can decide for yourself.
>
> • Your officials: your two senators, your representative, your governor and state legislators, with photos,
>   contact details, committee seats, voting records, campaign money and financial disclosures.
> • Every vote in Congress: how each member voted, in plain words, and how you would have voted.
> • Bills: search any bill in Congress, read the official plain-English summary, follow it, and see where it is
>   on the road to becoming law. Search your state's bills too.
> • Alerts: hear when your members vote, when a bill you follow moves, or when bills on a topic you choose take a step.
> • This Week in Congress: a short spoken briefing each Saturday, read by a computer voice from public records.
> • Elections: Election Day countdown, registration and polling-place links, and every candidate in your federal
>   races shown equally, in a new random order each time.
> • The Constitution and the Bill of Rights, with photos of the originals, and How Our Government Works in plain words.
> • A practice citizenship test with all 128 official civics questions.
> • The national debt, the days Congress has been in session, and what's coming up on the floor and in committees.
>
> Strictly neutral: no ratings, no rankings, no opinions. Every number links to its source.
>
> No accounts, no ads, no tracking. What you enter and follow stays on your device.
>
> Power to the People is an independent app and is not affiliated with, endorsed by, or made by the U.S. government,
> Congress, any state, or any political party. Its information comes from public government sources, including
> Congress.gov (congress.gov), the Clerk of the House (clerk.house.gov), the U.S. Senate (senate.gov), the National
> Archives (archives.gov), the U.S. Treasury (fiscaldata.treasury.gov), the U.S. Census Bureau (census.gov) and the
> Federal Election Commission (fec.gov).

**Release notes for testers**

> First test build. Please try setting your address, looking up your officials, following a bill, a topic and a
> member of Congress, and listening to This Week in Congress. Tell me anything that looks wrong or confusing.

## Google Play: Data safety form

- Does your app collect or share any of the required user data types? **Yes**
- Is all of the user data collected by your app encrypted in transit? **Yes**
- Do you provide a way for users to request that their data is deleted? **Yes** (removing the address in the app,
  turning off answer sharing, or uninstalling deletes it; nothing is tied to an account)

Data types to mark (all: **Collected**, **not shared**, purpose **App functionality**, **optional**):

| Data type | Processed ephemerally? | Why |
|---|---|---|
| Personal info → **Address** | **Yes** | Sent to the Census Bureau or Google's election service to look up districts or a polling place, then discarded |
| App activity → **In-app search history** | **Yes** | State bill searches are passed to Open States and not kept |
| App activity → **Other user-generated content** | No | Opt-in Yea/Nay answers kept anonymously for the app-wide counts |

Not shared: passing data to a third party because the user asked for that lookup isn't "sharing" under Google's
definitions. No location permission, no device identifiers, no contacts, no analytics.

**App content (other questions):** Ads: **No**. App access: **All functionality is available without special access**
(no login). Content rating questionnaire: reference/education app, no violence, no user-to-user communication, no
purchases yet. Target audience: **13 and over** (general audience; not designed for children). News app: **No**.
Government app: **No** (and the description says the app isn't affiliated with the government).

**Internal testing:** Testing → Internal testing → Create new release → upload `Power to the People 1.0 (1).aab`
(on the Desktop) → keep **Play App Signing** on → release notes above → Save → Review release → Start rollout.
Add testers' Google account emails on the Testers tab and share the opt-in link.

The upload key is in `C:\Users\rodkc\.ptp-signing\` (the keystore and its password file). **Back that folder up
somewhere safe and private.** It isn't in the project or on GitHub. If it's ever lost, Play support can reset the
upload key; Google holds the real app-signing key.

## Apple: App Privacy answers

- Do you or your third-party partners collect data from this app? **Yes**
- Data type: **User Content → Other User Content** (the opt-in Yea/Nay answers)
  - Used for: **App Functionality**
  - Linked to the user's identity? **No**
  - Used for tracking? **No**

Apple doesn't count data that is only processed in real time to answer a request and not kept, so the address
lookups and state bill searches aren't listed.

## Apple: TestFlight, step by step

1. **Create the app record.** In App Store Connect (appstoreconnect.apple.com) → **Apps** → the **+** button →
   **New App**.
   - Platforms: **iOS**
   - Name: **Power to the People** (if the name is taken, try "Power to the People: Civics")
   - Primary language: **English (U.S.)**
   - Bundle ID: **com.theeclecticwitch.powertothepeople** (it appears in the list once the first build is signed;
     if it isn't there yet, tell Claude and the archive step will register it)
   - SKU: anything unique to you, such as **PTP001**
   - User access: **Full Access**
2. **Upload the build.** Claude archives the app on the Mac and uploads it with Xcode, which is signed in to your
   Apple account. (Or in Xcode yourself: open `iosApp/PowerToThePeople.xcodeproj` on the Mac → choose "Any iOS
   Device" → **Product → Archive** → **Distribute App** → **TestFlight & App Store** → **Distribute**.)
3. **Wait for processing.** Apple emails when the build is ready, usually 10 to 30 minutes. It then shows under
   your app → **TestFlight**. The encryption question is already answered inside the app ("no special encryption").
4. **Invite internal testers.** TestFlight → **Internal Testing** → **+** to make a group → add people. Internal
   testers must be users on your App Store Connect team (Users and Access); up to 100, and no Apple review is needed.
5. **Or external testers** (anyone with an email, up to 10,000): TestFlight → **External Testing** → make a group,
   add the build, fill in **Test Information** (a short description, a feedback email, and the privacy policy URL),
   and submit it for **Beta App Review**, which usually takes a day or so.
6. **Testers** install Apple's free **TestFlight** app from the App Store, then open your invitation email (or
   public link) on their iPhone or iPad and tap **Install**.

Screenshots and the full App Store listing aren't needed until you submit for the public App Store.
