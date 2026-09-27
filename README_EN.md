# CustomDiscs Forge

**CustomDiscs** is a Forge mod for **Minecraft 1.7.10** that adds custom audio to music discs, player heads, and a custom horn item.

Designed for **Forge 10.13.4.1614** and GT New Horizons-compatible environments.

**[Русская версия / Russian version →](README.md)**

> **Based on [Navoei/CustomDiscs](https://github.com/Navoei/CustomDiscs).**
>
> This project uses Navoei's CustomDiscs as its foundation and extends/reworks it for Minecraft 1.7.10 / Forge with additional networking, client-side audio caching, positional playback, jukebox handling, and other changes.

## Features

* Custom audio for:

  * Music Discs
  * Player Heads
  * `customdiscs:custom_horn`
* WAV, MP3 and FLAC support
* Positional playback with distance attenuation
* Server-controlled playback range
* **63 blocks default range**
* Independent client-side volume for:

  * Discs
  * Heads
  * Horns
* SHA-256 verified client-side audio cache
* Bounded audio transfer in network chunks
* Jukebox state tracking
* Custom models using legacy numeric model IDs
* Localized messages
* Server-controlled playback metadata
* No Simple Voice Chat, microphone, or voice-chat server required

## Requirements

* Minecraft **1.7.10**
* Forge **10.13.4.1614**
* **Java 8**
* The same CustomDiscs JAR on the server and every client

> Compatibility with a specific GT New Horizons version has not yet been verified.

## Installation

1. Download `customdiscs-0.3.1.jar` from the [Releases](https://github.com/TcKatFire/CustomDiscs-Forge-1.7.10/releases) page.
2. Put the **same JAR** into the `mods/` folder on:

   * the dedicated server;
   * every client.
3. Start the server once.
4. Put your authorized audio files into:

```text
config/customdiscs/musicdata/
```

Supported formats:

```text
.wav
.mp3
.flac
```

5. Hold a supported disc, player head, or Custom Horn and run:

```text
/customdiscs create <filename>
```

Example:

```text
/customdiscs create my_song.mp3
```

> Server and clients must use the same CustomDiscs version.
> This Forge JAR is **not** a Paper/Spigot plugin.

## Commands

All commands use the `/customdiscs` root.

| Command                                          | Description                                       |
| ------------------------------------------------ | ------------------------------------------------- |
| `/customdiscs create <filename> [title]`         | Assign an audio file to the held item             |
| `/customdiscs download <url> <filename>`         | Download an allowed audio file through the server |
| `/customdiscs range <blocks>`                    | Set the playback range                            |
| `/customdiscs goatcooldown <ticks>`              | Set Custom Horn cooldown                          |
| `/customdiscs revert`                            | Remove CustomDiscs data from the held item        |
| `/customdiscs setmodel [name-or-id]`             | Select or apply a custom model                    |
| `/customdiscs revertmodel`                       | Remove the custom model ID                        |
| `/customdiscs reload`                            | Reload configuration and language files           |
| `/customdiscs volume <disc\|head\|horn> <0-100>` | Set local volume for a category                   |

`volume` is handled locally by the client. Other commands are handled by the server.

> Only download audio that you are authorized to use.

## Configuration

Main configuration files:

```text
config/
├── customdiscs.cfg
├── customdiscs-client.cfg
└── customdiscs/
    ├── musicdata/
    ├── models.yml
    └── langs/
```

Client audio cache:

```text
<Minecraft directory>/customdiscs/cache/
```

The volume GUI is available under:

**Options → Controls → CustomDiscs Volume Settings**

Default key: **O**

### Playback Range

Default server-controlled range:

```text
63 blocks
```

Per-item range overrides cannot exceed the configured server maximum.

Client-side volume does not affect the server playback range.

### Jukebox Behavior

When a world/chunk is loaded, an already-inserted custom disc is detected without automatically starting playback.

Playback starts when a jukebox changes from empty to a custom disc.

Jukebox reconciliation runs every **10 ticks**, so hopper-driven changes may take up to 10 ticks to be detected.

## Custom Models

Minecraft 1.7.10 does not have modern `Custom Model Data` or a goat horn item.

CustomDiscs therefore provides:

* a custom `Custom Horn` item;
* legacy numeric model IDs;
* resource-pack textures;
* model definitions in `config/customdiscs/models.yml`.

## Building

A **Java 8 JDK** is required.

```powershell
.\gradlew.bat check build --no-daemon
```

The resulting JAR is placed in:

```text
build/libs/
```

The test suite covers:

* audio decoding;
* packet serialization;
* packet discriminators;
* playback lifecycle;
* jukebox state transitions;
* command behavior;
* volume/range separation.

These checks do not replace a live dedicated server/client test.

## Troubleshooting

If audio does not work:

1. Make sure the server and client use the same mod version.
2. Compare `network_check` lines in `latest.log`.
3. Check server logs for:

   * `playback_encode`
   * `audio_chunk_encode`
4. Compare them with any malformed-packet warnings on the client.
5. Make sure the audio file exists in:

```text
config/customdiscs/musicdata/
```

The server reads the source file and sends audio data to the client. The client stores the received data in its own cache and does **not** access the server's filesystem.

> Playback already in progress is not synchronized to players who join or reconnect while the audio is playing.

## Credits

### Original Project

**[Navoei/CustomDiscs](https://github.com/Navoei/CustomDiscs)**

This project is based on Navoei's CustomDiscs and has been reworked and extended for Minecraft 1.7.10 / Forge.

### CustomDiscs Forge

**Author:** TcKatFire

**Target:** Minecraft 1.7.10 / Forge 10.13.4.1614

## License

See [`LICENSE`](LICENSE).

The original MIT copyright and license notice is retained.
