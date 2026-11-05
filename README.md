# AwakePlanner

A small Windows 11 tray app that keeps the PC from locking, sleeping, or turning the screen off during working hours. The default hours are Mon–Fri 08:30–19:00. It needs no admin rights.

## Features
- **Keeps the PC awake** through the Windows `SetThreadExecutionState` API while the schedule is active. Outside the schedule, Windows power settings apply as usual.
- **Optional *Simulate F15 keypress* setting.** Turn this on if a corporate policy still locks the screen: it presses the invisible F15 key once a minute.
- **Schedule per weekday**, with its own on/off switch and from/till times for each day.
- **Mode:** *Schedule*, *Always on*, or *Off*. You can switch it from the window or from the tray menu.
- **Dark mode schedule.** Switches Windows between light and dark mode, either at fixed times or at sunrise/sunset for your coordinates, with optional offsets in minutes. You can apply it to apps, to Windows itself (taskbar, Start), or to both. The theme is changed only at each switch time, so a manual change holds until the next one. Pick a country to fill in its capital's coordinates, or enter latitude and longitude yourself. The setting can also be changed from the tray menu.
- **Tray icon:** green means active, grey means idle. The tooltip shows the current status. Double-click the icon to open the window.
- **Closing or minimising the window** hides it to the tray, and it leaves the taskbar. To quit, use *Exit* in the tray menu.
- **Single instance:** a second launch shows a message and quits.
- **Settings and log files** are kept in `%APPDATA%\AwakePlanner\` (`config.properties` and `app.log`).

## Requirements
Java 25 (JDK or JRE) on `PATH`.

## Build
```
mvn package
```
The build produces `target/AwakePlanner.jar` and `target/AwakePlanner.exe`. The `.exe` wraps the jar and still needs Java 25 installed. Local builds are versioned `0.0.0-dev`. Pass `-Drevision=1.2.3` to set a version.

To regenerate `src/main/resources/icon.ico` after changing the glyph in `StatusIcons`, run `moogl.IconGenerator` from the test sources.

## Install and autostart
1. Download `AwakePlanner.exe` from the [latest release](https://github.com/Mo-og/AwakePlanner/releases/latest). Put it in a folder you can write to without admin rights, for example `%USERPROFILE%\Apps\AwakePlanner\`. Don't use `Program Files`, or the app won't be able to update itself.
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
2. Creates the GitHub release `v1.2.0` with `AwakePlanner.exe`, `AwakePlanner.jar` and a `.sha256` file for each.
3. Writes release notes from the commits.

Installed copies pick it up on their next daily check. Tags must look like `vMAJOR.MINOR.PATCH`, and each one must be higher than the last. Pushes to `master` without a tag only run the *Build* workflow (tests), and publish nothing.

To redo a broken release, delete the release and its tag on GitHub (`git push origin :v1.2.0`), then tag again. It's usually simpler to push the next patch version.

## Check it works
While the app is active, run `powercfg /requests` in a terminal. `javaw.exe` should be listed under `DISPLAY` and `SYSTEM`.
