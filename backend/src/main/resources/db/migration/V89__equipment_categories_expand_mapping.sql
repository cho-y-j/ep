-- V89: 확장된 장비 종류의 등록증 매핑.
-- 건설기계 종류 → 건설기계등록증 필수, 차량 종류 → 자동차등록증 필수.
-- (Phase 2 종류별 서류 체크리스트 어드민이 나오면 종류별로 세밀 조정 가능. 지금은 그룹 기본값.)
UPDATE document_types
   SET required = TRUE,
       applies_to_categories = 'EXCAVATOR,WHEEL_LOADER,CRANE,FORKLIFT,DOZER,GRADER,PUMP_TRUCK,ROLLER,PILE_DRIVER,CONCRETE_MIXER_TRUCK,ASPHALT_FINISHER,CRUSHER,AIR_COMPRESSOR,BORING_MACHINE,TOWER_CRANE,DUMP_TRUCK,SKID_LOADER'
 WHERE name = '건설기계등록증' AND applies_to = 'EQUIPMENT';

UPDATE document_types
   SET required = TRUE,
       applies_to_categories = 'AERIAL_LIFT,TRAILER,WING_BODY,CARGO_TRUCK,FREIGHT_TRUCK,TANK_LORRY,LADDER_TRUCK'
 WHERE name = '자동차등록증' AND applies_to = 'EQUIPMENT';
