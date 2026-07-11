-- 협력사(부모→자식) 보완 요청: requester_role 에 EQUIPMENT_SUPPLIER(18자)/MANPOWER_SUPPLIER(16자) 저장.
-- 기존 VARCHAR(16) 로는 EQUIPMENT_SUPPLIER INSERT 가 깨지므로 32 로 확장.
ALTER TABLE document_supplement_requests ALTER COLUMN requester_role TYPE VARCHAR(32);
