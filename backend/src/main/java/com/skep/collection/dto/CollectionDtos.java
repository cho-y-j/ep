package com.skep.collection.dto;

import com.skep.document.OwnerType;

import java.time.LocalDateTime;
import java.util.List;

/** 서류 수집 링크 관련 요청/응답 DTO 모음. JSON 전역 SNAKE_CASE — 필드는 camelCase record. */
public final class CollectionDtos {
    private CollectionDtos() {}

    /**
     * 생성 요청의 대상 1건 — 대상마다 필수/선택 서류타입을 따로 고른다.
     * 갱신형: ownerId 로 기존 자원 지정. 등록형: ownerId=null, plannedType(장비종류 code / 역할 name)+quantity 로
     * 미등록 슬롯 N개 생성(공개 링크에서 값 입력 시 자원 생성). 한 요청에 갱신형+등록형 혼합 금지.
     */
    public record CreateTarget(
            OwnerType ownerType,
            Long ownerId,
            List<Long> requiredTypeIds,
            List<Long> optionalTypeIds,
            String plannedType,
            Integer quantity
    ) {}

    /** 수집 요청 생성 — 대상 N개(배열 순서 = sort_order) + 받는사람. 등록형이면 targetCompanyId(소유 협력업체) 필수. */
    public record CreateRequest(
            String title,
            String recipientName,
            String recipientPhone,
            String recipientEmail,
            Long targetCompanyId,
            List<CreateTarget> targets
    ) {}

    /** 작성자(인증) 화면용 항목. id = 공개 업로드 단위. */
    public record ItemResponse(
            Long id,
            Long documentTypeId,
            String documentTypeName,
            boolean required,
            int sortOrder,
            boolean uploaded,
            Long uploadedDocumentId,
            String fileName
    ) {}

    /** 작성자(인증) 화면용 대상 1건 + 그 대상의 서류들. */
    public record TargetResponse(
            Long id,
            OwnerType ownerType,
            Long ownerId,
            String ownerName,
            int sortOrder,
            int itemCount,
            int uploadedCount,
            int requiredRemaining,
            List<ItemResponse> items
    ) {}

    /** 작성자(인증) 화면용 요청 상세. */
    public record Response(
            Long id,
            String token,
            String title,
            String ownerSummary,
            String recipientName,
            String recipientPhone,
            String recipientEmail,
            String status,
            LocalDateTime createdAt,
            LocalDateTime submittedAt,
            LocalDateTime sentAt,
            String publicUrl,
            int targetCount,
            int itemCount,
            int uploadedCount,
            List<TargetResponse> targets
    ) {}

    /** 목록용 — targets/items 없이 카운트만. 50대 규모에서 응답 폭증·N+1 방지. supplierNames = 대상 소유 협력업체명(distinct). */
    public record SummaryResponse(
            Long id,
            String token,
            String title,
            String ownerSummary,
            String recipientName,
            String recipientPhone,
            String recipientEmail,
            String status,
            LocalDateTime createdAt,
            LocalDateTime submittedAt,
            LocalDateTime sentAt,
            String publicUrl,
            int targetCount,
            int itemCount,
            int uploadedCount,
            List<String> supplierNames
    ) {}

    /** 공개(무로그인) 페이지용 항목. id = 업로드 단위. */
    public record PublicItem(
            Long id,
            Long documentTypeId,
            String name,
            boolean required,
            boolean uploaded,
            String fileName,
            String sampleImageUrl,      // V116: 마스킹된 예시 URL, null = 미등록
            boolean samplePdf,          // 샘플이 PDF면 true — 뷰어 img/iframe 분기용
            String sampleDescription    // V119: 샘플 설명글, null = 미등록
    ) {}

    /**
     * 공개(무로그인) 페이지용 대상 섹션.
     * 등록형 미등록 슬롯이면 registered=false — 화면은 [유형(plannedTypeLabel)][값 입력(inputKind)][등록] 을 먼저 보여주고
     * 등록 후에야 서류 업로드가 열린다. inputValue = 등록된 값(차량번호/이름), 미등록이면 null.
     */
    public record PublicTarget(
            Long id,
            OwnerType ownerType,
            String ownerLabel,
            String plannedType,
            String plannedTypeLabel,
            boolean registered,
            String inputKind,
            String inputValue,
            int itemCount,
            int uploadedCount,
            int requiredRemaining,
            List<PublicItem> items
    ) {}

    /** 공개(무로그인) 페이지용 — 최소 정보만. */
    public record PublicResponse(
            String title,
            String recipientName,
            String status,
            boolean expired,
            int itemCount,
            int uploadedCount,
            int requiredRemaining,
            List<PublicTarget> targets
    ) {}

    /** PDF 합쳐 이메일 발송 — 받는 주소(미지정 시 요청에 저장된 recipient_email). */
    public record SendPdfRequest(String email, String subject) {}

    /** 대상 자원의 유형(장비종류/인력역할)에 설정된 서류 — 수집요청 폼 자동 체크용. sort_order 오름차순. */
    public record SuggestResponse(
            List<Long> requiredTypeIds,
            List<Long> optionalTypeIds
    ) {}

    /** 다중 대상 폼 자동 체크 — 대상 목록을 한 번에 물어본다. */
    public record SuggestBatchRequest(List<SuggestTarget> targets) {}

    public record SuggestTarget(OwnerType ownerType, Long ownerId) {}

    public record SuggestBatchResult(
            OwnerType ownerType,
            Long ownerId,
            List<Long> requiredTypeIds,
            List<Long> optionalTypeIds
    ) {}

    public record SuggestBatchResponse(List<SuggestBatchResult> results) {}
}
