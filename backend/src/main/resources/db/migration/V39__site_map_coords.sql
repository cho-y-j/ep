-- V39: 현장 지도 좌표 + 폴리곤 + 자원 배치 좌표
-- 카카오 지도 통합 — 주소 → 좌표 자동변환(프론트 geocoder) + 폴리곤(GeoJSON) + 자원별 클릭 배치 좌표.

ALTER TABLE sites ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE sites ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE sites ADD COLUMN polygon_geojson TEXT;
ALTER TABLE sites ADD COLUMN map_zoom INTEGER;

ALTER TABLE equipment_site_assignments ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE equipment_site_assignments ADD COLUMN longitude DOUBLE PRECISION;

ALTER TABLE person_site_assignments ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE person_site_assignments ADD COLUMN longitude DOUBLE PRECISION;
