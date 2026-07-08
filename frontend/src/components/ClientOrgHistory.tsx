import { useEffect, useState } from 'react';
import { api } from '../lib/api';

type ResourceType = 'equipment' | 'person';

interface HistoryRow {
  id: number;
  client_org_id: number;
  client_org_name: string;
  period_start: string;
  period_end: string | null;
  source: 'ADMIN' | 'WORK_PLAN';
}

interface Props {
  resourceType: ResourceType;
  resourceId: number;
  /** ADMIN 수동 등록 폼 활성 여부. equipment/person 상세 페이지에서 ADMIN 일 때만 true. */
  adminMode?: boolean;
}

interface ClientOrg { id: number; name: string; code: string; active: boolean; }
interface Draft { clientOrgId: string; periodStart: string; periodEnd: string; }
const EMPTY: Draft = { clientOrgId: '', periodStart: '', periodEnd: '' };

/** 자원 카드/상세 페이지의 ClientOrg 경험 chip + 기간 이력 펼침. */
export default function ClientOrgHistory({ resourceType, resourceId, adminMode = false }: Props) {
  const [rows, setRows] = useState<HistoryRow[]>([]);
  const [expanded, setExpanded] = useState(false);
  const [adding, setAdding] = useState(false);
  const [orgs, setOrgs] = useState<ClientOrg[]>([]);
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    const url = `/api/client-org-history/${resourceType}/${resourceId}`;
    try {
      const res = await api.get<HistoryRow[]>(url);
      setRows(res.data);
    } catch { /* 비공개 자원이면 무시 */ }
  }
  useEffect(() => { void load(); }, [resourceType, resourceId]);

  async function loadOrgs() {
    if (orgs.length > 0) return;
    const res = await api.get<ClientOrg[]>('/api/client-orgs');
    setOrgs(res.data);
  }
  async function startAdd() {
    setAdding(true);
    setDraft(EMPTY);
    setError(null);
    await loadOrgs();
  }

  async function save() {
    setError(null);
    if (!draft.clientOrgId || !draft.periodStart) {
      setError('원청기관과 시작일은 필수입니다');
      return;
    }
    try {
      await api.post(`/api/client-org-history/${resourceType}/${resourceId}`, {
        client_org_id: Number(draft.clientOrgId),
        period_start: draft.periodStart,
        period_end: draft.periodEnd || null,
      });
      setAdding(false);
      void load();
    } catch (e: any) {
      setError(e?.response?.data?.message || '추가 실패');
    }
  }

  async function remove(historyId: number) {
    if (!confirm('이력을 삭제하시겠습니까?')) return;
    await api.delete(`/api/client-org-history/${resourceType}-history/${historyId}`);
    void load();
  }

  // chip 표시용: ClientOrg 별로 그룹
  const grouped = new Map<number, { name: string; rows: HistoryRow[] }>();
  for (const r of rows) {
    const g = grouped.get(r.client_org_id) ?? { name: r.client_org_name, rows: [] };
    g.rows.push(r);
    grouped.set(r.client_org_id, g);
  }
  const groups = [...grouped.entries()];

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2 flex-wrap">
        {groups.length === 0
          ? <span className="text-xs text-slate-400">경험 이력 없음</span>
          : groups.map(([orgId, g]) => (
              <span key={orgId}
                    className="inline-flex items-center text-xs px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-200">
                {g.name}{g.rows.length > 1 && <span className="ml-1 text-indigo-500">({g.rows.length})</span>}
              </span>
            ))}
        {groups.length > 0 && (
          <button onClick={() => setExpanded(!expanded)}
                  className="text-xs text-slate-500 hover:text-slate-700 underline">
            {expanded ? '접기' : '기간 보기'}
          </button>
        )}
        {adminMode && !adding && (
          <button onClick={startAdd} className="text-xs text-emerald-600 hover:text-emerald-700">
            + 이력 추가
          </button>
        )}
      </div>

      {expanded && groups.length > 0 && (
        <div className="text-xs space-y-2 p-3 border border-slate-200 rounded bg-slate-50">
          {groups.map(([orgId, g]) => (
            <div key={orgId}>
              <div className="font-semibold text-slate-700">{g.name}</div>
              <ul className="ml-3 mt-1 space-y-0.5">
                {g.rows.map((r) => (
                  <li key={r.id} className="text-slate-600 flex items-center gap-2">
                    <span>
                      {r.period_start}{r.period_end ? ` ~ ${r.period_end}` : ' ~ (진행/미상)'}
                    </span>
                    <span className={`text-[10px] px-1 rounded ${
                      r.source === 'ADMIN' ? 'bg-slate-200 text-slate-600' : 'bg-emerald-100 text-emerald-700'
                    }`}>
                      {r.source === 'ADMIN' ? '관리자 등록' : '작업이력'}
                    </span>
                    {adminMode && (
                      <button onClick={() => remove(r.id)}
                              className="text-[10px] text-rose-500 hover:text-rose-700 ml-auto">삭제</button>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}

      {adding && (
        <div className="p-3 border border-slate-300 rounded bg-white space-y-2">
          <div className="text-xs font-semibold text-slate-700">새 이력 추가</div>
          <div className="grid grid-cols-3 gap-2">
            <select className="input text-xs" value={draft.clientOrgId}
                    onChange={(e) => setDraft({ ...draft, clientOrgId: e.target.value })}>
              <option value="">원청기관 선택</option>
              {orgs.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
            </select>
            <input type="date" className="input text-xs" value={draft.periodStart}
                   onChange={(e) => setDraft({ ...draft, periodStart: e.target.value })} />
            <input type="date" className="input text-xs" placeholder="종료일(옵션)" value={draft.periodEnd}
                   onChange={(e) => setDraft({ ...draft, periodEnd: e.target.value })} />
          </div>
          {error && <div className="text-xs text-rose-600">{error}</div>}
          <div className="flex justify-end gap-2">
            <button onClick={() => setAdding(false)} className="text-xs text-slate-500">취소</button>
            <button onClick={save} className="text-xs btn-primary px-2 py-1">저장</button>
          </div>
        </div>
      )}
    </div>
  );
}
