import { useCallback, useEffect, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import PageContainer from '../../components/ui/PageContainer';
import { PageHeader, SearchInput } from '../../components/ui';
import { PERSON_ROLE_LABEL, type PersonRole } from '../../types/person';

type PersonRow = { id: number; name: string; roles: PersonRole[]; supplier_name?: string | null };
type SentRow = {
  id: number;
  title: string;
  body: string;
  created_at: string;
  target?: string | null;
  recipient_count: number;
  read_count: number;
};
type RecipientRow = { person_id: number; name: string; read_at: string | null };

type Tab = 'compose' | 'outbox';

function fmt(dt: string): string {
  return new Date(dt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' }).slice(0, 16);
}

export default function SupplierAnnouncementPage() {
  const [tab, setTab] = useState<Tab>('compose');

  return (
    <AppShell breadcrumb={[{ label: '인원 공지' }]}>
      <PageContainer>
        <PageHeader
          title="인원 공지"
          subtitle="자기 소속 조종원·인력원 폰/워치로 공지를 보내고, 확인 상태를 추적합니다."
        />
        <div className="flex gap-1 border-b border-slate-200 mb-5">
          {([['compose', '보내기'], ['outbox', '발신함']] as const).map(([key, label]) => (
            <button
              key={key}
              type="button"
              onClick={() => setTab(key)}
              className={`px-4 py-2 text-sm font-semibold -mb-px border-b-2 ${
                tab === key
                  ? 'border-brand-600 text-brand-700'
                  : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
        {tab === 'compose' ? <ComposeTab /> : <OutboxTab />}
      </PageContainer>
    </AppShell>
  );
}

function ComposeTab() {
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [query, setQuery] = useState('');
  const [target, setTarget] = useState<'phone' | 'all'>('phone');
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<{ attempted: number; targets: number; recipients?: number } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [persons, setPersons] = useState<PersonRow[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    api.get<any>('/api/persons?size=500')
      .then((res) => {
        if (cancelled) return;
        const list = Array.isArray(res.data) ? res.data : (res.data?.content ?? []);
        setPersons(list.map((p: any) => ({
          id: p.id, name: p.name, roles: p.roles ?? [], supplier_name: p.supplier_name ?? p.supplierName,
        })));
      })
      .catch(() => { if (!cancelled) setPersons([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const q = query.trim().toLowerCase();
  const shown = q
    ? persons.filter((p) => p.name.toLowerCase().includes(q)
        || p.roles.some((r) => (PERSON_ROLE_LABEL[r] ?? '').includes(q)))
    : persons;

  const toggle = (id: number) =>
    setSelectedIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));

  const send = async () => {
    if (!title.trim() || !body.trim()) { setError('제목과 내용은 필수입니다'); return; }
    if (selectedIds.length === 0) { setError('보낼 인원을 1명 이상 선택하세요'); return; }
    setSending(true); setError(null); setResult(null);
    try {
      const res = await api.post('/api/announcements/broadcast', {
        title: title.trim(), body: body.trim(), person_ids: selectedIds, target,
      });
      setResult(res.data);
      setTitle(''); setBody(''); setSelectedIds([]);
    } catch (e) {
      const err = e as AxiosError<{ message?: string }>;
      setError(err.response?.data?.message ?? err.message ?? '발송 실패');
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="space-y-4 max-w-2xl">
      <div>
        <label className="block text-xs font-semibold text-slate-700 mb-1">제목</label>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={64}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          placeholder="예: 내일 07시 현장 집합"
        />
      </div>
      <div>
        <label className="block text-xs font-semibold text-slate-700 mb-1">내용</label>
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          maxLength={500}
          rows={4}
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          placeholder="자세한 내용을 입력하세요"
        />
      </div>

      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="block text-xs font-semibold text-slate-700">
            인원 선택 ({selectedIds.length}명)
          </label>
          <div className="text-xs text-slate-500">
            <button type="button" onClick={() => setSelectedIds(shown.map((p) => p.id))}
              className="underline hover:text-slate-700 mr-2">전체 선택</button>
            <button type="button" onClick={() => setSelectedIds([])}
              className="underline hover:text-slate-700">선택 해제</button>
          </div>
        </div>
        <SearchInput value={query} onChange={setQuery} placeholder="이름·역할 검색" className="mb-2" />
        <div className="rounded-md border border-slate-300 bg-white max-h-56 overflow-y-auto p-2">
          {loading && <div className="text-sm text-slate-500 px-2 py-1">불러오는 중…</div>}
          {!loading && shown.length === 0 && (
            <div className="text-sm text-slate-500 px-2 py-1">표시할 인원이 없습니다.</div>
          )}
          {shown.map((p) => (
            <label key={p.id} className="flex items-center gap-2 px-2 py-1 hover:bg-slate-50 rounded cursor-pointer text-sm">
              <input type="checkbox" checked={selectedIds.includes(p.id)} onChange={() => toggle(p.id)} />
              <span className="text-slate-900">{p.name}</span>
              {p.roles.length > 0 && (
                <span className="text-xs text-slate-500">· {p.roles.map((r) => PERSON_ROLE_LABEL[r] ?? r).join(', ')}</span>
              )}
            </label>
          ))}
        </div>
      </div>

      <div>
        <label className="block text-xs font-semibold text-slate-700 mb-1">발송 대상 기기</label>
        <div className="flex gap-4 text-sm">
          <label className="inline-flex items-center gap-2">
            <input type="radio" name="target" value="phone"
              checked={target === 'phone'} onChange={() => setTarget('phone')} />
            폰만
          </label>
          <label className="inline-flex items-center gap-2">
            <input type="radio" name="target" value="all"
              checked={target === 'all'} onChange={() => setTarget('all')} />
            폰 + 워치
          </label>
        </div>
      </div>

      <div className="rounded-md bg-blue-50 border border-blue-200 p-3 text-xs text-blue-800">
        선택한 인원의 폰/워치로 푸시가 전송되고, 발신함에서 각자의 확인 여부를 볼 수 있습니다.
      </div>
      <button
        onClick={send}
        disabled={sending}
        className="rounded-md bg-brand-600 px-6 py-2 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50"
      >
        {sending ? '발송 중...' : '공지 발송'}
      </button>
      {result && (
        <div className="rounded-md bg-emerald-50 border border-emerald-200 p-3 text-xs text-emerald-800">
          발송 완료 — 대상 {result.recipients ?? result.targets}명 (푸시 시도 {result.attempted}건)
        </div>
      )}
      {error && (
        <div className="rounded-md bg-red-50 border border-red-200 p-3 text-xs text-red-800">{error}</div>
      )}
    </div>
  );
}

function OutboxTab() {
  const [rows, setRows] = useState<SentRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [openId, setOpenId] = useState<number | null>(null);
  const [recipients, setRecipients] = useState<RecipientRow[]>([]);
  const [recipLoading, setRecipLoading] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    api.get<SentRow[]>('/api/announcements/sent')
      .then((res) => setRows(res.data ?? []))
      .catch(() => setRows([]))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const openRecipients = async (id: number) => {
    if (openId === id) { setOpenId(null); return; }
    setOpenId(id);
    setRecipLoading(true);
    try {
      const res = await api.get<RecipientRow[]>(`/api/announcements/${id}/recipients`);
      setRecipients(res.data ?? []);
    } catch {
      setRecipients([]);
    } finally {
      setRecipLoading(false);
    }
  };

  if (loading) return <p className="text-sm text-slate-400 py-12 text-center">불러오는 중...</p>;
  if (rows.length === 0) return <p className="text-sm text-slate-400 py-12 text-center">보낸 공지가 없습니다.</p>;

  return (
    <div className="rounded-xl border border-slate-200 bg-white divide-y divide-slate-100">
      {rows.map((r) => {
        const open = openId === r.id;
        const rate = r.recipient_count > 0 ? Math.round((r.read_count / r.recipient_count) * 100) : 0;
        return (
          <div key={r.id}>
            <button
              type="button"
              onClick={() => void openRecipients(r.id)}
              className="w-full text-left p-4 hover:bg-slate-50 flex items-start gap-3"
            >
              <div className="flex-1 min-w-0">
                <div className="text-sm font-semibold text-slate-900">{r.title}</div>
                <p className="text-sm text-slate-600 mt-1 line-clamp-2">{r.body}</p>
                <div className="text-xs text-slate-400 mt-2">{fmt(r.created_at)}</div>
              </div>
              <div className="shrink-0 text-right">
                <div className="text-xs font-semibold text-slate-700">
                  확인 {r.read_count}/{r.recipient_count}
                </div>
                <div className="text-xs text-slate-400">{rate}%</div>
              </div>
            </button>
            {open && (
              <div className="px-4 pb-4 bg-slate-50/60">
                {recipLoading ? (
                  <p className="text-xs text-slate-400 py-3">수신자 불러오는 중...</p>
                ) : recipients.length === 0 ? (
                  <p className="text-xs text-slate-400 py-3">수신자가 없습니다.</p>
                ) : (
                  <ul className="divide-y divide-slate-100 rounded-md border border-slate-200 bg-white">
                    {recipients.map((rc) => (
                      <li key={rc.person_id} className="flex items-center justify-between px-3 py-2 text-sm">
                        <span className="text-slate-800">{rc.name}</span>
                        {rc.read_at ? (
                          <span className="text-xs text-emerald-700">확인 · {fmt(rc.read_at)}</span>
                        ) : (
                          <span className="text-xs text-slate-400">미확인</span>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
