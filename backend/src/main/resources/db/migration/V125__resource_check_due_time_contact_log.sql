-- 검사(자원 점검) 개선 묶음:
-- 1) due_time — 검사 통보에 날짜뿐 아니라 시간도(선택). 기존 행 NULL 무해.
-- 2) contact_log — 발행사(공급사·BP)의 통화·연락 기록. "[7/24 14:00 이름] 내용" 줄 append 방식.
-- 3) 명칭 정정 — 검사종류 라벨 "자동차 안전점검" → "자동차 반입검사" (enum 코드 VEHICLE_SAFETY 불변).
--    회신 서류종류명도 함께 정정 — ResourceCheckService.resolveDocumentTypeId 의 이름 조회와 동기.
ALTER TABLE resource_check_requests ADD COLUMN due_time TIME;
ALTER TABLE resource_check_requests ADD COLUMN contact_log TEXT;

UPDATE document_types SET name = '자동차 반입검사 결과서' WHERE name = '자동차 안전점검 결과서';
