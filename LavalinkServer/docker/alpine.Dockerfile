FROM azul/zulu-openjdk-alpine:17-jre-headless-latest

ARG TARGETARCH
ARG YT_DLP_VERSION=2026.08.19

RUN apk add --no-cache libgcc ffmpeg curl ca-certificates \
    && case "${TARGETARCH}" in \
         amd64) YT_DLP_ASSET="yt-dlp_musllinux"; YT_DLP_SHA256="f3dec9cfeaf304cec98290fe41c6ad465d4b747d302473559643e7af24929722" ;; \
         arm64) YT_DLP_ASSET="yt-dlp_musllinux_aarch64"; YT_DLP_SHA256="17b1641628be455368db73ab54a2858da22342084f300f7e0d50bdcbef2acf82" ;; \
         *) echo "Unsupported yt-dlp architecture: ${TARGETARCH}" >&2; exit 1 ;; \
       esac \
    && curl --fail --silent --show-error --location --retry 3 \
         "https://github.com/yt-dlp/yt-dlp/releases/download/${YT_DLP_VERSION}/${YT_DLP_ASSET}" \
         --output /usr/local/bin/yt-dlp \
    && echo "${YT_DLP_SHA256}  /usr/local/bin/yt-dlp" | sha256sum -c - \
    && chmod 0755 /usr/local/bin/yt-dlp \
    && /usr/local/bin/yt-dlp --version \
    && apk del curl

# Run as non-root user
RUN addgroup -g 322 -S lavalink && \
    adduser -u 322 -S lavalink lavalink

WORKDIR /opt/Lavalink

RUN mkdir -p /opt/Lavalink/plugins /opt/Lavalink/logs /opt/Lavalink/video-cache \
    && chown -R lavalink:lavalink /opt/Lavalink

USER lavalink

COPY --chown=lavalink:lavalink LavalinkServer/build/libs/MagmaLink-musl.jar MagmaLink.jar

EXPOSE 2333

ENTRYPOINT ["java", "-jar", "MagmaLink.jar"]
