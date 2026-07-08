-- V45: 견적 응답 단순화 — 차량 선택 없이 단가만 응답 가능하도록 equipment_id NULL 허용
ALTER TABLE quotation_dispatched_equipments
    ALTER COLUMN equipment_id DROP NOT NULL;
