-- P2c: 현장에 원청(client_org) 차원 추가 — 원청 통합 관제 허브가 자기 원청 현장만 조회.
-- NULL 허용(기존 현장은 원청 미지정). ADMIN 현장 편집 폼에서 지정.
ALTER TABLE sites ADD COLUMN client_org_id BIGINT;
