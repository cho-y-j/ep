import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import AppShell from '../../components/layout/AppShell';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import type { SiteResponse } from '../../types/site';
import type { ResourceCompliance, SiteCompliance, SupplementResponse } from '../../types/compliance';
import { SUPPLEMENT_STATUS_LABEL } from '../../types/compliance';
import type { OwnerType } from '../../types/document';
import QuickAddResourceDialog from './QuickAddResourceDialog';
import DocFilePreviewDialog from './DocFilePreviewDialog';
import OcrUploadDialog from '../document/OcrUploadDialog';
import type { DocumentTypeResponse } from '../../types/document';
import { formatOwnerSubLabel } from '../../lib/format';

/**
 * S-11: 작업계획서 작성 직전 단계의 "서류관리" 통합 dashboard.
 *
 * 흐름:
 *   1. 사이트 선택 → 백엔드 /api/sites/{id}/compliance 호출
 *   2. BP 회사 / 장비 / 인원 자원별 컴플라이언스 카드 표시
 *   3. 빠진/만료/REJECTED 서류 옆 [보완 요청] → 다이얼로그 → 발송
 *   4. 모든 ✓ 시 [작업계획서 만들기] CTA 활성
 */
export default function DocumentManagementPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const isBP = user?.role === 'BP';
  const isSupplier = user?.role === 'EQUIPMENT_SUPPLIER' || user?.role === 'MANPOWER_SUPPLIER';

  const [tab, setTab] = useState<'board' | 'sent' | 'received' | 'myDocs'>(isSupplier ? 'myDocs' : 'board');
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [siteId, setSiteId] = useState<number | null>(null);
  const [comp, setComp] = useState<SiteCompliance | null>(null);
  const [loading, setLoading] = useState(false);
  const [supplements, setSupplements] = useState<SupplementResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [supDialog, setSupDialog] = useState<SupplementDialogState | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    api.get<SiteResponse[]>('/api/sites')
      .then((res) => setSites(res.data))
      .catch(() => {});
    api.get<SupplementResponse[]>('/api/document-supplements')
      .then((res) => setSupplements(res.data))
      .catch(() => {});
  }, [reloadKey]);

  useEffect(() => {
    if (!siteId) {
      setComp(null);
      return;
    }
    setLoading(true);
    api.get<SiteCompliance>(`/api/sites/${siteId}/compliance`)
      .then((res) => setComp(res.data))
      .catch((err) => setError(err?.response?.data?.message ?? '컴플라이언스 로드 실패'))
      .finally(() => setLoading(false));
  }, [siteId, reloadKey]);

  const sentSupplements = useMemo(
    () => supplements.filter((s) => isAdmin || s.requester_user_id === user?.id),
    [supplements, isAdmin, user?.id]
  );
  const receivedSupplements = useMemo(
    () => supplements.filter((s) => s.target_supplier_company_id === user?.company_id),
    [supplements, user?.company_id]
  );

  return (
    <AppShell breadcrumb={[{ label: '서류관리' }]}>
      <div className="mx-auto max-w-7xl space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-950">서류관리</h1>
          <p className="mt-1 text-sm text-slate-500">
            작업계획서 작성 전 단계 — 현장별 자원 서류 컴플라이언스 점검 + 공급사에 보완 요청 발송.
            서류 100% 갖춰지면 작업계획서를 만들 수 있습니다.
          </p>
        </div>

        <div className="border-b border-slate-200 flex gap-1">
          {!isSupplier && (
            <TabButton active={tab === 'board'} onClick={() => setTab('board')}>
              현장별 보드
            </TabButton>
          )}
          {!isSupplier && (
            <TabButton active={tab === 'sent'} onClick={() => setTab('sent')}>
              보낸 보완 요청 ({sentSupplements.length})
            </TabButton>
          )}
          {(isSupplier || isAdmin) && (
            <TabButton active={tab === 'received'} onClick={() => setTab('received')}>
              받은 보완 요청 ({receivedSupplements.length})
            </TabButton>
          )}
          {isSupplier && (
            <TabButton active={tab === 'myDocs'} onClick={() => setTab('myDocs')}>
              내 서류 만료 관리
            </TabButton>
          )}
        </div>

        {error && (
          <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-600">{error}</p>
        )}

        {tab === 'board' && (
          <BoardTab
            sites={sites}
            siteId={siteId}
            setSiteId={setSiteId}
            comp={comp}
            loading={loading}
            isAdmin={isAdmin}
            isBP={isBP}
            onRequestSupplement={(state) => setSupDialog(state)}
            onCreateWorkPlan={() => {
              const site = sites.find((s) => s.id === siteId);
              const params = new URLSearchParams();
              if (siteId) params.set('siteId', String(siteId));
              if (site?.name) params.set('title', `${site.name} 작업계획`);
              const url = `/work-plans/new${params.toString() ? '?' + params.toString() : ''}`;
              window.open(url, '_blank', 'noopener');
            }}
          />
        )}

        {tab === 'sent' && (
          <SupplementListTable
            rows={sentSupplements}
            emptyMsg="보낸 보완 요청이 없습니다."
            onCancel={async (id) => {
              await api.post(`/api/document-supplements/${id}/cancel`, {});
              setReloadKey((k) => k + 1);
            }}
            canCancel={(s) => s.status === 'OPEN' && (isAdmin || s.requester_user_id === user?.id)}
          />
        )}

        {tab === 'received' && (
          <SupplementListTable
            rows={receivedSupplements}
            emptyMsg="받은 보완 요청이 없습니다."
            onCancel={null}
            canCancel={() => false}
            showFix
          />
        )}

        {tab === 'myDocs' && <MyDocsExpiryView />}

        {supDialog && (
          <SupplementDialog
            state={supDialog}
            onClose={() => setSupDialog(null)}
            onSubmitted={() => {
              setSupDialog(null);
              setReloadKey((k) => k + 1);
            }}
          />
        )}
      </div>
    </AppShell>
  );
}

