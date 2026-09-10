FROM azul/zulu-openjdk-alpine:17-jre-headless-latest

RUN apk add --no-cache libgcc ffmpeg

# Run as non-root user
RUN addgroup -g 322 -S lavalink && \
    adduser -u 322 -S lavalink lavalink

WORKDIR /opt/MagmaLink

RUN mkdir -p /opt/MagmaLink/video-cache \
    && chown -R lavalink:lavalink /opt/MagmaLink

USER lavalink

COPY LavalinkServer/build/libs/MagmaLink-musl.jar MagmaLink.jar

ENTRYPOINT ["java", "-jar", "MagmaLink.jar"]
