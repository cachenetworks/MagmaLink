FROM eclipse-temurin:17-jre-jammy

# FFmpeg is used by MagmaLink's HLS video plane.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Run as non-root user
RUN groupadd -g 322 lavalink && \
    useradd -r -u 322 -g lavalink lavalink

WORKDIR /opt/MagmaLink

RUN mkdir -p /opt/MagmaLink/video-cache \
    && chown -R lavalink:lavalink /opt/MagmaLink

USER lavalink

COPY --chown=lavalink:lavalink LavalinkServer/build/libs/MagmaLink.jar MagmaLink.jar

EXPOSE 2333

ENTRYPOINT ["java", "-jar", "MagmaLink.jar"]
