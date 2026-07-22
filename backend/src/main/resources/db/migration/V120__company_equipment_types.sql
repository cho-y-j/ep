-- V120: 회사별 취급 장비종류(다대다). 공급사(장비업체)가 자주 쓰는 종류만 지정 →
-- 장비 등록·서류 수집요청의 종류 선택이 그 종류만 기본 표시(전체 보기 토글로 전체). 비어 있으면 전체(기존 동작).
-- equipment_type_code 는 equipment_type.code(VARCHAR PK) 참조. company_id 는 companies.id.
CREATE TABLE company_equipment_type (
    company_id          BIGINT      NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    equipment_type_code VARCHAR(32) NOT NULL REFERENCES equipment_type (code) ON DELETE CASCADE,
    PRIMARY KEY (company_id, equipment_type_code)
);
