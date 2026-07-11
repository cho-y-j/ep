package com.skep.document;

import com.skep.alimtalk.AlimTalkService;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationRepository;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.user.User;
import com.skep.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 서류 만료 임박 in-app 알림 — 매일 08:40, D-30/D-7 자원(장비/인원) 소유 공급사에게 통지.
 *
 * 소스는 만료 관리 화면(AdminExpiringDocumentsPage → DocumentService.expiringQueue)과 동일한
 * {@link DocumentRepository#findExpiringAll(LocalDate)} chain-head 서류. 그중 정확히 30·7일 남은 건만.
 *
 * 스팸 방지: 이산 시점(30/7일 전)만 + 같은 날 재실행 중복 생성 가드(재기동/수동 트리거 대비).
 * 알림톡/SMS 외부발송은 범위 아님 — in-app 알림만 생성.
 */
@Component
@RequiredArgsConstructor
public class ExpiringDocumentScheduler {

    private static final Set<Integer> WINDOWS = Set.of(30, 7);

    private final DocumentRepository documents;
    private final DocumentTypeRepository documentTypes;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final NotificationService notifications;
    private final NotificationRepository notificationRepo;
    private final AlimTalkService alimTalk;
    private final UserRepository users;

    /** B2: 만료 알림 외부발송(SMS) 게이트 — 기본 OFF. OFF 시 발송 경로 진입 자체 차단(기존 in-app 동작 불변). */
    @Value("${skep.alimtalk.expiry-notify.enabled:false}")
    private boolean expiryNotifyEnabled;

    @Scheduled(cron = "0 40 8 * * *")
    @Transactional
    public void remindExpiring() {
        LocalDate today = LocalDate.now();
        // D-30 이내 chain-head 서류 중 정확히 30·7일 남은 건만.
        List<Document> due = new ArrayList<>();
        for (Document d : documents.findExpiringAll(today.plusDays(30))) {
            if (d.getExpiryDate() == null) continue;
            long days = ChronoUnit.DAYS.between(today, d.getExpiryDate());
            if (days >= 0 && WINDOWS.contains((int) days)) due.add(d);
        }
        if (due.isEmpty()) return;

        // 소유 자원 batch 조회 — supplier_id + 라벨 (N+1 회피).
        List<Long> equipIds = due.stream().filter(d -> d.getOwnerType() == OwnerType.EQUIPMENT)
                .map(Document::getOwnerId).distinct().toList();
        List<Long> personIds = due.stream().filter(d -> d.getOwnerType() == OwnerType.PERSON)
                .map(Document::getOwnerId).distinct().toList();
        Map<Long, Equipment> eqMap = new HashMap<>();
        equipmentRepo.findAllById(equipIds).forEach(e -> eqMap.put(e.getId(), e));
        Map<Long, Person> personMap = new HashMap<>();
        personRepo.findAllById(personIds).forEach(p -> personMap.put(p.getId(), p));
        Map<Long, String> typeNameCache = new HashMap<>();

        LocalDateTime todayStart = today.atStartOfDay();
        for (Document d : due) {
            Long supplierId;
            String ownerLabel;
            String linkType;
            if (d.getOwnerType() == OwnerType.EQUIPMENT) {
                Equipment e = eqMap.get(d.getOwnerId());
                if (e == null || e.getSupplierId() == null) continue;
                supplierId = e.getSupplierId();
                ownerLabel = e.getVehicleNo() != null ? e.getVehicleNo()
                        : (e.getModel() != null ? e.getModel() : "장비#" + e.getId());
                linkType = "EQUIPMENT";
            } else if (d.getOwnerType() == OwnerType.PERSON) {
                Person p = personMap.get(d.getOwnerId());
                if (p == null || p.getSupplierId() == null) continue;
                supplierId = p.getSupplierId();
                ownerLabel = p.getName();
                linkType = "PERSON";
            } else {
                continue; // COMPANY 등 — 자원 소유 공급사 범위 밖.
            }
            // 같은 날 (회사, type, 링크대상) 이미 생성됐으면 skip — 재실행/재기동 중복 방지.
            if (notificationRepo.existsByTargetCompanyIdAndTypeAndLinkTypeAndLinkIdAndCreatedAtGreaterThanEqual(
                    supplierId, NotificationType.DOCUMENT_EXPIRING, linkType, d.getOwnerId(), todayStart)) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(today, d.getExpiryDate());
            String typeName = typeNameCache.computeIfAbsent(d.getDocumentTypeId(),
                    id -> documentTypes.findById(id).map(DocumentType::getName).orElse("서류"));
            String msg = ownerLabel + " " + typeName + " 만료 " + days + "일 남음 (만료일 " + d.getExpiryDate() + ")";
            notifications.sendToCompany(supplierId, NotificationType.DOCUMENT_EXPIRING,
                    "서류 만료 임박", msg, linkType, d.getOwnerId(), null);
            // B2: 외부발송 게이트(기본 OFF). ON 일 때만 in-app dedup 통과 건을 자원 소유 공급사 마스터 phone 으로 SMS.
            if (expiryNotifyEnabled) {
                for (User u : users.findByCompanyIdAndIsCompanyAdminTrue(supplierId)) {
                    if (u.getPhone() != null && !u.getPhone().isBlank()) {
                        alimTalk.sendSmsText(u.getPhone(), "[SKEP] " + msg, null);
                    }
                }
            }
        }
    }
}
