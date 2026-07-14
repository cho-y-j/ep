/**
 * 영역-크롭 OCR 템플릿(ocr_region_template) 스키마 + 상수.
 * 저장 형태는 V83/V84 시드와 동일: {version:1, aspect:{w,h}, fields:[{key, box:[x,y,w,h] 0..1, parser}]}.
 */

/** parser 선택지 — paddle document_align.py `_PARSERS` 와 1:1 (authoritative). */
export type Parser = 'text' | 'date' | 'date_range_end' | 'vehicle_no' | 'year' | 'biz_no';

export const PARSERS: Parser[] = ['text', 'date', 'date_range_end', 'vehicle_no', 'year', 'biz_no'];

export const PARSER_LABEL: Record<Parser, string> = {
  text: '텍스트',
  date: '날짜',
  date_range_end: '기간(끝날짜)',
  vehicle_no: '차량번호',
  year: '연식(연도)',
  biz_no: '사업자번호',
};

/** 필드 박스 — box 는 warp된 캔버스(natural=aspect) 기준 0..1 정규화 [x, y, w, h]. */
export type Box = {
  key: string;
  box: [number, number, number, number];
  parser: Parser;
};

export type Aspect = { w: number; h: number };

export type RegionTemplate = {
  version: number;
  aspect: Aspect;
  fields: Box[];
};

/** 기본 aspect — A4 세로 1653×2339 (warp 목표 비율). */
export const DEFAULT_ASPECT: Aspect = { w: 1653, h: 2339 };

/** 표준 field key 팔레트 — doc-type required_fields 와 합쳐 datalist 제안(커스텀 snake_case 허용). */
export const STANDARD_KEYS: string[] = [
  'vehicle_no', 'model', 'year', 'vin', 'serial_number', 'expiry_date',
  'biz_no', 'start_date', 'owner_name', 'business_name', 'address',
  'license_no', 'birth_date', 'name', 'registration_no',
];
