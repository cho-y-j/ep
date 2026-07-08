-- Phase4: 외부 장비 기사(조종원) Person 연결 — 기사 로그인 계정과 장비를 잇는다.
ALTER TABLE equipment ADD COLUMN operator_person_id BIGINT;
