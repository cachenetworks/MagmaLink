package lavalink.server.video

import lavalink.server.info.AppInfo
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/magma/v1")
class MagmaInfoRestHandler(
    private val appInfo: AppInfo,
    private val videoSessions: VideoSessionManager,
    private val videoConfig: VideoConfig
) {
    @GetMapping("/info")
    fun info() = MagmaInfoResponse(
        version = appInfo.versionBuild,
        videoEnabled = videoConfig.enabled,
        activeVideoSessions = videoSessions.activeSessionCount()
    )
}
