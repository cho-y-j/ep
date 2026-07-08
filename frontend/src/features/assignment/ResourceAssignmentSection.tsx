import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import type { AssignmentResponse } from '../../types/assignment';
import type { SiteResponse } from '../../types/site';

type ResourceKind = 'equipment' | 'person';

type Props = {
  resourceKind: ResourceKind;
  resourceId: number;
  /** 자원의 supplier id — 후보 사이트 필터링에 사용 (ADMIN/BP만 수정 가능). */
  resourceSupplierId: number;
  /** 현재 자원 응답에서 가져온 기존 배치 정보 (UI 즉시 표시용). */
  currentSiteId?: number | null;
  currentSiteName?: string | null;
  /** 배치/해제 후 부모에게 자원 새로고침 트리거. */
  onChanged?: () => void;
};

/**
 * 자원 상세 페이지(장비/인원)에서 공용으로 사용하는 배치 카드.
 * - 현재 배치 사이트 표시
 * - 배치/해제 액션 (ADMIN/BP만)
 * - 배치 이력 리스트
 */
export default function ResourceAssignmentSection({
  resourceKind, resourceId, resourceSupplierId,
  currentSiteId, currentSiteName, onChanged,
}: Props) {
  const { user } = useAuth();
  const [history, setHistory] = useState<AssignmentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [showAssign, setShowAssign] = useState(false);

  // ADMIN 또는 BP가 사이트 소유한 경우만 배치/해제 가능 (서버에서 강제. UI는 ADMIN/BP만 노출).
  const canModify = user?.role === 'ADMIN' || user?.role === 'BP';

  const baseUrl = useMemo(
    () => (resourceKind === 'equipment' ? `/api/equipment/${resourceId}` : `/api/persons/${resourceId}`),
    [resourceKind, resourceId]
  );

  const loadHistory = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<AssignmentResponse[]>(`${baseUrl}/assignments`);
      setHistory(res.data);
    } finally {
      setLoading(false);
    }
  }, [baseUrl]);

  useEffect(() => { void loadHistory(); }, [loadHistory]);

  async function release() {
    if (!confirm('현장 배치를 해제하시겠습니까?')) return;
    setBusy(true);
    try {
      await api.delete(`${baseUrl}/assignment`, { data: { release_reason: '수동 해제' } });
      onChanged?.();
      await loadHistory();
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '해제 실패');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-bold text-slate-900">현장 배치</h2>
        {canModify && !showAssign && (
          <button
            type="button"
            onClick={() => setShowAssign(true)}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-brand-100 bg-white text-sm font-semibold text-brand-700 hover:bg-brand-50"
          >
            <span>+</span> {currentSiteId ? '현장 이동' : '현장 배치'}
          </button>
        )}
      </div>

      {/* 현재 배치 */}
      <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 mb-4">
        {currentSiteId ? (
          <div className="flex items-center justify-between gap-3">
            <div className="min-w-0">
              <div className="text-xs text-slate-500 mb-1">현재 배치 현장</div>
              <Link
                to={`/sites/${currentSiteId}`}
                className="text-base font-bold text-slate-900 hover:text-brand-700 truncate block"
              >
                {currentSiteName ?? `현장 #${currentSiteId}`}
              </Link>
            </div>
            {canModify && (
              <button
                type="button"
                onClick={release}
                disabled={busy}
                className="px-3 py-2 rounded-lg border border-rose-200 bg-white text-sm font-semibold text-rose-600 hover:bg-rose-50 disabled:opacity-50 shrink-0"
              >
                해제
              </button>
            )}
          </div>
        ) : (
          <p className="text-sm text-slate-400">현재 배치된 현장이 없습니다.</p>
        )}
      </div>

      {/* 배치 폼 */}
      {showAssign && canModify && (
        <AssignForm
          resourceKind={resourceKind}
          resourceId={resourceId}
          resourceSupplierId={resourceSupplierId}
          onDone={() => {
            setShowAssign(false);
            onChanged?.();
            void loadHistory();
          }}
          onCancel={() => setShowAssign(false)}
        />
      )}

      {/* 이력 */}
      <div>
        <h3 className="text-sm font-bold text-slate-700 mb-2">배치 이력</h3>
        {loading ? (
          <p className="text-xs text-slate-400">불러오는 중...</p>
        ) : history.length === 0 ? (
          <p className="text-xs text-slate-400">이력이 없습니다.</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {history.map((h) => (
              <li key={h.id} className="py-3 flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <Link
                    to={`/sites/${h.site_id}`}
                    className="text-sm font-semibold text-slate-900 hover:text-brand-700 truncate block"
                  >
                    {h.site_name ?? `현장 #${h.site_id}`}
                  </Link>
                  <div className="text-xs text-slate-500 mt-0.5">
                    {fmtDateTime(h.assigned_at)}
                    {h.released_at ? ` ~ ${fmtDateTime(h.released_at)}` : ' ~ 배치 중'}
                  </div>
                  {h.note && <div className="text-xs text-slate-500 mt-1 truncate">메모: {h.note}</div>}
                  {h.release_reason && (
                    <div className="text-xs text-slate-500 mt-1 truncate">해제 사유: {h.release_reason}</div>
                  )}
                </div>
                <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold shrink-0 ${
                  h.active ? 'bg-blue-100 text-blue-700' : 'bg-slate-100 text-slate-500'
                }`}>
                  {h.active ? '배치 중' : '해제됨'}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function fmtDateTime(s: string) {
  return new Date(s).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' }).slice(0, 16);
}

/** 배치 폼: 자원 supplier가 참여 중인 ACTIVE site 목록을 보여주고 선택. */
function AssignForm({
  resourceKind, resourceId, resourceSupplierId, onDone, onCancel,
}: {
  resourceKind: ResourceKind;
  resourceId: number;
  resourceSupplierId: number;
  onDone: () => void;
  onCancel: () => void;
}) {
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [siteId, setSiteId] = useState<number | ''>('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await api.get<SiteResponse[]>('/api/sites');
        if (cancelled) return;
        // 1) ACTIVE 사이트만
        // 2) 그 사이트 participants 중에 자원의 supplier_id가 ACTIVE 상태로 있는 사이트만
        // BP/ADMIN 모두 list 응답에서 participants가 null이라 사이트별로 fetch가 필요 → list+filter 후 detail.
        const active = res.data.filter((s) => s.status === 'ACTIVE');
        const detailed = await Promise.all(active.map((s) => api.get<SiteResponse>(`/api/sites/${s.id}`)));
        if (cancelled) return;
        const eligible = detailed
          .map((r) => r.data)
          .filter((s) => (s.participants ?? []).some(
            (p) => p.company_id === resourceSupplierId && p.status === 'ACTIVE'
          ));
        setSites(eligible);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [resourceSupplierId]);

  async function submit() {
    if (!siteId) return;
    setBusy(true);
    try {
      const url = resourceKind === 'equipment'
        ? `/api/equipment/${resourceId}/assignment`
        : `/api/persons/${resourceId}/assignment`;
      await tryAssign(url, { site_id: siteId, note: note || null });
      onDone();
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '배치 실패');
    } finally {
      setBusy(false);
    }
  }

  /**
   * 일반 배치 시도 후 DOCUMENTS_BLOCKED 면 ADMIN 한정으로 사유 prompt → override 재시도.
   */
  async function tryAssign(url: string, body: Record<string, unknown>) {
    try {
      await api.post(url, body);
    } catch (err) {
      if (err instanceof AxiosError && err.response?.data?.code === 'DOCUMENTS_BLOCKED') {
        const reason = prompt(`${err.response.data.message}\n\nADMIN 만 강제 진행 가능. 사유:`);
        if (!reason || !reason.trim()) throw err;
        await api.post(url, { ...body, override: true, override_reason: reason.trim() });
        return;
      }
      throw err;
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 mb-4 space-y-3">
      <div>
        <label className="text-xs font-medium text-slate-600">현장 선택</label>
        {loading ? (
          <p className="text-xs text-slate-400 mt-1">불러오는 중...</p>
        ) : sites.length === 0 ? (
          <p className="text-xs text-rose-600 mt-1">
            이 자원의 공급사가 참여 중인 ACTIVE 현장이 없습니다.<br />
            먼저 현장의 참여업체에 공급사를 추가하세요.
          </p>
        ) : (
          <select
            value={siteId}
            onChange={(e) => setSiteId(e.target.value ? Number(e.target.value) : '')}
            className="mt-1 w-full px-3 py-2 rounded-lg border border-slate-200 text-sm"
          >
            <option value="">현장 선택...</option>
            {sites.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}{s.code ? ` (${s.code})` : ''}
              </option>
            ))}
          </select>
        )}
      </div>
      <div>
        <label className="text-xs font-medium text-slate-600">메모 (선택)</label>
        <input
          type="text"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          className="mt-1 w-full px-3 py-2 rounded-lg border border-slate-200 text-sm"
          maxLength={255}
          placeholder="배치 메모"
        />
      </div>
      <div className="flex justify-end gap-2">
        <button
          type="button"
          onClick={onCancel}
          className="px-3 py-2 rounded-lg text-sm text-slate-600 hover:bg-slate-100"
        >
          취소
        </button>
        <button
          type="button"
          onClick={submit}
          disabled={busy || !siteId}
          className="px-4 py-2 rounded-lg bg-brand-600 text-white text-sm font-semibold hover:bg-brand-700 disabled:opacity-50"
        >
          {busy ? '배치 중...' : '배치'}
        </button>
      </div>
    </div>
  );
}
