import { useCallback, useEffect, useMemo, useState } from 'react';
import axios, { AxiosError } from 'axios';

/**
 * 작업자 모바일웹 — "내 작업 달력"(재기획 §0-A #3 · §3.6.3).
 * 출근코드 로그인(field-auth) → X-Field-Token 저장. 점검원 페이지(InspectorPage) 공개라우트 패턴 재사용.
 * 정산주기(현장정산일 26~25) 단위 그리드로 근무일·OT·서명 상태를 한눈에. ◀▶ 주기 이동, 날짜 탭 → 상세.
 */

type SignStatus = 'UNSIGNED' | 'SIGNED' | 'PHOTO';
type WorkerDay = {
  id: number; work_date: string; work_content: string | null; work_location: string | null;
  site_name: string | null; rate_type: string;
  ot_early: number; ot_lunch: number; ot_evening: number; ot_night: number; ot_overnight: number;
  ot_total: number; start_time: string | null; end_time: string | null;
  sign_status: SignStatus; memo: string | null;
};
type WorkerCalendar = {
  period: string; cycle_start: string; cycle_end: string; settlement_day: number | null;
  days: WorkerDay[];
  totals: {
    work_days: number;
    ot_early_hours: number; ot_lunch_hours: number; ot_evening_hours: number;
    ot_night_hours: number; ot_overnight_hours: number; ot_total_hours: number;
    signed_count: number; photo_count: number; unsigned_count: number;
  };
};
type ListLog = {
  id: number; work_date: string; work_content: string | null; site_name: string | null;
  ot_total: number; sign_status: SignStatus;
};
type ListResp = { logs: ListLog[]; work_days: number; ot_total_hours: number };

const TOKEN_KEY = 'worker_token';
const NAME_KEY = 'worker_name';
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const OT_LABELS: [keyof WorkerDay, string][] = [
  ['ot_early', '조출'], ['ot_lunch', '점심'], ['ot_evening', '연장'], ['ot_night', '야간'], ['ot_overnight', '철야'],
];

const bare = () => axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL ?? '', timeout: 20_000 });
function msg(e: unknown, fallback: string) {
  return e instanceof AxiosError ? (e.response?.data?.message ?? fallback) : fallback;
}

// ── 날짜 헬퍼(로컬 기준, UTC 밀림 방지) ─────────────────────────────
function parseISO(s: string): Date { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); }
function toISO(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function ym(y: number, m0: number): string { return `${y}-${String(m0 + 1).padStart(2, '0')}`; }
function shiftMonth(period: string, delta: number): string {
  const [y, m] = period.split('-').map(Number);
  const d = new Date(y, m - 1 + delta, 1);
  return ym(d.getFullYear(), d.getMonth());
}
/** 날짜가 속한 정산주기의 앵커 기준월(YYYY-MM). settlementDay null 이면 달력월. */
function anchorForDate(dateISO: string, settlementDay: number | null): string {
  const d = parseISO(dateISO);
  if (settlementDay == null) return ym(d.getFullYear(), d.getMonth());
  const base = d.getDate() > settlementDay ? new Date(d.getFullYear(), d.getMonth() + 1, 1) : d;
  return ym(base.getFullYear(), base.getMonth());
}
function mdLabel(iso: string): string { const d = parseISO(iso); return `${d.getMonth() + 1}/${d.getDate()}`; }
function fmtH(n: number): string { return `${n % 1 === 0 ? n : n.toFixed(1)}h`; }
function rateLabel(rt: string): string { return rt === 'MONTHLY' ? '월대' : rt === 'DAILY' ? '일대' : rt; }
function signMeta(s: SignStatus): { icon: string; label: string } {
  if (s === 'SIGNED') return { icon: '✓', label: 'BP 서명' };
  if (s === 'PHOTO') return { icon: '📷', label: '전표 사진' };
  return { icon: '⏳', label: '미서명' };
}
/** 정산주기 시작 주(일요일)부터 종료일이 포함된 주까지 — 주기 단위 6주 그리드 셀. */
function buildGrid(cycleStart: string, cycleEnd: string): string[] {
  const start = parseISO(cycleStart);
  const end = parseISO(cycleEnd);
  const d = new Date(start); d.setDate(start.getDate() - start.getDay()); // 그 주 일요일로
  const cells: string[] = [];
  while (d <= end || cells.length % 7 !== 0) {
    cells.push(toISO(d));
    d.setDate(d.getDate() + 1);
    if (cells.length > 56) break; // 안전장치(최대 8주)
  }
  return cells;
}

export default function WorkerCalendarPage() {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY));
  const [name, setName] = useState<string | null>(() => localStorage.getItem(NAME_KEY));
  const [tab, setTab] = useState<'calendar' | 'list'>('calendar');

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(NAME_KEY);
    setToken(null); setName(null);
  }, []);

  const onLogin = (tok: string, nm: string) => {
    localStorage.setItem(TOKEN_KEY, tok);
    localStorage.setItem(NAME_KEY, nm);
    setToken(tok); setName(nm); setTab('calendar');
  };

  return (
    <div className="min-h-screen bg-slate-100">
      <header className="sticky top-0 z-10 flex items-center justify-between bg-blue-700 px-4 py-3 text-white shadow">
        <div className="flex items-center gap-2">
          <span className="text-lg">📅</span>
          <span className="text-sm font-bold">내 작업 달력</span>
        </div>
        {token && (
          <button onClick={logout} className="-my-2 inline-flex min-h-[44px] items-center text-xs text-blue-100 underline">{name ?? '작업자'} · 로그아웃</button>
        )}
      </header>

      <main className="mx-auto max-w-md px-4 py-4">
        {!token ? (
          <LoginView onLogin={onLogin} />
        ) : (
          <>
            <div className="mb-3 flex overflow-hidden rounded-xl border border-slate-200 bg-white text-sm font-semibold">
              <button onClick={() => setTab('calendar')}
                      className={`flex-1 min-h-[44px] py-2.5 ${tab === 'calendar' ? 'bg-blue-600 text-white' : 'text-slate-500'}`}>달력</button>
              <button onClick={() => setTab('list')}
                      className={`flex-1 min-h-[44px] py-2.5 ${tab === 'list' ? 'bg-blue-600 text-white' : 'text-slate-500'}`}>내역</button>
            </div>
            {tab === 'calendar' ? <CalendarView token={token} onExpired={logout} /> : <ListView token={token} onExpired={logout} />}
          </>
        )}
      </main>
    </div>
  );
}

