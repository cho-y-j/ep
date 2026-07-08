package com.skep.document;

/**
 * 서류 소유자 유형.
 *
 * - PERSON: 인원 — 면허, 안전교육 등
 * - EQUIPMENT: 장비 — 등록원부, 보험, 비파괴 검사 등
 * - COMPANY: 회사 — 사업자 등록증, 통장 사본, 건설업 등록증 등 (S-9-G)
 */
public enum OwnerType {
    PERSON,
    EQUIPMENT,
    COMPANY
}
