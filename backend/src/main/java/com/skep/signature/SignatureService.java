package com.skep.signature;

import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignatureService {

    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    private static final Map<SignatureRole, String> ROLE_LABEL = Map.of(
            SignatureRole.AUTHOR,     "작성자 (현장소장)",
            SignatureRole.SUPERVISOR, "담당자 (관리감독자)",
            SignatureRole.CONFIRMER,  "확인자 (HYPER)",
            SignatureRole.REVIEWER,   "검토자 (안전관리자)",
            SignatureRole.APPROVER,   "승인자 (현장총괄)"
    );

    public static String roleLabel(SignatureRole r) {
        return ROLE_LABEL.getOrDefault(r, r.name());
    }

    private final WorksheetSignatureRepository repo;
    private final WorkPlanRepository workPlanRepo;
    private final SignatureMailService mailService;
    private final com.skep.docx.WorkPlanPdfService pdfService;

    /** 작업계획서 모든 사인 슬롯 조회 (없으면 빈 슬롯 5개 반환). */
    @Transactional(readOnly = true)
    public List<WorksheetSignature> listForWorkPlan(Long workPlanId) {
        return repo.findByWorkPlanIdOrderById(workPlanId);
    }

    /** 모든 SIGNED 5개인지 (제출 게이트). */
    @Transactional(readOnly = true)
    public boolean allSigned(Long workPlanId) {
        long signed = repo.countByWorkPlanIdAndStatus(workPlanId, SignatureStatus.SIGNED);
        return signed >= 5;
    }

    /** 작성자 본인 사인 — BP 사용자가 로그인 상태에서 그 자리에서 PNG 저장. */
    @Transactional
    public WorksheetSignature signAsAuthor(Long workPlanId, byte[] pngBytes, AuthenticatedUser actor) {
        WorkPlan wp = workPlanRepo.findById(workPlanId)
                .orElseThrow(() -> new IllegalArgumentException("작업계획서를 찾을 수 없습니다"));
        // 권한: ADMIN 또는 BP 본인 회사만
        if (actor.role() != Role.ADMIN
                && (actor.companyId() == null || !actor.companyId().equals(wp.getBpCompanyId()))) {
            throw new SecurityException("작성자 사인은 해당 BP 본인 또는 ADMIN 만 가능합니다");
        }
        WorksheetSignature sig = repo.findByWorkPlanIdAndRole(workPlanId, SignatureRole.AUTHOR)
                .orElseGet(() -> {
                    WorksheetSignature s = new WorksheetSignature();
                    s.setWorkPlanId(workPlanId);
                    s.setRole(SignatureRole.AUTHOR);
                    return s;
                });
        sig.setSignerName(actor.name());
        sig.setSignerEmail(actor.email());
        sig.setSignaturePng(pngBytes);
        sig.setSignedAt(LocalDateTime.now());
        sig.setSignedByUserId(actor.id());
        sig.setStatus(SignatureStatus.SIGNED);
        return repo.save(sig);
    }

    /** 4명 (담당자/확인자/검토자/승인자) 사인 요청 — 토큰 생성 + 메일 발송. */
    @Transactional
    public Map<SignatureRole, WorksheetSignature> requestExternalSigns(
            Long workPlanId,
            Map<SignatureRole, RequestSpec> specs,
            AuthenticatedUser actor
    ) {
        return requestExternalSigns(workPlanId, specs, actor, null, true);
    }

    /**
     * PDF 첨부 옵션이 있는 버전.
     *
     * @param templateId   PDF 변환 시 사용할 DOCX 템플릿 id. null 이면 시스템의 첫 번째 가시 템플릿 자동 선택.
     * @param attachPdf    true 면 PDF 변환 후 첨부 발송 (LibreOffice 사용, ~5초 추가).
     *                     변환 실패해도 메일은 첨부 없이 발송 (graceful).
     */
    @Transactional
    public Map<SignatureRole, WorksheetSignature> requestExternalSigns(
            Long workPlanId,
            Map<SignatureRole, RequestSpec> specs,
            AuthenticatedUser actor,
            Long templateId,
            boolean attachPdf
    ) {
        WorkPlan wp = workPlanRepo.findById(workPlanId)
                .orElseThrow(() -> new IllegalArgumentException("작업계획서를 찾을 수 없습니다"));
        if (actor.role() != Role.ADMIN
                && (actor.companyId() == null || !actor.companyId().equals(wp.getBpCompanyId()))) {
            throw new SecurityException("사인 요청은 해당 BP 본인 또는 ADMIN 만 가능합니다");
        }

        // PDF 한 번 생성 (5명 공유). 실패하면 첨부 없이 진행.
        byte[] pdfBytes = null;
        String pdfFilename = null;
        if (attachPdf) {
            try {
                Long tid = templateId != null ? templateId : pdfService.defaultTemplateId(actor);
                if (tid != null) {
                    pdfBytes = pdfService.renderPdf(workPlanId, tid, actor);
                    String base = wp.getTitle() != null ? wp.getTitle() : ("work-plan-" + workPlanId);
                    pdfFilename = com.skep.common.SafeText.sanitizeFileName(base) + ".pdf";
                } else {
                    log.info("PDF 첨부 건너뜀: 사용 가능한 WORK_PLAN 템플릿 없음");
                }
            } catch (Exception ex) {
                log.warn("PDF 첨부 생성 실패 — 첨부 없이 발송 진행: workPlanId={} reason={}", workPlanId, ex.getMessage());
                pdfBytes = null;
            }
        }

        Map<SignatureRole, WorksheetSignature> result = new HashMap<>();
        for (Map.Entry<SignatureRole, RequestSpec> e : specs.entrySet()) {
            SignatureRole role = e.getKey();
            if (role == SignatureRole.AUTHOR) continue; // 작성자는 별도 API
            RequestSpec spec = e.getValue();
            if (spec == null || spec.email() == null || spec.email().isBlank()) continue;

            WorksheetSignature sig = repo.findByWorkPlanIdAndRole(workPlanId, role)
                    .orElseGet(() -> {
                        WorksheetSignature s = new WorksheetSignature();
                        s.setWorkPlanId(workPlanId);
                        s.setRole(role);
                        return s;
                    });
            sig.setSignerName(spec.name());
            sig.setSignerEmail(spec.email());
            sig.setSignToken(UUID.randomUUID().toString().replace("-", ""));
            sig.setTokenExpiresAt(LocalDateTime.now().plus(TOKEN_TTL));
            sig.setStatus(SignatureStatus.PENDING);
            sig.setSignaturePng(null);
            sig.setSignedAt(null);
            sig.setSignedByUserId(null);
            WorksheetSignature saved = repo.save(sig);
            result.put(role, saved);

            String title = wp.getTitle() != null ? wp.getTitle() : ("작업계획서 #" + workPlanId);
            try {
                mailService.sendSignRequest(
                        spec.email(), spec.name(), roleLabel(role),
                        title, saved.getSignToken(), actor.name(),
                        pdfBytes, pdfFilename
                );
            } catch (Exception ex) {
                log.error("사인 요청 메일 발송 실패: workPlanId={} role={}", workPlanId, role, ex);
                // 메일 실패해도 토큰은 저장 — 사용자가 수동 재발송 가능
            }
        }
        return result;
    }

    /** 토큰으로 사인 정보 조회 (사인 페이지에서 호출). */
    @Transactional(readOnly = true)
    public Optional<WorksheetSignature> findByToken(String token) {
        return repo.findBySignToken(token);
    }

    /** 토큰으로 사인 제출. */
    @Transactional
    public WorksheetSignature submitSignature(String token, byte[] pngBytes) {
        WorksheetSignature sig = repo.findBySignToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 사인 링크입니다"));
        if (sig.getStatus() == SignatureStatus.INVALIDATED) {
            throw new IllegalStateException("이 사인 요청은 무효화되었습니다. 재발송을 요청하세요");
        }
        if (sig.getTokenExpiresAt() != null && LocalDateTime.now().isAfter(sig.getTokenExpiresAt())) {
            sig.setStatus(SignatureStatus.EXPIRED);
            repo.save(sig);
            throw new IllegalStateException("사인 링크가 만료되었습니다 (7일 경과)");
        }
        sig.setSignaturePng(pngBytes);
        sig.setSignedAt(LocalDateTime.now());
        sig.setStatus(SignatureStatus.SIGNED);
        return repo.save(sig);
    }

    /** 워크시트 수정 시 모든 사인 무효화 (사용자가 다이얼로그에서 동의한 경우만 호출). */
    @Transactional
    public int invalidateAll(Long workPlanId, AuthenticatedUser actor) {
        WorkPlan wp = workPlanRepo.findById(workPlanId)
                .orElseThrow(() -> new IllegalArgumentException("작업계획서를 찾을 수 없습니다"));
        if (actor.role() != Role.ADMIN
                && (actor.companyId() == null || !actor.companyId().equals(wp.getBpCompanyId()))) {
            throw new SecurityException("무효화는 해당 BP 본인 또는 ADMIN 만 가능합니다");
        }
        List<WorksheetSignature> all = repo.findByWorkPlanIdOrderById(workPlanId);
        int count = 0;
        for (WorksheetSignature sig : all) {
            if (sig.getStatus() == SignatureStatus.SIGNED) {
                sig.setStatus(SignatureStatus.INVALIDATED);
                repo.save(sig);
                count++;
            }
        }
        return count;
    }

    /** 사인 PNG 바이트 조회 (DOCX 임베드용). */
    @Transactional(readOnly = true)
    public Optional<byte[]> getSignaturePng(Long workPlanId, SignatureRole role) {
        return repo.findByWorkPlanIdAndRole(workPlanId, role)
                .filter(s -> s.getStatus() == SignatureStatus.SIGNED && s.getSignaturePng() != null)
                .map(WorksheetSignature::getSignaturePng);
    }

    /** native query 로 PNG 만 직접 fetch — JPA byte[] 매핑이 일부 환경에서 null 로 떨어지는 회피용. */
    @Transactional(readOnly = true)
    public byte[] fetchPngById(Long id) {
        byte[] r = repo.findSignaturePngById(id);
        log.info("fetchPngById({}) result length={}", id, r == null ? -1 : r.length);
        return r;
    }

    /** 외부 사인 요청 입력값. */
    public record RequestSpec(String name, String email) {}
}
