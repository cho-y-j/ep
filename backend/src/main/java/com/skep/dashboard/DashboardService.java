package com.skep.dashboard;

import com.skep.company.CompanyRepository;
import com.skep.dashboard.dto.DashboardSummary;
import com.skep.dashboard.dto.ExpiringDocumentItem;
import com.skep.document.Document;
import com.skep.document.DocumentRepository;
import com.skep.document.DocumentType;
import com.skep.document.DocumentTypeRepository;
import com.skep.document.OwnerType;
import com.skep.equipment.EquipmentCategory;
import com.skep.equipment.EquipmentRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import com.skep.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int EXPIRING_DAYS_WINDOW = 30;
    private static final int EXPIRING_LIST_LIMIT = 20;

    private final UserRepository userRepo;
    private final CompanyRepository companyRepo;
    private final PersonRepository personRepo;
    private final EquipmentRepository equipmentRepo;
    private final DocumentRepository docRepo;
    private final DocumentTypeRepository typeRepo;

    public DashboardService(UserRepository userRepo, CompanyRepository companyRepo,
                            PersonRepository personRepo, EquipmentRepository equipmentRepo,
                            DocumentRepository docRepo, DocumentTypeRepository typeRepo) {
        this.userRepo = userRepo;
        this.companyRepo = companyRepo;
        this.personRepo = personRepo;
        this.equipmentRepo = equipmentRepo;
        this.docRepo = docRepo;
        this.typeRepo = typeRepo;
    }

    public DashboardSummary summary(AuthenticatedUser actor) {
        boolean admin = actor.role() == Role.ADMIN;
        Long companyId = actor.companyId();
        LocalDate today = LocalDate.now();
        LocalDate maxDate = today.plusDays(EXPIRING_DAYS_WINDOW);

        // counts
        Long personsCount;
        Long equipmentCount;
        Long companiesCount = null;
        Long usersPending = null;
        long expiringCount;
        long unverifiedCount;
        List<Document> expiringDocs;

        if (admin) {
            personsCount = personRepo.count();
            equipmentCount = equipmentRepo.count();
            companiesCount = companyRepo.count();
            usersPending = userRepo.findAll().stream().filter(u -> !u.isEnabled()).count();
            expiringCount = docRepo.countExpiringByDate(maxDate);
            unverifiedCount = docRepo.countByVerified(false);
            expiringDocs = limit(docRepo.findExpiringAll(maxDate));
        } else if ((actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) && companyId != null) {
            personsCount = personRepo.countBySupplierId(companyId);
            equipmentCount = equipmentRepo.countBySupplierId(companyId);
            expiringDocs = limit(docRepo.findExpiringForCompany(companyId, maxDate));
            expiringCount = expiringDocs.size();
            unverifiedCount = expiringDocs.stream().filter(d -> !d.isVerified()).count(); // 회사 범위만
        } else {
            // BP/WORKER 등 — 기본값 0
            personsCount = 0L;
            equipmentCount = 0L;
            expiringCount = 0;
            unverifiedCount = 0;
            expiringDocs = List.of();
        }

        // expiringDocs → ExpiringDocumentItem (owner name 포함)
        Map<Long, String> typeNameCache = new HashMap<>();
        Map<Long, String> ownerNameCache = new HashMap<>();
        List<ExpiringDocumentItem> expiringItems = expiringDocs.stream().map(d -> {
            String typeName = typeNameCache.computeIfAbsent(d.getDocumentTypeId(),
                    id -> typeRepo.findById(id).map(DocumentType::getName).orElse("(?)"));
            String ownerName = ownerNameCache.computeIfAbsent(
                    cacheKey(d.getOwnerType(), d.getOwnerId()),
                    k -> resolveOwnerName(d.getOwnerType(), d.getOwnerId())
            );
            long daysLeft = ChronoUnit.DAYS.between(today, d.getExpiryDate());
            return new ExpiringDocumentItem(
                    d.getId(),
                    d.getDocumentTypeId(),
                    typeName,
                    d.getOwnerType(),
                    d.getOwnerId(),
                    ownerName,
                    d.getExpiryDate(),
                    daysLeft
            );
        }).toList();

        return new DashboardSummary(
                new DashboardSummary.Counts(
                        personsCount,
                        equipmentCount,
                        companiesCount,
                        usersPending,
                        expiringCount,
                        unverifiedCount
                ),
                expiringItems
        );
    }

    private String resolveOwnerName(OwnerType type, Long id) {
        if (type == OwnerType.EQUIPMENT) {
            return equipmentRepo.findById(id).map(e -> {
                String label = e.getVehicleNo() != null ? e.getVehicleNo()
                        : (e.getModel() != null ? e.getModel()
                        : categoryLabel(e.getCategory()));
                return label;
            }).orElse("(삭제됨)");
        }
        return personRepo.findById(id).map(Person::getName).orElse("(삭제됨)");
    }

    private static String categoryLabel(EquipmentCategory c) {
        return switch (c) {
            case EXCAVATOR -> "굴삭기";
            case WHEEL_LOADER -> "휠로더";
            case CRANE -> "크레인";
            case FORKLIFT -> "지게차";
            case DOZER -> "도저";
            case GRADER -> "그레이더";
            case AERIAL_LIFT -> "고소작업차";
            case PUMP_TRUCK -> "펌프카";
            case ATTACHMENT -> "어태치먼트";
        };
    }

    private static <T> List<T> limit(List<T> src) {
        return src.size() > EXPIRING_LIST_LIMIT ? src.subList(0, EXPIRING_LIST_LIMIT) : src;
    }

    private static Long cacheKey(OwnerType t, Long id) {
        // 단순 키: ownerType ordinal * 1e9 + id
        return (long) t.ordinal() * 1_000_000_000L + id;
    }
}
