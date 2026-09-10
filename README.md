# MagmaLink

<img align="right" src="/branding/lavalink.svg" width=200 alt="MagmaLink logo">

A Lavalink-compatible audio node based on [Lavaplayer](https://github.com/lavalink-devs/lavaplayer) and [Koe](https://github.com/KyokoBot/koe), extended with an optional HTTP video streaming plane.

MagmaLink preserves Lavalink v4's REST and websocket endpoints, so existing
Lavalink clients can use it as a normal audio node. Video is exposed separately
through `/magma/v1/videos/*` as HLS or range-aware progressive HTTP media.

This repository is a compatibility-focused fork of
[Lavalink](https://github.com/lavalink-devs/Lavalink). The original MIT license
and attribution are retained.

Being used in production by FredBoat, Dyno, LewdBot, and more.

A [basic example bot](https://github.com/lavalink-devs/lavalink-client/tree/main/testbot) is available.

[![Lavalink Guild](https://discordapp.com/api/guilds/1082302532421943407/embed.png?style=banner2)](https://discord.gg/ZW4s47Ppw4)

> [!NOTE]
> The audio-compatible base is Lavalink 4.2.2. MagmaLink video features are
> opt-in and documented in [docs/video.md](docs/video.md).

## Getting started
* Pick one of the [up-to-date clients](https://lavalink.dev/clients). Advanced users can create their own using the [API documentation](https://lavalink.dev/api/)
* See the [server configuration documentation](https://lavalink.dev/configuration/) for configuring your Lavalink server
* Explore [available plugins](https://lavalink.dev/plugins) for extra features
* See also our [FAQ](https://lavalink.dev/getting-started/faq)
<details>
<summary>Table of Contents</summary>

- [Features](#features)
- [Requirements](#requirements)
- [Hardware Support](#hardware-support)
- [Changelog](#changelog)
- [Versioning policy](#versioning-policy)

</details>

## Features
* Support for [DAVE](https://daveprotocol.com)
* Powered by Lavaplayer
* Minimal CPU/memory footprint
* Twitch/YouTube (via [this](https://github.com/lavalink-devs/youtube-source#plugin) plugin) stream support
* HTTP video streaming through HLS and progressive range requests
* Optional external video resolvers and yt-dlp integration
* Event system
* Seeking
* Volume control
* REST API for resolving Lavaplayer tracks, controlling players, and more
* Statistics (good for load balancing)
* Basic authentication
* Prometheus metrics
* Docker images
* [Plugin support](https://lavalink.dev/plugins.html)

## Video streaming

See [the Video API guide](docs/video.md) for the resolver contract, HLS
endpoints, FFmpeg requirements, security defaults, and client integration.

## Build

MagmaLink requires Java 17 or newer. Build the server with:

```bash
./gradlew :Lavalink-Server:bootJar
```

The output is `LavalinkServer/build/libs/MagmaLink.jar`. For HLS video
packaging, install FFmpeg on the host or use the included standard/Alpine
Docker image.

## Docker image

The standard image includes FFmpeg for HLS video playback and is published to
GHCR on every push to `main`:

```bash
docker pull ghcr.io/cachenetworks/magmalink:latest
```

The production two-node deployment is available in [`compose.yaml`](compose.yaml).

## Requirements

* Java 17 LTS or newer required. (we recommend running the latest LTS version or newer)
* OpenJDK or Zulu running on Linux AMD64 is officially supported.

Support for other JVMs is also best-effort. Periodic CPU utilization stats are prone not to work everywhere.

## Hardware Support

Lavalink also runs on other hardware, but support is best-effort.
Here is a list of known working hardware:

| Operating System | Architecture | DAVE | Lavaplayer | JDA-NAS | Timescale | AVX2  |
|------------------|--------------|------|------------|---------|-----------|-------|
| linux            | x86-64       | ✅    | ✅          | ✅       | ✅         | ✅     |
| linux            | x86          | ✅    | ✅          | ✅       | ✅         | ✅     |
| linux            | arm          | ✅    | ✅          | ✅       | ✅         | ❌     |
| linux            | armhf        | ✅    | ✅          | ❌       | ❌         | ❌     |
| linux            | aarch32      | ✅    | ✅          | ❌       | ❌         | ❌     |
| linux            | aarch64      | ✅    | ✅          | ✅       | ✅         | ❌     |
| linux-musl       | x86-64       | ✅    | ✅          | ✅       | ✅         | ✅     |
| linux-musl       | aarch64      | ✅    | ✅          | ✅       | ✅         | ❌     |
| windows          | x86-64       | ✅    | ✅          | ✅       | ✅         | ✅     |
| Windows          | x86          | ✅    | ✅          | ✅       | ✅         | ✅     |
| Windows          | aarch64      | ✅    | ❌[^1]      | ✅       | ❌[^1]     | ❌[^1] |
| darwin           | x86-64       | ✅    | ✅          | ✅       | ✅         | ✅     |
| darwin           | aarch64e     | ✅    | ✅          | ✅       | ✅         | ❌     |

[^1]: Windows on ARM is not natively supported, but seems to work fine with x86-64 JVMs & emulation.

> [!NOTE]
> The minium supported version for glibc in DAVE is 2.35 (Ubuntu 22.04).

## Changelog

Please see [here](CHANGELOG.md)

## Versioning policy

Lavalink follows [Semantic Versioning](https://semver.org/).

The version number is composed of the following parts:

    MAJOR breaking API changes
    MINOR new backwards compatible features
    PATCH backwards compatible bug fixes
    PRERELEASE pre-release version
    BUILD additional build metadata

Version numbers can come in different combinations, depending on the release type:

    `MAJOR.MINOR.PATCH` - Stable release
    `MAJOR.MINOR.PATCH+BUILD` - Stable release with additional build metadata
    `MAJOR.MINOR.PATCH-PRERELEASE` - Pre-release
    `MAJOR.MINOR.PATCH-PRERELEASE+BUILD` - Pre-release additional build metadata
