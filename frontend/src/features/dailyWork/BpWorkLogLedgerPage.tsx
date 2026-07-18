import { useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AppShell from '../../components/layout/AppShell';
import { SignaturePadDialog } from '../workPlan/create/components/SignaturePadDialog';
import LedgerPanel from './LedgerPanel';
import { OT_COLS, SIGN_BADGE, type DailyWorkLog } from './types';

type Tab = 'sign' | 'ledger';

export default function BpWorkLogLedgerPage() {
  const [tab, setTab] = useState<Tab>('sign');
  const [logs, setLogs] = useState<DailyWorkLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [unsignedOnly, setUnsignedOnly] = useState(true);
  const [signTarget, setSignTarget] = useState<DailyWorkLog | null>(null);

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

  const unsignedCount = useMemo(() => logs.filter((l) => l.sign_status === 'UNSIGNED').length, [logs]);
  const shown = useMemo(() => (unsignedOnly ? logs.filter((l) => l.sign_status === 'UNSIGNED') : logs), [logs, unsignedOnly]);

  const doSign = async (pngBase64: string) => {
    if (!signTarget) return;
    try {
      await api.post(`/api/daily-work-logs/${signTarget.id}/sign`, { signature_png_base64: pngBase64 });
      toast.success('서명이 완료되었습니다');
      setSignTarget(null);
      void load();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '서명에 실패했습니다');
    }
  };

  return (
    <AppShell breadcrumb={[{ label: '작업확인 원장' }]}>
      <div className="mx-auto max-w-6xl space-y-6">
        <header className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold text-slate-950">작업확인 원장 (서명)</h1>
            <p className="mt-1 text-sm text-slate-500">
              공급사가 올린 일일 작업확인서를 현장에서 확인하고 서명합니다. 월간 원장은 정산주기(현장정산일) 기준으로 집계됩니다.
            </p>
          </div>
          {unsignedCount > 0 && (
            <span className="shrink-0 rounded-full bg-amber-100 text-amber-800 text-sm font-semibold px-3 py-1">
              미서명 {unsignedCount}건
            </span>
          )}
        </header>

        <div className="flex gap-1 border-b border-slate-200">
          <TabBtn active={tab === 'sign'} onClick={() => setTab('sign')}>서명 목록</TabBtn>
          <TabBtn active={tab === 'ledger'} onClick={() => setTab('ledger')}>월간 원장</TabBtn>
        </div>

        {tab === 'sign' && (
          <div className="space-y-3">
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <input type="checkbox" checked={unsignedOnly} onChange={(e) => setUnsignedOnly(e.target.checked)} />
              미서명만 보기
            </label>
            {loading ? (
              <div className="text-sm text-slate-400">불러오는 중…</div>
            ) : shown.length === 0 ? (
              <div className="card p-10 text-center">
                <div className="text-4xl mb-2">✅</div>
                <div className="font-semibold text-slate-700">{unsignedOnly ? '서명 대기 중인 확인서가 없습니다' : '표시할 확인서가 없습니다'}</div>
                <p className="mt-1 text-sm text-slate-400">공급사가 일일 확인서를 올리면 여기에서 서명할 수 있습니다.</p>
              </div>
            ) : (
              <div className="space-y-2">
                {shown.map((l) => <BpLogCard key={l.id} log={l} onSign={() => setSignTarget(l)} />)}
              </div>
            )}
          </div>
        )}

        {tab === 'ledger' && <LedgerPanel logs={logs} />}
      </div>

      <SignaturePadDialog
        open={!!signTarget}
        title="작업확인 서명"
        signerName={signTarget ? `${signTarget.work_date} · ${signTarget.equipment_label ?? signTarget.person_name ?? ''}` : undefined}
        onClose={() => setSignTarget(null)}
        onConfirm={doSign}
      />
    </AppShell>
  );
}

function BpLogCard({ log, onSign }: { log: DailyWorkLog; onSign: () => void }) {
  const otTotal = OT_COLS.reduce((s, c) => s + (log[`ot_${c.key}` as keyof DailyWorkLog] as number), 0);
  const resource = log.equipment_label ?? log.person_name ?? '자원 미지정';
  const badge = SIGN_BADGE[log.sign_status];

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
          {log.supplier_company_name ?? ''}
          {log.site_name ? ` · ${log.site_name}` : ''}
          {log.work_location ? ` · ${log.work_location}` : ''}
          {log.work_content ? ` · ${log.work_content}` : ''}
          {otTotal > 0 ? ` · OT ${otTotal}h` : ''}
        </div>
      </div>
      <div className="flex items-center gap-2 text-xs">
        {log.has_slip_photo && (
          <button onClick={() => openBlob(`/api/daily-work-logs/${log.id}/photo`)} className="text-blue-600 font-semibold hover:underline">전표 사진</button>
        )}
        {log.sign_status === 'SIGNED' ? (
          log.has_sign_image && (
            <button onClick={() => openBlob(`/api/daily-work-logs/${log.id}/sign-image`)} className="text-emerald-600 font-semibold hover:underline">서명 보기</button>
          )
        ) : (
          <button onClick={onSign} className="btn-primary px-3 py-1.5">서명</button>
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
