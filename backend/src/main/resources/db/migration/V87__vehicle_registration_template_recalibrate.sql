-- V87: 자동차등록증(name='자동차등록증') 영역-OCR 템플릿 재보정.
-- 배경: 기존 템플릿 박스가 실제 발급본(별지 제1호서식) 레이아웃과 어긋나 필드가 다른 행에 떨어짐
--       → vehicle_no/model/year/vin 이 빈 값으로 추출됐다.
-- 조치: 실샘플을 문서 4모서리로 canonical warp 한 뒤 전체 OCR 로 각 필드 좌표를 재측정해 박스 교체.
-- 검증: corners 정렬 후 vehicle_no=83사1725·model=수림22.6톤저상트레일러·year=2016·vin=KN9ENEZTZGNBWJ006 추출 성공.
--       (검사유효기간은 최신 행이 수기 기재라 OCR 불안정 → warp 미리보기 보고 수동 보정.)
UPDATE document_types
   SET ocr_region_template = '{"version":1,"aspect":{"w":1653,"h":2339},"fields":[{"key":"vehicle_no","box":[0.150,0.086,0.125,0.028],"parser":"vehicle_no"},{"key":"model","box":[0.150,0.112,0.200,0.030],"parser":"text"},{"key":"year","box":[0.808,0.120,0.072,0.028],"parser":"year"},{"key":"vin","box":[0.148,0.140,0.210,0.030],"parser":"text"},{"key":"expiry_date","box":[0.478,0.558,0.245,0.032],"parser":"date_range_end"}]}'
 WHERE name = '자동차등록증' AND applies_to = 'EQUIPMENT';
