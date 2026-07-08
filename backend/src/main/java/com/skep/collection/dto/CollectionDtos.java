package com.skep.collection.dto;

import com.skep.document.OwnerType;

import java.time.LocalDateTime;
import java.util.List;

/** 서류 수집 링크 관련 요청/응답 DTO 모음. */
public final class CollectionDtos {
    private CollectionDtos() {}

    /** 수집 요청 생성 — 필수/선택 서류타입 선택 + 받는사람. */
    public record CreateRequest(
            OwnerType ownerType,
            Long ownerId,
            List<Long> requiredTypeIds,
            List<Long> optionalTypeIds,
            String title,
            String recipientName,
            String recipientPhone,
            String recipientEmail
    ) {}

    /** 작성자(인증) 화면용 항목. */
    public record ItemResponse(
            Long documentTypeId,
            String documentTypeName,
            boolean required,
            int sortOrder,
            boolean uploaded,
            Long uploadedDocumentId,
            String fileName
    ) {}

    /** 작성자(인증) 화면용 요청 상세. */
    public record Response(
            Long id,
            String token,
            OwnerType ownerType,
            Long ownerId,
            String ownerName,
            String title,
            String recipientName,
            String recipientPhone,
            String recipientEmail,
            String status,
            LocalDateTime createdAt,
            LocalDateTime submittedAt,
            LocalDateTime sentAt,
            String publicUrl,
            List<ItemResponse> items
    ) {}

    /** 공개(무로그인) 페이지용 항목. */
    public record PublicItem(
            Long documentTypeId,
            String name,
            boolean required,
            boolean uploaded,
            String fileName
    ) {}

    /** 공개(무로그인) 페이지용 — 최소 정보만. */
    public record PublicResponse(
            String title,
            OwnerType ownerType,
            String ownerLabel,
            String recipientName,
            String status,
            boolean expired,
            List<PublicItem> items
    ) {}

    /** PDF 합쳐 이메일 발송 — 받는 주소(미지정 시 요청에 저장된 recipient_email). */
    public record SendPdfRequest(String email, String subject) {}
}
