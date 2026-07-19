package com.skep.field;

import com.skep.announcement.Announcement;
import com.skep.announcement.AnnouncementRecipient;
import com.skep.announcement.AnnouncementRecipientRepository;
import com.skep.announcement.AnnouncementRepository;
import com.skep.common.ApiException;
import com.skep.company.CompanyService;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanPerson;
import com.skep.workplan.WorkPlanPersonRepository;
import com.skep.workplan.WorkPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 공지 발행·발신함 — target=phone(기본) 폰만, target=all 폰+워치 동시.
 * ADMIN/BP 외에 공급사(EQUIPMENT/MANPOWER)도 자기 소속 인원(조종원/인력원)에게 발송 가능(스코프 가드).
 */
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final PersonRepository persons;
    private final FieldFcmService fcm;
    private final WorkPlanRepository workPlans;
    private final WorkPlanPersonRepository workPlanPersons;
    private final AnnouncementRepository announcements;
    private final AnnouncementRecipientRepository announcementRecipients;
    private final CompanyService companyService;

    @PostMapping("/broadcast")
    @PreAuthorize("hasAnyRole('ADMIN','BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER')")
    public Map<String, Object> broadcast(@RequestBody BroadcastRequest req,
                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        if (req.title == null || req.title.isBlank()) {
            throw ApiException.badRequest("NO_TITLE", "title 필수");
        }
        if (req.body == null || req.body.isBlank()) {
            throw ApiException.badRequest("NO_BODY", "body 필수");
        }
        boolean isAdmin = actor.role() == Role.ADMIN;
        boolean isSupplier = actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER;
        // 폰 토큰 보유 + (옵션: 워치 토큰만 보유) person 양쪽 모두 수집해 union.
        List<Person> phoneTargets;
        List<Person> watchTargets;
        boolean includeWatch = "all".equalsIgnoreCase(req.target) || "watch".equalsIgnoreCase(req.target);
        if (req.personIds != null && !req.personIds.isEmpty()) {
            if (isSupplier) ensureSupplierOwnsPersons(actor, req.personIds);
            else if (!isAdmin) ensureBpOwnsPersons(actor, req.personIds);
            phoneTargets = persons.findByFcmTokenIsNotNullAndIdIn(req.personIds);
            watchTargets = includeWatch ? persons.findByWatchFcmTokenIsNotNullAndIdIn(req.personIds) : List.of();
        } else if (req.supplierId != null) {
            if (isSupplier) ensureSupplierScope(actor, req.supplierId);
            else if (!isAdmin) ensureBpOwnsSupplier(actor, req.supplierId);
            phoneTargets = persons.findByFcmTokenIsNotNullAndSupplierIdIn(List.of(req.supplierId));
            watchTargets = includeWatch ? persons.findByWatchFcmTokenIsNotNullAndSupplierIdIn(List.of(req.supplierId)) : List.of();
        } else {
            // 전체 작업자 대상 — ADMIN 만 허용. BP·공급사는 크로스테넌트 방지를 위해 대상 지정 필수.
            if (!isAdmin) throw ApiException.forbidden("BROADCAST_SCOPE_ALL", "전체 발송은 ADMIN 만 가능합니다");
            phoneTargets = persons.findByFcmTokenIsNotNull();
            watchTargets = includeWatch ? persons.findByWatchFcmTokenIsNotNull() : List.of();
        }
        List<String> phoneTokens = phoneTargets.stream()
                .map(Person::getFcmToken).filter(Objects::nonNull).filter(t -> !t.isBlank()).toList();
        List<String> watchTokens = watchTargets.stream()
                .map(Person::getWatchFcmToken).filter(Objects::nonNull).filter(t -> !t.isBlank()).toList();
        int phoneSent = phoneTokens.isEmpty() ? 0
                : fcm.sendAnnouncement(phoneTokens, req.title.trim(), req.body.trim());
        int watchSent = watchTokens.isEmpty() ? 0
                : fcm.sendAnnouncement(watchTokens, req.title.trim(), req.body.trim());
        java.util.Set<Long> uniqueTargetIds = new java.util.HashSet<>();
        phoneTargets.forEach(p -> uniqueTargetIds.add(p.getId()));
        watchTargets.forEach(p -> uniqueTargetIds.add(p.getId()));

        // 확인 추적: 발송 공지를 영속화하고 수신 audience(토큰 유무 무관) 를 수신자로 기록 → 발송자 확인율.
        List<Person> recipientPersons = resolveRecipients(req);
        Announcement ann = announcements.save(Announcement.builder()
                .siteId(req.siteId)
                .title(req.title.trim())
                .body(req.body.trim())
                .senderUserId(actor.id())
                .senderCompanyId(actor.companyId())
                .target(req.target)
                .build());
        announcementRecipients.saveAll(recipientPersons.stream()
                .map(p -> AnnouncementRecipient.builder()
                        .announcementId(ann.getId()).personId(p.getId()).build())
                .toList());

        return Map.of(
                "attempted", phoneSent + watchSent,
                "phone_sent", phoneSent,
                "watch_sent", watchSent,
                "targets", uniqueTargetIds.size(),
                "announcement_id", ann.getId(),
                "recipients", recipientPersons.size()
        );
    }

    /** 확인 추적용 수신자 audience — broadcast 스코프와 동일하되 토큰 필터는 제외(확인은 폰토큰 없어도 가능). */
    private List<Person> resolveRecipients(BroadcastRequest req) {
        if (req.personIds != null && !req.personIds.isEmpty()) {
            return persons.findAllById(req.personIds);
        }
        if (req.supplierId != null) {
            return persons.findBySupplierIdInOrderByIdDesc(List.of(req.supplierId));
        }
        return persons.findAll(); // 전체(ADMIN 전용 — broadcast 에서 이미 스코프 가드).
    }

    /** BP 는 자기 회사 작업계획서에 배정된 작업자에게만 발송 가능. 스코프 밖 person 포함 시 403. */
    private void ensureBpOwnsPersons(AuthenticatedUser actor, List<Long> personIds) {
        List<Long> bpWpIds = workPlans.findByBpCompanyId(actor.companyId()).stream()
                .map(WorkPlan::getId).toList();
        Set<Long> scoped = workPlanPersons.findByWorkPlanIdIn(bpWpIds).stream()
                .map(WorkPlanPerson::getPersonId).collect(Collectors.toSet());
        if (!scoped.containsAll(personIds)) {
            throw ApiException.forbidden("BROADCAST_OUT_OF_SCOPE", "본인 현장 소속 작업자만 발송 대상으로 지정할 수 있습니다");
        }
    }

    /** BP 는 자기 회사 작업계획서에 참여하는 공급사에게만 발송 가능. 스코프 밖 공급사면 403. */
    private void ensureBpOwnsSupplier(AuthenticatedUser actor, Long supplierId) {
        List<Long> bpWpIds = workPlans.findByBpCompanyId(actor.companyId()).stream()
                .map(WorkPlan::getId).toList();
        boolean inScope = workPlanPersons.findByWorkPlanIdIn(bpWpIds).stream()
                .anyMatch(wpp -> supplierId.equals(wpp.getSupplierCompanyId()));
        if (!inScope) {
            throw ApiException.forbidden("BROADCAST_OUT_OF_SCOPE", "본인 현장에 참여하는 공급사만 발송 대상으로 지정할 수 있습니다");
        }
    }

    /**
     * 공급사는 자기 소속(본인+하위공급사) 인원에게만 발송 가능. 타 공급사 person 포함 시 403.
     * 스코프는 인원 목록(PersonService)이 노출하는 것과 동일한 selfAndChildren — 목록/발송 불일치 방지.
     */
    private void ensureSupplierOwnsPersons(AuthenticatedUser actor, List<Long> personIds) {
        Set<Long> ownIds = persons.findBySupplierIdInOrderByIdDesc(supplierScope(actor)).stream()
                .map(Person::getId).collect(Collectors.toSet());
        if (!ownIds.containsAll(personIds)) {
            throw ApiException.forbidden("BROADCAST_OUT_OF_SCOPE", "본인 소속 인원에게만 발송할 수 있습니다");
        }
    }

    /** 공급사가 supplierId 로 지정 시 — 본인+하위공급사 범위 밖이면 403. */
    private void ensureSupplierScope(AuthenticatedUser actor, Long supplierId) {
        if (!supplierScope(actor).contains(supplierId)) {
            throw ApiException.forbidden("BROADCAST_OUT_OF_SCOPE", "본인 소속 인원에게만 발송할 수 있습니다");
        }
    }

    private List<Long> supplierScope(AuthenticatedUser actor) {
        if (actor.companyId() == null) {
            throw ApiException.forbidden("NO_COMPANY", "소속 회사가 없는 계정입니다");
        }
        return companyService.selfAndChildren(actor.companyId());
    }

    /** 발신함 — 내 회사가 보낸 공지 목록 + 수신자/확인 집계(최신순). ADMIN 은 전체. */
    @GetMapping("/sent")
    @PreAuthorize("hasAnyRole('ADMIN','BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER')")
    public List<SentAnnouncement> sent(@AuthenticationPrincipal AuthenticatedUser actor) {
        List<Announcement> mine = actor.companyId() != null
                ? announcements.findBySenderCompanyIdOrderByCreatedAtDesc(actor.companyId())
                : announcements.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        Map<Long, List<AnnouncementRecipient>> byAnn = mine.isEmpty() ? Map.of()
                : announcementRecipients.findByAnnouncementIdIn(mine.stream().map(Announcement::getId).toList())
                    .stream().collect(Collectors.groupingBy(AnnouncementRecipient::getAnnouncementId));
        return mine.stream().map(a -> {
            List<AnnouncementRecipient> rs = byAnn.getOrDefault(a.getId(), List.of());
            int read = (int) rs.stream().filter(r -> r.getReadAt() != null).count();
            return new SentAnnouncement(a.getId(), a.getTitle(), a.getBody(), a.getCreatedAt(),
                    a.getTarget(), a.getSiteId(), rs.size(), read);
        }).toList();
    }

    /** 발신함 상세 — 수신자별 확인(읽음) 상태. 본인 회사 발송분만(ADMIN 전체), 아니면 403. */
    @GetMapping("/{id}/recipients")
    @PreAuthorize("hasAnyRole('ADMIN','BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER')")
    public List<RecipientStatus> recipients(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
        Announcement a = announcements.findById(id)
                .orElseThrow(() -> ApiException.notFound("ANNOUNCEMENT_NOT_FOUND", "공지를 찾을 수 없습니다"));
        if (actor.role() != Role.ADMIN
                && (a.getSenderCompanyId() == null || !a.getSenderCompanyId().equals(actor.companyId()))) {
            throw ApiException.forbidden("ANNOUNCEMENT_DENIED", "본인이 발송한 공지만 조회할 수 있습니다");
        }
        List<AnnouncementRecipient> rs = announcementRecipients.findByAnnouncementIdOrderByIdAsc(id);
        Map<Long, Person> byId = persons.findAllById(rs.stream().map(AnnouncementRecipient::getPersonId).toList())
                .stream().collect(Collectors.toMap(Person::getId, Function.identity()));
        return rs.stream().map(r -> {
            Person p = byId.get(r.getPersonId());
            return new RecipientStatus(r.getPersonId(),
                    p != null ? p.getName() : "작업자 #" + r.getPersonId(), r.getReadAt());
        }).toList();
    }

    /** 발신함 목록 행 — 확인율(readCount/recipientCount) 표시용. */
    public record SentAnnouncement(Long id, String title, String body, LocalDateTime createdAt,
                                   String target, Long siteId, int recipientCount, int readCount) {}

    /** 수신자별 확인 상태 — readAt NULL=미확인. */
    public record RecipientStatus(Long personId, String name, LocalDateTime readAt) {}

    public static class BroadcastRequest {
        public String title;
        public String body;
        public List<Long> personIds;
        public Long supplierId;
        /** 현장 스코프(선택) — 안전 상황판 [공지] 탭·요약이 이 값으로 현장별 확인율 집계. */
        public Long siteId;
        /** "phone"(기본) — 폰만. "all"/"watch" — 워치도 포함. */
        public String target;
    }
}
