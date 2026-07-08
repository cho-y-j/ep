// 센코어 마스터 템플릿 필드 스키마
// 132 placeholder를 섹션별 그룹핑, 기본값·라벨·타입 정의
// AI 재작성 대상 섹션도 명시

export type FieldType = 'text' | 'textarea' | 'checkbox' | 'date' | 'linkedPerson' | 'linkedEquipment' | 'image';

export interface TemplateField {
  key: string;
  label: string;
  type: FieldType;
  defaultValue?: any;
  placeholder?: string;
  linkedTo?: string; // "equipment.vehicleNo", "person.operator.name" 등
  aiPrompt?: string;  // AI 재작성 시 힌트
}

export interface TemplateSection {
  id: string;
  title: string;
  description?: string;
  page: 'p1' | 'p2' | 'p3' | 'p4' | 'p5';
  fields: TemplateField[];
  aiRewritable?: boolean;
  essential?: boolean; // 필수 섹션 — UI 상단에 항상 노출
}

export const CHECK_YES = '☑';
export const CHECK_NO = '☐';

// ─── p1 표지 ────────────────────────────────────────────────
const P1 = {
  workType: {
    id: 'p1_workType', page: 'p1', title: '작업 유형',
    fields: [
      { key: 'workTypeHeavy', label: '중량물 취급', type: 'checkbox', defaultValue: false },
      { key: 'workTypeVehicle', label: '차량계 건설기계', type: 'checkbox', defaultValue: true },
    ],
  } as TemplateSection,

  equipment: {
    id: 'p1_equipment', page: 'p1', title: '장비 정보', description: '장비 선택 시 자동 채움', essential: true,
    fields: [
      { key: 'equipmentName', label: '장비명', type: 'text', linkedTo: 'equipment.name' },
      { key: 'equipmentModel', label: '모델', type: 'text', linkedTo: 'equipment.model' },
      { key: 'vehicleNo', label: '차량번호', type: 'text', linkedTo: 'equipment.vehicleNo' },
    ],
  } as TemplateSection,

  site: {
    id: 'p1_site', page: 'p1', title: '현장/업체/기간', essential: true,
    fields: [
      { key: 'siteName', label: '현장명', type: 'text', defaultValue: '용인 Cluster 1기 구축공사(IBL)' },
      { key: 'submitCompany', label: '업체명', type: 'text', defaultValue: '(주)스켑중기' },
      { key: 'workPeriodStart', label: '작업기간 시작', type: 'date', defaultValue: '2025-11-11' },
      { key: 'workPeriodEnd', label: '작업기간 종료', type: 'date', defaultValue: '2025-11-30' },
    ],
  } as TemplateSection,

  signatures: {
    id: 'p1_signatures', page: 'p1', title: '서명 (작성/검토/확인/승인)', essential: true,
    fields: [
      { key: 'sig0_name', label: '작성자 이름', type: 'text' },
      { key: 'sig0_position', label: '작성자 직위', type: 'text', defaultValue: 'Biz.P 현장소장' },
      { key: 'sig0_date', label: '작성자 일자', type: 'date' },
      { key: 'sig1_name', label: '담당자 이름', type: 'text' },
      { key: 'sig1_position', label: '담당자 직위', type: 'text', defaultValue: 'SKEP 관리감독자' },
      { key: 'sig1_date', label: '담당자 일자', type: 'date' },
      { key: 'sig2_name', label: '확인자 이름', type: 'text' },
      { key: 'sig2_position', label: '확인자 직위', type: 'text', defaultValue: 'SKEP HYPER' },
      { key: 'sig2_date', label: '확인자 일자', type: 'date' },
      { key: 'sig3_name', label: '검토자 이름', type: 'text' },
      { key: 'sig3_position', label: '검토자 직위', type: 'text', defaultValue: 'SKEP 안전관리자' },
      { key: 'sig3_date', label: '검토자 일자', type: 'date' },
      { key: 'sig4_name', label: '승인자 이름', type: 'text' },
      { key: 'sig4_position', label: '승인자 직위', type: 'text', defaultValue: 'SKEP 현장총괄' },
      { key: 'sig4_date', label: '승인자 일자', type: 'date' },
    ],
  } as TemplateSection,

  attachments: {
    id: 'p1_attachments', page: 'p1', title: '참조 첨부서류 체크리스트',
    description: '등록된 서류에 따라 자동 체크', essential: true,
    fields: Array.from({ length: 16 }, (_, i) => ({
      key: `attach${i}`,
      label: [
        '건설기계 등록원부', '장비 제원표',
        'Rigging Gear List', '장비 실사 사진',
        '자동차 보험증권', '사업자 등록증',
        '자동차 등록증', '운전면허증 (경력)',
        '기초 안전보건교육 이수증', '조종사 안전교육 이수증',
        '비파괴검사서 (자분탐상)', '비파괴검사서 (초음파)',
        '안전인증서 (KCs)', '붐 수리 보고서',
        '반입전 검사서류', '현장 운영계획',
      ][i],
      type: 'checkbox' as FieldType,
      defaultValue: true,
    })),
  } as TemplateSection,
};

