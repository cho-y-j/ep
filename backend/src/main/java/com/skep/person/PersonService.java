package com.skep.person;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyType;
import com.skep.document.DocumentRepository;
import com.skep.document.OwnerType;
import com.skep.person.dto.CreatePersonRequest;
import com.skep.person.dto.UpdatePersonRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteParticipant;
import com.skep.site.SiteParticipantRepository;
import com.skep.site.SiteParticipantStatus;
import com.skep.site.SiteRepository;
import com.skep.storage.FileStorage;
import com.skep.user.Role;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class PersonService {

    private static final int EXPIRING_DAYS = 30;

    private final PersonRepository repo;
    private final CompanyRepository companies;
    private final com.skep.company.CompanyService companyService;
    private final DocumentRepository docRepo;
    private final FileStorage storage;
    private final SiteRepository sites;
    private final SiteParticipantRepository participants;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public PersonService(PersonRepository repo, CompanyRepository companies,
                         com.skep.company.CompanyService companyService,
                         DocumentRepository docRepo, FileStorage storage,
                         SiteRepository sites, SiteParticipantRepository participants,
                         org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.companies = companies;
        this.companyService = companyService;
        this.docRepo = docRepo;
        this.storage = storage;
        this.sites = sites;
        this.participants = participants;
        this.passwordEncoder = passwordEncoder;
    }

    /** 인원 ID 리스트에 대해 만료 임박 서류 수를 owner_id별로 그룹핑해서 반환. */
    @Transactional(readOnly = true)
    public Map<Long, Long> expiringCountsByPersonIds(List<Long> personIds) {
        if (personIds.isEmpty()) return Map.of();
        LocalDate maxDate = LocalDate.now().plusDays(EXPIRING_DAYS);
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : docRepo.countExpiringGroupedByOwner(OwnerType.PERSON, personIds, maxDate)) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    /** 인원 ID 리스트에 대해 전체 서류 수를 owner_id별로 그룹핑해서 반환. */
    @Transactional(readOnly = true)
    public Map<Long, Long> documentCountsByPersonIds(List<Long> personIds) {
        if (personIds.isEmpty()) return Map.of();
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : docRepo.countTotalGroupedByOwner(OwnerType.PERSON, personIds)) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    /**
     * 권한별 가시성 (S-3.1 정책 Person 적용):
     * - ADMIN: 전체.
     * - EQUIPMENT_SUPPLIER / MANPOWER_SUPPLIER: 자기 회사 자원만.
     * - BP: 자기 사이트의 ACTIVE 참여 공급사 (장비+인력 양쪽) 인원만.
     * - WORKER: 차단.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Person> search(
            AuthenticatedUser actor,
            Long supplierIdParam,
            PersonRole role,
            String q,
            org.springframework.data.domain.Pageable pageable
    ) {
        String searchTerm = (q == null || q.isBlank()) ? "" : q.trim();

        if (actor.role() == Role.BP) {
            requireCompany(actor);
            List<Long> visibleSupplierIds = visibleSupplierIdsForBp(actor.companyId());
            if (visibleSupplierIds.isEmpty()) {
                return org.springframework.data.domain.Page.empty(pageable);
            }
            if (supplierIdParam != null && !visibleSupplierIds.contains(supplierIdParam)) {
                return org.springframework.data.domain.Page.empty(pageable);
            }
            List<Long> targetSuppliers = supplierIdParam != null
                    ? List.of(supplierIdParam)
                    : visibleSupplierIds;
            return repo.searchInSuppliers(targetSuppliers, role, searchTerm, pageable);
        }
        if (actor.role() == Role.WORKER) {
            throw ApiException.forbidden("PERSON_DENIED", "인원 조회 권한이 없습니다");
        }

        // V77: 공급사 목록은 본인 + 직속 자식(있으면) 자원 포함. 자식이 없으면 selfAndChildren = {본인} 이라 기존과 동일.
        if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            requireCompany(actor);
            List<Long> scope = companyService.selfAndChildren(actor.companyId());
            if (supplierIdParam != null) {
                if (!scope.contains(supplierIdParam)) return org.springframework.data.domain.Page.empty(pageable);
                return repo.search(supplierIdParam, role, searchTerm, pageable);
            }
            return repo.searchInSuppliers(scope, role, searchTerm, pageable);
        }

        Long supplierId = resolveListSupplier(actor, supplierIdParam);
        return repo.search(supplierId, role, searchTerm, pageable);
    }

    /** BP 시점에서 인원 조회 가능한 공급사 회사 ID 들.
     *  - 본인 회사 (#5 BP 직속 운전수/지휘자)
     *  - 자기 사이트에 ACTIVE 참여 중인 공급사 (장비+인력)
     *  create 권한은 이미 본인 회사 허용 (#5) — search 도 같은 정책으로 맞춤. */
    private List<Long> visibleSupplierIdsForBp(Long bpCompanyId) {
        java.util.Set<Long> ids = new java.util.LinkedHashSet<>();
        ids.add(bpCompanyId);
        List<Long> siteIds = sites.findByBpCompanyIdOrderByIdDesc(bpCompanyId).stream()
                .map(Site::getId).toList();
        if (!siteIds.isEmpty()) {
            participants.findBySiteIdIn(siteIds).stream()
                    .filter(p -> p.getStatus() == SiteParticipantStatus.ACTIVE)
                    .map(SiteParticipant::getCompanyId)
                    .forEach(ids::add);
        }
        return new java.util.ArrayList<>(ids);
    }

    @Transactional(readOnly = true)
    public Person get(Long id, AuthenticatedUser actor) {
        Person p = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("PERSON_NOT_FOUND", "person " + id + " not found"));
        ensureCanAccess(actor, p.getSupplierId());
        return p;
    }

    public Person create(CreatePersonRequest req, AuthenticatedUser actor) {
        Long supplierId = resolveCreateSupplier(actor, req.supplierId());
        Company supplier = companies.findById(supplierId)
                .orElseThrow(() -> ApiException.badRequest("SUPPLIER_NOT_FOUND", "회사 " + supplierId + " 를 찾을 수 없습니다"));
        // BP 회사도 자체 인원 보유 가능 (직속 운전수/지휘자 등) — 정책 확장 (#5)
        validateRoles(req.roles(), supplier.getType());

        Person p = Person.builder()
                .supplierId(supplierId)
                .name(req.name())
                .birth(req.birth())
                .phone(req.phone())
                .roles(req.roles())
                .employeeNo(req.employeeNo())
                .jobTitle(req.jobTitle())
                .team(req.team())
                .qualification(req.qualification())
                .address(req.address())
                .email(req.email())
                .hiredAt(req.hiredAt())
                .status(req.status())
                .employmentType(req.employmentType())
                .attendanceCode(generateUniqueAttendanceCode())
                .build();
        applyCredentials(p, req.username(), req.password());
        return repo.save(p);
    }

    /** 작업자 앱 로그인 계정(아이디/비번) 설정·변경. 아이디는 전역 유일, 비번은 BCrypt 해시 저장. */
    private void applyCredentials(Person p, String username, String password) {
        String u = username == null ? null : username.trim();
        boolean hasUser = u != null && !u.isBlank();
        boolean hasPw = password != null && !password.isBlank();
        if (!hasUser && !hasPw) return;
        if (hasUser) {
            repo.findByUsername(u)
                    .filter(other -> !other.getId().equals(p.getId()))
                    .ifPresent(other -> { throw ApiException.conflict("USERNAME_TAKEN", "이미 사용 중인 아이디입니다: " + u); });
            String hash = hasPw ? passwordEncoder.encode(password) : p.getPasswordHash();
            if (hash == null) throw ApiException.badRequest("PASSWORD_REQUIRED", "비밀번호를 함께 입력하세요");
            p.setCredentials(u, hash);
        } else { // 비번만 변경 — 기존 아이디 유지
            if (p.getUsername() == null) throw ApiException.badRequest("USERNAME_REQUIRED", "아이디를 먼저 설정하세요");
            p.setCredentials(p.getUsername(), passwordEncoder.encode(password));
        }
    }

    /** 6자리 영숫자 코드 — UNIQUE 충돌 시 최대 20회 재시도. */
    private String generateUniqueAttendanceCode() {
        for (int i = 0; i < 20; i++) {
            String code = AttendanceCodeGenerator.next();
            if (repo.findByAttendanceCode(code).isEmpty()) return code;
        }
        throw ApiException.conflict("ATTENDANCE_CODE_EXHAUSTED", "출퇴근 코드 충돌이 반복됩니다");
    }

    public Person update(Long id, UpdatePersonRequest req, AuthenticatedUser actor) {
        Person p = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("PERSON_NOT_FOUND", "person " + id + " not found"));
        ensureCanModify(actor, p.getSupplierId());

        if (req.roles() != null) {
            Company supplier = companies.findById(p.getSupplierId())
                    .orElseThrow(() -> ApiException.badRequest("SUPPLIER_NOT_FOUND", "supplier missing"));
            validateRoles(req.roles(), supplier.getType());
        }
        p.update(req.name(), req.birth(), req.phone(), req.roles(),
                req.employeeNo(), req.jobTitle(), req.team(), req.qualification(),
                req.address(), req.email(), req.hiredAt(),
                req.status(), req.employmentType());
        applyCredentials(p, req.username(), req.password());
        return p;
    }

    public void delete(Long id, AuthenticatedUser actor) {
        Person p = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("PERSON_NOT_FOUND", "person " + id + " not found"));
        ensureCanModify(actor, p.getSupplierId());
        String photoKey = p.getPhotoKey();
        repo.delete(p);
        if (photoKey != null) storage.delete(photoKey);
    }

    public Person uploadPhoto(Long id, MultipartFile file, AuthenticatedUser actor) {
        Person p = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("PERSON_NOT_FOUND", "person " + id + " not found"));
        ensureCanModify(actor, p.getSupplierId());

        com.skep.common.ImageUploadValidator.validateOrThrow(file);
        String contentType = file.getContentType();

        String oldKey = p.getPhotoKey();
        String newKey = storage.store(file);
        p.setPhoto(newKey, contentType);
        if (oldKey != null) storage.delete(oldKey);
        return p;
    }

    public Person deletePhoto(Long id, AuthenticatedUser actor) {
        Person p = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("PERSON_NOT_FOUND", "person " + id + " not found"));
        ensureCanModify(actor, p.getSupplierId());

        String oldKey = p.getPhotoKey();
        p.clearPhoto();
        if (oldKey != null) storage.delete(oldKey);
        return p;
    }

    @Transactional(readOnly = true)
    public PhotoData loadPhoto(Long id, AuthenticatedUser actor) {
        Person p = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("PERSON_NOT_FOUND", "person " + id + " not found"));
        ensureCanAccess(actor, p.getSupplierId());
        if (p.getPhotoKey() == null) {
            throw ApiException.notFound("PHOTO_NOT_FOUND", "사진이 등록되지 않았습니다");
        }
        return new PhotoData(storage.load(p.getPhotoKey()), p.getPhotoContentType());
    }

    public record PhotoData(Resource resource, String contentType) {}

    private void validateRoles(Set<PersonRole> roles, CompanyType companyType) {
        // 간소 등록 허용 — 역할 없이 등록 후 상세에서 추가 가능. 빈/null 이면 검증 스킵.
        if (roles == null || roles.isEmpty()) {
            return;
        }
        for (PersonRole r : roles) {
            if (!r.isAllowedFor(companyType)) {
                throw ApiException.badRequest("ROLE_COMPANY_TYPE_MISMATCH",
                        "역할 " + r + " 은(는) " + companyType + " 회사에 등록할 수 없습니다");
            }
        }
    }

    private Long resolveListSupplier(AuthenticatedUser actor, Long supplierIdParam) {
        if (actor.role() == Role.ADMIN) return supplierIdParam;
        if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            requireCompany(actor);
            return actor.companyId();
        }
        return supplierIdParam;
    }

    private Long resolveCreateSupplier(AuthenticatedUser actor, Long supplierIdParam) {
        if (actor.role() == Role.ADMIN) {
            if (supplierIdParam == null) {
                throw ApiException.badRequest("SUPPLIER_REQUIRED", "supplier_id 가 필요합니다 (ADMIN)");
            }
            return supplierIdParam;
        }
        if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER || actor.role() == Role.BP) {
            requireCompany(actor);
            if (supplierIdParam == null) return actor.companyId();
            // V77: 본인 또는 직속 자식(협력사) 소유로 대행 등록 허용. 그 외 회사는 차단.
            if (!companyService.selfAndChildren(actor.companyId()).contains(supplierIdParam)) {
                throw ApiException.forbidden("FORBIDDEN_OTHER_COMPANY", "다른 회사의 인원을 등록할 수 없습니다");
            }
            return supplierIdParam;
        }
        throw ApiException.forbidden("ROLE_NOT_ALLOWED", "인원 등록 권한이 없는 역할입니다");
    }

    private void ensureCanAccess(AuthenticatedUser actor, Long supplierId) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            // V77: 읽기만 본인 + 직속 자식 확장(부모→자식 단방향). 자식/형제는 selfAndChildren={본인} 이라 자동 차단.
            if (!companyService.selfAndChildren(actor.companyId()).contains(supplierId)) {
                throw ApiException.forbidden("FORBIDDEN_OTHER_COMPANY", "본인 회사의 인원만 조회 가능합니다");
            }
            return;
        }
        if (actor.role() == Role.BP) {
            requireCompany(actor);
            // BP 본인 회사 인원 (#5 직속 운전수) + 자기 사이트 ACTIVE 참여 공급사 인원
            if (supplierId.equals(actor.companyId())) return;
            if (!visibleSupplierIdsForBp(actor.companyId()).contains(supplierId)) {
                throw ApiException.forbidden("PERSON_VIEW_DENIED",
                        "자기 회사 또는 자기 사이트 ACTIVE 참여공급사 인원만 조회할 수 있습니다");
            }
            return;
        }
        // WORKER 차단.
        throw ApiException.forbidden("PERSON_DENIED", "인원 조회 권한이 없습니다");
    }

    private void ensureCanModify(AuthenticatedUser actor, Long supplierId) {
        if (actor.role() == Role.ADMIN) return;
        // #5: BP 본인 회사 직속 인원도 수정 가능 (운전수/지휘자 등).
        if ((actor.role() == Role.EQUIPMENT_SUPPLIER
                || actor.role() == Role.MANPOWER_SUPPLIER
                || actor.role() == Role.BP)
                && supplierId.equals(actor.companyId())) return;
        throw ApiException.forbidden("FORBIDDEN", "수정 권한이 없습니다");
    }

    private void requireCompany(AuthenticatedUser actor) {
        if (actor.companyId() == null) {
            throw ApiException.forbidden("NO_COMPANY", "소속 회사가 지정되지 않은 사용자입니다 (재로그인 필요)");
        }
    }
}
