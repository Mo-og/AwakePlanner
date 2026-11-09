# AwakePlanner

A small Windows 11 tray app that keeps the PC from locking, sleeping, or turning the screen off during working hours. The default hours are Mon–Fri 08:30–19:00. It needs no admin rights.

## Features
- **Keeps the PC awake** through the Windows `SetThreadExecutionState` API while the schedule is active. Outside the schedule, Windows power settings apply as usual.
- **Optional *Simulate F15 keypress* setting.** Turn this on if a corporate policy still locks the screen: it presses the invisible F15 key once a minute.
- **Schedule per weekday**, with its own on/off switch and from/till times for each day.
- **Mode:** *Schedule*, *Always on*, or *Off*. You can switch it from the window or from the tray menu.
- **Dark mode schedule.** Switches Windows between light and dark mode, either at fixed times or at sunrise/sunset for your coordinates, with optional offsets in minutes. You can apply it to apps, to Windows itself (taskbar, Start), or to both. The theme is changed only at each switch time, so a manual change holds until the next one. Pick a country, US state or region to fill in its capital's coordinates (every inhabited UTC offset has at least one), or enter latitude and longitude yourself. The setting can also be changed from the tray menu.
- **Timer window.** A small panel that drops down from the top of the screen like a notch and counts down the awake time left, to the second. You can open it three ways:
  - The global hotkey, **Ctrl+Alt+F12** by default. Pressing it again closes the panel.
  - A single click on the tray icon.
  - *Show timer* in the tray menu.

  The panel follows the Windows light/dark setting and closes itself after 15 seconds. It waits while the mouse is over it, and once you have pointed at it, it closes 2 seconds after the mouse leaves. It has these buttons:
  - **+15m / +1h** add time. While idle, or in *Off* mode, they start a temporary awake session from now.
  - **−15m / −1h** remove time. If you remove more than is left, the PC stops being kept awake until the scheduled block would have ended, and the schedule resumes at its next start.
  - ⚙ opens the settings, ✕ closes the panel.

  In *Always on* mode the adjust buttons are disabled. Changing the mode clears any adjustment. Adjustments survive a restart (`%APPDATA%\AwakePlanner\session.properties`).