// ─── p2 장비 세부 ──────────────────────────────────────────
const P2 = {
  equipmentDetail: {
    id: 'p2_equipmentDetail', page: 'p2', title: '장비 상세 (장비 선택 시 자동 채움)',
    fields: [
      { key: 'equipmentCapacity', label: '성능(적재하중)', type: 'text', defaultValue: '45m' },
      { key: 'manufactureYear', label: '장비 출고 년수', type: 'text', linkedTo: 'equipment.upperPartYear' },
      { key: 'equipmentSerialNo', label: '기기 고유번호', type: 'text', linkedTo: 'equipment.serialNo' },
      { key: 'manufacturer', label: '제조일자', type: 'text', linkedTo: 'equipment.manufacturer' },
      { key: 'equipmentSpec', label: '제원', type: 'text', linkedTo: 'equipment.capacity' },
    ],
  } as TemplateSection,

  operator: {
    id: 'p2_operator', page: 'p2', title: '조종원 (인력 선택 시 자동 채움)', essential: true,
    fields: [
      { key: 'operatorName', label: '조종원 성명', type: 'text', linkedTo: 'person.operator.name' },
      { key: 'operatorLicense', label: '자격·면허', type: 'text', defaultValue: '화물운송종사 자격증' },
      { key: 'operatorLicenseNo', label: '면허번호', type: 'text', linkedTo: 'person.operator.licenseNo' },
      { key: 'operatorLicenseDate', label: '면허 취득일', type: 'date', defaultValue: '2013-01-17' },
      { key: 'operatorEduDate', label: '교육 이수일', type: 'date', defaultValue: '2025-09-10' },
    ],
  } as TemplateSection,

  workEnv: {
    id: 'p2_workEnv', page: 'p2', title: '작업 환경',
    fields: [
      { key: 'workRadius', label: '작업반경', type: 'text', defaultValue: '3m' },
      { key: 'slopeLimit', label: '경사도(작업/주행)', type: 'text', defaultValue: '작업 7° / 주행 3°' },
      { key: 'loc_flat', label: '평지', type: 'checkbox', defaultValue: true },
      { key: 'loc_slope', label: '경사지', type: 'checkbox', defaultValue: false },
      { key: 'loc_slopeDeg', label: '경사지 (%)', type: 'text', defaultValue: '' },
      { key: 'soil_dry', label: '건조', type: 'checkbox', defaultValue: false },
      { key: 'soil_medium', label: '보통', type: 'checkbox', defaultValue: true },
      { key: 'soil_soft', label: '연약', type: 'checkbox', defaultValue: false },
      { key: 'soil_sand', label: '모래', type: 'checkbox', defaultValue: false },
      { key: 'soil_gravel', label: '자갈', type: 'checkbox', defaultValue: false },
      { key: 'soil_concrete', label: '콘크리트', type: 'checkbox', defaultValue: true },
      { key: 'soil_asphalt', label: '아스팔트', type: 'checkbox', defaultValue: false },
      { key: 'powerline_yes', label: '가공전선 있음', type: 'checkbox', defaultValue: false },
      { key: 'powerline_voltage', label: '전압용량', type: 'text', defaultValue: '' },
      { key: 'powerline_distance', label: '이격거리(m)', type: 'text', defaultValue: '' },
      { key: 'powerline_no', label: '가공전선 없음', type: 'checkbox', defaultValue: true },
    ],
  } as TemplateSection,

  groundSupport: {
    id: 'p2_groundSupport', page: 'p2', title: '지반보강 / 침하방지 / 아웃트리거',
    fields: [
      { key: 'ground_steelplate', label: '철판설치', type: 'checkbox', defaultValue: true },
      { key: 'ground_improve', label: '지반개량', type: 'checkbox', defaultValue: false },
      { key: 'ground_soil', label: '양질토사 성토', type: 'checkbox', defaultValue: false },
      { key: 'ground_other', label: '기타', type: 'checkbox', defaultValue: false },
      { key: 'ground_none', label: '해당 없음', type: 'checkbox', defaultValue: false },
      { key: 'sinkRisk_yes', label: '침하가능 있음', type: 'checkbox', defaultValue: false },
      { key: 'sink_manhole', label: '맨홀', type: 'checkbox', defaultValue: false },
      { key: 'sink_trench', label: 'Trench', type: 'checkbox', defaultValue: false },
      { key: 'sink_excavation', label: '굴착 단부', type: 'checkbox', defaultValue: false },
      { key: 'sink_other', label: '기타', type: 'checkbox', defaultValue: false },
      { key: 'sink_otherText', label: '기타 설명', type: 'text', defaultValue: '' },
      { key: 'sink_distance', label: '이격거리(m)', type: 'text', defaultValue: '' },
      { key: 'sinkRisk_no', label: '침하가능 없음', type: 'checkbox', defaultValue: true },
      { key: 'outrigger_size', label: '아웃트리거 사이즈', type: 'text', defaultValue: '8.17m X 8.17m' },
      { key: 'antiSink_plate', label: '철판', type: 'checkbox', defaultValue: true },
      { key: 'antiSink_plateSize', label: '철판 사이즈', type: 'text', defaultValue: '600mm X 600mm' },
      { key: 'antiSink_wood', label: '침목', type: 'checkbox', defaultValue: false },
      { key: 'antiSink_woodSize', label: '침목 사이즈', type: 'text', defaultValue: '' },
      { key: 'antiSink_other', label: '기타', type: 'checkbox', defaultValue: false },
      { key: 'antiSink_otherText', label: '기타 설명', type: 'text', defaultValue: '' },
      { key: 'antiSink_none', label: '해당 없음', type: 'checkbox', defaultValue: false },
      { key: 'midProp_installed', label: '중간 고임목 설치', type: 'checkbox', defaultValue: true },
      { key: 'midProp_method', label: '방법', type: 'text', defaultValue: '받침목' },
      { key: 'midProp_notInstalled', label: '미 설치', type: 'checkbox', defaultValue: false },
    ],
  } as TemplateSection,

  workControl: {
    id: 'p2_workControl', page: 'p2', title: '출입금지 / 풍속 / 작업내용',
    aiRewritable: true, essential: true,
    fields: [
      { key: 'restrict_fence', label: '통제 휀스', type: 'checkbox', defaultValue: true },
      { key: 'restrict_rope', label: '접근 방지 로프', type: 'checkbox', defaultValue: false },
      { key: 'restrict_watcher', label: '감시인', type: 'checkbox', defaultValue: true },
      { key: 'restrict_cone', label: '라바콘+걸이대', type: 'checkbox', defaultValue: true },
      { key: 'windStop_value', label: '풍속 기준 (m/s)', type: 'text', defaultValue: '10' },
      { key: 'windStop_legal', label: '법적 기준', type: 'checkbox', defaultValue: true },
      { key: 'windStop_manufacturer', label: '제조사 기준', type: 'checkbox', defaultValue: false },
      { key: 'windStop_self', label: '자체 기준', type: 'checkbox', defaultValue: false },
      {
        key: 'workDescription', label: '작업내용', type: 'textarea',
        defaultValue: '철골 설치 및 마감(볼팅, 일팩, 수직도 등), 용접 및 사상 작업',
        aiPrompt: '고소작업차로 수행할 건설 작업 내용을 한 문장으로 구체적으로 작성',
      },
      { key: 'usage_daily', label: '일대', type: 'checkbox', defaultValue: false },
      { key: 'usage_monthly', label: '월대', type: 'checkbox', defaultValue: true },
    ],
  } as TemplateSection,

  failSafe: {
    id: 'p2_failSafe', page: 'p2', title: 'Fail Safe 체크',
    fields: [
      { key: 'fs_d_req_backBeep', label: '[일대 필수] 후방경보음', type: 'checkbox', defaultValue: true },
      { key: 'fs_d_req_backCam', label: '[일대 필수] 후방카메라', type: 'checkbox', defaultValue: true },
      { key: 'fs_d_opt_approach', label: '[일대 선택] 장비접근경보', type: 'checkbox', defaultValue: false },
      { key: 'fs_d_opt_control', label: '[일대 선택] 장비접근제어', type: 'checkbox', defaultValue: false },
      { key: 'fs_m_req_system', label: '[월대 필수] 장비접근경보/제어', type: 'checkbox', defaultValue: true },
      { key: 'fs_m_opt_control', label: '[월대 선택] 장비접근제어', type: 'checkbox', defaultValue: false },
      { key: 'fs_yes', label: 'Yes', type: 'checkbox', defaultValue: false },
      { key: 'fs_no', label: 'No', type: 'checkbox', defaultValue: false },
      { key: 'fs_na', label: 'N/A', type: 'checkbox', defaultValue: true },
    ],
  } as TemplateSection,
};

