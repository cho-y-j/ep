-- NFC(RFC) 태그 식별자 — 작업자 카드(persons) / 차량 태그(equipment). 카드 도착 후 등록(현재 dormant).
-- 생체/NFC 출근 method 기록 (생체검증은 단말에서, 서버는 기록만).
ALTER TABLE persons   ADD COLUMN nfc_tag_id VARCHAR(64);
ALTER TABLE equipment ADD COLUMN nfc_tag_id VARCHAR(64);
ALTER TABLE attendance_sessions ADD COLUMN check_in_method VARCHAR(20);

-- 태그는 자원당 유일 (NULL 다수 허용).
CREATE UNIQUE INDEX ux_persons_nfc_tag   ON persons(nfc_tag_id)   WHERE nfc_tag_id IS NOT NULL;
CREATE UNIQUE INDEX ux_equipment_nfc_tag ON equipment(nfc_tag_id) WHERE nfc_tag_id IS NOT NULL;
