package com.skep.workplan.preset;

import com.skep.common.ApiException;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 작업계획서 프리셋 — 사용자별 1~9 슬롯.
 *
 * 프론트가 새 plan 작성 시 이 슬롯에서 헤더+(필요시 자원 목록)을 가져와 폼에 시드.
 * 슬롯 1~9 외 값은 거부.
 */
@RestController
@RequestMapping("/api/work-plan-presets")
public class WorkPlanPresetController {

    private final WorkPlanPresetRepository repo;

    public WorkPlanPresetController(WorkPlanPresetRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<PresetResponse> list(@CurrentUser AuthenticatedUser actor) {
        return repo.findByUserIdOrderBySlotAsc(actor.id()).stream()
                .map(PresetResponse::from)
                .toList();
    }

    @GetMapping("/{slot}")
    public PresetResponse get(@PathVariable Short slot, @CurrentUser AuthenticatedUser actor) {
        validateSlot(slot);
        WorkPlanPreset p = repo.findByUserIdAndSlot(actor.id(), slot)
                .orElseThrow(() -> ApiException.notFound("PRESET_NOT_FOUND", "프리셋이 비어 있습니다"));
        return PresetResponse.from(p);
    }

    /** PUT — 슬롯에 저장 (이미 있으면 덮어쓰기, 없으면 새로 생성). 멱등. */
    @PutMapping("/{slot}")
    public PresetResponse upsert(@PathVariable Short slot,
                                 @Valid @RequestBody UpsertPresetRequest req,
                                 @CurrentUser AuthenticatedUser actor) {
        validateSlot(slot);
        WorkPlanPreset p = repo.findByUserIdAndSlot(actor.id(), slot)
                .orElse(null);
        if (p == null) {
            p = repo.save(WorkPlanPreset.builder()
                    .userId(actor.id())
                    .slot(slot)
                    .name(req.name())
                    .payloadJson(req.payloadJson())
                    .build());
        } else {
            p.update(req.name(), req.payloadJson());
        }
        return PresetResponse.from(p);
    }

    @DeleteMapping("/{slot}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable Short slot, @CurrentUser AuthenticatedUser actor) {
        validateSlot(slot);
        repo.findByUserIdAndSlot(actor.id(), slot).ifPresent(repo::delete);
    }

    private void validateSlot(Short slot) {
        if (slot == null || slot < 1 || slot > 9) {
            throw ApiException.badRequest("INVALID_SLOT", "슬롯은 1~9 만 허용됩니다");
        }
    }

    public record UpsertPresetRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull String payloadJson  // 자유 JSON 문자열. 프론트가 의미 부여.
    ) {}

    public record PresetResponse(
            Long id, Short slot, String name, String payloadJson,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static PresetResponse from(WorkPlanPreset p) {
            return new PresetResponse(p.getId(), p.getSlot(), p.getName(), p.getPayloadJson(),
                    p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}
