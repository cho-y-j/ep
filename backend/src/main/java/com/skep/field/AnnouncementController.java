package com.skep.field;

import com.skep.common.ApiException;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanPerson;
import com.skep.workplan.WorkPlanPersonRepository;
import com.skep.workplan.WorkPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** ADMIN/BP 공지 발행 — target=phone(기본) 폰만, target=all 폰+워치 동시. */
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final PersonRepository persons;
    private final FieldFcmService fcm;
    private final WorkPlanRepository workPlans;
    private final WorkPlanPersonRepository workPlanPersons;

    @PostMapping("/broadcast")
    @PreAuthorize("hasAnyRole('ADMIN','BP')")
    public Map<String, Object> broadcast(@RequestBody BroadcastRequest req,
                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        if (req.title == null || req.title.isBlank()) {
            throw ApiException.badRequest("NO_TITLE", "title 필수");
        }
        if (req.body == null || req.body.isBlank()) {
            throw ApiException.badRequest("NO_BODY", "body 필수");
        }
        boolean isAdmin = actor.role() == Role.ADMIN;
        // 폰 토큰 보유 + (옵션: 워치 토큰만 보유) person 양쪽 모두 수집해 union.
        List<Person> phoneTargets;
        List<Person> watchTargets;
        boolean includeWatch = "all".equalsIgnoreCase(req.target) || "watch".equalsIgnoreCase(req.target);
        if (req.personIds != null && !req.personIds.isEmpty()) {
            if (!isAdmin) ensureBpOwnsPersons(actor, req.personIds);
            phoneTargets = persons.findByFcmTokenIsNotNullAndIdIn(req.personIds);
            watchTargets = includeWatch ? persons.findByWatchFcmTokenIsNotNullAndIdIn(req.personIds) : List.of();
        } else if (req.supplierId != null) {
            if (!isAdmin) ensureBpOwnsSupplier(actor, req.supplierId);
            phoneTargets = persons.findByFcmTokenIsNotNullAndSupplierIdIn(List.of(req.supplierId));
            watchTargets = includeWatch ? persons.findByWatchFcmTokenIsNotNullAndSupplierIdIn(List.of(req.supplierId)) : List.of();
        } else {
            // 전체 작업자 대상 — ADMIN 만 허용. BP 는 크로스테넌트 방지를 위해 대상 지정 필수.
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
        return Map.of(
                "attempted", phoneSent + watchSent,
                "phone_sent", phoneSent,
                "watch_sent", watchSent,
                "targets", uniqueTargetIds.size()
        );
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

    public static class BroadcastRequest {
        public String title;
        public String body;
        public List<Long> personIds;
        public Long supplierId;
        /** "phone"(기본) — 폰만. "all"/"watch" — 워치도 포함. */
        public String target;
    }
}
