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

| Area | State |
|---|---|
| Desktop build | Working |
| Reading current game data (CASC) | Mostly working |
| Current model format | In progress |
| Android module | Planned |
| Touch controls | Planned |
| 60 FPS with classic graphics | Planned |

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

<details>
<summary>Other supported game data layouts</summary>

Older releases that ship MPQ archives work too: list each archive as `Type=MPQ` in `[DataSources]` and set `MaxPlayers=16` in `[Emulator]`. See the [upstream README](https://github.com/Retera/WarsmashModEngine#readme) for details on each layout.

</details>

## Credits

- [Retera](https://github.com/Retera) for Warsmash and everyone who contributed to it
- [libGDX](https://libgdx.com/)

## License

[GNU AGPL v3.0](LICENSE), same as upstream.