// ─── p3 위험요인 (6행 고정 + 체크박스) ────────────────────
const P3 = {
  riskCheck: {
    id: 'p3_riskCheck', page: 'p3', title: '방호장치 / 안전조치 대상',
    fields: [
      { key: 'risk_fall', label: '추락', type: 'checkbox', defaultValue: true },
      { key: 'risk_drop', label: '낙하', type: 'checkbox', defaultValue: true },
      { key: 'risk_tip', label: '전도', type: 'checkbox', defaultValue: true },
      { key: 'risk_fly', label: '비래', type: 'checkbox', defaultValue: false },
    ],
  } as TemplateSection,

  riskReasons: {
    id: 'p3_riskReasons', page: 'p3', title: '위험요인 · 사고발생원인',
    description: '6개 위험요인 본문', aiRewritable: true,
    fields: [
      { key: 'risk1_reason', label: '위험요인 1', type: 'textarea', defaultValue: '철골 작업 시 근로자 안전고리 미 체결로 인한 추락', aiPrompt: '철골·고소작업차 작업의 추락 위험을 한 문장으로' },
      { key: 'risk2_reason', label: '위험요인 2', type: 'textarea', defaultValue: '이탈방지핀 미 체결 및 고정상태 미 확인으로 인한 낙하', aiPrompt: '수공구/부재 낙하 위험을 한 문장으로' },
      { key: 'risk3_reason', label: '위험요인 3', type: 'textarea', defaultValue: '아웃트리거 최대확장 미 실시, 발판 설치 미흡에 의한 전도', aiPrompt: '장비 전도 위험을 한 문장으로' },
      { key: 'risk4_reason', label: '위험요인 4', type: 'textarea', defaultValue: '붐 회전반경 내 장비간 동선 점검 미흡에 의한 충돌', aiPrompt: '붐/장비 충돌 위험을 한 문장으로' },
      { key: 'risk5_reason', label: '위험요인 5', type: 'textarea', defaultValue: '작업구간 통제 미흡으로 인한 협착', aiPrompt: '협착 위험을 한 문장으로' },
      { key: 'risk6_reason', label: '위험요인 6', type: 'textarea', defaultValue: '강한 바람에 의한 무게중심 이동, 장비 균형 상실 등으로 인한 붐대 붕괴', aiPrompt: '강풍에 의한 붕괴 위험을 한 문장으로' },
    ],
  } as TemplateSection,

  safetyActions: {
    id: 'p3_safetyActions', page: 'p3', title: '안전대책',
    description: '각 위험요인에 대응하는 안전대책', aiRewritable: true,
    fields: [
      { key: 'safety1a_action', label: '대책 1-a', type: 'textarea', defaultValue: '- 작업 전 생명줄 설치 및 고정상태 확인', aiPrompt: '추락방지 대책 한 줄' },
      { key: 'safety1b_action', label: '대책 1-b', type: 'textarea', defaultValue: '- 근로자 안전고리 2줄걸이 체결 및 고정상태 확인 철저' },
      { key: 'safety2_action', label: '대책 2', type: 'textarea', defaultValue: '공도구이탈방지핀 체결 및 고정상태 이상유무 확인 후 작업진행', aiPrompt: '낙하방지 대책 한 줄' },
      { key: 'safety3a_action', label: '대책 3-a', type: 'textarea', defaultValue: '- 아웃트리거 최대확장 실시여부 확인', aiPrompt: '전도방지 대책 한 줄' },
      { key: 'safety3b_action', label: '대책 3-b', type: 'textarea', defaultValue: '- 아웃트리거 발판 설치상태 확인 후 작업 실시' },
      { key: 'safety4a_action', label: '대책 4-a', type: 'textarea', defaultValue: '- 작업 전 검사 시 붐 회전반경, 방향 및 작업순서 공유', aiPrompt: '충돌방지 대책 한 줄' },
      { key: 'safety4b_action', label: '대책 4-b', type: 'textarea', defaultValue: '- 운전원 임의작업 금지' },
      { key: 'safety5_action', label: '대책 5', type: 'textarea', defaultValue: '작업구간 구획설정 및 유도원 배치하여 접근통제 실시', aiPrompt: '협착방지 대책 한 줄' },
      { key: 'safety6_action', label: '대책 6', type: 'textarea', defaultValue: '풍속에 따른 작업중지 기준 준수(10m/s 이상 작업금지)', aiPrompt: '강풍 대책 한 줄' },
    ],
  } as TemplateSection,
};

