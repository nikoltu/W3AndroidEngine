<div align="center">

# Warsmash Android

A hobby Android port of the [Warsmash](https://github.com/Retera/WarsmashModEngine) engine for W3.

[![License: AGPL v3](https://img.shields.io/badge/license-AGPL--3.0-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Desktop-3DDC84?logo=android&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![libGDX](https://img.shields.io/badge/libGDX-1.12.1-E74C3C)
![Status](https://img.shields.io/badge/status-early%20development-yellow)

</div>

## About

This fork continues Retera's work on Warsmash and adapts it for Android. The aim is to play W3 on phones and tablets with classic graphics at a steady 60 FPS.

> [!IMPORTANT]
> No game files are included or distributed. The engine reads them from your own legally obtained copy of W3.

## Status

Current version: **v0.1.031**

| Area | State |
|---|---|
| Desktop build | Working |
| Reading current game data (CASC) or extracted files | Working |
| Current model, texture and map formats | Working |
| Android app (tested on a Galaxy Tab S8 Ultra) | Working |
| Touch controls (tap, long press, box select, two finger pan and pinch zoom, pen button, control group bar, hero buttons) | Working, being tuned |
| Melee games | In progress: all hero abilities of the four races can be cast, including illusions (Mirror Image, Wand of Illusion); the unit abilities and researches of all four races are in place (for example Spell Steal, Cloud, Web, Possession, the Meat Wagon corpse hold, Cyclone, Sentinel, Ultravision); autocast spells are also used in combat; taverns, mercenary camps, the goblin laboratory and the marketplace sell heroes, units and items, and items can be sold to shops; hero experience, revival, blight, building refunds and the melee victory and defeat rules follow the game data; every race can train its units, research its upgrades and upgrade its main hall; creeps call for help, guard their camps and sleep at night; map scripts gain natives and events for timed life, buffs, hero skills and transports; computer opponents of all four races play the melee game with their own AI scripts |
| 60 FPS with classic graphics | Reached on the test tablet, not yet measured on phones |

## Running on desktop

Requires JDK 17.

1. Copy `core/assets/warsmash.ini` to `core/assets/warsmash.local.ini`.
2. Point `[DataSources]` at your game install:

   ```ini
   [DataSources]
   Count=3
   Type00=CASC
   Path00="C:\Path\To\Game"
   Prefixes00=war3.w3mod,war3.w3mod\_deprecated.w3mod,war3.w3mod\_locales\enus.w3mod
   Type01=Folder
   Path01="..\..\resources"
   Type02=Folder
   Path02="."
   ```

3. Run:

   ```bash
   ./gradlew desktop:runGame -Pargs="-window -ini warsmash.local.ini"
   ```

## Running on Android

Requires Android 8.0 or newer and your own copy of the game data on the device.

1. Build and install the app:

   ```bash
   ./gradlew android:installDebug
   ```

2. Copy the game data to the device: an installed game folder (CASC) or extracted files. Files copied into the app's own folder (`Android/data/com.etheller.warsmash.android/files/data`) are found by themselves.
3. On first start, choose that folder in the **Game data folder** screen with **Choose folder** and tap **Continue**. A folder outside the app's own folder needs the **All files access** permission, which the screen asks for.
4. Later starts go straight to the game. To pick another folder, hold the app icon and choose **Game data**.

<details>
<summary>Other supported game data layouts</summary>

Older releases that ship MPQ archives work too: list each archive as `Type=MPQ` in `[DataSources]` and set `MaxPlayers=16` in `[Emulator]`. See the [upstream README](https://github.com/Retera/WarsmashModEngine#readme) for details on each layout.

</details>

## Credits

- [Retera](https://github.com/Retera) for Warsmash and everyone who contributed to it
- [libGDX](https://libgdx.com/)

## License

[GNU AGPL v3.0](LICENSE), same as upstream.