type DocRow = {
  id: number;
  document_type_id: number;
  document_type_name?: string | null;
  owner_type: 'EQUIPMENT' | 'PERSON' | 'COMPANY';
  owner_id: number;
  owner_name?: string | null;
  owner_sub_label?: string | null;
  owner_assignment_status?: string | null;
  owner_external?: boolean;
  owner_business_name?: string | null;
  file_name?: string | null;
  expiry_date?: string | null;
  verification_status?: string | null;
  extracted_data?: string | null;
  rejected_reason?: string | null;
};

/** 자원이 현재 현장 투입 중인지: 장비 ASSIGNED / 인원 ON_DUTY. */
function isDeployed(ownerType: DocRow['owner_type'], status: string | null | undefined): boolean {
  if (ownerType === 'EQUIPMENT') return status === 'ASSIGNED';
  if (ownerType === 'PERSON') return status === 'ON_DUTY';
  return false;
}

/** BoardTab 자원 카드 제목 — sub label(종류/역할)을 한국어로 매핑해 합친다. */
function ownerCardTitle(r: ResourceCompliance): string {
  const sl = formatOwnerSubLabel(r.owner_type, r.owner_sub_label);
  return `${r.owner_name}${sl ? ` · ${sl}` : ''}`;
}

function MyDocsExpiryView() {
  const { company } = useAuth();
  const [rows, setRows] = useState<DocRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<'all' | 'expired' | 'soon' | 'valid'>('all');
  const [ownerFilter, setOwnerFilter] = useState<'all' | 'EQUIPMENT' | 'PERSON' | 'COMPANY'>('all');
  const [sourcingFilter, setSourcingFilter] = useState<'all' | 'internal' | 'external'>('all');
  const [q, setQ] = useState('');
  const [addDialog, setAddDialog] = useState<null | 'EQUIPMENT' | 'PERSON'>(null);
  type GroupSortKey = 'type' | 'name' | 'docs' | 'status';
  const [sortKey, setSortKey] = useState<GroupSortKey | null>(null);
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc');
  const [previewDoc, setPreviewDoc] = useState<DocRow | null>(null);
  const [reuploadDoc, setReuploadDoc] = useState<DocRow | null>(null);
  const [allTypes, setAllTypes] = useState<DocumentTypeResponse[]>([]);
  const [verifyableMap, setVerifyableMap] = useState<Map<number, boolean>>(new Map());
  const [reloadKey, setReloadKey] = useState(0);
  const [globalReverifyBusy, setGlobalReverifyBusy] = useState(false);
  const canAddEquipment = company?.type === 'EQUIPMENT';
  const canAddPerson = company?.type === 'EQUIPMENT' || company?.type === 'MANPOWER';

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    api.get<DocRow[]>('/api/documents/my-supplier')
      .then((r) => { if (!cancelled) setRows(r.data); })
      .catch(() => { if (!cancelled) setRows([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [reloadKey]);

  useEffect(() => {
    Promise.all([
      api.get<DocumentTypeResponse[]>('/api/document-types', { params: { appliesTo: 'PERSON' } }),
      api.get<DocumentTypeResponse[]>('/api/document-types', { params: { appliesTo: 'EQUIPMENT' } }),
      api.get<DocumentTypeResponse[]>('/api/document-types', { params: { appliesTo: 'COMPANY' } }),
    ]).then(([p, e, c]) => {
      const all = [...p.data, ...e.data, ...c.data];
      setAllTypes(all);
      const m = new Map<number, boolean>();
      all.forEach((t) => m.set(t.id, !!t.verify_endpoint));
      setVerifyableMap(m);
    }).catch(() => {});
  }, []);

  async function reverifyAllVisible() {
    if (globalReverifyBusy) return;
    const ids = filtered.filter((r) => verifyableMap.get(r.document_type_id) === true).map((r) => r.id);
    if (ids.length === 0) {
      alert('자동 검증 가능한 서류가 없습니다.');
      return;
    }
    if (!window.confirm(`현재 보이는 ${ids.length}건의 서류를 모두 재검증합니다. 진행할까요?`)) return;
    setGlobalReverifyBusy(true);
    try {
      await Promise.allSettled(ids.map((id) => api.post(`/api/documents/${id}/verify`, { user_inputs: {} })));
      setReloadKey((k) => k + 1);
    } finally {
      setGlobalReverifyBusy(false);
    }
  }

  const today = new Date(); today.setHours(0, 0, 0, 0);
  const daysFrom = (s: string | null | undefined) => {
    if (!s) return Infinity;
    const d = new Date(s);
    return Math.floor((d.getTime() - today.getTime()) / 86400000);
  };

  const stats = {
    expired: rows.filter((r) => r.expiry_date && daysFrom(r.expiry_date) < 0).length,
    soon: rows.filter((r) => r.expiry_date && daysFrom(r.expiry_date) >= 0 && daysFrom(r.expiry_date) <= 60).length,
    valid: rows.filter((r) => !r.expiry_date || daysFrom(r.expiry_date) > 60).length,
  };

  const qLower = q.trim().toLowerCase();
  const filtered = rows.filter((r) => {
    if (ownerFilter !== 'all' && r.owner_type !== ownerFilter) return false;
    if (sourcingFilter === 'external' && r.owner_external !== true) return false;
    if (sourcingFilter === 'internal' && r.owner_external === true) return false;
    if (filter !== 'all') {
      const d = daysFrom(r.expiry_date);
      if (filter === 'expired' && !(r.expiry_date && d < 0)) return false;
      if (filter === 'soon' && !(r.expiry_date && d >= 0 && d <= 60)) return false;
      if (filter === 'valid' && !(!r.expiry_date || d > 60)) return false;
    }
    if (qLower) {
      const hay = `${r.owner_name ?? ''} ${r.document_type_name ?? ''}`.toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    return true;
  }).sort((a, b) => daysFrom(a.expiry_date) - daysFrom(b.expiry_date));

  // 자원별 그룹핑 + 만료/임박 많은 그룹 우선 정렬
  const grouped = (() => {
    const groups = new Map<string, { owner_type: DocRow['owner_type']; owner_id: number; owner_name: string | null; owner_sub_label: string | null; owner_assignment_status: string | null; owner_external: boolean; owner_business_name: string | null; rows: DocRow[] }>();
    for (const r of filtered) {
      const k = `${r.owner_type}:${r.owner_id}`;
      if (!groups.has(k)) {
        groups.set(k, { owner_type: r.owner_type, owner_id: r.owner_id, owner_name: r.owner_name ?? null, owner_sub_label: r.owner_sub_label ?? null, owner_assignment_status: r.owner_assignment_status ?? null, owner_external: !!r.owner_external, owner_business_name: r.owner_business_name ?? null, rows: [] });
      }
      groups.get(k)!.rows.push(r);
    }
    const arr = Array.from(groups.values());
    const score = (rs: DocRow[]) => {
      let expired = 0, soon = 0, minDays = Infinity;
      for (const r of rs) {
        if (!r.expiry_date) continue;
        const d = daysFrom(r.expiry_date);
        if (d < 0) expired++;
        else if (d <= 60) soon++;
        if (d < minDays) minDays = d;
      }
      return { expired, soon, minDays };
    };
    arr.sort((a, b) => {
      const sa = score(a.rows), sb = score(b.rows);
      if (sa.expired !== sb.expired) return sb.expired - sa.expired;
      if (sa.soon !== sb.soon) return sb.soon - sa.soon;
      return sa.minDays - sb.minDays;
    });
    return arr;
  })();

  // 헤더 클릭 정렬 — 기본(null)은 만료/임박 우선의 스마트 정렬 유지
  const typeLabelOf = (g: { owner_type: DocRow['owner_type']; owner_sub_label: string | null }) =>
    g.owner_type === 'COMPANY' ? '회사'
      : formatOwnerSubLabel(g.owner_type, g.owner_sub_label) ?? (g.owner_type === 'EQUIPMENT' ? '장비' : '인원');

  const severityOf = (rs: DocRow[]) => {
    let expired = 0, rejected = 0, soon = 0, needVerify = 0;
    for (const r of rs) {
      const d = daysFrom(r.expiry_date);
      if (r.expiry_date && d < 0) expired++;
      else if (r.expiry_date && d >= 0 && d <= 60) soon++;
      if (r.verification_status === 'REJECTED') rejected++;
      else if (verifyableMap.get(r.document_type_id) === true && r.verification_status !== 'VERIFIED') needVerify++;
    }
    if (expired + rejected > 0) return 4;
    if (soon > 0) return 3;
    if (needVerify > 0) return 2;
    return 1;
  };

  const sortedGroups = (() => {
    if (!sortKey) return grouped;
    const arr = [...grouped];
    arr.sort((a, b) => {
      let cmp = 0;
      if (sortKey === 'type') cmp = typeLabelOf(a).localeCompare(typeLabelOf(b), 'ko');
      else if (sortKey === 'name') cmp = (a.owner_name ?? '').localeCompare(b.owner_name ?? '', 'ko');
      else if (sortKey === 'docs') cmp = a.rows.length - b.rows.length;
      else cmp = severityOf(a.rows) - severityOf(b.rows);
      return sortDir === 'asc' ? cmp : -cmp;
    });
    return arr;
  })();

  const toggleSort = (key: GroupSortKey) => {
    if (sortKey !== key) { setSortKey(key); setSortDir('asc'); return; }
    if (sortDir === 'asc') { setSortDir('desc'); return; }
    setSortKey(null);
  };

  const SortHead = ({ k, label }: { k: GroupSortKey; label: string }) => {
    const active = sortKey === k;
    const arrow = !active ? '↕' : sortDir === 'asc' ? '↑' : '↓';
    return (
      <button type="button" onClick={() => toggleSort(k)}
        className={`inline-flex items-center gap-1 font-semibold ${active ? 'text-slate-900' : 'text-slate-500 hover:text-slate-700'}`}>
        <span>{label}</span>
        <span className={`text-[10px] ${active ? 'text-brand-600' : 'text-slate-300'}`}>{arrow}</span>
      </button>
    );
  };

  if (loading) return <div className="text-sm text-slate-400">불러오는 중…</div>;

  return (
    <div className="space-y-4">
      {addDialog && (
        <QuickAddResourceDialog
          kind={addDialog}
          selfSupplierType={company?.type}
          onClose={() => setAddDialog(null)}
        />
      )}

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <button onClick={() => setFilter(filter === 'expired' ? 'all' : 'expired')}
                className={`card flex items-center gap-4 p-4 text-left transition ${filter === 'expired' ? 'ring-2 ring-rose-400' : 'hover:shadow-md'}`}>
          <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-rose-50 text-rose-500">
            <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" /></svg>
          </span>
          <span>
            <span className="block text-sm font-medium text-slate-600">만료</span>
            <span className="block text-3xl font-bold text-rose-600 tabular-nums">{stats.expired}</span>
          </span>
        </button>
        <button onClick={() => setFilter(filter === 'soon' ? 'all' : 'soon')}
                className={`card flex items-center gap-4 p-4 text-left transition ${filter === 'soon' ? 'ring-2 ring-amber-400' : 'hover:shadow-md'}`}>
          <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-amber-50 text-amber-500">
            <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" /></svg>
          </span>
          <span>
            <span className="block text-sm font-medium text-slate-600">임박 <span className="text-xs text-slate-400">(30/60일 이내)</span></span>
            <span className="block text-3xl font-bold text-amber-600 tabular-nums">{stats.soon}</span>
          </span>
        </button>
        <button onClick={() => setFilter(filter === 'valid' ? 'all' : 'valid')}
                className={`card flex items-center gap-4 p-4 text-left transition ${filter === 'valid' ? 'ring-2 ring-emerald-400' : 'hover:shadow-md'}`}>
          <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-emerald-50 text-emerald-500">
            <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /><polyline points="9 12 11 14 15 10" /></svg>
          </span>
          <span>
            <span className="block text-sm font-medium text-slate-600">정상</span>
            <span className="block text-3xl font-bold text-emerald-600 tabular-nums">{stats.valid}</span>
          </span>
        </button>
      </div>

      {/* 인력/차량 구분 탭 */}
      <div className="inline-flex rounded-lg border border-slate-300 overflow-hidden">
        {([['all', '전체'], ['EQUIPMENT', '장비'], ['PERSON', '인원'], ['COMPANY', '회사']] as const).map(([v, label]) => (
          <button key={v} type="button" onClick={() => setOwnerFilter(v)}
                  className={`px-4 py-2 text-sm font-semibold border-r border-slate-200 last:border-r-0 transition ${
                    ownerFilter === v ? 'bg-brand-600 text-white' : 'bg-white text-slate-700 hover:bg-slate-50'
                  }`}>
            {label}
          </button>
        ))}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <select value={filter} onChange={(e) => setFilter(e.target.value as typeof filter)}
                className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700">
          <option value="all">상태 전체</option>
          <option value="expired">만료</option>
          <option value="soon">임박</option>
          <option value="valid">정상</option>
        </select>
        <select value={sourcingFilter} onChange={(e) => setSourcingFilter(e.target.value as typeof sourcingFilter)}
                className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700">
          <option value="all">사업자 전체</option>
          <option value="internal">우리 장비</option>
          <option value="external">외부 조달</option>
        </select>
        <div className="relative flex-1 min-w-[220px]">
          <input
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="자원 이름 또는 서류 종류 검색"
            className="w-full rounded-lg border border-slate-300 bg-white pl-9 pr-3 py-2 text-sm"
          />
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" /></svg>
        </div>
        <button onClick={reverifyAllVisible} disabled={globalReverifyBusy}
                className="px-4 py-2 text-sm font-semibold rounded-lg bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50">
          {globalReverifyBusy ? '검증 중…' : '전체 재검증'}
        </button>
        {canAddEquipment && (
          <button onClick={() => setAddDialog('EQUIPMENT')}
                  className="px-4 py-2 text-sm font-semibold rounded-lg border border-blue-300 bg-white text-blue-700 hover:bg-blue-50">
            + 장비 추가
          </button>
        )}
        {canAddPerson && (
          <button onClick={() => setAddDialog('PERSON')}
                  className="px-4 py-2 text-sm font-semibold rounded-lg border border-blue-300 bg-white text-blue-700 hover:bg-blue-50">
            + 인력 추가
          </button>
        )}
      </div>

      {grouped.length === 0 ? (
        <div className="card p-8 text-center text-sm text-slate-400">해당 조건의 서류가 없습니다.</div>
      ) : (
        <div className="card p-0 overflow-hidden">
          <div className="flex items-center gap-3 px-4 sm:px-5 py-2.5 bg-slate-50 border-b border-slate-200 text-xs">
            <span className="w-28 shrink-0"><SortHead k="type" label="종류" /></span>
            <span className="w-24 shrink-0 font-semibold text-slate-500">사업자</span>
            <span className="flex-1 min-w-0"><SortHead k="name" label="이름" /></span>
            <div className="flex items-center gap-3 shrink-0">
              <span className="w-20 flex justify-end"><SortHead k="docs" label="서류" /></span>
              <span className="w-28 flex justify-center"><SortHead k="status" label="상태" /></span>
              <span className="w-14" />
            </div>
          </div>
          <div className="divide-y divide-slate-100">
            {sortedGroups.map((g) => (
              <ResourceGroupCard
                key={`${g.owner_type}:${g.owner_id}`}
                group={g}
                daysFrom={daysFrom}
                verifyableMap={verifyableMap}
                onRowClick={(r) => setPreviewDoc(r)}
                onReverified={() => setReloadKey((k) => k + 1)}
              />
            ))}
          </div>
        </div>
      )}

      {previewDoc && (
        <DocFilePreviewDialog
          doc={previewDoc}
          docType={allTypes.find((t) => t.id === previewDoc.document_type_id)}
          canReverify={verifyableMap.get(previewDoc.document_type_id) === true}
          onClose={() => setPreviewDoc(null)}
          onReverified={() => setReloadKey((k) => k + 1)}
          onReupload={() => { const d = previewDoc; setPreviewDoc(null); setReuploadDoc(d); }}
        />
      )}

      {reuploadDoc && (
        <OcrUploadDialog
          open={true}
          ownerType={reuploadDoc.owner_type}
          ownerId={reuploadDoc.owner_id}
          types={allTypes}
          presetTypeId={reuploadDoc.document_type_id}
          title={`재업로드 — ${reuploadDoc.document_type_name ?? ''}`}
          onClose={() => setReuploadDoc(null)}
          onUploaded={() => { setReuploadDoc(null); setReloadKey((k) => k + 1); }}
        />
      )}
    </div>
  );
}

function ResourceGroupCard({ group, daysFrom, verifyableMap, onRowClick, onReverified }: {
  group: { owner_type: DocRow['owner_type']; owner_id: number; owner_name: string | null; owner_sub_label: string | null; owner_assignment_status: string | null; owner_external: boolean; owner_business_name: string | null; rows: DocRow[] };
  daysFrom: (s: string | null | undefined) => number;
  verifyableMap: Map<number, boolean>;
  onRowClick: (r: DocRow) => void;
  onReverified: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [reverifyBusy, setReverifyBusy] = useState(false);

  const verifyableRows = group.rows.filter((r) => verifyableMap.get(r.document_type_id) === true);

  async function reverifyAll() {
    if (!verifyableRows.length || reverifyBusy) return;
    if (!window.confirm(`이 자원의 자동 검증 가능한 ${verifyableRows.length}건을 모두 재검증합니다. 진행할까요?`)) return;
    setReverifyBusy(true);
    try {
      await Promise.allSettled(
        verifyableRows.map((r) => api.post(`/api/documents/${r.id}/verify`, { user_inputs: {} }))
      );
      onReverified();
    } finally {
      setReverifyBusy(false);
    }
  }
  const href = group.owner_type === 'EQUIPMENT' ? `/equipment/${group.owner_id}`
    : group.owner_type === 'PERSON' ? `/persons/${group.owner_id}`
    : '/my-company';
  const subLabel = formatOwnerSubLabel(group.owner_type, group.owner_sub_label);
  const typeLabel = group.owner_type === 'EQUIPMENT'
    ? (subLabel ?? '장비')
    : group.owner_type === 'PERSON'
      ? (subLabel ?? '인원')
      : '회사';
  const total = group.rows.length;
  const expired = group.rows.filter((r) => r.expiry_date && daysFrom(r.expiry_date) < 0).length;
  const rejected = group.rows.filter((r) => r.verification_status === 'REJECTED').length;
  const soon = group.rows.filter((r) => r.expiry_date && daysFrom(r.expiry_date) >= 0 && daysFrom(r.expiry_date) <= 60).length;
  const needVerify = group.rows.filter((r) => verifyableMap.get(r.document_type_id) === true
    && r.verification_status !== 'VERIFIED' && r.verification_status !== 'REJECTED').length;

  // 요약 배지 — 문제(만료+반려 혼합) > 만료 > 반려 > 임박 > 재검증 필요 > 정상
  const problem = expired + rejected;
  const badge = problem > 0
    ? {
        label: expired > 0 && rejected > 0 ? `문제 ${problem}건` : expired > 0 ? `만료 ${expired}건` : `반려 ${rejected}건`,
        cls: 'bg-rose-100 text-rose-700',
      }
    : soon > 0
      ? { label: `임박 ${soon}건`, cls: 'bg-amber-100 text-amber-800' }
      : needVerify > 0
        ? { label: '재검증 필요', cls: 'bg-blue-100 text-blue-700' }
        : { label: '정상', cls: 'bg-emerald-100 text-emerald-700' };

  return (
    <div>
      <div onClick={() => setOpen(!open)}
           className="flex items-center gap-3 px-4 sm:px-5 py-4 cursor-pointer hover:bg-slate-50 transition-colors">
        <span className="w-28 shrink-0">
          <span className="inline-flex max-w-full items-center px-2.5 py-1 rounded-md bg-slate-100 text-slate-600 text-xs font-semibold truncate" title={typeLabel}>{typeLabel}</span>
        </span>
        <span className="w-24 shrink-0 min-w-0">
          {group.owner_type === 'EQUIPMENT' ? (
            <span className="flex flex-col gap-0.5 items-start">
              <span className={`inline-flex items-center px-1.5 py-0.5 rounded text-[11px] font-semibold ${group.owner_external ? 'bg-amber-100 text-amber-800' : 'bg-slate-100 text-slate-500'}`}>
                {group.owner_external ? '외부' : '내부'}
              </span>
              {group.owner_external && group.owner_business_name && (
                <span className="max-w-full truncate text-[11px] text-slate-500" title={group.owner_business_name}>{group.owner_business_name}</span>
              )}
            </span>
          ) : (
            <span className="text-xs text-slate-300">-</span>
          )}
        </span>
        <span className="flex-1 min-w-0 flex items-center gap-2">
          <span className="font-bold text-slate-900 truncate">{group.owner_name ?? '#' + group.owner_id}</span>
          {isDeployed(group.owner_type, group.owner_assignment_status) && (
            <span className="inline-flex shrink-0 items-center gap-1 px-2 py-0.5 rounded-md bg-blue-100 text-blue-700 text-[11px] font-semibold">
              <span className="h-1.5 w-1.5 rounded-full bg-blue-500" />
              투입중
            </span>
          )}
        </span>
        <div className="flex items-center gap-3 shrink-0">
          <span className="w-20 text-right text-sm text-slate-500 whitespace-nowrap">서류 {total}건</span>
          <span className="w-28 flex justify-center">
            <span className={`inline-flex px-2.5 py-1 rounded-md text-xs font-semibold whitespace-nowrap ${badge.cls}`}>{badge.label}</span>
          </span>
          <Link to={href} onClick={(e) => e.stopPropagation()}
                className="w-14 inline-flex items-center justify-end gap-1 text-sm font-medium text-slate-600 hover:text-slate-900 whitespace-nowrap">
            상세
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="5" y1="12" x2="19" y2="12" /><polyline points="12 5 19 12 12 19" /></svg>
          </Link>
        </div>
      </div>
      {open && (
        <div className="border-t border-slate-100 bg-slate-50/40">
          {verifyableRows.length > 0 && (
            <div className="flex justify-end px-4 pt-2">
              <button onClick={reverifyAll} disabled={reverifyBusy}
                      className="px-2.5 py-1 rounded-md text-xs font-semibold border border-blue-200 bg-blue-50 text-blue-700 hover:bg-blue-100 disabled:opacity-50">
                {reverifyBusy ? '검증 중…' : `이 자원 재검증 (${verifyableRows.length}건)`}
              </button>
            </div>
          )}
          <table className="w-full text-sm">
          <thead className="text-left text-xs text-slate-500">
            <tr>
              <th className="px-4 py-2 font-semibold">서류</th>
              <th className="px-4 py-2 font-semibold">만료일</th>
              <th className="px-4 py-2 font-semibold">상태</th>
              <th className="px-4 py-2 font-semibold">검증</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {group.rows.map((r) => {
              const d = daysFrom(r.expiry_date);
              const chip = !r.expiry_date ? <span className="text-xs text-slate-400">-</span>
                : d < 0 ? <span className="px-1.5 py-0.5 rounded text-[11px] font-semibold bg-rose-100 text-rose-700">만료 {-d}일</span>
                : d <= 30 ? <span className="px-1.5 py-0.5 rounded text-[11px] font-semibold bg-amber-100 text-amber-800">D-{d}</span>
                : d <= 60 ? <span className="px-1.5 py-0.5 rounded text-[11px] font-semibold bg-yellow-100 text-yellow-800">D-{d}</span>
                : <span className="px-1.5 py-0.5 rounded text-[11px] font-semibold bg-emerald-100 text-emerald-800">D-{d}</span>;
              const verifyChip = r.verification_status === 'VERIFIED'
                ? <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[11px] font-bold bg-emerald-600 text-white">✓ 검증</span>
                : r.verification_status === 'REJECTED'
                  ? <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[11px] font-bold bg-rose-600 text-white">✗ 반려</span>
                  : <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[11px] font-semibold bg-slate-200 text-slate-700">대기</span>;
              return (
                <tr key={r.id} className="hover:bg-blue-50/40 cursor-pointer" onClick={() => onRowClick(r)}>
                  <td className="px-4 py-2 text-slate-700">{r.document_type_name ?? '#' + r.document_type_id}</td>
                  <td className="px-4 py-2 text-xs tabular-nums">{r.expiry_date ?? '-'}</td>
                  <td className="px-4 py-2">{chip}</td>
                  <td className="px-4 py-2">{verifyChip}</td>
                </tr>
              );
            })}
          </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// ──────────────────────────────────────────────────────────────────
// 보드 탭 — 사이트 단위 컴플라이언스 카드
// ──────────────────────────────────────────────────────────────────
type SupplementDialogState = {
  ownerType: OwnerType;
  ownerId: number;
  ownerName: string;
  documentTypeId: number;
  documentTypeName: string;
  contextSiteId?: number | null;
};

function BoardTab({
  sites,
  siteId,
  setSiteId,
  comp,
  loading,
  isAdmin,
  isBP,
  onRequestSupplement,
  onCreateWorkPlan,
}: {
  sites: SiteResponse[];
  siteId: number | null;
  setSiteId: (v: number | null) => void;
  comp: SiteCompliance | null;
  loading: boolean;
  isAdmin: boolean;
  isBP: boolean;
  onRequestSupplement: (s: SupplementDialogState) => void;
  onCreateWorkPlan: () => void;
}) {
  const canRequest = isAdmin || isBP;

  return (
    <>
      <section className="card space-y-3">
        <label className="block">
          <span className="text-xs font-semibold text-slate-500">현장</span>
          <select
            value={siteId ?? ''}
            onChange={(e) => setSiteId(e.target.value ? Number(e.target.value) : null)}
            className="input mt-1 bg-white"
          >
            <option value="">— 현장 선택 —</option>
            {sites.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name} {s.code ? `· ${s.code}` : ''}
              </option>
            ))}
          </select>
        </label>
      </section>

      {!siteId ? (
        <p className="text-sm text-slate-400 text-center py-8">
          현장을 선택하면 서류 컴플라이언스가 표시됩니다.
        </p>
      ) : loading ? (
        <p className="text-sm text-slate-400">불러오는 중...</p>
      ) : !comp ? null : (
        <>
          <ProgressBanner comp={comp} onCreateWorkPlan={onCreateWorkPlan} />
          <ResourceCard
            title={`발주자(BP) — ${comp.bp_company_name}`}
            r={comp.bp_company}
            canRequest={canRequest}
            contextSiteId={comp.site_id}
            onRequest={onRequestSupplement}
          />
          {comp.equipments.length > 0 && (
            <section>
              <h2 className="text-sm font-semibold text-slate-700 px-1 mb-2">
                장비 ({comp.equipments.length}대)
              </h2>
              <div className="grid gap-3">
                {comp.equipments.map((r) => (
                  <ResourceCard
                    key={`E${r.owner_id}`}
                    title={ownerCardTitle(r)}
                    subtitle={r.supplier_company_name ?? undefined}
                    r={r}
                    canRequest={canRequest}
                    contextSiteId={comp.site_id}
                    onRequest={onRequestSupplement}
                  />
                ))}
              </div>
            </section>
          )}
          {comp.persons.length > 0 && (
            <section>
              <h2 className="text-sm font-semibold text-slate-700 px-1 mb-2">
                인원 ({comp.persons.length}명)
              </h2>
              <div className="grid gap-3">
                {comp.persons.map((r) => (
                  <ResourceCard
                    key={`P${r.owner_id}`}
                    title={ownerCardTitle(r)}
                    subtitle={r.supplier_company_name ?? undefined}
                    r={r}
                    canRequest={canRequest}
                    contextSiteId={comp.site_id}
                    onRequest={onRequestSupplement}
                  />
                ))}
              </div>
            </section>
          )}
        </>
      )}
    </>
  );
}

function ProgressBanner({
  comp,
  onCreateWorkPlan,
}: {
  comp: SiteCompliance;
  onCreateWorkPlan: () => void;
}) {
  const ready = comp.ready_for_work_plan;
  const pct = comp.progress_pct;
  return (
    <section
      className={`card flex items-center justify-between gap-4 ${
        ready ? 'bg-emerald-50 border-emerald-200' : 'bg-amber-50 border-amber-200'
      }`}
    >
      <div className="flex-1">
        <div className="text-sm font-semibold text-slate-900">
          {ready ? '모든 필수 서류 준비 완료' : '서류 보완 진행 중'}
        </div>
        <div className="text-xs text-slate-600 mt-0.5">
          {comp.total_ok_items} / {comp.total_required_items} 항목 완료 ({pct}%)
        </div>
        <div className="mt-2 h-2 bg-slate-200 rounded-full overflow-hidden">
          <div
            className={`h-full ${ready ? 'bg-emerald-500' : 'bg-amber-500'} transition-all`}
            style={{ width: pct + '%' }}
          />
        </div>
      </div>
      <button
        type="button"
        onClick={onCreateWorkPlan}
        disabled={!ready}
        className={`shrink-0 px-4 py-2 rounded-md text-sm font-semibold ${
          ready
            ? 'bg-emerald-600 text-white hover:bg-emerald-700'
            : 'bg-slate-200 text-slate-400 cursor-not-allowed'
        }`}
        title={ready ? '' : '모든 필수 서류가 검증 완료되면 활성화됩니다.'}
      >
        작업계획서 만들기 →
      </button>
    </section>
  );
}

function ResourceCard({
  title,
  subtitle,
  r,
  canRequest,
  contextSiteId,
  onRequest,
}: {
  title: string;
  subtitle?: string;
  r: ResourceCompliance;
  canRequest: boolean;
  contextSiteId?: number;
  onRequest: (s: SupplementDialogState) => void;
}) {
  const [open, setOpen] = useState(true);
  const required = r.items.filter((i) => i.required);
  const optional = r.items.filter((i) => !i.required);
  return (
    <details className="card overflow-hidden p-0" open={open} onToggle={(e) => setOpen((e.target as HTMLDetailsElement).open)}>
      <summary className="cursor-pointer px-4 py-3 flex items-center justify-between bg-slate-50 hover:bg-slate-100 list-none">
        <div className="flex-1 min-w-0">
          <div className="text-sm font-bold text-slate-900 truncate">{title}</div>
          {subtitle && <div className="text-xs text-slate-500 truncate">{subtitle}</div>}
        </div>
        <div className="flex items-center gap-2">
          {r.ready_for_work_plan ? (
            <span className="text-xs bg-emerald-100 text-emerald-700 px-2 py-0.5 rounded-full font-semibold">
              준비 완료
            </span>
          ) : (
            <span className="text-xs bg-amber-100 text-amber-700 px-2 py-0.5 rounded-full font-semibold">
              {r.required_ok}/{r.required_total} ·{' '}
              {r.missing_count > 0 && `누락 ${r.missing_count}`}
              {r.rejected_count > 0 && ` · 반려 ${r.rejected_count}`}
              {r.expiring_count > 0 && ` · 만료 ${r.expiring_count}`}
            </span>
          )}
          <span className="text-xs text-slate-400">▾</span>
        </div>
      </summary>
      <div className="px-4 py-3 border-t border-slate-200 space-y-1">
        {required.length === 0 && optional.length === 0 && (
          <p className="text-xs text-slate-400 italic">정의된 서류가 없습니다.</p>
        )}
        {required.map((i) => (
          <ItemRow
            key={i.document_type_id}
            item={i}
            canRequest={canRequest}
            onRequest={() =>
              onRequest({
                ownerType: r.owner_type,
                ownerId: r.owner_id,
                ownerName: r.owner_name,
                documentTypeId: i.document_type_id,
                documentTypeName: i.document_type_name,
                contextSiteId,
              })
            }
          />
        ))}
        {optional.length > 0 && (
          <details className="mt-2">
            <summary className="cursor-pointer text-xs text-slate-500 hover:text-slate-700">
              선택 서류 ({optional.length}건) ▾
            </summary>
            <div className="mt-1 space-y-1">
              {optional.map((i) => (
                <ItemRow
                  key={i.document_type_id}
                  item={i}
                  canRequest={canRequest}
                  onRequest={() =>
                    onRequest({
                      ownerType: r.owner_type,
                      ownerId: r.owner_id,
                      ownerName: r.owner_name,
                      documentTypeId: i.document_type_id,
                      documentTypeName: i.document_type_name,
                      contextSiteId,
                    })
                  }
                />
              ))}
            </div>
          </details>
        )}
      </div>
    </details>
  );
}

function ItemRow({
  item,
  canRequest,
  onRequest,
}: {
  item: ResourceCompliance['items'][number];
  canRequest: boolean;
  onRequest: () => void;
}) {
  const status =
    item.verified && !item.expired
      ? { label: '검증 완료', cls: 'bg-emerald-100 text-emerald-700' }
      : item.expired
        ? { label: '만료됨', cls: 'bg-rose-100 text-rose-700' }
        : item.expiring_soon
          ? { label: '만료 임박', cls: 'bg-amber-100 text-amber-700' }
          : item.rejected
            ? { label: '반려', cls: 'bg-rose-100 text-rose-700' }
            : item.ocr_review_required
              ? { label: 'OCR 검토', cls: 'bg-amber-100 text-amber-700' }
              : !item.present
                ? { label: '미등록', cls: 'bg-slate-200 text-slate-700' }
                : { label: '검증 대기', cls: 'bg-slate-200 text-slate-700' };
  const needsAction =
    !item.verified || item.expired || item.expiring_soon || item.rejected || !item.present;

  return (
    <div className="flex items-center justify-between gap-2 py-1 px-1 hover:bg-slate-50 rounded">
      <div className="flex-1 min-w-0">
        <div className="text-sm text-slate-800 truncate">
          {item.required && <span className="text-rose-500 mr-1">*</span>}
          {item.document_type_name}
          {item.expiry_date && (
            <span className="text-[10px] text-slate-400 ml-2 tabular-nums">~{item.expiry_date}</span>
          )}
        </div>
      </div>
      <div className="flex items-center gap-1.5 shrink-0">
        <span className={`text-xs px-2 py-0.5 rounded-full ${status.cls}`}>{status.label}</span>
        {item.open_supplement && (
          <span className="text-xs px-2 py-0.5 rounded-full bg-blue-100 text-blue-700">
            보완요청 진행중
          </span>
        )}
        {canRequest && needsAction && !item.open_supplement && (
          <button
            type="button"
            onClick={onRequest}
            className="text-xs px-2 py-0.5 rounded border border-amber-300 text-amber-700 hover:bg-amber-50"
          >
            보완 요청
          </button>
        )}
      </div>
    </div>
  );
}

// ──────────────────────────────────────────────────────────────────
// 보완 요청 다이얼로그
// ──────────────────────────────────────────────────────────────────
function SupplementDialog({
  state,
  onClose,
  onSubmitted,
}: {
  state: SupplementDialogState;
  onClose: () => void;
  onSubmitted: () => void;
}) {
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.post('/api/document-supplements', {
        target_owner_type: state.ownerType,
        target_owner_id: state.ownerId,
        document_type_id: state.documentTypeId,
        context_site_id: state.contextSiteId,
        reason: reason.trim() || undefined,
      });
      onSubmitted();
    } catch (err: any) {
      setError(err?.response?.data?.message ?? '발송 실패');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={() => !busy && onClose()}>
      <div className="bg-white rounded-xl p-6 max-w-lg w-full space-y-3 shadow-xl" onClick={(e) => e.stopPropagation()}>
        <h3 className="text-lg font-bold">서류 보완 요청</h3>
        <div className="text-sm text-slate-700 space-y-1 bg-slate-50 px-3 py-2 rounded">
          <div>대상 자원: <b>{state.ownerName}</b></div>
          <div>서류: <b>{state.documentTypeName}</b></div>
        </div>
        <label className="block">
          <span className="text-xs font-semibold text-slate-500">사유 (공급사에게 보임)</span>
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={4}
            placeholder="예: 비파괴검사서가 만료됐습니다. 갱신 후 재업로드 부탁드립니다."
            className="input mt-1 resize-y"
          />
        </label>
        {error && (
          <p className="text-xs text-rose-700 bg-rose-50 border border-rose-200 rounded px-2 py-1">{error}</p>
        )}
        <div className="flex justify-end gap-2 pt-1">
          <button type="button" onClick={onClose} disabled={busy} className="btn-ghost">취소</button>
          <button
            type="button"
            onClick={submit}
            disabled={busy}
            className="btn-primary disabled:opacity-50"
          >
            {busy ? '발송 중...' : '발송'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ──────────────────────────────────────────────────────────────────
// 보완 요청 목록 테이블
// ──────────────────────────────────────────────────────────────────
function fixUrl(s: SupplementResponse): string {
  const q = `?supplementType=${s.document_type_id}`;
  if (s.target_owner_type === 'EQUIPMENT') return `/equipment/${s.target_owner_id}${q}`;
  if (s.target_owner_type === 'PERSON') return `/persons/${s.target_owner_id}${q}`;
  return `/my-company${q}`;
}

function SupplementListTable({
  rows,
  emptyMsg,
  onCancel,
  canCancel,
  showFix = false,
}: {
  rows: SupplementResponse[];
  emptyMsg: string;
  onCancel: ((id: number) => Promise<void>) | null;
  canCancel: (s: SupplementResponse) => boolean;
  showFix?: boolean;
}) {
  if (rows.length === 0) {
    return <div className="card p-6 text-center text-sm text-slate-400">{emptyMsg}</div>;
  }
  return (
    <div className="card overflow-x-auto p-0">
      <table className="w-full text-sm">
        <thead className="border-b border-slate-200 bg-slate-50 text-left text-slate-500">
          <tr>
            <th className="px-4 py-3 font-semibold">자원</th>
            <th className="px-4 py-3 font-semibold">서류</th>
            <th className="px-4 py-3 font-semibold">공급사</th>
            <th className="px-4 py-3 font-semibold">사유</th>
            <th className="px-4 py-3 font-semibold">상태</th>
            <th className="px-4 py-3 font-semibold">생성</th>
            <th className="px-4 py-3 font-semibold">액션</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((s) => (
            <tr key={s.id}>
              <td className="px-4 py-3 text-slate-900">{s.target_owner_name ?? `#${s.target_owner_id}`}</td>
              <td className="px-4 py-3 text-slate-700">{s.document_type_name ?? `#${s.document_type_id}`}</td>
              <td className="px-4 py-3 text-slate-700">{s.target_supplier_company_name}</td>
              <td className="px-4 py-3 text-xs text-slate-500 max-w-[280px] truncate" title={s.reason ?? ''}>
                {s.reason || '-'}
              </td>
              <td className="px-4 py-3">
                <span className={`text-xs px-2 py-0.5 rounded-full ${
                  s.status === 'OPEN' ? 'bg-amber-100 text-amber-700'
                    : s.status === 'RESOLVED' ? 'bg-emerald-100 text-emerald-700'
                    : 'bg-slate-100 text-slate-600'
                }`}>
                  {SUPPLEMENT_STATUS_LABEL[s.status]}
                </span>
              </td>
              <td className="px-4 py-3 text-xs text-slate-500 tabular-nums">
                {new Date(s.created_at).toLocaleDateString('ko-KR')}
              </td>
              <td className="px-4 py-3">
                <div className="flex items-center gap-1.5">
                  {showFix && s.status === 'OPEN' && (
                    <Link
                      to={fixUrl(s)}
                      className="text-xs px-2 py-0.5 rounded bg-brand-600 text-white hover:bg-brand-700 whitespace-nowrap"
                    >
                      보완하러 가기
                    </Link>
                  )}
                  {onCancel && canCancel(s) && (
                    <button
                      type="button"
                      onClick={() => onCancel(s.id)}
                      className="text-xs px-2 py-0.5 rounded border border-slate-300 hover:bg-slate-50"
                    >
                      취소
                    </button>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`px-4 py-2 text-sm font-semibold border-b-2 ${
        active ? 'border-brand-600 text-brand-700' : 'border-transparent text-slate-500 hover:text-slate-800'
      }`}
    >
      {children}
    </button>
  );
}