// ─── p4 점검사항 + 인력사항 ─────────────────────────────
const CHECK_POINTS = [
  '작업장소의 지반 및 지반상태는 조사하였는가?',
  '운행경로의 지정 및 작업지휘자, 유도자(신호수)는 배치하였는가?',
  '경사면 하부 등 작업구역 내 통제조치를 하였는가?',
  '중량물의 동요나 이동을 방지하기 위한 구름 멈춤대, 쐐기 등은 준비 되었는가?',
  '노면 붕괴 방지, 지반침하방지, 노폭유지 등에 대한 대책은 적절한가?',
  '근로자 및 고압선 등 작업반경 내 장애물과의 접촉위험은 없는가?',
  '작업시작 전 장비의 차륜 / 제동 / 조정 / 하역 / 조정장치는 점검하였는가?',
  '작업시작 전 장비의 전조등, 후미등, 방향지시기, 경보장치의 이상유무를 점검하였는가?',
];

const P4 = {
  checkPoints: {
    id: 'p4_checkPoints', page: 'p4', title: '점검사항 (8항목) Yes / N/A',
    fields: CHECK_POINTS.flatMap((cp, i) => [
      // label을 템플릿 실제 문구 그대로 사용 → 미리보기에서 클릭 시 정확 매칭
      { key: `p4_check${i + 1}_yes`, label: cp, type: 'checkbox' as FieldType, defaultValue: true },
      { key: `p4_check${i + 1}_na`, label: `${i + 1}번 N/A`, type: 'checkbox' as FieldType, defaultValue: false },
    ]),
  } as TemplateSection,

  supervisor: {
    id: 'p4_supervisor', page: 'p4', title: '작업지휘자', essential: true,
    fields: [
      { key: 'supervisor_company', label: '소속', type: 'text', defaultValue: '(주)스켑중기' },
      { key: 'supervisor_position', label: '직책', type: 'text', defaultValue: '부장' },
      { key: 'supervisor_name', label: '성명', type: 'text', linkedTo: 'person.supervisor.name' },
    ],
  } as TemplateSection,

  protective: {
    id: 'p4_protective', page: 'p4', title: '개인 보호구 지급여부',
    fields: [
      { key: 'pp_helmet', label: '안전모', type: 'checkbox', defaultValue: true },
      { key: 'pp_shoes', label: '안전화', type: 'checkbox', defaultValue: true },
      { key: 'pp_belt', label: '안전대', type: 'checkbox', defaultValue: true },
      { key: 'pp_vest', label: '신호수 조끼', type: 'checkbox', defaultValue: true },
      { key: 'pp_other', label: '기타', type: 'checkbox', defaultValue: false },
      { key: 'pp_otherText', label: '기타 설명', type: 'text', defaultValue: '' },
    ],
  } as TemplateSection,

  signalMethod: {
    id: 'p4_signalMethod', page: 'p4', title: '신호방법',
    fields: [
      { key: 'sig_radio', label: '무전', type: 'checkbox', defaultValue: true },
      { key: 'sig_hand', label: '수신호', type: 'checkbox', defaultValue: true },
      { key: 'sig_baton', label: '신호봉', type: 'checkbox', defaultValue: true },
      { key: 'sig_whistle', label: '호각', type: 'checkbox', defaultValue: true },
    ],
  } as TemplateSection,

  specialEdu: {
    id: 'p4_specialEdu', page: 'p4', title: '특별안전보건교육 구분',
    fields: [
      { key: 'sedu_heavy', label: '중량물 취급', type: 'checkbox', defaultValue: false },
      { key: 'sedu_vehicle', label: '차량계 하역운반/건설기계', type: 'checkbox', defaultValue: true },
    ],
  } as TemplateSection,
};

