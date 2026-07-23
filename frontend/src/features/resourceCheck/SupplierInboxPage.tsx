import { useEffect, useState, type ReactElement } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import {
  CHECK_TYPE_LABEL, CHECK_STATUS_LABEL, CHECK_STATUS_CHIP_CLS,
  type ResourceCheckResponse,
} from '../../types/resourceCheck';

const TYPE_OPTIONS = Object.entries(CHECK_TYPE_LABEL).map(([value, label]) => ({ value, label }));
const STATUS_OPTIONS = Object.entries(CHECK_STATUS_LABEL).map(([value, label]) => ({ value, label }));

export default function ResourceCheckSupplierInbox() {
  const [items, setItems] = useState<ResourceCheckResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<number | null>(null);
  // 클라이언트 필터 — 로드된 요청을 좁힘.
  const [q, setQ] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const res = await api.get<ResourceCheckResponse[]>('/api/resource-checks/supplier-list');
      setItems(res.data);
    } finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);

  const onUpload = async (req: ResourceCheckResponse, files: File[]) => {
    setBusy(req.id);
    try {
      // 파일 1개면 그대로, 2개 이상이면 올린 순서대로 서버가 1개 PDF로 병합.
      const fd = new FormData();
      for (const f of files) fd.append('file', f);
      await api.post(`/api/resource-checks/${req.id}/submit-file`, fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      toast.success('회신 완료 — BP 검토 대기');
      void load();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '회신 실패');
    } finally { setBusy(null); }
  };

  const qLower = q.trim().toLowerCase();
  const filtered = items.filter((r) => {
    if (statusFilter && r.status !== statusFilter) return false;
    if (typeFilter && r.check_type !== typeFilter) return false;
    if (qLower) {
      const hay = `${CHECK_TYPE_LABEL[r.check_type]} ${r.owner_label} ${r.notes ?? ''}`.toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    return true;
  });
  const activeFilterCount = [q, statusFilter, typeFilter].filter(Boolean).length;
  const resetFilters = () => { setQ(''); setStatusFilter(''); setTypeFilter(''); };

  const renderRow = (r: ResourceCheckResponse) => (
    <div key={r.id} className="card p-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="font-bold text-slate-900">{CHECK_TYPE_LABEL[r.check_type]}</span>
          <span className={`px-1.5 py-0.5 text-[10px] rounded-full font-semibold ${CHECK_STATUS_CHIP_CLS[r.status]}`}>
            {CHECK_STATUS_LABEL[r.status]}
          </span>
        </div>
        <div className="mt-0.5 text-sm text-slate-700 truncate">
          {r.owner_label}
          {r.due_date && (
            <span className="ml-2 text-xs text-rose-700">
              마감 {r.due_date}{r.due_time ? ` ${r.due_time.slice(0, 5)}` : ''}
            </span>
          )}
        </div>
        {r.notes && <div className="mt-0.5 text-xs text-slate-500 truncate">{r.notes}</div>}
        {r.review_note && (
          <div className="mt-1 px-2 py-1 rounded bg-rose-50 border border-rose-200 text-xs text-rose-800">
            BP 메모: {r.review_note}
          </div>
        )}
      </div>
      <div className="shrink-0 flex items-center gap-2">
        {(r.status === 'REQUESTED' || r.status === 'REJECTED') && (
          <label className={`px-3 py-1.5 text-xs rounded border border-blue-500 text-blue-700 hover:bg-blue-50 cursor-pointer ${busy === r.id ? 'opacity-50 pointer-events-none' : ''}`}>
            {busy === r.id ? '업로드 중…' : '서류 첨부 회신'}
            <input type="file" accept="image/*,application/pdf" multiple className="hidden"
                   onChange={(e) => {
                     const fs = Array.from(e.target.files ?? []);
                     if (fs.length) void onUpload(r, fs);
                     e.target.value = '';
                   }} />
          </label>
        )}
        {r.document_id && (
          <a href={`/api/documents/${r.document_id}/file`} target="_blank" rel="noopener noreferrer"
             className="px-3 py-1.5 text-xs rounded border border-slate-300 text-slate-700 hover:bg-slate-50">
            제출 서류 보기
          </a>
        )}
      </div>
    </div>
  );

  // R2 조합 묶음 — 같은 combo_equipment_id 행을 장비 라벨 헤더 아래로 묶음(첫 등장 위치에 배치).
  // combo_equipment_id 없는 행은 기존 단독 렌더 그대로(무회귀).
  const blocks: ReactElement[] = [];
  const seenCombo = new Set<number>();
  for (const r of filtered) {
    if (r.combo_equipment_id == null) { blocks.push(renderRow(r)); continue; }
    if (seenCombo.has(r.combo_equipment_id)) continue;
    seenCombo.add(r.combo_equipment_id);
    const group = filtered.filter((x) => x.combo_equipment_id === r.combo_equipment_id);
    blocks.push(
      <div key={`combo-${r.combo_equipment_id}`}
           className="rounded-xl border border-indigo-200 bg-indigo-50/40 p-2 space-y-2">
        <div className="flex items-center gap-2 px-1">
          <span className="px-1.5 py-0.5 text-[10px] rounded-full font-semibold bg-indigo-600 text-white">조합</span>
          <span className="text-sm font-bold text-slate-900 truncate">
            {r.combo_equipment_label ?? `장비 #${r.combo_equipment_id}`}
          </span>
          <span className="text-xs text-slate-500">점검 {group.length}건</span>
        </div>
        {group.map((g) => renderRow(g))}
      </div>,
    );
  }

  return (
    <AppShell>
      <PageHeader
        title="받은 점검 요청"
        subtitle="BP 가 보낸 자동차 반입검사·건강검진·안전교육 등 요청"
      />

      <FilterBar
        search={{ value: q, onChange: setQ, placeholder: '종류·자원 검색' }}
        activeFilterCount={activeFilterCount}
        onReset={resetFilters}
      >
        <FilterSelect value={typeFilter} onChange={setTypeFilter} placeholder="종류 전체" options={TYPE_OPTIONS} />
        <FilterSelect value={statusFilter} onChange={setStatusFilter} placeholder="상태 전체" options={STATUS_OPTIONS} />
      </FilterBar>

      {loading ? (
        <div className="text-sm text-slate-400">로딩…</div>
      ) : items.length === 0 ? (
        <div className="card p-8 text-center text-slate-400">받은 요청이 없습니다.</div>
      ) : filtered.length === 0 ? (
        <div className="card p-8 text-center text-slate-400">조건에 맞는 요청이 없습니다.</div>
      ) : (
        <div className="space-y-2">
          {blocks}
        </div>
      )}
    </AppShell>
  );
}
