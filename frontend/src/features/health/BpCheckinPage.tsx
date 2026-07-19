import { useEffect, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AppShell from '../../components/layout/AppShell';
import { useAuth } from '../auth/AuthContext';

type SiteRow = { id: number; name: string };
type PersonRow = { id: number; name: string };
type Unmeasured = { person_id: number; name: string; health_risk_level: string; phone: string | null };
type Checkin = {
  id: number;
  person_id: number;
  person_name: string;
  sys: number;
  dia: number;
  pulse: number | null;
  method: string;
  verdict: 'OK' | 'CAUTION' | 'BLOCK';
  measured_at: string;
};

const EMPTY_FORM = { personId: '' as number | '', sys: '', dia: '', pulse: '', method: 'MANUAL' };

/**
 * P5-W4 1겹 — 혈압 체크인 입력 + 오늘 현황(미측정 고위험군 강조). 관리자·BP·공급사.
 * BLOCK 이어도 출근 차단은 없음(권고+관리자 통보) — 여기선 기록·현황만.
 */
export default function BpCheckinPage() {
  const { user } = useAuth();
  const allowed = user?.role === 'ADMIN' || user?.role === 'BP'
    || user?.role === 'EQUIPMENT_SUPPLIER' || user?.role === 'MANPOWER_SUPPLIER';

  const [sites, setSites] = useState<SiteRow[]>([]);
  const [siteId, setSiteId] = useState<number | null>(null);
  const [sitePersons, setSitePersons] = useState<PersonRow[]>([]);
  const [unmeasured, setUnmeasured] = useState<Unmeasured[]>([]);
  const [checkins, setCheckins] = useState<Checkin[]>([]);
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!allowed) return;
    api.get<SiteRow[]>('/api/sites')
      .then((r) => { setSites(r.data); if (r.data.length > 0) setSiteId(r.data[0].id); })
      .catch(() => setSites([]));
  }, [allowed]);

  useEffect(() => {
    if (siteId == null) { setSitePersons([]); setUnmeasured([]); setCheckins([]); return; }
    reload(siteId);
  }, [siteId]);

  function reload(id: number) {
    api.get<PersonRow[]>('/api/bp-checkins/persons', { params: { siteId: id } })
      .then((r) => setSitePersons(r.data)).catch(() => setSitePersons([]));
    api.get<Unmeasured[]>('/api/bp-checkins/unmeasured', { params: { siteId: id } })
      .then((r) => setUnmeasured(r.data)).catch(() => setUnmeasured([]));
    api.get<Checkin[]>('/api/bp-checkins', { params: { siteId: id } })
      .then((r) => setCheckins(r.data)).catch(() => setCheckins([]));
  }

  async function submit() {
    if (siteId == null) { toast.error('현장을 선택하세요'); return; }
    if (form.personId === '') { toast.error('작업자를 선택하세요'); return; }
    if (form.sys === '' || form.dia === '') { toast.error('수축기·이완기 혈압을 입력하세요'); return; }
    setSaving(true);
    try {
      const { data } = await api.post<Checkin>('/api/bp-checkins', {
        person_id: form.personId,
        site_id: siteId,
        sys: Number(form.sys),
        dia: Number(form.dia),
        pulse: form.pulse === '' ? null : Number(form.pulse),
        method: form.method,
      });
      if (data.verdict === 'BLOCK') toast.error(`차단권고: ${data.sys}/${data.dia} — 관리자에 통보되었습니다`);
      else if (data.verdict === 'CAUTION') toast.error(`주의: ${data.sys}/${data.dia} — 무리하지 않도록 안내하세요`);
      else toast.success(`정상 범위(${data.sys}/${data.dia})로 기록했습니다`);
      setForm({ ...EMPTY_FORM, method: form.method });
      reload(siteId);
    } catch (e) {
      const err = e as AxiosError<{ message?: string }>;
      toast.error(err.response?.data?.message ?? '기록에 실패했습니다');
    } finally {
      setSaving(false);
    }
  }

  if (!allowed) {
    return <AppShell><div className="p-6 text-slate-600">접근 권한이 없습니다.</div></AppShell>;
  }

  return (
    <AppShell>
      <div className="p-6 space-y-5">
        <div>
          <h1 className="h1-page">혈압 체크인</h1>
          <p className="mt-1 text-sm text-slate-500">
            작업 전 혈압을 측정·기록합니다. 임계 초과 시 <span className="font-medium text-rose-600">고소·고강도 배제 권고</span>와
            관리자 통보가 자동 기록됩니다(출근을 막지는 않습니다).
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <label className="text-sm font-medium text-slate-700">현장</label>
          <select
            value={siteId ?? ''}
            onChange={(e) => setSiteId(e.target.value ? Number(e.target.value) : null)}
            className="rounded border border-slate-300 px-3 py-1.5 text-sm min-w-[220px]"
          >
            {sites.length === 0 && <option value="">현장 없음</option>}
            {sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </div>

        <div className="grid gap-5 lg:grid-cols-2">
          {/* 입력 폼 */}
          <section className="rounded border border-slate-200 bg-white p-4">
            <h2 className="text-sm font-semibold text-slate-800 mb-3">혈압 입력</h2>
            <div className="space-y-3">
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">작업자</label>
                <select
                  value={form.personId}
                  onChange={(e) => setForm((f) => ({ ...f, personId: e.target.value ? Number(e.target.value) : '' }))}
                  className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
                >
                  <option value="">선택하세요</option>
                  {sitePersons.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                </select>
                {sitePersons.length === 0 && (
                  <p className="mt-1 text-[11px] text-slate-400">이 현장에 배치된 작업자가 없습니다.</p>
                )}
              </div>
              <div className="grid grid-cols-3 gap-3">
                <NumField label="수축기(mmHg)" value={form.sys} onChange={(v) => setForm((f) => ({ ...f, sys: v }))} />
                <NumField label="이완기(mmHg)" value={form.dia} onChange={(v) => setForm((f) => ({ ...f, dia: v }))} />
                <NumField label="맥박(선택)" value={form.pulse} onChange={(v) => setForm((f) => ({ ...f, pulse: v }))} />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">측정 방식</label>
                <select
                  value={form.method}
                  onChange={(e) => setForm((f) => ({ ...f, method: e.target.value }))}
                  className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
                >
                  <option value="MANUAL">커프 수기 입력</option>
                  <option value="BLE">블루투스 혈압계</option>
                </select>
              </div>
              <button
                onClick={submit} disabled={saving}
                className="rounded bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
              >
                {saving ? '기록 중…' : '체크인 기록'}
              </button>
            </div>
          </section>

          {/* 오늘 미측정 고위험군 */}
          <section className="rounded border border-slate-200 bg-white p-4">
            <h2 className="text-sm font-semibold text-slate-800 mb-1">
              오늘 미측정 고위험군
              <span className="ml-2 rounded-full bg-rose-100 px-2 py-0.5 text-xs font-medium text-rose-700">
                {unmeasured.length}명
              </span>
            </h2>
            <p className="text-xs text-slate-500 mb-3">건강검진 기반 고위험군(HIGH) 중 오늘 혈압을 아직 측정하지 않은 인원입니다.</p>
            {unmeasured.length === 0 ? (
              <div className="rounded border border-dashed border-slate-200 p-6 text-center text-sm text-slate-400">
                미측정 고위험군이 없습니다.
              </div>
            ) : (
              <ul className="space-y-1.5">
                {unmeasured.map((p) => (
                  <li key={p.person_id} className="flex items-center justify-between rounded bg-rose-50 px-3 py-2">
                    <span className="text-sm text-slate-800">
                      {p.name}
                      <span className="ml-2 rounded bg-rose-600 px-1.5 py-0.5 text-[10px] font-semibold text-white">HIGH</span>
                    </span>
                    <button
                      onClick={() => setForm((f) => ({ ...f, personId: p.person_id }))}
                      className="rounded border border-rose-300 px-2 py-1 text-xs font-medium text-rose-700 hover:bg-rose-100"
                    >
                      혈압 입력
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        {/* 오늘 측정 현황 */}
        <section className="rounded border border-slate-200 bg-white p-4">
          <h2 className="text-sm font-semibold text-slate-800 mb-3">오늘 측정 현황 ({checkins.length})</h2>
          {checkins.length === 0 ? (
            <div className="rounded border border-dashed border-slate-200 p-6 text-center text-sm text-slate-400">
              오늘 측정 기록이 없습니다.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
                    <th className="py-2 pr-3">작업자</th>
                    <th className="py-2 pr-3">혈압</th>
                    <th className="py-2 pr-3">맥박</th>
                    <th className="py-2 pr-3">방식</th>
                    <th className="py-2 pr-3">판정</th>
                    <th className="py-2 pr-3">측정 시각</th>
                  </tr>
                </thead>
                <tbody>
                  {checkins.map((c) => (
                    <tr key={c.id} className="border-b border-slate-100">
                      <td className="py-2 pr-3 text-slate-800">{c.person_name}</td>
                      <td className="py-2 pr-3 tabular-nums">{c.sys}/{c.dia}</td>
                      <td className="py-2 pr-3 tabular-nums">{c.pulse ?? '—'}</td>
                      <td className="py-2 pr-3 text-slate-500">{c.method === 'BLE' ? '블루투스' : '수기'}</td>
                      <td className="py-2 pr-3"><VerdictBadge v={c.verdict} /></td>
                      <td className="py-2 pr-3 text-slate-500">{new Date(c.measured_at).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </AppShell>
  );
}

function VerdictBadge({ v }: { v: 'OK' | 'CAUTION' | 'BLOCK' }) {
  const map = {
    OK: { cls: 'bg-emerald-100 text-emerald-700', label: '정상' },
    CAUTION: { cls: 'bg-amber-100 text-amber-700', label: '주의' },
    BLOCK: { cls: 'bg-rose-100 text-rose-700', label: '차단권고' },
  } as const;
  const m = map[v];
  return <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${m.cls}`}>{m.label}</span>;
}

function NumField({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <div>
      <label className="block text-xs font-medium text-slate-600 mb-1">{label}</label>
      <input
        type="number" inputMode="numeric"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
      />
    </div>
  );
}
