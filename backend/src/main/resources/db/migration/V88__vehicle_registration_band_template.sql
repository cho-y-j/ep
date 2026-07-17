-- V88: 자동차등록증 템플릿을 '밴드+패턴(kind)' 방식으로 교체.
-- 배경: 고정영역 박스는 손정렬 4모서리 코너 편차에 통째로 밀려, 값이 다른 칸으로 오추출됨(브라우저 실사용에서 확인).
-- 방식: 넉넉한 밴드 안에서 '값의 패턴'(번호판/한글명/연도)으로 찾아 위치에 무관하게 추출(paddle _pick_value).
--       + warp 후 스캔앱식 보정(그레이·CLAHE·언샵)으로 OCR 정확도·미리보기 가독성↑.
-- 추출 3가지만: 차량번호(plate)·차명(hangul)·연식(최초등록일 year). vin 등은 제외(사용자 지정).
UPDATE document_types
   SET ocr_region_template = '{"version":1,"aspect":{"w":1653,"h":2339},"fields":[{"key":"vehicle_no","box":[0.08,0.055,0.35,0.070],"kind":"plate"},{"key":"model","box":[0.08,0.088,0.35,0.065],"kind":"hangul"},{"key":"year","box":[0.55,0.045,0.43,0.065],"kind":"year"}]}'
 WHERE name = '자동차등록증' AND applies_to = 'EQUIPMENT';
