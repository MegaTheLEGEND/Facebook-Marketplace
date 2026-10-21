# TODO

- [~] **Disable internal webview**: make all links open directly in the system browser.
  - Patch written (`externalLinks` option, on by default), **untested on device**. `X.dBh.A00` decides how a link opens (WEBVIEW / EXTERNAL / CUSTOM_TABS); it now always returns EXTERNAL, which is what Facebook's own "links open externally" setting does.
  - To test: tap a link in a listing description or a seller message; it should open in your default browser and no in-app browser (BrowserLiteActivity) should appear.
  - Possible gap: other launchers (Messenger, `LinkHandlingHelper` / `X.Rrg.A0C`, `BrowserLiteActivity` started directly) don't go through `dBh`. Safety net if needed: make `BrowserLiteActivity` redirect its URL to the system browser and finish.
- [ ] **Remove Reels**: delete the reels viewport engine and any code paths that load reels. Search for `reels` and remove.
  - Needs a device log to pin down. The Reels viewer isn't a standalone activity in this build (no `FbShortsViewerActivity` class; the string is only a tab/surface tag in `X.3qM`). Reels play inside Facebook's generic video "watch entry point" (`X.AFP` implements the launcher interfaces `X.VdR` / `X.VdS`) plus profile launchers (`X.BWk`, tag `FbShortsProfileViewerLauncher`), and there's no class named "viewport engine".
  - Plan: with the patched app, tap a reel on a user's profile and capture `adb logcat | findstr /i "reel shorts ActivityTaskManager START"`. The `START u0 {... cmp=...}` line or the fragment class name gives the exact class to block (return early / finish).
  - Candidates once known: the profile launcher (`X.BWk`), the video entry-point launcher (`X.AFP`), reels URI routes (`/reel/`, `/reels/audio|hashtag|topic`) in the deep-link mapper.
- [ ] **Disable back button to home screen**: remove the back button on the marketplace tab that sends you back to facebook home screen. maybe make the marketplace tab the new homescreen
- [ ] **Disable group suggestions**:
  - Remove any UI or data that suggests groups.
  - Remove strings like "group recommendations" and the label "from groups".
  - Ensure no group-based suggestions appear in the feed.
