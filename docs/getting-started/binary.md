---
description: How to run MagmaLink as a standalone binary
---

# Standalone Binary

## Prerequisites

Install Java 17 or higher. You can download it [here](https://www.azul.com/downloads/?package=jdk#zulu).

## Installation

Download the latest `MagmaLink.jar` from the MagmaLink release page.

Create a new directory and place the `MagmaLink.jar` file inside it. This will be your MagmaLink installation directory.

## Configuration

Check out the [configuration](../configuration/index.md) page to learn how to configure Lavalink.
The recommended way would be to use a [Config File](../configuration/config/file.md).

## Running MagmaLink

Now run the following command in the directory where you placed the `MagmaLink.jar` file.

```bash
java -jar MagmaLink.jar
```

Now keep your terminal open and wait for MagmaLink to start.
If you want to run MagmaLink in the background we recommend checking out either [Screen](https://www.gnu.org/software/screen/), [Systemd](systemd.md), or [Docker](docker.md).
