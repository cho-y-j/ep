package com.skep.equipment;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyType;
import com.skep.equipment.dto.CreateEquipmentRequest;
import com.skep.equipment.dto.UpdateEquipmentRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.storage.FileStorage;
import com.skep.user.Role;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Transactional
public class EquipmentService {

    private final EquipmentRepository repo;
    private final CompanyRepository companies;
    private final FileStorage storage;

    public EquipmentService(EquipmentRepository repo, CompanyRepository companies, FileStorage storage) {
        this.repo = repo;
        this.companies = companies;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<Equipment> list(AuthenticatedUser actor, Long supplierIdParam, EquipmentCategory category) {
        Long supplierId = resolveListSupplier(actor, supplierIdParam);

        if (supplierId != null && category != null) {
            return repo.findBySupplierIdAndCategoryOrderByIdDesc(supplierId, category);
        }
        if (supplierId != null) {
            return repo.findBySupplierIdOrderByIdDesc(supplierId);
        }
        if (category != null) {
            return repo.findByCategoryOrderByIdDesc(category);
        }
        return repo.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public Equipment get(Long id, AuthenticatedUser actor) {
        Equipment e = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "equipment " + id + " not found"));
        ensureCanAccess(actor, e.getSupplierId());
        return e;
    }

    public Equipment create(CreateEquipmentRequest req, AuthenticatedUser actor) {
        Long supplierId = resolveCreateSupplier(actor, req.supplierId());
        Company supplier = companies.findById(supplierId)
                .orElseThrow(() -> ApiException.badRequest("SUPPLIER_NOT_FOUND", "회사 " + supplierId + " 를 찾을 수 없습니다"));
        if (supplier.getType() != CompanyType.EQUIPMENT) {
            throw ApiException.badRequest("SUPPLIER_NOT_EQUIPMENT", "장비공급사 유형이 아닌 회사에 장비를 등록할 수 없습니다");
        }
        Equipment e = Equipment.builder()
                .supplierId(supplierId)
                .vehicleNo(req.vehicleNo())
                .category(req.category())
                .model(req.model())
                .manufacturer(req.manufacturer())
                .year(req.year())
                .build();
        return repo.save(e);
    }

    public Equipment update(Long id, UpdateEquipmentRequest req, AuthenticatedUser actor) {
        Equipment e = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "equipment " + id + " not found"));
        ensureCanModify(actor, e.getSupplierId());
        e.update(req.vehicleNo(), req.category(), req.model(), req.manufacturer(), req.year());
        return e;
    }

    public void delete(Long id, AuthenticatedUser actor) {
        Equipment e = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "equipment " + id + " not found"));
        ensureCanModify(actor, e.getSupplierId());
        String photoKey = e.getPhotoKey();
        repo.delete(e);
        if (photoKey != null) storage.delete(photoKey);
    }

    public Equipment uploadPhoto(Long id, MultipartFile file, AuthenticatedUser actor) {
        Equipment e = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "equipment " + id + " not found"));
        ensureCanModify(actor, e.getSupplierId());

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw ApiException.badRequest("INVALID_IMAGE", "이미지 파일만 업로드할 수 있습니다");
        }

        String oldKey = e.getPhotoKey();
        String newKey = storage.store(file);
        e.setPhoto(newKey, contentType);
        if (oldKey != null) storage.delete(oldKey);
        return e;
    }

    public Equipment deletePhoto(Long id, AuthenticatedUser actor) {
        Equipment e = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "equipment " + id + " not found"));
        ensureCanModify(actor, e.getSupplierId());

        String oldKey = e.getPhotoKey();
        e.clearPhoto();
        if (oldKey != null) storage.delete(oldKey);
        return e;
    }

    @Transactional(readOnly = true)
    public PhotoData loadPhoto(Long id, AuthenticatedUser actor) {
        Equipment e = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "equipment " + id + " not found"));
        ensureCanAccess(actor, e.getSupplierId());
        if (e.getPhotoKey() == null) {
            throw ApiException.notFound("PHOTO_NOT_FOUND", "사진이 등록되지 않았습니다");
        }
        return new PhotoData(storage.load(e.getPhotoKey()), e.getPhotoContentType());
    }

    public record PhotoData(Resource resource, String contentType) {}

    /** ADMIN: 어떤 supplier도 OK. EQUIPMENT_SUPPLIER: 본인 회사 강제. 그 외: 본인 회사 read-only 가정. */
    private Long resolveListSupplier(AuthenticatedUser actor, Long supplierIdParam) {
        if (actor.role() == Role.ADMIN) {
            return supplierIdParam;
        }
        if (actor.role() == Role.EQUIPMENT_SUPPLIER) {
            requireCompany(actor);
            return actor.companyId();
        }
        // BP 등은 supplier_id 명시 시 그 회사만 보고, 없으면 전체 (장비 검색용)
        return supplierIdParam;
    }

    private Long resolveCreateSupplier(AuthenticatedUser actor, Long supplierIdParam) {
        if (actor.role() == Role.ADMIN) {
            if (supplierIdParam == null) {
                throw ApiException.badRequest("SUPPLIER_REQUIRED", "supplier_id 가 필요합니다 (ADMIN)");
            }
            return supplierIdParam;
        }
        if (actor.role() == Role.EQUIPMENT_SUPPLIER) {
            requireCompany(actor);
            // EQUIPMENT_SUPPLIER가 다른 회사 ID 보내면 차단
            if (supplierIdParam != null && !supplierIdParam.equals(actor.companyId())) {
                throw ApiException.forbidden("FORBIDDEN_OTHER_COMPANY", "다른 회사의 장비를 등록할 수 없습니다");
            }
            return actor.companyId();
        }
        throw ApiException.forbidden("ROLE_NOT_ALLOWED", "장비 등록 권한이 없는 역할입니다");
    }

    private void ensureCanAccess(AuthenticatedUser actor, Long supplierId) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.EQUIPMENT_SUPPLIER) {
            if (!supplierId.equals(actor.companyId())) {
                throw ApiException.forbidden("FORBIDDEN_OTHER_COMPANY", "본인 회사의 장비만 조회 가능합니다");
            }
        }
        // BP/MANPOWER/WORKER는 read-only로 모두 허용 (목록/상세). 추후 좁힐 수 있음.
    }

    private void ensureCanModify(AuthenticatedUser actor, Long supplierId) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.EQUIPMENT_SUPPLIER && supplierId.equals(actor.companyId())) return;
        throw ApiException.forbidden("FORBIDDEN", "수정 권한이 없습니다");
    }

    private void requireCompany(AuthenticatedUser actor) {
        if (actor.companyId() == null) {
            throw ApiException.forbidden("NO_COMPANY", "소속 회사가 지정되지 않은 사용자입니다 (재로그인 필요)");
        }
    }
}
