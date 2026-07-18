// 조종원 면허·자격 자동 채움 파싱 계층 (기획 §7-③: 원천 = OCR 검증값, 수기 qualification 폴백)
// Document.extracted_data(JSON) 에서 면허번호·자격종류·취득일·교육이수일을 추출한다.
// - 면허번호: 운전면허/화물운송자격/건설기계조종사면허 서류의 OCR/수기(manual*) 값
// - 자격종류: 채택한 서류의 종류명 (하드코딩 제거)
// - 취득일: 자격 서류의 발급/취득일 (없으면 '')
// - 교육이수일: 안전교육/기초안전 서류의 이수일 (없으면 '')
import type { DocumentResponse } from '../../types/document';

export type LicenseSource = 'ocr' | 'qualification' | 'none';

export interface OperatorLicenseInfo {
  personId: number;
  name: string;
  licenseNo: string;    // 면허/자격 번호
  licenseType: string;  // 자격 종류 = 채택 서류 종류명 (없으면 '')
  licenseDate: string;  // 면허 취득/발급일 YYYY-MM-DD (없으면 '')
  eduDate: string;      // 교육 이수일 YYYY-MM-DD (없으면 '')
  source: LicenseSource;
}

// 면허/자격 서류 우선순위 — 앞선 종류를 우선 채택 (고소작업차 = 화물운송자격 원천).
const LICENSE_TYPE_PRIORITY = ['화물운송자격', '건설기계조종사', '운전면허'];

// extracted_data 안의 면허/자격 번호 키 후보 (manual* 우선 — 업로드 시 사용자 확정값).
const LICENSE_NO_KEYS = [
  'manualLicenseNumber', 'manualLicenseNo', 'manualCargoLicenseNo',
  'cargoLicenseNo', 'cargo_license_no',
  'license_no', 'licenseNumber', 'licenseNo',
  'registration_no', 'registrationNo', 'manualRegistrationNo',
];
// 면허 취득/발급일 키 후보 (화물자격 실양식엔 미추출 — 있으면 채움).
const LICENSE_DATE_KEYS = [
  'manualIssueDate', 'manualAcquisitionDate', 'manualLicenseDate', 'manualAcquireDate',
  'issueDate', 'issue_date', 'acquisitionDate', 'acquisition_date',
  'licenseDate', 'license_date', 'acquireDate', 'acquire_date',
];
// 교육 이수일 키 후보.
const EDU_DATE_KEYS = [
  'manualCompletedDate', 'manualCompletionDate', 'manualEduDate',
  'completed_date', 'completedDate', 'completionDate', 'completion_date',
  'eduDate', 'edu_date', 'trneDt',
];

function parseExtracted(json?: string | null): Record<string, string> {
  if (!json) return {};
  try {
    const obj = JSON.parse(json);
    if (!obj || typeof obj !== 'object') return {};
    const out: Record<string, string> = {};
    for (const [k, v] of Object.entries(obj)) {
      if (v == null) continue;
      if (typeof v === 'string' || typeof v === 'number') out[k] = String(v);
    }
    return out;
  } catch {
    return {};
  }
}

function firstKey(map: Record<string, string>, keys: string[]): string {
  for (const k of keys) {
    const v = map[k];
    if (v != null && String(v).trim() !== '') return String(v).trim();
  }
  return '';
}

/** OCR 날짜 문자열(2020.06.11 / 2020-06-11 / 20200611) → YYYY-MM-DD. 실패 시 ''. */
export function toIsoDate(s?: string): string {
  if (!s) return '';
  const t = String(s).trim();
  let m = t.match(/(\d{4})[.\-/]\s*(\d{1,2})[.\-/]\s*(\d{1,2})/);
  if (m) return `${m[1]}-${m[2].padStart(2, '0')}-${m[3].padStart(2, '0')}`;
  m = t.match(/^(\d{4})(\d{2})(\d{2})$/);
  if (m) return `${m[1]}-${m[2]}-${m[3]}`;
  return '';
}

function priorityOf(typeName: string): number {
  for (let i = 0; i < LICENSE_TYPE_PRIORITY.length; i++) {
    if (typeName.includes(LICENSE_TYPE_PRIORITY[i])) return i;
  }
  return LICENSE_TYPE_PRIORITY.length;
}

function isLicenseDoc(typeName: string): boolean {
  return priorityOf(typeName) < LICENSE_TYPE_PRIORITY.length
    || typeName.includes('면허') || typeName.includes('자격');
}

function isEduDoc(typeName: string): boolean {
  // 자격/면허 서류는 제외하고 순수 교육·이수·수료 서류만.
  if (typeName.includes('자격') || typeName.includes('면허')) return false;
  return typeName.includes('교육') || typeName.includes('이수') || typeName.includes('수료');
}

/**
 * 한 조종원의 서류 목록에서 면허·자격·교육 정보 추출.
 * OCR/수기 값이 있으면 source='ocr', 없으면 Person.qualification 폴백(source='qualification').
 */
export function extractOperatorLicense(
  person: { id: number; name: string; qualification?: string | null },
  docs: DocumentResponse[],
): OperatorLicenseInfo {
  // 1) 면허/자격 서류 — 우선순위 높은 종류 + 번호 존재하는 것 채택.
  let best: { doc: DocumentResponse; map: Record<string, string>; pr: number } | null = null;
  for (const d of docs) {
    const name = d.document_type_name || '';
    if (!isLicenseDoc(name)) continue;
    const map = parseExtracted(d.extracted_data);
    if (!firstKey(map, LICENSE_NO_KEYS)) continue;
    const pr = priorityOf(name);
    if (!best || pr < best.pr) best = { doc: d, map, pr };
  }

  // 2) 교육 이수일 — 안전교육/기초안전 서류 이수일.
  let eduDate = '';
  for (const d of docs) {
    if (!isEduDoc(d.document_type_name || '')) continue;
    const dt = toIsoDate(firstKey(parseExtracted(d.extracted_data), EDU_DATE_KEYS));
    if (dt) { eduDate = dt; break; }
  }

  if (best) {
    return {
      personId: person.id,
      name: person.name,
      licenseNo: firstKey(best.map, LICENSE_NO_KEYS),
      licenseType: best.doc.document_type_name || '',
      licenseDate: toIsoDate(firstKey(best.map, LICENSE_DATE_KEYS)),
      eduDate,
      source: 'ocr',
    };
  }

  // 3) 폴백 — Person.qualification.
  const q = (person.qualification || '').trim();
  return {
    personId: person.id,
    name: person.name,
    licenseNo: q,
    licenseType: '',
    licenseDate: '',
    eduDate,
    source: q ? 'qualification' : 'none',
  };
}
