# MagmaLink Video API

MagmaLink keeps Lavalink's audio API and websocket protocol intact. Video is a
separate HTTP media plane because Discord voice transport carries Opus audio,
not browser or media-player video.

All routes below use the same `Authorization` header as Lavalink.

## Resolve a video

The resolver accepts a direct `http://` or `https://` media URL when
`allowDirectUrls` is enabled. MagmaLink only treats URLs with a known media
extension, or an extensionless URL that passes a bounded media probe, as
direct media. Provider pages such as YouTube, TikTok, Vimeo, and Twitch are
sent to the external resolver or optional `yt-dlp` adapter instead.

The media probe checks the response body and content type, so an HTML login
page, bot challenge, or provider page returned with HTTP 200 is rejected
before a video session is created.

```http
GET /magma/v1/videos/load?identifier=https%3A%2F%2Fexample.com%2Fmovie.mp4
Authorization: your-lavalink-password
```

Example response:

```json
{
  "loadType": "video",
  "data": {
    "id": "a-video-session-id",
    "identifier": "https://example.com/movie.mp4",
    "title": "Example movie",
    "durationMs": 120000,
    "width": 1920,
    "height": 1080,
    "mimeType": "video/mp4",
    "isSeekable": true,
    "accessToken": "session-bearer-token",
    "streamUrl": "/magma/v1/videos/a-video-session-id/stream?token=session-bearer-token",
    "manifestUrl": "/magma/v1/videos/a-video-session-id/manifest.m3u8?token=session-bearer-token"
  }
}
```

The returned session is on-demand. Calling the manifest URL starts FFmpeg and
generates HLS segments in the configured cache directory.

The token is scoped to this session and expires when the session is removed or
cleaned up. Treat it like a bearer credential. The normal Lavalink
`Authorization` header may still be used instead of the token.

## HLS playback

Use `manifestUrl` with a browser HLS player, AVPlayer, VLC, or another HLS
client:

```http
GET /magma/v1/videos/a-video-session-id/manifest.m3u8
Authorization: your-lavalink-password
```

The manifest references segment URLs in the same session directory. MagmaLink
serves those files from:

```text
GET /magma/v1/videos/{sessionId}/{segmentFile}
```

The default implementation creates MPEG-TS segments and AAC audio. Video is
stream-copied where possible; audio is normalized to AAC so separate video and
audio URLs can be muxed into one HLS stream.

## Progressive playback

For a single muxed source, `streamUrl` is a range-aware HTTP proxy and supports
standard `Range: bytes=...` requests. When a resolver returns separate
`videoUrl` and `audioUrl` values, use HLS instead.

## External resolver contract

Set `magmalink.video.resolver.url` to a service that accepts:

```http
POST /resolve
Authorization: Bearer resolver-token
Content-Type: application/json

{"identifier":"https://provider.example/video/123"}
```

It must return one progressive URL or a video/audio pair:

```json
{
  "identifier": "https://provider.example/video/123",
  "title": "Example",
  "durationMs": 120000,
  "width": 1920,
  "height": 1080,
  "mimeType": "video/mp4",
  "streamUrl": "https://cdn.example/video.mp4",
  "isSeekable": true,
  "headers": {
    "User-Agent": "video-resolver"
  }
}
```

For adaptive providers, return `videoUrl` and `audioUrl` instead of
`streamUrl`. MagmaLink validates returned URLs and blocks private/local network
targets by default to reduce SSRF risk.

## Optional yt-dlp adapter

Install `yt-dlp` beside the MagmaLink process and enable it:

```yaml
magmalink:
  video:
    ytDlp:
      enabled: true
      binary: yt-dlp
      format: "best[ext=mp4]/best"
```

Provider availability, login requirements, rate limits, and content rights
remain the responsibility of the operator and the resolver configuration.

## Docker

Use the standard or Alpine MagmaLink image for HLS; both include FFmpeg. The
distroless image is suitable for Lavalink-compatible audio-only deployments
unless FFmpeg is provided by the surrounding runtime.

## Compatibility

Existing Lavalink clients continue to connect through `/v4/websocket`,
`/v4/loadtracks`, `/v4/sessions/...`, and the other Lavalink routes. A client
does not need to understand the video API to use MagmaLink as a normal
Lavalink audio node.

`GET /magma/v1/info` reports the MagmaLink product name, Lavalink API version,
video availability, and active video-session count.
