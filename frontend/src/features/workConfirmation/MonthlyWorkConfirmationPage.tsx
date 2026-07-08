import { useEffect, useState } from 'react';
import AppShell from '../../components/layout/AppShell';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import CalendarWorkConfirmationView from './CalendarWorkConfirmationView';

export type DailyRow = {
  id: number;
  work_date: string;
  morning_hours?: number | null;
  afternoon_hours?: number | null;
  overtime_hours?: number | null;
  night_hours?: number | null;
  total_hours?: number | null;
  work_content?: string | null;
  supplier_signed: boolean;
  bp_signed: boolean;
};

export type Monthly = {
  person_id: number;
  person_name: string;
  supplier_name?: string | null;
  bp_name?: string | null;
  year: number;
  month: number;
  total_days: number;
  morning_hours: number;
  afternoon_hours: number;
  overtime_hours: number;
  night_hours: number;
  total_hours: number;
  days: DailyRow[];
};

const now = new Date();

function h(v?: number | null): string {
  if (v == null || v === 0) return '-';
  return String(v);
}

export default function MonthlyWorkConfirmationPage() {
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [rows, setRows] = useState<Monthly[]>([]);
  const [loading, setLoading] = useState(false);
  const [expanded, setExpanded] = useState<number | null>(null);
  const [view, setView] = useState<'table' | 'calendar'>('table');
  const [calPersonId, setCalPersonId] = useState<number | null>(null);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    api.get<Monthly[]>('/api/work-confirmations/monthly', { params: { year, month } })
      .then((r) => { if (alive) setRows(r.data); })
      .catch(() => { if (alive) { toast.error('월별 작업확인서를 불러올 수 없습니다'); setRows([]); } })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [year, month]);

  const downloadPdf = async (m: Monthly) => {
    try {
      const res = await api.get('/api/work-confirmations/monthly/pdf', {
        params: { year, month, personId: m.person_id }, responseType: 'blob',
      });
      const url = URL.createObjectURL(res.data as Blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `월별작업확인서_${year}-${month}_${m.person_name}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      toast.error('PDF 생성 실패');
    }
  };

  const years = [now.getFullYear(), now.getFullYear() - 1, now.getFullYear() - 2];

  return (
    <AppShell breadcrumb={[{ label: '월별 작업확인서' }]}>
      <div className="mx-auto max-w-6xl px-6 py-8 space-y-5">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold">월별 작업확인서</h1>
            <p className="text-sm text-slate-500 mt-1">
              인원별로 한 달치 일별 작업확인서를 집계합니다. 인원 행을 펼치면 일별 내역, PDF 버튼으로 월별 확인서를 내려받습니다.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <select value={year} onChange={(e) => setYear(Number(e.target.value))} className="input h-9 text-sm">
              {years.map((y) => <option key={y} value={y}>{y}년</option>)}
            </select>
            <select value={month} onChange={(e) => setMonth(Number(e.target.value))} className="input h-9 text-sm">
              {Array.from({ length: 12 }, (_, i) => i + 1).map((mm) => <option key={mm} value={mm}>{mm}월</option>)}
            </select>
            <div className="ml-1 inline-flex overflow-hidden rounded-lg border border-slate-200">
              <button onClick={() => setView('table')}
                      className={`h-9 px-3 text-sm ${view === 'table' ? 'bg-brand-600 text-white' : 'bg-white text-slate-600'}`}>표</button>
              <button onClick={() => setView('calendar')}
                      className={`h-9 px-3 text-sm ${view === 'calendar' ? 'bg-brand-600 text-white' : 'bg-white text-slate-600'}`}>달력</button>
            </div>
          </div>
        </div>

        {loading ? (
          <div className="card p-8 text-center text-slate-400">불러오는 중…</div>
        ) : rows.length === 0 ? (
          <div className="card p-8 text-center text-sm text-slate-400">{year}년 {month}월 작업확인서가 없습니다.</div>
        ) : view === 'calendar' ? (
          (() => {
            const cur = rows.find((r) => r.person_id === (calPersonId ?? rows[0].person_id)) ?? rows[0];
            return (
              <div className="card p-4 space-y-3">
                <div className="flex items-center gap-2">
                  <span className="text-sm text-slate-600">인원</span>
                  <select className="input h-9 text-sm" value={cur.person_id}
                          onChange={(e) => setCalPersonId(Number(e.target.value))}>
                    {rows.map((m) => <option key={m.person_id} value={m.person_id}>{m.person_name}</option>)}
                  </select>
                  <button onClick={() => downloadPdf(cur)}
                          className="ml-auto rounded bg-slate-100 px-2.5 py-1 text-xs font-semibold hover:bg-slate-200">PDF</button>
                </div>
                <CalendarWorkConfirmationView m={cur} />
              </div>
            );
          })()
        ) : (
          <div className="card overflow-x-auto p-0">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-left text-slate-500">
                <tr>
                  <th className="px-3 py-2 font-semibold">인원</th>
                  <th className="px-3 py-2 font-semibold">소속</th>
                  <th className="px-3 py-2 font-semibold text-right">근무일</th>
                  <th className="px-3 py-2 font-semibold text-right">오전</th>
                  <th className="px-3 py-2 font-semibold text-right">오후</th>
                  <th className="px-3 py-2 font-semibold text-right">연장</th>
                  <th className="px-3 py-2 font-semibold text-right">야간</th>
                  <th className="px-3 py-2 font-semibold text-right">합계</th>
                  <th className="px-3 py-2"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {rows.map((m) => (
                  <PersonRow key={m.person_id} m={m}
                    open={expanded === m.person_id}
                    onToggle={() => setExpanded(expanded === m.person_id ? null : m.person_id)}
                    onPdf={() => downloadPdf(m)} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </AppShell>
  );
}

function PersonRow({ m, open, onToggle, onPdf }:
  { m: Monthly; open: boolean; onToggle: () => void; onPdf: () => void }) {
  return (
    <>
      <tr className="cursor-pointer hover:bg-slate-50" onClick={onToggle}>
        <td className="px-3 py-2 font-semibold">{m.person_name}</td>
        <td className="px-3 py-2 text-slate-600 text-xs">{m.supplier_name ?? '-'}</td>
        <td className="px-3 py-2 text-right tabular-nums">{m.total_days}일</td>
        <td className="px-3 py-2 text-right tabular-nums text-xs">{h(m.morning_hours)}</td>
        <td className="px-3 py-2 text-right tabular-nums text-xs">{h(m.afternoon_hours)}</td>
        <td className="px-3 py-2 text-right tabular-nums text-xs">{h(m.overtime_hours)}</td>
        <td className="px-3 py-2 text-right tabular-nums text-xs">{h(m.night_hours)}</td>
        <td className="px-3 py-2 text-right tabular-nums font-semibold">{h(m.total_hours)}</td>
        <td className="px-3 py-2 text-right" onClick={(e) => e.stopPropagation()}>
          <button onClick={onPdf} className="px-2 py-1 text-xs rounded bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold">PDF</button>
        </td>
      </tr>
      {open && (
        <tr className="bg-slate-50/60">
          <td colSpan={9} className="px-3 py-2">
            <table className="w-full text-xs">
              <thead className="text-slate-400">
                <tr>
                  <th className="px-2 py-1 text-left font-medium">일자</th>
                  <th className="px-2 py-1 text-right font-medium">오전</th>
                  <th className="px-2 py-1 text-right font-medium">오후</th>
                  <th className="px-2 py-1 text-right font-medium">연장</th>
                  <th className="px-2 py-1 text-right font-medium">야간</th>
                  <th className="px-2 py-1 text-right font-medium">일계</th>
                  <th className="px-2 py-1 text-left font-medium">작업내용</th>
                  <th className="px-2 py-1 text-center font-medium">사인</th>
                </tr>
              </thead>
              <tbody>
                {m.days.map((d) => (
                  <tr key={d.id} className="border-t border-slate-200">
                    <td className="px-2 py-1">{d.work_date}</td>
                    <td className="px-2 py-1 text-right tabular-nums">{h(d.morning_hours)}</td>
                    <td className="px-2 py-1 text-right tabular-nums">{h(d.afternoon_hours)}</td>
                    <td className="px-2 py-1 text-right tabular-nums">{h(d.overtime_hours)}</td>
                    <td className="px-2 py-1 text-right tabular-nums">{h(d.night_hours)}</td>
                    <td className="px-2 py-1 text-right tabular-nums font-semibold">{h(d.total_hours)}</td>
                    <td className="px-2 py-1 text-slate-600 max-w-[240px] truncate">{d.work_content ?? '-'}</td>
                    <td className="px-2 py-1 text-center text-[11px]">
                      {d.supplier_signed && d.bp_signed ? '완료' : d.supplier_signed ? '공급사' : d.bp_signed ? 'BP' : '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </td>
        </tr>
      )}
    </>
  );
}
