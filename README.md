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
The build produces `target/AwakePlanner.jar`.

To also build a Windows launcher, `target/AwakePlanner.exe`, run `mvn -Pexe package`. The `.exe` wraps the jar and still needs Java 25 installed.

## Install and autostart
1. Copy `target/AwakePlanner.jar` to a permanent folder, for example `%USERPROFILE%\Apps\AwakePlanner\`.
2. Press `Win+R`, type `shell:startup`, and press Enter to open the Startup folder.
3. In the Startup folder, create a shortcut with this target:
   ```
   javaw.exe -jar "%USERPROFILE%\Apps\AwakePlanner\AwakePlanner.jar"
   ```
   If `javaw.exe` is not on `PATH`, use its full path, for example `C:\Program Files\Java\jdk-25\bin\javaw.exe`.

   Alternatively, put `scripts/AwakePlanner.cmd` next to the jar and place a shortcut to it in the Startup folder.

The app starts hidden in the tray. Run it with `--show` to open the window on start, or untick *Start hidden in tray* in the settings.

## Check it works
While the app is active, run `powercfg /requests` in a terminal. `javaw.exe` should be listed under `DISPLAY` and `SYSTEM`.
