-- V86: 장비 종류별 '등록증' 필수 매핑 (건설기계 vs 자동차).
-- 장비 종류가 곧 등록증 종류를 결정한다:
--   · 건설기계관리법 대상(굴삭기·휠로더·크레인·지게차·도저·그레이더·펌프카) → 건설기계등록증
--   · 고소작업차는 건설기계 27종이 아니라 특수자동차(자동차관리법) → 자동차등록증
--   · 어태치먼트(부착작업장치)는 별도 등록 대상 아님 → 등록증 없음
-- 폼(EquipmentCreateForm.pickRegistrationType)이 선택된 종류에 걸린 필수 등록증을 자동 선택한다.
UPDATE document_types
   SET required = TRUE,
       applies_to_categories = 'EXCAVATOR,WHEEL_LOADER,CRANE,FORKLIFT,DOZER,GRADER,PUMP_TRUCK'
 WHERE name = '건설기계등록증' AND applies_to = 'EQUIPMENT';

UPDATE document_types
   SET required = TRUE,
       applies_to_categories = 'AERIAL_LIFT'
 WHERE name = '자동차등록증' AND applies_to = 'EQUIPMENT';