- **Timer settings.** Change the hotkey under *Timer window* in the settings: click the field and press a new combination. Changes in this section take effect immediately, without *Save*. It needs Ctrl, Alt or Shift, plus a letter, a digit or F1–F24. Esc cancels, and Backspace restores the default. Untick *Hotkey* to turn it off but keep the combination, or click *Clear* to remove it. If another app already uses the combination, the settings window and a tray message say so. The auto-close delay (5–120 s) can also be changed or turned off there.
- **Light and dark settings window** that follows the Windows apps light/dark setting, switching within a couple of seconds.
- **Tray icon:** green means active, grey means idle. The tooltip shows the current status. Single-click shows the timer, and double-click opens the settings window.
- **Closing or minimising the window** hides it to the tray, and it leaves the taskbar. To quit, use *Exit* in the tray menu.
- **Single instance:** a second launch shows a message and quits.
- **Settings and log files** are kept in `%APPDATA%\AwakePlanner\` (`config.properties` and `app.log`).

## Downloads
Each release has two Windows builds:
- **`AwakePlanner-full.zip` (no Java needed).** A portable folder with `AwakePlanner.exe` and its own trimmed Java runtime, about 50 MB unpacked.
- **`AwakePlanner.exe` / `AwakePlanner.jar` (slim).** Small, but needs Java 25 (JDK or JRE) on `PATH`.

Both update themselves. The full build swaps only its jar and keeps its bundled runtime. If a release needs a newer Java version or a JDK module the installed copy doesn't have, nothing is downloaded. Instead the app shows a dialog with a link to the release page, so you can download it by hand. The dialog appears once per version, and again whenever you click *Check for updates*. Each release states what it needs in its `AwakePlanner.runtime` asset.

## Build
```
mvn package
```
The build produces `target/AwakePlanner.jar` and `target/AwakePlanner.exe`. The `.exe` wraps the jar and still needs Java 25 installed. Local builds are versioned `0.0.0-dev`. Pass `-Drevision=1.2.3` to set a version.

To build the full package as well, run this on Windows (jpackage builds for the OS it runs on):
```
mvn -Pfull -Drevision=1.2.3 package
```
This runs `jlink` to make a runtime with the modules in the `runtime.modules` pom property, and `jpackage` to make an app image. The result is `target/AwakePlanner-full.zip`. If code starts using a new JDK module, add it to `runtime.modules` (`jdeps --print-module-deps target/AwakePlanner.jar` lists them). Installed full builds will then ask for a manual download.

To regenerate `src/main/resources/icon.ico` after changing the glyph in `StatusIcons`, run `moogl.IconGenerator` from the test sources.

## Install and autostart
1. From the [latest release](https://github.com/Mo-og/AwakePlanner/releases/latest), download one of these:
   - `AwakePlanner-full.zip`, if Java isn't installed. Unzip it so that you get `%USERPROFILE%\Apps\AwakePlanner\AwakePlanner.exe`.
   - `AwakePlanner.exe`, if Java 25 is installed. Put it in `%USERPROFILE%\Apps\AwakePlanner\`.

   Use a folder you can write to without admin rights. Don't use `Program Files`, or the app won't be able to update itself.
2. Press `Win+R`, type `shell:startup`, and press Enter to open the Startup folder.
3. In the Startup folder, create a shortcut to `AwakePlanner.exe`.

To run the jar instead, use a shortcut with the target `javaw.exe -jar "%USERPROFILE%\Apps\AwakePlanner\AwakePlanner.jar"`, or put `scripts/AwakePlanner.cmd` next to the jar and make a shortcut to that. If `javaw.exe` is not on `PATH`, use its full path, for example `C:\Program Files\Java\jdk-25\bin\javaw.exe`.

The app starts hidden in the tray. Run it with `--show` to open the window on start, or untick *Start hidden in tray* in the settings.

## Updates
With *Install updates automatically* ticked (the default), the app checks GitHub once a day, starting 2 minutes after launch. Click *Check for updates* in the window or the tray menu to check right away.

When a newer release is out, the app:
1. Downloads the same kind of file it's running from (`.exe` or `.jar`) into `%APPDATA%\AwakePlanner\update\`.
2. Checks it against the release's `.sha256` file.
3. Exits, swaps in the new file, and starts again.

Development builds (`0.0.0-dev`) only report a newer release and never replace themselves. Each step is written to `app.log`.

## Releasing
Releases are built by GitHub Actions (`.github/workflows/release.yml`) whenever a version tag is pushed.

One-time setup:
1. Keep the repository public. The app downloads releases without a token.
2. In the repository, open *Settings → Actions → General* and make sure Actions are allowed. The workflow asks for `contents: write` itself, so no secrets are needed.

To publish a version:
```
git tag v1.2.0
git push origin v1.2.0
```
The *Release* workflow then:
1. Builds with that version (shown in the window and in the `.exe` properties).
2. Creates the GitHub release `v1.2.0` with `AwakePlanner.exe`, `AwakePlanner.jar`, `AwakePlanner-full.zip`, `AwakePlanner.runtime` and a `.sha256` file for each. The release job runs on a Windows runner because jpackage builds for the OS it runs on.
3. Writes release notes from the commits.

Installed copies pick it up on their next daily check. Tags must look like `vMAJOR.MINOR.PATCH`, and each one must be higher than the last. Pushes to `master` without a tag only run the *Build* workflow (tests), and publish nothing.

To redo a broken release, delete the release and its tag on GitHub (`git push origin :v1.2.0`), then tag again. It's usually simpler to push the next patch version.

## Check it works
While the app is active, run `powercfg /requests` in a terminal. `javaw.exe` should be listed under `DISPLAY` and `SYSTEM`.
