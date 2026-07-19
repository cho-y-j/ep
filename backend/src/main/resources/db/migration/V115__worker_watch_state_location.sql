-- 안전 상황판 지도 통합 — 워치 최신 위치(폰 GPS 게이트웨이가 POST /api/field-auth/sensor 로 실어보낸 lat/lng).
-- 워치 마커를 최근 수신 위치에 표시(출근 좌표 폴백은 프론트에서 처리). 워치 저전력 설계 무손상 — 위치는 폰이 보강.
ALTER TABLE worker_watch_states ADD COLUMN lat DOUBLE PRECISION;
ALTER TABLE worker_watch_states ADD COLUMN lng DOUBLE PRECISION;
