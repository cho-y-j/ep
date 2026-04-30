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
    private final DocumentRepository docRepo;
    private final FileStorage storage;

    public PersonService(PersonRepository repo, CompanyRepository companies,
                         DocumentRepository docRepo, FileStorage storage) {
        this.repo = repo;
        this.companies = companies;
        this.docRepo = docRepo;
        this.storage = storage;
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

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Person> search(
            AuthenticatedUser actor,
            Long supplierIdParam,
            PersonRole role,
            String q,
            org.springframework.data.domain.Pageable pageable
    ) {
        Long supplierId = resolveListSupplier(actor, supplierIdParam);
        String searchTerm = (q == null || q.isBlank()) ? "" : q.trim();
        return repo.search(supplierId, role, searchTerm, pageable);
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
        if (supplier.getType() == CompanyType.BP) {
            throw ApiException.badRequest("SUPPLIER_NOT_ALLOWED", "BP사에는 인원을 등록할 수 없습니다");
        }
        validateRoles(req.roles(), supplier.getType());

        Person p = Person.builder()
                .supplierId(supplierId)
                .name(req.name())
                .birth(req.birth())
                .phone(req.phone())
                .roles(req.roles())
                .build();
        return repo.save(p);
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
        p.update(req.name(), req.birth(), req.phone(), req.roles());
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

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw ApiException.badRequest("INVALID_IMAGE", "이미지 파일만 업로드할 수 있습니다");
        }

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
        if (roles == null || roles.isEmpty()) {
            throw ApiException.badRequest("ROLES_REQUIRED", "역할을 1개 이상 선택해야 합니다");
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
        if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            requireCompany(actor);
            if (supplierIdParam != null && !supplierIdParam.equals(actor.companyId())) {
                throw ApiException.forbidden("FORBIDDEN_OTHER_COMPANY", "다른 회사의 인원을 등록할 수 없습니다");
            }
            return actor.companyId();
        }
        throw ApiException.forbidden("ROLE_NOT_ALLOWED", "인원 등록 권한이 없는 역할입니다");
    }

    private void ensureCanAccess(AuthenticatedUser actor, Long supplierId) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            if (!supplierId.equals(actor.companyId())) {
                throw ApiException.forbidden("FORBIDDEN_OTHER_COMPANY", "본인 회사의 인원만 조회 가능합니다");
            }
        }
    }

    private void ensureCanModify(AuthenticatedUser actor, Long supplierId) {
        if (actor.role() == Role.ADMIN) return;
        if ((actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER)
                && supplierId.equals(actor.companyId())) return;
        throw ApiException.forbidden("FORBIDDEN", "수정 권한이 없습니다");
    }

    private void requireCompany(AuthenticatedUser actor) {
        if (actor.companyId() == null) {
            throw ApiException.forbidden("NO_COMPANY", "소속 회사가 지정되지 않은 사용자입니다 (재로그인 필요)");
        }
    }
}
