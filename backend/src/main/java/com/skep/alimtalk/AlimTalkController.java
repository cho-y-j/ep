package com.skep.alimtalk;

import com.skep.alimtalk.dto.DirectAlimTalkRequest;
import com.skep.common.ApiException;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.sms.SmsLog;
import com.skep.sms.SmsLogRepository;
import com.skep.user.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 독립 알림톡 발송 화면 API — 템플릿 목록 / 직접 발송 / 발송 이력. ADMIN·BP 만. */
@RestController
@RequestMapping("/api/alimtalk")
@RequiredArgsConstructor
public class AlimTalkController {

    private final AlimTalkService alimTalk;
    private final SmsLogRepository smsLogs;

    public record TemplateInfo(String name, String code, String label, String content) {}

    @GetMapping("/templates")
    public List<TemplateInfo> templates(@CurrentUser AuthenticatedUser actor) {
        ensureSender(actor);
        return List.of(
                new TemplateInfo("HEALTH_CHECK", AlimTalkTemplate.HEALTH_CHECK.code, "건강검진 요청", AlimTalkTemplate.HEALTH_CHECK.content),
                new TemplateInfo("VEHICLE_SAFETY", AlimTalkTemplate.VEHICLE_SAFETY.code, "차량 안전점검 요청", AlimTalkTemplate.VEHICLE_SAFETY.content),
                new TemplateInfo("EQUIPMENT_QUOTE", AlimTalkTemplate.EQUIPMENT_QUOTE.code, "장비 투입 요청", AlimTalkTemplate.EQUIPMENT_QUOTE.content)
        );
    }

    @PostMapping("/send")
    public List<AlimTalkService.SendResult> send(@Valid @RequestBody DirectAlimTalkRequest req,
                                                 @CurrentUser AuthenticatedUser actor) {
        ensureSender(actor);
        AlimTalkTemplate template;
        try {
            template = AlimTalkTemplate.valueOf(req.template());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("BAD_TEMPLATE", "알 수 없는 템플릿: " + req.template());
        }
        Map<String, String> vars = req.vars() != null ? req.vars() : Map.of();
        // send 는 @Async — 직접 발송 화면은 동기 결과가 필요하므로 future 를 join 해서 결과 수집.
        return req.phones().stream()
                .map(p -> alimTalk.send(p, template, vars, actor.id()).join())
                .toList();
    }

    public record LogRow(Long id, String phone, String purpose, String status, String provider, LocalDateTime createdAt) {}

    @GetMapping("/logs")
    public List<LogRow> logs(@CurrentUser AuthenticatedUser actor) {
        ensureSender(actor);
        List<SmsLog> rows = actor.role() == Role.ADMIN
                ? smsLogs.findTop100ByProviderStartingWithOrderByIdDesc("DAON")
                : smsLogs.findTop100ByProviderStartingWithAndSentByOrderByIdDesc("DAON", actor.id());
        return rows.stream()
                .map(l -> new LogRow(l.getId(), l.getPhone(), l.getPurpose(), l.getStatus(), l.getProvider(), l.getCreatedAt()))
                .toList();
    }

    private void ensureSender(AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN && actor.role() != Role.BP) {
            throw ApiException.forbidden("BP_ADMIN_ONLY", "BP/ADMIN 만 알림톡 발송 가능");
        }
    }
}
