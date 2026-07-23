import { useEffect, useState } from 'react';
import { api } from '../../lib/api';

export type DeployBlock = { kind: 'DOCUMENT' | 'CHECK' | 'SAFETY' | 'COMPLIANCE' | string; label: string; detail?: string | null };
export type DeployCheckResult = { ready: boolean; blocks: DeployBlock[] };

/** R1 조합(차량+조종원) 판정 — GET /api/resources/equipment/{id}/deploy-check-combo. */
export type ComboOperatorCheck = { person_id: number; person_name: string; priority: number; ready: boolean; blocks: DeployBlock[] };
export type ComboDeployCheckResult = {
  equipment_id: number;
  equipment_label: string;
  combo_ready: boolean;
  equipment: DeployCheckResult;
  operators: ComboOperatorCheck[];
};

type Site = { id: number; name: string };

const KIND_LABEL: Record<string, string> = {
  DOCUMENT: '서류',
  CHECK: '반입검사·검진·교육',
  SAFETY: '안전점검',
  COMPLIANCE: '이행지시',
};

/**
 * L3 현장 투입가능 사전판정 카드 — 자원 상세(장비·인원)에 노출.
 * 현장을 선택하면 그 현장 기준(안전점검 현장 한정)으로 판정, 미지정이면 자원 전체 기준.
 */
export default function DeployCheckCard({ ownerType, ownerId }: { ownerType: 'equipment' | 'person'; ownerId: number }) {
  const [sites, setSites] = useState<Site[]>([]);
  const [siteId, setSiteId] = useState<number | ''>('');
  const [result, setResult] = useState<DeployCheckResult | null>(null);
  const [combo, setCombo] = useState<ComboDeployCheckResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.get<Site[]>('/api/sites')
      .then((r) => setSites((r.data ?? []).map((s) => ({ id: s.id, name: s.name }))))
      .catch(() => setSites([]));
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    const params = siteId === '' ? {} : { siteId };
    // 장비는 R1 조합 판정(장비 판정 포함)으로 — 장비 판정 값은 단건 API 와 동일(같은 4게이트 산출).
    const req = ownerType === 'equipment'
      ? api.get<ComboDeployCheckResult>(`/api/resources/equipment/${ownerId}/deploy-check-combo`, { params })
          .then((r) => { if (!cancelled) { setCombo(r.data); setResult(r.data.equipment); } })
      : api.get<DeployCheckResult>(`/api/resources/${ownerType}/${ownerId}/deploy-check`, { params })
          .then((r) => { if (!cancelled) { setCombo(null); setResult(r.data); } });
    req
      .catch((e) => { if (!cancelled) { setResult(null); setCombo(null); setError(e?.response?.data?.message || '판정을 불러올 수 없습니다'); } })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [ownerType, ownerId, siteId]);

  const operators = combo?.operators ?? [];

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-base font-bold text-slate-900">현장 투입가능 판정</h3>
            {!loading && operators.length > 0 && (
              <span className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${
                combo?.combo_ready
                  ? 'bg-emerald-100 text-emerald-700'
                  : 'bg-amber-100 text-amber-700'}`}>
                {combo?.combo_ready ? '조합 투입 준비' : '조합 미준비'}
              </span>
            )}
          </div>
          <p className="mt-0.5 text-xs text-slate-500">
            기준: 필수서류 전건 검증완료·유효기한 내
            {ownerType === 'equipment' ? ' · 반입검사(차량 안전점검) 승인' : ' · 건강검진·안전교육 승인'}
            {' '}· 안전점검 완료 · 미해결 이행지시 없음
            {operators.length > 0 && ' — 조합(교대조) 조종원 전원 통과 시 조합 투입 준비'}
          </p>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <span className="shrink-0 text-xs font-semibold text-slate-500">현장</span>
          <select
            value={siteId}
            onChange={(e) => setSiteId(e.target.value ? Number(e.target.value) : '')}
            className="input w-full min-w-0 sm:w-56"
          >
            <option value="">현장 미지정 (자원 전체 기준)</option>
            {sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </label>
      </div>

      <div className="mt-4">
        {loading ? (
          <div className="text-sm text-slate-400">판정 중…</div>
        ) : error ? (
          <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-500">{error}</div>
        ) : !result ? null : result.ready ? (
          <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2.5">
            <span className="text-emerald-600">✓</span>
            <span className="text-sm font-semibold text-emerald-800">
              {siteId === '' ? '투입 가능 (기본 판정 통과)' : '이 현장에 바로 투입 가능'}
            </span>
          </div>
        ) : (
          <div className="space-y-2">
            <div className="text-sm font-semibold text-amber-700">부족 항목 {result.blocks.length}건 — 아래를 해결해야 투입 가능</div>
            <ul className="space-y-1.5">
              {result.blocks.map((b, i) => (
                <li key={i} className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50/60 px-3 py-2">
                  <span className="mt-0.5 shrink-0 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-bold text-amber-800">
                    {KIND_LABEL[b.kind] ?? b.kind}
                  </span>
                  <span className="min-w-0 text-sm text-slate-700">
                    {b.label}
                    {b.detail ? <span className="ml-1 text-xs text-slate-500">· {b.detail}</span> : null}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* R1 조합(교대조) 조종원별 판정 — 장비 대상일 때만. 조종원 0명이면 장비 단독 판정(섹션 없음). */}
        {!loading && !error && operators.length > 0 && (
          <div className="mt-4 border-t border-slate-100 pt-3">
            <div className="text-xs font-semibold text-slate-500">조합(교대조) 조종원 판정</div>
            <ul className="mt-2 space-y-1.5">
              {operators.map((op) => (
                <li key={op.person_id}
                  className={`rounded-lg border px-3 py-2 ${
                    op.ready ? 'border-emerald-200 bg-emerald-50/50' : 'border-amber-200 bg-amber-50/60'}`}>
                  <div className="flex items-center gap-2">
                    <span className="shrink-0 rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-bold text-slate-600">
                      {op.priority}순위
                    </span>
                    <span className="min-w-0 truncate text-sm font-medium text-slate-800">{op.person_name}</span>
                    <span className={`ml-auto shrink-0 text-xs font-semibold ${
                      op.ready ? 'text-emerald-700' : 'text-amber-700'}`}>
                      {op.ready ? '통과 ✓' : `차단 ${op.blocks.length}건`}
                    </span>
                  </div>
                  {!op.ready && (
                    <ul className="mt-1.5 space-y-1">
                      {op.blocks.map((b, i) => (
                        <li key={i} className="flex items-start gap-2 text-xs text-slate-600">
                          <span className="mt-0.5 shrink-0 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-bold text-amber-800">
                            {KIND_LABEL[b.kind] ?? b.kind}
                          </span>
                          <span className="min-w-0">
                            {b.label}
                            {b.detail ? <span className="ml-1 text-slate-400">· {b.detail}</span> : null}
                          </span>
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}
