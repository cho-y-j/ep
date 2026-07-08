package com.skep.field;

import com.skep.common.ApiException;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** ADMIN/BP 공지 발행 — target=phone(기본) 폰만, target=all 폰+워치 동시. */
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final PersonRepository persons;
    private final FieldFcmService fcm;

    @PostMapping("/broadcast")
    @PreAuthorize("hasAnyRole('ADMIN','BP')")
    public Map<String, Object> broadcast(@RequestBody BroadcastRequest req) {
        if (req.title == null || req.title.isBlank()) {
            throw ApiException.badRequest("NO_TITLE", "title 필수");
        }
        if (req.body == null || req.body.isBlank()) {
            throw ApiException.badRequest("NO_BODY", "body 필수");
        }
        // 폰 토큰 보유 + (옵션: 워치 토큰만 보유) person 양쪽 모두 수집해 union.
        List<Person> phoneTargets;
        List<Person> watchTargets;
        boolean includeWatch = "all".equalsIgnoreCase(req.target) || "watch".equalsIgnoreCase(req.target);
        if (req.personIds != null && !req.personIds.isEmpty()) {
            phoneTargets = persons.findByFcmTokenIsNotNullAndIdIn(req.personIds);
            watchTargets = includeWatch ? persons.findByWatchFcmTokenIsNotNullAndIdIn(req.personIds) : List.of();
        } else if (req.supplierId != null) {
            phoneTargets = persons.findByFcmTokenIsNotNullAndSupplierIdIn(List.of(req.supplierId));
            watchTargets = includeWatch ? persons.findByWatchFcmTokenIsNotNullAndSupplierIdIn(List.of(req.supplierId)) : List.of();
        } else {
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

    public static class BroadcastRequest {
        public String title;
        public String body;
        public List<Long> personIds;
        public Long supplierId;
        /** "phone"(기본) — 폰만. "all"/"watch" — 워치도 포함. */
        public String target;
    }
}
