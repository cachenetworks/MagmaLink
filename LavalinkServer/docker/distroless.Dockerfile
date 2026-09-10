FROM gcr.io/distroless/java17-debian12:nonroot

WORKDIR /opt/Lavalink

# Distroless images do not contain FFmpeg; use Dockerfile or alpine.Dockerfile
# when HLS video packaging is required.
COPY LavalinkServer/build/libs/MagmaLink.jar MagmaLink.jar

EXPOSE 2333

ENTRYPOINT ["java", "-jar"]

CMD ["MagmaLink.jar"]
