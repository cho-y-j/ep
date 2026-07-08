package com.skep.worksheet;

import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * S-9-F: AI 재작성 stub.
 *
 * 현재는 환경변수 ANTHROPIC_API_KEY 가 없을 때 503 으로 응답해 프론트에서 안내.
 * 실제 호출은 Claude API 통합 후 활성화 예정.
 */
@RestController
@RequestMapping("/api/ai")
public class AiRewriteController {

    @Value("${ANTHROPIC_API_KEY:}")
    private String apiKey;

    public record RewriteRequest(String text, String prompt) {
    }

    @PostMapping("/rewrite")
    public ResponseEntity<Map<String, Object>> rewrite(
            @RequestBody RewriteRequest req,
            @CurrentUser AuthenticatedUser actor
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.status(503).body(Map.of(
                    "ok", false,
                    "message", "AI 재작성은 ANTHROPIC_API_KEY 환경변수 설정 후 사용 가능합니다."
            ));
        }
        // TODO: Anthropic SDK 통합 — 현재는 echo
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "value", req.text() == null ? "" : req.text()
        ));
    }
}
