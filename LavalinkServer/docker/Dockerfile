FROM eclipse-temurin:17-jre-jammy

ARG TARGETARCH
ARG YT_DLP_VERSION=2026.08.19

# FFmpeg is used by MagmaLink's HLS video plane.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg ca-certificates curl \
    && case "${TARGETARCH}" in \
         amd64) YT_DLP_ASSET="yt-dlp_linux"; YT_DLP_SHA256="58162f9bfdc27458ea47bfcb311cf47028f17d8154a8bf7d689861d46399230d" ;; \
         arm64) YT_DLP_ASSET="yt-dlp_linux_aarch64"; YT_DLP_SHA256="b16e4dab368a816cd05d477d698a605a6ae87ccee1c8ffd38fa21d7254141fcc" ;; \
         *) echo "Unsupported yt-dlp architecture: ${TARGETARCH}" >&2; exit 1 ;; \
       esac \
    && curl --fail --silent --show-error --location --retry 3 \
         "https://github.com/yt-dlp/yt-dlp/releases/download/${YT_DLP_VERSION}/${YT_DLP_ASSET}" \
         --output /usr/local/bin/yt-dlp \
    && echo "${YT_DLP_SHA256}  /usr/local/bin/yt-dlp" | sha256sum --check --strict - \
    && chmod 0755 /usr/local/bin/yt-dlp \
    && /usr/local/bin/yt-dlp --version \
    && apt-get purge -y --auto-remove curl \
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
