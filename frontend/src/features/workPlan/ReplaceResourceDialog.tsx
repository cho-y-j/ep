import { useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import { equipmentCategoryLabel, type EquipmentResponse } from '../../types/equipment';
import type { PersonResponse } from '../../types/person';
import type { WorkPlanResponse } from '../../types/workPlan';
import type { DeployCheckResult } from '../readiness/DeployCheckCard';

type BadgeState = Record<number, DeployCheckResult | 'loading'>;

type Props = {
  workPlanId: number;
  siteId?: number | null;
  /** 장비 유지 시 조종원 후보를 불러올 현재 장비 공급사(원본 장비의 공급사). */
  currentEquipmentSupplierId?: number | null;
  /** 교체 후보에서 제외할 현재(원본) 장비 id. */
  currentEquipmentId?: number | null;
  onClose: () => void;
  onReplaced: (newPlanId: number) => void;
  onOpenChangeRequest: () => void;
};

/** deploy-check 판정 배지 — 가능(초록) / 부족 N건(주황, hover 상세). */
function DeployBadge({ state }: { state?: DeployCheckResult | 'loading' }) {
  if (state === undefined || state === 'loading') {
    return <span className="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-semibold text-slate-400">판정 중…</span>;
  }
  if (state.ready) {
    return <span className="shrink-0 rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-bold text-emerald-800">✓ 가능</span>;
  }
  const title = state.blocks.map((b) => `${b.label}${b.detail ? ' · ' + b.detail : ''}`).join('\n');
  return (
    <span title={title} className="shrink-0 cursor-help rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-bold text-amber-800">
      ! 부족 {state.blocks.length}건
    </span>
  );
}

/**
 * P1c: L2 자원 교체 다이얼로그. 후보(현장 참여 공급사 장비)에 deploy-check 배지 표시 →
 * 새 장비/조종원 선택 + 사유 → replace-resource → 새 계획서 편집으로 이동(원본 자동 종료).
 */
export default function ReplaceResourceDialog({
  workPlanId, siteId, currentEquipmentSupplierId, currentEquipmentId, onClose, onReplaced, onOpenChangeRequest,
}: Props) {
  const [equipCandidates, setEquipCandidates] = useState<EquipmentResponse[]>([]);
  const [equipBadges, setEquipBadges] = useState<BadgeState>({});
  const [newEquipmentId, setNewEquipmentId] = useState<number | null>(null);
  const [operatorCandidates, setOperatorCandidates] = useState<PersonResponse[]>([]);
  const [operatorBadges, setOperatorBadges] = useState<BadgeState>({});
  const [selectedOperatorIds, setSelectedOperatorIds] = useState<number[]>([]);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<number | null>(null);

  // 장비 후보 + 각 후보 deploy-check 배지 로드.
  useEffect(() => {
    let cancelled = false;
    const params = siteId ? { siteId } : {};
    api.get<EquipmentResponse[]>(`/api/work-plans/${workPlanId}/candidates/equipment`)
      .then((r) => {
        if (cancelled) return;
        const candidates = r.data.filter((eq) => eq.id !== currentEquipmentId);
        setEquipCandidates(candidates);
        candidates.forEach((eq) => {
          setEquipBadges((p) => ({ ...p, [eq.id]: 'loading' }));
          api.get<DeployCheckResult>(`/api/resources/equipment/${eq.id}/deploy-check`, { params })
            .then((res) => { if (!cancelled) setEquipBadges((p) => ({ ...p, [eq.id]: res.data })); })
            .catch(() => { if (!cancelled) setEquipBadges((p) => { const n = { ...p }; delete n[eq.id]; return n; }); });
        });
      })
      .catch(() => { if (!cancelled) setEquipCandidates([]); });
    return () => { cancelled = true; };
  }, [workPlanId, siteId, currentEquipmentId]);

  // 조종원 후보 공급사 = 선택한 새 장비의 공급사, 없으면 현재 장비 공급사.
  const operatorSupplierId = useMemo(() => {
    if (newEquipmentId != null) return equipCandidates.find((e) => e.id === newEquipmentId)?.supplier_id ?? null;
    return currentEquipmentSupplierId ?? null;
  }, [newEquipmentId, equipCandidates, currentEquipmentSupplierId]);

  // 조종원 후보 + 배지 로드.
  useEffect(() => {
    setSelectedOperatorIds([]);
    if (!operatorSupplierId) { setOperatorCandidates([]); return; }
    let cancelled = false;
    const params = siteId ? { siteId } : {};
    api.get<{ content: PersonResponse[] }>('/api/persons', { params: { supplierId: operatorSupplierId, role: 'OPERATOR', size: 200 } })
      .then((r) => {
        if (cancelled) return;
        const ops = r.data.content ?? [];
        setOperatorCandidates(ops);
        ops.forEach((p) => {
          setOperatorBadges((prev) => ({ ...prev, [p.id]: 'loading' }));
          api.get<DeployCheckResult>(`/api/resources/person/${p.id}/deploy-check`, { params })
            .then((res) => { if (!cancelled) setOperatorBadges((prev) => ({ ...prev, [p.id]: res.data })); })
            .catch(() => { if (!cancelled) setOperatorBadges((prev) => { const n = { ...prev }; delete n[p.id]; return n; }); });
        });
      })
      .catch(() => { if (!cancelled) setOperatorCandidates([]); });
    return () => { cancelled = true; };
  }, [operatorSupplierId, siteId]);

  const toggleOperator = (id: number) =>
    setSelectedOperatorIds((prev) => prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]);

  const canSubmit = newEquipmentId != null || selectedOperatorIds.length > 0;

  async function execute() {
    if (!canSubmit) return;
    setBusy(true);
    setError(null);
    try {
      const res = await api.post<WorkPlanResponse>(`/api/work-plans/${workPlanId}/replace-resource`, {
        new_equipment_id: newEquipmentId ?? undefined,
        new_operator_person_ids: selectedOperatorIds.length ? selectedOperatorIds : undefined,
        reason: reason.trim() || undefined,
      });
      setDone(res.data.id);
    } catch (e: any) {
      setError(e?.response?.data?.message ?? '자원 교체에 실패했습니다');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="flex max-h-[90vh] w-full max-w-lg flex-col overflow-hidden rounded-xl bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-3">
          <h3 className="text-base font-bold text-slate-900">자원 교체</h3>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-700">✕</button>
        </div>

        {done != null ? (
          <div className="space-y-4 p-5">
            <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-3 text-sm text-emerald-800">
              새 작업계획서 <b>#{done}</b> 가 생성되고, 원본 계획서는 자동 종료되었습니다. 새 계획서에서 서명을 다시 수집하세요(전원 재서명).
            </div>
            <div className="flex justify-end gap-2">
              <button type="button" onClick={onClose} className="rounded-md border border-slate-200 px-4 py-1.5 text-sm font-semibold text-slate-600 hover:bg-slate-50">닫기</button>
              <button type="button" onClick={() => onReplaced(done)} className="rounded-md bg-blue-600 px-4 py-1.5 text-sm font-bold text-white hover:bg-blue-700">
                새 계획서 편집 →
              </button>
            </div>
          </div>
        ) : (
          <>
            <div className="min-h-0 flex-1 space-y-4 overflow-y-auto p-5">
              <p className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500">
                교체하면 새 계획서가 생성되고 원본은 자동 종료됩니다. 워크시트 내용은 복사되고, 서명은 전원 다시 받습니다.
              </p>

              {/* 새 장비 선택 */}
              <div>
                <div className="mb-1.5 text-xs font-bold text-slate-700">새 장비 (현장 참여 공급사 · 각 후보 투입가능 판정)</div>
                {equipCandidates.length === 0 ? (
                  <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-xs text-slate-500">
                    교체 후보 장비가 없습니다. 같은 현장의 서류심사·반입검사를 마친 자원이 후보로 표시됩니다.
                  </div>
                ) : (
                  <div className="space-y-1.5">
                    <label className="flex cursor-pointer items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm hover:bg-slate-50">
                      <input type="radio" checked={newEquipmentId == null} onChange={() => setNewEquipmentId(null)} />
                      <span className="text-slate-600">장비 유지 (조종원만 교체)</span>
                    </label>
                    {equipCandidates.map((eq) => (
                      <label key={eq.id} className="flex cursor-pointer items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm hover:bg-slate-50">
                        <input type="radio" checked={newEquipmentId === eq.id} onChange={() => setNewEquipmentId(eq.id)} />
                        <span className="min-w-0 flex-1 truncate">
                          <span className="font-semibold text-slate-800">{eq.vehicle_no ?? eq.model ?? `장비#${eq.id}`}</span>
                          <span className="ml-1 text-xs text-slate-500">{equipmentCategoryLabel(eq.category)}{eq.supplier_name ? ` · ${eq.supplier_name}` : ''}</span>
                        </span>
                        <DeployBadge state={equipBadges[eq.id]} />
                      </label>
                    ))}
                  </div>
                )}
              </div>

              {/* 조종원 선택 (선택) */}
              <div>
                <div className="mb-1.5 text-xs font-bold text-slate-700">조종원 교체 (선택 · {newEquipmentId != null ? '새 장비 공급사' : '현재 장비 공급사'})</div>
                {operatorCandidates.length === 0 ? (
                  <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-400">
                    조종원 후보가 없습니다.
                  </div>
                ) : (
                  <div className="space-y-1.5">
                    {operatorCandidates.map((p) => (
                      <label key={p.id} className="flex cursor-pointer items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm hover:bg-slate-50">
                        <input type="checkbox" checked={selectedOperatorIds.includes(p.id)} onChange={() => toggleOperator(p.id)} />
                        <span className="min-w-0 flex-1 truncate">
                          <span className="font-semibold text-slate-800">{p.name}</span>
                          {p.phone && <span className="ml-1 text-xs text-slate-500">{p.phone}</span>}
                        </span>
                        <DeployBadge state={operatorBadges[p.id]} />
                      </label>
                    ))}
                  </div>
                )}
              </div>

              {/* 사유 */}
              <div>
                <div className="mb-1.5 text-xs font-bold text-slate-700">교체 사유 (선택)</div>
                <textarea
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  rows={2}
                  placeholder="예: 장비 고장으로 대체 투입"
                  className="input w-full"
                />
              </div>

              <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-[11px] text-slate-500">
                같은 현장의 기검증 자원으로 바꾸는 경우 <button type="button" onClick={onOpenChangeRequest} className="font-semibold text-blue-600 underline">업체변경 신청서</button> 로도 처리할 수 있습니다.
              </div>

              {error && <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</div>}
            </div>

            <div className="flex items-center justify-between gap-2 border-t border-slate-200 px-5 py-3">
              <span className="text-[11px] text-slate-400">{canSubmit ? '' : '새 장비 또는 조종원을 선택하세요'}</span>
              <div className="flex gap-2">
                <button type="button" onClick={onClose} className="rounded-md border border-slate-200 px-4 py-1.5 text-sm font-semibold text-slate-600 hover:bg-slate-50">취소</button>
                <button type="button" onClick={() => void execute()} disabled={busy || !canSubmit}
                        className="rounded-md bg-blue-600 px-4 py-1.5 text-sm font-bold text-white hover:bg-blue-700 disabled:opacity-50">
                  {busy ? '교체 중…' : '교체 실행'}
                </button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
