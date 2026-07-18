import { useEffect, useMemo, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import { useAuth } from '../auth/AuthContext';
import { toast } from '../../lib/toast';
import type { InspectionResponse } from '../../types/safety';
import { KIND_LABEL, STATUS_CHIP, STATUS_LABEL } from '../../types/safety';
import InspectionCreateDialog from './InspectionCreateDialog';
import LegalInspectionStatusCard from './LegalInspectionStatusCard';

/**
 * 안전점검 일정 관리 페이지.
 *  - BP/ADMIN: 현장 선택 → list + 일정 등록 / 통보 / 완료 처리
 *  - 공급사: 자기 회사 받은 list + 확인 사인
 */
type Site = { id: number; name: string };

export default function SafetyInspectionsPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const isBP = user?.role === 'BP';
  const isSupplier = user?.role === 'EQUIPMENT_SUPPLIER' || user?.role === 'MANPOWER_SUPPLIER';

  const [sites, setSites] = useState<Site[]>([]);
  const [siteId, setSiteId] = useState<number | null>(null);
  const [list, setList] = useState<InspectionResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);

  // BP/ADMIN 은 현장 list 가져와서 select. 공급사는 mine.
  useEffect(() => {
    if (isSupplier) return;
    api.get<Site[]>('/api/sites').then((r) => {
      setSites(r.data);
      if (r.data.length > 0) setSiteId(r.data[0].id);
    }).catch(() => setSites([]));
  }, [isSupplier]);

  async function load() {
    setLoading(true);
    try {
      const url = isSupplier ? '/api/safety-inspections/mine'
        : (siteId ? `/api/safety-inspections/site/${siteId}` : null);
      if (!url) { setList([]); return; }
      const res = await api.get<InspectionResponse[]>(url);
      setList(res.data);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [siteId, isSupplier]);

  async function send(id: number) {
    try {
      await api.post(`/api/safety-inspections/${id}/send`);
      toast.success('공급사에 통보했습니다');
      void load();
    } catch (err) {
      toast.error(err instanceof AxiosError ? (err.response?.data?.message ?? '실패') : '실패');
    }
  }
  async function confirm(id: number) {
    try {
      await api.post(`/api/safety-inspections/${id}/confirm`);
      toast.success('일정을 확인했습니다');
      void load();
    } catch (err) {
      toast.error(err instanceof AxiosError ? (err.response?.data?.message ?? '실패') : '실패');
    }
  }
  async function complete(id: number) {
    const notes = window.prompt('검사 결과 메모 (선택)') ?? '';
    try {
      await api.post(`/api/safety-inspections/${id}/complete`, { resultNotes: notes });
      toast.success('검사 완료 처리됨');
      void load();
    } catch (err) {
      toast.error(err instanceof AxiosError ? (err.response?.data?.message ?? '실패') : '실패');
    }
  }

  const pageTitle = isSupplier ? '받은 안전점검 일정' : '안전점검 관리';

  const grouped = useMemo(() => {
    const map = new Map<string, InspectionResponse[]>();
    list.forEach((i) => {
      const key = i.status;
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(i);
    });
    return map;
  }, [list]);

  return (
    <AppShell breadcrumb={[{ label: pageTitle }]}>
      <div className="max-w-5xl mx-auto px-6 py-6 space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold">{pageTitle}</h1>
            <p className="text-sm text-slate-500 mt-1">
              {isSupplier
                ? '받은 차량검사 / 입소검사 일정. 확인 후 검사 당일 현장에 입소하세요.'
                : '현장 입소 전 차량검사 (사전) + 입소검사 (당일) 일정 관리.'}
            </p>
          </div>
          {(isAdmin || isBP) && siteId && (
            <button onClick={() => setCreateOpen(true)} className="btn-primary">+ 일정 등록</button>
          )}
        </div>

        {/* BP/ADMIN 현장 선택 */}
        {!isSupplier && sites.length > 0 && (
          <div className="card flex items-center gap-3">
            <span className="text-sm text-slate-600">현장</span>
            <select value={siteId ?? ''} onChange={(e) => setSiteId(Number(e.target.value))}
                    className="input bg-white max-w-xs">
              {sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </div>
        )}

        {/* S2′ 법정점검(안전점검원 NFC) 현황 — BP/ADMIN */}
        {(isAdmin || isBP) && siteId && <LegalInspectionStatusCard siteId={siteId} />}

        {loading ? (
          <p className="text-sm text-slate-400">로딩...</p>
        ) : list.length === 0 ? (
          <div className="card py-12 text-center text-slate-400">등록된 검사 일정이 없습니다.</div>
        ) : (
          <div className="space-y-3">
            {Array.from(grouped.entries()).map(([status, items]) => (
              <div key={status}>
                <div className="text-xs font-semibold text-slate-500 mb-1.5">{STATUS_LABEL[status as keyof typeof STATUS_LABEL]} ({items.length})</div>
                <div className="space-y-1.5">
                  {items.map((it) => (
                    <div key={it.id} className="card flex items-center justify-between gap-4 py-3">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className={`text-[11px] px-1.5 py-0.5 rounded font-semibold ${STATUS_CHIP[it.status]}`}>
                            {STATUS_LABEL[it.status]}
                          </span>
                          <span className="text-xs font-bold text-slate-700">{KIND_LABEL[it.kind]}</span>
                          <span className="text-sm font-semibold text-slate-900">{it.target_label}</span>
                          <span className="text-xs text-slate-500">·</span>
                          <span className="text-sm text-slate-700">{new Date(it.scheduled_at).toLocaleString('ko-KR')}</span>
                        </div>
                        <div className="text-xs text-slate-500 mt-0.5">
                          현장 {it.site_name} · 공급사 {it.supplier_company_name ?? '-'}
                        </div>
                        {it.result_notes && <div className="text-xs text-slate-600 mt-1">결과: {it.result_notes}</div>}
                      </div>
                      <div className="flex gap-1.5">
                        {(isAdmin || isBP) && it.status === 'PENDING' && (
                          <button onClick={() => send(it.id)} className="text-xs px-2.5 py-1.5 rounded border border-blue-300 text-blue-700 hover:bg-blue-50">
                            공급사 통보
                          </button>
                        )}
                        {isSupplier && it.status === 'SENT' && (
                          <button onClick={() => confirm(it.id)} className="text-xs px-2.5 py-1.5 rounded border border-amber-300 text-amber-700 hover:bg-amber-50">
                            일정 확인
                          </button>
                        )}
                        {(isAdmin || isBP) && (it.status === 'CONFIRMED' || it.status === 'SENT') && (
                          <button onClick={() => complete(it.id)} className="text-xs px-2.5 py-1.5 rounded border border-emerald-300 text-emerald-700 hover:bg-emerald-50">
                            검사 완료
                          </button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {createOpen && siteId && (
        <InspectionCreateDialog
          siteId={siteId}
          onClose={() => setCreateOpen(false)}
          onCreated={() => { setCreateOpen(false); void load(); }}
        />
      )}
    </AppShell>
  );
}
