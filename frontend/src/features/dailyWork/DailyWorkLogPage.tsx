import { useEffect, useRef, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AppShell from '../../components/layout/AppShell';
import DailyWorkLogForm, { type FormOptions } from './DailyWorkLogForm';
import LedgerPanel from './LedgerPanel';
import { OT_COLS, SIGN_BADGE, type DailyWorkLog } from './types';

type Tab = 'create' | 'list' | 'ledger';

export default function DailyWorkLogPage() {
  const [tab, setTab] = useState<Tab>('list');
  const [logs, setLogs] = useState<DailyWorkLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [options, setOptions] = useState<FormOptions>({ equipment: [], persons: [], contracts: [], sites: [], bps: [] });
  const [editing, setEditing] = useState<DailyWorkLog | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const { data } = await api.get<DailyWorkLog[]>('/api/daily-work-logs');
      setLogs(data ?? []);
    } catch {
      setLogs([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);
  useEffect(() => {
    (async () => {
      const [eq, pp, ct, st, bp] = await Promise.all([
        api.get<any[]>('/api/equipment').then((r) => r.data ?? []).catch(() => []),
        api.get<{ content: any[] }>('/api/persons', { params: { size: 100 } }).then((r) => r.data?.content ?? []).catch(() => []),
        api.get<any[]>('/api/contracts').then((r) => r.data ?? []).catch(() => []),
        api.get<any[]>('/api/sites').then((r) => r.data ?? []).catch(() => []),
        api.get<any[]>('/api/companies/bp-list').then((r) => r.data ?? []).catch(() => []),
      ]);
      setOptions({
        equipment: eq.map((e) => ({ id: e.id, label: e.vehicle_no || e.model || `장비 #${e.id}` })),
        persons: pp.map((p) => ({ id: p.id, name: p.name })),
        contracts: ct.map((c) => ({ id: c.id, title: c.title || `계약 #${c.id}`, rate_type: c.rate_type, bp_company_id: c.bp_company_id ?? null, site_id: c.site_id ?? null, site_name: c.site_name ?? null })),
        sites: st.map((s) => ({ id: s.id, name: s.name })),
        bps: bp.map((b) => ({ id: b.id, name: b.name })),
      });
    })();
  }, []);

  const onCreated = () => { void load(); setTab('list'); };

  return (
    <AppShell breadcrumb={[{ label: '일일 확인서' }]}>
      <div className="mx-auto max-w-6xl space-y-6">
        <header>
          <h1 className="text-2xl font-bold text-slate-950">일일 작업확인서</h1>
          <p className="mt-1 text-sm text-slate-500">
            하루 1건씩 작업내용·위치·구분·OT를 기록하면 BP가 현장에서 서명하거나, 단독모드에선 종이 전표 사진으로 갈음합니다.
            월간 원장·정산은 이 기록에서 자동 집계됩니다.
          </p>
        </header>

        <div className="flex gap-1 border-b border-slate-200">
          <TabBtn active={tab === 'create'} onClick={() => { setEditing(null); setTab('create'); }}>작성</TabBtn>
          <TabBtn active={tab === 'list'} onClick={() => setTab('list')}>목록 ({logs.length})</TabBtn>
          <TabBtn active={tab === 'ledger'} onClick={() => setTab('ledger')}>월간 원장</TabBtn>
        </div>

        {tab === 'create' && (
          <div className="card p-5">
            <DailyWorkLogForm existing={null} options={options} onSaved={onCreated} />
          </div>
        )}

        {tab === 'list' && (
          loading ? (
            <div className="text-sm text-slate-400">불러오는 중…</div>
          ) : logs.length === 0 ? (
            <div className="card p-10 text-center">
              <div className="text-4xl mb-2">📝</div>
              <div className="font-semibold text-slate-700">아직 작성한 일일 확인서가 없습니다</div>
              <p className="mt-1 text-sm text-slate-400">“작성” 탭에서 오늘·과거 날짜의 작업을 기록하세요.</p>
            </div>
          ) : (
            <div className="space-y-2">
              {logs.map((l) => <LogCard key={l.id} log={l} onEdit={() => setEditing(l)} onChanged={load} />)}
            </div>
          )
        )}

        {tab === 'ledger' && <LedgerPanel logs={logs} />}
      </div>

      {editing && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-2xl max-h-[92vh] flex flex-col">
            <div className="px-5 py-3 border-b flex items-center justify-between">
              <h3 className="font-bold text-slate-900">일일 확인서 수정</h3>
              <button onClick={() => setEditing(null)} className="text-slate-500 hover:text-slate-800 text-xl leading-none">×</button>
            </div>
            <div className="px-5 py-4 overflow-y-auto">
              <DailyWorkLogForm existing={editing} options={options}
                                onSaved={() => { setEditing(null); void load(); }}
                                onCancel={() => setEditing(null)} />
            </div>
          </div>
        </div>
      )}
    </AppShell>
  );
}

function LogCard({ log, onEdit, onChanged }: { log: DailyWorkLog; onEdit: () => void; onChanged: () => void }) {
  const fileRef = useRef<HTMLInputElement | null>(null);
  const [busy, setBusy] = useState(false);
  const otTotal = OT_COLS.reduce((s, c) => s + (log[`ot_${c.key}` as keyof DailyWorkLog] as number), 0);
  const resource = log.equipment_label ?? log.person_name ?? '자원 미지정';
  const badge = SIGN_BADGE[log.sign_status];

  const uploadPhoto = async (file: File) => {
    setBusy(true);
    try {
      const fd = new FormData();
      fd.append('file', file);
      await api.post(`/api/daily-work-logs/${log.id}/photo`, fd);
      toast.success('전표 사진으로 갈음했습니다');
      onChanged();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '사진 업로드 실패');
    } finally {
      setBusy(false);
    }
  };

  const openBlob = async (url: string) => {
    try {
      const { data } = await api.get(url, { responseType: 'blob' });
      window.open(URL.createObjectURL(data), '_blank');
    } catch {
      toast.error('열 수 없습니다');
    }
  };

  return (
    <div className="card p-4 flex flex-wrap items-center gap-x-4 gap-y-2">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="font-bold text-slate-900 tabular-nums">{log.work_date}</span>
          <span className="text-sm text-slate-700 truncate">{resource}</span>
          <span className={`shrink-0 px-2 py-0.5 rounded text-[11px] font-semibold ${badge.cls}`}>{badge.text}</span>
        </div>
        <div className="mt-0.5 text-xs text-slate-500 truncate">
          {log.site_name ?? '현장 미지정'}
          {log.work_location ? ` · ${log.work_location}` : ''}
          {log.work_content ? ` · ${log.work_content}` : ''}
          {` · ${log.rate_type === 'DAILY' ? '일대' : '월대'}`}
          {otTotal > 0 ? ` · OT ${otTotal}h` : ''}
          {log.start_time ? ` · ${log.start_time.slice(0, 5)}~${log.end_time?.slice(0, 5) ?? ''}` : ''}
        </div>
      </div>
      <div className="flex items-center gap-2 text-xs">
        {log.has_sign_image && (
          <button onClick={() => openBlob(`/api/daily-work-logs/${log.id}/sign-image`)} className="text-emerald-600 font-semibold hover:underline">서명 보기</button>
        )}
        {log.has_slip_photo && (
          <button onClick={() => openBlob(`/api/daily-work-logs/${log.id}/photo`)} className="text-blue-600 font-semibold hover:underline">전표 사진</button>
        )}
        {log.sign_status !== 'SIGNED' && (
          <>
            <input ref={fileRef} type="file" accept="image/*" className="hidden"
                   onChange={(e) => { const f = e.target.files?.[0]; if (f) void uploadPhoto(f); e.target.value = ''; }} />
            <button disabled={busy} onClick={() => fileRef.current?.click()} className="text-slate-600 font-semibold hover:underline disabled:opacity-50">
              {busy ? '업로드…' : '사진 갈음'}
            </button>
            <button onClick={onEdit} className="text-brand-600 font-semibold hover:underline">수정</button>
          </>
        )}
      </div>
    </div>
  );
}

function TabBtn({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" onClick={onClick}
            className={`px-4 py-2 text-sm font-semibold border-b-2 -mb-px ${active ? 'border-brand-600 text-brand-700' : 'border-transparent text-slate-500 hover:text-slate-800'}`}>
      {children}
    </button>
  );
}
