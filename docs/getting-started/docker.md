---
description: How to run MagmaLink as a Docker container
---

# Docker

Build the included image locally, or publish it to the container registry used
by your MagmaLink repository:

```bash
docker build -f LavalinkServer/docker/Dockerfile -t magmalink:local .
```

Install [Docker](https://docs.docker.com/engine/install/) & [Docker Compose](https://docs.docker.com/compose/install/)

## Docker Image Variants

| Variant      | Description                                  | Java Version | User  | Group | Example                                       |
|--------------|----------------------------------------------|--------------|-------|-------|-----------------------------------------------|
| `Ubuntu`     | Default variant with FFmpeg for HLS video    | 18           | 322   | 322   | `magmalink:local`                             |
| `Alpine`     | Smaller variant with FFmpeg for HLS video   | 17           | 322   | 322   | Build `alpine.Dockerfile`                    |
| `Distroless` | Small audio-only variant                    | 17           | 65534 | 65534 | Build `distroless.Dockerfile`                |

Create a `compose.yml` with the following content:

```yaml title="compose.yml"
services:
  magmalink:
    image: magmalink:local
    container_name: magmalink
    restart: unless-stopped
    environment:
      # set Java options here (6GB heap size)
      - _JAVA_OPTIONS=-Xmx6G
      # set lavalink server port
      # - SERVER_PORT=2333
      # set password for lavalink
      # - LAVALINK_SERVER_PASSWORD=youshallnotpass
    volumes:
      # mount application.yml from the same directory, if you want to use environment variables remove this line below
      - ./application.yml:/opt/MagmaLink/application.yml
      # persist plugins between restarts, make sure to create the folder & set the correct permissions and user/group id mentioned above
      - ./plugins/:/opt/MagmaLink/plugins/
      # persist generated HLS segments; the container user is UID/GID 322
      - ./video-cache/:/opt/MagmaLink/video-cache/
    networks:
      - magmalink
    expose:
      # lavalink exposes port 2333 to connect to for other containers (this is for documentation purposes only)
      - 2333
    ports:
      # you only need this if you want to make your lavalink accessible from outside of containers, keep in mind this will expose your lavalink to the internet
      - "2333:2333"
      # if you want to restrict access to localhost only
      # - "127.0.0.1:2333:2333"
networks:
  # create a MagmaLink network you can add other containers to
  magmalink:
    name: magmalink
```

Create an `application.yml` file in the same directory as the `compose.yml` file. ([Example here](../configuration/config/file.md#example-applicationyml)) or use environment variables ([Example here](../configuration/config/environment-variables.md#example-environment-variables))

Run `docker compose up -d`. See [Docker Compose Up](https://docs.docker.com/engine/reference/commandline/compose_up/)

If your bot also runs in a docker container you can make that container join the
magmalink network and use `magmalink` (service name) as the hostname to connect.
See [Docker Networking](https://docs.docker.com/network/) & [Docker Compose Networking](https://docs.docker.com/compose/networking/)
