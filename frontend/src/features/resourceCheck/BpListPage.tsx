import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import { useAuth } from '../auth/AuthContext';
import IssueResourceCheckDialog from './IssueResourceCheckDialog';
import SupplierResourcePickerDialog, { type PickedResource } from './SupplierResourcePickerDialog';
import {
  CHECK_TYPE_LABEL, CHECK_STATUS_LABEL, CHECK_STATUS_CHIP_CLS,
  type ResourceCheckResponse,
} from '../../types/resourceCheck';
import type { InspectionResponse } from '../../types/safety';

const TYPE_OPTIONS = Object.entries(CHECK_TYPE_LABEL).map(([value, label]) => ({ value, label }));
const STATUS_OPTIONS = Object.entries(CHECK_STATUS_LABEL).map(([value, label]) => ({ value, label }));

export default function ResourceCheckBpList() {
  const { user } = useAuth();
  // 공급사도 자기+자식 자원에 직접 발행·승인(BP 미사용 현실 대응) — 발행 진입점은 공급사에게만 노출.
  const isSupplier = user?.role === 'EQUIPMENT_SUPPLIER' || user?.role === 'MANPOWER_SUPPLIER';
  const [items, setItems] = useState<ResourceCheckResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<number | null>(null);
  // 클라이언트 필터 — 로드된 요청을 좁힘.
  const [q, setQ] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  // 공급사 발행: 자원 선택 → IssueResourceCheckDialog (계획서 없이 발행 — BpReceivedReviewsPage 경로 재사용).
  const [pickerOpen, setPickerOpen] = useState(false);
  const [checkTarget, setCheckTarget] = useState<PickedResource | null>(null);
  // A2 연결뷰: target(owner) 별 SafetyInspection 상태 병기. key = `${owner_type}:${owner_id}`.
  const [inspByTarget, setInspByTarget] = useState<Map<string, InspectionResponse[]>>(new Map());

  const load = async () => {
    setLoading(true);
    try {
      const res = await api.get<ResourceCheckResponse[]>('/api/resource-checks/bp-list');
      setItems(res.data);
    } finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);

  // distinct target 만 by-target 조회. 신규 엔드포인트 미배포(404)면 해당 항목은 map 에 안 담겨 병기 생략.
  // by-target 은 BP 본인 현장 스코프 전용(B5) — 공급사 발행 뷰에선 호출 자체를 생략(403 소음 방지).
  useEffect(() => {
    if (items.length === 0 || isSupplier) { setInspByTarget(new Map()); return; }
    let cancelled = false;
    const distinct = new Map<string, { targetType: 'VEHICLE' | 'PERSON'; targetId: number }>();
    for (const r of items) {
      distinct.set(`${r.owner_type}:${r.owner_id}`, {
        targetType: r.owner_type === 'EQUIPMENT' ? 'VEHICLE' : 'PERSON',
        targetId: r.owner_id,
      });
    }
    void Promise.all(
      Array.from(distinct.entries()).map(([key, t]) =>
        api.get<InspectionResponse[]>('/api/safety-inspections/by-target', {
          params: { targetType: t.targetType, targetId: t.targetId },
        })
          .then((res) => [key, res.data] as const)
          .catch(() => null),
      ),
    ).then((results) => {
      if (cancelled) return;
      const map = new Map<string, InspectionResponse[]>();
      for (const row of results) if (row) map.set(row[0], row[1]);
      setInspByTarget(map);
    });
    return () => { cancelled = true; };
  }, [items, isSupplier]);

  const inspStatusFor = (r: ResourceCheckResponse): { label: string; cls: string } | null => {
    const list = inspByTarget.get(`${r.owner_type}:${r.owner_id}`);
    if (!list) return null;
    if (list.length === 0) return { label: '없음', cls: 'bg-slate-100 text-slate-500' };
    if (list.some((i) => i.status === 'COMPLETED')) return { label: '완료', cls: 'bg-emerald-100 text-emerald-800' };
    return { label: '일정 대기', cls: 'bg-amber-100 text-amber-800' };
  };

  const review = async (id: number, action: 'approve' | 'reject') => {
    let note: string | null = null;
    if (action === 'reject') {
      note = window.prompt('반려 사유 (선택)') ?? '';
    }
    setBusy(id);
    try {
      await api.post(`/api/resource-checks/${id}/${action}`, { note });
      toast.success(action === 'approve' ? '승인됨' : '반려됨');
      void load();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '실패');
    } finally { setBusy(null); }
  };

  const qLower = q.trim().toLowerCase();
  const filtered = items.filter((r) => {
    if (statusFilter && r.status !== statusFilter) return false;
    if (typeFilter && r.check_type !== typeFilter) return false;
    if (qLower) {
      const hay = `${CHECK_TYPE_LABEL[r.check_type]} ${r.owner_label} ${r.supplier_company_name ?? ''} ${r.notes ?? ''}`.toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    return true;
  });
  const activeFilterCount = [q, statusFilter, typeFilter].filter(Boolean).length;
  const resetFilters = () => { setQ(''); setStatusFilter(''); setTypeFilter(''); };

  return (
    <AppShell>
      <PageHeader
        title="보낸 점검 요청"
        subtitle="자원에 발행한 자동차 안전점검·건강검진·안전교육 등 — 회신 검토"
        actions={isSupplier ? (
          <button type="button" onClick={() => setPickerOpen(true)} className="btn-primary text-sm">
            검사 통보
          </button>
        ) : undefined}
      />

      <FilterBar
        search={{ value: q, onChange: setQ, placeholder: '종류·자원·공급사 검색' }}
        activeFilterCount={activeFilterCount}
        onReset={resetFilters}
      >
        <FilterSelect value={typeFilter} onChange={setTypeFilter} placeholder="종류 전체" options={TYPE_OPTIONS} />
        <FilterSelect value={statusFilter} onChange={setStatusFilter} placeholder="상태 전체" options={STATUS_OPTIONS} />
      </FilterBar>

      {loading ? (
        <div className="text-sm text-slate-400">로딩…</div>
      ) : items.length === 0 ? (
        <div className="card p-8 text-center text-slate-400">보낸 요청이 없습니다.</div>
      ) : filtered.length === 0 ? (
        <div className="card p-8 text-center text-slate-400">조건에 맞는 요청이 없습니다.</div>
      ) : (
        <div className="space-y-2">
          {filtered.map((r) => {
            const si = inspStatusFor(r);
            return (
            <div key={r.id} className="card p-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div className="min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="font-bold text-slate-900">{CHECK_TYPE_LABEL[r.check_type]}</span>
                  <span className={`px-1.5 py-0.5 text-[10px] rounded-full font-semibold ${CHECK_STATUS_CHIP_CLS[r.status]}`}>
                    {CHECK_STATUS_LABEL[r.status]}
                  </span>
                  {si && (
                    <span className={`px-1.5 py-0.5 text-[10px] rounded-full font-semibold ${si.cls}`}>
                      안전점검 {si.label}
                    </span>
                  )}
                </div>
                <div className="mt-0.5 text-sm text-slate-700 truncate">
                  {r.owner_label} → {r.supplier_company_name ?? '공급사'}
                  {r.due_date && <span className="ml-2 text-xs text-rose-700">마감 {r.due_date}</span>}
                </div>
                {r.notes && <div className="mt-0.5 text-xs text-slate-500 truncate">{r.notes}</div>}
              </div>
              <div className="shrink-0 flex items-center gap-2">
                {r.document_id && (
                  <a href={`/api/documents/${r.document_id}/file`} target="_blank" rel="noopener noreferrer"
                     className="px-3 py-1.5 text-xs rounded border border-slate-300 text-slate-700 hover:bg-slate-50">
                    제출 서류 보기
                  </a>
                )}
                {r.status === 'SUBMITTED' && (
                  <>
                    <button onClick={() => void review(r.id, 'approve')} disabled={busy === r.id}
                            className="px-3 py-1.5 text-xs rounded bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50">
                      승인 → 투입 대기
                    </button>
                    <button onClick={() => void review(r.id, 'reject')} disabled={busy === r.id}
                            className="px-3 py-1.5 text-xs rounded border border-rose-500 text-rose-700 hover:bg-rose-50 disabled:opacity-50">
                      반려
                    </button>
                  </>
                )}
              </div>
            </div>
            );
          })}
        </div>
      )}

      {/* 공급사 발행 — 자원 선택 후 검사·교육·검진 날짜 통보 (계획서 없이 발행) */}
      {pickerOpen && (
        <SupplierResourcePickerDialog
          open
          includeEquipment={user?.role === 'EQUIPMENT_SUPPLIER'}
          onClose={() => setPickerOpen(false)}
          onPick={(t) => { setPickerOpen(false); setCheckTarget(t); }}
        />
      )}
      {checkTarget && (
        <IssueResourceCheckDialog
          open
          onClose={() => setCheckTarget(null)}
          onIssued={() => { void load(); }}
          workPlanId={null}
          ownerType={checkTarget.ownerType}
          ownerId={checkTarget.ownerId}
          ownerLabel={checkTarget.ownerLabel}
          supplierCompanyId={checkTarget.supplierCompanyId}
          supplierCompanyName={checkTarget.supplierCompanyName}
        />
      )}
    </AppShell>
  );
}
