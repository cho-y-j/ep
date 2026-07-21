import { useEffect, useMemo, useState } from 'react';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar } from '../../components/ui';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';

/** /api/admin/equipment-types — EquipmentType 엔티티(SNAKE_CASE). */
type EquipType = { code: string; name: string; grp: string; sort_order: number; active: boolean };

type Requirement = 'REQUIRED' | 'OPTIONAL' | 'NONE';
/** /api/admin/equipment-types/{code}/documents — 전체 활성 EQUIPMENT 서류 + 이 종류에 대한 3상태 + 전역 정렬순서. */
type DocRow = { document_type_id: number; name: string; has_expiry: boolean; requirement: Requirement; sort_order: number };

const REQ_LABEL: Record<Requirement, string> = { REQUIRED: '필수', OPTIONAL: '선택', NONE: '해당없음' };
const REQ_ORDER: Requirement[] = ['REQUIRED', 'OPTIONAL', 'NONE'];

export default function EquipmentTypeDocsPage() {
  const [types, setTypes] = useState<EquipType[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [rows, setRows] = useState<DocRow[]>([]);
  const [loadingTypes, setLoadingTypes] = useState(true);
  const [loadingRows, setLoadingRows] = useState(false);
  // 종류 추가 / 서류 추가 인라인 폼 (null = 닫힘).
  const [addType, setAddType] = useState<{ code: string; name: string; grp: string; sortOrder: string } | null>(null);
  const [addDoc, setAddDoc] = useState<{ name: string; hasExpiry: boolean } | null>(null);
  const [saving, setSaving] = useState(false);
  const [q, setQ] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const r = await api.get<EquipType[]>('/api/admin/equipment-types');
        setTypes(r.data);
        if (r.data.length > 0) setSelected(r.data[0].code);
      } catch { toast.error('장비 종류를 불러올 수 없습니다'); }
      finally { setLoadingTypes(false); }
    })();
  }, []);

  useEffect(() => {
    if (!selected) { setRows([]); return; }
    setLoadingRows(true);
    api.get<DocRow[]>(`/api/admin/equipment-types/${selected}/documents`)
      .then((r) => setRows(r.data))
      .catch(() => toast.error('서류 체크리스트를 불러올 수 없습니다'))
      .finally(() => setLoadingRows(false));
  }, [selected]);

  // 종류를 그룹(건설기계/차량/기타)별로 — 등장 순서 유지(정렬은 sort_order). 검색어로 좌측 목록 필터.
  const qLower = q.trim().toLowerCase();
  const grouped = useMemo(() => {
    const map = new Map<string, EquipType[]>();
    for (const t of types) {
      if (qLower && !t.name.toLowerCase().includes(qLower)) continue;
      if (!map.has(t.grp)) map.set(t.grp, []);
      map.get(t.grp)!.push(t);
    }
    return Array.from(map.entries());
  }, [types, qLower]);

  /** 종류 추가 → POST /api/admin/equipment-types → 목록 갱신 + 새 종류 선택. */
  async function createType() {
    if (!addType) return;
    const code = addType.code.trim().toUpperCase();
    if (!code || !addType.name.trim() || !addType.grp.trim()) { toast.error('코드·이름·그룹을 모두 입력하세요'); return; }
    setSaving(true);
    try {
      await api.post('/api/admin/equipment-types', {
        code, name: addType.name.trim(), grp: addType.grp.trim(),
        sort_order: addType.sortOrder ? Number(addType.sortOrder) : undefined,
      });
      const r = await api.get<EquipType[]>('/api/admin/equipment-types');
      setTypes(r.data);
      setSelected(code);
      setAddType(null);
      toast.success('장비 종류를 추가했습니다');
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '종류 추가 실패');
    } finally { setSaving(false); }
  }

  /** 서류 추가 → 새 EQUIPMENT document_type 생성 → 선택 종류 체크리스트에 반영(기본 해당없음). */
  async function createDoc() {
    if (!addDoc) return;
    if (!addDoc.name.trim()) { toast.error('서류 이름을 입력하세요'); return; }
    setSaving(true);
    try {
      // 새 서류는 목록 맨 뒤로 — 기존 최대 sort_order + 10 (0으로 몰려 순서변경이 안 먹는 것 방지).
      const nextOrder = (rows.length ? Math.max(...rows.map((r) => r.sort_order)) : 0) + 10;
      await api.post('/api/admin/document-types', {
        name: addDoc.name.trim(), applies_to: 'EQUIPMENT',
        has_expiry: addDoc.hasExpiry, requires_verification: false,
        required: false, blocks_assignment: false, sort_order: nextOrder,
      });
      if (selected) {
        const r = await api.get<DocRow[]>(`/api/admin/equipment-types/${selected}/documents`);
        setRows(r.data);
      }
      setAddDoc(null);
      toast.success('서류 종류를 추가했습니다. 아래에서 필수/선택으로 지정하세요');
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '서류 추가 실패');
    } finally { setSaving(false); }
  }

  /** 3상태 즉시 저장 + 로컬 반영. */
  async function setReq(docId: number, requirement: Requirement) {
    if (!selected) return;
    const prev = rows;
    setRows((cur) => cur.map((d) => (d.document_type_id === docId ? { ...d, requirement } : d)));
    try {
      await api.patch(`/api/admin/equipment-types/${selected}/documents/${docId}`, { requirement });
    } catch (e: any) {
      setRows(prev); // 롤백
      toast.error(e?.response?.data?.message ?? '저장 실패');
    }
  }

  /** 위/아래 이동 → 인접 서류와 전역 sort_order 스왑(PATCH 2회) → 체크리스트 새로고침. 순서는 모든 종류 공통. */
  async function move(i: number, dir: -1 | 1) {
    const j = i + dir;
    if (!selected || j < 0 || j >= rows.length) return;
    // 두 행 위치 교환 후 10 간격으로 재번호 → 동일 sort_order(새 서류 0끼리 등)여도 확실히 순서가 바뀐다.
    const reordered = [...rows];
    [reordered[i], reordered[j]] = [reordered[j], reordered[i]];
    const patches = reordered
      .map((row, idx) => ({ row, order: (idx + 1) * 10 }))
      .filter(({ row, order }) => row.sort_order !== order)
      .map(({ row, order }) => api.patch(`/api/admin/document-types/${row.document_type_id}`, { sort_order: order }));
    try {
      await Promise.all(patches);
      const r = await api.get<DocRow[]>(`/api/admin/equipment-types/${selected}/documents`);
      setRows(r.data);
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '순서 변경 실패');
    }
  }

  return (
    <AppShell breadcrumb={[{ label: '장비종류 서류' }]}>
      <div className="max-w-6xl mx-auto px-6 py-8 space-y-4">
        <PageHeader
          title="장비종류 서류 체크리스트"
          subtitle="장비 종류를 고르고, 각 서류를 필수 / 선택 / 해당없음으로 지정합니다. 지정한 필수/선택은 해당 종류 장비의 등록·서류 화면 체크리스트에 반영됩니다."
          actions={
            <>
              <button type="button"
                onClick={() => { setAddDoc(null); setAddType({ code: '', name: '', grp: '', sortOrder: '' }); }}
                className="rounded-lg border border-brand-100 bg-white px-3 py-2 text-sm font-semibold text-brand-700 shadow-sm hover:bg-brand-50">
                + 종류 추가
              </button>
              <button type="button"
                onClick={() => { setAddType(null); setAddDoc({ name: '', hasExpiry: false }); }}
                className="rounded-lg border border-brand-100 bg-white px-3 py-2 text-sm font-semibold text-brand-700 shadow-sm hover:bg-brand-50">
                + 서류 추가
              </button>
            </>
          }
        />

        {addType && (
          <div className="card p-4 space-y-3">
            <div className="text-sm font-bold text-slate-900">새 장비 종류</div>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-4">
              <input className="input" placeholder="코드 (예: EXCAVATOR)" value={addType.code}
                onChange={(e) => setAddType({ ...addType, code: e.target.value })} />
              <input className="input" placeholder="이름 (예: 굴삭기)" value={addType.name}
                onChange={(e) => setAddType({ ...addType, name: e.target.value })} />
              <input className="input" placeholder="그룹 (건설기계/차량/기타)" value={addType.grp}
                onChange={(e) => setAddType({ ...addType, grp: e.target.value })} />
              <input className="input" type="number" placeholder="정렬순서" value={addType.sortOrder}
                onChange={(e) => setAddType({ ...addType, sortOrder: e.target.value })} />
            </div>
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setAddType(null)} className="rounded-lg px-3 py-2 text-sm text-slate-600 hover:bg-slate-100">취소</button>
              <button type="button" disabled={saving} onClick={createType} className="btn-primary disabled:opacity-50">{saving ? '추가 중…' : '추가'}</button>
            </div>
          </div>
        )}

        {addDoc && (
          <div className="card p-4 space-y-3">
            <div className="text-sm font-bold text-slate-900">새 장비 서류종류</div>
            <p className="text-xs text-slate-500">전체 장비 서류 목록에 추가됩니다. 추가 후 각 종류에서 필수/선택으로 지정하세요.</p>
            <div className="flex flex-wrap items-center gap-3">
              <input className="input min-w-[12rem] flex-1" placeholder="서류 이름 (예: 보험증권)" value={addDoc.name}
                onChange={(e) => setAddDoc({ ...addDoc, name: e.target.value })} />
              <label className="flex items-center gap-2 text-sm text-slate-700">
                <input type="checkbox" checked={addDoc.hasExpiry}
                  onChange={(e) => setAddDoc({ ...addDoc, hasExpiry: e.target.checked })} />
                만료관리
              </label>
            </div>
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setAddDoc(null)} className="rounded-lg px-3 py-2 text-sm text-slate-600 hover:bg-slate-100">취소</button>
              <button type="button" disabled={saving} onClick={createDoc} className="btn-primary disabled:opacity-50">{saving ? '추가 중…' : '추가'}</button>
            </div>
          </div>
        )}

        <FilterBar search={{ value: q, onChange: setQ, placeholder: '장비 종류 검색' }} />

        {loadingTypes ? (
          <div className="card p-8 text-center text-slate-400">불러오는 중…</div>
        ) : (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-[240px_1fr]">
            {/* 좌: 종류 목록 (그룹별) */}
            <div className="card p-0 overflow-hidden self-start">
              {grouped.map(([grp, list]) => (
                <div key={grp}>
                  <div className="px-3 py-2 bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500">{grp}</div>
                  {list.map((t) => (
                    <button key={t.code} type="button" onClick={() => setSelected(t.code)}
                      className={`w-full px-3 py-2 text-left text-sm border-b border-slate-100 flex items-center gap-2 ${
                        selected === t.code ? 'bg-brand-50 text-brand-700 font-semibold' : 'hover:bg-slate-50 text-slate-700'
                      } ${t.active ? '' : 'opacity-50'}`}>
                      <span className="truncate">{t.name}</span>
                      {!t.active && <span className="ml-auto text-[10px] text-slate-400">숨김</span>}
                    </button>
                  ))}
                </div>
              ))}
            </div>

            {/* 우: 선택된 종류의 서류 체크리스트 */}
            <div className="card p-0 overflow-hidden">
              <div className="px-4 py-2.5 bg-slate-50 border-b border-slate-200 text-sm font-semibold text-slate-700">
                {types.find((t) => t.code === selected)?.name ?? '종류'} — 서류 ({rows.length})
              </div>
              {loadingRows ? (
                <div className="p-8 text-center text-slate-400">불러오는 중…</div>
              ) : rows.length === 0 ? (
                <div className="p-6 text-center text-sm text-slate-400">등록된 장비 서류종류가 없습니다</div>
              ) : (
                <div className="divide-y divide-slate-100">
                  {rows.map((d, i) => (
                    <div key={d.document_type_id} className="px-4 py-3 flex flex-wrap items-center gap-x-3 gap-y-2">
                      <span className="font-medium text-slate-900 flex-1 min-w-[10rem] truncate">
                        {d.name}
                        {d.has_expiry && (
                          <span className="ml-2 text-[10px] px-1.5 py-0.5 rounded bg-amber-50 text-amber-700 border border-amber-200 align-middle">만료관리</span>
                        )}
                      </span>
                      <div className="inline-flex rounded-lg border border-slate-300 overflow-hidden">
                        <button type="button" disabled={i === 0} onClick={() => move(i, -1)}
                          title="위로" aria-label="위로"
                          className="px-2 py-1 text-xs text-slate-600 hover:bg-slate-50 disabled:opacity-30 disabled:cursor-not-allowed">↑</button>
                        <button type="button" disabled={i === rows.length - 1} onClick={() => move(i, 1)}
                          title="아래로" aria-label="아래로"
                          className="px-2 py-1 text-xs text-slate-600 border-l border-slate-300 hover:bg-slate-50 disabled:opacity-30 disabled:cursor-not-allowed">↓</button>
                      </div>
                      <div className="inline-flex rounded-lg border border-slate-300 overflow-hidden">
                        {REQ_ORDER.map((r) => {
                          const on = d.requirement === r;
                          const tone = r === 'REQUIRED' ? 'bg-rose-600' : r === 'OPTIONAL' ? 'bg-brand-600' : 'bg-slate-500';
                          return (
                            <button key={r} type="button" onClick={() => setReq(d.document_type_id, r)}
                              className={`px-3 py-1 text-xs font-semibold border-l first:border-l-0 border-slate-300 transition ${
                                on ? `${tone} text-white` : 'bg-white text-slate-500 hover:bg-slate-50'
                              }`}>
                              {REQ_LABEL[r]}
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </AppShell>
  );
}