// ── 로그인(출근코드) ───────────────────────────────────────────────
function LoginView({ onLogin }: { onLogin: (token: string, name: string) => void }) {
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const submit = async () => {
    if (!code.trim()) { setErr('출근코드를 입력하세요'); return; }
    setBusy(true); setErr(null);
    try {
      const { data } = await bare().post('/api/field-auth/auth', { code: code.trim() });
      onLogin(data.token, data.name ?? '작업자');
    } catch (e) {
      setErr(msg(e, '로그인 실패'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="card mt-8 space-y-4">
      <h1 className="text-lg font-bold text-slate-900">작업자 로그인</h1>
      <p className="text-xs text-slate-500">공급사에서 발급받은 <b>출근코드</b>를 입력하세요.</p>
      <input value={code} onChange={(e) => setCode(e.target.value.toUpperCase())}
             onKeyDown={(e) => e.key === 'Enter' && submit()}
             className="input w-full min-h-[44px] text-center text-lg tracking-widest" placeholder="출근코드" autoCapitalize="characters" />
      {err && <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">{err}</p>}
      <button onClick={submit} disabled={busy} className="btn-primary w-full min-h-[44px] disabled:opacity-50">
        {busy ? '로그인 중…' : '로그인'}
      </button>
    </div>
  );
}

// ── 달력(정산주기 그리드) ──────────────────────────────────────────
function CalendarView({ token, onExpired }: { token: string; onExpired: () => void }) {
  const [period, setPeriod] = useState<string | null>(null); // null = 오늘 주기(백엔드 해석)
  const [data, setData] = useState<WorkerCalendar | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    setErr(null);
    try {
      const { data } = await bare().get<WorkerCalendar>('/api/field-auth/work-calendar',
        { headers: { 'X-Field-Token': token }, params: period ? { period } : {} });
      setData(data);
    } catch (e) {
      if (e instanceof AxiosError && e.response?.status === 403) { onExpired(); return; }
      setErr(msg(e, '달력을 불러오지 못했습니다'));
    }
  }, [token, period, onExpired]);
  useEffect(() => { void load(); }, [load]);

  const byDate = useMemo(() => {
    const m = new Map<string, WorkerDay>();
    (data?.days ?? []).forEach((d) => m.set(d.work_date, d));
    return m;
  }, [data]);

  if (err) return <div className="card text-center text-sm text-red-600">{err}</div>;
  if (!data) return <p className="py-10 text-center text-sm text-slate-400">불러오는 중…</p>;

  const base = period ?? data.period;
  const cells = buildGrid(data.cycle_start, data.cycle_end);
  const inCycle = (iso: string) => iso >= data.cycle_start && iso <= data.cycle_end;

  const onCell = (iso: string) => {
    if (inCycle(iso)) { setSelected(iso); return; }
    // 주기 밖 날짜 탭 → 그 날짜가 속한 주기로 전환.
    const anchor = anchorForDate(iso, data.settlement_day);
    setSelected(iso);
    setPeriod(anchor);
  };
  const nav = (delta: number) => { setSelected(null); setPeriod(shiftMonth(base, delta)); };

  const selDay = selected ? byDate.get(selected) : undefined;

  return (
    <div className="space-y-3">
      {/* 주기 범위 + 이동 */}
      <div className="flex items-center justify-between rounded-xl bg-white px-2 py-2 shadow-sm">
        <button onClick={() => nav(-1)} className="rounded-lg px-3 py-2 text-lg text-slate-500 active:bg-slate-100">◀</button>
        <div className="text-center">
          <div className="text-[11px] font-medium text-slate-400">정산주기</div>
          <div className="text-base font-bold text-slate-900">{mdLabel(data.cycle_start)} ~ {mdLabel(data.cycle_end)}</div>
        </div>
        <button onClick={() => nav(1)} className="rounded-lg px-3 py-2 text-lg text-slate-500 active:bg-slate-100">▶</button>
      </div>

      {/* 달력 그리드 */}
      <div className="rounded-xl bg-white p-2 shadow-sm">
        <div className="grid grid-cols-7">
          {WEEKDAYS.map((w, i) => (
            <div key={w} className={`pb-1 text-center text-[11px] font-semibold ${i === 0 ? 'text-red-400' : i === 6 ? 'text-blue-400' : 'text-slate-400'}`}>{w}</div>
          ))}
          {cells.map((iso) => {
            const d = parseISO(iso);
            const day = byDate.get(iso);
            const within = inCycle(iso);
            const isSel = selected === iso;
            const sign = day ? signMeta(day.sign_status) : null;
            return (
              <button key={iso} onClick={() => onCell(iso)}
                      className={`relative m-0.5 aspect-square rounded-lg text-left ${
                        !within ? 'opacity-35' : ''} ${
                        day ? 'bg-blue-600 text-white' : 'bg-slate-50 text-slate-700'} ${
                        isSel ? 'ring-2 ring-amber-400' : ''}`}>
                <span className="absolute left-1 top-0.5 text-[11px] font-bold">{d.getDate()}</span>
                {sign && (
                  <span className="absolute right-0.5 top-0.5 text-[10px] leading-none">{sign.icon}</span>
                )}
                {day && day.ot_total > 0 && (
                  <span className="absolute bottom-0.5 left-1/2 -translate-x-1/2 rounded bg-white/25 px-1 text-[10px] font-bold leading-tight">
                    {fmtH(day.ot_total)}
                  </span>
                )}
              </button>
            );
          })}
        </div>
        <div className="mt-2 flex flex-wrap justify-center gap-x-3 gap-y-1 border-t border-slate-100 pt-2 text-[11px] text-slate-500">
          <span><span className="mr-1 inline-block h-2.5 w-2.5 rounded-sm bg-blue-600 align-middle" />근무일</span>
          <span>✓ 서명</span><span>📷 전표사진</span><span>⏳ 미서명</span>
        </div>
      </div>

      {/* 선택 날짜 상세 */}
      {selected && (
        selDay ? <DayDetail day={selDay} /> : (
          <div className="card text-center text-sm text-slate-400">{mdLabel(selected)} — 근무 기록이 없습니다</div>
        )
      )}

      {/* 주기 합계 */}
      <TotalsCard cal={data} />
    </div>
  );
}

// ── 선택 날짜 상세 카드 ────────────────────────────────────────────
function DayDetail({ day }: { day: WorkerDay }) {
  const sign = signMeta(day.sign_status);
  const otParts = OT_LABELS
    .map(([k, label]) => [label, Number(day[k])] as [string, number])
    .filter(([, v]) => v > 0);
  return (
    <div className="card space-y-2.5">
      <div className="flex items-center justify-between">
        <div className="text-base font-bold text-slate-900">{day.work_date} ({WEEKDAYS[parseISO(day.work_date).getDay()]})</div>
        <span className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${
          day.sign_status === 'SIGNED' ? 'bg-emerald-100 text-emerald-700'
          : day.sign_status === 'PHOTO' ? 'bg-blue-100 text-blue-700' : 'bg-amber-100 text-amber-700'}`}>
          {sign.icon} {sign.label}
        </span>
      </div>
      <DetailRow k="작업내용" v={day.work_content ?? '—'} />
      <DetailRow k="작업위치" v={day.work_location ?? '—'} />
      {day.site_name && <DetailRow k="현장" v={day.site_name} />}
      <DetailRow k="작업시간" v={day.start_time || day.end_time ? `${(day.start_time ?? '').slice(0, 5)} ~ ${(day.end_time ?? '').slice(0, 5)}` : '—'} />
      <DetailRow k="구분" v={rateLabel(day.rate_type)} />
      <div className="flex justify-between gap-2 text-sm">
        <span className="text-slate-500">OT</span>
        <span className="text-right font-medium text-slate-800">
          {otParts.length ? otParts.map(([l, v]) => `${l} ${fmtH(v)}`).join(' · ') : '없음'}
          {day.ot_total > 0 && <span className="ml-1 font-bold text-blue-700">(합계 {fmtH(day.ot_total)})</span>}
        </span>
      </div>
      {day.memo && <DetailRow k="메모" v={day.memo} />}
    </div>
  );
}
function DetailRow({ k, v }: { k: string; v: string }) {
  return (
    <div className="flex justify-between gap-3 text-sm">
      <span className="shrink-0 text-slate-500">{k}</span>
      <span className="text-right font-medium text-slate-800">{v}</span>
    </div>
  );
}

// ── 주기 합계 카드(큰 글씨 §0-A) ───────────────────────────────────
function TotalsCard({ cal }: { cal: WorkerCalendar }) {
  const t = cal.totals;
  const signedTotal = t.signed_count + t.photo_count + t.unsigned_count;
  const otParts: [string, number][] = [
    ['조출', t.ot_early_hours], ['점심', t.ot_lunch_hours], ['연장', t.ot_evening_hours],
    ['야간', t.ot_night_hours], ['철야', t.ot_overnight_hours],
  ].filter(([, v]) => (v as number) > 0) as [string, number][];
  return (
    <div className="card space-y-3 bg-slate-900 text-white">
      <div className="text-xs font-medium text-slate-300">이번 주기 합계 ({mdLabel(cal.cycle_start)} ~ {mdLabel(cal.cycle_end)})</div>
      <div className="grid grid-cols-3 gap-2 text-center">
        <div>
          <div className="text-2xl font-extrabold">{t.work_days}</div>
          <div className="text-[11px] text-slate-400">근무일</div>
        </div>
        <div>
          <div className="text-2xl font-extrabold text-blue-300">{fmtH(t.ot_total_hours)}</div>
          <div className="text-[11px] text-slate-400">OT 합계</div>
        </div>
        <div>
          <div className="text-2xl font-extrabold text-emerald-300">{t.signed_count + t.photo_count}<span className="text-base text-slate-400">/{signedTotal}</span></div>
          <div className="text-[11px] text-slate-400">서명완료</div>
        </div>
      </div>
      {otParts.length > 0 && (
        <div className="flex flex-wrap justify-center gap-1.5 border-t border-slate-700 pt-2.5">
          {otParts.map(([l, v]) => (
            <span key={l} className="rounded-full bg-slate-700 px-2.5 py-1 text-xs font-semibold">{l} {fmtH(v)}</span>
          ))}
        </div>
      )}
      {t.unsigned_count > 0 && (
        <div className="text-center text-[11px] text-amber-300">미서명 {t.unsigned_count}건</div>
      )}
    </div>
  );
}

// ── 내역 리스트(전체, 최근순) ──────────────────────────────────────
function ListView({ token, onExpired }: { token: string; onExpired: () => void }) {
  const [resp, setResp] = useState<ListResp | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      setErr(null);
      try {
        const { data } = await bare().get<ListResp>('/api/field-auth/daily-work-logs', { headers: { 'X-Field-Token': token } });
        setResp(data);
      } catch (e) {
        if (e instanceof AxiosError && e.response?.status === 403) { onExpired(); return; }
        setErr(msg(e, '내역을 불러오지 못했습니다'));
      }
    })();
  }, [token, onExpired]);

  if (err) return <div className="card text-center text-sm text-red-600">{err}</div>;
  if (!resp) return <p className="py-10 text-center text-sm text-slate-400">불러오는 중…</p>;
  if (resp.logs.length === 0) return <div className="card py-10 text-center text-sm text-slate-400">작업 기록이 없습니다.</div>;

  return (
    <div className="space-y-2">
      <div className="flex justify-between rounded-xl bg-white px-3 py-2 text-sm shadow-sm">
        <span className="text-slate-500">총 근무 <b className="text-slate-900">{resp.work_days}일</b></span>
        <span className="text-slate-500">OT 합계 <b className="text-blue-700">{fmtH(resp.ot_total_hours)}</b></span>
      </div>
      <ul className="space-y-2">
        {resp.logs.map((l) => {
          const sign = signMeta(l.sign_status);
          return (
            <li key={l.id} className="card flex items-center justify-between gap-2 py-3">
              <div className="min-w-0">
                <div className="text-sm font-bold text-slate-900">{l.work_date}</div>
                <div className="mt-0.5 truncate text-xs text-slate-500">
                  {l.work_content || '작업내용 미입력'}{l.site_name ? ` · ${l.site_name}` : ''}
                </div>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                {l.ot_total > 0 && <span className="rounded bg-blue-50 px-1.5 py-0.5 text-[11px] font-bold text-blue-700">OT {fmtH(l.ot_total)}</span>}
                <span className="text-sm" title={sign.label}>{sign.icon}</span>
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
