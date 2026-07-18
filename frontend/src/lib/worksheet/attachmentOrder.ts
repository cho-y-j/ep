// 첨부 자동 조립 — 실양식 67p 표지 체크리스트 순서 규칙(기획 §3.6.2).
// 등록원부 → 자동차/건설기계 등록증 → 사업자등록증
//  → 조종원별 자격묶음(운전면허·기초안전·화물자격·수료) → 교육실시확인서
//  → 안전인증서 → 제원표 → 보험 → 기타
// 조종원 2인이면 각자 자격묶음이 조종원 순서대로 묶여 배치된다.
import type { DocumentResponse } from '../../types/document';

// 장비/회사 서류 phase.
function equipmentPhase(name: string): number {
  if (name.includes('등록원부')) return 0;
  if (name.includes('사업자')) return 2;         // 사업자등록증 (等록증보다 먼저 판정)
  if (name.includes('등록증')) return 1;         // 자동차/건설기계 등록증
  if (name.includes('안전인증') || name.includes('KCs') || name.includes('KCS')) return 5;
  if (name.includes('제원') || name.includes('작업반경') || name.includes('반경도')) return 6;
  if (name.includes('보험')) return 7;
  if (name.includes('성적서')) return 8;
  return 9;
}

// 인력 서류 phase(자격묶음=3 / 교육확인서=4) + 묶음 내 순서(sub).
function personPhaseSub(name: string): { phase: number; sub: number } {
  if (name.includes('운전면허')) return { phase: 3, sub: 0 };
  if (name.includes('기초')) return { phase: 3, sub: 1 };   // 기초안전보건교육 이수증
  if (name.includes('화물')) return { phase: 3, sub: 2 };
  if (name.includes('조종사') || name.includes('고소작업대') || name.includes('수료') || name.includes('건설기계조종'))
    return { phase: 3, sub: 3 };
  if (name.includes('면허') || name.includes('자격')) return { phase: 3, sub: 4 };
  if (name.includes('확인서') || name.includes('실시')) return { phase: 4, sub: 0 };  // 교육실시확인서
  if (name.includes('교육') || name.includes('이수')) return { phase: 4, sub: 1 };
  return { phase: 9, sub: 0 };
}

interface Entry { id: number; phase: number; person: number; sub: number; nat: number; }

/** 선택된 장비/인력 서류를 표지 체크리스트 순서로 정렬한 doc id 배열을 반환. */
export function buildAttachmentOrder(params: {
  equipDocs: DocumentResponse[];
  selectedEquipDocIds: Set<number>;
  orderedPersonIds: number[];                     // 조종원 먼저, 그다음 나머지 역할 순
  personDocs: Record<number, DocumentResponse[]>;
  selectedPersonDocIds: Set<number>;
}): number[] {
  const { equipDocs, selectedEquipDocIds, orderedPersonIds, personDocs, selectedPersonDocIds } = params;
  const entries: Entry[] = [];

  equipDocs.forEach((d, nat) => {
    if (!selectedEquipDocIds.has(d.id)) return;
    entries.push({ id: d.id, phase: equipmentPhase(d.document_type_name || ''), person: -1, sub: 0, nat });
  });

  orderedPersonIds.forEach((pid, pIdx) => {
    (personDocs[pid] ?? []).forEach((d, nat) => {
      if (!selectedPersonDocIds.has(d.id)) return;
      const { phase, sub } = personPhaseSub(d.document_type_name || '');
      entries.push({ id: d.id, phase, person: pIdx, sub, nat });
    });
  });

  entries.sort((a, b) => {
    if (a.phase !== b.phase) return a.phase - b.phase;
    // 인력 phase(3=자격묶음, 4=교육확인서)만 조종원 순서로 그룹핑.
    const ap = (a.phase === 3 || a.phase === 4) ? a.person : 0;
    const bp = (b.phase === 3 || b.phase === 4) ? b.person : 0;
    if (ap !== bp) return ap - bp;
    if (a.sub !== b.sub) return a.sub - b.sub;
    return a.nat - b.nat;
  });

  return entries.map((e) => e.id);
}
