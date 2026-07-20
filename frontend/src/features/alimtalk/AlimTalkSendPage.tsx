import { useEffect, useMemo, useState } from 'react';
import AppShell from '../../components/layout/AppShell';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';

type TemplateInfo = { name: string; code: string; label: string; content: string };
type SendResult = { phone: string; status: string; provider: string | null; message: string | null };
type LogRow = { id: number; phone: string; purpose: string; status: string; provider: string; created_at: string };

const VAR_RE = /#\{([^}]+)\}/g;
const normalize = (s: string) => s.replace(/\s+/g, '').trim();

function extractVars(content: string): string[] {
  const out: string[] = [];
  let m: RegExpExecArray | null;
  VAR_RE.lastIndex = 0;
  while ((m = VAR_RE.exec(content)) !== null) {
    if (!out.includes(m[1])) out.push(m[1]);
  }
  return out;
}

/** 독립 알림톡 발송 — 견적/점검 흐름과 별개로 템플릿을 골라 직접 발송. */
export default function AlimTalkSendPage() {
  const [templates, setTemplates] = useState<TemplateInfo[]>([]);
  const [selected, setSelected] = useState<string>('');
  const [vars, setVars] = useState<Record<string, string>>({});
  const [input, setInput] = useState('');
  const [phones, setPhones] = useState<string[]>([]);
  const [sending, setSending] = useState(false);
  const [results, setResults] = useState<SendResult[]>([]);
  const [logs, setLogs] = useState<LogRow[]>([]);

  const loadLogs = () => {
    api.get<LogRow[]>('/api/alimtalk/logs').then((r) => setLogs(r.data)).catch(() => {});
  };

  useEffect(() => {
    api.get<TemplateInfo[]>('/api/alimtalk/templates')
      .then((r) => { setTemplates(r.data); if (r.data[0]) setSelected(r.data[0].name); })
      .catch(() => toast.error('템플릿 조회 실패'));
    loadLogs();
  }, []);

  const tpl = useMemo(() => templates.find((t) => t.name === selected), [templates, selected]);
  const varNames = useMemo(() => (tpl ? extractVars(tpl.content) : []), [tpl]);

  // 템플릿 바뀌면 변수 초기화 (브랜드명=SKEP 기본, 나머지 빈값)
  useEffect(() => {
    if (!tpl) return;
    const next: Record<string, string> = {};
    extractVars(tpl.content).forEach((v) => { next[v] = v === '브랜드명' ? 'SKEP' : ''; });
    setVars(next);
    setResults([]);
  }, [tpl]);

  const preview = useMemo(() => {
    if (!tpl) return '';
    return tpl.content.replace(VAR_RE, (_full, k) => {
      if (k === '요청일시' && !vars[k]) return '(발송 시각 자동)';
      return vars[k] ?? `#{${k}}`;
    });
  }, [tpl, vars]);

  const addPhone = () => {
    const p = normalize(input);
    if (p && !phones.includes(p)) setPhones([...phones, p]);
    setInput('');
  };

  const send = async () => {
    if (!tpl || phones.length === 0) { toast.error('수신번호를 입력하세요'); return; }
    setSending(true);
    try {
      const r = await api.post<SendResult[]>('/api/alimtalk/send', { template: tpl.name, vars, phones });
      setResults(r.data);
      const ok = r.data.filter((x) => x.status === 'SENT').length;
      toast.success(`발송 완료 — 성공 ${ok}/${r.data.length}`);
      loadLogs();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '발송 실패');
    } finally {
      setSending(false);
    }
  };

  return (
    <AppShell>
      <div className="max-w-3xl mx-auto p-4 space-y-4">
        <div>
          <h1 className="text-lg font-bold text-slate-900">알림톡 발송</h1>
          <p className="text-sm text-slate-500 mt-0.5">템플릿을 고르고 내용을 채워 카카오 알림톡(실패 시 SMS)을 직접 발송합니다.</p>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-4 space-y-3">
          <label className="block">
            <span className="text-xs font-semibold text-slate-500">템플릿</span>
            <select value={selected} onChange={(e) => setSelected(e.target.value)}
                    className="mt-1 w-full px-2.5 py-1.5 text-sm border border-slate-300 rounded">
              {templates.map((t) => <option key={t.name} value={t.name}>{t.label} ({t.code})</option>)}
            </select>
          </label>

          {varNames.length > 0 && (
            <div className="grid grid-cols-2 gap-2">
              {varNames.map((v) => (
                <label key={v} className="block">
                  <span className="text-xs font-semibold text-slate-500">{v}{v === '요청일시' ? ' (비우면 자동)' : ''}</span>
                  <input value={vars[v] ?? ''} onChange={(e) => setVars({ ...vars, [v]: e.target.value })}
                         placeholder={v === '요청일시' ? '자동(현재시각)' : ''}
                         className="mt-1 w-full px-2.5 py-1.5 text-sm border border-slate-300 rounded" />
                </label>
              ))}
            </div>
          )}
        </div>

        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-xs font-semibold text-slate-500 mb-1">미리보기</div>
          <pre className="whitespace-pre-wrap text-sm text-slate-800 font-sans">{preview}</pre>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white p-4 space-y-2">
          <span className="text-xs font-semibold text-slate-500">수신번호</span>
          {phones.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {phones.map((p) => (
                <span key={p} className="inline-flex items-center gap-1 rounded-full bg-slate-100 border border-slate-300 px-2 py-0.5 text-xs">
                  {p}
                  <button type="button" onClick={() => setPhones(phones.filter((x) => x !== p))} className="text-slate-400 hover:text-rose-500" aria-label="삭제">×</button>
                </span>
              ))}
            </div>
          )}
          <div className="flex gap-1.5">
            <input value={input} onChange={(e) => setInput(e.target.value)}
                   onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addPhone(); } }}
                   placeholder="010-0000-0000" inputMode="tel"
                   className="flex-1 px-2.5 py-1.5 text-sm border border-slate-300 rounded" />
            <button type="button" onClick={addPhone} className="px-2.5 py-1.5 text-sm rounded border border-slate-300 hover:bg-slate-100">추가</button>
          </div>
          <button onClick={send} disabled={sending || phones.length === 0}
                  className="btn-primary disabled:opacity-50 text-sm w-full mt-1">
            {sending ? '발송 중…' : `발송 (${phones.length}건)`}
          </button>
        </div>

        {results.length > 0 && (
          <div className="rounded-xl border border-slate-200 bg-white p-4">
            <div className="text-xs font-semibold text-slate-500 mb-2">발송 결과</div>
            <ul className="space-y-1 text-sm">
              {results.map((r, i) => (
                <li key={i} className="flex justify-between gap-2">
                  <span>{r.phone}</span>
                  <span className={r.status === 'SENT' ? 'text-emerald-600' : 'text-rose-600'}>
                    {r.status}{r.provider ? ` (${r.provider})` : ''}{r.message ? ` — ${r.message}` : ''}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="rounded-xl border border-slate-200 bg-white p-4">
          <div className="flex items-center justify-between mb-2">
            <div className="text-xs font-semibold text-slate-500">최근 발송 이력</div>
            <button onClick={loadLogs} className="text-xs text-sky-600 hover:underline">새로고침</button>
          </div>
          {logs.length === 0 ? (
            <div className="text-sm text-slate-400">발송 이력이 없습니다.</div>
          ) : (
            <table className="w-full text-xs">
              <thead className="text-slate-500">
                <tr className="text-left border-b">
                  <th className="py-1 font-semibold">시각</th>
                  <th className="font-semibold">템플릿</th>
                  <th className="font-semibold">수신번호</th>
                  <th className="font-semibold">상태</th>
                  <th className="font-semibold">채널</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((l) => (
                  <tr key={l.id} className="border-b border-slate-100">
                    <td className="py-1">{(l.created_at ?? '').replace('T', ' ').slice(0, 16)}</td>
                    <td>{l.purpose}</td>
                    <td>{l.phone}</td>
                    <td className={l.status === 'SENT' ? 'text-emerald-600' : 'text-rose-600'}>{l.status}</td>
                    <td>{l.provider}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </AppShell>
  );
}
