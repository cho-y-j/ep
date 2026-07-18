-- V108: S2′ 안전점검원 법정점검(NFC 강제) — 어드민 편집형 점검 템플릿 + 점검 기록.
-- 조종원 일일점검(daily_equipment_inspections)과 별도 트랙(2점검 체제).
-- 증거사슬(§5): tag_read_at·tag_verified·서명 = "실제 현장에서 점검했다" 물증.

-- 점검 템플릿 — ADMIN 이 항목을 편집(항목 추가/삭제/순서). 사용자 실템플릿 수령 시 입력만.
CREATE TABLE safety_check_templates (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    target      VARCHAR(32)  NOT NULL DEFAULT 'EQUIPMENT',   -- v1: EQUIPMENT 고정
    items       JSONB        NOT NULL DEFAULT '[]',          -- [{no, text, required}]
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_by  BIGINT,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

-- 법정점검 기록 — 점검원(person)이 NFC 태그 후 제출.
CREATE TABLE legal_inspections (
    id                  BIGSERIAL PRIMARY KEY,
    equipment_id        BIGINT      NOT NULL,
    site_id             BIGINT,                              -- 태그 시점 배치 현장(스냅샷). NULL 허용.
    inspector_person_id BIGINT      NOT NULL,
    inspect_date        DATE        NOT NULL,
    template_id         BIGINT      NOT NULL,
    tag_id_submitted    VARCHAR(64),                         -- 제출된 태그 값(증거)
    tag_verified        BOOLEAN     NOT NULL DEFAULT false,  -- 실제 NFC 태그로 확인(true) vs 수동 폴백(false)
    tag_read_at         TIMESTAMP,                           -- 태그 시각(현장 방문 증명)
    items_result        JSONB       NOT NULL DEFAULT '[]',   -- [{no, checked, na, note}]
    sign_png            BYTEA,                               -- 점검원 서명
    memo                TEXT,
    created_at          TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uq_legal_inspection UNIQUE (equipment_id, inspect_date, template_id)
);

CREATE INDEX idx_legal_inspections_eq_date   ON legal_inspections (equipment_id, inspect_date);
CREATE INDEX idx_legal_inspections_inspector ON legal_inspections (inspector_person_id);

-- 기본 시드 1건 — 작업계획서 실양식 p4 CHECK POINT 8문(고소작업차). 어드민이 실템플릿 수령 시 편집.
INSERT INTO safety_check_templates (name, target, items, active, created_at, updated_at) VALUES
('고소작업차 법정 점검표 (기본)', 'EQUIPMENT',
 '[
   {"no":1,"text":"작업장소의 지반 및 지반상태는 조사하였는가?","required":true},
   {"no":2,"text":"운행경로의 지정 및 작업지휘자, 유도자(신호수)는 배치하였는가?","required":true},
   {"no":3,"text":"경사면 하부 등 작업구역 내 통제조치를 하였는가?","required":true},
   {"no":4,"text":"중량물의 전도·이동을 방지하기 위한 구름 멈춤대, 쐐기 등은 준비되었는가?","required":true},
   {"no":5,"text":"노면 붕괴 방지, 지반침하 방지, 노폭 유지 등에 대한 대책은 적절한가?","required":true},
   {"no":6,"text":"근로자 및 고압선 등 작업반경 내 장애물과의 접촉위험은 없는가?","required":true},
   {"no":7,"text":"작업시작 전 장비의 차륜/제동/조정/하역/조정장치는 점검하였는가?","required":true},
   {"no":8,"text":"작업시작 전 장비의 전조등, 후미등, 방향지시기, 경보장치의 이상유무를 점검하였는가?","required":true}
 ]'::jsonb,
 true, now(), now());
