import { useMemo, useState } from 'react';
import type { Monthly, DailyRow } from './MonthlyWorkConfirmationPage';

const WD = ['일', '월', '화', '수', '목', '금', '토'];
const pad = (n: number) => String(n).padStart(2, '0');

/** 한 인원의 월을 달력(7열 그리드)으로. 날짜칸에 서명상태/시간, 클릭 시 일별 상세. */
export default function CalendarWorkConfirmationView({ m }: { m: Monthly }) {
  const [detail, setDetail] = useState<DailyRow | null>(null);
  const byDate = useMemo(() => {
    const map = new Map<string, DailyRow>();
    for (const d of m.days) map.set(d.work_date, d);
    return map;
  }, [m]);

  const startWeekday = new Date(m.year, m.month - 1, 1).getDay();
  const daysInMonth = new Date(m.year, m.month, 0).getDate();
  const cells: (number | null)[] = [];
  for (let i = 0; i < startWeekday; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);

  return (
    <div>
      <div className="grid grid-cols-7 border-b border-slate-200 text-center text-xs font-semibold">
        {WD.map((w, i) => (
          <div key={w} className={`py-2 ${i === 0 ? 'text-rose-500' : i === 6 ? 'text-blue-500' : 'text-slate-500'}`}>{w}</div>
        ))}
      </div>
      <div className="grid grid-cols-7">
        {cells.map((d, idx) => {
          if (d == null) return <div key={idx} className="min-h-[68px] border-b border-r border-slate-100 bg-slate-50/40" />;
          const wd = idx % 7;
          const row = byDate.get(`${m.year}-${pad(m.month)}-${pad(d)}`);
          const both = !!row && row.supplier_signed && row.bp_signed;
          const one = !!row && (row.supplier_signed || row.bp_signed);
          return (
            <button key={idx} type="button" disabled={!row} onClick={() => row && setDetail(row)}
              className={`min-h-[68px] border-b border-r border-slate-100 p-1.5 text-left align-top ${row ? 'cursor-pointer hover:bg-brand-50' : 'cursor-default'}`}>
              <div className={`text-xs ${wd === 0 ? 'text-rose-500' : wd === 6 ? 'text-blue-500' : 'text-slate-600'}`}>{d}</div>
              {row && (
                <div className="mt-1 space-y-0.5">
                  <span className={`inline-block rounded px-1 text-[10px] font-semibold ${
                    both ? 'bg-emerald-100 text-emerald-700' : one ? 'bg-amber-100 text-amber-700' : 'bg-slate-100 text-slate-600'
                  }`}>
                    {both ? '완료' : one ? '서명중' : '대기'}
                  </span>
                  {row.total_hours != null && row.total_hours > 0 && (
                    <div className="text-[11px] font-semibold text-slate-700">{row.total_hours}h</div>
                  )}
                </div>
              )}
            </button>
          );
        })}
      </div>
      <div className="flex flex-wrap gap-x-4 gap-y-1 border-t border-slate-200 px-2 py-2 text-xs text-slate-600">
        <span>근무 <b className="text-slate-900">{m.total_days}</b>일</span>
        <span>합계 <b className="text-slate-900">{m.total_hours ?? 0}</b>h</span>
        <span className="text-slate-400">오전 {m.morning_hours || 0} · 오후 {m.afternoon_hours || 0} · 연장 {m.overtime_hours || 0} · 야간 {m.night_hours || 0}</span>
      </div>

      {detail && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={() => setDetail(null)}>
          <div className="w-full max-w-sm rounded-xl bg-white shadow-xl" onClick={(e) => e.stopPropagation()}>
            <div className="border-b px-5 py-3 font-bold text-slate-900">{detail.work_date} · {m.person_name}</div>
            <div className="space-y-2 px-5 py-4 text-sm">
              <DRow label="오전" v={detail.morning_hours} />
              <DRow label="오후" v={detail.afternoon_hours} />
              <DRow label="연장" v={detail.overtime_hours} />
              <DRow label="야간" v={detail.night_hours} />
              <div className="flex justify-between border-t pt-2 font-semibold"><span>일계</span><span>{detail.total_hours ?? 0}h</span></div>
              {detail.work_content && <div className="pt-1 text-slate-600">{detail.work_content}</div>}
              <div className="flex gap-3 pt-1 text-xs">
                <span className={detail.supplier_signed ? 'text-emerald-600' : 'text-slate-400'}>공급사 {detail.supplier_signed ? '서명✓' : '미서명'}</span>
                <span className={detail.bp_signed ? 'text-emerald-600' : 'text-slate-400'}>BP {detail.bp_signed ? '서명✓' : '미서명'}</span>
              </div>
            </div>
            <div className="border-t px-5 py-3 text-right">
              <button onClick={() => setDetail(null)} className="rounded px-3 py-1.5 text-sm hover:bg-slate-100">닫기</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function DRow({ label, v }: { label: string; v?: number | null }) {
  return (
    <div className="flex justify-between">
      <span className="text-slate-500">{label}</span>
      <span>{v && v > 0 ? `${v}h` : '-'}</span>
    </div>
  );
}
