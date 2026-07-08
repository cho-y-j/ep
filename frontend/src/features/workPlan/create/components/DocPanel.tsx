import type { DocumentResponse } from '../../../../types/document';
import type { PersonResponse } from '../../../../types/person';
import type { EquipmentResponse } from '../../../../types/equipment';
import type { DocPreviewTarget, RequiredRoleDef } from '../types';
import { DocCard } from './DocCard';

/** attachmentOrder 순으로 정렬. order 에 없으면 원래 순서 유지 (안정 정렬). */
function sortByOrder<T extends { id: number }>(docs: T[], order: number[] | undefined): T[] {
  if (!order || order.length === 0) return docs;
  const idx = new Map(order.map((id, i) => [id, i]));
  return [...docs].sort((a, b) => {
    const ai = idx.get(a.id) ?? Number.MAX_SAFE_INTEGER;
    const bi = idx.get(b.id) ?? Number.MAX_SAFE_INTEGER;
    return ai - bi;
  });
}

interface EquipDocsGroupProps {
  equipment: EquipmentResponse;
  docs: DocumentResponse[];
  selectedIds: Set<number>;
  onSelect: (s: Set<number>) => void;
  onPreview: (t: DocPreviewTarget) => void;
  /** 그룹 내에서 a/b 두 첨부 위치를 교환. ↑↓ 클릭 시 호출. */
  onSwap?: (aId: number, bId: number) => void;
  /** 정렬용 attachmentOrder. */
  order?: number[];
}

/** 장비 첨부 서류 — 카드 그리드. */
export function EquipDocsGroup({ equipment, docs, selectedIds, onSelect, onPreview, onSwap, order }: EquipDocsGroupProps) {
  const sorted = sortByOrder(docs, order);
  const checkedCount = sorted.filter((d) => selectedIds.has(d.id)).length;
  return (
    <div className="rounded-xl bg-white">
      <div className="flex items-center justify-between mb-2">
        <div className="text-sm font-semibold text-slate-800">
          장비 첨부 서류 · {equipment.vehicle_no || equipment.model || '#' + equipment.id}
          <span className="ml-2 text-[11px] bg-blue-100 text-blue-800 px-1.5 py-0.5 rounded-full">
            {checkedCount}/{sorted.length}
          </span>
        </div>
      </div>
      {sorted.length > 0 ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-3">
          {sorted.map((d, i) => (
            <DocCard
              key={d.id}
              doc={d}
              checked={selectedIds.has(d.id)}
              onToggle={() => {
                const n = new Set(selectedIds);
                if (n.has(d.id)) n.delete(d.id);
                else n.add(d.id);
                onSelect(n);
              }}
              onPreview={() =>
                onPreview({
                  docId: d.id,
                  category: d.document_type_name,
                  mimeType: d.content_type,
                  originalName: d.file_name,
                  ownerName: equipment.vehicle_no || '#' + equipment.id,
                })
              }
              onMove={
                onSwap
                  ? (dir) => {
                      const j = i + dir;
                      if (j < 0 || j >= sorted.length) return;
                      onSwap(d.id, sorted[j].id);
                    }
                  : undefined
              }
            />
          ))}
        </div>
      ) : (
        <div className="text-xs text-slate-400 italic py-4 text-center border border-dashed border-slate-200 rounded-lg">
          등록된 서류 없음
        </div>
      )}
    </div>
  );
}

interface RoleDocsGroupProps {
  role: RequiredRoleDef;
  people: PersonResponse[];
  personDocs: Record<number, DocumentResponse[]>;
  selectedIds: Set<number>;
  onSelect: (s: Set<number>) => void;
  onPreview: (t: DocPreviewTarget) => void;
  onSwap?: (aId: number, bId: number) => void;
  order?: number[];
}

/** 한 역할에 배정된 모든 인원의 서류 — 카드 그리드. */
export function RoleDocsGroup({ role, people, personDocs, selectedIds, onSelect, onPreview, onSwap, order }: RoleDocsGroupProps) {
  const allDocs: { d: DocumentResponse; person: PersonResponse }[] = [];
  for (const person of people) {
    const docs = personDocs[person.id] ?? [];
    for (const d of docs) allDocs.push({ d, person });
  }
  const sorted = sortByOrder(
    allDocs.map(({ d, person }) => ({ id: d.id, d, person })),
    order
  );
  const checkedCount = sorted.filter(({ d }) => selectedIds.has(d.id)).length;

  return (
    <div className="rounded-xl bg-white">
      <div className="flex items-center justify-between mb-2">
        <div className="text-sm font-semibold text-slate-800">
          {role.label} 첨부 서류 · {people.length}명
          <span className="ml-2 text-[11px] bg-emerald-100 text-emerald-800 px-1.5 py-0.5 rounded-full">
            {checkedCount}/{sorted.length}
          </span>
        </div>
      </div>
      {sorted.length > 0 ? (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-3">
          {sorted.map(({ d, person }, i) => (
            <DocCard
              key={person.id + '_' + d.id}
              doc={d}
              ownerHint={people.length > 1 ? person.name : undefined}
              checked={selectedIds.has(d.id)}
              onToggle={() => {
                const n = new Set(selectedIds);
                if (n.has(d.id)) n.delete(d.id);
                else n.add(d.id);
                onSelect(n);
              }}
              onPreview={() =>
                onPreview({
                  docId: d.id,
                  category: d.document_type_name,
                  mimeType: d.content_type,
                  originalName: d.file_name,
                  ownerName: person.name,
                })
              }
              onMove={
                onSwap
                  ? (dir) => {
                      const j = i + dir;
                      if (j < 0 || j >= sorted.length) return;
                      onSwap(d.id, sorted[j].d.id);
                    }
                  : undefined
              }
            />
          ))}
        </div>
      ) : (
        <div className="text-xs text-slate-400 italic py-4 text-center border border-dashed border-slate-200 rounded-lg">
          등록된 서류 없음
        </div>
      )}
    </div>
  );
}
