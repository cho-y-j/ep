-- V27: 자동차등록증 차주 ≠ 사업자(공급사) 분리 필드
--
-- 배경: 장비공급사는 그 차량을 운영하는 사업자(supplier_id). 자동차등록증상 차주(소유자)는
-- 다를 수 있음 (개인 소유 차를 회사가 운영하거나, 회사 명의지만 등록 회사명이 다른 케이스).
-- 작업계획서 작성/검증 시 두 정보를 모두 표시해야 함.
--
-- vehicle_owner_name        : 자동차등록증상 소유자 이름 (개인/법인명)
-- vehicle_owner_business_no : 소유자가 법인이면 사업자등록번호 (개인이면 NULL)
-- vehicle_owner_resident_no : 소유자가 개인이면 주민번호 (PII — DocumentResponse 응답 시 PiiMasker.mask 통과)

ALTER TABLE equipment
    ADD COLUMN vehicle_owner_name VARCHAR(100),
    ADD COLUMN vehicle_owner_business_no VARCHAR(32),
    ADD COLUMN vehicle_owner_resident_no VARCHAR(32);
