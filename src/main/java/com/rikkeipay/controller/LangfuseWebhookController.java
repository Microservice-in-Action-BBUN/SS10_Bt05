package com.rikkeipay.controller;

import com.rikkeipay.dto.JudgeEvaluationResponse;
import com.rikkeipay.service.AutoEvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST Controller tiếp nhận Webhook sự kiện từ Langfuse Server (Event: trace-created)
 * và kích hoạt Worker LLM-as-a-Judge đánh giá tự động bất đồng bộ.
 */
@RestController
@RequestMapping("/api/v1/webhooks/langfuse")
public class LangfuseWebhookController {

    private static final Logger log = LoggerFactory.getLogger(LangfuseWebhookController.class);

    private final AutoEvaluationService autoEvaluationService;

    public LangfuseWebhookController(AutoEvaluationService autoEvaluationService) {
        this.autoEvaluationService = autoEvaluationService;
    }

    /**
     * Nhận sự kiện trace từ Langfuse Webhook để tự động chấm điểm.
     */
    @PostMapping("/trace-evaluation")
    public ResponseEntity<Map<String, Object>> handleTraceWebhook(@RequestBody Map<String, Object> payload) {
        log.info("[LangfuseWebhookController] Tiếp nhận Webhook payload từ Langfuse...");

        try {
            String traceId = (String) payload.get("traceId");
            String userInput = (String) payload.get("input");
            String assistantOutput = (String) payload.get("output");

            if (traceId == null || userInput == null || assistantOutput == null) {
                log.warn("[LangfuseWebhookController] Payload không đầy đủ dữ liệu. Bỏ qua evaluation.");
                return ResponseEntity.badRequest().body(Map.of("status", "IGNORED", "message", "Missing required fields"));
            }

            // Kích hoạt chấm điểm tự động
            JudgeEvaluationResponse evalResult = autoEvaluationService.evaluateTrace(traceId, userInput, assistantOutput);

            return ResponseEntity.ok(Map.of(
                    "status", "EVALUATION_COMPLETED",
                    "traceId", traceId,
                    "overallScore", evalResult.getOverallScore(),
                    "alert", evalResult.isCriticalSecurityAlert()
            ));

        } catch (Exception e) {
            log.error("[LangfuseWebhookController] Lỗi khi xử lý webhook evaluation: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}
