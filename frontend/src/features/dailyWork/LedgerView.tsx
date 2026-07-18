import { OT_COLS, eachDate, money, type Ledger, type LedgerRow, type SignStatus } from './types';

/** 월간 작업확인 원장 — test/작업확인서 월간.png 레이아웃 그대로. 빈 날짜행까지 그린다. */
export default function LedgerView({ ledger }: { ledger: Ledger }) {
  const byDate = new Map<string, LedgerRow>();
  ledger.rows.forEach((r) => byDate.set(r.work_date, r));
  const dates = eachDate(ledger.start_date, ledger.end_date);
  const t = ledger.totals;
  const notes = ledger.rows.filter((r) => r.memo && r.memo.trim()).map((r) => `${md(r.work_date)} ${r.memo}`);

  return (
    <div className="card p-4 sm:p-6 space-y-4">
      {/* 헤더 */}
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-200 pb-3">
        <div className="space-y-0.5 text-sm">
          <div className="text-lg font-bold text-slate-900">
            {ledger.period} 작업확인 원장
          </div>
          <div className="text-slate-600"><span className="text-slate-400">공사명</span> {ledger.site_name ?? '현장 미지정'}</div>
          {ledger.equipment_label && (
            <div className="text-slate-600"><span className="text-slate-400">장비번호</span> {ledger.equipment_label}</div>
          )}
          {ledger.person_name && (
            <div className="text-slate-600"><span className="text-slate-400">운전원</span> {ledger.person_name}</div>
          )}
          <div className="text-xs text-slate-400">
            정산주기 {ledger.start_date} ~ {ledger.end_date}
            {ledger.settlement_day != null ? ` (현장정산일 ${ledger.settlement_day}일)` : ' (달력월)'}
          </div>
        </div>
      </div>

      {/* 표 — 데스크톱 표 + 모바일 가로스크롤 */}
      <div className="overflow-x-auto -mx-4 sm:mx-0 px-4 sm:px-0">
        <table className="min-w-[640px] w-full text-sm border-collapse">
          <thead>
            <tr className="bg-slate-50 text-slate-600">
              <th className="border border-slate-200 px-2 py-1.5 font-semibold w-24">날짜</th>
              <th className="border border-slate-200 px-2 py-1.5 font-semibold text-left">작업내용</th>
              {OT_COLS.map((c) => (
                <th key={c.key} className="border border-slate-200 px-1.5 py-1.5 font-semibold w-16">
                  <div>{c.label}</div>
                  <div className="text-[10px] font-normal text-slate-400">{c.time}</div>
                </th>
              ))}
              <th className="border border-slate-200 px-2 py-1.5 font-semibold w-16">확인</th>
            </tr>
          </thead>
          <tbody>
            {dates.map((d) => {
              const r = byDate.get(d);
              return (
                <tr key={d} className={r ? '' : 'text-slate-300'}>
                  <td className="border border-slate-200 px-2 py-1 text-center tabular-nums whitespace-nowrap">{md(d)}</td>
                  <td className="border border-slate-200 px-2 py-1 text-slate-700">{r?.work_content ?? ''}</td>
                  {OT_COLS.map((c) => {
                    const v = r ? (r[`ot_${c.key}` as keyof LedgerRow] as number) : 0;
                    return (
                      <td key={c.key} className="border border-slate-200 px-1.5 py-1 text-center tabular-nums">
                        {v ? v : ''}
                      </td>
                    );
                  })}
                  <td className="border border-slate-200 px-2 py-1 text-center">
                    {r ? <SignMark s={r.sign_status} /> : ''}
                  </td>
                </tr>
              );
            })}
          </tbody>
          <tfoot>
            <tr className="bg-slate-50 font-semibold text-slate-800">
              <td className="border border-slate-200 px-2 py-1.5 text-center">합계</td>
              <td className="border border-slate-200 px-2 py-1.5">근무 {t.work_days}일</td>
              {OT_COLS.map((c) => {
                const h = t[`ot_${c.key}_hours` as keyof typeof t] as number;
                return (
                  <td key={c.key} className="border border-slate-200 px-1.5 py-1.5 text-center tabular-nums">
                    {h ? h : ''}
                  </td>
                );
              })}
              <td className="border border-slate-200 px-2 py-1.5" />
            </tr>
          </tfoot>
        </table>
      </div>

      {/* 특기사항 */}
      {notes.length > 0 && (
        <div className="text-xs text-slate-500">
          <div className="font-semibold text-slate-600 mb-1">※ 특기사항</div>
          <ul className="space-y-0.5">
            {notes.map((n, i) => <li key={i}>· {n}</li>)}
          </ul>
        </div>
      )}

      {/* 금액 합계 — 계약 연결 시 */}
      <div className="rounded-lg bg-slate-50 p-4">
        {ledger.contract_id ? (
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
            <Sum label={`기본단가 (${ledger.rate_type === 'DAILY' ? '일대' : '월대'})`} value={money(ledger.base_rate)} sub />
            <Sum label={`기본 금액 (근무 ${t.work_days}일)`} value={money(t.base_amount)} />
            <Sum label="OT 금액" value={money(t.ot_amount)} />
            <Sum label="합계" value={money(t.total_amount)} strong />
          </div>
        ) : (
          <div className="text-xs text-slate-500">
            계약이 연결되지 않아 금액은 표시되지 않습니다. 일일 확인서에 계약을 연결하면 분류별 단가로 금액이 계산됩니다.
            <div className="mt-1 font-semibold text-slate-700">근무 {t.work_days}일 · OT 합계 {otSum(t)}시간</div>
          </div>
        )}
      </div>
    </div>
  );
}

function SignMark({ s }: { s: SignStatus }) {
  if (s === 'SIGNED') return <span className="text-emerald-600 font-bold">✓</span>;
  if (s === 'PHOTO') return <span className="text-blue-600" title="전표 사진 갈음">📷</span>;
  return <span className="text-slate-300">–</span>;
}

function Sum({ label, value, strong, sub }: { label: string; value: string; strong?: boolean; sub?: boolean }) {
  return (
    <div>
      <div className="text-[11px] text-slate-400">{label}</div>
      <div className={`tabular-nums ${strong ? 'text-lg font-bold text-brand-700' : sub ? 'text-sm text-slate-600' : 'text-base font-semibold text-slate-900'}`}>{value}</div>
    </div>
  );
}

function md(d: string): string {
  const [, m, day] = d.split('-');
  return `${Number(m)}/${Number(day)}`;
}

function otSum(t: Ledger['totals']): number {
  return t.ot_early_hours + t.ot_lunch_hours + t.ot_evening_hours + t.ot_night_hours + t.ot_overnight_hours;
}
