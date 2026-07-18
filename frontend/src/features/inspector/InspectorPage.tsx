import { useCallback, useEffect, useMemo, useState } from 'react';
import axios, { AxiosError } from 'axios';
import { useSignaturePad } from '../workPlan/create/components/useSignaturePad';

/**
 * 안전점검원(안전점검회사 소속) 모바일웹 — S2′ 법정점검(NFC 강제).
 * 점검원 자가로그인(username/password → X-Field-Token) 재사용. 조종원 일일점검과 별도 트랙.
 * 흐름: 로그인 → 오늘 점검대상 → NFC 태그(장비 검증) → 체크리스트+서명 → 저장(증거요약).
 */

type Item = { no: number; text: string; required: boolean };
type Template = { id: number; name: string; items: Item[] };
type Target = {
  equipment_id: number; vehicle_no: string | null; model: string | null; category: string | null;
  site_id: number | null; site_name: string | null; has_nfc_tag: boolean; done_today: boolean;
};
type OpenResp = {
  open_token: string; tag_read_at: string; tag_verified: boolean;
  equipment_id: number; equipment_label: string; template: Template;
};
type Evidence = {
  id: number; equipment_id: number; inspect_date: string; tag_read_at: string;
  tag_verified: boolean; inspector_name: string; signed: boolean; created_at: string;
};
type ItemResult = { checked: boolean; na: boolean; note: string };

const TOKEN_KEY = 'inspector_token';
const NAME_KEY = 'inspector_name';
// 검수/개발용 수동 tagId 폴백 노출 플래그 — 일반 점검원에겐 미노출(NFC 강제 원칙).
const DEV_FLAG = 'inspector_dev';

const bare = () => axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL ?? '', timeout: 20_000 });
function msg(e: unknown, fallback: string) {
  return e instanceof AxiosError ? (e.response?.data?.message ?? fallback) : fallback;
}

export default function InspectorPage() {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY));
  const [name, setName] = useState<string | null>(() => localStorage.getItem(NAME_KEY));
  const [open, setOpen] = useState<OpenResp | null>(null);
  const [evidence, setEvidence] = useState<Evidence | null>(null);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(NAME_KEY);
    setToken(null); setName(null); setOpen(null); setEvidence(null);
  }, []);

  const onLogin = (tok: string, nm: string) => {
    localStorage.setItem(TOKEN_KEY, tok);
    localStorage.setItem(NAME_KEY, nm);
    setToken(tok); setName(nm);
  };

  return (
    <div className="min-h-screen bg-slate-100">
      <header className="sticky top-0 z-10 flex items-center justify-between bg-indigo-700 px-4 py-3 text-white shadow">
        <div className="flex items-center gap-2">
          <span className="text-lg">🛡️</span>
          <span className="text-sm font-bold">법정점검 (안전점검원)</span>
        </div>
        {token && (
          <button onClick={logout} className="text-xs text-indigo-100 underline">{name ?? '점검원'} · 로그아웃</button>
        )}
      </header>

      <main className="mx-auto max-w-md px-4 py-4">
        {!token ? (
          <LoginView onLogin={onLogin} />
        ) : evidence ? (
          <DoneView evidence={evidence} onBack={() => { setEvidence(null); setOpen(null); }} />
        ) : open ? (
          <ChecklistView token={token} open={open} onDone={setEvidence} onCancel={() => setOpen(null)} />
        ) : (
          <TargetsView token={token} onLogout={logout} onOpened={setOpen} />
        )}
      </main>
    </div>
  );
}

