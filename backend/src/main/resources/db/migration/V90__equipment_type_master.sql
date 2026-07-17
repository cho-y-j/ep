-- V90: 장비 종류(차종) 마스터 테이블 — 어드민에서 추가/수정/숨김 관리용.
-- 기존 EquipmentCategory enum 25종을 시드. 이후 단계에서 enum→String 전환 시 이 테이블이 정본.
-- equipment.category 에 하드 FK 는 걸지 않음(기존 자유 문자열·CSV 참조 → soft delete + 앱검증으로 대체).
CREATE TABLE equipment_type (
    code       VARCHAR(32)  PRIMARY KEY,          -- 불변 식별자 (현 enum name)
    name       VARCHAR(100) NOT NULL,             -- 표시 라벨 (수정 가능)
    grp        VARCHAR(16)  NOT NULL,             -- 건설기계 / 차량 / 기타
    sort_order INT          NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP
);

INSERT INTO equipment_type (code, name, grp, sort_order) VALUES
 ('EXCAVATOR','굴삭기','건설기계',10),
 ('WHEEL_LOADER','휠로더','건설기계',20),
 ('CRANE','크레인','건설기계',30),
 ('FORKLIFT','지게차','건설기계',40),
 ('DOZER','도저','건설기계',50),
 ('GRADER','그레이더','건설기계',60),
 ('PUMP_TRUCK','펌프카','건설기계',70),
 ('ROLLER','롤러','건설기계',80),
 ('PILE_DRIVER','항타·항발기','건설기계',90),
 ('CONCRETE_MIXER_TRUCK','콘크리트믹서트럭','건설기계',100),
 ('ASPHALT_FINISHER','아스팔트피니셔','건설기계',110),
 ('CRUSHER','쇄석기','건설기계',120),
 ('AIR_COMPRESSOR','공기압축기','건설기계',130),
 ('BORING_MACHINE','천공기','건설기계',140),
 ('TOWER_CRANE','타워크레인','건설기계',150),
 ('DUMP_TRUCK','덤프트럭','건설기계',160),
 ('SKID_LOADER','스키드로더','건설기계',170),
 ('AERIAL_LIFT','고소작업차','차량',180),
 ('TRAILER','트레일러','차량',190),
 ('WING_BODY','윙바디','차량',200),
 ('CARGO_TRUCK','카고트럭','차량',210),
 ('FREIGHT_TRUCK','화물차','차량',220),
 ('TANK_LORRY','탱크로리','차량',230),
 ('LADDER_TRUCK','사다리차','차량',240),
 ('ATTACHMENT','어태치먼트','기타',250);
