FROM azul/zulu-openjdk-alpine:17-jre-headless-latest

RUN apk add --no-cache libgcc ffmpeg

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
