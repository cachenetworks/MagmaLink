package lavalink.server.video

import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBodyReturnValueHandler

class VideoRestHandlerTest {
    @Test
    fun `progressive stream endpoint is handled as a Spring streaming response`() {
        val method = VideoRestHandler::class.java.getDeclaredMethod(
            "stream",
            String::class.java,
            HttpServletRequest::class.java
        )
        val returnType = MethodParameter(method, -1)

        assertTrue(
            StreamingResponseBodyReturnValueHandler().supportsReturnType(returnType),
            "The stream endpoint must declare ResponseEntity<StreamingResponseBody> or Spring will not use its streaming handler"
        )
    }
}
