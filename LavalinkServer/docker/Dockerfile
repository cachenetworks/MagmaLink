FROM eclipse-temurin:17-jre-jammy

ARG TARGETARCH
ARG YT_DLP_VERSION=2026.08.19
ARG DENO_VERSION=v2.9.6

# FFmpeg is used by MagmaLink's HLS video plane.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg ca-certificates curl unzip \
    && case "${TARGETARCH}" in \
         amd64) YT_DLP_ASSET="yt-dlp_linux"; YT_DLP_SHA256="58162f9bfdc27458ea47bfcb311cf47028f17d8154a8bf7d689861d46399230a"; DENO_ASSET="deno-x86_64-unknown-linux-gnu.zip"; DENO_SHA256="394f07f4da2bebe6ce6f1e7ce0fa16429b29b08c35e3fac3fe25972676dff4b2" ;; \
         arm64) YT_DLP_ASSET="yt-dlp_linux_aarch64"; YT_DLP_SHA256="b16e4dab368a816cd05d477d698a605a6ae87ccee1c8ffd38fa21d7254141fcc"; DENO_ASSET="deno-aarch64-unknown-linux-gnu.zip"; DENO_SHA256="9a46afc6c392c7cd2ff71a31558935545b46408d0e87f7a86908c712721c046e" ;; \
         *) echo "Unsupported yt-dlp architecture: ${TARGETARCH}" >&2; exit 1 ;; \
       esac \
    && curl --fail --silent --show-error --location --retry 3 \
         "https://github.com/yt-dlp/yt-dlp/releases/download/${YT_DLP_VERSION}/${YT_DLP_ASSET}" \
         --output /usr/local/bin/yt-dlp \
    && echo "${YT_DLP_SHA256}  /usr/local/bin/yt-dlp" | sha256sum --check --strict - \
    && chmod 0755 /usr/local/bin/yt-dlp \
    && curl --fail --silent --show-error --location --retry 3 \
         "https://github.com/denoland/deno/releases/download/${DENO_VERSION}/${DENO_ASSET}" \
         --output /tmp/deno.zip \
    && echo "${DENO_SHA256}  /tmp/deno.zip" | sha256sum --check --strict - \
    && unzip -p /tmp/deno.zip deno > /usr/local/bin/deno \
    && chmod 0755 /usr/local/bin/deno \
    && /usr/local/bin/yt-dlp --version \
    && /usr/local/bin/deno --version \
    && apt-get purge -y --auto-remove curl unzip \
    && rm -f /tmp/deno.zip \
    && rm -rf /var/lib/apt/lists/*

# Run as non-root user
RUN groupadd -g 322 lavalink && \
    useradd -r -u 322 -g lavalink lavalink

WORKDIR /opt/Lavalink

RUN mkdir -p /opt/Lavalink/plugins /opt/Lavalink/logs /opt/Lavalink/video-cache \
    && chown -R lavalink:lavalink /opt/Lavalink

USER lavalink

COPY --chown=lavalink:lavalink LavalinkServer/build/libs/MagmaLink.jar MagmaLink.jar

EXPOSE 2333

ENTRYPOINT ["java", "-jar", "MagmaLink.jar"]
