-- sites 에 지오펜스 반경 컬럼 추가. NULL 이면 출퇴근 시 기본 100m 사용.
ALTER TABLE sites ADD COLUMN IF NOT EXISTS geofence_radius_m INTEGER;