// ─── p5 ─────────────────────────────────────────────────
const P5 = {
  confirm: {
    id: 'p5_confirm', page: 'p5', title: '현장 확인 서명',
    fields: [
      { key: 'confirmName1', label: 'Step 1 서명', type: 'text' },
      { key: 'confirmName2', label: '안전대책 서명', type: 'text' },
    ],
  } as TemplateSection,
};

// ─── 인력 표 (p4 인력사항 테이블은 복잡 구조라 별도) ─────
// ※ 현재 템플릿은 인력 표 따로 없음 (p4 인적사항 섹션 내 작업지휘자·유도원만 placeholder)
// ※ 작업배치도 이미지 {%workSiteDiagram}는 별도 처리

// ─── 전체 스키마 ────────────────────────────────────────
export const SCHEMA: TemplateSection[] = [
  P1.workType, P1.equipment, P1.site, P1.signatures, P1.attachments,
  P2.equipmentDetail, P2.operator, P2.workEnv, P2.groundSupport, P2.workControl, P2.failSafe,
  P3.riskCheck, P3.riskReasons, P3.safetyActions,
  P4.checkPoints, P4.supervisor, P4.protective, P4.signalMethod, P4.specialEdu,
  P5.confirm,
];

// ─── 기본값 병합 ─────────────────────────────────────────
export function buildDefaultValues(): Record<string, any> {
  const vals: Record<string, any> = {};
  for (const sec of SCHEMA) {
    for (const f of sec.fields) {
      if (f.defaultValue !== undefined) vals[f.key] = f.defaultValue;
      else vals[f.key] = f.type === 'checkbox' ? false : '';
    }
  }
  return vals;
}

// ─── 체크박스 → ☑/☐ 변환 ────────────────────────────────
export function normalizeForRender(values: Record<string, any>): Record<string, any> {
  const out: Record<string, any> = {};
  const schemaMap: Record<string, TemplateField> = {};
  for (const sec of SCHEMA) for (const f of sec.fields) schemaMap[f.key] = f;

  for (const [k, v] of Object.entries(values)) {
    const field = schemaMap[k];
    if (field?.type === 'checkbox') {
      out[k] = v ? CHECK_YES : CHECK_NO;
    } else if (field?.type === 'date' && v) {
      // 날짜 포맷 yyyy-MM-dd → yyyy.MM.dd
      out[k] = String(v).replace(/-/g, '.');
    } else {
      out[k] = v ?? '';
    }
  }
  return out;
}