// ── 로그인 ────────────────────────────────────────────────────────
function LoginView({ onLogin }: { onLogin: (token: string, name: string) => void }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const submit = async () => {
    if (!username.trim() || !password.trim()) { setErr('아이디/비밀번호를 입력하세요'); return; }
    setBusy(true); setErr(null);
    try {
      const { data } = await bare().post('/api/field-auth/login', { username: username.trim(), password });
      onLogin(data.token, data.name ?? '점검원');
    } catch (e) {
      setErr(msg(e, '로그인 실패'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="card mt-8 space-y-4">
      <h1 className="text-lg font-bold text-slate-900">점검원 로그인</h1>
      <p className="text-xs text-slate-500">안전점검회사에서 발급받은 아이디로 로그인하세요.</p>
      <input value={username} onChange={(e) => setUsername(e.target.value)} className="input w-full" placeholder="아이디" autoCapitalize="none" />
      <input value={password} onChange={(e) => setPassword(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && submit()}
             type="password" className="input w-full" placeholder="비밀번호" />
      {err && <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">{err}</p>}
      <button onClick={submit} disabled={busy} className="btn-primary w-full disabled:opacity-50">
        {busy ? '로그인 중…' : '로그인'}
      </button>
    </div>
  );
}

// ── 오늘 점검 대상 ─────────────────────────────────────────────────
function TargetsView({ token, onLogout, onOpened }: {
  token: string; onLogout: () => void; onOpened: (o: OpenResp) => void;
}) {
  const [targets, setTargets] = useState<Target[] | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [siteFilter, setSiteFilter] = useState<number | 'all'>('all');
  const [tagging, setTagging] = useState<Target | null>(null);

  const load = useCallback(async () => {
    setErr(null);
    try {
      const { data } = await bare().get<Target[]>('/api/field-auth/inspector/targets', { headers: { 'X-Field-Token': token } });
      setTargets(data);
    } catch (e) {
      if (e instanceof AxiosError && e.response?.status === 403) setErr('NOT_INSPECTOR');
      else setErr(msg(e, '목록을 불러오지 못했습니다'));
      setTargets([]);
    }
  }, [token]);
  useEffect(() => { void load(); }, [load]);

  const sites = useMemo(() => {
    const m = new Map<number, string>();
    (targets ?? []).forEach((t) => { if (t.site_id) m.set(t.site_id, t.site_name ?? `현장 #${t.site_id}`); });
    return Array.from(m.entries());
  }, [targets]);

  const visible = (targets ?? []).filter((t) => siteFilter === 'all' || t.site_id === siteFilter);
  const doneCount = visible.filter((t) => t.done_today).length;

  if (err === 'NOT_INSPECTOR') {
    return (
      <div className="card mt-8 space-y-3 text-center">
        <p className="text-sm text-slate-700">이 계정은 <b>점검원(INSPECTOR)</b> 권한이 없습니다.</p>
        <button onClick={onLogout} className="btn-primary w-full">다른 계정으로 로그인</button>
      </div>
    );
  }
  if (tagging) {
    return <TagView token={token} target={tagging} onOpened={onOpened} onBack={() => setTagging(null)} />;
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h1 className="text-base font-bold text-slate-900">오늘 점검 대상</h1>
        {visible.length > 0 && (
          <span className="text-xs font-semibold text-slate-500">{doneCount}/{visible.length} 완료</span>
        )}
      </div>
      {sites.length > 1 && (
        <select value={siteFilter} onChange={(e) => setSiteFilter(e.target.value === 'all' ? 'all' : Number(e.target.value))}
                className="input w-full bg-white text-sm">
          <option value="all">전체 현장</option>
          {sites.map(([id, nm]) => <option key={id} value={id}>{nm}</option>)}
        </select>
      )}
      {err && err !== 'NOT_INSPECTOR' && <p className="text-sm text-red-600">{err}</p>}
      {targets === null ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : visible.length === 0 ? (
        <div className="card py-10 text-center text-sm text-slate-400">배치된 점검 대상 장비가 없습니다.</div>
      ) : (
        <ul className="space-y-2">
          {visible.map((t) => (
            <li key={t.equipment_id}>
              <button disabled={!t.has_nfc_tag || t.done_today} onClick={() => setTagging(t)}
                      className={`card flex w-full items-center justify-between gap-3 py-3 text-left ${(!t.has_nfc_tag || t.done_today) ? 'opacity-60' : 'hover:border-indigo-300'}`}>
                <div className="min-w-0">
                  <div className="truncate text-sm font-bold text-slate-900">{t.vehicle_no || t.model || `장비 #${t.equipment_id}`}</div>
                  <div className="mt-0.5 truncate text-xs text-slate-500">
                    {t.category ?? ''}{t.site_name ? ` · ${t.site_name}` : ''}
                  </div>
                </div>
                {t.done_today ? (
                  <span className="shrink-0 rounded-full bg-emerald-100 px-2 py-1 text-[11px] font-bold text-emerald-700">완료</span>
                ) : !t.has_nfc_tag ? (
                  <span className="shrink-0 rounded-full bg-slate-100 px-2 py-1 text-[11px] font-medium text-slate-400">NFC 미등록</span>
                ) : (
                  <span className="shrink-0 rounded-full bg-indigo-100 px-2 py-1 text-[11px] font-bold text-indigo-700">점검 →</span>
                )}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ── NFC 태그 ──────────────────────────────────────────────────────
function TagView({ token, target, onOpened, onBack }: {
  token: string; target: Target; onOpened: (o: OpenResp) => void; onBack: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [scanning, setScanning] = useState(false);
  const [manualTag, setManualTag] = useState('');
  const nfcSupported = typeof window !== 'undefined' && 'NDEFReader' in window;
  const showManual = localStorage.getItem(DEV_FLAG) === '1';

  const doOpen = async (tagId: string, source: 'NFC' | 'MANUAL') => {
    setBusy(true); setErr(null);
    try {
      const { data } = await bare().post<OpenResp>('/api/field-auth/inspector/open',
        { equipment_id: target.equipment_id, tag_id: tagId, source },
        { headers: { 'X-Field-Token': token } });
      onOpened(data);
    } catch (e) {
      setErr(msg(e, '태그 확인 실패'));
    } finally {
      setBusy(false); setScanning(false);
    }
  };

  const startNfc = async () => {
    if (!nfcSupported) { setErr('이 기기/브라우저는 NFC 를 지원하지 않습니다. 안드로이드 Chrome 에서 태그하세요.'); return; }
    setErr(null); setScanning(true);
    try {
      const reader = new (window as any).NDEFReader();
      await reader.scan();
      reader.onreading = (ev: any) => { void doOpen(String(ev.serialNumber || ''), 'NFC'); };
      reader.onreadingerror = () => { setErr('태그를 읽지 못했습니다. 다시 시도하세요.'); setScanning(false); };
    } catch (e) {
      setErr('NFC 스캔을 시작할 수 없습니다(권한/지원 확인).'); setScanning(false);
    }
  };

  return (
    <div className="space-y-4">
      <button onClick={onBack} className="text-xs text-slate-500">← 목록</button>
      <div className="card space-y-1 text-center">
        <div className="text-lg font-bold text-slate-900">{target.vehicle_no || target.model || `장비 #${target.equipment_id}`}</div>
        <div className="text-xs text-slate-500">{target.category ?? ''}{target.site_name ? ` · ${target.site_name}` : ''}</div>
      </div>

      <div className="card space-y-3 text-center">
        <div className="text-4xl">📶</div>
        <p className="text-sm text-slate-600">장비의 <b>NFC 태그</b>를 휴대폰으로 태그해야 점검을 시작할 수 있습니다.<br />(현장 방문 증명 — 태그 시각·단말이 기록됩니다)</p>
        <button onClick={startNfc} disabled={busy || scanning} className="btn-primary w-full disabled:opacity-50">
          {scanning ? 'NFC 태그 대기 중… (장비에 갖다 대세요)' : '📲 NFC 태그하여 점검 시작'}
        </button>
        {!nfcSupported && (
          <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700">
            NFC 지원 기기(안드로이드 Chrome)에서 태그하세요. 이 화면에서는 태그를 읽을 수 없습니다.
          </p>
        )}
        {err && <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">{err}</p>}
      </div>

      {showManual && (
        <div className="card space-y-2 border-dashed">
          <p className="text-[11px] font-semibold text-slate-400">검수/개발용 수동 입력 (tag_verified=false 로 기록)</p>
          <div className="flex gap-2">
            <input value={manualTag} onChange={(e) => setManualTag(e.target.value)} className="input flex-1 text-sm" placeholder="tag id" />
            <button onClick={() => manualTag.trim() && doOpen(manualTag.trim(), 'MANUAL')} disabled={busy}
                    className="rounded-lg border border-slate-300 px-3 text-sm font-semibold text-slate-700 disabled:opacity-50">확인</button>
          </div>
        </div>
      )}
    </div>
  );
}

// ── 체크리스트 + 서명 ──────────────────────────────────────────────
function ChecklistView({ token, open, onDone, onCancel }: {
  token: string; open: OpenResp; onDone: (e: Evidence) => void; onCancel: () => void;
}) {
  const items = open.template.items ?? [];
  const [results, setResults] = useState<Record<number, ItemResult>>({});
  const [memo, setMemo] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const { canvasRef, hasInk, handlers, clear, getDataUrl } = useSignaturePad(true);

  const setRes = (no: number, patch: Partial<ItemResult>) =>
    setResults((prev) => {
      const base: ItemResult = prev[no] ?? { checked: false, na: false, note: '' };
      return { ...prev, [no]: { ...base, ...patch } };
    });

  const requiredDone = items.filter((it) => it.required).every((it) => {
    const r = results[it.no];
    return r && (r.checked || r.na);
  });

  const submit = async () => {
    setBusy(true); setErr(null);
    try {
      const itemsResult = items.map((it) => {
        const r = results[it.no] ?? { checked: false, na: false, note: '' };
        return { no: it.no, checked: r.checked, na: r.na, note: r.note.trim() || null };
      });
      const { data } = await bare().post<Evidence>('/api/field-auth/inspector/submit',
        { open_token: open.open_token, items_result: itemsResult, sign_png_base64: getDataUrl(), memo: memo.trim() || null },
        { headers: { 'X-Field-Token': token } });
      onDone(data);
    } catch (e) {
      setErr(msg(e, '제출 실패'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-3">
      <button onClick={onCancel} className="text-xs text-slate-500">← 취소</button>
      <div className="card flex items-center justify-between">
        <div className="text-sm font-bold text-slate-900">{open.equipment_label}</div>
        <span className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${open.tag_verified ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'}`}>
          {open.tag_verified ? 'NFC 태그 확인' : '수동(검수)'}
        </span>
      </div>
      <div className="text-xs text-slate-500">태그 시각 {new Date(open.tag_read_at).toLocaleString('ko-KR')}</div>

      <ol className="space-y-2">
        {items.map((it) => {
          const r = results[it.no] ?? { checked: false, na: false, note: '' };
          return (
            <li key={it.no} className="card space-y-2 py-3">
              <div className="flex gap-1.5 text-sm text-slate-800">
                <span className="shrink-0 font-bold text-slate-400">{it.no}.</span>
                <span>{it.text}{it.required && <span className="text-rose-500"> *</span>}</span>
              </div>
              <div className="flex gap-2">
                <button onClick={() => setRes(it.no, { checked: true, na: false })}
                        className={`flex-1 rounded-lg py-2 text-sm font-semibold ${r.checked ? 'bg-emerald-600 text-white' : 'bg-slate-100 text-slate-600'}`}>확인</button>
                <button onClick={() => setRes(it.no, { checked: false, na: true })}
                        className={`flex-1 rounded-lg py-2 text-sm font-semibold ${r.na ? 'bg-slate-500 text-white' : 'bg-slate-100 text-slate-600'}`}>N/A</button>
              </div>
              <input value={r.note} onChange={(e) => setRes(it.no, { note: e.target.value })}
                     className="input w-full text-xs" placeholder="특이사항 (선택)" />
            </li>
          );
        })}
      </ol>

      <label className="block">
        <span className="text-xs font-semibold text-slate-500">종합 특이사항</span>
        <textarea value={memo} onChange={(e) => setMemo(e.target.value)} className="input mt-1 w-full text-sm" rows={2} />
      </label>

      <div className="card space-y-2">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold text-slate-500">점검원 서명 <span className="text-rose-500">*</span></span>
          <button onClick={clear} className="text-[11px] text-slate-500 underline">지우기</button>
        </div>
        <canvas ref={canvasRef} width={520} height={200}
                className="h-[200px] w-full touch-none rounded-lg border border-slate-300 bg-white" {...handlers} />
      </div>

      {err && <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">{err}</p>}
      {!requiredDone && <p className="text-center text-xs text-amber-600">필수 항목(*)을 모두 확인 또는 N/A 하세요.</p>}
      <button onClick={submit} disabled={busy || !requiredDone || !hasInk} className="btn-primary w-full disabled:opacity-50">
        {busy ? '저장 중…' : '점검 완료 · 저장'}
      </button>
    </div>
  );
}

// ── 완료(증거요약) ─────────────────────────────────────────────────
function DoneView({ evidence, onBack }: { evidence: Evidence; onBack: () => void }) {
  return (
    <div className="card mt-6 space-y-3 text-center">
      <div className="text-5xl">✅</div>
      <h1 className="text-lg font-bold text-slate-900">법정점검이 기록되었습니다</h1>
      <dl className="space-y-1.5 rounded-lg bg-slate-50 p-3 text-left text-sm">
        <Row k="점검원" v={evidence.inspector_name} />
        <Row k="점검일" v={evidence.inspect_date} />
        <Row k="태그 시각" v={new Date(evidence.tag_read_at).toLocaleString('ko-KR')} />
        <Row k="태그 검증" v={evidence.tag_verified ? 'NFC 실태그 확인' : '수동 입력(검수)'} />
        <Row k="서명" v={evidence.signed ? '완료' : '없음'} />
      </dl>
      <button onClick={onBack} className="btn-primary w-full">목록으로</button>
    </div>
  );
}

function Row({ k, v }: { k: string; v: string }) {
  return (
    <div className="flex justify-between gap-2">
      <dt className="text-slate-500">{k}</dt>
      <dd className="font-medium text-slate-800">{v}</dd>
    </div>
  );
}
