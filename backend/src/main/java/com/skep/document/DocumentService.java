package com.skep.document;

import com.skep.common.ApiException;
import com.skep.document.dto.DocumentResponse;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.storage.FileStorage;
import com.skep.user.Role;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class DocumentService {

    private final DocumentRepository docRepo;
    private final DocumentTypeRepository typeRepo;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final FileStorage storage;

    public DocumentService(DocumentRepository docRepo, DocumentTypeRepository typeRepo,
                           EquipmentRepository equipmentRepo, PersonRepository personRepo,
                           FileStorage storage) {
        this.docRepo = docRepo;
        this.typeRepo = typeRepo;
        this.equipmentRepo = equipmentRepo;
        this.personRepo = personRepo;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listForOwner(OwnerType ownerType, Long ownerId, AuthenticatedUser actor) {
        Long ownerSupplierId = ownerSupplierIdOrThrow(ownerType, ownerId);
        ensureCanAccess(actor, ownerSupplierId);

        List<Document> docs = docRepo.findByOwnerTypeAndOwnerIdOrderByIdDesc(ownerType, ownerId);
        Map<Long, String> typeNames = new HashMap<>();
        return docs.stream().map(d -> {
            String name = typeNames.computeIfAbsent(d.getDocumentTypeId(),
                    id -> typeRepo.findById(id).map(DocumentType::getName).orElse("(삭제됨)"));
            return DocumentResponse.from(d, name);
        }).toList();
    }

    public DocumentResponse upload(OwnerType ownerType, Long ownerId, Long documentTypeId,
                                   LocalDate expiryDate, MultipartFile file, AuthenticatedUser actor) {
        Long ownerSupplierId = ownerSupplierIdOrThrow(ownerType, ownerId);
        ensureCanModify(actor, ownerSupplierId);

        DocumentType type = typeRepo.findById(documentTypeId).orElseThrow(() ->
                ApiException.badRequest("DOCUMENT_TYPE_NOT_FOUND", "document type " + documentTypeId + " not found"));
        if (type.getAppliesTo() != ownerType) {
            throw ApiException.badRequest("DOCUMENT_TYPE_OWNER_MISMATCH",
                    type.getName() + " 는 " + ownerType + " 서류가 아닙니다");
        }
        if (type.isHasExpiry() && expiryDate == null) {
            throw ApiException.badRequest("EXPIRY_REQUIRED", type.getName() + " 는 만료일 입력 필수입니다");
        }

        String key = storage.store(file);
        Document doc = Document.builder()
                .documentTypeId(documentTypeId)
                .ownerType(ownerType)
                .ownerId(ownerId)
                .fileKey(key)
                .fileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed")
                .fileSize(file.getSize())
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .expiryDate(expiryDate)
                .uploadedBy(actor.id())
                .build();
        docRepo.save(doc);
        return DocumentResponse.from(doc, type.getName());
    }

    @Transactional(readOnly = true)
    public Document getForDownload(Long documentId, AuthenticatedUser actor) {
        Document d = docRepo.findById(documentId).orElseThrow(() ->
                ApiException.notFound("DOCUMENT_NOT_FOUND", "document " + documentId + " not found"));
        Long supplierId = ownerSupplierIdOrThrow(d.getOwnerType(), d.getOwnerId());
        ensureCanAccess(actor, supplierId);
        return d;
    }

    public Resource loadFile(Document d) {
        return storage.load(d.getFileKey());
    }

    public DocumentResponse updateExpiry(Long id, LocalDate expiryDate, AuthenticatedUser actor) {
        Document d = docRepo.findById(id).orElseThrow(() ->
                ApiException.notFound("DOCUMENT_NOT_FOUND", "document " + id + " not found"));
        Long supplierId = ownerSupplierIdOrThrow(d.getOwnerType(), d.getOwnerId());
        ensureCanModify(actor, supplierId);
        d.updateExpiry(expiryDate);
        DocumentType type = typeRepo.findById(d.getDocumentTypeId()).orElseThrow();
        return DocumentResponse.from(d, type.getName());
    }

    public DocumentResponse setVerified(Long id, boolean verified, AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("VERIFY_ADMIN_ONLY", "검증 표시는 관리자만 가능합니다");
        }
        Document d = docRepo.findById(id).orElseThrow(() ->
                ApiException.notFound("DOCUMENT_NOT_FOUND", "document " + id + " not found"));
        if (verified) d.markVerified(); else d.unmarkVerified();
        DocumentType type = typeRepo.findById(d.getDocumentTypeId()).orElseThrow();
        return DocumentResponse.from(d, type.getName());
    }

    public void delete(Long id, AuthenticatedUser actor) {
        Document d = docRepo.findById(id).orElseThrow(() ->
                ApiException.notFound("DOCUMENT_NOT_FOUND", "document " + id + " not found"));
        Long supplierId = ownerSupplierIdOrThrow(d.getOwnerType(), d.getOwnerId());
        ensureCanModify(actor, supplierId);
        String key = d.getFileKey();
        docRepo.delete(d);
        // 파일은 마지막에 삭제. 트랜잭션 롤백 시 DB row 는 살아있고 파일만 사라지는 상황 방지엔 좀 부족하지만 실용적.
        storage.delete(key);
    }

    private Long ownerSupplierIdOrThrow(OwnerType ownerType, Long ownerId) {
        if (ownerType == OwnerType.EQUIPMENT) {
            Equipment e = equipmentRepo.findById(ownerId).orElseThrow(() ->
                    ApiException.notFound("EQUIPMENT_NOT_FOUND", "equipment " + ownerId + " not found"));
            return e.getSupplierId();
        }
        Person p = personRepo.findById(ownerId).orElseThrow(() ->
                ApiException.notFound("PERSON_NOT_FOUND", "person " + ownerId + " not found"));
        return p.getSupplierId();
    }

    private void ensureCanAccess(AuthenticatedUser actor, Long ownerSupplierId) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            if (!ownerSupplierId.equals(actor.companyId())) {
                throw ApiException.forbidden("FORBIDDEN_OTHER_COMPANY", "본인 회사의 서류만 접근 가능합니다");
            }
        }
        // BP/WORKER 등은 read-only로 허용 (운영 정책 따라 추후 좁힐 수 있음)
    }

    private void ensureCanModify(AuthenticatedUser actor, Long ownerSupplierId) {
        if (actor.role() == Role.ADMIN) return;
        if ((actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER)
                && ownerSupplierId.equals(actor.companyId())) return;
        throw ApiException.forbidden("FORBIDDEN", "수정 권한이 없습니다");
    }
}
